# Tank Arena Rewrite Architecture

This document describes how the Kotlin rewrite is built **today** (2026-10-19). Decisions and their
reasons are in the [ADRs](adrs/); the original game it reproduces is described in
[`original/docs/game/`](../../original/docs/game/) and the C source in `original/src/`.

> **Planned changes.** Kubriko is gone since 2026-10-19 ([ADR 0019](adrs/0019-plain-kotlin-no-kubriko.md));
> maps move to a native format ([ADR 0022](adrs/0022-native-map-format.md)), the editor moves into the
> game ([ADR 0021](adrs/0021-editor-in-the-game.md)) and the modules are reorganised for Android and a
> dedicated server ([ADR 0025](adrs/0025-multiplatform-modules-and-android.md)). The target shape is
> summarised at the end of this document; the order is in the [roadmap](roadmap.md).

## Overview

```text
                 +------------- app-desktop (desktop app) on game-ui (library) -----+
 keyboard  -->   | Compose shell: intro, menus, prompts, text pages, options         |
                 | GameplayScreen                                                    |
                 |   SimulationLoop thread (100 ticks/s)                             |
                 |     InputFrame per player --> MatchClient.tick() --> ServerFrame  |
                 |   UI thread, every display frame:                                 |
                 |     per player window: RenderScene on a Canvas (ClientScene.sync) |
                 |                        + HUD/radar (Compose Canvas)               |
                 |   AudioManager --> DesktopAudioBackend (mixer), MusicPlayer       |
                 +-------------------------------------------------------------------+
                                    |  LocalMatchClient (in-process)
                 +--------------------- game-sim (library) ----------------------+
                 | Match: map objects, StructureGrid, ComputerTankAi, |
                 | DualRules, mission rules  -->  WorldSnapshot + PlayerViews        |
                 +-------------------------------------------------------------------+
                                    |  ShippedMaps (class path)
                 +--------------------- game-content (library) ---------------------+
                 | maps/*.tamap, pictures catalog, terrain table,                    |
                 | pixel masks, sounds, music                                        |
                 +-------------------------------------------------------------------+
```

## Modules

Nine modules (2026-10-20, [ADR 0025](adrs/0025-multiplatform-modules-and-android.md)); the libraries
compile for the JVM and Android. Dependencies point one way:
`game-protocol` ← `game-content` ← `game-sim` ← `game-net` ← `game-ui` ← `app-desktop` and
`app-android`; `app-server` uses `game-net` without any Compose; `tools-mapconv` uses `game-content`
and `game-sim`. Packages are under `nl.tankarena`; artifacts are `nl.tankarena:<module>`.

| Module | Kind | Holds |
|---|---|---|
| `:game-protocol` | library | Wire and timing types, deterministic maths; no Compose |
| `:game-content` | library | Shipped maps, generated tables, pixel masks, sounds, music, `.MAP` parser |
| `:game-sim` | library | The authoritative simulation, the native map format, `.MAP` import and export, the editor's rules |
| `:game-net` | library | Network games ([ADR 0024](adrs/0024-networking.md)): messages, the match server with its lobby, the remote match client |
| `:game-ui` | library (Compose) | The whole game: screen state machine, renderer, HUD, both menus, editor and replay screens, input layer, sound mixer, pictures |
| `:app-desktop` | desktop app | Window, desktop audio and MIDI, gamepads (Jamepad), screenshots |
| `:app-android` | Android app | Activity, Android audio and MIDI, gamepads, touch screen, app storage |
| `:app-server` | command line | The dedicated server: hosts network games without a screen |
| `:app-relay` | command line | The internet relay and lobby: game codes, tunnels, the public game list |
| `:tools-mapconv` | command line | Map conversion and asset extraction (the extractors live here) |

### `:game-protocol`

- `core/FixedStepClock` (100 ticks/s), `core/LegacyDirections` (16-step compass), `core/DetMath`
  (trigonometry and roots through `StrictMath`, identical on every JVM; the simulation uses only this).
