# Amper Migration

## Status
- The rewrite project now builds through Amper.
- Gradle build files and wrapper scripts are removed as part of this cutover.
- Desktop remains the only supported runtime target for now.

## Project Entry Point
- Root build file: `project.yaml`
- Per-module build files: `module.yaml`
- Wrapper scripts:
  - `./amper`
  - `amper.bat`

## Module Layout
- Shared libraries use Amper lib modules with `platforms: [jvm]`.
- Desktop/runtime/editor/tool modules use JVM modules.
- Source layout follows Amper conventions:
  - production code under `src/kotlin`
  - tests under `test/kotlin`

## Common Commands
- Full build:
  - `XDG_CACHE_HOME=/var/projects/TankArena/.cache AMPER_BOOTSTRAP_CACHE_DIR=/var/projects/TankArena/.amper-cache ./amper build`
- Desktop compile:
  - `XDG_CACHE_HOME=/var/projects/TankArena/.cache AMPER_BOOTSTRAP_CACHE_DIR=/var/projects/TankArena/.amper-cache ./amper task :app-desktop:compileJvm`
- JVM tests:
  - `XDG_CACHE_HOME=/var/projects/TankArena/.cache AMPER_BOOTSTRAP_CACHE_DIR=/var/projects/TankArena/.amper-cache ./amper task :game-content:testJvm`
  - `XDG_CACHE_HOME=/var/projects/TankArena/.cache AMPER_BOOTSTRAP_CACHE_DIR=/var/projects/TankArena/.amper-cache ./amper task :game-sim:testJvm`
  - `XDG_CACHE_HOME=/var/projects/TankArena/.cache AMPER_BOOTSTRAP_CACHE_DIR=/var/projects/TankArena/.amper-cache ./amper task :game-legacy:testJvm`

## Notes
- Local Amper bootstrap and download caches are intentionally kept inside the repository root during development and are ignored by Git.
- The shared gameplay/content/import modules remain free of Compose and Kubriko dependencies.
- Future `wasmJs` enablement should be added by widening the pure modules first, not by changing the desktop app modules.
