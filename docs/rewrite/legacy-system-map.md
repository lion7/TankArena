# Legacy System Map

Where each part of the original C source (`src/`, about 23,800 lines of C and assembly) lives in the
rewrite. Kotlin paths are abbreviated: `server/` is `game-server/src/com/tankarena/sim/kubriko/server/`,
`client/` is `game-client/src/com/tankarena/`, `content/` is `game-content/src/com/tankarena/`.

## Gameplay (`src/tanks`, `src/check`, `src/objects`, `src/world`)

| Original | Rewrite |
|---|---|
| `tanks/control.c` (movement, heights, terrain effects) | `server/ServerTankActor.kt`; terrain effects in `ServerMatchPrototype.applyTerrainEffects` |
| `tanks/fire.c` (weapons) | `server/ServerTankActor.kt` (`stepFire`, `fire*`) |
| `tanks/tank.c` (damage, death, start shield) | `server/ServerTankActor.kt` (`applyPendingResolutions`, `respawn`) |
| `tanks/computer.c` (AI) | `server/ComputerTankAi.kt` |
| `check/hitwall.c`, `check/overlap.c` | `server/StructureGrid.kt`, `ServerTankActor.overlapWithMask` |
| `check/hitobj.c` (shot hits) | `ServerMatchPrototype.checkProjectileVehicleHits`, flame contacts |
| `check/collide.c` (vehicle collisions, mines) | `ServerTankActor.collideWithVehicle`, `ServerMatchPrototype.triggerMineContacts` |
| `check/pit.c` | `ServerMatchPrototype.inPit`, `ServerTankActor.stepFall` |
| `check/math.c`, `check/raw_over.s` | Kotlin math; `content/PictureMasks.kt` |
| `objects/turret.c` | `server/ServerTurretActor.kt` |
| `objects/rocket.c`, `mortar.c`, `mine.c`, `abomb.c` | `ServerRocketActor`, `ServerMortarActor`, `ServerMineActor`, `MatchObjects.Abomb` |
| `objects/man.c`, `bonus.c` | `server/MatchObjects.kt` (`Soldier`, `Bonus`) |
| `objects/train.c` | `ServerMatchPrototype.controlTrain`, `ServerTrainActor` |
| `objects/b52.c`, `zeppelin.c` | `ServerMatchPrototype` (B52 and zeppelin steps), `ServerB52Actor`, `ServerZeppelinActor` |
| `objects/goal.c`, `flag.c`, `product.c`, `lock.c`, `warp.c`, `destroy.c`, `enforce.c` | `ServerMatchPrototype` steps plus the matching `Server*Actor` |
| `objects/player.c` (start pads) | `server/DualRules.kt`, `ServerTankActor` (pads, refill) |
| `objects/objects.c` (object list, goal counter) | `ServerMatchPrototype` (`modifyGoalCounter`, mission rules) |
| `world/bullet.c` | `server/ServerProjectileActor.kt` |
| `world/effects.c` | `GameEvent` effects; drawn by `client/render/kubriko/ClientScene.kt` |
| `world/status.c` (status display, pilot messages) | `client/ui/compose/hud/HudOverlay.kt`; messages from the server |
| `world/under.c`, `world/rawunder.s` (terrain drawing) | `client/render/kubriko/TerrainActor.kt` |
| `world/parts.c` (debris) | **not ported** |
| `world/transp.c` (conveyors, rotating platforms) | **not ported** |

## Game flow (`src/game`)

| Original | Rewrite |
|---|---|
| `main.c` (start-up, command-line switches) | `client/app/desktop/Main.kt`; switches **not ported** |
| `mainloop.c` (game loop, windows, drawing) | `client/app/desktop/SimulationLoop.kt`, `Main.kt` gameplay screen, `RenderSceneSupport.legacyMapWindows` |
| `controls.c` (keys, F-keys) | `KeyBinding` in `Main.kt`; pause, F11, F12, PrtScr, +/- **not ported** |
| `menu.c`, `prompt.c`, `title.c` | `client/ui/compose/menu/` |
| `single.c`, `dualvc.c`, `listcod.c`, `passwd.c` | `MissionCodes`, `MissionGate`, `PasswordStore`; `server/MatchSetup.kt` |
| `dual.c`, `seltank.c`, `sellist.c` | `DualMatch`, `TankSelectScreen`, map prompt; `server/DualRules.kt` |
| `mapfiles.c` (load/save maps, map-type rules) | importer (`content/legacy/LegacyMapParser.kt`, `server/legacy/`), `MissionCatalog` filters |
| `init.c` (pictures, night pictures) | `tools-mapconv` extractors; `NightLighting`, `NightHud` |
| `night.c` | `client/render/kubriko/NightLighting.kt` |
| `radar.c` | `HudOverlay` radar; contacts from `ServerMatchPrototype.buildRadar` |
| `settings.c` (`TANK.CFG`) | `GameOptionsStore` (`settings.json`) |

## Graphics, sound, hardware (`src/graph`, `src/hardware`)

| Original | Rewrite |
|---|---|
| `graph/palette.c` (fades, gamma, colour cycling, flash) | `client/ui/compose/LegacyScreen.kt`, `GammaCorrection.kt`, `render/kubriko/PaletteCycling.kt` |
| `graph/mode13.c`, `5x5text.c`, `5x5font.s` | `client/ui/compose/legacy/Mode13Screen.kt`, `GeneratedLegacyFonts.kt` |
| `graph/fli.c` | `client/ui/compose/legacy/FliAnimation.kt` |
| `graph/sprites.s`, `buffer.c`, `graph.c` | Kubriko sprites and Compose drawing |
| `graph/grab.c` (screenshots) | **not ported** |
| `hardware/sound.c` | `client/audio/` (`AudioManager`, `DesktopAudioBackend`, `MusicPlayer`) |
| `hardware/timer.c` | `game-protocol/.../core/FixedStepClock.kt`, `SimulationLoop` |
| `hardware/keyboard.s` | Compose key events |
| `hardware/joystick.s` | **not ported** |

## Data and tools

| Original | Rewrite |
|---|---|
| `data/tanks.c` | `server/GeneratedLegacyVehicles.kt` (`extract-vehicles`) |
| `data/pictures.c` | `content/GeneratedLegacyPictureCatalog.kt`, `GeneratedLegacyPictureTerrain.kt` |
| `edit/*` (map editor) | Planned: an editor screen in the game ([ADR 0021](adrs/0021-editor-in-the-game.md)); today only the `:game-editor` stub |
| `setup/*` (sound and video setup) | out of scope |
| `utils/*` (picture packing and conversion) | `tools-mapconv` extractors work in the other direction |
| `include/*` | constants live next to the code that uses them, with C references |

Data files and their converted forms are listed in [assets.md](assets.md).
