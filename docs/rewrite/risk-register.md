# Risk Register

Each risk has a **status**: *Active* (being mitigated), *Accepted* (known and carried; mitigations
are about detection), or *Closed* (kept briefly for the record).

## Parity regressions while porting rules

- **Status:** Active.
- **Risk:** porting one rule from the C source silently changes another (tick order, shared state in
  `Match`), and the difference is only noticed in play.
- **Mitigations:**
  - Rule tests with expected values from the C source ([testing strategy](testing-strategy.md)).
  - Recorded playthroughs as regression tests (six committed: BEGIN1–3, COMBAT1); replay them after
    every `GAME_RULES_VERSION` bump and re-record the ones that changed. **Gap:** BEGIN4, more
    missions and player-vs-player matches.
  - `IdleScanTest` after rule or import changes, to catch maps that win or lose by themselves (as
    happened with the unsigned goal layer).
  - Compare with DOSBox footage of the original before calling behaviour a bug or a fix.

## Tests only as good as their coverage

- **Status:** Active.
- **Risk:** CI (GitHub Actions, since 2026-10-19) runs every test on every push, but the recordings
  cover only BEGIN1–3 and COMBAT1, and the reference renders eight moments; a regression elsewhere can
  still pass.
- **Mitigations:** add recordings for more missions and player-vs-player modes; watch the nightly idle
  scan and tick benchmark; reference renders on Ubuntu runners must match the ones made locally (Skia
  raster output; if they differ, regenerate them on the runner).

## Server size and coupling

- **Status:** Active.
- **Risk:** the match's rules share one state object, and `Tank` (1,760 lines) holds all vehicle
  rules; changes are hard to review and easy to entangle.
- **Mitigations:** `Match` was split by subsystem into `Match*.kt` files on 2026-10-19 (600 lines
  left in `Match.kt`), with recordings and rule tests proving no behaviour changed. `Tank` is next;
  moving each subsystem's state out of `Match` into its own class can follow when a subsystem is
  reworked.

## Native map format and converters

- **Status:** Active.
- **Risk:** converting the 69 maps to the new format ([ADR 0022](adrs/0022-native-map-format.md)) could
  lose data the importer does not model, and `.MAP` export could write files the original rejects.
- **Mitigations:** keep unknown object bytes in a `legacy` field; round-trip tests for every map;
  exported maps spot-checked in DOSBox; `MAPS/*.MAP` stays in the repository as the reference.

## Editor far behind

- **Status:** Active.
- **Risk:** the in-game editor ([ADR 0021](adrs/0021-editor-in-the-game.md)) is the largest open
  parity block; until it exists, maps can only be fixed through `.MAP` files and the importer.
- **Mitigations:** build it on the native format and the game renderer (phase 4), starting with the
  layers and objects the original editor covers; play-test from the editor to shorten the loop.

## Scope growth (Extended, networking, Android)

- **Status:** Active.
- **Risk:** Extended features (four players, gamepads, networking, Android) pull effort away from
  Classic parity, or leak changes into Classic.
- **Mitigations:** Extended changes only through named ruleset flags; Classic recordings must keep
  passing; the roadmap keeps the core and map work first.

## Toolchain and Compose packaging

- **Status:** Accepted.
- **Risk:** the Kotlin toolchain is pre-1.0; its executable jar is already broken by Compose 1.12's
  jar name clash, and no Gradle plugins (Kover, Compose packaging) are available.
- **Mitigations:** packaging scripts use the resolved class path and `jpackage` directly
  ([ADR 0017](adrs/0017-desktop-packaging.md)); the toolchain version is pinned and upgraded
  deliberately.

## Windows builds depend on one remote builder

- **Status:** Accepted.
- **Risk:** the Windows package is built on a self-hosted BuildKit VM (`win2k22`); its daemon can die
  after a dropped connection.
- **Mitigations:** documented restart; Linux builds do not depend on it.

## Closed

- **Kubriko dependency and its removal:** closed 2026-10-19. No module uses Kubriko; recordings and
  reference renders were unchanged by the removal ([ADR 0019](adrs/0019-plain-kotlin-no-kubriko.md)).
- **Tick-rate drift** (three different rates in code): closed 2026-05 by T01, 100 Hz everywhere
  ([ADR 0002](adrs/0002-fixed-step-100hz.md)).
- **Simulation running at ~30 ticks/s on the UI thread:** closed 2026-10-18 by the simulation thread
  ([ADR 0014](adrs/0014-simulation-thread-and-recordings.md)).
- **55,000 wall actors:** closed 2026-10-06 by the structure grid
  ([ADR 0011](adrs/0011-structure-grid-and-pixel-masks.md)).
- **Missing version fields:** closed by `PROTOCOL_VERSION`, `SCENE_SCHEMA_VERSION` and
  `GAME_RULES_VERSION`.
- **Legacy import coverage:** closed; every shipped map imports with no notes.
- **Map source of truth undecided:** closed 2026-10-19 by [ADR 0003](adrs/0003-legacy-map-import.md)
  (native format; until it lands, regenerate and commit scenes with every importer change).
