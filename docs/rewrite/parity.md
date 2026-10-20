# Feature Parity

What the rewrite does and does not yet do compared with the original game, checked against the code
and the C source on 2026-10-19. The original is described in [`docs/game/`](../game/); where that
text and `src/` disagree, `src/` wins ([ADR 0010](adrs/0010-port-the-original-rules.md)).

**Parity** means: the 69 original maps play as in the original, in every mode, with the original's
rules, presentation, sound, controls and editor capabilities, except for the deviations and
exclusions listed below. Parity is the goal of the **Classic** ruleset; the Extended ruleset builds on
it with named, documented changes ([ADR 0023](adrs/0023-classic-and-extended-rulesets.md)). The order
of the open work is in the [roadmap](roadmap.md) (phase 3 for rules, phase 4 for the editor).

Legend for the open items: **missing** (not built), **partial** (built, something missing),
**differs** (built, behaves differently from the original and must be fixed).

## Summary

| Area | State |
|---|---|
| Engine, tick, game loop | Done |
| Vehicles and movement | Done, 4 differences |
| Weapons | Done except mine and mortar details |
| Terrain and structures | Done except conveyors, rotating platforms, debris and terrain effects |
| Map objects | Done except supermarket rules, goal who=3, warp details |
| Computer AI | Done (port of `computer.c`), one field not imported |
| Modes | Done except supermarket mayhem |
| Shell, menus, options | Done except in-game keys, controls setup, command-line switches |
| Status display, radar, presentation | Done, 2 differences |
| Audio and music | Done, 3 sound differences |
| Map editor | Mostly missing |
| Content and import | Done for the game; editor-only data not converted |

## Intentional deviations

Decided differences from the original. They are not parity bugs.

- **Architecture:** authoritative server and render-only client, a simulation thread, JSON maps
  ([ADR 0007](adrs/0007-authoritative-headless-kubriko-server.md),
  [ADR 0014](adrs/0014-simulation-thread-and-recordings.md),
  [ADR 0008](adrs/0008-kubriko-and-scene-json.md)).
- **Player data as JSON** in the user's folders instead of `TANK.CFG` / `PASSWD.DAT`
  ([ADR 0020](adrs/0020-user-data-as-json.md)).
- **Elimination fallback win** (`ALL_ENEMIES_ELIMINATED`): when a map's object contributions cannot
  reach 100 %, destroying every enemy wins. The original has no such rule; it is kept so that no map
  becomes unwinnable. (Its reachability sum ignores the goal layer, locks, trains, B52s and destroyers,
  so it may fire on maps that the original could still decide; check that when touching it.)
- **Match recordings** of every mission for regression tests ([ADR 0014](adrs/0014-simulation-thread-and-recordings.md)).
- **Integer-scaled 640×480 window** on modern displays; no 320×200 or other video modes
  ([ADR 0013](adrs/0013-original-presentation.md)).
- **Java MIDI synthesizer** instead of a sound card's ([ADR 0018](adrs/0018-audio-and-music.md)).
- **Only the 69 maps of the original release** ([ADR 0015](adrs/0015-ship-the-original-69-maps.md)).

## Out of scope

- The shareware build's zeppelin that the game spawns by itself (`src/game/mapfiles.c:324`).
- The DOS setup programs (`src/setup/`: sound card, video mode); the JDK picks the audio device.
- `VICTORY.FLI` and `THUNDER.FLI`: not in the original release either; the code plays them if present.
- Map locking/encryption of the original editor (`lock`, `protect`).

## Faithful quirks (not bugs)

Kept because the original does the same ([ADR 0010](adrs/0010-port-the-original-rules.md)):

- In player vs player every start pad of a player's vehicle kind spawns a tank and all of them obey
  that player's keys; F1–F10 is disabled in that mode (`src/game/controls.c:220`).
- Computer tanks stacked on each other at start destroy each other (FOREST, NIRVANA, NUKEBOMB, Q2,
  RACE_10, SUBURBS).
- Water never kills tanks; it slows them and hides the hull.
- BASE1's wagons have no engine and never move.
- Turrets cannot be shot; they fall with the building under them, and turrets placed off buildings
  vanish at start.

## Engine and game loop

Done: 100 ticks per second; legacy timers; deterministic seeded randomness; colour cycling; nuke
flash; fades; grey view for the losing player.

