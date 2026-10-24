# Testing Strategy

How the rewrite is tested today, and what is missing. The parity target is the original game: the C
source in `original/src/`, described in [`original/docs/`](../original/docs/), and DOSBox footage of
the original release.

## Principles

1. **Test the simulation, not the pixels.** All gameplay truth lives in `:game-sim`, which runs
   headless and is driven only by inputs. Every rule can be checked by stepping ticks and reading the
   `WorldSnapshot`, `PlayerView`s and `GameEvent`s.
2. **One seam.** Tests, recordings and bots drive a match through `MatchClient` (`submitInput`,
   `tick`) or `Match` directly; never by synthesising clicks.
3. **The original is the oracle.** Expected values come from the C source
   (`original/src/data/tanks.c`, `original/src/include/define.h`, …), not from what the Kotlin code
   currently does. Each parity test cites its source line
   ([ADR 0010](adrs/0010-port-the-original-rules.md)).
4. **Deterministic or broken.** Identical input gives identical output
   ([ADR 0014](adrs/0014-simulation-thread-and-recordings.md)). A flaky test is a bug in the code or
   the test; no automatic retries, no quarantine.
5. **Every bug becomes a test** at the lowest layer that reproduces it.

## What exists

`./kotlin test` runs **401 tests**, all green on 2026-10-22: 283 in `:game-sim`, 88 in `:game-ui`,
15 in `:game-net`, 11 in `:game-content`, 3 in `:app-server`, 1 in `:app-desktop`. `:game-protocol`,
`:app-android`, `:app-relay` and `:tools-mapconv` have none of their own (the relay is tested in
`:game-net`, the extractors through their output).

### Continuous integration

`.github/workflows/ci.yml` (GitHub Actions, Ubuntu 24.04):

| Job | Trigger | Contents |
|---|---|---|
| `test` | every push and pull request, and nightly | `./kotlin build`, `./kotlin test`; on failure the differing reference renders are uploaded |
| `nightly` | every night at 02:30 UTC, or by hand | idle scan (`IDLE_SCAN=30000`) and tick benchmark (`TICK_BENCH=1`), logs uploaded |

The toolchain, its JDK and the Maven dependencies are cached between runs
(`~/.cache/JetBrains/Kotlin`). Runners need Maven Central, Google Maven (Compose's `androidx.*`
artifacts) and the JetBrains toolchain repository. Two more workflows build and push the relay image
(`relay.yml`) and deploy the website (`website.yml`).

### Rule tests (`:game-sim`)

Small fixture maps (built through `Match.fromCanonicalMap`) or shipped maps, one rule per test:

| Topic | Test classes |
|---|---|
| Vehicles | `TankMovementTest`, `TankCollisionTest`, `VehicleSpecTest`, `VehicleHitTest`, `VehicleRulesTest`, `TankLifecycleTest` |
| Weapons and damage | `TankFireTest`, `TankDamageModelTest`, `RocketTest`, `RocketDefenceTest`, `MortarTest`, `MineTest`, `TurretFireTest`, `AircraftAndWeaponsTest` |
| Terrain and structures | `TerrainMaterialTest`, `TerrainGridBuilderTest`, `TerrainEffectsTest`, `StructureGridTest`, `StructureGameplayTest`, `BombersFieldsRampsTest` |
| Objects | `MapObjectsTest`, `ProductTest`, `SupermarketMayhemTest`, `GoalCaptureTest`, `GoalLinkTest`, `WarpTest` |
| Missions, control, modes, AI | `MissionEvaluationTest`, `ControlAndObjectivesTest`, `DualRulesTest`, `NoVehicleViewTest`, `PilotAndNightTest`, `ComputerTankAiTest`, `AiTankTest` |
| Extended | `ExtendedFlagsTest`, `TeamsTest` |
| Infrastructure | `ActorRoundTripTest`, `SchemaVersionTest`, `MatchObjectListTest`, `MatchTest`, `LocalMatchClientTest`, `PlayerViewTest`, `legacy/LegacyObjectParserTest` |

