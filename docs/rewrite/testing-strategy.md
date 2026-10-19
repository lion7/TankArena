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

`./kotlin test` runs 295 tests, all green: 226 in `:game-server`, 60 in `:game-client`, 9 in
`:game-content`. `:game-protocol`, `:game-editor` and `:tools-mapconv` have none.

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

**No recordings are committed yet**, so the first test currently checks nothing. Playing BEGIN1–4
(and later every mission) under the current rules version and committing the recordings is the most
valuable next step.

### On-demand tools (pass trivially unless switched on)

| Switch | Test | Output |
|---|---|---|
| `IDLE_SCAN=<ticks>` | `IdleScanTest` | How every single-player map ends with nobody playing (catches maps that win or lose by themselves) |
| `TICK_BENCH=1` (`TICK_BENCH_MAPS`, `TICK_BENCH_TICKS`) | `TickBenchmarkTest` | Per-map tick timings incl. p99, with the player driving and firing |
| `DUMP_DIR=<dir>` | `ScreenDumpTest`, parts of `FliAnimationTest`, `NightHudTest` | Offscreen renders as PNG |

```bash
IDLE_SCAN=30000 ./kotlin test -m game-server
TICK_BENCH=1 ./kotlin test -m game-server
```

### Client tests (`:game-client`)

Pure logic and offscreen rendering, no UI automation: `AudioMathTest`, `MissionCodesTest`,
`GameOptionsTest`, `PasswordStoreTest`, `MapWindowTest`, `TankSelectTest`, `TextPagesTest`,
`Mode13ScreenTest`, `FliAnimationTest`, `PaletteCyclingTest`, `DirectionalFrameTest`, `ArrowTest`,
`NightLightingTest`, `NightHudTest` (asserts pixel colours of an offscreen render), `ShadowTest`,
`ScreenDumpTest`.

## What is not there yet

In order of value:

1. **Committed recordings** of the original missions (see above), re-recorded deliberately whenever
   `GAME_RULES_VERSION` changes.
2. **Continuous integration.** Fix `.gitignore` first (its `.*` rule ignores `.github/` and
   `.forgejo/`). Stages:

   | Stage | Trigger | Contents | Budget |
   |---|---|---|---|
   | check | every push | `./kotlin build`, `./kotlin test` | < 10 min |
   | smoke | every PR | desktop smoke under Xvfb (item 5) | < 3 min |
   | nightly | schedule | idle scan, every scene × mode boot, soak bots, tick benchmark | < 60 min |

   Runners need Maven Central, Google Maven (Compose's `androidx.*` artifacts) and the JetBrains
   toolchain repository (`packages.jetbrains.team`).
3. **Every scene boots in every mode.** For each scene in `index.txt` and each mode its map type
   allows: boot, assert the expected player tanks, tick 200 ticks idle, check invariants.
4. **Invariants and bots** (scripted, not LLM): idle, random-input (seeded) and objective-seeking bots;
   per-tick invariants (finite positions, armor and ammo in range, no unbounded object growth, one
   `MissionResult`, tick under 5 ms); nightly soak of 20 seeds × 30,000 ticks per scene, failures
   saved as recordings.
5. **Desktop smoke.** Start the packaged or built game under `xvfb-run` with
   `TANKARENA_AUTOSTART=begin1 TANKARENA_QUIT_AFTER=10`; assert it ran, logged no exception and drew
   a mostly non-black frame. The packaging smoke test (`HeadlessException` without a display) covers
   only class loading.
6. **Golden images.** Offscreen renders (the `ImageComposeScene` path already used by the dump tests)
   of fixed snapshots compared with PNGs: BEGIN1 start, an explosion, a wreck, split screen, night,
   each object family. DOSBox captures of the original are the reference for presentation.
7. **Scene freshness** (only while [ADR 0003](adrs/0003-legacy-map-import.md) keeps `.MAP` as the
   source): regenerate the scenes into a temporary folder and compare with the committed ones.
8. **Divergence reporting for recordings:** store a hash of the snapshot every N ticks in the recording
   so a failing replay names the first tick that differs, instead of only "different result".
9. **Match control for agents** (debug builds only): a small MCP server over stdio wrapping
   `MatchClient` with `list_scenes`, `start`, `step`, `snapshot`, `render` and `save_recording`, so a
   coding agent can probe the game at simulation speed and turn findings into tests. Never linked into
   the release client.
10. **Playtest checklist** before merging milestones: BEGIN1 win and loss, a player-vs-player map, a
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
