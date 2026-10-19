# ADR 0019: Remove Kubriko from the Server

- **Status:** Proposed (decided in direction on 2026-10-19; not implemented).
- **Amends:** [ADR 0007](0007-authoritative-headless-kubriko-server.md) and
  [ADR 0008](0008-kubriko-and-scene-json.md). Must be decided together with
  [ADR 0003](0003-legacy-map-import.md).

## Context

Since the port of the original's rules ([ADR 0010](0010-port-the-original-rules.md)) the server uses
Kubriko only as a container:

- `ServerMatchPrototype.tick()` is a hand-written pipeline; Kubriko's tick is one of its ~45 steps and
  only calls `update()` on five actor types.
- `CollisionManager` is registered but has no effect; `TerrainSlideManager` merely applies queued
  damage and pushes; `PhysicsManager` was never used.
- Replication does not use Kubriko serialization (`projectToWireState` builds protocol states).
- Kubriko adds actors asynchronously, so the server polls until they appear (`awaitActorCount`).
- `:game-server` must enable Compose and depend on Kubriko's engine, collision, serialization and
  scene-editor API artifacts, which a headless or network server should not need.

## Decision

- The simulation becomes **plain Kotlin**: map objects are ordinary classes owned by the match, the
  tick calls their step functions directly in the original's order, and no Kubriko or Compose types
  appear in `:game-server`.
- **Kubriko stays on the client** for the playfield viewport and sprites
  ([ADR 0001](0001-desktop-first-compose-kubriko.md)) and in the editor
  ([ADR 0009](0009-editor-wraps-kubriko-scene-editor.md)).
- The **map file format** must then be read without Kubriko's `SerializationManager`. Options: keep
  the current JSON shape and read it with `kotlinx.serialization`, or define a new format. That choice
  is made together with ADR 0003 (source of truth), so content is converted only once.
- The editor gets its own Kubriko `Editable` wrappers around the plain map data.

## Consequences

- `:game-server` loses its Compose and Kubriko dependencies; a dedicated or network server and a WASM
  build get simpler.
- Removes `TerrainSlideManager`, `Resolvable`, the no-op `CollisionManager`, `ServerWallActor`,
  `awaitActorCount` and the `Collidable`/`Editable` interfaces on server classes.
- A sizeable refactor of `ServerMatchPrototype` (3,149 lines) and the 17 `Server*Actor` classes; it
  must keep `GAME_RULES_VERSION` unchanged (no behaviour change), which recorded playthroughs and the
  rule tests verify.
