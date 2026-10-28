# Chill Zone RTP 0.1.0-alpha (UNCOMPILED PROTOTYPE)

**This is source code, not an installable JAR. It has not been compiled or game-tested.**

- /rtp opens a 27-slot chest with three glass sections and the grass/netherrack/end-stone choices.
- Overworld at most 2,500 blocks from world spawn; not available until Moderation grace finishes.
- Nether must have been visited and Moderation must report open.
- End must have been visited and Moderation must report open, and targets are in the outer islands.
- Global 60s cooldown applied only on successful teleports.
- Solid ground / no fluid / headroom checks; 48 attempts before gracefully giving up.
- The visited-dimension state persists in the world data folder.
- Moderation state is accessed via the existing `ServerStateManager` public methods; fail closed when the mod/state is unavailable.

## Critical testing caveats

1. Compile first in Minecraft 26.3 CI; APIs are unverified. Minecraft 26.3 may require updates to method signatures.
2. Test Nether safety, End void, world-border constraints, Geyser Bedrock chest clicks, and 60-second cooldown; no test outcomes are claimed.
3. Existing advancement requirements have been *approximated by saved legitimate dimension visits*, not exact advancement criteria. This needs improvement if the exact advancement is essential.
4. RTP teleport and chunk scanning are synchronous in this first alpha and could cause lag for unexplored chunks. Next iteration should be asynchronous / bounded via scheduling and chunk preloading.
5. `server preparing` resets server grace state; `/server start` opens one-hour grace via Moderation. RTP reads it; does not change those commands.
6. A fully unloaded target chunk can cause an unexpected heightmap result. Test thoroughly before use on live server.
7. Do not confuse 2,500 max distance with earlier plan's 2,000 MIN distance. This alpha explicitly uses max 2,500.

This prototype is intentionally separate from Homes and Shop; it is a new /rtp mod as requested.

## 0.1.1-alpha — Overworld RTP launch timing

Overworld RTP is locked during the first **30 minutes** following `/server start`, then opens automatically, while PvP remains under Moderation's original **60-minute** grace period. `/server preparing` locks Overworld RTP again. A subsequent `/server start` resets the 30-minute lock interval. This queries `isServerStarted()` and `graceSecondsRemaining()` from Moderation; missing methods fail closed.

The RTP GUI, 60-second global cooldown, Nether/End unlock checks, and safe landing prototype are unchanged. Source is uncompiled and not yet gameplay tested.
