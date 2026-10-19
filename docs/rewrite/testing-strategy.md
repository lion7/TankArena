# Testing Strategy

How the rewrite is tested today, and what is planned. The parity target is the original game: the C
source in `src/`, described in [`docs/game/`](../game/), and DOSBox footage of the original release.

## Principles

1. **Test the simulation, not the pixels.** All gameplay truth lives in `:game-server`, which runs
   headless at 100 ticks per second and is driven only by inputs. Every rule can be checked by
   stepping ticks and reading the `WorldSnapshot`, `PlayerView`s and `GameEvent`s.
2. **One seam.** Tests, recordings, bots and agents drive a match through `MatchClient`
   (`submitInput`, `tick`) or `ServerMatchPrototype` directly; never by synthesising clicks.
3. **The original is the oracle.** Expected values come from the C source (`src/data/tanks.c`,
   `src/include/define.h`, …), not from what the Kotlin code currently does. Each parity test cites its
   source line in a comment ([ADR 0010](adrs/0010-port-the-original-rules.md)).
4. **Deterministic or broken.** Identical input gives identical output
   ([ADR 0014](adrs/0014-simulation-thread-and-recordings.md)). A flaky test is a bug in the code or
   the test, never "infrastructure"; no automatic retries, no quarantine.
5. **Every bug becomes a test** at the lowest layer that reproduces it.

## What exists (2026-10-19)

`./kotlin test` runs 297 tests, all green: 226 in `:game-server`, 62 in `:game-client`, 9 in
`:game-content`. `:game-protocol`, `:game-editor` and `:tools-mapconv` have none.

### Continuous integration

`.github/workflows/ci.yml` (GitHub Actions, Ubuntu):

| Job | Trigger | Contents |
|---|---|---|
| `test` | every push and pull request, and nightly | `./kotlin build`, `./kotlin test`; on failure the differing reference renders are uploaded |
| `nightly` | every night at 02:30 UTC, or by hand | idle scan (`IDLE_SCAN=30000`) and tick benchmark (`TICK_BENCH=1`), logs uploaded |

The toolchain, its JDK and the Maven dependencies are cached between runs
(`~/.cache/JetBrains/Kotlin`). Runners need Maven Central, Google Maven (Compose's `androidx.*`
artifacts) and the JetBrains toolchain repository (`packages.jetbrains.team`). `.gitignore` ignores
dot-folders except `.github/` and `.dockerignore`.

### Rule tests (`:game-server`)

Small fixture maps (`CanonicalMapDefinition` through `ServerMatchPrototype.fromCanonicalMap`) or
shipped scenes, one rule per test:

| Topic | Test classes |
|---|---|
| Vehicles | `ServerTankMovementTest`, `ServerTankCollisionTest`, `VehicleSpecTest`, `VehicleHitTest`, `ServerTankLifecycleTest` |
| Weapons and damage | `ServerTankFireTest`, `ServerTankDamageModelTest`, `ServerRocketTest`, `ServerMortarTest`, `ServerMineTest`, `ServerTurretFireTest`, `AircraftAndWeaponsTest` |
| Terrain and structures | `TerrainMaterialTest`, `TerrainGridBuilderTest`, `StructureGridTest`, `StructureGameplayTest`, `BombersFieldsRampsTest` |
| Objects | `MapObjectsTest`, `ServerProductActorTest`, `ServerGoalCaptureTest` |
| Missions, control, modes, AI | `MissionEvaluationTest`, `ControlAndObjectivesTest`, `DualRulesTest`, `PilotAndNightTest`, `ComputerTankAiTest`, `ServerAiTankTest` |
| Infrastructure | `ActorRoundTripTest` (5 actor types), `SchemaVersionTest`, `FixedStepClockTest`, `HeadlessKubrikoSmokeTest`, `TerrainSlideManagerTest`, `ServerMatchPrototypeTest`, `LocalMatchClientTest`, `ServerPlayerViewTest`, `EditorBoundaryGuardTest`, `legacy/LegacyObjectParserTest` |

Conventions: name tests after the behaviour; cite the C source; avoid the `queueDamage*ForTest`
shortcuts where real projectiles reach the same state within about 1,000 ticks.

### Content tests

- `AllShippedMapsImportCleanTest`: every `.MAP` in `MAPS/` imports with no notes. (It silently
  skips maps the parser rejects; it should fail instead.)
- `ShippedSceneMatchTest` (17 tests): the index lists every scene; facts about BEGIN1, BEGIN2 and
  other shipped maps (spawns, terrain, goal counter wins and losses, player-vs-player pads, groups).
- `SceneJsonBootstrapTest`, `TankArenaSceneRoundTripTest`: every generated pair is well-formed and
  round-trips.
- `LegacySpriteResourcesTest` (`:game-content`): the extracted pictures resolve.

### Recorded playthroughs

`RecordedPlaythroughTest` replays every `game-server/testResources/recordings/*.json` and requires
the identical result; a second test records and replays a scripted BEGIN2 match. Recordings come from
normal play (the client saves every mission) and are tied to `GAME_RULES_VERSION`
([ADR 0014](adrs/0014-simulation-thread-and-recordings.md)).

Committed on 2026-10-19: BEGIN1 ×2, BEGIN2 won and lost, BEGIN3 and COMBAT1 lost. Five of them were
played under rules v10 and replay identically under v11, so they were relabelled. BEGIN4's v10
recording no longer replays (v11 changed its rocket turrets) and must be played again.

When the rules version is bumped, first replay the existing recordings with the new version: re-record
only those that change, and relabel the rest. Recordings are the main safety net for the Kubriko
removal ([ADR 0019](adrs/0019-plain-kotlin-no-kubriko.md)), which must leave all of them passing.

