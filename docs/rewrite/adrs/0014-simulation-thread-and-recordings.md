# ADR 0014: Simulation Thread, Determinism and Recorded Playthroughs

- **Status:** Accepted (2026-10-06 recordings, commit `509c667`; 2026-10-18 simulation thread,
  commit `e969636`).
- **Changes planned 2026-10-19:** the loop moves from the desktop client into the common `MatchHost`
  with one driver per platform ([ADR 0019](0019-plain-kotlin-no-kubriko.md)); recordings also store the
  ruleset, and each ruleset has its own rules version ([ADR 0023](0023-classic-and-extended-rulesets.md)).
  Recordings of BEGIN1–3 and COMBAT1 are committed (`9f816d5`).
- **Supersedes:** the replay plan of 2026-05 (10 Hz `WorldSnapshot` keyframes, `ReplayProtocol`,
  per-tick checksums; tasks T36–T38).

## Context

Until 2026-10-18 the client ran one tick per `delay(10)` on the UI thread and reached only about 30
ticks per second, so the game ran at a third of the original's speed. Separately, the project needed
regression tests for whole missions that a human played, so that porting one rule does not silently
break another.

## Decision

**Threading**

- A match runs on its own thread (`SimulationLoop`, in `game-server/.../sim/` since 2026-10-19), paced by
  `System.nanoTime()` at exactly 100 ticks per second. When it falls behind it runs up to 10 ticks back
  to back, then drops the backlog, so a long stall slows the game instead of fast-forwarding it.
- Each tick it submits the players' `InputFrame`s, ticks the match, queues the events per player
  window and for audio, and publishes the finished view through an `AtomicReference`. The UI thread
  draws the newest view once per display frame.

**Determinism**

- The server uses no wall-clock time and no global random source. Every random stream is a seeded
  `kotlin.random.Random` (per match from the map size, per tank, per AI, per projectile, and for
  trains, aircraft and structures).
- The same scene, mode, setup and input sequence therefore always produce the same match.

**Recordings**

- `LocalMatchClient(recordAs = …)` records every mission: the setup and the input *changes* per tick
  (`MatchRecorder`, as `PlayerIntentFrame`s), plus the result (won, reason, kills, goal counters,
  tick). No snapshots are stored.
- The client saves each finished mission to `$XDG_DATA_HOME/tankarena/recordings` (default
  `~/.local/share/tankarena/recordings`) as `<mission>-<stamp>-<won|lost|unfinished>.json`.
- Recordings copied into `game-server/testResources/recordings/` are replayed headless by
  `RecordedPlaythroughTest`, which requires the identical result.
- `GAME_RULES_VERSION` (`MatchRecording.kt`, 11 on 2026-10-19) is bumped with every change that makes
  the same inputs play out differently; a recording from another version fails and must be played
  again.

## Consequences

- The game runs at the original's speed regardless of the display rate.
- Whole-mission regression tests come from normal play, with tiny files.
- A rules change can invalidate recordings; replay them first, since many survive a version bump
  unchanged (five v10 recordings replayed identically under v11 and were relabelled). Committed on
  2026-10-19: BEGIN1 ×2, BEGIN2 won and lost, BEGIN3, COMBAT1 lost. BEGIN4 must be played again (v11
  changed its rocket turrets). See the [testing strategy](../testing-strategy.md).
- There is no mid-match divergence check; a failing replay says only that the result differs.
- The simulation thread lives in the client module; a dedicated server or network host will need its
  own loop around `MatchClient`.
