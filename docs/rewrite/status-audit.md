# Tank Arena Kotlin Rewrite — Status Audit

**Audit date:** 2026-09-25 · **Branch:** `rewrite` @ `a076ca9` · **Last code change:** 2026-05-11

This audit was written from the code, not from the existing progress docs. [`full-rewrite-status.md`](../full-rewrite-status.md) and [`parity-tasks.md`](parity-tasks.md) say T01–T06 and T08–T32 are done, and this audit checked each of those claims:

- against the Kotlin sources in the six modules;
- against the original C source in `src/`;
- against the path the desktop client actually runs in play.

Where the progress docs and the code disagree, the code wins, and §6 lists every disagreement.

> **Update, same day:** work on the §8 milestone has since landed (commits `2fce7af` and `76c20a6`). §9 lists what changed; §1–§7 still describe the code at `a076ca9`.

> **Build note.** The test suite could not be run while writing this audit, because the sandbox could not download the Amper distribution (HTTP 403 from the network proxy). The test counts below are static counts of `@Test` methods. Run `./amper test` locally to confirm the suite is green.

---

## 1. Summary

The rewrite has a solid **architectural skeleton**:

- an authoritative headless Kubriko server;
- a clean protocol module;
- snapshot replication keyed by `actorId`;
- all 121 shipped maps imported;
- 194 unit tests.

Gameplay parity is **much further behind than the status docs suggest**. Many systems marked "done" exist only as server classes exercised by unit tests. They are not reachable, or not active, in the game the player actually launches.

| Area | Rough completeness vs. original | Comment |
|---|---|---|
| Architecture / modules / protocol | ~90% | In good shape. Some duplication (three input types). |
| Content import (maps, sprites) | ~70% | Maps and pictures are imported. Sound, music, FLI, night lighting and UI bitmaps are not. |
| Server simulation (unit-test level) | ~35% | 5 of 12 weapons. One vehicle model with generic stats. Objects are mostly stubs. |
| Server simulation (**as actually played**) | ~15% | No terrain grid, no solid layer and no waypoints on the production path. Tanks are only injected for mission `BEGIN1`. |
| Client (rendering / HUD / input / audio) | ~20% | Draws 4 of 14 actor types. No effects. Audio is a stub. No split-screen. No second player. |
| Menus / shell flow | ~35% | New game → mode → mission → debrief works. Options, editor launch, info, keys, passwords and tank select are missing. |
| Editor | ~5% | Stock Kubriko `SceneEditor` with 4 actor types. No tiles, mission text, validation or playtest. |
| Replay / networking / packaging / CI | 0–5% | Only data classes exist. No CI. No packaging. |

**Overall estimate: about 20–25% of the way to the feature-complete bar** defined in `full-rewrite-status.md` §8.

---

## 2. What was built and how

### 2.1 Modules

| Module | Main LoC | Tests (`@Test`) | Role |
|---|---|---|---|
| `:game-protocol` | ~700 | 0 | Math, IDs, `FixedStepClock` (100 Hz), `InputFrame`/`PlayerIntentFrame`, `WorldSnapshot`, the `ActorState` hierarchy, `GameEvent`, `PlayerView`/`HudState`, `ReplayProtocol` (data classes only) |
| `:game-content` | ~6,100 (4,750 of it generated) | 10 | `LegacyMapParser`, `MapSceneSidecar` (schema v1), `PictureCatalog`, `TerrainGridBuilder`, the generated picture catalog, sprite-name registry, and 121 scene + sidecar pairs |
| `:game-server` | ~9,800 (incl. tests) | 170 | `ServerMatchPrototype`, 17 `Server*Actor` types, `TerrainSlideManager`, `AreaDamageResolver`, AI (`TilePathfinder`, `LineOfSight`, `WaypointFollower`, `AiModeDispatcher`), and the legacy importer (`legacy/*`) |
| `:game-client` | ~2,850 + 2,960 PNGs | 12 | Compose Desktop shell, Kubriko viewport, `ClientScene.sync`, HUD and radar overlays, an audio-math stub |
| `:game-editor` | 73 | 0 | Wraps Kubriko `SceneEditor` |
| `:tools-mapconv` | 130 | 0 | CLI: `map`, `scene`, `scene-all`, `extract-pictures`, `extract-pictures-png` |

For comparison, the original is about 21.7k lines of C and assembly in `src/`.

