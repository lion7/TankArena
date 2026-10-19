# ADR 0005: Network-Ready Input and Protocol Boundary

- **Status:** Accepted (2026-04; revised 2026-10-19).
- **Related:** [ADR 0024](0024-networking.md) (networking), [ADR 0026](0026-input-and-gamepads.md) (input layer).

## Context

The original reads the keyboard and joysticks inside the game loop and moves the objects in the same
global structures it draws from. Networked play and replays both need a narrow seam between "what a
player asks for" and "what the game decides".

## Decision

- Player input crosses the seam as one `InputFrame` per player per tick
  (`game-protocol/src/com/tankarena/protocol/Frames.kt`): drive and turn keys, turret keys, fire,
  weapon cycling, shield and tank selection (F1–F10). The rocket lock marker is steered with the
  drive keys while fire is held, as in the original. The server turns it into the internal
  `PlayerIntentFrame` (`game-protocol/src/com/tankarena/input/PlayerIntent.kt`), which is also what
  the computer AI produces and what recordings store.
- Output crosses the seam as a `ServerFrame`: the `WorldSnapshot` plus one `PlayerView` per player
  (camera, status display, radar, lock marker) and the tick's `GameEvent`s.
- `MatchClient` (`submitInput`, `tick`, `currentServerFrame`, `dispose`) is the only way the client
  talks to a match. `LocalMatchClient` runs the match in-process; `RemoteMatchClient` implements the
  same interface for networked play ([ADR 0024](0024-networking.md)), where each `InputFrame` also
  carries the tick it is meant for and a player and team id ([ADR 0027](0027-four-players-two-teams.md)).
- All of these types are plain `@Serializable` Kotlin with no Compose or Kubriko dependency.

## Consequences

- Recordings, tests and (later) the network all drive a match the same way.
- The protocol carries versions (`PROTOCOL_VERSION`, see [architecture](../architecture.md#versions))
  so incompatible peers or files can be rejected.
- Leftover types from earlier designs (`ReplayProtocol.kt`, `PlayerIntent`/`ClientInput` in
  `snapshot/PlayerInput.kt`, `EntityId`, `Tick`) are unused and should be deleted.
