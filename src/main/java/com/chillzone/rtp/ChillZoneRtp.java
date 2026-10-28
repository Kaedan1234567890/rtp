package com.chillzone.rtp;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.LevelResource;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** 0.1.2-alpha: /rtp menu; build and in-game behavior not yet verified. */
public final class ChillZoneRtp implements ModInitializer {
    private static final long COOLDOWN_MS = 60_000L;
    private static final long OVERWORLD_RTP_LOCK_SECONDS = 30L * 60L;
    private static final int OVERWORLD_MAX = 2500;
    private static final int NETHER_MIN = 250, NETHER_MAX = 2000;
    private static final int END_MIN = 1500, END_MAX = 3500;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Random RANDOM = new Random();
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Visit> VISITS = new HashMap<>();
    private static Path persistence;
    private record Visit(boolean nether, boolean end) {}
    private static final class SaveData { Map<String, Visit> visited = new HashMap<>(); }

    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
            dispatcher.register(Commands.literal("rtp").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                open(player); return 1;
            })));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            persistence = server.getWorldPath(LevelResource.ROOT).resolve("chillzone-rtp-visits.json");
            load();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> save());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Track actual legitimate prior dimension visits, persisted between restarts.
            if (server.getTickCount() % 100 != 0) return;
            boolean dirty = false;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                UUID id = p.getUUID(); Visit old = VISITS.getOrDefault(id, new Visit(false,false));
                boolean nether = old.nether() || p.level().dimension().equals(Level.NETHER);
                boolean end = old.end() || p.level().dimension().equals(Level.END);
                if (nether != old.nether() || end != old.end()) {
                    VISITS.put(id, new Visit(nether,end)); dirty = true;
                }
            }
            if (dirty) save();
        });
    }
    private static void load() {
        VISITS.clear();
        try {
            if (persistence == null || !Files.exists(persistence)) return;
            SaveData obj = GSON.fromJson(Files.readString(persistence), SaveData.class);
            if (obj != null && obj.visited != null) obj.visited.forEach((id,visit) -> {
                try { VISITS.put(UUID.fromString(id),visit); } catch (IllegalArgumentException ignored) {}
            });
        } catch (Exception ignored) { /* no permissions are conferred on read failure */ }
    }
    private static void save() {
        if (persistence == null) return;
        try {
            SaveData o = new SaveData(); VISITS.forEach((id, v) -> o.visited.put(id.toString(),v));
            Files.writeString(persistence,GSON.toJson(o));
        } catch (Exception ignored) {}
    }
    private static boolean moderationState(String method) {
        try {
            Class<?> type = Class.forName("com.chillzone.moderation.ServerStateManager");
            return (Boolean)type.getMethod(method).invoke(null);
        } catch (ReflectiveOperationException | ClassCastException e) { return false; } // fail closed
    }
    /** Overworld RTP unlocks 30 minutes into the one-hour launch grace period.
     * Moderation continues to control PvP separately for the entire hour.
     * Never unlock when preparing/not started or when the Moderation API is absent.
     */
    private static boolean overworldRtpAllowed() {
        if (!moderationState("isServerStarted")) return false;
        try {
            Class<?> type = Class.forName("com.chillzone.moderation.ServerStateManager");
            Object seconds = type.getMethod("graceSecondsRemaining").invoke(null);
            return seconds instanceof Number && ((Number)seconds).longValue() <= OVERWORLD_RTP_LOCK_SECONDS;
        } catch (ReflectiveOperationException | ClassCastException e) { return false; }
    }
    private static boolean unlocked(ServerPlayer player, int dimension) {
        if (dimension == 0) return overworldRtpAllowed();
        Visit v = VISITS.getOrDefault(player.getUUID(),new Visit(false,false));
        if (dimension == 1) return v.nether() && moderationState("isNetherOpen");
        return v.end() && moderationState("isEndOpen");
    }
    private static Component label(String s) { return Component.literal(s); }
    private static ItemStack stack(net.minecraft.world.item.Item item, String title) {
        ItemStack result = new ItemStack(item);
        result.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(title));
        return result;
    }
    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inv, who) -> new Gui(id,inv,player),label("Chill Zone — RTP")));
    }
    private static final class Gui extends ChestMenu {
        private final ServerPlayer player;
        private Gui(int id, Inventory inv, ServerPlayer player) {
            super(MenuType.GENERIC_9x3,id,inv,new SimpleContainer(27),3);
            this.player = player;
            for(int i=0;i<27;i++) {
                int column=i%9;
                int dim=column<3?0:column<6?1:2;
                net.minecraft.world.item.Item glass = dim==0 ? Items.LIME_STAINED_GLASS_PANE : dim==1 ? Items.RED_STAINED_GLASS_PANE : Items.YELLOW_STAINED_GLASS_PANE;
                getContainer().setItem(i, stack(glass," "));
            }
            getContainer().setItem(10, unlocked(player,0)?stack(Items.GRASS_BLOCK,"Overworld • max 2500 blocks"):stack(Items.BARRIER,"Overworld • opens 30 min after /server start"));
            getContainer().setItem(13, unlocked(player,1)?stack(Items.NETHERRACK,"Nether • 60s cooldown"):stack(Items.BARRIER,"Nether • visit first + dimension open"));
            getContainer().setItem(16, unlocked(player,2)?stack(Items.END_STONE,"End • outer islands"):stack(Items.BARRIER,"End • visit first + dimension open"));
        }
        @Override public void clicked(int slot, int button, ContainerInput input, Player clicker) {
            if (slot==10 || slot==13 || slot==16) {
                int dim=slot==10?0:slot==13?1:2;
                // Run only for the player whose GUI this is; never move decorative items.
                if (clicker == player) {
                    player.closeContainer(); attempt(player,dim);
                }
            }
        }
        @Override public ItemStack quickMoveStack(Player p, int slot){return ItemStack.EMPTY;}
        @Override public boolean canTakeItemForPickAll(ItemStack s,net.minecraft.world.inventory.Slot slot){return false;}
        @Override public boolean stillValid(Player p){return true;}
    }
    private static void attempt(ServerPlayer player,int dim) {
        if (!unlocked(player,dim)) { player.sendSystemMessage(label("RTP locked. Unlock the dimension and wait for it to open.")); return; }
        long now=System.currentTimeMillis(); long last=COOLDOWNS.getOrDefault(player.getUUID(),0L);
        if(now-last < COOLDOWN_MS){ player.sendSystemMessage(label("RTP cooldown: "+((COOLDOWN_MS-now+last+999)/1000)+"s remaining"));return; }
        MinecraftServer server=player.getServer();
        ServerLevel target=server.getLevel(dim==0?Level.OVERWORLD:dim==1?Level.NETHER:Level.END);
        if(target==null){player.sendSystemMessage(label("That world is unavailable."));return;}
        // Search is bounded, and cooldown is only consumed after a successful teleport.
        BlockPos safe=findSafe(target,dim);
        if(safe==null){player.sendSystemMessage(label("Couldn't locate safe solid ground. Try again."));return;}
        // Recheck gates after search, avoiding entry when /end close or /nether close happened.
        if(!unlocked(player,dim)){player.sendSystemMessage(label("That dimension is now locked."));return;}
        Vec3 destination=new Vec3(safe.getX()+0.5,safe.getY(),safe.getZ()+0.5);
        if(player.teleportTo(target,destination.x,destination.y,destination.z,java.util.Set.of(),player.getYRot(),player.getXRot(),false)){
            COOLDOWNS.put(player.getUUID(),System.currentTimeMillis());
            player.sendSystemMessage(label("Random teleport successful."));
        } else player.sendSystemMessage(label("Teleport failed; cooldown not consumed."));
    }
    private static BlockPos findSafe(ServerLevel world,int dim) {
        int min=dim==0?100:dim==1?NETHER_MIN:END_MIN;
        int max=dim==0?OVERWORLD_MAX:dim==1?NETHER_MAX:END_MAX;
        for(int attempts=0;attempts<48;attempts++){
            double a=RANDOM.nextDouble()*Math.PI*2;
            // Uniform probability by surface area rather than clustering around center.
            double r=Math.sqrt(RANDOM.nextDouble()*(max*max-min*min)+min*min);
            int x=(int)Math.round(r*Math.cos(a)),z=(int)Math.round(r*Math.sin(a));
            if(dim==0){BlockPos spawn=world.getSharedSpawnPos();x+=spawn.getX();z+=spawn.getZ();}
            if(!world.getWorldBorder().isWithinBounds(new BlockPos(x,64,z)))continue;
            // Overworld / End: heightmap identifies highest safe candidate; Nether requires scanning caves.
            if(dim!=1){
                int y=world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)+1;
                BlockPos pos=new BlockPos(x,y,z);
                if(valid(world,pos))return pos;
            } else {
                for(int y=Math.min(115,world.getMaxY()-3);y>Math.max(35,world.getMinY()+3);y--){
                    BlockPos pos=new BlockPos(x,y,z);
                    if(valid(world,pos))return pos;
                }
            }
        }
        return null;
    }
    private static boolean valid(ServerLevel world,BlockPos feet) {
        if(feet.getY()<=world.getMinY()+2 || feet.getY()+2>=world.getMaxY())return false;
        if(!world.getWorldBorder().isWithinBounds(feet))return false;
        BlockState floor=world.getBlockState(feet.below());
        BlockState legs=world.getBlockState(feet);
        BlockState head=world.getBlockState(feet.above());
        if(floor.isAir()||!floor.getFluidState().isEmpty()||!legs.getFluidState().isEmpty()||!head.getFluidState().isEmpty())return false;
        if(floor.is(Blocks.MAGMA_BLOCK)||floor.is(Blocks.CACTUS)||floor.is(Blocks.CAMPFIRE)||floor.is(Blocks.SOUL_CAMPFIRE))return false;
        if(!floor.isCollisionShapeFullBlock(world,feet.below()))return false;
        return legs.isAir() && head.isAir();
    }
}