### 2.2 Stack

- Kotlin/JVM with Compose Multiplatform (desktop).
- Kubriko `0.1.2` from Maven Central.
- Amper `0.10.0` build.
- kotlinx.serialization `1.11.0`.

### 2.3 Architecture decisions (ADRs 0001–0009)

These are sound, and the code follows them:

- **Authoritative server even for single player.** `LocalMatchClient` runs `ServerMatchPrototype` in-process, and the client only renders `ServerFrame`s ([ADR 0007](adrs/0007-authoritative-headless-kubriko-server.md)).
- **One replication contract.** `Serializable<T>.save()` produces the `ActorState` used both for scene JSON and for per-tick snapshots ([ADR 0008](adrs/0008-kubriko-and-scene-json.md)).
- **100 Hz fixed step.** Legacy tick constants port unchanged ([ADR 0002](adrs/0002-fixed-step-50hz.md); the file name still says 50 Hz).
- **Schema versioning.** `WorldSnapshot.PROTOCOL_VERSION = 2` and `SCENE_SCHEMA_VERSION = 1`; payloads from a newer version are rejected.
- **Module collapse from 11 to 6** ([ADR 0006](adrs/0006-module-collapse.md)).

---

## 3. Where the rewrite deliberately differs from the original

| Topic | Original (C / Allegro / DOS) | Rewrite | Assessment |
|---|---|---|---|
| Runtime model | One process. The game loop mutates global structs directly (`src/game/mainloop.c`). | Authoritative headless server plus a render-only client over `ServerFrame`s. | Good choice; prepares for networking and replay. |
| Engine | Allegro 3, 8-bit palette, page flipping, hand-written ASM blitters. | Kubriko (Compose) for the viewport; Compose for menus and HUD. | Good. Palette effects (gamma, night, A-bomb flash, grey loser view) need a new design. |
| Map format | Binary `.MAP`: header, 3 layers, 3 text sections, object blob. | Kubriko scene JSON (actors) plus a `MapSceneSidecar` (tile layers, mission text, metadata). | Good. The legacy parser was meant to be deleted after import (ADR 0003, T07). |
| Walls | Tile layer with per-tile strength and ruin variants. | Each solid tile becomes a `ServerWallActor` (55,327 wall actors across 121 scenes). | ⚠ Heavy. Structure strength and ruins are not modelled. A tile grid plus a sparse destructible-structure actor would be closer to the original and much lighter. |
| Map editor | Built into `tank.exe`: a full structure and object editor with property menus and a SPECIAL menu. | Separate app wrapping Kubriko's generic `SceneEditor` (ADR 0009). | Much less capable today. Tile painting, mission text, map flags and object properties all have to be rebuilt on top. |
| Tick | 100 Hz timer (`define.h sec=100`). | 100 Hz server tick; replay and network at 10 Hz. | Matches. |
| Collision | Pixel-overlap and per-hit-type wall tests; crash damage from mass and velocity. | Kubriko overlap detection plus a custom MTV slide (`TerrainSlideManager`). | Acceptable. Crash damage and mass are missing. |
| Input | 10 keys per player, RELATIVE/DIRECT modes, 2 joysticks. | Hard-coded WASD/QE/Ctrl/Shift for player 0 only. | Placeholder. |
| Configuration | `tank.cfg`, `PASSWD.DAT`. | None. | Not started. |
| Mission loading in client | — | `MissionCatalog` **re-imports `MAPS/*.MAP` at runtime** through `LegacyMapImporter`. It ignores the committed `resources/scenes`. | ⚠ Goes against ADR 0003 and blocks T07. |

---

## 4. Feature parity matrix

Legend:

- ✅ works in the game as played
- 🟡 partial, or works only in unit tests / on the `fromCanonicalMap` test path
- 🟥 stub: data classes or serialization only, no behaviour
- ❌ absent

### 4.1 Shell, modes and meta-game

