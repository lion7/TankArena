# Build

The project builds with the Kotlin toolchain (the successor of Amper), pinned to `0.13.0` by the wrappers. The Gradle scaffold has been removed.

## Layout
- Root: `project.yaml`
- Per-module: `<module>/module.yaml`
- Wrappers: `./kotlin`, `kotlin.bat` (refresh with `./kotlin update`)
- Source layout: `src/kotlin` for production, `test/kotlin` for tests.
- Build output: `build/` (override with `--build-dir` or `KOTLIN_TOOLCHAIN_BUILD_DIR`).

Library modules use `product: kmp/lib` with `platforms: [jvm]`. The two app modules (`:game-client`, `:game-editor`) and the CLI (`:tools-mapconv`) use `product: jvm/app`.

## Versions

Kotlin, Compose Multiplatform, kotlinx.serialization and the JDK are not pinned in the module files; they follow the toolchain defaults (for `0.13.0`: Kotlin 2.4.20, Compose 1.12.1, serialization 1.11.0, JDK 25). Updating the toolchain therefore updates them too. Check the resolved values with:
```bash
./kotlin show settings --all-modules
```

Library coordinates (Kubriko, Compose material3) carry explicit versions in each `module.yaml`.

## IDE

IntelliJ IDEA with the "Kotlin Toolchain" plugin.

## Cache placement (optional)

To keep bootstrap and download caches inside the repo (both paths are gitignored), prefix commands with:
- `XDG_CACHE_HOME=/var/projects/TankArena/.cache`
- `KOTLIN_CLI_BOOTSTRAP_CACHE_DIR=/var/projects/TankArena/.kotlin-cli-cache`

## Common commands

Full build:
```bash
./kotlin build
```

All tests:
```bash
./kotlin test
```

Tests for one module:
```bash
./kotlin test -m game-server
```

Replace the module name with `game-content`, `game-protocol`, `game-client`, or `tools-mapconv` as needed.

Run the desktop client:
```bash
./kotlin run -m game-client
```

Run the editor:
```bash
./kotlin run -m game-editor
```

## Kubriko dependency

The runtime uses Kubriko `0.7.3` from Maven Central (`io.github.pandulapeter.kubriko:*-desktop:0.7.3`). The headless `Kubriko.newInstance` + `tick(Int)` API the server relies on is included in the public release — no fork or `mavenLocal` setup is required. See [ADR 0008](adrs/0008-kubriko-and-scene-json.md).
