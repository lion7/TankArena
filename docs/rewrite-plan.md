# Tank Arena Rewrite Plan (Kotlin Multiplatform + KorGE)

## Scope and assumptions
- This rewrite is architecture-first. We preserve gameplay structure and retro feel, not C implementation details.
- Original Allegro/hardware abstraction code is treated as behavior reference only.
- First milestone targets desktop and web-capable project layout, with desktop runtime validated first.

## What exists in the legacy repo

### Core gameplay and loop
- Main game loop and sequencing are in `src/game/mainloop.c` (`read_controls`, physics/object control passes, layered draw pipeline). 
- Map loading, game reset, and map suitability/startpoint checks are in `src/game/mapfiles.c`.
- Collision and hit logic are separated into `src/check/*`.
- Tank behavior and weapons are in `src/tanks/*` and `src/objects/*`.

### Object/entity model
- Central object model is `objectstruct` in `src/include/types.h` with function pointers (`control`, `write`, `hit`, `remove`, `light`) and a union for per-type payload structs.
- This is effectively object-oriented-in-C and is the main design to replace with Kotlin data + systems.

### Map / level formats
- Legacy map header is `maptype` in `src/include/types.h`.
- Loading and object deserialization from `.MAP` files happens in `src/game/mapfiles.c`.
- Existing sample maps live in `MAPS/`.

### Rendering responsibilities
- Frame composition is split into map/background bottom layer + object writing + overlays/status/radar in `src/game/mainloop.c`.
- Low-level buffer/palette routines are in `src/utils/*`.

### Input handling paths
- Keyboard/joystick processing and control-state translation are in `src/game/controls.c`.
- The old code includes direct hardware keyboard/joystick integrations (`src/hardware/keyboard.s`).

### Audio responsibilities
- Sound and music initialization/playback are in `src/hardware/sound.c`.

### Editor-related code
- Editor code is under `src/edit/*` and uses menu callbacks and tool modes.

## Target module layout

### `game-core` (KMP, engine-agnostic)
Contains deterministic simulation and gameplay state:
- Fixed tick simulation loop.
- Entity/state model (`Tank`, `Projectile`, `Explosion` now; more later).
- Input intents as actions (no raw keycodes).
- Future: collisions, weapons, AI, map/missions, save/replay determinism.

### `game-client-korge` (KorGE app)
Contains platform/runtime concerns:
- Window setup, virtual resolution, scaling.
- Input mapping from keyboard/gamepad to core actions.
- Rendering as a pure projection of `game-core` state.
- Audio wiring in later milestones.

### `game-protocol` (KMP DTOs)
Contains transport-neutral messages:
- Input commands, snapshots, replay/event schema (initial DTO placeholders now).

### `game-editor` (KMP scaffold)
Contains editor domain scaffolding only for now:
- Minimal placeholder for in-game editor mode direction.
- No full editor implementation in this milestone.

## Legacy → Rewrite subsystem mapping

| Legacy subsystem | Legacy location | Rewrite module | Notes |
|---|---|---|---|
| Main loop orchestration | `src/game/mainloop.c` | `game-core` + `game-client-korge` | Core owns deterministic update; client owns frame/render timing and view projection. |
| Objectstruct + function pointers | `src/include/types.h` | `game-core` | Replace with sealed entities + explicit systems/composition. |
| Controls (keys/joystick) | `src/game/controls.c` | `game-client-korge` + `game-core` | Client maps raw input to actions; core consumes intents only. |
| Map loading / map metadata | `src/game/mapfiles.c`, `MAPS/*` | `game-core` (+future loader in client/tooling) | Add new Kotlin serializers + compatibility strategy later. |
| Collision / hit checks | `src/check/*` | `game-core` | Keep deterministic and testable. |
| Audio | `src/hardware/sound.c` | `game-client-korge` | Core emits events; client resolves playback. |
| Editor tools | `src/edit/*` | `game-editor` | Begin with scaffold; defer full feature parity. |

## Milestone 1 (implemented now)
- New Gradle multi-module Kotlin project added.
- `game-core` deterministic fixed-step simulation and basic entity model.
- `game-client-korge` KorGE scene with virtual resolution and layered rendering placeholders.
- Input action mapping for one player movement + primary fire intent.
- `game-protocol` DTO placeholders for future multiplayer/replays.
- `game-editor` minimal scaffold.
- Basic tests for `game-core` movement and projectile spawn.

## Near-term next milestones
1. Add tile/map model into `game-core` and parse legacy map format in tooling layer.
2. Add deterministic collision systems and weapon registry extension points.
3. Add AI controller interface and first bot behavior.
4. Add game mode abstraction (`Deathmatch`, `Race`, `CTF`, etc.) as pluggable rulesets.
5. Add replay log format and deterministic playback harness.
6. Expand KorGE renderer to sprite atlas and camera/radar/status HUD parity.

## Next slice implemented in this branch
- Build reliability fixes: pinned Gradle Java runtime to JDK 21 in `gradle.properties` and normalized Kotlin plugin versioning from the root build file.
- KorGE dependency updated to Maven Central artifact (`com.soywiz.korge:korge-jvm:6.0.0`) to avoid blocked custom repository access.
- Client runtime source moved to JVM source set and adapted to KorGE 6 API usage.
- Added `game-core` extension point contracts (`WeaponBehavior`, `AiController`, `GameModeRules`, `MissionScript`) and map model scaffolding (`MapDefinition`, `TileCell`).

## Completion status
- **Current status:** foundational vertical slice only.
- **Not a full port yet:** gameplay/content parity with legacy Tank Arena is still in progress.
- **Definition of done for full port:** mode parity + map/editor parity + deterministic replay-compatible simulation + local multiplayer + production-ready asset/audio integration.
