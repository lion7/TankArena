# Risk Register

Each risk has a **status**: *Active* (being mitigated), *Accepted* (known and carried; mitigations are
about detection), or *Closed* (kept briefly for the record).

## Parity regressions without recordings

- **Status:** Active.
- **Risk:** porting or changing one rule silently changes another (step order, shared state in
  `Match`), and the difference is only noticed in play. Since rules v12 no whole-mission recording is
  replayed in the tests, so that net is missing today.
- **Mitigations:**
  - Rule tests with expected values from the C source ([testing strategy](testing-strategy.md)).
  - Play new recordings (BEGIN1–4, more missions, two-vs-computer, player vs player) and replay them
    after every rules version bump ([roadmap](roadmap.md#1-safety-net)).
  - `IdleScanTest` nightly, to catch maps that win or lose by themselves.
  - Compare with DOSBox footage of the original before calling behaviour a bug or a fix.

## Extended changes leaking into Classic

- **Status:** Active.
- **Risk:** Extended features (four players, mouse, network, smooth motion) change Classic's play or
  look by accident.
- **Mitigations:** Extended rule changes only through named `RuleFlags`, off in Classic; Extended
  screen options only apply when the ruleset is Extended; Classic recordings and the reference
  renders (all Classic) must keep passing.

## Simulation size and coupling

- **Status:** Active.
- **Risk:** the match's rules share one state object, and `Tank` (1,965 lines) holds all vehicle
  rules; changes are hard to review and easy to entangle.
- **Mitigations:** `Match` is split by subsystem into `Match*.kt` files; `Tank` is next
  ([roadmap](roadmap.md#8-code-health)); rule tests guard behaviour.

## Exported maps the original rejects

- **Status:** Active.
- **Risk:** the export writes object records from the original editor's defaults plus the modelled
  fields; a field the rewrite does not model could make an edited or new map play differently in the
  original, or be refused.
- **Mitigations:** every original map exports byte for byte (tested); imported maps keep their
  unmodelled bytes in the `legacy` section; the [manual checklist](editor-test-checklist.md) tries
  exported maps in DOSBox (not done yet).

## Network play untried on real networks

- **Status:** Active.
- **Risk:** LAN discovery, NAT, Wi-Fi and latency behave differently between real machines than in
  tests and between the emulator and its host.
- **Mitigations:** a two-machine LAN game and an internet game through the public relay before calling
  network play finished; own-vehicle prediction only if needed.

## The public relay

- **Status:** Accepted.
- **Risk:** `wss://relay.tankarena.nl` is a public service run by the project; abuse (code guessing,
  many tunnels) or an outage stops internet games for everyone using the default.
- **Mitigations:** codes refused after five wrong guesses a minute per address (taken from
  `X-Forwarded-For` behind the proxy, which drops a client's own header), at most three tunnels per
  game, no stored data; players can set their own relay; LAN and direct play do not need it.

## Toolchain and Compose packaging

- **Status:** Accepted.
- **Risk:** the Kotlin toolchain is pre-1.0; its executable jar is broken by Compose 1.12's jar name
  clash, and no Gradle plugins (Kover, Compose packaging) are available.
- **Mitigations:** packaging scripts use the resolved class path and `jpackage` directly
  ([ADR 0017](adrs/0017-desktop-packaging.md)); the toolchain version is pinned and upgraded
  deliberately.

## Windows builds depend on one remote builder

- **Status:** Accepted.
- **Risk:** the Windows package is built on a self-hosted BuildKit VM (`win2k22`); its daemon can die
  after a dropped connection.
- **Mitigations:** documented restart; Linux and Android builds do not depend on it.

## Closed

- **The game engine dependency:** closed 2026-10-19; plain Kotlin everywhere, with recordings and
  reference renders unchanged ([ADR 0019](adrs/0019-plain-kotlin-no-game-engine.md)).
- **Editor far behind:** closed 2026-10-20; the in-game editor does everything the original's does
  ([ADR 0021](adrs/0021-editor-in-the-game.md)).
- **Map source of truth undecided:** closed 2026-10-19 ([ADR 0003](adrs/0003-legacy-map-import.md)).
- **Tick-rate drift** (three different rates in code): closed 2026-05, 100 Hz everywhere
  ([ADR 0002](adrs/0002-fixed-step-100hz.md)).
- **Simulation at about 30 ticks a second on the UI thread:** closed 2026-10-18 by the simulation
  thread ([ADR 0014](adrs/0014-simulation-thread-and-recordings.md)).
- **55,000 wall objects:** closed 2026-10-06 by the structure grid
  ([ADR 0011](adrs/0011-structure-grid-and-pixel-masks.md)).
- **Missing version fields:** closed by the rules, protocol, network and map format versions.
- **Legacy import coverage:** closed; every shipped map imports with no notes.
