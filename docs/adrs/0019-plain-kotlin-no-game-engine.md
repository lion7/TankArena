# ADR 0019: Plain Kotlin and the Game's Own Renderer, No Game Engine

- **Status:** Accepted (2026-10-19).
- **Related:** [ADR 0001](0001-kotlin-and-compose-multiplatform.md) (Kotlin and Compose),
  [ADR 0007](0007-authoritative-server.md) (authoritative simulation),
  [ADR 0025](0025-multiplatform-modules-and-android.md) (modules and Android).

## Context

Until 2026-10-19 the rewrite used a third-party game engine on top of Compose (see
[history](../history.md)). After the port of the original's rules
([ADR 0010](0010-port-the-original-rules.md)) it did little for the game: the simulation was already
a hand-written pipeline in the original's order, collisions were the original's pixel tests,
replication was a hand-written projection, and terrain, effects, night lighting and colour cycling
were the project's own drawing code. What was left — actor bookkeeping, a viewport, a sprite
manager, a scene file format and a generic scene editor — tied the simulation to a UI library and
made a dedicated server, Android, four views and an in-game editor harder. It was also maintained by
one person, the project's largest external risk.

## Decision

No game engine and no ECS framework anywhere; plain Kotlin classes.

- **Simulation (`:game-sim`, common Kotlin):** `Match` owns all match state: vehicles, map objects
  (`MatchObjectList`, updated in insertion order), runtime objects (bonuses, soldiers, A-bombs,
  burning pieces), the `StructureGrid`, goal counters and seeded random streams. `Match.tick()` runs
  the step in the original's order ([ADR 0002](0002-fixed-step-100hz.md)); its rules are split by
  subsystem into `Match*.kt` files. `SimulationLoop` drives it on any JVM thread
  ([ADR 0014](0014-simulation-thread-and-recordings.md)).
- **Deterministic maths:** all trigonometry goes through `DetMath` (`StrictMath`).
- **Renderer (`:game-ui`):** `RenderScene` draws a view of a `WorldSnapshot` on a Compose `Canvas`:
  terrain, objects by layer and height, the top layer, effects, then night and colour cycling as
  runtime shaders; the status display and radar are drawn over it. `PictureCache` loads the extracted
  pictures. Split screen calls the same renderer once per view.
- **Map files:** the native `.tamap` format, read and written with kotlinx.serialization
  ([ADR 0022](0022-native-map-format.md)).
- **Editor:** a screen of the game on the same renderer and map model
  ([ADR 0021](0021-editor-in-the-game.md)).

## Consequences

- `:game-sim` depends only on `:game-content`, `:game-protocol` and kotlinx.serialization. It runs in
  the desktop game, on Android, in the dedicated server and in headless tests.
- The removal changed no gameplay: the rules version stayed the same, every recording replayed
  identically, and the reference renders came out byte for byte the same.
- The project owns its renderer and its object bookkeeping.
- Not done from the original plan, and not needed so far: a separate `World`/`MatchHost` layer (the
  network host wraps a `LocalMatchClient` instead), a picture atlas, and splitting `Tank.kt` like
  `Match` (see the [roadmap](../roadmap.md) and [architecture](../architecture.md#known-structural-debt)).
