# ADR 0014: Simulation Thread, Determinism and Recorded Playthroughs

- **Status:** Accepted (recordings 2026-10-06, simulation thread 2026-10-18). Replaces the 2026-05
  plan of 10 Hz snapshot keyframes for replays.
- **Related:** [ADR 0002](0002-fixed-step-100hz.md) (ticks and steps),
  [ADR 0023](0023-classic-and-extended-rulesets.md) (a rules version per ruleset).

## Context

Until 2026-10-18 the client ran one tick per `delay(10)` on the UI thread and reached only about 30
ticks a second, so the game ran at a third of the original's speed. Separately, the project needed
regression tests for whole missions played by a person, so that porting one rule does not silently
break another.

## Decision

**Threading**

- A match runs on its own thread (`SimulationLoop`, `:game-sim`; a network host runs its
  `HostedMatch` the same way), paced by `System.nanoTime()` at exactly 100 ticks a second. When it
  falls behind it runs up to `MAX_CATCH_UP` ticks back to back, then drops the backlog, so a long
  stall slows the game instead of fast-forwarding it.
- Each tick it submits the players' `InputFrame`s, ticks the match, queues the events per player
  window and for audio, and publishes the finished frame through an `AtomicReference`. The UI thread
  draws the newest frame on every display frame.

**Determinism**

- The simulation uses no wall-clock time and no global random source. Every random stream is a seeded
  `kotlin.random.Random` (per match from the map size, per tank, per AI, per projectile, and for
  trains, aircraft and structures). All trigonometry goes through `DetMath` (`StrictMath`), which
  gives the same results on every JVM.
- The same map, mode, ruleset, setup and input sequence therefore always produce the same match.

**Recordings**

- `LocalMatchClient(recordAs = …)` records every match except an editor play-test: the setup (map,
  mode, ruleset and its rules version, cheats, number of players) and the input *changes* per tick as
  `PlayerIntentFrame`s, plus the result (won, reason, kills, goal counters, tick). No snapshots are
  stored.
- The game saves each finished match under `recordings/` in the player's data folder
  (`~/.local/share/tankarena/recordings` on the desktop) as `<mission>-<stamp>-<outcome>.json`.
  Extended's replay viewer plays them back (`ReplayMatchClient`).
- Recordings copied into `game-sim/testResources/recordings/` are replayed headless by
  `RecordedPlaythroughTest`, which requires the identical result.
- `GAME_RULES_VERSION` (Classic) and `EXTENDED_RULES_VERSION` are bumped with every change that makes
  the same inputs play out differently. A test recording of another version fails until it is
  replayed under the new version and either relabelled (same result) or played again.

## Consequences

- The game runs at the original's speed whatever the display rate.
- Whole-mission regression tests come from normal play, in small files.
- A rules change can invalidate recordings. The six committed recordings were made under Classic v11
  and are kept in `recordings/v11/` as input for the reference renders only; new ones have to be
  played ([roadmap](../roadmap.md)).
- There is no mid-match divergence check: a failing replay only says that the result differs.
- Network games do not depend on determinism across machines: one host simulates
  ([ADR 0024](0024-networking.md)).
