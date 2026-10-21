# Build

The project builds with the Kotlin toolchain (the successor of Amper), pinned to **0.13.0** by the
wrappers ([ADR 0016](adrs/0016-kotlin-toolchain-build.md)). There is no Gradle build.

## Layout

- Root: `project.yaml` (the module list) and `common.module-template.yaml` (applied by every module:
  kotlinx.serialization, and the publishing group `nl.tankarena`; the artifact id is the module's
  name); per module: `<module>/module.yaml`.
- Wrappers: `./kotlin` and `kotlin.bat` (`./kotlin update` moves the pin).
- Sources: `<module>/src/` with packages directly under it (no `src/kotlin`).
- Tests: `<module>/test/`, packages directly under it (`test/nl/tankarena/…`).
- Resources: `game-content/resources/`, `game-ui/resources/`,
  `game-ui/composeResources/drawable/` (Compose resources, accessors in `nl.tankarena.ui.resources`),
  `game-sim/testResources/`, `app-desktop/testResources/`.
- Build output: `build/` (override with `--build-dir` or `KOTLIN_TOOLCHAIN_BUILD_DIR`).

Products: `:game-protocol`, `:game-content`, `:game-sim` and `:game-ui` are `kmp/lib` with
`platforms: [jvm, android]` ([ADR 0025](adrs/0025-multiplatform-modules-and-android.md); platform
code in `src@jvm/` and `src@android/`, desktop-only tests in `test@jvm/`); `:app-android` is
`android/app` (the Android SDK is found in `~/Android/Sdk`, or set `ANDROID_HOME`);
`:game-net` is a `kmp/lib` like them (Ktor 3.6.0, kotlinx-serialization-cbor); `:app-desktop`,
`:app-server`, `:app-relay` and `:tools-mapconv` are `jvm/app` (main classes
`nl.tankarena.app.desktop.MainKt`, `nl.tankarena.server.MainKt`, `nl.tankarena.relay.MainKt` and
`nl.tankarena.tools.mapconv.MainKt`).

## Versions

Kotlin, Compose Multiplatform, kotlinx.serialization and the JDK are not pinned in the module files;
they follow the toolchain defaults. For 0.13.0: Kotlin 2.4.20, Compose 1.12.1, kotlinx.serialization
1.11.0, JDK 25. Explicit version: `org.jetbrains.compose.material3:material3`
1.12.0-alpha03 (there is no 1.12.1 release of it). There is no game engine dependency.

```bash
./kotlin show settings --all-modules     # resolved versions
```

## Commands

```bash
./kotlin build                           # all modules
./kotlin test                            # all tests (387 on 2026-10-20, each counted once)
./kotlin test -m game-sim                # one module
./kotlin run -m app-desktop              # the game (opens a window)
./kotlin build -m app-android            # the Android app: build/tasks/_app-android_buildAndroidDebug/*.apk
./kotlin package -m app-android          # the release bundle (.aab), shrunk by R8
packaging/package-android.sh debug release bundle   # named APKs and bundle in build/dist/android
./kotlin run -m app-server -- --map flagisle --players 2   # a dedicated network game server
./kotlin run -m app-relay                                  # the internet relay (port 47310)
./kotlin run -m tools-mapconv -- <subcommand> <args>
```

The Android build does not notice changed resources or a changed `AndroidManifest.xml`; remove
`build/tasks/_app-android_buildAndroidDebug` to get them into the APK. An emulator reaches this
machine at `10.0.2.2`, which is how it joins a server running here.

The converter subcommands are listed in [assets.md](assets.md#tools-mapconv); regenerating the maps
is described in [content-format.md](content-format.md#making-the-files).

## Packages

Self-contained Linux and Windows packages are built in containers with `packaging/build-linux.sh` and
`packaging/build-windows.sh`; see [ADR 0017](adrs/0017-desktop-packaging.md) and
[`packaging/README.md`](../../packaging/README.md). Do not ship `./kotlin package -f executable-jar`
output: Compose 1.12's same-named jars collide in it.

## IDE

IntelliJ IDEA with the "Kotlin Toolchain" plugin.

## Caches (optional)

To keep download caches inside the repository (both paths are git-ignored), prefix commands with:

```bash
XDG_CACHE_HOME=$PWD/.cache KOTLIN_CLI_BOOTSTRAP_CACHE_DIR=$PWD/.kotlin-cli-cache ./kotlin build
```

Leftover caches from older setups (`.amper-cache/`, `.cache/JetBrains/Amper/`, `.gradle/`) are no
longer used and can be deleted.