- `protocol/Frames.kt`: `InputFrame` (one per player per tick), `Team`.
- `input/PlayerIntent.kt`: `PlayerIntentFrame`, the server's internal intent, also produced by the AI
  and stored in recordings.
- `protocol/snapshot/`: `WorldSnapshot`, the `ActorState` hierarchy (about 19 states keyed by
  `actorId`), `GameEvent` (sounds, effects, explosions, messages, mission result, …), `PlayerView`,
  `HudState`, `RadarContact`, `LockMarker`, `TileState`, `ServerFrame`, and the terrain types
  `TerrainMaterial`, `TerrainTile`, `TerrainGrid`.

### `:game-content`

- `resources/maps`: the 69 shipped maps as `.tamap` files and `index.txt` ([content-format.md](content-format.md)).
- `MapSceneSidecar`: a map's header fields, mission texts and tile layers in memory (the name is a
  leftover of the old sidecar file).
- Generated from the C source: `GeneratedLegacyPictureCatalog` and `GeneratedLegacyPictureTerrain`
  (`original/src/data/pictures.c`); `PictureCatalog` and `TerrainGridBuilder` build each map's terrain grid
  (material and speed per tile).
- `PictureMasks`: opaque-pixel masks of every picture (`resources/masks/pictures.mask`).
- `legacy/LegacyMapParser`: the raw `.MAP` reader (importer only).
- Resources: `scenes/`, `masks/`, `sounds/` (29 WAVs), `music/` (two MIDI files).

### `:game-sim`

All under `nl.tankarena.sim`.

