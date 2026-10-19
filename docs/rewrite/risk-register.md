# Risk Register

Each risk has a **status**: *Active* (being mitigated), *Accepted* (known and carried; mitigations
are about detection), or *Closed* (kept briefly for the record).

## Parity regressions while porting rules

- **Status:** Active.
- **Risk:** porting one rule from the C source silently changes another (tick order, shared state in
  `ServerMatchPrototype`), and the difference is only noticed in play.
- **Mitigations:**
  - Rule tests with expected values from the C source ([testing strategy](testing-strategy.md)).
  - Recorded playthroughs as regression tests, re-recorded on every `GAME_RULES_VERSION` bump. **Gap:**
    none are committed yet.
  - `IdleScanTest` after rule or import changes, to catch maps that win or lose by themselves (as
    happened with the unsigned goal layer).
  - Compare with DOSBox footage of the original before calling behaviour a bug or a fix.

## No continuous integration

- **Status:** Active.
- **Risk:** tests are only run by hand; a broken build or test can sit on `rewrite` unnoticed.
- **Mitigations:** add CI (fix the `.*` rule in `.gitignore` first); until then run `./kotlin test`
  before every commit.

## Server size and coupling

- **Status:** Active.
- **Risk:** `ServerMatchPrototype` (3,149 lines) and `ServerTankActor` (1,760 lines) hold most rules;
  changes are hard to review and easy to entangle.
- **Mitigations:** split by subsystem as part of [ADR 0019](adrs/0019-remove-kubriko-from-the-server.md),
  with `GAME_RULES_VERSION` unchanged and recordings plus rule tests proving no behaviour changed.

## Kubriko dependency

- **Status:** Accepted (client and editor), being reduced (server).
- **Risk:** Kubriko is young and maintained by one person. A release can change its serialization
  format (and so the shipped scenes), its threading (the server already polls for asynchronously added
  actors) or its rendering API.
- **Mitigations:**
  - Pin the version in every `module.yaml`; bumps are deliberate and followed by `scene-all` and the
    full test suite.
  - Remove Kubriko from the server ([ADR 0019](adrs/0019-remove-kubriko-from-the-server.md)), which
    also takes the map format out of Kubriko's hands.
  - Kubriko's sprite manager fails if used before the viewport exists; load early images directly.

## Map source of truth undecided

- **Status:** Active.
- **Risk:** with both `MAPS/*.MAP` and the committed scenes in play
  ([ADR 0003](adrs/0003-legacy-map-import.md)), a hand edit of a scene is lost at the next
  `scene-all`, and an importer change without regeneration leaves stale scenes.
- **Mitigations:** never hand-edit generated scenes; regenerate and commit with every importer change;
  add the scene-freshness test; decide ADR 0003 together with the new map format.

## Editor far behind

- **Status:** Active.
- **Risk:** the editor edits four object types and no tile layers, so parity for the editor is the
  largest open block, and maps can only be changed through `.MAP` files.
- **Mitigations:** extend it per [ADR 0009](adrs/0009-editor-wraps-kubriko-scene-editor.md); keep the
  boundary guard so it never runs the simulation.

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

- **Tick-rate drift** (three different rates in code): closed 2026-05 by T01, 100 Hz everywhere
  ([ADR 0002](adrs/0002-fixed-step-100hz.md)).
- **Simulation running at ~30 ticks/s on the UI thread:** closed 2026-10-18 by the simulation thread
  ([ADR 0014](adrs/0014-simulation-thread-and-recordings.md)).
- **55,000 wall actors:** closed 2026-10-06 by the structure grid
  ([ADR 0011](adrs/0011-structure-grid-and-pixel-masks.md)).
- **Missing version fields:** closed by `PROTOCOL_VERSION`, `SCENE_SCHEMA_VERSION` and
  `GAME_RULES_VERSION`.
- **Legacy import coverage:** closed; every shipped map imports with no notes (but see the map source
  question above).