| Original feature | Status | Notes |
|---|---|---|
| Intro FLIs (LOGO, TITLE) and credits bitmaps | ❌ | FLI not converted. |
| Main menu | 🟡 | "Start new game" works. Game options, Map editor, Arena info and Default keys are shown disabled. |
| New game: PvP / 1P vs CPU / 2P vs CPU | 🟡 | Screens exist, but both two-player modes spawn a second tank that nobody controls. |
| Game options (laps, rounds, losers tank, gamma, volumes) | ❌ | |
| Mission code entry, `PASSWD.DAT`, known-codes list | ❌ | A mission list is shown instead. The debrief uses `nextMissionCode` for "Next mission" (🟡). |
| Separate SINGLE / DUALVC code chains | ❌ | |
| DUAL map list filtered by map type | 🟡 | Mode compatibility is stored in the sidecar; filtering not verified. |
| Tank selection arena (`SELECT.MAP`, 8 slots, Losers tank) | ❌ | |
| Briefing / success / failure text | 🟡 | Mission select shows only the first briefing line. The debrief shows success or failure text. |
| DUAL rounds, "ROUND n", VICTORY.FLI | ❌ | |
| Deathmatch (4 lives, base destroyed → sudden death) | 🟡 | A generic DUAL elimination rule exists; no base or sudden-death rule. |
| Race (laps, per-player checkpoints, respawn at checkpoint) | ❌ | |
| Capture the flag (random flag positions, deliver to base) | 🟡 | Pickup, carry and return on death work on the server. No delivery or capture win, no team check, and flags are not rendered. |
| Supermarket Mayhem ($40 cash, 20 products, 3:00 limit) | 🟥 | Products can be picked up. No cash, no quota, no timer, no product HUD. |
| Pause (P), gamma (F11), screenshot, radar toggle (F12) | ❌ | Esc returns to the menu. |
| **Switch tanks with F1–F10** (single player and DUALVC) | ❌ | Distinctive original feature. |
| Automatic hand-over to another friendly tank on death | ❌ | |
| Pilot face portrait and 2-line messages | ❌ | |
| Mission complete / failed graphics, debrief | 🟡 | Compose debrief with score, kills, captures and time. |
| Music (TUNE.MID; Belved.mid on the Market world) | ❌ | |
| Setup: resolutions, control modes, key bindings, joystick | ❌ | T34, T35, T39. |
| Cheats and command-line switches (`-GOD`, `-NOFAIL`, …) | ❌ | Could map to dev flags. |

### 4.2 Viewports, HUD and presentation

| Original feature | Status | Notes |
|---|---|---|
| Single centred viewport, camera follows the player | ✅ | Clamped to the legacy 640×400 playfield. |
| Split-screen for DUAL / DUALVC | ❌ | The client uses `playerViews.firstOrNull()` only (T33). |
| Adjustable window size (keypad +/-) | ❌ | |
| Static noise on death; grey loser view | ❌ | |
| HUD: armor, fuel, lives | ✅ | |
| HUD: weapon strip, icon, ammo | 🟡 | A text label only, and wrong for weapon index 2 and anything above 5. No ammo shown. |
| HUD: good and evil goal counters, tank ID | 🟡 | Mission % and tank are shown. |
| HUD: opponent edge arrow (dual) | ❌ | |
| HUD: score, kills, time | ✅ | Rewrite addition; not present in the original. |
| Radar (round, 15-tile range, colour-coded, missiles in yellow) | 🟡 | A square box of dots; no missiles. |
| Always-on debug overlay | ⚠ | `ViewportDebugOverlay` in `Main.kt` must be gated behind a flag. |
| Snapshot interpolation | ❌ | Actors snap to each tick; ADR 0002 says the client should interpolate. |
| Night missions (palette, light masks, headlights, light tiles) | ❌ | `TankArenaWorld.NIGHT` exists; LIGHT.DAT and NIGHT.PAL are not used. |
| Effects: dust, smoke, fire, explosions, blood, splash, sparks, … (17 kinds) | ❌ | No client consumer for `GameEvent.Explosion`. |
| Debris parts with smoke trails | ❌ | |
| Damaged, ruined and dead tile variants | ❌ | Always drawn as `INTACT`. |
| Rendering of flag, product, lock, warp, train, zeppelin, B52, mine, rocket, mortar | ❌ | `ClientScene` maps these to `Unit`. Projectiles are a 3 px cross. |
| Tank / turret sprites | ✅ | Body and turret for 8 directions × type. No team colour. |

### 4.3 Vehicles

