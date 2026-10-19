# Tank Arena Rewrite Architecture

This document describes how the Kotlin rewrite is built **today** (2026-10-19). Decisions and their
reasons are in the [ADRs](adrs/); the original game it reproduces is described in
[`docs/game/`](../game/) and the C source in `src/`.

> **Planned changes.** Kubriko is being removed everywhere ([ADR 0019](adrs/0019-plain-kotlin-no-kubriko.md)),
> maps move to a native format ([ADR 0022](adrs/0022-native-map-format.md)), the editor moves into the
> game ([ADR 0021](adrs/0021-editor-in-the-game.md)) and the modules are reorganised for Android and a
> dedicated server ([ADR 0025](adrs/0025-multiplatform-modules-and-android.md)). The target shape is
> summarised at the end of this document; the order is in the [roadmap](roadmap.md).

## Overview

```text
                 +-------------------- game-client (desktop app) -------------------+
 keyboard  -->   | Compose shell: intro, menus, prompts, text pages, options         |
                 | GameplayScreen                                                    |
                 |   SimulationLoop thread (100 ticks/s)                             |
                 |     InputFrame per player --> MatchClient.tick() --> ServerFrame  |
                 |   UI thread, every display frame:                                 |
                 |     per player window: Kubriko viewport (ClientScene.sync)        |
                 |                        + HUD/radar (Compose Canvas)               |
                 |   AudioManager --> DesktopAudioBackend (mixer), MusicPlayer       |
                 +-------------------------------------------------------------------+
                                    |  LocalMatchClient (in-process)
                 +--------------------- game-server (library) ----------------------+
                 | ServerMatchPrototype: map objects, StructureGrid, ComputerTankAi, |
                 | DualRules, mission rules  -->  WorldSnapshot + PlayerViews        |
                 +-------------------------------------------------------------------+
                                    |  ShippedScenes (class path)
                 +--------------------- game-content (library) ---------------------+
                 | scene_*.json + metadata_*.json, pictures catalog, terrain table,  |
                 | pixel masks, sounds, music                                        |
                 +-------------------------------------------------------------------+
```

## Modules

Six modules, all JVM-only ([ADR 0006](adrs/0006-module-collapse.md)). Dependencies point one way:
`game-protocol` ← `game-content` ← `game-server` ← `game-client`, `game-editor`, `tools-mapconv`.

| Module | Kind | Holds |
|---|---|---|
| `:game-protocol` | library | Wire and timing types; no Compose, no Kubriko |
| `:game-content` | library | Shipped maps, generated tables, pixel masks, sounds, music, map importer |
| `:game-server` | library | The authoritative simulation and the server half of the importer |
| `:game-client` | desktop app | Shell, playfield rendering, HUD, audio, settings, recordings |
| `:game-editor` | desktop app | Map editor on Kubriko's `SceneEditor` (to be deleted, ADR 0021) |
| `:tools-mapconv` | command line | Map conversion and asset extraction |

### `:game-protocol`

- `core/FixedStepClock` (100 ticks/s), `core/LegacyDirections` (16-step compass).
- `protocol/Frames.kt`: `InputFrame` (one per player per tick), `Team`.
- `input/PlayerIntent.kt`: `PlayerIntentFrame`, the server's internal intent, also produced by the AI
  and stored in recordings.
- `protocol/snapshot/`: `WorldSnapshot`, the `ActorState` hierarchy (about 19 states keyed by
  `actorId`), `GameEvent` (sounds, effects, explosions, messages, mission result, …), `PlayerView`,
  `HudState`, `RadarContact`, `LockMarker`, `TileState`, `ServerFrame`, and the terrain types
  `TerrainMaterial`, `TerrainTile`, `TerrainGrid`.
- Unused leftovers to delete: `ReplayProtocol.kt`, `snapshot/PlayerInput.kt`, `EntityId`, `Tick`,
  `wrapCoordinate`, `toroidalDelta`, and the never-emitted events `Fired`, `FlagCaptured`,
  `FlagReturned`, `FlagDelivered`.

