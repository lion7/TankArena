# ADR 0002: Fixed-Step Simulation at 100 Ticks per Second

- **Status:** Accepted (2026-05-06; revised 2026-10-19). Supersedes the first version of this ADR,
  which chose 50 Hz.
- **Related:** [ADR 0014](0014-simulation-thread-and-recordings.md) (threading, determinism,
  recordings).

## Context

The original's timer runs at 100 Hz (`sec 100` in `src/include/define.h`, `install_int_ex(game_tick,
BPS_TO_TIMER(sec))` in `src/hardware/timer.c`). Every delay, refire time, countdown and AI cadence
in the C source is a number of these ticks (`tinit(x)` / `tdone(x)`). Running the rewrite at the same
rate lets those numbers be copied without conversion.

The first version of this ADR picked 50 Hz, before any gameplay had been ported. A later revision
moved to 100 Hz and also planned 10 Hz snapshot keyframes for replays and the network; that part was
never built and is withdrawn here (replays store inputs instead; see ADR 0014).

## Decision

- The authoritative simulation advances in fixed steps of **10 ms (100 ticks per second)**.
  `FixedStepClock.TICKS_PER_SECOND = 100` and `MILLIS_PER_TICK = 10`
  (`game-protocol/src/com/tankarena/core/FixedStepClock.kt`).
- Every `*_TICKS` constant in `:game-server` is in these units and cites the C source it came from.
- The tick rate is independent of the display: the client draws the newest finished tick on every
  display frame. Gameplay never runs faster or slower to match the screen.
- A future network transport chooses its own send rate; it is not tied to the simulation rate.

## Consequences

- C timings port one-to-one (refire `weap.speed`, respawn 300, shield and cloak durations, AI
  re-targeting every 100 + rnd(100), and so on).
- The client does not interpolate between ticks; at 100 ticks per second the steps are smaller than a
  display frame, so none is needed.
- Where the original derived motion from the elapsed timer (`dt`) rather than per tick, the port uses
  the fixed 10 ms step; physics constants are expressed per second and multiplied by 0.01 s.

## The original's game step is not its timer tick

The original's timer ticks at 100 Hz, but `game_control()` does not run every tick:
`twait(np); np = tinit(2);` (`src/game/mainloop.c:17-21`) waits until `timer > np` and then sets the
next step 2 ticks later, so steps are at least **3 timer ticks apart (about 33 steps per second)**,
fewer when drawing is slow. Movement uses the elapsed time (`dt`) and every delay uses `tinit()`
timers, so both are independent of the step rate — those port correctly to 100 Hz.

Rules that change something by a fixed amount **per step** do not: for example the guided rocket gains
1 px/s per step (`src/objects/rocket.c:29`) and a rotor with `auto_rotation` 0 turns one direction per
step. Ported one-to-one onto the 100 Hz tick they run about three times faster (the rewrite's rocket
reaches 150 px/s in 1.5 s instead of about 4.5 s). Such rules must be paced at one step per 3 ticks.
Finding and fixing them is open parity work ([parity.md](../parity.md#engine-and-game-loop)).