| Original feature | Status | Notes |
|---|---|---|
| 23 vehicles (tank, car, chopper, plane) with per-vehicle stats from `src/data/tanks.c` | ❌ | `tankType` only selects the sprite. Every tank uses the same constants (`ServerTankActor.kt:22-45`). |
| Mass, acceleration, friction, crash factor, rotation delay | ❌ | |
| Helicopter hover; auto-rotating gun | ❌ | |
| Plane lift; crash unless landing on a runway; plane bombs | ❌ | |
| Turret modes (static / turret / auto) | 🟡 | Every tank has a free turret. |
| Tank-vs-tank collision slide | ✅ | T08. |
| Crash damage | ❌ | |
| Smoke when armor is below 1/3; 30 s wrecks | ❌ | |
| Refill of ammo, fuel and armor at base | ❌ | |
| Shield key with a recharging pool | 🟡 | Server routes damage through the shield and the shield decays. `grantShield` has no production caller, and the `shield` input is never read. |
| Fuel drain; no movement at zero fuel | ✅ | Server only. |
| Lives and 300-tick respawn | ✅ | Respawn does not reset ammo or weapon. |

### 4.4 Weapons (the original has 12 plus the night Light)

| # | Weapon | Status | Notes |
|---|---|---|---|
| 0 | Main cannon | 🟡 | Refire 70 ticks matches. **Damage 25 vs legacy 10**. Ammo is unlimited (legacy 30). No alternating barrels. No dual-mode range of 500. |
| 1 | Chain gun | 🟡 | Refire 10 and ammo 1500 match. **Damage 10 vs legacy 1**. No extra-barrel bonus. |
| 2 | Flame thrower | ❌ | |
| 3 | Mines (light) | 🟡 | Damage 7 matches; 100-tick grace; proximity trigger. Heavy mines (16 shots outward) are absent. |
| 4 | Rocket | 🟡 | Locks onto the nearest enemy automatically. **The original has a player-steered lock-on marker** (450 px limit). |
| 5 | Mortar | 🟡 | Arc and falloff work. Damage 15 is a guess (the legacy power row is 0; damage comes from the blast). No recoil and no plane bombs. |
| 6 | A-bomb (countdown, 210 px blast, palette flash) | ❌ | `ExplosionKind.ABOMB` is reused by destroyers and the B52. |
| 7–8 | Paratroopers with chain gun / flamethrower | ❌ | No men actors at all. |
| 9 | Smoke screen | ❌ | |
| 10 | Invisibility | ❌ | |
| 11 | Extra speed | ❌ | |
| — | Light (headlight at night) | ❌ | |
| — | Weapon cycling | 🟡 | The server supports `cycleWeaponLeft/Right`, but **the client binds no keys for it**, so players cannot switch weapons. |
| — | Area damage | 🟡 | Hits tanks only. Turrets, walls, structures, trains and B52s are never damaged. |

### 4.5 World objects

| Object | Status | Notes |
|---|---|---|
| Player start pads (tank / chopper / plane / race / flag, P1 and P2) | 🟥 | The importer does not produce player tanks for most maps. **The client hard-codes player and enemy tanks for `BEGIN1` only** (`Main.kt:444-480`); on other maps nobody is controllable. |
| Computer tanks (with activation, weights, waypoints) | 🟥 | The parser emits `AI_TANK`, which `CanonicalSceneBuilder` drops (`else -> Unit`). |
| Turrets (9 types; shoot-at filter, forbidden zones, fixed direction) | 🟡 | They aim and fire. **They cannot be destroyed**: projectiles just vanish (`ServerProjectileActor.kt:96`). Maps with turrets can therefore never reach "all enemies eliminated". |
| Goals (reached by good tank / evil tank / train / object) | 🟡 | Only the "reached" contribution works; no per-trigger variants. |
| Structure-destruction goal % and object goal % | ❌ | |
| Flags | 🟡 | See CTF above. |
| Products | 🟥 | Pickup only; no price deduction. |
| Locks (blow, kill or remove a target when linked things are destroyed) | 🟥 | Data only. No `stepLocks` in the tick. |
| Warps | 🟡 | Teleport to stored coordinates (15 px radius, 30-tick cooldown). Tanks only; the original also warps men, B52s and rockets. |
| Destroyers | 🟥 | **Unreachable**: `CanonicalSceneBuilder.kt:79` turns DESTROYER and ENFORCER objects into enemy tanks. The actor itself only handles immediate mode, with a fixed 100 damage. |
| Enforcers | 🟥 | Unreachable, as above. `enforcedWeapon` is never read. The parser writes key `evil` while the builder reads `bad`. |
| Trains and 8 wagon types (rails, switches, stations, turret wagon) | 🟥 | Moves 1 px per tick in x with wrap. No rails, no collision, no wagons. |
| Zeppelin | 🟡 | Horizontal sweep. It was a shareware nag in the original, so low priority. |
| B52 flyover and bombing | 🟡 | Periodic bombs; no warp interaction, no wreck. |
| Men (placed and paratroopers) and men in structures | ❌ | Parsed, never built. |
| Bonuses (14 types, random or fixed) | ❌ | Parsed, never built. Nothing grants shield, invulnerability, fuel or ammo. |
| Authored light and heavy mines | ❌ | Parsed, never built. |