### `:game-content`

- `ShippedScenes`: loads `scenes/index.txt` and the `scene_<name>.json` / `metadata_<name>.json`
  pairs from the class path; rejects sidecars newer than `SCENE_SCHEMA_VERSION`.
- `MapSceneSidecar`: header fields, mission texts and tile layers of a map
  ([content-format.md](content-format.md)).
- Generated from the C source: `GeneratedLegacyPictureCatalog` and `GeneratedLegacyPictureTerrain`
  (`src/data/pictures.c`); `PictureCatalog` and `TerrainGridBuilder` build each map's terrain grid
  (material and speed per tile).
- `PictureMasks`: opaque-pixel masks of every picture (`resources/masks/pictures.mask`).
- `legacy/LegacyMapParser`: the raw `.MAP` reader (importer only).
- `tools/mapconv/*`: the extractor library behind `:tools-mapconv` (pictures, fonts, night tables,
  masks, vehicle and terrain tables).
- Resources: `scenes/`, `masks/`, `sounds/` (29 WAVs), `music/` (two MIDI files).

### `:game-server`

All under `com.tankarena.sim.kubriko.server`.

| File | Role |
|---|---|
| `ServerMatchPrototype` | The match: setup, the tick pipeline, hits, structures, objects, mission rules, snapshot and views |
| `ServerTankActor` | Vehicles: movement physics, heights, weapons, damage, shield, cloak, lights |
| `ComputerTankAi` | Port of `control_computer_tank()` ([ADR 0012](adrs/0012-port-the-computer-ai.md)) |
| `StructureGrid` | Buildings: strengths, links, ruins, goal shares, masks ([ADR 0011](adrs/0011-structure-grid-and-pixel-masks.md)) |
| `DualRules` | Player vs player: start pads and lives, flag hunt, races |
| `MatchSetup` | Mode (`SINGLE`, `DUAL`, `DUAL_VS_COMPUTER`) and who controls which tanks |
| `LegacyVehicles`, `GeneratedLegacyVehicles` | The 23-vehicle table from `src/data/tanks.c` |
| `MatchObjects` | Runtime-only objects: bonuses, soldiers, A-bombs |
| `AreaDamageResolver` | Blast damage for mines, mortars, rockets, A-bombs |
| `Server*Actor` (17 files) | Map objects: turret, projectile, mine, rocket, mortar, goal, flag, product, lock, warp, destroyer, enforcer, train, zeppelin, B52, wall |
| `MatchClient` | `MatchClient` interface and `LocalMatchClient` |
| `MatchRecording` | Recorder, replay, `GAME_RULES_VERSION` |
| `TankArenaSerializableMetadata` | Scene type ids for Kubriko serialization |
| `CanonicalSceneBuilder`, `LegacyToSceneJson`, `legacy/*` | The importer (tools and tests only) |

### `:game-client`

| Package | Contents |
|---|---|
| `app/desktop` | `Main.kt` (window, screen state machine, key bindings, gameplay screen), `SimulationLoop`, `RecordingStore`, `PerfLog` |
| `ui/compose/menu` | Main and mode menus, mission-code prompt and list, `PasswordStore`, game options, tank selection, intro, text pages, briefing and end banners |
| `ui/compose/legacy` | Mode 13h screen and fonts (generated), `FliAnimation` |
| `ui/compose` | `LegacyScreen` (640×480 and 320×200 screens, fades), `GammaCorrection`, shell state |
| `ui/compose/hud` | `HudOverlay` (status display, radar, pilot face and messages, banners, arrow), `NightHud` |
| `render/kubriko` | `TankArenaViewport`, `ClientScene`, terrain and top-layer actors, sprite catalog and preload, `NightLighting`, `PaletteCycling`, map window layout |
| `audio` | `AudioBackend`, `DesktopAudioBackend` (software mixer), `AudioManager`, `MusicPlayer` |

