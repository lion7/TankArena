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

- **differs** — per-step rules run about 3× too fast. The original does one game step every 3 timer
  ticks (`src/game/mainloop.c:17-21`), the rewrite one per 10 ms tick. Timers and `dt`-based movement
  are unaffected, but anything the original changes by a fixed amount per step is not: confirmed for
  the guided rocket's acceleration (+1 px/s per step, `src/objects/rocket.c:29`; reaches 150 px/s in
  1.5 s instead of ~4.5 s); to check: rotors with `auto_rotation` 0, effect animations, and every other
  `++`/`--` in a control function without a `tdone()` guard. See
  [ADR 0002](adrs/0002-fixed-step-100hz.md#the-originals-game-step-is-not-its-timer-tick).
- **differs** — tick order: the server's pipeline is grouped by concern and does not follow
  `game_control()`'s `transportation → effects → bullets → parts → overlap → objects` order. Only
  visible in edge cases (who moves first); revisit if a recorded playthrough disagrees with footage.
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

- **differs** — respawn reuses the dead tank, so its wreck vanishes; the original creates a new tank
  and leaves the wreck (`src/objects/player.c:76`).
- **differs** — the map edge clamps (plus boundary walls); the original wraps `x`/`y` in
  `new_position()`.
- **differs** — fuel use is keyed on "has a player" instead of `!contr.computer`; the start shield
  ignores the original's `o->h >= 1` condition.
- **differs** — low-armor smoke is also emitted on water (`src/tanks/control.c:426` skips it).

## Weapons

Done: shared refire timer, ammo and the empty click; weapon cycling with the "SELECTED" message and
the light as 13th slot at night; firing rules in water; main gun (both ranges, velocity inheritance,
alternating barrels), small-rocket main guns; force-field reflection; hit impulses and mortar recoil;
chain gun with the extra-gun bonus; flamethrower; guided rocket with the player-steered lock marker
and "INCOMING MISSILE"; mortar shell and plane bombs; A-bomb with shelters and flash; gunners,
flamers and paratroopers; smoke screen; cloak; speed-up; light; all 14 bonuses.

Open:

- **differs** — heavy mines are imported as light mines with damage 20; the original fires 8 small
  rockets (power 10, range 100) (`src/objects/mine.c`). Map mine power is ignored (fixed 7/20).
- **differs** — mine trigger: the rewrite uses 30 + 14 px with no height check; the original triggers
  on contact (`o->r + 3`) when the height difference is below 2 (`src/check/collide.c:36`).
- **differs** — mine visibility: all mines are drawn; the original draws only heavy mines, and not on
  water.
- **differs** — mortar blast: applied in one tick with a linear falloff; the original grows 5 → 60 px
  in steps of 5 every 3 ticks at power 5 per step (`src/objects/mortar.c`).
- **differs** — rocket target: only live tanks other than the owner; the original takes any object
  with `r > 0` within 15 px of the marker (`nearest_target`).
- **missing** — rocket exhaust trail.

## Terrain and structures

Done: material and speed per picture from `src/data/pictures.c`; water (slow, weapons, hidden hull,
men drown); lava; runways and helipads; rails; bushes; structure strengths for normal, crash and
flame hits with linked pieces, ruins, the signed goal layer, bonuses and paratroopers from destroyed
buildings; bridges turning into water; A-bomb boxes; force fields; shelters; night maps.

Open:

- **missing** — conveyor belts (`T_LEFT/RIGHT/UP/DOWN`) and rotating platforms (`ROTATE_L/R`)
  moving vehicles (`src/world/transp.c`); only their colour cycling is done.
- **missing** — debris (`src/world/parts.c`: tank parts, wall parts, pixels, flames). Without it,
  fuel dumps only show flames: their `PT_FLAME` parts should hit tanks for 7 and walls for 10 and can
  set off neighbouring dumps.
- **missing** — terrain effects from `underground_effects()` (`src/tanks/control.c:342-382`): water
  splash on entering and driving, lava particles, oil splash, dust on background 0.
- **partial** — plain `BRIDGE` tiles (base `-1`) are not handled separately; impact unverified.

## Map objects

Done: goal counter (good/bad, 100 % decides, ignored in player vs player, −100 for the last human
tank) with contributions from tanks, turrets, locks, destroyers, trains, B52s and the goal layer; goal
objects for `who` 0–2; flags and flag hunt; locks (all activations and targets); destroyers;
enforcers; trains with rails, curves, bridges, wagons, the turret wagon and the A-bomb wagon; B52s
(take-off, salvos, craters, crashes, wrecks); zeppelin movement; soldiers and paratroopers; bonuses;
authored mines; start pads; turrets (nine kinds, side selection, forbidden directions, barrels,
falling with their building); computer tanks.

Open:

- **missing** — goal `who = 3` (a linked object reaching the goal); 5 shipped goals use it and never
  complete.
- **missing** — supermarket mayhem rules (`src/objects/product.c`, `src/world/status.c:162-185`,
  `src/include/define.h:32-34`): cash 40, price per product, 20 products needed, 180 s limit, −100 when
  cash or time runs out, goal `who = 0` blocked while products remain (`src/objects/goal.c:22`), the
  products/time/cash display and product strip, radar hidden. Pickup and the "PICKED UP" message work.
- **differs** — warps teleport only live tanks and add a 30-tick cooldown; the original also warps men,
  B52s, rockets and wrecks, has no cooldown, and shows `TELEPORT_SPARK` with `SND_TELEPORT`
  (`src/objects/warp.c:28-31`).
- **missing** — placed men and bonuses as map objects (parsed, not built; no shipped map has them, so
  this matters only for the editor).

## AI

Done: `ComputerTankAi` ports `control_computer_tank()`: activation, target choice weighted by
`attack_tanks` / `attack_goals`, waypoints, attack waves, dangers, crash panic, fire and speed rules
([ADR 0012](adrs/0012-port-the-computer-ai.md)).

Open:

- **differs** — `computer.fire_delay` is not imported, so computer tanks refire at `weap.speed`
  (`src/tanks/fire.c:171`).
- **differs** — cloaked tanks still show on the radar (`src/game/radar.c:37` hides them). Turrets
  already ignore them; computer tanks targeting them is faithful.

## Modes

Done: one player vs computer by mission code with the known-codes list and invalid-code messages;
mission chains via the next mission code; F1–F10 tank switching and hand-over on death; two players
vs computer; player vs player with start pads and lives (last life when the pad building falls), flag
hunt, races with checkpoints and laps, best-of-N rounds with ROUND and score messages, YOU WIN, the
losers tank option, and the tank selection arena (`SELECT.MAP` running live).

Open:

- **partial** — supermarket mayhem (see map objects).
- **differs** — F1–F10 in two players vs computer: the rewrite selects for player one without Alt and
  player two with Alt; the original selects for `id_select = 1` without Alt and 0 with Alt
  (`src/game/controls.c:38,163-168`).
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

- **missing** — in-game keys (`src/game/controls.c`): P pause (210-217), F11 gamma (177-185), F12 radar
  toggle (207), PrtScr screenshot (174), keypad +/- window size (187-205).
- **missing** — controls setup: key remapping, RELATIVE/DIRECT control modes, joysticks/gamepads,
  saved in `settings.json`. Bindings are hard-coded in `KeyBinding` (`game-client/.../Main.kt`).
- **missing** — command-line switches and cheats (`src/game/main.c:52-97`, `cheat.txt`): `-GOD`,
  `-NOFAIL`, `-CODES`, `-UNLOCK`, `-LOADSOFWEAPONS`, `-NOLOCKLIMIT`, `-AUTOLOCK`, `-MISSING`,
  `-UNUSED`, `-KEYTEST`, `-DEBUG`, `-INFO` (`-SETUP`/`-SETSOUND` belong to the out-of-scope setup).
- **missing** — "EXIT TO DOS? (Y/N)" style confirmation on Exit.
- **missing** — the main menu's "Map editor" entry is disabled; it should open the in-game editor.

## Status display, radar and presentation

Done: original screen layout and window placement; panel background; meters, weapon icons and ammo,
goal counters, lives and flag icons; pilot face and messages; banners (MISSION, COMPLETED, FAILED,
YOU WIN); opponent arrow; round radar with the original colours; lock marker; night lighting with
headlights, lamps and B52 lights; the night status display; shadows; effects (muzzle flash, sparks,
flames, explosions, smoke, burning wrecks, blood, smoke screen, ruins).

Open:

- **missing** — effects `DUST`, `LAVA_EFFECT`, `SPLASH` on terrain, `TELEPORT_SPARK`, rocket
  `EXHAUST` (see terrain, objects, weapons).
- **missing** — radar hidden in supermarket mayhem; radar toggle (F12).

## Audio

Done: the 29 original samples with distance, pan and pitch; per-player panning in split screen;
music with the night tune and in-game volume ([ADR 0018](adrs/0018-audio-and-music.md)).

Open:

- **missing** — `SND_SPLASH` on entering or leaving water (`src/tanks/control.c:345`); ricochet sounds
  (`ricoc1`–`6`) and the teleport sound are bundled but never played.
- **differs** — mortar impact plays the mortar sound; the original plays `SND_NUKE`.
- **differs** — distance is measured from the window's camera instead of the player's tank.

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

- **differs** — `computer.fire_delay` is not read (see AI).
- **missing** — placed men and bonuses are parsed but not written to scenes.
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
