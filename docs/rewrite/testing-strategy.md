# Testing Strategy

This document says how Tank Arena is tested and how an AI agent fits into that.

It covers:

- what to test, at which layer, and with which tool;
- what runs in CI and when;
- where an AI agent helps and where it does not.

The parity target is the original game, described in [`docs/game/`](../game/) and the C source in `src/`.

## 1. Principles

**1. Test the simulation, not the pixels.**

- All gameplay truth lives in `:game-server`. That module is headless, runs at 100 Hz, and is driven only by `InputFrame`s.
- Every gameplay rule can be checked by stepping ticks and reading `WorldSnapshot`, `PlayerView` and `GameEvent`s. No window, GPU or screenshot is needed.
- Most tests, including the ones an AI agent runs, belong here.

**2. Use the protocol seam as the automation interface.**

- `MatchClient` is the boundary between "a player" and "the game":
  - `submitInput(InputFrame)` sends input;
  - `tick()` returns a `ServerFrame`.
- Unit tests, bots, replays and AI agents should all drive the game through this one seam. They should not synthesise mouse clicks on a canvas.

**3. Use the original game as the oracle.**

- Expected numbers come from the C source (`src/data/tanks.c`, `src/include/define.h`, …) or from `docs/game/`. They do not come from "what the Kotlin code currently does".
- Each parity test cites its source line.

**4. Tests must be deterministic.**

- The server must produce identical output for identical input.
- A flaky test is a bug in the code or in the test, never "infrastructure".
- Example: a Kubriko race once made `update()` skip newly added actors. It was fixed in `ServerMatchPrototype.awaitDynamicActorsSettled`, not retried away.

**5. Every bug found by any method becomes a regression test** at the lowest layer that reproduces it.

## 2. Assessment of the two "AI plays the game" approaches

| | Method A: visual agent (AppAgent-style, screenshots → VLM → clicks) | Method B: bridge server inside the app |
|---|---|---|
| Fit for this game | Poor as a primary tool. The playfield is a Kubriko canvas updated 100×/s. A VLM loop takes seconds per step, so it cannot steer a tank in real time. Its results are non-deterministic and costly to run. AppAgent targets Android via ADB; the game is desktop-first. | Good, but **not** through the Compose semantics tree. Menus and HUD have semantics; the arena (tanks, bullets, terrain) is drawn on a canvas and is invisible to it. |
| What we adopt | A narrow role: an occasional **exploratory and visual sanity pass** over screenshots (§3.7). It is never a gate. | A **debug-only Match Control API** at the protocol seam (§3.5). It exposes inputs, ticks, snapshots, events and rendered frames. Compose UI testing is used only for menus and HUD (§3.6). |

> `compose-ui-test-server` / `COMPOSE_UI_TEST_SERVER_ENABLED` is taken from the source notes and has not been evaluated here. Whatever tool drives Compose, it can only see semantics nodes, so it cannot observe gameplay.

## 3. Test layers

### 3.1 Rule tests: `:game-server`, every commit

These are small fixture maps built with `CanonicalMapDefinition` (see `ControlAndObjectivesTest`, `ServerTankFireTest`). Each test pins one rule.

| Area | Example assertions | Oracle |
|---|---|---|
| Weapons | refire ticks, TTL, damage, ammo per vehicle | `tanks.c` `weap.speed/num/power` |
| Damage model | shield → armor routing, invulnerability, wreck, respawn delay | `src/tanks/tank.c` |
| Goal counter | contributions, −100 on last human tank, 100% wins | `objects.c` `modify_goal_counter` |
| Control | group control, hand-over, F1–F10 | `src/tanks/control.c` |
| AI | activation radius, `attack_tanks == 0`, no path → hold | `src/tanks/computer.c` |
| Terrain | lava, water, pit zones, speed factors | `src/check/pit.c`, `pictures.c` |

Conventions:

- Name tests after the behaviour.
- Put the C reference in a comment.
- Never use `queueDamage*ForTest` where real projectiles can produce the same state within about 1,000 ticks.

### 3.2 Content tests: `:game-content` and `:game-server`, every commit

- **Import:** `AllShippedMapsImportCleanTest` checks that every `.MAP` imports with no notes. `RACE3.MAP` is a known failure that should be fixed.
- **Shipped scenes:** add `EveryShippedSceneBootsTest`. For each scene in `scenes/index.txt` and each mode allowed by its map type:
  - boot it;
  - assert the expected player tanks exist;
  - tick 200 ticks with idle input;
  - assert no exception and the invariants in §3.4.
  - Budget: under 2 minutes in total.
- **Scene freshness:** regenerate scenes into a temp dir and diff them against the committed `resources/scenes`. A non-empty diff means someone changed the importer without regenerating. Delete this test once T07 removes the importer.
- **Parity pool golden facts** (`parity-fixtures.md`): BEGIN1, FLAGISLE, RACE1, MISSION1, FLAGRACE and 3 VS 3 get one test each. Each test pins the authored inventory: count of player tanks per mode, enemies, turrets, goals, and the sum of goal contributions.

