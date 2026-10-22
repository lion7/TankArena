# ADR 0002: Fixed-Step Simulation at 100 Ticks per Second

- **Status:** Accepted (2026-05-06; revised 2026-10-19). Supersedes the first version of this ADR,
  which chose 50 Hz.
- **Related:** [ADR 0014](0014-simulation-thread-and-recordings.md) (threading, determinism,
  recordings).

## Context

The original's timer runs at 100 Hz (`sec 100` in `original/src/include/define.h`, `install_int_ex(game_tick,
BPS_TO_TIMER(sec))` in `original/src/hardware/timer.c`). Every delay, refire time, countdown and AI cadence
in the C source is a number of these ticks (`tinit(x)` / `tdone(x)`). Running the rewrite at the same
rate lets those numbers be copied without conversion.

The first version of this ADR picked 50 Hz, before any gameplay had been ported. A later revision
moved to 100 Hz and also planned 10 Hz snapshot keyframes for replays and the network; that part was
never built and is withdrawn here (replays store inputs instead; see ADR 0014).

## Decision

- The authoritative simulation advances in fixed steps of **10 ms (100 ticks per second)**.
  `FixedStepClock.TICKS_PER_SECOND = 100` and `MILLIS_PER_TICK = 10`
  (`game-protocol/src/nl/tankarena/core/FixedStepClock.kt`).
- Every `*_TICKS` constant in `:game-sim` is in these units and cites the C source it came from.
- The tick rate is independent of the display: the client draws the newest finished tick on every
  display frame. Gameplay never runs faster or slower to match the screen.
- A future network transport chooses its own send rate; it is not tied to the simulation rate.

## Consequences

- C timings port one-to-one (refire `weap.speed`, respawn 300, shield and cloak durations, AI
  re-targeting every 100 + rnd(100), and so on), counted in ticks; they run out on steps (below).
- The client does not interpolate; like the original, the world changes about 33 times a second.
- Where the original derives motion from the elapsed timer (`dt`), the port uses the 0.03 s of a
  step (`STEP_SECONDS`); physics constants are per second.

## The original's game step is not its timer tick

The original's timer ticks at 100 Hz, but `game_control()` does not run every tick:
`twait(np); np = tinit(2);` (`original/src/game/mainloop.c:17-21`) waits until `timer > np` and then sets the
next step 2 ticks later, so steps are at least **3 timer ticks apart (about 33 steps per second)**,
fewer when drawing is slow. Movement uses the elapsed time (`dt`) and every delay uses `tinit()`
timers, so both are independent of the step rate — those port correctly to 100 Hz.

Two things depend on the step rate:

- Rules that change something by a fixed amount **per step**: the guided rocket gains 1 px/s per step
  (`original/src/objects/rocket.c:29`), computer tanks roll their steering noise per step
  (`original/src/tanks/computer.c:715`), wagons close up per step (`original/src/objects/train.c:267`), wrecks explode
  per step. On a 100 Hz tick they run three times as often.
- **Timers** (`tdone(t)` is `timer >= t`) are only looked at on a step, so a `tinit(n)` set on a step
  runs out on the first step at or after `n` ticks: 7 lasts 9, 10 lasts 12, 200 lasts 201. Counted
  per tick, tanks turned about 29% and the chain gun fired about 20% faster than in the original.

**Decision (first version, 2026-10-19):** keep timers and movement on the 100 Hz tick and run only the
per-step rules in a legacy step. **Revised the same day** when the audit found the timer rounding, and
built: the whole game runs in **legacy steps**. `Match.tick()` counts 100 Hz ticks and runs a game
step on every third one (`STEP_TICKS`); the two ticks between leave the world as it is and ignore the
controls, as the original reads them only on a step. In a step every rule runs once, movement covers
`STEP_SECONDS` (0.03 s, the original's `dt`), and count-down timers lose 3 ticks but stop at 0
(`afterStep()`), which gives `tdone()`'s rounding. Both rulesets use it (decided by the user,
2026-10-19: "so it stays true to the original"). Classic rules v12 and Extended v2; every recording
had to be played again.
