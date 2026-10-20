# ADR 0024: Networked Play — Authoritative Server with Snapshots

- **Status:** Accepted (2026-10-19); partly implemented (2026-10-20: LAN play by address or from
  the LAN list, and the dedicated server; frames are not deltas yet). Extended ruleset only
  ([ADR 0023](0023-classic-and-extended-rulesets.md)).

## Context

The simulation is deterministic and already separated from the client by `MatchClient`,
`InputFrame` and `ServerFrame` ([ADR 0005](0005-network-ready-input-protocol-boundary.md)). Networked
play must work between desktop and Android (and later the browser), for up to four players, and let a
player join a waiting match.

Two families of netcode were considered:

- **Lockstep or rollback** (only inputs are sent; every peer simulates): tiny bandwidth, but every
  peer must compute bit-identical results. Floating-point maths can differ in the last bit between the
  JVM, Android and WASM, which would desync the match, and late joining is hard.
- **Authoritative server with snapshots**: one machine simulates; clients send inputs and draw the
  snapshots they receive. More bandwidth, but tolerant of platform differences and simple to join.

## Decision

- **Server authority.** One `MatchHost` simulates at 100 ticks per second. Clients send `InputFrame`s
  stamped with the tick they are meant for; the server applies each at the latest tick it can.
- **Snapshots** go to clients 20–30 times per second, delta-compressed against the last snapshot the
  client acknowledged, and limited to what lies near that client's views (some maps have more than
  1,000 mines and many changed tiles). Events (sounds, effects, messages) travel with them.
- **Smoothing on the client:** other objects are drawn about 100 ms in the past and interpolated
  between snapshots. Predicting the player's own vehicle (with correction) is added only if LAN tests
  show it is needed for internet play.
- **Transport:** Ktor WebSockets with kotlinx.serialization (CBOR or a compact binary form) — available
  on the JVM, Android and in the browser.
- **Hosting:** a *listen server* (one player hosts from the menu, others join) and a headless
  `app-server` for dedicated hosting; both run the same `MatchHost`. LAN discovery by UDP broadcast;
  internet play first by address, later through a small relay/lobby server. No host migration.
- **Protocol versioning:** the handshake compares `PROTOCOL_VERSION` and the ruleset's
  `GAME_RULES_VERSION`; mismatches are refused with a clear message.

## Consequences

- The deterministic core is still valuable (recordings, tests, replays), but networked play does not
  depend on cross-platform bit-exactness.
- The client gains snapshot interpolation, which also smooths local play at high display rates.
- `RemoteMatchClient` implements `MatchClient`; menus and rendering do not care whether a match is
  local or remote.
- Cheating by modified clients is limited to what inputs can do.
