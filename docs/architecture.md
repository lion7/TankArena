# Tank Arena Rewrite Architecture

How the Kotlin rewrite is built today (2026-10-22). The reasons are in the [ADRs](adrs/README.md);
the original game it reproduces is described in [`original/docs/`](../original/docs/) and its C source
is in `original/src/`.

## Overview

```text
 keyboard, mouse,   +---------- app-desktop / app-android: window or activity, GamePlatform ----------+
 pads, touch  --->  | game-ui: TankArenaApp                                                           |
                    |   shell: intro, Classic menus (320x200), Extended menus, editor, replays        |
                    |   gameplay screen                                                               |
                    |     input layer: devices -> InputState -> InputFrame per player                 |
                    |     SimulationLoop thread (100 ticks/s): MatchClient.tick() -> ServerFrame      |
                    |     UI thread, every display frame: one view per player                         |
                    |       RenderScene on a Canvas (ClientScene.sync) + status display and radar     |
                    |     AudioManager -> MixerAudioBackend -> PcmOutput; Music                       |
                    +---------------------------------------------------------------------------------+
                          | LocalMatchClient (in process)       | RemoteMatchClient (game-net)
                          v                                     v
                    +-------------- game-sim --------------+  host: GameServer + HostedMatch,
                    | Match: map objects, StructureGrid,   |  app-server, app-relay
                    | ComputerTankAi, DualRules, mission   |
                    | rules -> WorldSnapshot + PlayerViews |
                    +--------------------------------------+
                          | ShippedMaps (class path)
                    +------------ game-content ------------+
                    | maps/*.tamap, picture catalog,       |
                    | terrain tables, masks, sounds, music |
                    +--------------------------------------+
```

## Modules

Ten modules ([ADR 0025](adrs/0025-multiplatform-modules-and-android.md)); the libraries compile for
the JVM and Android. Packages are under `nl.tankarena`; artifacts are `nl.tankarena:<module>`.
Dependencies point one way:

```text
game-protocol <- game-content <- game-sim <- game-net <- game-ui <- app-desktop, app-android
                                                 game-net <- app-server, app-relay
                                 game-sim <- tools-mapconv
```

### `:game-protocol`

- `core/`: `FixedStepClock` (100 ticks/s), `LegacyDirections` (the 16-step compass), `DetMath`
  (trigonometry and roots through `StrictMath`; the simulation uses only this).
- `protocol/Frames.kt`: `InputFrame` (one per player per tick), `Team`.
- `input/PlayerIntent.kt`: `PlayerIntentFrame`, the simulation's internal intent, also made by the AI
  and stored in recordings.
- `protocol/snapshot/`: `WorldSnapshot` and `PROTOCOL_VERSION`, the `ActorState` hierarchy (one state
  per kind of object, keyed by `actorId`), `GameEvent` (sounds, effects, explosions, debris, messages,
  mission result, …), `PlayerView`, `HudState`, `RadarContact`, `LockMarker`, `TileState`,
  `ServerFrame`, and the terrain types.

### `:game-content`

- `resources/maps/`: the 69 shipped maps as `.tamap` files and `index.txt`
  ([content-format.md](content-format.md)).
