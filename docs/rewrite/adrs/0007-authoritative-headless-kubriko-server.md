# ADR 0007: Authoritative Server, Render-Only Client

- **Status:** Accepted (2026-04; revised 2026-10-19). The authoritative-server decision stands; the
  use of Kubriko described under "How the server uses Kubriko today" is removed by
  [ADR 0019](0019-plain-kotlin-no-kubriko.md), and networking follows [ADR 0024](0024-networking.md).

## Context

Before 2026-04 the simulation was a pure-Kotlin `:game-sim` with a separate render projection, and the
client owned the game loop. There was no place for a networked server, and the two actor models
drifted apart.

## Decision

- **One authoritative simulation per match**, in `:game-server` (`Match`). Single
  player, two players on one machine and (later) networked play all run the same code.
- **The client only renders.** It submits `InputFrame`s and draws `ServerFrame`s through `MatchClient`
  ([ADR 0005](0005-network-ready-input-protocol-boundary.md)); it never decides gameplay.
- **Split screen is a client concern.** The server builds one `PlayerView` per player; the client
  decides how the views are laid out (side by side, as in the original's `compute_window_size()`).
- **Replication is a projection.** Each tick the server builds a `WorldSnapshot` by projecting its
  objects into the protocol's `ActorState` types (`projectToWireState`), adding the small runtime
  objects (bonuses, soldiers, A-bombs) and the structure tiles that changed. The client keeps one
  render actor per stable `actorId` (`ClientScene.sync`).

## How the server used Kubriko (until 2026-10-19)

Removed on 2026-10-19 (phase 1, [ADR 0019](0019-plain-kotlin-no-kubriko.md)); kept for the record.
The 2026-04 version of this ADR said the match is a headless Kubriko instance whose managers do the
work. That is no longer true:

- `Match.tick()` is a hand-written pipeline of about 45 steps in the original's order
  of concerns. Kubriko's `tickSource.tick(10)` is one step in it, which runs the `update()` of the
  tank, projectile, mine, rocket and mortar actors.
- Kubriko's `ActorManager` holds the map objects, and its `SerializationManager` reads the scene file.
- `CollisionManager` is registered but does nothing useful; collisions are the original's pixel-mask
  and circle tests (`StructureGrid`, `Tank`; see [ADR 0011](0011-structure-grid-and-pixel-masks.md)).
- `TerrainSlideManager` no longer slides tanks along walls; it only applies each tank's queued damage
  and vehicle pushes after the actor update.
- `PhysicsManager` (Box2D) was never used.

## Consequences

- One simulation code path, deterministic and testable without a window
  ([ADR 0014](0014-simulation-thread-and-recordings.md)).
- Scene-file state (`map object.State`) and wire state (`ActorState`) are separate types; adding a
  field to a map object may mean touching both.
- The server's Kubriko dependency costs more than it gives (asynchronous actor adds that need
  `awaitActorCount`, no-op managers, Compose on the server classpath). ADR 0019 removes it.
