# Build

The project builds through Amper. The Gradle scaffold has been removed.

## Layout
- Root: `project.yaml`
- Per-module: `<module>/module.yaml`
- Wrappers: `./amper`, `amper.bat`
- Source layout: `src/kotlin` for production, `test/kotlin` for tests.

Library modules use `product: lib` with `platforms: [jvm]`. The two app modules (`:game-client`, `:game-editor`) and the CLI (`:tools-mapconv`) use `product: jvm/app`.

## Cache placement

Local Amper bootstrap and download caches live in the repo root and are gitignored:
- `XDG_CACHE_HOME=/var/projects/TankArena/.cache`
- `AMPER_BOOTSTRAP_CACHE_DIR=/var/projects/TankArena/.amper-cache`

Prefix every command with these so cache state stays local.

## Common commands

Full build:
```
XDG_CACHE_HOME=/var/projects/TankArena/.cache AMPER_BOOTSTRAP_CACHE_DIR=/var/projects/TankArena/.amper-cache ./amper build
```

Run the desktop client:
```
XDG_CACHE_HOME=/var/projects/TankArena/.cache AMPER_BOOTSTRAP_CACHE_DIR=/var/projects/TankArena/.amper-cache ./amper task :game-client:runJvm
```

Run the editor:
```
XDG_CACHE_HOME=/var/projects/TankArena/.cache AMPER_BOOTSTRAP_CACHE_DIR=/var/projects/TankArena/.amper-cache ./amper task :game-editor:runJvm
```

Tests for a module:
```
XDG_CACHE_HOME=/var/projects/TankArena/.cache AMPER_BOOTSTRAP_CACHE_DIR=/var/projects/TankArena/.amper-cache ./amper task :game-server:testJvm
```

Replace the module name with `:game-content`, `:game-protocol`, `:game-client`, or `:tools-mapconv` as needed.

## Kubriko dependency

The runtime uses Kubriko `0.1.2` from Maven Central (`io.github.pandulapeter.kubriko:*-desktop:0.1.2`). The headless `Kubriko.newInstance` + `tick(Int)` API the server relies on is included in the public release — no fork or `mavenLocal` setup is required. See [ADR 0008](adrs/0008-kubriko-and-scene-json.md).