### 3.3 Deterministic replays: `:game-server`, every commit

This builds on T36–T38. It is the backbone for the regression and AI layers.

**Format.** `ReplayHeader` + per-tick `InputFrame`s + a snapshot hash every 10 ticks. It is stored as JSON lines under `game-server/test/resources/replays/`.

**Snapshot hash.** SHA-256 over a canonical encoding of `WorldSnapshot.actors`:

- sorted by `actorId`;
- floats rounded to 1e-3;
- events excluded.

**Recorder.** A `RecordingMatchClient` wraps `LocalMatchClient`. The desktop client turns it on with `-Dtankarena.record=<file>`, so human play sessions become test assets.

**Test.** `ReplayRegressionTest` replays every file and compares hashes. The first divergent tick is reported with a diff of the two snapshots.

**Updating goldens.** This is explicit (`-Dtankarena.updateReplays=true`) and reviewed in the PR. A behaviour change shows up as changed replay files, which makes it visible.

**Pre-requisite.** Determinism must be audited first:

- no `Random` without a seed taken from the replay header;
- no wall-clock reads;
- no iteration over `IdentityHashMap`/`HashMap` where order affects outcomes;
- collision resolution order fixed by `actorId`.

Add `DeterminismTest`: run the same replay twice in one JVM and in two fresh JVMs, and assert identical hashes.

### 3.4 Bots, soak and fuzzing: `:game-server`, nightly

These are scripted, not LLM-driven, so they are fast and reproducible.

**Bots** (`game-server/test/.../bots/`):

- `IdleBot`
- `RandomInputBot(seed)`
- `HunterBot`: drives with the existing A* and LOS toward the nearest enemy and fires.
- `ObjectiveBot`: goes for goals with positive contribution.

**Invariants** are checked every tick:

- no NaN or infinite positions;
- tanks inside world bounds;
- `armor` within `0..maxArmor`;
- ammo never negative;
- actor count does not grow without bound (no leaked projectiles or mines);
- every `MissionResult` is emitted exactly once;
- tick duration under 5 ms at typical density, which is the T42 performance budget.

**Soak.** For each shipped scene, run `RandomInputBot` with 20 seeds × 30,000 ticks (5 game-minutes). A failure prints a seed and tick, which are converted into a replay file (§3.3).

**Winnability.** `HunterBot`/`ObjectiveBot` must win BEGIN1–BEGIN4 and MISSION1–3 within 10 game-minutes. A mission that becomes unwinnable after a change is a regression. The CAR-only BEGIN maps caught exactly this kind of issue: dormant AI and goal counters.

**Balance telemetry**, nightly and recorded but not asserted:

- time-to-win per mission;
- player deaths per mission;
- shots per kill.

Trend these against the original's feel when tuning `VehicleSpec`.

### 3.5 Match Control API for AI agents: debug builds only

The precise "bridge server", placed at the right seam.

**Where.** A new `tools-matchctl` module (or `:game-server` `debug` package) that wraps `ServerMatchPrototype` and `MatchClient`. It is never linked into the release client.

**Transport.** An **MCP server over stdio** is the primary transport. A coding agent such as Claude Code can then call it as a tool with no network exposure. An optional HTTP mode binds to `127.0.0.1` only. It is disabled unless `TANKARENA_MATCHCTL=1`, and it requires a random token printed at start-up.

**Tools / endpoints:**

| Call | Effect |
|---|---|
| `list_scenes()` | base names, mode compatibility, mission codes |
| `start(scene, mode, seed)` | creates a match and returns `matchId` and the initial `ServerFrame` |
| `step(matchId, inputs, ticks)` | applies the `InputFrame`s for N ticks and returns the summarised frame plus all events |
| `snapshot(matchId, filter)` | full or filtered `WorldSnapshot` (e.g. tanks only) |
| `render(matchId, playerId)` | PNG of the player's view, rendered offscreen (see §3.6) |
| `save_replay(matchId)` / `load_replay(file)` | exports the session as a §3.3 replay |
| `assert(matchId, expr)` | evaluates a small predicate (e.g. `tank(player0).armor > 0`) for agent-written checks |

**The AI loop.** The agent plays or probes the game with `step`/`snapshot`. It works at full simulation speed, not wall-clock speed. When it finds a defect it:

1. saves the replay;
2. writes a failing rule test (§3.1) or a replay test (§3.3);
3. fixes the code;
4. re-runs.

The agent's output is **tests in the repo**, never a pass or fail verdict that cannot be reproduced.

**Good agent tasks:**

- "Find a way to die on MISSION1 that doesn't involve enemy fire."
- "Can the player get stuck on any BEGIN map?"
- "Compare weapon timings with `tanks.c` and report mismatches as failing tests."

