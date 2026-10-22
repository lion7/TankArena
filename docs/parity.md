# Feature Parity

What the rewrite does and does not yet do compared with the original game, checked against the code
and the C source on 2026-10-20 (Classic parity, roadmap phase 3, finished). The original is described in [`original/docs/`](../original/docs/); where that
text and `original/src/` disagree, `original/src/` wins ([ADR 0010](adrs/0010-port-the-original-rules.md)).

**Parity** means: the 69 original maps play as in the original, in every mode, with the original's
rules, presentation, sound, controls and editor capabilities, except for the deviations and
exclusions listed below. Parity is the goal of the **Classic** ruleset; the Extended ruleset builds on
it with named, documented changes ([ADR 0023](adrs/0023-classic-and-extended-rulesets.md)). The order
of the open work is in the [roadmap](roadmap.md) (phase 3 for rules, phase 4 for the editor).

Legend: **done** (matches the original), **missing** (not built), **partial** (built, something
missing), **differs** (built, behaves differently from the original and must be fixed), **unverified**
(believed right, not yet compared with the original running).

## Summary

| Area | State |
|---|---|
| Engine, tick, game loop | Done (legacy step, `game_control()` order); one detail unverified |
| Vehicles and movement | Done |
| Weapons | Done |
| Terrain and structures | Done |
| Map objects | Done |
| Computer AI | Done (port of `computer.c`) |
| Modes | Done, supermarket mayhem included |
| Shell, menus, options | Done; joysticks are gamepads (phase 6) |
| Status display, radar, presentation | Done |
| Audio and music | Done |
| Map editor | Layers, structures, objects, undo, save, play-test; header, texts and the rest in phase 4 |
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
- **Controls setup inside the game**, as "Controls setup" in the game options menu (decided 2026-10-20):
  the original had it only in a separate setup program (`TANK -SETUP`), which nobody starts from a
  shell today. `-SETUP` still opens it.
- **No `-CODES`** (decided 2026-10-20): it only printed the map list to the console.
- **"Switch to Extended" in the main menu** (decided 2026-10-20), so a player can leave Classic without
  a command-line switch ([ADR 0023](adrs/0023-classic-and-extended-rulesets.md)).

## Out of scope

- The shareware build's zeppelin that the game spawns by itself (`original/src/game/mapfiles.c:324`).
- The DOS setup programs (`original/src/setup/`: sound card, video mode); the JDK picks the audio device.
- `VICTORY.FLI` and `THUNDER.FLI`: not in the original release either; the code plays them if present.
- Map locking/encryption of the original editor (`lock`, `protect`).

## Faithful quirks (not bugs)

Kept because the original does the same ([ADR 0010](adrs/0010-port-the-original-rules.md)):

- In player vs player every start pad of a player's vehicle kind spawns a tank and all of them obey
  that player's keys; F1–F10 is disabled in that mode (`original/src/game/controls.c:220`). No shipped map has
  two such pads for a player. Extended gives each player one vehicle.
- Computer tanks stacked on each other at start destroy each other (of the shipped maps, SUBURBS).
  In Extended they survive.
- Water never kills tanks; it slows them and hides the hull.
- BASE1's wagons have no engine and never move.
- Turrets cannot be shot; they fall with the building under them, and turrets placed off buildings
  vanish at start.

## Engine and game loop

Done: 100 ticks per second; legacy timers; deterministic seeded randomness; colour cycling; nuke
flash; fades; grey view for the losing player.

Open:

