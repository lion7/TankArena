# ADR 0028: Internet Play Through a Relay and Lobby Server

- **Status:** Accepted and built (2026-10-20). Step 3 of phase 8 in the [roadmap](../roadmap.md);
  builds on [ADR 0024](0024-networking.md). Built as designed, with these choices: the relay
  server is `RelayServer` in `:game-net` (so one test runs relay, host and guest together) and
  `:app-relay` only starts it; TLS is left to a proxy in front of the relay (Caddy, nginx), the
  game connects with `wss://` when the relay address says so; `app-server --relay` publishes a
  dedicated server's games too.

## Context

LAN play works (ADR 0024): a host listens on port 47300, guests connect to it by address or from the
LAN list, frames are deltas deflated to about 3–18 KB/s per guest. Over the internet this breaks
down in two places:

- **Reaching the host.** Most hosts sit behind a home router (NAT), many behind carrier-grade NAT or a
  mobile network; nobody outside can connect to them without port forwarding, which most players
  cannot or will not set up.
- **Finding a game.** UDP broadcast only reaches the local network, and an IP address is awkward to
  pass on.

The game's connection is a WebSocket over TCP. `GameServer.serve()` already takes any
`DefaultWebSocketSession`: it does not care whether the host accepted the connection or opened it.

## Decision

A small public service, `app-relay`, does two jobs: a **lobby** where hosts list their games and
guests find them by a short code, and a **relay** that carries a guest's connection to a host that
cannot be reached directly. The game messages pass through unchanged; the relay never reads them.

### The pieces

```text
  guest ──WebSocket──► relay ◄──WebSocket── host (tunnel per guest)
                         ▲
                         └──────WebSocket── host (control connection)
```

- **Control connection.** A host that publishes its game opens one WebSocket to the relay
  (`/host`) and registers: name, map, players and maximum, the three versions, public or private.
  The relay answers with a game code of six letters and digits without look-alikes, shown as
  `K7Q-4MX`. The host updates the player count as the lobby changes and closes the connection when
  the game ends; the code dies with it.
- **Join.** A guest connects to the relay at `/join/<code>`. The relay tells the host over its
  control connection: `incoming <tunnel id>`.
- **Tunnel.** The host opens a second WebSocket to the relay at `/tunnel/<tunnel id>` and hands it to
  `GameServer.serve()` exactly as if the guest had connected directly. From then on the relay copies
  every binary message from one side to the other. Each hop negotiates its own deflate.
- **Game list.** `GET /games` gives the public games (name, map, players, versions) as JSON; the join
  screen shows them under the LAN games, filtered by version. Private games are only reached by
  their code.

### In the game

- **Hosting:** the lobby gets a choice "Also on the internet": the host opens the control connection
  and shows the game code next to its LAN addresses. Direct LAN joins keep working at the same time.
- **Joining:** "Join a game" lists LAN games, then internet games, then "Type an address or a code…".
  A typed code goes through the relay; an address connects directly as now.
- **The relay's address** is a setting with a default; a group can run its own relay.

### The relay itself

- Ktor CIO, a few hundred lines, a `jvm/app` module next to `app-server`; it shares `game-net`'s
  version constants and nothing else.
- `wss://` (TLS) on the public side; the game's own LAN connections stay plain `ws://`.
- State is in memory only: games by code, open tunnels. A restart drops the games and their players,
  as a host leaving does.
- Limits per game: at most `MAX_NET_PLAYERS - 1` tunnels; codes are refused after five wrong guesses
  from one address per minute; a tunnel that is not picked up by the host within 10 s is closed.
- Bandwidth: 3–18 KB/s per guest each way through the relay; 100 games with three guests each is
  about 5 MB/s, within one small virtual machine.

### The protocol

The control connection carries small JSON messages; game traffic stays in the existing CBOR messages
inside the tunnels.

```text
host  → relay   register {name, map, players, maxPlayers, netVersion, protocolVersion, rulesVersion, public}
relay → host    registered {code}
host  → relay   update {players}
relay → host    incoming {tunnel}
relay → host    closed {reason}              (relay shutting down, game expired)
guest → relay   GET /join/<code>             upgrades to the WebSocket that becomes the tunnel
host  → relay   GET /tunnel/<tunnel>         the host's end of that tunnel
```

## Alternatives considered

- **Port forwarding or UPnP/NAT-PMP.** No server needed, but routers often refuse it, carrier-grade
  NAT makes it impossible, and opening ports from a game is a security risk. Kept only as "join by
  address" for those who set it up themselves.
- **UDP hole punching or WebRTC data channels.** Direct paths and lower latency, but it needs a
  different transport (UDP with its own reliability, or WebRTC with STUN/TURN), still fails behind
  symmetric NAT, and so still needs a relay (TURN) for some players. Too much for a 33-steps-a-second
  game whose frames already arrive 100 ms late on purpose.
- **A game server in the cloud for every match.** `app-server` can already be run anywhere, and stays
  the answer for groups that want one; but a host in the game itself should not need a server
  rented by anyone.

## Consequences

- One extra hop: about 10–60 ms more delay, inside the 100 ms playback buffer; own-vehicle prediction
  (ADR 0024) is only added if play over the relay shows it is needed.
- Someone has to run the default relay; it holds no accounts and no data worth stealing, and a
  group can run its own.
- `GameServer` stays as it is: tunnels are sessions like any other.
- The relay could read the game messages it copies (they are not encrypted end to end); it does not
  parse or keep them. Connections to the relay use `wss://` so nobody else on the way can.