### 4.6 Terrain

| Feature | Status | Notes |
|---|---|---|
| 4 worlds (Desert, Grass, Tile, Market), 3 layers, up to 200×200 | ✅ | Imported and rendered. |
| Material speed multipliers (oil, road, …) | 🟡 | Implemented, but **the production `fromSceneJson` path builds `TerrainGrid.empty` and an empty solid layer** (`ServerMatchPrototype.kt:1510-1511`), so this never runs in play. |
| Lava damage, water kill | 🟡 | Same issue. Water also kills amphibious and air units. |
| Pits and small pits | 🟡 | Simplified centre-box test; dead in play for the same reason. |
| Ramps (jumps), conveyor belts, rotating platforms | ❌ | |
| Force fields that reflect bullets | ❌ | |
| Bushes (hide units), shelters | ❌ | |
| Fuel dumps (explode), destructible structures with strength and ruins | ❌ | Walls are indestructible actors. |
| Bridges (destroyable), runways, helipads | ❌ | Material labels only. |
| Race checkpoints and START tile | ❌ | |
| Rails, switches, stations | ❌ | |

### 4.7 AI

| Feature | Status | Notes |
|---|---|---|
| Chase the nearest enemy; aim; fire when aligned | ✅ | |
| A* pathing, line-of-sight gating | 🟡 | Implemented, but walls are invisible to them in play (empty solid layer). `cachedPath` is **one field shared by all AI tanks**, which is a bug with more than one AI. |
| Waypoints (10 per tank, active or circular) | 🟥 | `waypoints = emptyList()` always. |
| Per-mode behaviour | 🟥 | `AiModeDispatcher.isAggressive()` is true for every mode and has no callers. |
| Fuzzy-logic target weighting (tanks vs goals), attack waves, activation radius | ❌ | |

### 4.8 Audio

| Feature | Status | Notes |
|---|---|---|
| Distance, pan and pitch math | ✅ | Unit tested (`AudioMathTest`). |
| Playback | 🟥 | `DesktopAudioBackend.play()` only moves a counter. `AudioManager` is not wired into the game loop. No WAVs are bundled (29 exist in `unpacked/sound`). |
| Per-player stereo in split-screen (P1 left, P2 right) | ❌ | |
| MIDI music | ❌ | |

### 4.9 Editor

| Original editor feature | Status |
|---|---|
| Structure painting per layer, regions, structure links | ❌ |
| Whole-map overview (TAB) | 🟡 (Kubriko zoom) |
| Add, edit, clone and remove objects of every family | 🟡 Only Wall, Tank, Turret and Goal are registered as `Editable` |
| Per-object property menus (controller, ID, loadout, activation, waypoints, …) | 🟡 Generic property inspector over `State` for the 4 types above |
| SPECIAL menu (map type, codes, day/night, goal %, bonuses) | ❌ Sidecar is not editable |
| Create a new map from a world template | ❌ |
| Map locking | ❌ (probably not wanted) |
| Validation (T40), playtest (T41), launch from the client menu | ❌ |

### 4.10 Tooling, quality and delivery

| Item | Status |
|---|---|
| `.MAP` → scene conversion for all 121 maps (the 122nd file has not been checked) | ✅ But the committed scenes are **stale**: regenerated at T06, before T19–T25. They contain only Wall, Tank, Turret and Goal actors. |
| Picture extraction (2,960 PNGs) and generated catalog | ✅ |
| Conversion of sound, music, FLI, LIGHT/NIGHT and `bitmaps.dat` (fonts, credits) | ❌ |
| Replay record, playback, checksums (T36–T38) | 🟥 Data classes only |
| Networking (`RemoteMatchClient`) | ❌ (post-parity) |
| Packaging (T43) | ❌ No distribution config |
| CI | ❌ No `.github/`. `.gitignore` contains `.*`, which would silently ignore `.github/` and `.editorconfig`. |
| Performance pass (T42) | ❌ |

