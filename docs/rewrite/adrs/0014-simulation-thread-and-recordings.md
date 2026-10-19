# ADR 0014: Simulation Thread, Determinism and Recorded Playthroughs

- **Status:** Accepted (2026-10-06 recordings, commit `509c667`; 2026-10-18 simulation thread,
  commit `e969636`).
- **Supersedes:** the replay plan of 2026-05 (10 Hz `WorldSnapshot` keyframes, `ReplayProtocol`,
  per-tick checksums; tasks T36–T38).

## Context

Until 2026-10-18 the client ran one tick per `delay(10)` on the UI thread and reached only about 30
ticks per second, so the game ran at a third of the original's speed. Separately, the project needed
regression tests for whole missions that a human played, so that porting one rule does not silently
break another.

## Decision

**Threading**

- A match runs on its own thread (`SimulationLoop` in `game-client/.../app/desktop/`), paced by
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
- A rules change invalidates all recordings. As of 2026-10-19 none are committed (the directory holds
  only its README), so this safety net is not in use yet; see the [testing strategy](../testing-strategy.md).
- There is no mid-match divergence check; a failing replay says only that the result differs.
- The simulation thread lives in the client module; a dedicated server or network host will need its
  own loop around `MatchClient`.