- `resources/`: `masks/pictures.mask`, `sounds/` (29 WAVs), `music/` (two MIDI files),
  `structures/map_0..3.dat` (the editor's structure libraries).
- Generated from the C source: `GeneratedLegacyPictureCatalog`, `GeneratedLegacyPictureTerrain`
  (`original/src/data/pictures.c`); `PictureCatalog` and `TerrainGridBuilder` build each map's terrain
  grid (material and speed per tile).
- `PictureMasks` (opaque-pixel masks of every picture), `StructureLibrary`, `LegacySpriteResources`
  (picture names), `MapSceneSidecar` (a map's header, texts and layers in memory; the name is a
  leftover).
- `legacy/LegacyMapParser`: the raw `.MAP` reader.

### `:game-sim`

All under `nl.tankarena.sim`.

| File | Role |
|---|---|
| `Match` | The match's state, setup, the tick and step pipeline, test hooks |
| `MatchWeapons`, `MatchStructures`, `MatchSmallObjects`, `MatchTrains`, `MatchAircraft`, `MatchObjectRules`, `MatchTerrain`, `MatchAi`, `MatchMission`, `MatchViews` | The rules per subsystem, as extension functions of `Match`: shots; buildings and debris; bonuses, soldiers and A-bombs; trains; B52s and zeppelin; warps, locks, destroyers, enforcers; conveyors and ground effects; computer tanks; goals and the mission's end; snapshot, views and radar |
| `Tank` | Vehicles: movement, heights, weapons, damage, shield, cloak, lights, wrecks |
| `Turret`, `Projectile`, `Mine`, `Rocket`, `Mortar`, `Goal`, `Flag`, `Product`, `Lock`, `Warp`, `Destroyer`, `Enforcer`, `Train`, `Zeppelin`, `B52` | The map objects, each with its `State` (its record in the map file) |
| `ComputerTankAi`, `TankSurroundings` | Port of `control_computer_tank()` ([ADR 0012](adrs/0012-port-the-computer-ai.md)) |
| `StructureGrid` | Buildings: strengths, links, ruins, goal shares, masks ([ADR 0011](adrs/0011-structure-grid-and-pixel-masks.md)) |
| `DualRules`, `DualVehicles`, `MatchSetup` | Player vs player (pads, lives, flag hunt, races, vehicle choice); modes and who controls which vehicles |
| `LegacyVehicles`, `GeneratedLegacyVehicles` | The 23-vehicle table from `original/src/data/tanks.c` |
| `MatchObjects`, `AreaDamageResolver` | Runtime-only objects (bonuses, soldiers, A-bombs, burning pieces); blast damage |
| `SceneObjects` | `SceneObject`, bodies, `MatchObjectList` (objects updated in insertion order), `SceneFile` (object states by type id) |
| `LegacyStep` | `STEP_TICKS`, `STEP_SECONDS`, timers that run out on steps ([ADR 0002](adrs/0002-fixed-step-100hz.md)) |
| `Ruleset`, `Cheats` | Classic and Extended with `RuleFlags` and their rules versions; `-GOD` and the other cheats |
| `MatchClient` | `MatchClient`, `LocalMatchClient`, `ReplayMatchClient` |
| `MatchRecording` | Recorder, replay, `GAME_RULES_VERSION` |
| `SimulationLoop` | Runs a match's ticks at 100 a second on its own thread |
| `map/` | `MapDocument` and `MapFile` (the `.tamap` format), `MapConversion` (document ↔ layers and objects), `ShippedMaps`, `LegacyMapImport`, `LegacyMapExport`, `MapEditor` and `MapObjects` (the editor's rules) |
| `legacy/`, `CanonicalSceneBuilder` | The importer's object parser and mapping; also builds test fixtures |

### `:game-net`

- `NetMessages.kt`: `ClientMessage` (hello, controls, vehicle choice, leave) and `ServerMessage`
  (welcome, refused, lobby, match start with the map's `.tamap` text, frame, round over, ended), sent as
  CBOR by `NetCodec`; `NET_VERSION`, port 47300, path `/tankarena`.
- `GameServer`: Ktor CIO WebSockets; admits machines after comparing versions, gives their players the
  next free places, keeps the lobby, and starts a `HostedMatch`.
- `HostedMatch`: a `LocalMatchClient` plus the joined players' latest controls; one frame per step to
  every other machine, with the step's events and only that machine's views.
- `DeltaFrames.kt`: `DeltaEncoder` (per machine, on the host) and `DeltaDecoder` make each frame the
  difference with the one before; the WebSocket deflates them.
- `LanDiscovery.kt`: `LanAnnouncer` (UDP beacon on port 47301, once a second) and `LanFinder`.
- `NetSeries`: rounds on the host: vehicle choice with a deadline, rounds until the match is decided,
  results to every machine.
- `GameClient` (connection, lobby, vehicle choice, results) and `RemoteMatchClient` (a `MatchClient`
  that sends its players' controls once a step and plays frames back about 100 ms behind).
- `RelayProtocol.kt`, `RelayServer`, `RelayHost`, `RelayDirectory`
  ([ADR 0028](adrs/0028-internet-relay-and-lobby.md)): the relay's messages and game codes, the relay
  itself, a host's publication with a tunnel per guest, the public game list.

### `:game-ui`

| Package | Contents |
|---|---|
| `app` | `TankArenaApp` (the screen state machine and the gameplay screen), `GamePlatform`, `AppDirs`, `GameplayFrame` (views, status display, banners), `DesktopControls` (keys, mouse and pads to `InputState`), `TouchControls`, `MousePointer`, the editor (`EditorScreens`, `EditorMapPage`, `EditorObjects`), `ReplayScreens`, `NetworkSession`, `RecordingStore`, `UserMapStore`, `StepInterpolation`, `StaticNoise`, `PerfLog` |
| `ui/compose/menu` | Classic's menus: main and mode menus, mission-code prompt and list, `PasswordStore`, game options and `GameOptionsStore`, controls setup, tank selection, intro, text pages, yes/no, banners |
| `ui/compose/extended` | Extended's look: home, campaign, local game, join, network host/join/lobby/results, settings, help, texts |
| `ui/compose/legacy` | Mode 13h screen and fonts (generated), `FliAnimation` |
| `ui/compose` | `LegacyScreen` (640 × 480 and 320 × 200 screens, fades), `GammaCorrection`, the shell's screen states |
| `ui/compose/hud` | `HudOverlay` (status display, radar, pilot face and messages, banners, arrow), `NightHud` |
| `render` | `TankArenaViewport` (one view), `RenderScene`, `PictureCache`, `ClientScene`, render actors, terrain and top-layer drawing, `DebrisPieces`, `NightLighting`, `PaletteCycling`, `PlatformGraphics` (`expect`), map window layout |
| `input` | `InputState`, `ControlMapping`, `PadReading`, `GamepadMenuKeys`, `Seat`, key names |
| `audio` | `AudioBackend`, `AudioManager` (the original's sound rules), `MixerAudioBackend` (the shared software mixer, `PcmOutput`) |

Resources: the 2,960 extracted pictures, night tables and light cones in `composeResources/drawable/`;
`resources/backgrounds/`, `fli/` and `help/`. Platform code: `src@jvm/` and `src@android/`.

### The apps

| Module | Contents |
|---|---|
| `:app-desktop` | `Main.kt` (window, key hook, `--classic` / `--extended`, cheats), `DesktopAudioBackend` (`javax.sound`), `MusicPlayer` (Java MIDI), `Gamepads` (Jamepad) |
| `:app-android` | `MainActivity` (activity, back button, folders, multicast lock), `AndroidPlatform` (`AudioTrack`, `MediaPlayer`, pads from key and motion events) |
| `:app-server` | The dedicated server's command line around `GameServer` and `NetSeries` |
| `:app-relay` | The relay's command line around `RelayServer` |
| `:tools-mapconv` | Subcommands and the extractors (pictures, fonts, night tables, masks, vehicle and terrain tables); see [assets.md](assets.md#tools-mapconv) |

## Boundaries

- `:game-protocol`, `:game-content`, `:game-sim` and `:game-net` contain no Compose code.
  Platform APIs (`javax.sound`, Java MIDI, Jamepad, Android) are only in the apps and behind
  `GamePlatform`, `PcmOutput` and `PlatformGraphics`.
- Only `:game-sim` decides gameplay. The screens render frames and send inputs.
- No runtime code reads the repository's `original/` folder; everything shipped comes from the class
  path. Only the tools and some tests read it.

## A match, step by step

1. **Choose.** The shell picks a mission by code ([ADR 0015](adrs/0015-ship-the-original-69-maps.md)
   for which maps exist) or, in player vs player, a map and each player's vehicle on the tank
   selection screen (which itself runs `select.tamap` as a live match).
2. **Create.** `LocalMatchClient.fromMap(document, mode, recordAs, dual, ruleset, cheats, players)`
   calls `Match.fromMap`: `MapConversion` restores fresh map objects and the layers from the document,
   `MatchSetup` gives vehicles to players per mode, and the terrain grid and `StructureGrid` are built
   from the layers. `initialize()` assigns `actorId`s, start shields, `DualRules`, trains, B52s, locks,
   fallen turrets and bonuses.
3. **Run.** `SimulationLoop` calls the match 100 times a second
   ([ADR 0014](adrs/0014-simulation-thread-and-recordings.md)): submit each player's `InputFrame`,
   then `tick()`.
4. **Tick and step.** `Match.tick()` latches the controls and runs a game step on every third tick
   ([ADR 0002](adrs/0002-fixed-step-100hz.md)). A step follows `game_control()`: the players' controls
   to every vehicle in their group; conveyors and turntables; shots move and hit structures, vehicles
   and small objects; burning pieces; vehicles on top of each other; then every object's own control
   (ground effects, computer tanks, rockets, mortars, mines, turrets, soldiers, A-bombs, warps, locks,
   destroyers, enforcers, trains, zeppelins, products, goals); vehicle deaths, wrecks and hand-overs;
   the mission rules.
5. **Publish.** The tick returns a `WorldSnapshot` (actor states, events, changed tiles, flash);
   `LocalMatchClient` adds one `PlayerView` per player into a `ServerFrame`. Events go to a queue per
   view and to the audio manager; the finished frame is published to the UI thread.
6. **Draw.** Each view is a `TankArenaViewport`: `ClientScene.sync` adds, updates and removes render
   actors by `actorId`, effects play from the queued events, and `RenderScene` draws terrain, objects
   by layer and height, and the top layer on a Compose `Canvas` through the view's camera; night and
   colour cycling are runtime shaders. The status display and radar are drawn over it. In Extended,
   smooth motion draws between the last two steps (`StepInterpolation`).
7. **End.** When the mission rules decide (goal counter, last human vehicle lost, elimination
   fallback; rounds in player vs player), a `MissionResult` event ends the match after the original's
   3-second run-on; the screen fades out, the recording is saved, and the success or failure text
   shows.

A network guest replaces steps 2–5 with `RemoteMatchClient`, which receives the host's frames
([ADR 0024](adrs/0024-networking.md)); a replay replaces them with `ReplayMatchClient`.

## Replication

- The snapshot is a **projection**, not the map format: `projectToWireState` copies each map object's
  fields into its `ActorState`, then the runtime objects and changed structure tiles are added. Map
  files and the wire therefore use different types.
- `actorId`s are stable for the life of a match; the screen keys its render actors by them.
- Each `PlayerView` carries the camera (following the vehicle, or the rocket marker while it is out),
  the `HudState`, radar contacts and the lock marker.

## Versions

| Constant | Value | Where | Checked |
|---|---|---|---|
| `GAME_RULES_VERSION` | 14 | `game-sim/…/sim/MatchRecording.kt` | Recordings; network handshake (Classic) |
| `EXTENDED_RULES_VERSION` | 7 | `game-sim/…/sim/Ruleset.kt` | Recordings; network handshake |
| `PROTOCOL_VERSION` | 9 | `game-protocol/…/snapshot/WorldSnapshot.kt` | Network handshake, LAN beacon |
| `NET_VERSION` | 4 | `game-net/…/net/NetMessages.kt` | Network handshake, LAN beacon, relay |
| `MAP_FORMAT_VERSION` | 1 | `game-sim/…/sim/map/MapDocument.kt` | `MapFile.read` refuses newer maps |

## Threads

- **UI thread** (Compose): input events, screens, drawing.
- **`simulation`**: the match's ticks (`SimulationLoop`), one per running match, also on a network
  host.
- **`audio-mixer`**: mixes up to 32 voices into one output.
- The platform's MIDI player for music.
- Network games: Ktor's coroutines for the WebSockets, and the LAN beacon and finder.

## Switches

Command line (desktop): `--classic`, `--extended`; the original's cheats `-GOD`, `-NOFAIL`,
`-LOADSOFWEAPONS`, `-NOLOCKLIMIT` (also `--god`, …); `-SETUP` opens the controls setup
([parity.md](parity.md#shell-and-meta-game)).

For development:

| Switch | Effect |
|---|---|
| `TANKARENA_PERF=1` | Logs tick time, loop and frame gaps and GC once a second to stdout and `./perf.log` |
| `TANKARENA_AUTOSTART=<code or name>` | Skips the menus into a one-player mission |
| `TANKARENA_AUTOSTART_PVP=1` | With `AUTOSTART`, goes to the tank selection for player vs player |
| `TANKARENA_QUIT_AFTER=<seconds>` | Closes the game after that time |
| `-Dtankarena.debugOverlay=true` | Draws the view and camera geometry |
| `-Dtankarena.assets.background=<path>` | Overrides the panel background picture |
| `TICK_BENCH`, `IDLE_SCAN`, `ALLOC_BENCH`, `FRAME_PROBE`, `DUMP_DIR`, `UPDATE_REFERENCE_RENDERS` | Test-only; see [testing-strategy.md](testing-strategy.md#on-demand-tools-pass-trivially-unless-switched-on) |

## Known structural debt

- `Tank.kt` is 1,965 lines; movement, weapons and damage could be split like `Match` was. The
  `Match*.kt` subsystems still share `Match`'s state (made `internal` for them).
- Test fixtures are built through the importer's `CanonicalSceneBuilder` (`Match.fromCanonicalMap`)
  instead of as map documents.
- `MapSceneSidecar` still carries its old name; `TankArenaWorld.NIGHT` is really the fourth world.
- `TankArenaApp.kt` holds the whole screen state machine and the gameplay screen in one file.
