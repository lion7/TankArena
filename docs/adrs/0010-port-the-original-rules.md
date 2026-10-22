# ADR 0010: Port the Original's Rules from the C Source

- **Status:** Accepted (2026-10-04; first commit `c57dcbb`, then `509c667` onwards).
- **Scope since 2026-10-19:** this is the definition of the **Classic** ruleset. Extended may deviate,
  but only through named ruleset flags ([ADR 0023](0023-classic-and-extended-rulesets.md)).
- **Supersedes:** the "parity task" approach of 2026-05 (tasks T01–T43, deleted; see
  [history](../history.md)).

## Context

Until 2026-09 the rewrite implemented gameplay from descriptions: one set of tank constants for every
vehicle, hand-written terrain ranges, guessed damage values (main gun 25 instead of 10), A* path
finding instead of the original AI, a 29×29 hit box instead of picture hits. The first playtests
against the original (DOSBox footage) showed the game did not feel like Tank Arena: tanks drove five
times too fast, pits and ramps sat on the wrong tiles, and turrets, rockets and mortars behaved
differently.

## Decision

- **The C source in `original/src/` is the authority** for every gameplay rule and number. `original/docs/`
  describes it in prose and is corrected when it disagrees with the source.
- **Port the original's algorithms**, not approximations: `new_velocity()`/`new_position()` movement,
  `hitwall.c`/`overlap.c` structure collisions, `line_obj_hit_point()` shot hits, `computer.c` AI,
  `train.c` rails, and so on. Kotlin code cites the C function or file it ports.
- **Generate data tables from the C source** instead of copying numbers by hand:
  `tools-mapconv extract-vehicles` (`original/src/data/tanks.c` → `GeneratedLegacyVehicles.kt`),
  `extract-picture-terrain` and `extract-pictures` (`original/src/data/pictures.c`), `extract-fonts`
  (`original/src/graph/5x5font.s`).
- **Faithful quirks stay.** Behaviour that looks like a bug but is what the original does is kept, for
  example: in player vs player every start pad of a player's vehicle kind spawns a tank and all of
  them follow that player's keys; stacked computer tanks that overlap at start blow each other up
  (FOREST, NIRVANA, NUKEBOMB, Q2, RACE_10, SUBURBS); BASE1's wagons never move. Before calling
  playtest behaviour a bug, check the C source.
- **Deliberate deviations are listed** in [parity.md](../parity.md#intentional-deviations), each with
  the reason. Everything else that differs from the original is a parity bug.
- Every change that makes the same inputs play out differently bumps `GAME_RULES_VERSION`
  ([ADR 0014](0014-simulation-thread-and-recordings.md)); it is 11 as of 2026-10-19.

## Consequences

- Gameplay matches the original closely enough to compare side by side with DOSBox footage.
- The server code follows the C code's structure in places (fuzzy AI rules, tick order inside a
  step), which is less idiomatic Kotlin but easy to check against the source.
- Unit tests take their expected values from the C source, not from the Kotlin code
  ([testing strategy](../testing-strategy.md)).
