# ADR 0017: Self-Contained Desktop Packages Built in Containers

- **Status:** Accepted (2026-10-19, commit `bd6a711`).
- **Related:** Android packages (APK/AAB) are added with [ADR 0025](0025-multiplatform-modules-and-android.md).

## Context

Players should be able to run the game without installing Java. Compose's Gradle packaging tasks are
not available under the Kotlin toolchain ([ADR 0016](0016-kotlin-toolchain-build.md)), and the
toolchain's executable jar is broken by a Compose 1.12 jar name clash. `jpackage` builds only for the
operating system it runs on, and the game bundles that system's native Skia library.

## Decision

- `packaging/package.sh` (Linux) and `packaging/package.ps1` (Windows) build the game, copy the
  runtime class path from the toolchain's Maven cache with group-qualified jar names, and run
  `jpackage` (JDK 25) with a runtime trimmed to `java.base`, `java.desktop`, `java.instrument`,
  `java.management` and `jdk.unsupported`.
- Both run in containers: `packaging/build-linux.sh` (`linux.Dockerfile`, local buildx builder) makes
  a `.tar.gz` and an AppImage in `build/dist/linux/`; `packaging/build-windows.sh`
  (`windows.Dockerfile`, a remote Windows Server 2022 BuildKit builder) makes a `.zip` and an MSI
  (WiX 3.14) in `build/dist/windows/`.
- All runtime assets come from the class path; player data goes to the user's own folders
  ([ADR 0020](0020-user-data-as-json.md)), never into the game folder.

Details: [`packaging/README.md`](../../../packaging/README.md).

## Consequences

- One command per platform produces installable packages; no JDK needed on the player's machine.
- The Windows build depends on a self-hosted remote builder; macOS packages are not built.
- `rpm` and `deb` targets exist in `package.sh` but are untested.
