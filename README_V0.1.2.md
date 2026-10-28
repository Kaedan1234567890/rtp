# Chill Zone RTP 0.1.2-alpha — build repair

**Status:** SOURCE ONLY — not compiled or tested inside Minecraft.

## Confirmed repair
The earlier 0.1.1 GitHub Actions workflow built version `0.1.1-alpha` but tried to upload `build/libs/chill-zone-rtp-0.1.0-alpha.jar`. GitHub therefore could fail with `No files were found` even if Java compilation worked. The workflow now builds `0.1.2-alpha`, displays `build/libs`, and uploads exactly `chill-zone-rtp-0.1.2-alpha.jar`.

The previous GitHub error video showed a stack-trace tail, **not** the first actual Java compilation error. A compiler fix cannot honestly be verified without the first `error:` line. This ZIP deliberately avoids speculative API changes that could break working code.

## Existing intended behavior (not yet tested)
- `/rtp`: 27-slot menu: Overworld, Nether, End
- Overworld available 30 min into Moderation's one-hour grace period; within 2,500 blocks of world spawn
- Nether/End require a saved dimension visit and Moderation dimension-open state
- 60-second global cooldown on successful teleport, safe ground checks, End outer islands

## Github Actions
1. Upload the **contents** of `chillzone-rtp-prototype` to the repository root (including `.github`).
2. Run the `Build Chill Zone RTP 0.1.2-alpha` workflow.
3. If `Compile and package RTP` fails, read the **first** `error:` above the bottom stack trace. Do not treat an artifact-upload error as a Java compiler error.
4. Only use the JAR from a successful workflow; do not put this ZIP in the Minecraft `mods` folder.

## Known limitations
- Cannot validate Java 25 Minecraft/Fabric 26.3 APIs in this offline environment.
- Chunk selection is synchronous; stress-test on a separate test world before production.
- This mod uses Moderation `ServerStateManager` methods that are confirmed to exist in Moderation 0.3.16 source.
