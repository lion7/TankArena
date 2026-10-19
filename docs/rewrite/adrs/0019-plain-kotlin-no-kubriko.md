# ADR 0019: Plain Kotlin Everywhere — No Kubriko

- **Status:** Accepted and done (2026-10-19). No module depends on Kubriko:
  - simulation and importer: `MatchObjectList` updates objects in Kubriko's order and `SceneFile`
    reads and writes the same scene JSON (all 69 scenes regenerate byte for byte, every recording
    replays unchanged); package `com.tankarena.sim` with plain names (`Match`, `Tank`, …), `Match`'s
    rules split into `Match*.kt` files, maths through `DetMath`;
  - client: `RenderScene` draws each window on a Compose `Canvas` the way Kubriko's viewport did and
    `PictureCache` replaces the sprite manager; the reference renders came out byte-identical;
  - `:game-editor` deleted.
  Still open from the plan below: `MatchHost` in common code (with the module layout of
  [ADR 0025](0025-multiplatform-modules-and-android.md)), splitting `Tank`, a picture atlas.
- **Supersedes:** the Kubriko parts of [ADR 0001](0001-desktop-first-compose-kubriko.md),
  [ADR 0007](0007-authoritative-headless-kubriko-server.md) and
  [ADR 0008](0008-kubriko-and-scene-json.md); [ADR 0009](0009-editor-wraps-kubriko-scene-editor.md).

## Context

Since the port of the original's rules ([ADR 0010](0010-port-the-original-rules.md)) Kubriko does
little for the game:

- **Server:** `Match.tick()` is a hand-written pipeline; Kubriko's tick is one of its
  ~45 steps and only calls `update()` on five actor types. `CollisionManager` has no effect,
  `TerrainSlideManager` only applies queued damage and pushes, replication is a hand-written
  projection, and actors are added asynchronously, so the server polls until they appear
  (`awaitActorCount`). The server needs Compose and four Kubriko artifacts on its class path.
- **Client:** it uses Kubriko's viewport, actor bookkeeping and sprite manager. Terrain, top layer,
  effects, night lighting and colour cycling are already the project's own drawing code. The sprite
  manager fails when used before the viewport exists. Four player windows would mean four Kubriko
  instances, each holding its own copy of the scene.
- **Editor and maps:** the map format is Kubriko scene JSON, and the editor is Kubriko's `SceneEditor`.

The goals ahead — networked play with a dedicated server, an Android package, gamepads, four players
on one screen, an in-game editor — all want a small, deterministic game core with no UI dependency, and
one renderer that draws any number of views on any Compose target. Kubriko is also maintained by one
person, which makes it the project's largest external risk.

## Decision

Remove Kubriko from every module.

- **Simulation (`game-sim`, common Kotlin):** a `World` owns all match state — vehicles, map objects,
  runtime objects, the structure grid, goal counters and the seeded random streams — as plain classes
  in typed lists with stable ids. `step(inputs)` advances one 10 ms tick and returns the tick's
  events. Systems run in the original's order (controls, transport, effects, bullets, parts, overlap,
  objects; see [ADR 0002](0002-fixed-step-100hz.md) for the legacy step). No ECS framework.
- **Match host (`MatchHost`, common Kotlin):** drives `step()` at 100 ticks per second from whatever
  loop the platform provides (desktop thread, Android thread, server coroutine), collects inputs and
  publishes frames. `LocalMatchClient` and the future `RemoteMatchClient`
  ([ADR 0024](0024-networking.md)) sit on top of it.
- **Renderer (Compose Canvas):** one renderer draws a `WorldSnapshot` into a rectangle: terrain,
  objects by height, top layer, effects, night and colour cycling as runtime shaders, HUD. Split screen
  is the same renderer called for each view. Pictures are loaded once into an image atlas.
- **Deterministic math:** all trigonometry goes through one helper (tables for the 16 directions,
  `StrictMath`-equivalent formulas elsewhere), so recordings replay identically on the JVM, Android and
  WASM.
- **Map files:** a native format read with kotlinx.serialization ([ADR 0022](0022-native-map-format.md)).

The migration keeps `GAME_RULES_VERSION` unchanged: the committed recordings and the rule tests must
pass before and after, proving that nothing about gameplay moved.

## Consequences

- `game-sim` depends only on Kotlin and kotlinx.serialization; it can run in a dedicated server, on
  Android and in a browser.
- Deleted (most of it in step 1a): `TerrainSlideManager`, Kubriko's `Collidable`/`Editable` interfaces on game
  classes, `Wall`/`WallState`, `TankArenaSerializableMetadata`, `awaitActorCount`,
  `ScenePreloadManager`, the Kubriko render actors and `:game-editor`.
- `Match` (3,149 lines) and `Tank` (1,760 lines) are split by system as part of the move. Done for
  `Match` on 2026-10-19: its rules sit in ten `Match*.kt` files, one per subsystem.
- The renderer is new code; its output is checked against the current client with offscreen renders
  of the same snapshots before the old one is removed.