Conventions: name tests after the behaviour; cite the C source; avoid the `queueDamage*ForTest`
shortcuts where real projectiles reach the same state within about 1,000 ticks.

### Maps and the editor's rules (`:game-sim`, `:game-content`)

- `MapDocumentTest`: every `.MAP` of the original release imports without notes to exactly its
  committed `.tamap` (so an importer change without `import-all` fails); every shipped map reads back
  to the same text and objects; a wrong legend name or a newer format is refused.
- `LegacyMapExportTest`: every original `.MAP` exports back to exactly its own bytes (unlocked);
  edited and new maps export from their own values.
- `MapEditorTest`, `sim/map/MapObjectsTest`: the editor's rules without a screen (painting,
  structures, sparse layers, objects, undo, new maps, train cars placed as the original does).
- `ShippedSceneMatchTest`: facts about BEGIN1, BEGIN2 and other shipped maps (spawns, terrain, goal
  counter wins and losses, player-vs-player pads, groups); `TankArenaSceneRoundTripTest`: object
  states round-trip through their type ids.
- `LegacySpriteResourcesTest`, `StructureLibraryTest` (`:game-content`).

### Recorded playthroughs

`RecordedPlaythroughTest` replays every `game-sim/testResources/recordings/*.json` and requires the
identical result; a second test records and replays a scripted BEGIN2 match. Recordings come from
normal play (the game saves every match) and are tied to their ruleset's rules version
([ADR 0014](adrs/0014-simulation-thread-and-recordings.md)).

**No current recordings.** The six committed ones (BEGIN1 ×2, BEGIN2 won and lost, BEGIN3, COMBAT1
lost) were played under Classic rules v11. Rules v12–v15 changed how every match plays out, so they
were moved to `recordings/v11/`, where only the reference renders use them as input. Until new ones
are played, the scripted BEGIN2 recording is the only replay check.

When a rules version is bumped, first replay the existing recordings with the new version:
re-record only those that change, and relabel the rest.

### Every map boots (`:game-ui`)

`ShippedMapsBootTest` starts every shipped map in every mode its map type allows (the original's
`check_suitable()`), and in player vs player once per vehicle the map's contents allow
(`select_tanks()`): 143 combinations. Each must give every player a living vehicle and run 200 idle
ticks without an exception, with every vehicle inside the world and finite speeds.

### Reference renders (`:game-ui`)

`ReferenceRenderTest` draws nine moments through the real game screen (`GameplayFrame`) offscreen at
640 × 480 and compares them with the PNGs in `game-ui/testResources/reference-renders/`: BEGIN1 at
the start, in a fight and won; COMBAT1 lost (grey view, banner, explosion); BEGIN3 (force field,
helicopter); ZULU at night; AIR with two players against the computer; FLAGISLE player vs player;
SUPER (supermarket mayhem).

- Moments come from the v11 recordings or idle matches; the last 150 ticks before each moment are
  drawn one by one with their events, so effects and colour cycling are in the same state on every
  run.
- A render fails when more than 0.2 % of its pixels differ by more than 8 in a colour channel; the
  actual image is written to `game-ui/build/reference-renders/`.
- `UPDATE_REFERENCE_RENDERS=1 ./kotlin test -m game-ui` rewrites the references; look at them before
  committing.

### Screens, input and sound (`:game-ui`)

Pure logic and offscreen rendering, no UI automation: `ControlsTest`, `ExtendedControlsTest`,
`JoinStateTest`, `GamepadMenuKeysTest`, `StepInterpolationTest`, `EditorRenderTest`,
`ReplayEntryTest`, `ExtendedSettingsTest`, `AudioMathTest`, `MixerAudioBackendTest`,
`MissionCatalogTest`, `MissionCodesTest`, `GameOptionsTest`, `PasswordStoreTest`, `MapWindowTest`,
`TankSelectTest`, `TextPagesTest`, `Mode13ScreenTest`, `FliAnimationTest`, `PaletteCyclingTest`,
`DirectionalFrameTest`, `ArrowTest`, `NightLightingTest`, `NightHudTest`, `ShadowTest`,
`ScreenDumpTest`.

