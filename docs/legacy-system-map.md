# Legacy System Map

Where each part of the original C source (`original/src/`, about 23,800 lines of C and assembly)
lives in the rewrite. Kotlin paths are abbreviated: `sim/` is `game-sim/src/nl/tankarena/sim/`,
`ui/` is `game-ui/src/nl/tankarena/`, `content/` is `game-content/src/nl/tankarena/`.

## Gameplay (`tanks`, `check`, `objects`, `world`)

| Original | Rewrite |
|---|---|
| `tanks/control.c` (movement, heights, terrain effects, wrecks) | `sim/Tank.kt`; terrain effects in `sim/MatchTerrain.kt` (`applyTerrainEffects`, `applyUndergroundEffects`) |
| `tanks/fire.c` (weapons) | `sim/Tank.kt` (`stepFire`, `fire*`), `sim/MatchWeapons.kt` |
| `tanks/tank.c` (damage, death, start shield) | `sim/Tank.kt` (`applyPendingResolutions`), `sim/DualRules.kt` (respawns) |
| `tanks/computer.c` (AI) | `sim/ComputerTankAi.kt`, `sim/TankSurroundings.kt`, `sim/MatchAi.kt` |
| `check/hitwall.c`, `check/overlap.c` | `sim/StructureGrid.kt`, `Tank.overlapWithMask` |
| `check/hitobj.c` (shot hits) | `Match.checkProjectileVehicleHits`, flame contacts (`sim/MatchStructures.kt`) |
| `check/collide.c` (vehicle collisions, mines) | `Tank.collideWithVehicle`, `Match.triggerMineContacts` |
| `check/pit.c` | `Match.inPit`, `Tank.stepFall` |
| `check/math.c`, `check/raw_over.s` | `DetMath`; `content/PictureMasks.kt` |
| `objects/turret.c` | `sim/Turret.kt` |
| `objects/rocket.c`, `mortar.c`, `mine.c`, `abomb.c` | `sim/Rocket.kt`, `Mortar.kt`, `Mine.kt`; A-bombs in `sim/MatchObjects.kt` |
| `objects/man.c`, `bonus.c` | `sim/MatchObjects.kt`, `sim/MatchSmallObjects.kt` |
| `objects/train.c` | `sim/MatchTrains.kt` (`controlTrain`), `sim/Train.kt` |
| `objects/b52.c`, `zeppelin.c` | `sim/MatchAircraft.kt`, `sim/B52.kt`, `sim/Zeppelin.kt` |
| `objects/goal.c`, `flag.c`, `product.c`, `lock.c`, `warp.c`, `destroy.c`, `enforce.c` | `sim/MatchObjectRules.kt`, `sim/MatchMission.kt` and the matching map object |
| `objects/player.c` (start pads) | `sim/DualRules.kt`, `sim/Tank.kt` (pads, refill) |
| `objects/objects.c` (object list, goal counter) | `sim/Match.kt`, `sim/SceneObjects.kt` (`MatchObjectList`), `modifyGoalCounter` |
| `world/bullet.c` | `sim/Projectile.kt` |
| `world/effects.c` | `GameEvent` effects; drawn by `ui/render/ClientScene.kt` |
| `world/parts.c` (debris) | Burning pieces in the simulation (`Match.stepFlameParts`); pixels, specks and wall pieces drawn from a debris event (`ui/render/DebrisPieces.kt`) |
| `world/transp.c` (conveyors, turntables) | `sim/MatchTerrain.kt` (`applyTransport`); carries vehicles and soldiers only ([parity.md](parity.md#terrain-and-structures)) |
| `world/status.c` (status display, pilot messages) | `ui/ui/compose/hud/HudOverlay.kt`; messages from the simulation |
| `world/under.c`, `world/rawunder.s` (terrain drawing) | `ui/render/TerrainActor.kt` |

## Game flow (`game`)

| Original | Rewrite |
|---|---|
| `main.c` (start-up, command-line switches) | `app-desktop/…/Main.kt`, `ui/app/TankArenaApp.kt`; cheats in `sim/Cheats.kt` |
| `mainloop.c` (game loop, windows, drawing, static view) | `sim/Match.kt` (`tick`, `step`), `sim/SimulationLoop.kt`, `ui/app/GameplayFrame.kt`, `ui/app/StaticNoise.kt`, `legacyMapWindows` |
| `controls.c` (keys, F-keys, pause, gamma, radar, screenshot, window size) | `ui/app/DesktopControls.kt`, `ui/input/`, `ui/app/TankArenaApp.kt` |
| `menu.c`, `prompt.c`, `title.c` | `ui/ui/compose/menu/` |
| `single.c`, `dualvc.c`, `listcod.c`, `passwd.c` | `MissionCodes`, `MissionGate`, `MissionCatalog`, `PasswordStore`; `sim/MatchSetup.kt` |
| `dual.c`, `seltank.c`, `sellist.c` | `DualMatch`, `TankSelectScreen`, the map prompt; `sim/DualRules.kt`, `sim/DualVehicles.kt` |
| `mapfiles.c` (load and save maps, map-type rules) | `content/legacy/LegacyMapParser.kt`, `sim/legacy/`, `sim/map/LegacyMapImport.kt`, `sim/map/LegacyMapExport.kt`; `MissionCatalog` filters |
| `init.c` (pictures, night pictures) | `tools-mapconv` extractors; `NightLighting`, `NightHud` |
| `night.c` | `ui/render/NightLighting.kt` |
| `radar.c` | `HudOverlay` radar; contacts from `Match.buildRadar` |
| `settings.c` (`TANK.CFG`) | `GameOptionsStore` (`settings.json`) |

## Graphics, sound, hardware (`graph`, `hardware`)

| Original | Rewrite |
|---|---|
| `graph/palette.c` (fades, gamma, colour cycling, flash) | `ui/ui/compose/LegacyScreen.kt`, `GammaCorrection.kt`, `ui/render/PaletteCycling.kt` |
| `graph/mode13.c`, `5x5text.c`, `5x5font.s` | `ui/ui/compose/legacy/Mode13Screen.kt`, `GeneratedLegacyFonts.kt` |
| `graph/fli.c` | `ui/ui/compose/legacy/FliAnimation.kt` |
| `graph/sprites.s`, `buffer.c`, `graph.c` | `ui/render/RenderScene.kt`, `PictureCache`, Compose drawing |
| `graph/grab.c` (screenshots) | `GamePlatform.screenshot` (PNG instead of BMP) |
| `hardware/sound.c` | `ui/audio/` (`AudioManager`, `MixerAudioBackend`); output and MIDI per app |
| `hardware/timer.c` | `game-protocol/…/core/FixedStepClock.kt`, `sim/LegacyStep.kt`, `SimulationLoop` |
| `hardware/keyboard.s` | Compose key events, `GameKeyHook` |
| `hardware/joystick.s` | Gamepads: Jamepad on the desktop, key and motion events on Android; `ui/input/` |

## Data, editor and tools

| Original | Rewrite |
|---|---|
| `data/tanks.c` | `sim/GeneratedLegacyVehicles.kt` (`extract-vehicles`) |
| `data/pictures.c` | `content/GeneratedLegacyPictureCatalog.kt`, `GeneratedLegacyPictureTerrain.kt` |
| `edit/*` (map editor) | `ui/app/Editor*.kt`, `sim/map/MapEditor.kt`, `sim/map/MapObjects.kt` ([ADR 0021](adrs/0021-editor-in-the-game.md)) |
| `setup/*` (sound and video setup) | The controls part is the in-game controls setup; sound and video setup are out of scope |
| `utils/*` (picture packing and conversion) | `tools-mapconv` extractors work in the other direction |
| `include/*` | Constants live next to the code that uses them, with C references |

Data files and their converted forms are listed in [assets.md](assets.md).