---

## 5. Test coverage

| Module | Test files | `@Test` | Notes |
|---|---|---|---|
| `:game-server` | 33 | 170 | Also hosts `FixedStepClockTest`, `SchemaVersionTest` and `EditorBoundaryGuardTest`, which belong to other modules. |
| `:game-client` | 1 | 12 | `AudioMathTest`, which uses reflection on private members. |
| `:game-content` | 1 | 10 | `LegacySpriteResourcesTest` |
| `:game-protocol`, `:game-editor`, `:tools-mapconv` | 0 | 0 | |

Gaps:

- **No test drives the production path**: `fromSceneJson` with a committed scene, then a playable match.
- 10 test sites use `queueDamage*ForTest` hooks to skip real combat.
- There are no tests at all for Lock, Warp, Destroyer, Enforcer, Train, Zeppelin or B52.
- `ActorRoundTripTest` covers only 5 of the 17 actors.

---

## 6. Where the status docs disagree with the code

| Claim (`full-rewrite-status.md` / `parity-tasks.md`) | Reality |
|---|---|
| T04: round-trip test for **every** `Server*Actor` | Covers Wall, Tank, Turret, Goal and Projectile only. |
| T06: scenes reflect the full importer | Regenerated before T19–T25; no object actors in the committed scenes. |
| T09: chain gun matches legacy damage | 10 vs legacy 1 (the main gun is 25 vs 10). |
| T15/T16: audio routed from server to backend; "working sound playback" | The backend is a no-op stub and is not wired into the client loop. |
| T17/T18: terrain effects applied each tick | Only on the test path `fromCanonicalMap`; the production `fromSceneJson` uses an empty grid. |
| T20: product pickup "with price deduction" | No money exists. |
| T21: lock "fires once condition met" | No lock behaviour in the tick. |
| T22: teleport to "paired exit" | Teleports to its own stored target coordinates; no pairing. |
| T23/T24: destroyers and enforcers execute | Unreachable from maps (the builder converts them to enemy tanks). Enforcer has no effect. |
| T21–T25: "Tests: serialization round-trip" | No such tests exist. |
| T26/T27: AI navigates around walls, respects LOS | Only with a solid layer; production passes an empty one. Path cache is shared across all AI tanks. |
| T28: waypoint patrol, per-mode behaviour | Waypoints are always empty; the dispatcher is a no-op. |
| T29: every mode reaches win/loss per spec | One generic rule set plus a DUAL branch. Race, CTF delivery and supermarket rules are missing. Turret maps can never be won by elimination. |
| T31: `InputFrame.weaponSelect` | Does not exist; there is cycling only, with no client keys. |
| §4 "Terrain (T17/T18)" under the Client heading, `TerrainMaterialTest` has 32 tests | Server-side feature; 31 tests. |
| `asset-migration-audit.md` | Refers to deleted modules (`game-render-kubriko`, `game-core`, `game-legacy`) and to KorGE. |
| `build.md`: source layout `src/kotlin` | Most modules use `src/` directly. |
| `risk-register.md` "Tick-Rate Drift" is Active | Resolved by T01. |

---

## 7. What's left to reach feature-complete

This is ordered by dependency and impact. Items **A1–A6 are correctness blockers**: until they land, most of the "done" server work is not observable in the game.

### A. Make the existing work reachable (blockers)
1. **Production bootstrap:** make `fromSceneJson` build the `TerrainGrid`, solid layer and waypoints from `MapSceneSidecar.tileLayers` and the actors. Add an end-to-end test that loads a committed scene and plays N ticks.
2. **Player spawn from map data:** remove the `BEGIN1` hack in `Main.kt`. Build player tanks from start pads (per mode and vehicle type) and computer tanks from `AI_TANK` objects.
3. **Fix `CanonicalSceneBuilder`:** give DESTROYER, ENFORCER, AI_TANK, MAN, BONUS, MINE, TRAIN and WAGON their own branches. Fix the `evil`/`bad` key mismatch.
4. **Regenerate and commit scenes** (`tools-mapconv scene-all`). Switch `MissionCatalog` to the committed `resources/scenes` instead of re-importing `.MAP` at runtime.
5. **Damageable turrets and structures:** route projectile and area damage to turrets and walls. Add a turret kill score.
6. **Client keys for weapon cycling and shield**, and read the `shield` input on the server.