### `:game-editor` and `:tools-mapconv`

- `:game-editor` is one file that opens Kubriko's `SceneEditor` on `game-content/resources/scenes`
  with four editable types (wall, tank, turret, goal). It is superseded by the in-game editor
  ([ADR 0021](adrs/0021-editor-in-the-game.md)) and will be deleted.
- `:tools-mapconv` is one file (`Main.kt`) of subcommands over the `:game-content` and `:game-server`
  converter code; see [assets.md](assets.md#tools-mapconv).

## Boundaries

- `:game-protocol` and `:game-content` contain no Compose or Kubriko code.
- Only `:game-server` decides gameplay. The client renders snapshots and sends inputs.
- The editor never runs the simulation (`EditorBoundaryGuardTest`).
- No runtime code reads repository folders (`MAPS/`, `DATA/`, `unpacked/`); everything shipped comes
  from the class path. Only the tools and some tests read them.
- Known exception to the intended layering: `:game-server` depends on Compose and Kubriko because its
  map objects are Kubriko actors. [ADR 0019](adrs/0019-plain-kotlin-no-kubriko.md) removes that.

## A match, step by step

1. **Choose.** The shell picks a mission (by code, [ADR 0015](adrs/0015-ship-the-original-69-maps.md)
   for which maps exist) or, in player vs player, a map and each player's vehicle on the tank selection
   screen (which itself runs `scene_select` as a live match).
2. **Create.** `LocalMatchClient.fromSceneJson(scene, sidecar, mode, dual, recordAs)` calls
   `ServerMatchPrototype.fromSceneJson`: Kubriko's serializer reads the scene's map objects, wall
   objects are dropped, `MatchSetup.assignControl` gives tanks to players per mode, and the terrain
   grid and `StructureGrid` are built from the sidecar. `initialize()` assigns `actorId`s, start
   shields, `DualRules`, trains, B52s, locks, fallen turrets and bonuses.
3. **Run.** `SimulationLoop` calls the gameplay step 100 times per second
   ([ADR 0014](adrs/0014-simulation-thread-and-recordings.md)): submit each player's `InputFrame`, then
   `tick()`.
4. **Tick.** `ServerMatchPrototype.tick()` runs a fixed pipeline: player intents to every tank in the
   player's group; AI tanks; turrets; terrain effects; the actor updates (through Kubriko's tick);
   projectile hits on structures and vehicles; flames; small objects; vehicle overlaps; mines,
   rockets, mortars, soldiers, A-bombs, warps, locks, destroyers, enforcers, trains, B52s and
   zeppelins; goals and the mission rules; then the snapshot.
5. **Publish.** The tick returns a `WorldSnapshot` (actor states, events, changed tiles, flash);
   `LocalMatchClient` wraps it with one `PlayerView` per player into a `ServerFrame`. Events go to a
   queue per player window and to the audio manager; the finished view is published to the UI thread.
6. **Draw.** Each player window is a Kubriko viewport: `ClientScene.sync` adds, updates and removes
   render actors by `actorId`, effects play from the queued events, terrain and the top layer are
   drawn as actors, night and colour cycling are applied as runtime shaders. The HUD and radar are a
   Compose canvas over the viewport.
7. **End.** When the mission rules decide (goal counter, last human tank lost, elimination fallback;
   rounds in player vs player), a `MissionResult` event ends the match after the original's 3-second
   run-on; the client fades out, saves the recording and shows the success or failure text.

## Replication

- The snapshot is a **projection**, not Kubriko serialization: `projectToWireState` copies each map
  object's fields into its protocol `ActorState`, then the runtime objects and changed structure tiles
  are added. Scene files and the wire therefore use different types.
- `actorId`s are stable for the life of a match; the client keys its render actors by them.
- Each `PlayerView` carries the camera (following the tank, or the rocket marker while it is out),
  `HudState`, radar contacts and the lock marker.

## Versions

