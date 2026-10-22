# ADR 0010: Port the Original's Rules from the C Source

- **Status:** Accepted (2026-10-04). This is the definition of the **Classic** ruleset; Extended may
  deviate only through named rule flags ([ADR 0023](0023-classic-and-extended-rulesets.md)).

## Context

Until 2026-09 the rewrite implemented gameplay from descriptions: one set of constants for every
vehicle, hand-written terrain ranges, guessed damage values (main gun 25 instead of 10), A* path
finding instead of the original AI, a 29 × 29 hit box instead of picture hits. Playtests against the
original in DOSBox showed it did not feel like Tank Arena: tanks drove five times too fast, pits and
ramps sat on the wrong tiles, and turrets, rockets and mortars behaved differently.

## Decision

- **The C source in `original/src/` is the authority** for every gameplay rule and number.
  [`original/docs/`](../../original/docs/) describes it in prose and is corrected when it disagrees
  with the source.
- **Port the original's algorithms**, not approximations: `new_velocity()` / `new_position()`
  movement, `hitwall.c` / `overlap.c` structure collisions, `line_obj_hit_point()` shot hits,
  `computer.c` AI, `train.c` rails, and so on. Kotlin code cites the C function or file it ports.
- **Generate data tables from the C source** instead of copying numbers by hand
  (`tools-mapconv extract-vehicles`, `extract-picture-terrain`, `extract-pictures`, `extract-fonts`;
  see [assets.md](../assets.md#tools-mapconv)).
- **Faithful quirks stay.** Behaviour that looks like a bug but is what the original does is kept
  (listed in [parity.md](../parity.md#faithful-quirks-not-bugs)). Before calling playtest behaviour a
  bug, check the C source.
- **Deliberate deviations are listed** in [parity.md](../parity.md#intentional-deviations), each with
  its reason. Everything else that differs from the original is a parity bug.
- Every change that makes the same inputs play out differently bumps `GAME_RULES_VERSION`
  ([ADR 0014](0014-simulation-thread-and-recordings.md)).

## Consequences

- Gameplay can be compared side by side with DOSBox footage of the original.
- The simulation follows the C code's structure in places (fuzzy AI rules, the order inside a step),
  which is less idiomatic Kotlin but easy to check against the source.
- Tests take their expected values from the C source, not from the Kotlin code
  ([testing strategy](../testing-strategy.md)).