- **done** — the game step: one step every 3 timer ticks like `game_control()`
  (`original/src/game/mainloop.c:17-21`), timers running out on steps like `tdone()`, per-step rules (rocket
  acceleration, steering noise, wagon spacing, explosions) once per step, the start and bonus shield as
  15 blinks of `tinit(5)` after the hold (rules v12, 2026-10-19). See
  [ADR 0002](adrs/0002-fixed-step-100hz.md#the-originals-game-step-is-not-its-timer-tick).
- **unverified** — a vehicle pushing against another re-collides once per step, as in the original;
  not compared with footage.
- **done** — step order like `game_control()`: shots move and hit first (their damage counts in the same
  step), then vehicles on top of each other, then every object's control; shots fired in a step move
  from the next one. Within the objects the rewrite groups by kind instead of the original's list
  order by height.
- **done** — draw order: below and above the top layer (height 4) as in `write_objects()`, and within
  each, vehicles by height, lowest first; effects keep the order they were made in.

## Vehicles

Done: the 23-vehicle table generated from `original/src/data/tanks.c`; legacy thrust and friction
(`new_velocity`), turn delays, speed-up bonus; mass-ratio collisions and ram damage; structure
crashes; overlap destruction at start; fuel per pixel; armor, shield key pool, start shield
(200 + 15×5 ticks); lives, 300-tick respawn and refill on pads; low-armor smoke and wrecks; planes
(take-off, lift, glide, runway landing, crashes), helicopters (hover, rotor guns); height layers for
structure collisions; pits, ramps; turret modes; atomic tank.

Open:

- **done** — wrecks (`original/src/tanks/control.c:14-39,138-146`, `original/src/objects/player.c:71-86`): a vehicle
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
  mines blow up with their imported power (`original/src/objects/mine.c:12-28`).
- **done** — mine trigger: a vehicle, train or bomber at height −1..1 whose radius plus 3 (light) or
  50 (heavy) covers the mine (`original/src/check/collide.c:25-40`).
- **done** — mine visibility: only heavy mines are drawn (RMINE), not on water (`original/src/objects/mine.c:7-10`).
- **done** — mortar blast (`original/src/objects/mortar.c`): SND_NUKE on impact, then a ring per step growing
  5 → 60 px, each hitting what it reaches for 5 (vehicles above ground including the shooter,
  soldiers, mines, low bombers, trains, bonuses, A-bombs) and the structures under it with five
  explosions around it; the shell's power plays no part.
- **done** — rocket target (`original/src/objects/objects.c:189-204`, `rocket.c:32-34`): the nearest object
  with a radius within 15 px of the marker (the shooter's own vehicle, wrecks, soldiers, bombers,
  trains, mines, bonuses, A-bombs, flags, products, the zeppelin, rockets); on arrival everything within
  10 px of it is hit through the shield key, the shooter too; without a target, or out of range, the
  structure at the target point.
- **done** — rocket exhaust trail: EXHAUST1/2 11 px behind every 10–29 ticks.
- **done** — a rocket in flight can be destroyed (`determine_hit()`, `rocket.c:40-46`): by a flame
  within 15 px, a shot within 3 px, a blast (mine, mortar ring, A-bomb, destroyer, another rocket), a
  bomber's bomb or a burning piece; it goes in a big explosion without damage. Shots, flames, bombs and
  burning pieces set off mines the same way.

## Terrain and structures

Done: material and speed per picture from `original/src/data/pictures.c`; water (slow, weapons, hidden hull,
men drown); lava; runways and helipads; rails; bushes; structure strengths for normal, crash and
flame hits with linked pieces, ruins, the signed goal layer, bonuses and paratroopers from destroyed
buildings; bridges turning into water; A-bomb boxes; force fields; shelters; night maps.

Open:

- **done** — conveyors (`T_LEFT/RIGHT/UP/DOWN`, 60 px/s) and turntables (`ROTATE_L/R`, a turn a second
  around the tile, heading unchanged) carry vehicles and soldiers below height 2; a vehicle that would
  end up in another stays (`original/src/world/transp.c`). Mines, bonuses and other small objects are not
  carried yet.
- **done** — debris (`original/src/world/parts.c`): burning pieces of fuel dumps fly in the simulation and
  burn vehicles (7) and structures (10), so dumps go up in chains; pixels from stopped shots (a
  chain-gun round one time in five), mortar specks and smoking pieces of wall (PARTA..F) from
  buildings that explode are thrown by the client from a debris event.
- **done** — `underground_effects()` after the move: five splashes and SND_SPLASH into and out of
  water, five lava bubbles into and out of lava, lava burning 2 every 10 ticks with a bubble, and while
  moving or flying a splash on water, an oil splash on oil, dust on bare desert ground.
- **done** — plain `BRIDGE` tiles: no picture has that type, so `hit_wall`'s case for it is never
  reached.

## Map objects

Done: goal counter (good/bad, 100 % decides, ignored in player vs player, −100 for the last human
tank) with contributions from tanks, turrets, locks, destroyers, trains, B52s and the goal layer; goal
objects for `who` 0–2; flags and flag hunt; locks (all activations and targets); destroyers;
enforcers; trains with rails, curves, bridges, wagons, the turret wagon and the A-bomb wagon; B52s
(take-off, salvos, craters, crashes, wrecks); zeppelin movement; soldiers and paratroopers; bonuses;
authored mines; start pads; turrets (nine kinds, side selection, forbidden directions, barrels,
falling with their building); computer tanks.

Open:

- **done** — goal `who = 3`: the importer links the goal to its vehicle (WEAPON's five goals).
- **done** — supermarket mayhem (`original/src/objects/product.c`, `original/src/world/status.c:162-232`,
  `original/src/objects/goal.c:22`, `original/src/tanks/tank.c:293`): cash 40, price per product, 20 products needed,
  180 s limit, −100 when cash or time runs out, the way out (goal `who = 0`) closed while products
  remain, pickup by human vehicles on the ground, the products/time/cash display, product strip and
  message line, no radar and no start shield (2026-10-20).
- **done** — warps move vehicles (wrecks too, at any height), soldiers, bombers and rockets within
  15 px, without a pause, with three sparks and SND_TELEPORT at each end (`original/src/objects/warp.c`).
- **done** — placed soldier and bonus objects are skipped with a note: the original refuses such maps.

## AI

Done: `ComputerTankAi` ports `control_computer_tank()`: activation, target choice weighted by
`attack_tanks` / `attack_goals`, waypoints, attack waves, dangers, crash panic, fire and speed rules
([ADR 0012](adrs/0012-port-the-computer-ai.md)).

Open:

- **done** — computer vehicles refire after their imported `computer.fire_delay` (`original/src/tanks/fire.c:171`).
- **done** — the radar as `write_radar()` draws it: no cloaked vehicles or wrecks, bombers and trains,
  colours per viewer, turrets by `shootat` (`original/src/game/radar.c`).

## Modes

Done: one player vs computer by mission code with the known-codes list and invalid-code messages;
mission chains via the next mission code; F1–F10 tank switching and hand-over on death; two players
vs computer; player vs player with start pads and lives (last life when the pad building falls), flag
hunt, races with checkpoints and laps, best-of-N rounds with ROUND and score messages, YOU WIN, the
losers tank option, and the tank selection arena (`SELECT.MAP` running live).

Open:

- **done** — supermarket mayhem (see map objects).
- **done** — F1–F10 in two players vs computer select for player two (`id_select = 1`), Alt+F-key
  for player one (`original/src/game/controls.c:38,163-168`).
- **done** — known codes ([ADR 0020](adrs/0020-user-data-as-json.md)): Classic lists every known
  code like the original, Extended only the mode's; the last code per mode and the last
  player-vs-player map are kept in `settings.json` like `TANK.CFG`. Public codes are added on the fly,
  not saved (intentional).
- **done** — the static-noise view when a player has no vehicle (an F-key id without one, or the
  wreck they looked at blew up): random palette colours 0..15 in 33-px tiles every frame, with the
  goal counters and the picked id (`original/src/game/mainloop.c:81-91`); F-keys may pick any id, and in two
  players vs computer not the other player's.

## Shell and meta-game

Done: intro (`LOGO.FLI`, `TITLE.FLI`, credits); main menu and mode menu; mission-code prompt and list;
map prompt for player vs player; briefing, success and failure texts; game options (laps, rounds,
losers tank, gamma, music and sound volume) with persistence; key help and arena info pages; the
3-second run-on and fades after a mission; victory count and the THUNDER/VICTORY hooks.

Open (in scope by decision of 2026-10-19):

- **done** — in-game keys (`original/src/game/controls.c`): P pauses until the next key, F11 steps the gamma
  with a "GAMMA CORRECTION / LEVEL n" message, F12 shows or hides the radar, = and - make the window
  bigger and smaller in 5% steps, PrtScr saves `TANK<n>.png` (screenshots folder); window size, radar
  and screenshot number are kept in `settings.json`. Taps shorter than a step count once.
- **done** — controls setup (legacy setup.c; Classic's game options, Extended's Settings → Controls and
  `-SETUP`): each
  player's mode (RELATIVE, or DIRECT: the vehicle turns the short way to where the keys point and
  drives forward, the turret with an aim key held) and ten keys; a key given to one slot leaves every
  other; DEL clears; "SAVE SETTINGS? (Y/N)". Kept in `settings.json`. A single player uses player
  two's keys (the grey cursor block, as KEYS.HLP shows; `controls.c` itself defaults to the keypad).
  **done** (2026-10-20) — joysticks are gamepads: per player KEYBOARD or JOYSTICK, the four
  joystick buttons (FIRE, AIM, WEAPON, SHIELD; several pressed together count as one) and the
  sensitivity, on a second page of the controls setup (Tab), as the original's setup program had them.
- **done** — command-line cheats `-GOD`, `-NOFAIL`, `-LOADSOFWEAPONS`, `-NOLOCKLIMIT` (also spelt
  `--god` ...; kept in recordings) (`original/src/game/main.c:52-97`, `cheat.txt`). `-SETUP` opens the controls
  setup. Not ported: `-CODES` (see intentional deviations) and the developer tools `-KEYTEST`,
  `-DEBUG`, `-INFO`, `-MISSING`, `-UNUSED`, `-AUTOLOCK`, `-UNLOCK`.
- **done** — "EXIT TO DOS? (Y/N)" after leaving the Classic main menu (Exit or ESC); only Y or N answer.
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
- **done** — radar hidden in supermarket mayhem; radar toggle (F12).

## Audio

Done: the 29 original samples with distance, pan and pitch; per-player panning in split screen;
music with the night tune and in-game volume ([ADR 0018](adrs/0018-audio-and-music.md)).

Open:

- **done** — `SND_SPLASH` on entering or leaving water; ricochets (`ricoc1`–`6`) for one chain-gun round
  in ten on walls and one in twenty on vehicles; the teleport sound.
- **done** — mortar impact plays SND_NUKE.
- **done** — sound distance from the player's vehicle (612 without one), volume above 32 only, pan by
  the two players' distances, pitch 900..1099 (`original/src/hardware/sound.c:62-109`).

## Map editor

Target: an editor screen inside the game, started from the main menu as in the original, that does
everything the original editor does plus validation, undo and play-test
([ADR 0021](adrs/0021-editor-in-the-game.md)). Built (2026-10-19/20): open a map from both menus or
make a new one; paint the three picture layers; the structure library; the goal, bonus and men layers;
every object kind with its settings and points; the map's settings and texts; one undo history; save;
play-test. This is roadmap
phase 4.

Missing:

- Whole-map overview. (The original's structure-library editor under F12, "not supposed to be used
  by other people", is left out on purpose: the libraries are shipped as they are.)
- Validation.
- Exported `.MAP` files tried in the original (DOSBox); export itself is built
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
324 automated tests; recorded-playthrough replays; every map booted in every mode; reference
renders; idle scan of every map; tick benchmark; CI on GitHub Actions.

Open: more recordings (BEGIN4, more missions, player vs player) and the other items in the
[testing strategy](testing-strategy.md#what-is-not-there-yet).

## Beyond parity

Networked play, up to four players in two teams, gamepads, the Extended menu with replays, Android and
later WASM belong to the Extended ruleset; see the [roadmap](roadmap.md) and ADRs 0023–0027.