| Constant | Value | Where | Checked |
|---|---|---|---|
| `PROTOCOL_VERSION` | 7 | `game-protocol/.../snapshot/WorldSnapshot.kt` | `decodeWorldSnapshot` (tests only today) |
| `SCENE_SCHEMA_VERSION` | 1 | `game-content/.../MapSceneSidecar.kt` | `ShippedScenes` rejects newer sidecars |
| `GAME_RULES_VERSION` | 11 | `game-server/.../MatchRecording.kt` | `RecordedPlaythroughTest` requires equality |

## Threads

- **UI thread** (Compose): input events, screens, drawing.
- **`simulation`** thread: the match ticks (`SimulationLoop`), one per running match.
- **`audio-mixer`** thread: mixes up to 32 voices onto one output line.
- Java's MIDI sequencer thread for music.
- Kubriko's own coroutines for actor bookkeeping (server and client instances).

## Debug and measurement switches

| Switch | Effect |
|---|---|
| `TANKARENA_PERF=1` | Logs tick time, loop and frame gaps and GC once a second to stdout and `./perf.log` |
| `TANKARENA_AUTOSTART=<code or name>` | Skips the menus into a one-player mission |
| `TANKARENA_AUTOSTART_PVP=1` | With `AUTOSTART`, goes to the tank selection for player vs player |
| `TANKARENA_QUIT_AFTER=<seconds>` | Closes the game after that time |
| `-Dtankarena.debugOverlay=true` | Draws viewport and camera geometry |
| `-Dtankarena.assets.background=<path>` | Overrides the panel background picture |
| `TICK_BENCH=1`, `IDLE_SCAN=<ticks>`, `DUMP_DIR` | Test-only; see [testing-strategy.md](testing-strategy.md) |

The original's command-line switches (`-GOD`, `-NOFAIL`, `-CODES`, …) are not implemented yet; they
are part of parity ([parity.md](parity.md#shell-and-meta-game)).

## Known structural debt

- `ServerMatchPrototype.kt` is 3,149 lines and `ServerTankActor.kt` 1,760; most new rules land there.
  Splitting by subsystem (objects, hits, mission rules, view building) would help, ideally as part of
  [ADR 0019](adrs/0019-plain-kotlin-no-kubriko.md).
- Dead code listed above (protocol leftovers, `ServerWallActor`/`WallState`, `TerrainSlideManager`'s
  old role, the no-op `CollisionManager`) and a stale KDoc on `fromSceneJson` that still mentions A*.
- `fromCanonicalMap` builds test fixtures through the importer's `CanonicalSceneBuilder`, so tests
  depend on the importer; with the native format they build map documents instead
  ([ADR 0022](adrs/0022-native-map-format.md)).
- `game-client/module.yaml` declares Kubriko's keyboard-input, collision and serialization plugins,
  which the client does not use.

## Target architecture

Where ADRs 0019–0027 lead; the [roadmap](roadmap.md) says in which order.

```text
app-desktop / app-android / app-server        platform drivers: window or activity, audio,
        |                                     MIDI, storage, gamepads, loop driver
game-ui (Compose)   renderer (Canvas, N views), HUD, Classic menus, Extended menus,
        |           replays, in-game editor, input layer
game-net            Ktor WebSockets: RemoteMatchClient, server around MatchHost
        |
game-sim            World, step() in the original's order, legacy step every 3rd tick,
        |           Ruleset (Classic / Extended flags), deterministic maths, MatchHost
game-content        native .tamap maps, picture catalog, terrain tables, masks, .MAP import/export
game-protocol       InputFrame (tick, player, team), snapshots, events, versions
```

- No Kubriko; `game-sim`, `game-content` and `game-protocol` are common Kotlin with no platform APIs.
- One renderer draws any number of views from one snapshot (1–2 in Classic, up to 4 in Extended).
- Local and networked matches both go through `MatchClient`; networked play is server-authoritative
  with snapshots ([ADR 0024](adrs/0024-networking.md)).