### 3.6 Client and UI tests: `:game-client`, every commit

**Pure logic, unit tests.** `AudioManager` math (exists), `DesktopControls` key mapping, `MissionCatalog.playableIn`, HUD formatting (`percent`, `formatTime`, weapon labels). Make these `internal` rather than testing them through reflection.

**Compose UI tests** use `runComposeUiTest` from `ui-test-junit4` / `compose.uiTest`. They cover menus and HUD only:

- the main menu goes to mode select, then mission select, then gameplay;
- Esc returns to the menu;
- the debrief shows `MissionResult` values;
- the mission list is filtered per mode (PvP lists only DUAL/DONT_CARE maps);
- the HUD text matches a given `HudState`.

Give the menu items and HUD gauges `testTag`s so tests and agents can address them.

**Render snapshots (golden images).** Render `ClientScene` for a fixed `WorldSnapshot` offscreen, using Compose Desktop's `ImageComposeScene` or a Kubriko offscreen render if the viewport needs its own loop. Compare against PNGs in `game-client/test/resources/golden/` with a small per-pixel tolerance. Cases:

- the BEGIN1 start;
- an explosion mid-animation;
- a wrecked tank;
- split-screen layout;
- each object sprite family.

The same offscreen renderer backs `render()` in §3.5.

**Desktop smoke.** Launch the real app under `xvfb-run` with `-Dtankarena.autostart=begin1` (a small dev flag to add), run 10 seconds, then take a screenshot with `java.awt.Robot`. Assert:

- the process is alive;
- no exception in the log;
- the screenshot is not mostly black.

This catches runtime linkage problems that compile-time checks miss, such as Compose/Kubriko version mismatches.

### 3.7 Visual agent pass: exploratory, optional, weekly

This is Method A in its useful role.

1. Feed a VLM the §3.6 screenshots, plus reference screenshots of the **original game running in DOSBox** for the same map and moment.
2. Ask it for discrepancies: missing sprites, wrong layering, HUD layout, palette, night-map lighting.
3. File the output as issues for a human to triage. It is never a CI gate.

DOSBox reference captures are also the parity oracle for presentation, which the C source alone cannot give.

### 3.8 Human playtest checklist: before each milestone merge

A 15-minute script in `docs/rewrite/playtest-checklist.md`:

- BEGIN1 win and loss;
- one DUAL map with two players on one keyboard;
- one co-op map;
- F-key switching;
- every weapon fired once;
- audio audible and panned;
- split-screen readable;
- Esc and retry flows.

Record each session with `-Dtankarena.record` so any bug found becomes a replay test (§3.3).

## 4. CI pipeline

There is no CI yet. Fix `.gitignore` first: its `.*` rule currently ignores `.github/`.

| Stage | Trigger | Contents | Budget |
|---|---|---|---|
| `check` | every push / PR | `./kotlin build`, all unit, rule, content and replay tests (§3.1–3.3), client unit + Compose UI + golden images (§3.6) | < 10 min |
| `smoke` | every PR | desktop smoke under Xvfb (§3.6) | < 3 min |
| `nightly` | schedule | soak, fuzz and winnability bots (§3.4), every-scene × every-mode boot, balance telemetry artifact | < 60 min |
| `weekly` | schedule / manual | visual agent pass (§3.7) → issues | manual triage |

**Flake policy:**

- No automatic retries.
- A failure is reproduced with its seed or replay, then fixed or the test is corrected in the same PR.
- Quarantine is not allowed (see Principle 4).

**Environment note.** Compose 1.10's desktop runtime artifacts (`androidx.*`) are published only on Google Maven (`dl.google.com`). Any CI runner or cloud agent session must allow that host as well as Maven Central and the JetBrains Kotlin toolchain repository (`packages.jetbrains.team`), or nothing beyond `:game-protocol`/`:game-content` will build.

## 5. Coverage targets

- `:game-server`: every rule in `docs/game/` has at least one test that cites its source.
- `:game-content`: every shipped scene boots in every compatible mode.
- `:game-protocol`: version checks plus a serialization round-trip for every `ActorState`/`GameEvent` subtype.
- `:game-client`: every screen has a Compose UI test, and every rendered actor family has a golden image.
- Line coverage is reported, not gated. Kover is enough.

## 6. Rollout order

1. CI with the `check` stage (unblocks everything else).
2. `EveryShippedSceneBootsTest`, invariants, and the scene-freshness test.
3. Determinism audit, then the replay recorder and `ReplayRegressionTest` (T36–T38).
4. Bots plus nightly soak and winnability.
5. Offscreen renderer and golden images; desktop smoke with an autostart flag.
6. Match Control API as an MCP server, with a short `docs/rewrite/matchctl.md` describing its tools for agents.
7. DOSBox reference captures and the weekly visual agent pass.