### Network (`:game-net`, `:app-server`)

`NetworkGameTest` runs a host and guests in one process (lobby, seats, refused versions, frames,
rounds); `DeltaFramesTest` checks that decoded deltas equal the frames; `RelayTest` runs relay, host
and guest together (codes, tunnels, the public list, wrong guesses); `ServerOptionsTest` parses
`app-server`'s options.

### On-demand tools (pass trivially unless switched on)

| Switch | Test | Output |
|---|---|---|
| `IDLE_SCAN=<ticks>` | `IdleScanTest` | How every single-player map ends with nobody playing (maps that win or lose by themselves) |
| `TICK_BENCH=1` (`TICK_BENCH_MAPS`, `TICK_BENCH_TICKS`) | `TickBenchmarkTest` | Per-map tick timings incl. p99, with the player driving and firing |
| `ALLOC_BENCH=1` | `AllocationBenchmarkTest` (`:game-ui`) | Bytes allocated per tick and per frame at 2400×1080 ([performance](performance.md)) |
| `FRAME_PROBE=1` | `FrameSizeProbeTest` (`:game-net`) | Bytes per network frame for every shipped map: whole, delta, deflated |
| `DUMP_DIR=<dir>` | `ScreenDumpTest`, parts of `FliAnimationTest`, `NightHudTest` | Offscreen renders as PNG |
| `UPDATE_REFERENCE_RENDERS=1` | `ReferenceRenderTest` | Rewrites the reference renders |

```bash
IDLE_SCAN=30000 ./kotlin test -m game-sim
TICK_BENCH=1 ./kotlin test -m game-sim
```

### Manual checks

- The [editor test checklist](editor-test-checklist.md): the editor's tools, and exported maps in the
  original under DOSBox.
- Android on the emulator (AVD `tankarena`); network games with the emulator joining a server on the
  same machine.

## What is not there yet

In order of value:

1. **Recordings under the current rules:** BEGIN1–4, more single-player missions, a two-vs-computer
   mission and player-vs-player matches (deathmatch, flag hunt, race); Extended recordings separately
   (four players, the mouse).
2. **Desktop smoke test:** start the packaged game under `xvfb-run` with
   `TANKARENA_AUTOSTART=begin1 TANKARENA_QUIT_AFTER=10`; check that it ran, logged no exception and
   drew a mostly non-black frame. The packaging smoke test (`HeadlessException` without a display)
   covers only class loading.
3. **Invariants and bots** (scripted): random-input (seeded) and objective-seeking bots; per-tick
   invariants beyond the boot test's (ammo in range, no unbounded object growth, one `MissionResult`,
   tick under 5 ms); a nightly soak of 20 seeds × 30,000 ticks per map, failures saved as recordings.
4. **Divergence reporting for recordings:** a hash of the snapshot every N ticks in the recording, so a
   failing replay names the first tick that differs instead of only "different result".
5. **More reference renders:** each object family, effects mid-animation, Extended's wide view and
   four views; DOSBox captures of the original as the reference for presentation (compared by eye,
   not in CI).
6. **Network games between real machines**, and a whole player-vs-player series through the screens.
7. **Match control for agents** (debug builds only): a small MCP server over stdio wrapping
   `MatchClient` (`list_maps`, `start`, `step`, `snapshot`, `render`, `save_recording`), so a coding
   agent can probe the game at simulation speed and turn findings into tests.

Coverage is reported, not gated. Kover is a Gradle plugin and is not available under the Kotlin
toolchain; JaCoCo's agent can replace it.

## Where AI agents fit

- **Yes:** through the simulation seam (item 7), at full speed, writing reproducible tests.
- **Occasionally:** a vision model comparing screenshots of the rewrite with DOSBox captures of the
  original for the same map and moment (layering, palette, missing pictures, layout). Its output is a
  list for a person to triage, never a CI gate. Steering a vehicle by screenshots in real time does
  not work at 100 ticks per second.
