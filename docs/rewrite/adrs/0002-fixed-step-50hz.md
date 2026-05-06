# ADR 0002: Fixed-Step Simulation Tick

- **Status:** Accepted (revised 2026-05-06).
- **Decision:** the authoritative server simulation runs at **100 Hz** (10 ms per tick) to match the legacy game. Replay storage and future networked play decimate snapshots to a lower rate.

## Context

The original game runs a 100 Hz timer: `define.h` defines `sec 100`, and `timer.c` installs `game_tick` with `BPS_TO_TIMER(sec)` — see [`docs/game/mechanics.md`](../../game/mechanics.md). Per-tick cooldowns, respawn delays, projectile TTLs, and turret fire delays in the legacy code are all expressed in 100 Hz units. Running the rewrite at the same rate makes those numbers transfer directly and removes a class of conversion errors when porting parity behavior.

This supersedes the earlier 50 Hz decision. The original ADR picked 50 Hz as a pragmatic mid-rate before the authoritative-server move ([ADR 0007](0007-authoritative-headless-kubriko-server.md)) — at the time the simulation was a pure-Kotlin loop with no replication consumer, so the rate was free to choose. With the authoritative server in place and parity work landing on the server actors, matching the legacy 100 Hz is the lower-friction choice.

## Decision

- **Local simulation: 100 Hz.** `ServerMatchPrototype` advances the headless Kubriko host by 10 ms per tick. All `*_TICKS` constants on server actors are interpreted in 100 Hz units, matching the legacy source.
- **Rendering interpolates, gameplay does not.** The client's render loop is independent of the simulation rate; visual smoothness comes from interpolating between the two most recent `WorldSnapshot`s, not from running gameplay faster.
- **Replay and network tick rates are decimated, not equal to the sim rate.** Snapshot frequency is decoupled from simulation frequency:
  - **Replay storage:** record one full `WorldSnapshot` every 4 ticks (**25 Hz**) plus per-tick `InputFrame`s and `GameEvent`s. The server is deterministic given inputs, so dense snapshots are redundant; 25 Hz keyframes are enough to seek and validate divergence.
  - **Network replication (future):** target **20–30 Hz** snapshot send rate over the wire (every 3–5 sim ticks), with per-tick input frames going server-bound. The exact rate is a tuning knob owned by `RemoteMatchClient` ([ADR 0007](0007-authoritative-headless-kubriko-server.md)); `ReplayProtocol.tickRate` already carries a per-stream rate field.
- **`:game-protocol` exposes the rate, doesn't hardcode it.** `FixedStepClock` carries the simulation rate as a constant; replay headers carry their own decimated rate. Code that reasons about wall time uses these constants rather than literal numbers.

## Consequences

- Legacy parity numbers (cooldowns, delays, TTLs, AI cadences) port directly without a 2× rescale.
- Higher CPU than 50 Hz, but the headless Kubriko tick is cheap and 100 Hz × tens of actors is well within budget on desktop.
- Replay files stay compact: snapshots at 25 Hz + per-tick inputs is roughly the same volume as snapshots at 50 Hz with no inputs, with stronger correctness guarantees.
- Networked play has headroom — the simulation rate is not gated on network jitter, since the wire rate is independently configurable.
- **Code follow-up required.** Three places currently disagree with this decision and need to be reconciled:
  - `game-protocol/.../FixedStepClock.kt`: `TICKS_PER_SECOND = 60` → should be `100`.
  - `game-server/.../ServerMatchPrototype.kt`: `MILLIS_PER_TICK = 33` → should be `10`.
  - All `*_TICKS` constants on server actors should be re-evaluated against the legacy timings now that the rate target is fixed.