| File | Role |
|---|---|
| `Match` | The match's state, setup, the tick pipeline and test hooks (600 lines) |
| `MatchWeapons`, `MatchStructures`, `MatchSmallObjects`, `MatchTrains`, `MatchAircraft`, `MatchObjectRules`, `MatchTerrain`, `MatchAi`, `MatchMission`, `MatchViews` | The match's rules per subsystem, as extension functions of `Match`: shots; buildings; bonuses, soldiers and A-bombs; trains; B52s and zeppelin; warps, locks, destroyers and enforcers; ground effects; computer tanks; goals and mission end; snapshot and radar |
| `Tank` | Vehicles: movement physics, heights, weapons, damage, shield, cloak, lights |
| `ComputerTankAi` | Port of `control_computer_tank()` ([ADR 0012](adrs/0012-port-the-computer-ai.md)) |
| `StructureGrid` | Buildings: strengths, links, ruins, goal shares, masks ([ADR 0011](adrs/0011-structure-grid-and-pixel-masks.md)) |
| `DualRules` | Player vs player: start pads and lives, flag hunt, races |
| `MatchSetup` | Mode (`SINGLE`, `DUAL`, `DUAL_VS_COMPUTER`) and who controls which tanks |
| `LegacyVehicles`, `GeneratedLegacyVehicles` | The 23-vehicle table from `original/src/data/tanks.c` |
| `MatchObjects` | Runtime-only objects: bonuses, soldiers, A-bombs |
| `AreaDamageResolver` | Blast damage for mines, mortars, rockets, A-bombs |
| `Tank`, `Turret`, `Projectile`, … (17 files) | Map objects: tank, turret, projectile, mine, rocket, mortar, goal, flag, product, lock, warp, destroyer, enforcer, train, zeppelin, B52, wall |
| `MatchClient` | `MatchClient` interface and `LocalMatchClient` |
| `MatchRecording` | Recorder, replay (`MatchReplay.play`, `replayFrames`), `GAME_RULES_VERSION` |
| `SimulationLoop` | Runs a match's ticks at 100 per second on its own thread |
| `SceneObjects` | `SceneObject`/`Steppable`/`Resolvable`, bodies, `SceneFile` (each object's state by type id), `MatchObjectList` |
| `map/` | `MapDocument`/`MapFile` (the `.tamap` format), `MapConversion` (document ↔ layers and objects), `ShippedMaps` (loader), `LegacyMapImport` (`.MAP` → document) |
| `CanonicalSceneBuilder`, `legacy/*` | The importer's object mapping (tools, tests and fixtures) |

### `:game-net`

- `NetMessages.kt`: `ClientMessage` (hello, inputs, leave) and `ServerMessage` (welcome, refused,
  lobby, match start with the map's `.tamap` text, frame, ended), sent as CBOR by `NetCodec`.
- `GameServer`: Ktor CIO WebSockets on port 47300; admits machines after comparing versions, gives
  their players the next free ids, keeps the lobby, and starts a `HostedMatch`.
- `HostedMatch`: a `LocalMatchClient` plus the joined players' latest controls; sends one frame per
  step (33 a second) with the events since the last one and only that machine's views.
- `DeltaFrames.kt`: `DeltaEncoder` (per machine, on the server) and `DeltaDecoder` make each frame
  the difference with the one before; the WebSocket deflates them (permessage-deflate).
- `LanDiscovery.kt`: `LanAnnouncer` (the lobby's beacon on UDP port 47301, once a second, to every
  broadcast address) and `LanFinder` (the games heard lately).
- `NetSeries`: the rounds of a game on the host: vehicle choice with a deadline, rounds until the
  match is decided, the result to every machine.
- `RelayProtocol.kt`, `RelayServer`, `RelayHost`, `RelayDirectory` ([ADR 0028](adrs/0028-internet-relay-and-lobby.md)):
  the relay's control messages and game codes, the relay itself, a host's publication with a tunnel
  per guest, the public game list.
- `GameClient` (connection, lobby, vehicle choice, results) and `RemoteMatchClient`: a `MatchClient` that sends its players'
  controls once a step and plays the frames back about 100 ms behind.

### `:game-ui`

| Package | Contents |
|---|---|
| `ui/compose/menu` | Main and mode menus, mission-code prompt and list, `PasswordStore`, game options and controls, tank selection, intro, text pages, briefing and end banners |
| `ui/compose/extended` | Extended's own look: home, campaign, local game, join, settings, help, texts |
| `ui/compose/legacy` | Mode 13h screen and fonts (generated), `FliAnimation` |
| `ui/compose` | `LegacyScreen` (640×480 and 320×200 screens, fades), `GammaCorrection`, the shell's screen states |
| `ui/compose/hud` | `HudOverlay` (status display, radar, pilot face and messages, banners, arrow), `NightHud` |
| `render` | `TankArenaViewport` (one player window), `RenderScene`, `PictureCache`, `ClientScene`, terrain and top-layer drawing, sprite catalog, `NightLighting`, `PaletteCycling`, map window layout |
| `input` | `InputState`, `ControlMapping` (relative and direct steering, aim direction), `PadReading` (also twin sticks), `GamepadMenuKeys`, `Seat` |
| `audio` | `AudioBackend`, `AudioManager` (the software mixer's logic) |

### `:app-desktop`

| Package | Contents |
|---|---|
| `app/desktop` | `Main.kt` (window, screen state machine, gameplay screen), `DesktopControls`, `GameplayFrame`, editor screens, replay screens, `UserMapStore`, `RecordingStore`, `PerfLog` |
| `audio` | `DesktopAudioBackend` (`javax.sound`), `MusicPlayer` (MIDI) |
| `input` | `Gamepads` (Jamepad) |

### `:tools-mapconv`

`Main.kt` (subcommands) and the extractors (pictures, fonts, night tables, masks, vehicle and terrain
tables) over `:game-content` and `:game-sim`; see
[assets.md](assets.md#tools-mapconv).

## Boundaries

- `:game-protocol`, `:game-content` and `:game-sim` contain no Compose code; desktop-only APIs
  (`javax.sound`, Jamepad, the player's folders) are in `:app-desktop`.
- Only `:game-sim` decides gameplay. The client renders snapshots and sends inputs.
- No runtime code reads repository folders (`original/MAPS/`, `original/DATA/`, `original/unpacked/`); everything shipped comes
  from the class path. Only the tools and some tests read them.
- `:game-sim` depends only on `:game-content`, `:game-protocol` and kotlinx.serialization. No module
  uses Kubriko (removed 2026-10-19, [ADR 0019](adrs/0019-plain-kotlin-no-kubriko.md)).

## A match, step by step

1. **Choose.** The shell picks a mission (by code, [ADR 0015](adrs/0015-ship-the-original-69-maps.md)
   for which maps exist) or, in player vs player, a map and each player's vehicle on the tank selection
   screen (which itself runs `select.tamap` as a live match).
2. **Create.** `LocalMatchClient.fromMap(document, mode, recordAs, dual)` calls `Match.fromMap`:
   `MapConversion` restores fresh map objects and the layers from the `.tamap` document,
   `MatchSetup.assignControl` gives tanks to players per mode, and the terrain grid and
   `StructureGrid` are built from the layers. `initialize()` assigns `actorId`s, start
   shields, `DualRules`, trains, B52s, locks, fallen turrets and bonuses.
3. **Run.** `SimulationLoop` calls the gameplay step 100 times per second
   ([ADR 0014](adrs/0014-simulation-thread-and-recordings.md)): submit each player's `InputFrame`, then
   `tick()`.
4. **Tick.** `Match.tick()` runs a fixed pipeline: player intents to every tank in the
   player's group; AI tanks; turrets; terrain effects; the map objects' own updates and queued tank
   damage (`MatchObjectList.step`, in the order objects were added);
   projectile hits on structures and vehicles; flames; small objects; vehicle overlaps; mines,
   rockets, mortars, soldiers, A-bombs, warps, locks, destroyers, enforcers, trains, B52s and
   zeppelins; goals and the mission rules; then the snapshot.
5. **Publish.** The tick returns a `WorldSnapshot` (actor states, events, changed tiles, flash);
   `LocalMatchClient` wraps it with one `PlayerView` per player into a `ServerFrame`. Events go to a
   queue per player window and to the audio manager; the finished view is published to the UI thread.
6. **Draw.** Each player window is a `TankArenaViewport`: `ClientScene.sync` adds, updates and removes
   render actors by `actorId` in its `RenderScene`, effects play from the queued events, and the scene
   is drawn on a Compose `Canvas` through the camera (terrain, then objects by layer, then the top
   layer); night and colour cycling are applied as runtime shaders. The HUD and radar are a
   Compose canvas over the viewport.
7. **End.** When the mission rules decide (goal counter, last human tank lost, elimination fallback;
   rounds in player vs player), a `MissionResult` event ends the match after the original's 3-second
   run-on; the client fades out, saves the recording and shows the success or failure text.

## Replication

- The snapshot is a **projection**, not the scene format: `projectToWireState` copies each map
  object's fields into its protocol `ActorState`, then the runtime objects and changed structure tiles
  are added. Scene files and the wire therefore use different types.
- `actorId`s are stable for the life of a match; the client keys its render actors by them.
- Each `PlayerView` carries the camera (following the tank, or the rocket marker while it is out),
  `HudState`, radar contacts and the lock marker.

## Versions

| Constant | Value | Where | Checked |
|---|---|---|---|
| `PROTOCOL_VERSION` | 8 | `game-protocol/.../snapshot/WorldSnapshot.kt` | `decodeWorldSnapshot` (tests only today) |
| `MAP_FORMAT_VERSION` | 1 | `game-sim/.../sim/map/MapDocument.kt` | `MapFile.read` refuses newer maps |
| `GAME_RULES_VERSION` | 11 | `game-sim/.../MatchRecording.kt` | `RecordedPlaythroughTest` requires equality |

## Threads

- **UI thread** (Compose): input events, screens, drawing.
- **`simulation`** thread: the match ticks (`SimulationLoop`), one per running match.
- **`audio-mixer`** thread: mixes up to 32 voices onto one output line.
- Java's MIDI sequencer thread for music.

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

- `Tank.kt` is 1,760 lines; vehicle movement, weapons and damage could be split like `Match` was.
  The subsystems in the `Match*.kt` files still share `Match`'s state (made `internal` for them).
- `fromCanonicalMap` builds test fixtures through the importer's `CanonicalSceneBuilder`, so tests
  depend on the importer's intermediate; they could build map documents instead.
- `MapSceneSidecar` still carries its old name; `MapInfo` would say what it is.

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
