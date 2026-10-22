# ADR 0007: Authoritative Simulation, Render-Only Client

- **Status:** Accepted (2026-04).
- **Related:** [ADR 0005](0005-network-ready-input-protocol-boundary.md) (the seam),
  [ADR 0014](0014-simulation-thread-and-recordings.md) (thread and determinism),
  [ADR 0024](0024-networking.md) (networking).

## Context

Early versions of the rewrite let the client own the game loop and kept a separate render model of
the world. There was no place for a networked server, and the two models drifted apart.

## Decision

- **One authoritative simulation per match**, `Match` in `:game-sim`. One player, two to four players
  on one machine, replays and network games all run the same code.
- **The client only renders.** It submits `InputFrame`s and draws `ServerFrame`s through
  `MatchClient`; it never decides gameplay. Effects that change nothing in the game (debris pixels,
  smoke, sparks) may be thrown by the client from an event.
- **Split screen is a client concern.** The simulation builds one `PlayerView` per player; the client
  lays the views out (side by side as in the original's `compute_window_size()`, or 2 × 2 for four
  players).
- **Replication is a projection.** Every step the simulation builds a `WorldSnapshot` by copying its
  objects into the protocol's `ActorState` types (`projectToWireState`), adding the small runtime
  objects (bonuses, soldiers, A-bombs, burning pieces) and the structure tiles that changed. The client
  keeps one render actor per stable `actorId` (`ClientScene.sync`).

## Consequences

- One deterministic simulation code path, testable without a window.
- Map-file state (each object's `State`) and wire state (`ActorState`) are separate types; adding a
  field to a map object may mean touching both.
- A network host only has to ship the same frames to another machine
  ([ADR 0024](0024-networking.md)).