### B. Combat and vehicle parity
7. A data-driven `VehicleSpec` table ported from `src/data/tanks.c`, covering all 23 vehicles: motion, armor, fuel, per-weapon ammo, speed and power, turret mode, gun positions. Replace the hard-coded constants and fix the damage values.
8. Vehicle families: helicopter hover, plane lift, runway landing, crashes, plane bombs, cars.
9. The remaining weapons: flamethrower, A-bomb, paratroopers (chain gun and flamer), smoke, invisibility, extra speed, headlight. Add a player-steered rocket lock-on marker and heavy mines.
10. Bonuses (14 types, random or fixed), base refill, crash damage, low-armor smoke, wrecks.

### C. World and objects
11. Destructible structures with strength and ruins, fuel dumps, men in structures, and a structure-destruction goal %.
12. Terrain specials: ramps, conveyors, rotators, force fields, bushes and shelters, bridges, race checkpoints and START, full pit shapes, amphibious and air exemptions.
13. Behaviour for locks, goal trigger variants, destroyers (area plus `what`), enforcers, warp pairing and non-tank warping.
14. Trains on rails with switches, stations and wagons (including the turret wagon), and train goals.
15. Men actors: walking, shooting, dying, blood.

### D. Modes and AI
16. Mode rules: DUAL rounds and match score, deathmatch sudden death, race laps and checkpoints, CTF delivery and team checks, Supermarket (cash, quota, timer), losers tank.
17. AI: per-tank path cache, authored waypoints (active or circular), activation radius and activators, fuzzy weighting of tanks vs goals, attack waves.
18. Single-player tank switching (F1–F10) and hand-over on death.

### E. Client, presentation and audio
19. Render every `ActorState` type, plus effects driven by `GameEvent` (explosions, smoke, fire, sparks, splashes, debris) and damaged or ruined tile variants.
20. Real audio: convert `SOUND.DAT` / `unpacked/sound` WAVs into bundled resources, add a `javax.sound` or OpenAL backend, and wire `AudioManager` to `ServerFrame` events. Add MIDI or re-encoded music.
21. Split-screen with two cameras, two HUDs and per-player stereo; second-player input (T33).
22. Night mode: light masks, headlights, light tiles (a shader or alpha-mask approach replacing the palette trick).
23. HUD completeness: weapon strip with ammo, pilot face and messages, goal counters, opponent arrow, round radar with missiles, snapshot interpolation. Remove or gate the debug overlay.
24. Shell: title and intro (convert the FLIs to video or frame sheets), options menu, mission code and password flow, tank-select arena, arena info and key help, pause.
25. Input: key rebinding, RELATIVE/DIRECT control modes, gamepad (T34, T35); persisted config and profile (T39).

### F. Editor and tooling
26. Register every actor type as `Editable`. Add tile-layer painting, a sidecar editor (map type, codes, day/night, goal %, bonuses, mission texts) and a new-map-from-world template.
27. Validation (T40), playtest (T41), and launching the editor from the client menu.
28. Asset converters for sounds, music, FLI, LIGHT/NIGHT and `bitmaps.dat`. Then do T07 (drop the legacy parser) and move `MAPS/` out of the runtime path.

### G. Delivery
29. CI workflow (fix `.gitignore` first), running `./amper test` on every push.
30. Replay record, playback and checksums (T36–T38).
31. Performance pass (T42): especially the ~55k wall actors. Consider a tile-grid collision fast path.
32. Packaging (T43): jpackage or Compose native distributions.
33. Documentation refresh: bring `full-rewrite-status.md`, `parity-tasks.md`, `risk-register.md`, `asset-migration-audit.md` and `build.md` in line with this audit.

### Post-parity
- Networked play (`RemoteMatchClient`, TFN1).
- WASM target (TFN2).

---

## 8. Suggested next milestone

**"One real mission, end to end":** make `BEGIN1` and one DUAL map fully playable from committed scene JSON, with:

- the terrain grid and solid layer;
- map-authored player and AI tanks;
- destructible turrets;
- rendered projectiles and explosions;
- working audio;
- weapon switching.