Open:

- **done** — the game step: one step every 3 timer ticks like `game_control()`
  (`src/game/mainloop.c:17-21`), timers running out on steps like `tdone()`, per-step rules (rocket
  acceleration, steering noise, wagon spacing, explosions) once per step, the start and bonus shield as
  15 blinks of `tinit(5)` after the hold (rules v12, 2026-10-19). See
  [ADR 0002](adrs/0002-fixed-step-100hz.md#the-originals-game-step-is-not-its-timer-tick).
- **unverified** — a vehicle pushing against another re-collides once per step, as in the original;
  not compared with footage.
- **done** — step order like `game_control()`: shots move and hit first (their damage counts in the same
  step), then vehicles on top of each other, then every object's control; shots fired in a step move
  from the next one. Within the objects the rewrite groups by kind instead of the original's list
  order by height.
- **partial** — draw order: objects are bucketed (below / above the top layer at height 4) rather
  than sorted by height `h = -4..24` as in `write_objects()`.

## Vehicles

Done: the 23-vehicle table generated from `src/data/tanks.c`; legacy thrust and friction
(`new_velocity`), turn delays, speed-up bonus; mass-ratio collisions and ram damage; structure
crashes; overlap destruction at start; fuel per pixel; armor, shield key pool, start shield
(200 + 15×5 ticks); lives, 300-tick respawn and refill on pads; low-armor smoke and wrecks; planes
(take-off, lift, glide, runway landing, crashes), helicopters (hover, rotor guns); height layers for
structure collisions; pits, ramps; turret modes; atomic tank.

Open:

- **done** — wrecks (`src/tanks/control.c:14-39,138-146`, `src/objects/player.c:71-86`): a vehicle
  becomes a wreck when its dying explosions end; it smokes, blows up when hit or after 3000 ticks while
  the round runs, and is gone; a player-vs-player respawn leaves the old wreck where it lay. Wrecks
  still reach goals.
- **done** — vehicles leaving the map come back in on the other side (`new_position()`).
- **done** — every vehicle not driven by the computer burns fuel for the move it tries; the start
  shield only protects at height ≥ 1.
- **done** — no driving smoke on water.

## Weapons

Done: shared refire timer, ammo and the empty click; weapon cycling with the "SELECTED" message and
the light as 13th slot at night; firing rules in water; main gun (both ranges, velocity inheritance,
alternating barrels), small-rocket main guns; force-field reflection; hit impulses and mortar recoil;
chain gun with the extra-gun bonus; flamethrower; guided rocket with the player-steered lock marker
and "INCOMING MISSILE"; mortar shell and plane bombs; A-bomb with shelters and flash; gunners,
flamers and paratroopers; smoke screen; cloak; speed-up; light; all 14 bonuses.

Open:

- **done** — heavy mines fire eight small rockets (power 10, 100 px) that belong to nobody; light
  mines blow up with their imported power (`src/objects/mine.c:12-28`).
- **done** — mine trigger: a vehicle, train or bomber at height −1..1 whose radius plus 3 (light) or
  50 (heavy) covers the mine (`src/check/collide.c:25-40`).
- **done** — mine visibility: only heavy mines are drawn (RMINE), not on water (`src/objects/mine.c:7-10`).
- **done** — mortar blast (`src/objects/mortar.c`): SND_NUKE on impact, then a ring per step growing
  5 → 60 px, each hitting what it reaches for 5 (vehicles above ground including the shooter,
  soldiers, mines, low bombers, trains, bonuses, A-bombs) and the structures under it with five
  explosions around it; the shell's power plays no part.
- **done** — rocket target (`src/objects/objects.c:189-204`, `rocket.c:32-34`): the nearest object
  with a radius within 15 px of the marker (the shooter's own vehicle, wrecks, soldiers, bombers,
  trains, mines, bonuses, A-bombs, flags, products, the zeppelin, rockets); on arrival everything within
  10 px of it is hit through the shield key, the shooter too; without a target, or out of range, the
  structure at the target point.
- **done** — rocket exhaust trail: EXHAUST1/2 11 px behind every 10–29 ticks.
- **done** — conveyors (`T_LEFT/RIGHT/UP/DOWN`, 60 px/s) and turntables (`ROTATE_L/R`, a turn a second
  around the tile, heading unchanged) carry vehicles and soldiers below height 2; a vehicle that would
  end up in another stays (`src/world/transp.c`). Mines, bonuses and other small objects are not
  carried yet.
- **done** — debris (`src/world/parts.c`): burning pieces of fuel dumps fly in the simulation and
  burn vehicles (7) and structures (10), so dumps go up in chains; pixels from stopped shots (a
  chain-gun round one time in five), mortar specks and smoking pieces of wall (PARTA..F) from
  buildings that explode are thrown by the client from a debris event.
- **done** — `underground_effects()` after the move: five splashes and SND_SPLASH into and out of
  water, five lava bubbles into and out of lava, lava burning 2 every 10 ticks with a bubble, and while
  moving or flying a splash on water, an oil splash on oil, dust on bare desert ground.
- **done** — plain `BRIDGE` tiles: no picture has that type, so `hit_wall`'s case for it is never
  reached.
- **done** — goal `who = 3`: the importer links the goal to its vehicle (WEAPON's five goals).
- **done** — supermarket mayhem (`src/objects/product.c`, `src/world/status.c:162-232`,
  `src/objects/goal.c:22`, `src/tanks/tank.c:293`): cash 40, price per product, 20 products needed,
  180 s limit, −100 when cash or time runs out, the way out (goal `who = 0`) closed while products
  remain, pickup by human vehicles on the ground, the products/time/cash display, product strip and
  message line, no radar and no start shield (2026-10-20).
- **done** — warps move vehicles (wrecks too, at any height), soldiers, bombers and rockets within
  15 px, without a pause, with three sparks and SND_TELEPORT at each end (`src/objects/warp.c`).
- **done** — placed soldier and bonus objects are skipped with a note: the original refuses such maps.
- **done** — computer vehicles refire after their imported `computer.fire_delay` (`src/tanks/fire.c:171`).
- **done** — the radar as `write_radar()` draws it: no cloaked vehicles or wrecks, bombers and trains,
  colours per viewer, turrets by `shootat` (`src/game/radar.c`).

## Modes

Done: one player vs computer by mission code with the known-codes list and invalid-code messages;
mission chains via the next mission code; F1–F10 tank switching and hand-over on death; two players
vs computer; player vs player with start pads and lives (last life when the pad building falls), flag
hunt, races with checkpoints and laps, best-of-N rounds with ROUND and score messages, YOU WIN, the
losers tank option, and the tank selection arena (`SELECT.MAP` running live).

Open:

- **done** — supermarket mayhem (see map objects).
- **done** — F1–F10 in two players vs computer select for player two (`id_select = 1`), Alt+F-key
  for player one (`src/game/controls.c:38,163-168`).
- **done** — known codes ([ADR 0020](adrs/0020-user-data-as-json.md)): Classic lists every known
  code like the original, Extended only the mode's; the last code per mode and the last
  player-vs-player map are kept in `settings.json` like `TANK.CFG`. Public codes are added on the fly,
  not saved (intentional).
- **unverified** — the static-noise view when a player has no tank (`src/game/mainloop.c`).

## Shell and meta-game

Done: intro (`LOGO.FLI`, `TITLE.FLI`, credits); main menu and mode menu; mission-code prompt and list;
map prompt for player vs player; briefing, success and failure texts; game options (laps, rounds,
losers tank, gamma, music and sound volume) with persistence; key help and arena info pages; the
3-second run-on and fades after a mission; victory count and the THUNDER/VICTORY hooks.

Open (in scope by decision of 2026-10-19):

- **done** — in-game keys (`src/game/controls.c`): P pauses until the next key, F11 steps the gamma
  with a "GAMMA CORRECTION / LEVEL n" message, F12 shows or hides the radar, = and - make the window
  bigger and smaller in 5% steps, PrtScr saves `TANK<n>.png` (screenshots folder); window size, radar
  and screenshot number are kept in `settings.json`. Taps shorter than a step count once.
- **missing** — controls setup: key remapping, RELATIVE/DIRECT control modes, joysticks/gamepads,
  saved in `settings.json`. Bindings are hard-coded in `KeyBinding` (`game-client/.../Main.kt`).
- **missing** — command-line switches and cheats (`src/game/main.c:52-97`, `cheat.txt`): `-GOD`,
  `-NOFAIL`, `-CODES`, `-UNLOCK`, `-LOADSOFWEAPONS`, `-NOLOCKLIMIT`, `-AUTOLOCK`, `-MISSING`,
  `-UNUSED`, `-KEYTEST`, `-DEBUG`, `-INFO` (`-SETUP`/`-SETSOUND` belong to the out-of-scope setup).
- **missing** — "EXIT TO DOS? (Y/N)" style confirmation on Exit.
- **done** — the main menu's "Map editor" entry opens the in-game editor (2026-10-19).

## Status display, radar and presentation

Done: original screen layout and window placement; panel background; meters, weapon icons and ammo,
goal counters, lives and flag icons; pilot face and messages; banners (MISSION, COMPLETED, FAILED,
YOU WIN); opponent arrow; round radar with the original colours; lock marker; night lighting with
headlights, lamps and B52 lights; the night status display; shadows; effects (muzzle flash, sparks,
flames, explosions, smoke, burning wrecks, blood, smoke screen, ruins).

Open:

- **done** — effects `DUST`, `LAVA_EFFECT`, `SPLASH` on terrain, `TELEPORT_SPARK`, rocket
  `EXHAUST`; dust and oil splashes lie under the vehicles.
- **done** — radar hidden in supermarket mayhem. **missing** — radar toggle (F12, see shell).

## Audio

Done: the 29 original samples with distance, pan and pitch; per-player panning in split screen;
music with the night tune and in-game volume ([ADR 0018](adrs/0018-audio-and-music.md)).

Open:

- **done** — `SND_SPLASH` on entering or leaving water; ricochets (`ricoc1`–`6`) for one chain-gun round
  in ten on walls and one in twenty on vehicles; the teleport sound.
- **done** — mortar impact plays SND_NUKE.
- **done** — sound distance from the player's vehicle (612 without one), volume above 32 only, pan by
  the two players' distances, pitch 900..1099 (`src/hardware/sound.c:62-109`).

## Map editor

Target: an editor screen inside the game, started from the main menu as in the original, that does
everything the original editor does plus validation, undo and play-test
([ADR 0021](adrs/0021-editor-in-the-game.md)). There is no editor at the moment: the old
`:game-editor` (Kubriko's `SceneEditor` on four object types) was deleted on 2026-10-19.

Missing:

- Every object type editable with its properties (pads with loadouts, computer tanks with activation,
  attack weights and waypoints, turrets, mines, bonuses, men, trains and wagons, flags, locks, goals,
  warps, destroyers, enforcers, products, B52s).
- Painting the base, solid and top layers with the original's structure library and links.
- Editing the goal, bonus and men layers.
- Editing the header ("SPECIAL" menu): map type and contents, mission codes, public flag, world,
  day/night, random bonuses; and the mission texts.
- New map from a world template (`DATA/MAP_0..3.DAT`); whole-map overview.
- Validation, undo, play-testing, and launch from the game's menu.
- Reading and writing the native map format, with `.MAP` import and export
  ([ADR 0022](adrs/0022-native-map-format.md)).

## Content and import

Done: all 69 maps import without notes; every header field, the 7 layers, the texts and objects of
types 0–19 except the runtime-only ones are read.

Open:

- **done** — `computer.fire_delay` is imported (tank record offset 120).
- **done** — placed men and bonuses are skipped with a note (see map objects).
- **partial** — header fields `map_version`, `shareware` and `lock` are parsed but unused (only matters
  for the editor writing maps).
- The per-type field offsets inside object records are only verified for the fields the game uses.

## Tooling and delivery

Done: build with the Kotlin toolchain; Linux (.tar.gz, AppImage) and Windows (.zip, MSI) packages;
291 automated tests; recorded-playthrough replays; every map booted in every mode; reference
renders; idle scan of every map; tick benchmark; CI on GitHub Actions.

Open: more recordings (BEGIN4, more missions, player vs player) and the other items in the
[testing strategy](testing-strategy.md#what-is-not-there-yet).

## Beyond parity

Networked play, up to four players in two teams, gamepads, the Extended menu with replays, Android and
later WASM belong to the Extended ruleset; see the [roadmap](roadmap.md) and ADRs 0023–0027.
