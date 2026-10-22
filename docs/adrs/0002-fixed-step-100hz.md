# ADR 0002: 100 Ticks per Second, One Game Step Every Third Tick

- **Status:** Accepted (2026-05-06; the legacy step 2026-10-19).
- **Related:** [ADR 0014](0014-simulation-thread-and-recordings.md) (thread, determinism,
  recordings).

## Context

The original's timer runs at 100 Hz (`sec 100` in `original/src/include/define.h`,
`install_int_ex(game_tick, BPS_TO_TIMER(sec))` in `original/src/hardware/timer.c`). Every delay,
refire time, countdown and AI cadence in the C source is a number of these ticks (`tinit(x)` /
`tdone(x)`).

But `game_control()` does not run on every tick: `twait(np); np = tinit(2);`
(`original/src/game/mainloop.c:17-21`) waits until `timer > np` and sets the next step 2 ticks later,
so game steps are at least **3 timer ticks apart, about 33 a second**. Two things depend on that:

- Rules that change something by a fixed amount **per step**: the guided rocket gains 1 px/s per step
  (`original/src/objects/rocket.c:29`), computer tanks roll their steering noise per step
  (`original/src/tanks/computer.c:715`), wagons close up per step (`original/src/objects/train.c:267`),
  wrecks explode per step. Run on every 100 Hz tick, they happen three times as often.
- **Timers** (`tdone(t)` is `timer >= t`) are only looked at on a step, so a `tinit(n)` set on a step
  runs out on the first step at or after `n` ticks: 7 lasts 9, 10 lasts 12, 200 lasts 201. Counted
  per tick, tanks turned about 29 % faster and the chain gun fired about 20 % faster than in the
  original.

## Decision

- The simulation counts time in **ticks of 10 ms** (`FixedStepClock.TICKS_PER_SECOND = 100`,
  `game-protocol/src/nl/tankarena/core/FixedStepClock.kt`). Every `*_TICKS` constant in `:game-sim`
  is in these units and cites the C source it came from.
- The game itself runs in **legacy steps**: `Match.tick()` runs a game step on every third tick
  (`STEP_TICKS`, `LegacyStep.kt`). The two ticks between leave the world as it is. In a step every
  rule runs once, in `game_control()`'s order; movement covers `STEP_SECONDS` (0.03 s, the
  original's `dt`); count-down timers lose 3 ticks but stop at 0 (`afterStep()`), which reproduces
  `tdone()`'s rounding.
- Keys pressed between steps are latched and count on the next step, as the original's keyboard
  handler keeps them.
- Both rulesets use the legacy step (decided 2026-10-19, "so it stays true to the original").
- The tick rate is independent of the display. Classic draws the newest step on every display frame;
  Extended can draw between steps (smooth motion, [ADR 0023](0023-classic-and-extended-rulesets.md)).
  A network transport sends one frame per step ([ADR 0024](0024-networking.md)).

## Consequences

- C timings port one to one (refire `weap.speed`, respawn 300, shield and cloak durations, AI
  re-targeting every 100 + rnd(100)), and they run out on steps exactly as in the original.
- Like the original, the world changes about 33 times a second.
- Introducing the legacy step (Classic rules v12) made every earlier recording obsolete.
