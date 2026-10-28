# RTP 0.1.3-alpha: fix verified Gradle configuration error

The provided GitHub log failed before compilation at build.gradle line 9:
`Could not find method modImplementation() ...`.

Fixed `modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"` to `implementation ...` to match the known-working Moderation project's Fabric 26.3 dependency style. This addresses the *exact* screenshot error; no RTP Java logic was changed.

Other changes: matched version and GitHub Actions artifact path to `0.1.3-alpha`.

This source was structurally verified but not Gradle compiled or tested in-game. A new compiler error may surface once this configuration error is removed.

Upload all the files INSIDE this directory to the root of the existing RTP GitHub repository. Do not upload the enclosing directory. Run Actions; if it succeeds, install the output JAR (not the ZIP).