Items A1–A6 plus the minimal parts of E19 and E20 get there. They turn the existing unit-tested server work into something a player can see, and every later parity task can then be checked in the actual game instead of only in unit tests.

---

## 9. Milestone progress (commits `2fce7af`, `76c20a6`)

### 9.1 What was done

| Item | Status | What changed |
|---|---|---|
| A1 Production bootstrap | ✅ | `fromSceneJson(scene, sidecar, mode)` builds the terrain grid and solid layer from the sidecar. AI routes around water, pits and lava, and holds position when no route exists. |
| A2 Player spawn from map data | ✅ | The parser reads the legacy tank controller fields (`contr.id`, `computer`, `good`, vehicle type, facing, activation). `MatchSetup` assigns control per mode, like `prepare_tank()` and `prepare_player()`. A player drives the whole group of tanks sharing their id, as in the original. The client's `BEGIN1` hack is gone. |
| A3 Scene builder fixes | ✅ | Destroyers, enforcers, map tanks and authored mines get real actors. The enforcer `evil` key is fixed. Tanks, turrets, pads and warps are converted from top-left to centre coordinates. |
| A4 Regenerate scenes; client loads committed scenes | ✅ | Scenes regenerated: 2,040 tanks, 2,018 mines, 221 locks, 130 warps and so on (previously 154 tanks and nothing else). `scenes/index.txt` plus `ShippedScenes` load missions from the classpath. The client no longer imports `.MAP` files. |
| A5 Damageable turrets | ✅ | Projectiles and area damage destroy turrets. Turrets block tanks, score, and apply their goal contribution. |
| A6 Weapon cycling and shield keys | 🟡 | Weapon cycling is bound to keys. The shield key is sent, but the server still ignores it. |
| Mission rules | ✅ | Legacy goal counter (`modify_goal_counter`): object contributions fill the good and bad counters, and losing the last human tank counts as −100. Elimination is only a fallback. DUAL is decided by player elimination. |
| Tank hand-over, F1–F10 switching | ✅ | New `ControlSwitched` event and `selectTankId` input. |
| AI activation | ✅ | Activation radius, activator side, `stay_active`; `attack_tanks = 0` (cars) never hunts. Each AI tank has its own path cache. |
| Fuel | ✅ | Drains per pixel driven, for human tanks only, from a 30,000 tank (it previously ran dry after one second of driving). |
| E19 Rendering (minimal) | 🟡 | Mines, rockets, mortar shells, flags, products, warps, trains, B52s, zeppelins and `EXP1`–`EXP5` explosions are drawn. Tanks use their own body, turret and wreck frames. No smoke, debris or ruined tiles yet. |
| E20 Audio | ✅ | Real `javax.sound` playback of the 29 legacy samples with volume, pan and pitch, wired into the match loop. |
| E21 Split screen, second player | ✅ | Side-by-side views and the legacy two-player key layout. |
| Flaky tests | ✅ | The server waits for Kubriko's asynchronously derived dynamic-actor list after adding or removing actors. Ticking before it caught up silently skipped `update()`. |

### 9.2 How it was verified

- Amper still cannot be downloaded in this sandbox. A throwaway Gradle build (not committed) compiled every module against the same Kotlin, Compose and Kubriko versions.
- Compose's `androidx.*` runtime artifacts are only on Google Maven, which the network policy blocks. Tests ran against the last runtime published to Maven Central, plus a stub of `androidx.lifecycle.Lifecycle`.
- Results: 185 server tests (15 new), 12 client tests and 10 content tests pass. The server suite passed six consecutive full runs after the race fix.
- New tests use the committed scenes: BEGIN1 spawns, the terrain grid, win by goal counter, loss of the last tank, DUAL pads and group control, co-op ids, and the PLANES2 squadron. Fixture tests cover hand-over, F-key switching, turret destruction and fuel.
- **The desktop UI was not launched.** Split screen, sprites, HUD and audio compile and are unit-covered where possible, but nobody has looked at them yet. Run `./amper task :game-client:runJvm` to check.

### 9.3 Still open from this milestone

- The server ignores the shield key (it is sent, but no shield-power pool exists yet).
- Vehicle stats from `src/data/tanks.c` are still not used. Every tank shares one set of constants and damage values (B7).
- `RACE3.MAP` still fails header parsing, so 121 of 122 maps are converted.
- T07 (drop the legacy parser): the client no longer depends on it, but `tools-mapconv` and the server test fixtures still do.
