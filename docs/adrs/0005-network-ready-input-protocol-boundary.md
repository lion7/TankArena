# ADR 0005: Input Frames and Server Frames as the Only Seam

- **Status:** Accepted (2026-04).
- **Related:** [ADR 0007](0007-authoritative-server.md) (authoritative server),
  [ADR 0024](0024-networking.md) (networking), [ADR 0026](0026-input-and-gamepads.md) (input layer).

## Context

The original reads the keyboard and joysticks inside the game loop and moves the objects in the same
global structures it draws from. Networked play, replays, tests and the computer AI all need a narrow
seam between "what a player asks for" and "what the game decides".

## Decision

- **Input** crosses the seam as one `InputFrame` per player per tick
  (`game-protocol/src/nl/tankarena/protocol/Frames.kt`): drive, turn and turret keys, fire, weapon
  cycling, shield, tank selection (F1–F10) and, while fire is held, the map position pointed at
  ([ADR 0029](0029-extended-controls.md)). Sticks, touch and the mouse are turned into these keys by
  the input layer ([ADR 0026](0026-input-and-gamepads.md)). The simulation turns the frame
  into the internal `PlayerIntentFrame` (`game-protocol/src/nl/tankarena/input/PlayerIntent.kt`),
  which is also what the computer AI produces and what recordings store.
- **Output** crosses the seam as a `ServerFrame`: the `WorldSnapshot` (actor states, the step's
  `GameEvent`s, changed tiles) plus one `PlayerView` per player (camera, status display, radar, lock
  marker).
- **`MatchClient`** (`submitInput`, `tick`, `currentServerFrame`, `dispose`) is the only way the game
  screen talks to a match. `LocalMatchClient` runs a match in-process, `ReplayMatchClient` plays a
  recording, and `RemoteMatchClient` (`:game-net`) plays a match hosted on another machine.
- All these types are plain `@Serializable` Kotlin with no UI dependency.

## Consequences

- Tests, recordings, replays, the AI and the network all drive a match the same way.
- `PROTOCOL_VERSION` (`WorldSnapshot.kt`) versions the snapshot types; network peers compare it in
  the handshake ([ADR 0024](0024-networking.md)).
