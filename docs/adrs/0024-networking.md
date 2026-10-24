# ADR 0024: Networked Play — Authoritative Host Sending Frames

- **Status:** Accepted (2026-10-19). Extended ruleset only
  ([ADR 0023](0023-classic-and-extended-rulesets.md)).
- **Related:** [ADR 0028](0028-internet-relay-and-lobby.md) (internet play through a relay).

## Context

The simulation is deterministic and separated from the screen by `MatchClient`, `InputFrame` and
`ServerFrame` ([ADR 0005](0005-network-ready-input-protocol-boundary.md)). Networked play must work
between desktop and Android (and later perhaps the browser), for up to four players, and let players
join a waiting game.

Two families of netcode were considered:

- **Lockstep or rollback** (only inputs are sent; every peer simulates): tiny bandwidth, but every peer
  must compute bit-identical results. Floating-point maths can differ in the last bit between the JVM,
  Android and WASM, which would desynchronise the match, and joining late is hard.
- **An authoritative host sending the world**: one machine simulates; the others send inputs and draw
  what they receive. More bandwidth, but tolerant of platform differences and simple to join.

## Decision

- **Host authority.** One machine runs the match (`HostedMatch`, a `LocalMatchClient` with the joined
  players' latest controls). Guests send their players' controls once a step; the host applies the
  latest it has.
- **One frame per step** (33 a second) to every other machine, with the step's events and only that
  machine's player views. Each frame is a **delta of the frame before** to that machine
  (`DeltaEncoder` / `DeltaDecoder`: new or changed actors, removed ids, changed tiles; whole again
  after a dropped frame). No acknowledgements are needed because the connection is ordered. The
  WebSocket deflates frames with the context of the ones before (permessage-deflate). Median 99 bytes
  a frame, SUBURBS 551: about 3 to 18 KB/s per guest, so frames are not limited to what is near the
  views.
- **Smoothing on the guest:** `RemoteMatchClient` plays the frames back about 100 ms (three steps)
  behind the newest, catching up after a stall. Own-vehicle prediction is not built; it is added only
  if play over the internet shows it is needed.
- **Transport:** Ktor WebSockets (CIO) with CBOR messages (`NetMessages.kt`, `NetCodec`), on the JVM
  and Android. Port 47300, path `/tankarena`.
- **Lobby and seats:** `GameServer` admits machines after comparing `NET_VERSION`, `PROTOCOL_VERSION`
  and the ruleset's rules version (a mismatch is refused with a message), gives their players the next
  free places (at most `MAX_NET_PLAYERS`, 4, in two teams, [ADR 0027](0027-four-players-two-teams.md))
  and starts the match with the map's `.tamap` text, so guests need not have the map.
- **Games and rounds:** together against the computer (one mission and its result) or against each
  other on a map. `NetSeries` runs player-vs-player rounds: every machine's players pick their
  vehicles before a round (30 s, then the last or first allowed; the choice starts on the last),
  rounds are counted until the match is decided, and every machine shows the round's result.
- **Back to the lobby (`NET_VERSION` 5):** after a match, or when the host leaves a round or the
  vehicle choice with ESC, the host and every connected machine go back to the lobby
  (`GameServer.reopenLobby()`, a `Lobby` message with `open`); machines can join again and the host
  may pick another map there (M). Against each other the rounds won count over every map as a series
  (`NetSeries.seriesBefore`, shown in the lobby, the vehicle choice and the result); the losers tank
  goes by it. A guest that leaves a round with ESC sits it out (`SitOut`, its vehicles stop) and
  plays again from the next round.
- **Hosting:** a *listen server* from Extended's "Network game" screen, or the headless `app-server`
  (`--map`, `--mode`, `--players`, `--rounds`, `--port`, `--repeat`, `--name`, `--relay`, `--public`)
  for dedicated hosting. No host migration.
- **Finding games:** on the LAN a lobby announces itself once a second by UDP broadcast on port 47301
  (`LanAnnouncer`, a CBOR `LanBeacon`); the join screen lists what `LanFinder` hears (only games of the
  same versions) next to "Type an address…". Android holds a Wi-Fi multicast lock meanwhile. Over the
  internet: by address, or by game code through the relay ([ADR 0028](0028-internet-relay-and-lobby.md)).

## Consequences

- Network play does not depend on cross-platform bit-exactness; determinism still serves recordings,
  tests and replays.
- Menus and rendering do not care whether a match is local or remote.
- Cheating by a modified guest is limited to what its controls can do.
- Not tried yet: a game between two real machines on a LAN, and a desktop host with an outside guest
  (tests and the Android emulator joining this machine only).