### On-demand tools (pass trivially unless switched on)

| Switch | Test | Output |
|---|---|---|
| `IDLE_SCAN=<ticks>` | `IdleScanTest` | How every single-player map ends with nobody playing (catches maps that win or lose by themselves) |
| `TICK_BENCH=1` (`TICK_BENCH_MAPS`, `TICK_BENCH_TICKS`) | `TickBenchmarkTest` | Per-map tick timings incl. p99, with the player driving and firing |
| `DUMP_DIR=<dir>` | `ScreenDumpTest`, parts of `FliAnimationTest`, `NightHudTest` | Offscreen renders as PNG |
| `UPDATE_REFERENCE_RENDERS=1` | `ReferenceRenderTest` | Rewrites the reference renders |

```bash
IDLE_SCAN=30000 ./kotlin test -m game-server
TICK_BENCH=1 ./kotlin test -m game-server
```

### Every map boots (`:game-client`)

`ShippedMapsBootTest` starts every shipped map in every mode its map type allows (legacy
`check_suitable()`), and in player vs player once per vehicle the map's contents allow (legacy
select_tanks()) — 143 combinations. Each must give every player a living vehicle and run 200 idle
ticks without an exception, with every vehicle inside the world and finite speeds.

### Reference renders (`:game-client`)

`ReferenceRenderTest` draws eight moments through the real game screen (`GameplayFrame`) offscreen at
640 × 480 and compares them with the PNGs in `game-client/testResources/reference-renders/`: BEGIN1
at the start, in a fight and won; COMBAT1 lost (grey view, banner, explosion); BEGIN3 (force field,
helicopter); ZULU at night; AIR with two players against the computer; FLAGISLE player vs player.

- Moments come from the committed recordings (or idle matches) through `MatchReplay.replayFrames`;
  the last 150 ticks before each moment are drawn one by one with their events, so effects and colour
  cycling are in the same state on every run.
- A render fails when more than 0.2 % of its pixels differ by more than 8 in a colour channel; the
  actual image is written to `game-client/build/reference-renders/`.
- `UPDATE_REFERENCE_RENDERS=1 ./kotlin test -m game-client` rewrites the references; look at them
  before committing.
- They pin what the current renderer draws, as the baseline for the renderer that replaces Kubriko
  ([ADR 0019](adrs/0019-plain-kotlin-no-kubriko.md)).

### Client tests (`:game-client`)

Pure logic and offscreen rendering, no UI automation: `AudioMathTest`, `MissionCodesTest`,
`GameOptionsTest`, `PasswordStoreTest`, `MapWindowTest`, `TankSelectTest`, `TextPagesTest`,
`Mode13ScreenTest`, `FliAnimationTest`, `PaletteCyclingTest`, `DirectionalFrameTest`, `ArrowTest`,
`NightLightingTest`, `NightHudTest` (asserts pixel colours of an offscreen render), `ShadowTest`,
`ScreenDumpTest`.

## What is not there yet

In order of value:

1. **More recordings:** BEGIN4, more single-player missions, a two-vs-computer mission and
   player-vs-player matches (deathmatch, flag hunt, race); once rulesets exist, Classic and Extended
   recordings separately.
2. **Invariants and bots** (scripted, not LLM): random-input (seeded) and objective-seeking bots;
   per-tick invariants beyond the boot test's (ammo in range, no unbounded object growth, one
   `MissionResult`, tick under 5 ms); nightly soak of 20 seeds × 30,000 ticks per map, failures saved
   as recordings.
3. **Desktop smoke.** Start the built game under `xvfb-run` with
   `TANKARENA_AUTOSTART=begin1 TANKARENA_QUIT_AFTER=10`; assert it ran, logged no exception and drew
   a mostly non-black frame. The packaging smoke test (`HeadlessException` without a display) covers
   only class loading.
4. **Map round trips** ([ADR 0022](adrs/0022-native-map-format.md)): `.MAP` → native → `.MAP` → native
   identical for every shipped map. Until the native format lands, a scene-freshness test (regenerate
   into a temporary folder and compare) catches importer changes without regeneration.
5. **Divergence reporting for recordings:** store a hash of the snapshot every N ticks in the recording
   so a failing replay names the first tick that differs, instead of only "different result".
6. **More reference renders:** each object family, effects mid-animation; DOSBox captures of the
   original as the reference for presentation (compared by eye, not in CI).
7. **Match control for agents** (debug builds only): a small MCP server over stdio wrapping
   `MatchClient` with `list_scenes`, `start`, `step`, `snapshot`, `render` and `save_recording`, so a
   coding agent can probe the game at simulation speed and turn findings into tests. Never linked into
   the release client.
8. **Playtest checklist** before merging milestones: BEGIN1 win and loss, a player-vs-player map, a
   co-op map, F-key switching, every weapon, sound panning, split screen, night map, Esc and retry.
   Each session's recording is kept if it found something.

Coverage is reported, not gated. Kover is a Gradle plugin and is not available under the Kotlin
toolchain; JaCoCo's agent or the toolchain's own support (when it exists) can replace it.

## Where AI agents fit

- **Yes:** through the simulation seam (item 9), at full speed, writing reproducible tests.
- **Occasionally:** a vision model comparing screenshots of the rewrite with DOSBox captures of the
  original for the same map and moment (layering, palette, missing sprites, layout). Its output is a
  list for a human to triage, never a CI gate. Steering a tank by screenshots in real time does not
  work at 100 ticks per second.
