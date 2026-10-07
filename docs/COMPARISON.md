# TankArena: C Original vs Kotlin Rewrite — Comparison Report

> Generated: 2026-05-19
> C source: ~19,000 lines across `src/`
> Kotlin source: ~19,850 lines across 6 Gradle modules

---

## 1. Architecture Overview

### C Original (`src/`)
The original codebase is a monolithic DOS 16-bit application targeting VGA mode 13h (320×200) via Allegro. It uses:

- **A single linked list** of `objectstruct` sorted by draw height (`h`) for z-ordering.
- **Function pointers** (`control`, `write`, `hit`, `remove`, `light`, `mwrite`) on each object for polymorphic behavior — a hand-crafted ECS.
- **Two linked lists** for bullets (`bulletstruct`) and effects (`effectstruct`) separately from the main object list for performance.
- **Raw pointer sprite data** loaded from `.DAT` files.
- **Direct hardware access** for sound (speaker port), timer, keyboard, and joystick via inline assembly (`.s` files).
- **Global state** declared in `globals.h` (e.g., `game`, `map`, `obj`, `timer`, `scr`).
- **Fuzzy logic** for AI (membership values, AND/OR/NOT operators on [0, 255]).

```
src/
├── check/       — collision (collide, hitwall, hitobj, overlap, pit)
├── data/        — embedded picture/tank data definitions
├── edit/        — map editor (23 files, very complete)
├── game/        — game modes (single, dual, dualvc), menus, settings
├── graph/       — rendering (buffer, sprites, palette, mode13)
├── hardware/    — sound, timer, keyboard, joystick (assembly)
├── include/     — all headers (types, globals, defines)
├── objects/     — game objects (tank, turret, mine, rocket, B52, ...)
├── setup/       — first-run setup (sound config, etc.)
├── tanks/       — tank physics, control, AI (fuzzy logic)
├── utils/       — tools (picture converters, password)
└── world/       — world rendering (bullets, effects, under-layer)
```

### Kotlin Rewrite
The rewrite uses a **server-client architecture** with a **protocol boundary** and the Kubriko game engine:

```
game-protocol/   — Protocol data types (WorldSnapshot, ActorState, PlayerInput)
game-content/    — Map parsing, sprite catalog, terrain grid
game-server/     — Simulation (ServerMatchPrototype, Server*Actor, AI)
game-client/     — Rendering (Kubriko + Compose), menus, HUD, audio
game-editor/     — Editor shell (stub)
tools-mapconv/   — Map converter (legacy .MAP → JSON)
```

Key architectural departures:
- **Kubriko engine** (external dependency) provides `ActorManager`, `CollisionManager`, and serialization.
- **State-based snapshots**: The server emits `WorldSnapshot` + `PlayerView` per tick; the client replicates render actors.
- **No raw hardware**: Sound uses JLayer/JSynth, input uses Compose/desktop key events.
- **Serialisable actors** via Kotlinx.serialization for replay/save support.
- **No monolithic global state**: The `ServerMatchPrototype` encapsulates a match; actors are first-class objects.

---

## 2. Feature Coverage

### 2.1 Core Gameplay Mechanics

| Feature | C Original | Kotlin Rewrite | Status |
|---------|-----------|----------------|--------|
| Tank movement (forward/reverse) | ✅ Physics-based (mass, accel, friction) | ✅ Simplified float physics | **Matched** |
| Tank turning (16 directions) | ✅ `dir & 15`, rotation speed per tank | ✅ `LegacyDirections.stepLeft/Right` | **Matched** |
| Turret aiming (independent from body) | ✅ Separate `ldir`, 16 dirs, `MAIN_TURRET` flag | ✅ Separate `turretDirection` | **Matched** |
| Main cannon firing | ✅ Bullet at velocity `v`, max distance, smoke | ✅ `ServerProjectileActor` with TTL | **Matched** |
| Chain gun | ✅ Separate ammo, fire rate, short TTL | ✅ Separate ammo, cooldown | **Matched** |
| Mine deployment | ✅ Light/heavy mines, 100-tick arming | ✅ Light mine only | **Partial** (no heavy mine) |
| Homing rocket | ✅ Full homing (`home()` function), smoke trail | ✅ Homing via `ServerRocketActor` | **Matched** |
| Mortar | ✅ Arcing projectile, expanding blast radius | ✅ Arcing with `ServerMortarActor` | **Matched** |
| Weapon switching | ✅ `num_weapon` (12) types via `tnk.weap.w` | ✅ 5 weapons, cycle via `nextOwnedWeapon` | **Matched** |
| Tank collision (tank vs tank) | ✅ Elastic collision with mass ratio | ✅ AABB push + velocity kill | **Approximated** |
| Tank vs wall (sprite overlap) | ✅ Pixel-level `overlap_rb/lb/rt/lt` vs sprite mask | ✅ AABB vs `ServerWallActor` | **Approximated** |
| Wall destruction (linked tiles) | ✅ Recursive `hit_wall()` with `LEFT/RIGHT/UP/DOWN` chain | ❌ Not implemented | **Missing** |
| Wall strength per layer per hit type | ✅ `strength[2][18][max_map_x*max_map_y]` | ❌ Single AABB per wall segment | **Missing** |
| Fuel system | ✅ Per-tick drain, fuel exhaustion kills | ✅ Per-tick drain, no movement when empty | **Matched** |
| Shield system | ✅ Charge/discharge (`power 0–6000`), `icount` invulnerability ticks | ✅ `shield` value, `invulnerableTicks` | **Matched (simplified)** |
| Lives / respawn | ✅ Tank ID system, search for replacement tank | ✅ `lives`, `respawnInTicks=300`, spawn point | **Matched** |
| Invulnerability on spawn | ✅ `tinit(200)` = 2 seconds | ✅ `invulnerableTicks` (initial value from State) | **Matched** |
| Radar | ✅ Range-based, colored dots by team | ✅ `RadarContact` list per player | **Matched** |

### 2.2 Game Objects

| Object | C Original | Kotlin Rewrite | Status |
|--------|-----------|----------------|--------|
| AI Tank | ✅ Fuzzy logic (700+ lines) | ✅ Pathfinding + targeting | **Rewritten (simpler AI)** |
| Turret | ✅ 9 types, configurable aim/shoot | ✅ 1 generic type, `step()` | **Simplified** |
| B52 | ✅ Multi-tile, 10 bombs, lights, wreck state | ✅ `ServerB52Actor`, linear move, bombs | **Simplified** |
| Train | ✅ Rail system (`RAILS_*` tiles), wagons, turret | ✅ `ServerTrainActor` (linear, no rails) | **Simplified** |
| Zeppelin | ✅ 32×16 tile multi-image, shareware-only | ✅ `ServerZeppelinActor` (linear move) | **Simplified** |
| Warp | ✅ Teleport tank within 15px radius | ✅ `ServerWarpActor` with cooldown | **Matched** |
| Destroyer (ABOMB) | ✅ Nuke all in radius | ✅ `ServerDestroyerActor`, area damage | **Matched** |
| Enforcer | ✅ Enforce weapon on AI tanks in radius | ✅ `ServerEnforcerActor` | **Matched** |
| Flag | ✅ Pick up, carry, base capture, flag number | ✅ `ServerFlagActor` (pickup, carry, return) | **Matched** |
| Goal | ✅ 4 types (good/bad/train/linked target) | ✅ `ServerGoalActor` (good/bad, radius) | **Simplified** |
| Lock | ✅ Activation range, target object | ✅ `ServerLockActor` | **Simplified** |
| Product | ✅ 15 types, pickup | ✅ `ServerProductActor` | **Matched** |
| Bonus | ✅ Random spawn from destroyed buildings | ❌ Not implemented | **Missing** |
| Man (civilian) | ✅ Spawn from destroyed buildings, patrol | ❌ Not implemented | **Missing** |

### 2.3 Game Modes

| Mode | C Original | Kotlin Rewrite | Status |
|------|-----------|----------------|--------|
| Single player (mission-based) | ✅ Code-based mission progression | ✅ Mission code, goal tracking | **Matched** |
| Dual (local 2-player) | ✅ Tank selection, round system | ⚠️ Partial (DUAL win condition in evaluateMission) | **Partial** |
| DualVC (network 2-player) | ✅ Separate control scheme | ❌ Not implemented | **Missing** |
| Mayhem/supermarket | ✅ Random products, timer, cash | ❌ Not implemented | **Missing** |

### 2.4 Terrain

| Feature | C Original | Kotlin Rewrite | Status |
|---------|-----------|----------------|--------|
| Terrain types (200+ tile types) | ✅ 3-layer maps (`mapm.m[0/1/2]`) | ✅ `TerrainMaterial` enum (24 types) | **Matched (subset)** |
| Speed modifiers per terrain | ✅ `pic[...].speed` multiplier | ✅ `TerrainTile.speedMultiplier` | **Matched** |
| Water (kill non-amphibious) | ✅ `hit_obj_for` on WATER tiles | ✅ `queueDamage(999)` | **Matched** |
| Lava damage | ✅ Continuous `HT_LAVA` damage | ✅ `queueDamage(1)` per tick | **Matched** |
| Pits (big/small, ramp escape) | ✅ Sub-tile `in_pit()` check, ramps | ✅ `checkPit()` (simplified) | **Approximated** |
| Ramps (small/big, vertical impulse) | ✅ `on_big_ramp/on_small_ramp` → `vz += speed/5` | ❌ Not implemented | **Missing** |
| Bridges (collapse to water) | ✅ `BRIDGE_H/V/CR` → water on layer 0 destroy | ❌ Not implemented | **Missing** |
| Helisite/Runway | ✅ Plane land/takeoff | ❌ Not implemented | **Missing** |
| Crater speed modifier | ✅ Via `NORM_KRAT/ROAD_KRAT` | ❌ Not implemented | **Missing** |
| Bushes (opacity) | ✅ Semi-transparent wall layer | ❌ Not implemented | **Missing** |
| Night mode | ✅ Separate `night_pal`, light sources | ❌ Not implemented | **Missing** |

### 2.5 Rendering

| Feature | C Original | Kotlin Rewrite | Status |
|---------|-----------|----------------|--------|
| Mode 13h (320×200, 256 colors) | ✅ Direct VGA hardware | ❌ Uses Kubriko + Compose desktop window | **Different platform** |
| Double buffering | ✅ Two `scr` pages, `scroll_screen` | ✅ Kubriko handles swap | **Matched concept** |
| Sprite rendering | ✅ Raw pointer arrays, direction variants | ✅ `LegacySpriteCatalog`, Compose drawables | **Matched concept** |
| Multi-layer z-ordering | ✅ Sorted linked list by `h` (-4 to 24) | ✅ Kubriko z-ordering | **Matched concept** |
| Pixel overlap collision | ✅ `overlap_rb/lb/rt/lt` raw sprite comparison | ❌ Not used | **Missing** |
| Effects (explosions, dust, smoke) | ✅ 100+ effect types, linked list | ✅ `GameEvent` for sound events | **Partial (sound only)** |
| Screen shake / camera | ✅ `ssx/ssy` scrolling | ✅ `cameraCenterX/Y` in `PlayerView` | **Matched concept** |
| 5×5 font text | ✅ Custom 5×5 font (`.s` file) | ✅ Compose `Text` with custom font | **Matched concept** |
| Radar overlay | ✅ Colored dots, range circle | ✅ `RadarOverlay` Compose screen | **Matched concept** |
| Status bar / HUD | ✅ `write_status()` with bar gauges | ✅ `HudOverlay` Compose screen | **Matched** |
| Debrief screen | ✅ Score display, next mission code | ✅ `DebriefScreen` Compose screen | **Matched** |
| Panel (bottom bar) | ✅ `panel` bitmap | ⚠️ Not yet rendered | **Missing** |

### 2.6 Audio

| Feature | C Original | Kotlin Rewrite | Status |
|---------|-----------|----------------|--------|
| Sound effects | ✅ Direct speaker + sound card | ✅ `AudioManager`, WAV playback | **Matched concept** |
| Music (MIDI) | ✅ `TUNE.MID`, `BELVED.MID` | ❌ Not implemented | **Missing** |
| Directional sound | ✅ `create_sound_absolute` (distance panning) | ✅ `GameEvent.Sound` with position | **Matched concept** |
| Sound categories | ✅ 18+ types (explode, crash, etc.) | ✅ `SoundKind` enum | **Matched** |

### 2.7 Input

| Feature | C Original | Kotlin Rewrite | Status |
|---------|-----------|----------------|--------|
| Keyboard | ✅ Direct port read (`inportb`) | ✅ Compose key events | **Matched concept** |
| Joystick | ✅ Assembly direct port access | ❌ Not implemented | **Missing** |
| Dual control scheme | ✅ `rel_or_dir[]`, relative vs absolute | ✅ `PlayerIntentFrame` (12 boolean flags) | **Matched concept** |
| Shield button | ✅ `PLAYER_SHIELD` | ✅ `shield` in `PlayerIntentFrame` | **Matched** |

---

## 3. Major Deviations From the Original

### 3.1 Physics Model

The C original uses **per-tank type physics** stored in `tankinfotype`:
- `mass`, `acc`, `ff_0`, `ff_v`, `fs_0`, `fs_v`, `crash`, `lift`, `rotation`, `change`

Each of 23 tank types has different movement characteristics (e.g., tanks vs cars vs helicopters vs planes).

The Kotlin rewrite uses **uniform physics constants** in `ServerTankActor`:
- `FORWARD_ACCELERATION = 0.45`, `REVERSE_ACCELERATION = 0.28`
- `LONGITUDINAL_FRICTION = 0.10`, `LATERAL_FRICTION = 0.18`
- `MAX_FORWARD_SPEED = 4.75`, `MAX_REVERSE_SPEED = 2.25`
- `TURN_COOLDOWN_TICKS = 7`, `TURRET_TURN_COOLDOWN_TICKS = 7`

**Impact:** All tanks in the Kotlin rewrite feel the same. The original has 23 distinct tank "feel" profiles — light cars are faster, heavy tanks turn slower, etc. This is a significant gameplay difference.

### 3.2 Wall Collision: Pixel Overlap vs AABB

The C original computes collision by comparing the tank's sprite against wall sprite masks using `overlap_rb/lb/rt/lt` (raw assembly-optimized pixel overlap). This gives **precise corner-cutting** behavior and allows the tank to partially overlap destructible walls.

The Kotlin rewrite uses **AABB (Axis-Aligned Bounding Box)** collision via `ServerWallActor` and `BoxCollisionMask`. Tanks are 29×29 pixel squares.

**Impact:**
- No corner-cutting in the rewrite.
- Walls don't have per-pixel destructible areas.
- The tank-to-tank elastic collision (`tanks_collide()`) is replaced with simple AABB push + velocity kill.

### 3.3 AI: Fuzzy Logic → Pathfinding + Targeting

The C original AI is a **fuzzy logic system** (700+ lines in `computer.c`) with:
- Fuzzy membership functions (close, left, front, right)
- Fuzzy operators (AND, OR, NOT on [0,255])
- Waypoint following with fuzzy terrain preference
- Activation radius, target preference weights

The Kotlin rewrite uses:
- `TilePathfinder` (A* on terrain grid)
- `LineOfSight` (Bresenham raycast)
- `WaypointFollower` (sequential waypoint navigation)
- Nearest-enemy targeting + turret aim-at-target

**Impact:** The rewrite AI is more modern and testable but lacks the nuanced fuzzy behavior. The original AI had configurable preferences (attack tanks vs attack goals vs follow waypoints) that gave each AI tank personality. The rewrite AI is simpler but more predictable.

### 3.4 Game Loop: Polling → Fixed-Tick Engine

The C original runs a **variable-frame polling loop**:
```c
// mainloop.c
do {
    game_control();
    write_screen();
    done = !do_fade_step();
} while (!tdone(game.end_round));
```

Timers use `timer` (global counter incremented per tick) with `tinit()`/`tdone()` for relative comparisons.

The Kotlin rewrite uses a **fixed 10ms tick** via Kubriko's `ManualTickSource`:
```kotlin
MILLIS_PER_TICK = 10
tickSource.tick(MILLIS_PER_TICK)
```

**Impact:** The rewrite is deterministic and supports replays. The original is frame-rate dependent and non-deterministic.

### 3.5 Map Format: Binary .MAP → JSON

The C original loads binary `.MAP` files with `maptype` header (packed struct) followed by map data and object blobs. The `mapm.m[3][max_map_x*max_map_y]` stores 3 layers of picture indices.

The Kotlin rewrite uses `LegacyMapParser` + `LegacyObjectParser` to parse `.MAP` files into `CanonicalLegacyMap` and then `LegacyToSceneJson` for JSON output.

**Impact:** The rewrite can read the original maps but converts them to JSON for editing. This is a positive deviation — it enables modern map editing.

### 3.6 Object System: Function Pointer VTable → Actor Classes

The C original uses a **vtable of function pointers** on each `objectstruct` for polymorphism:
```c
o->funct.control = control_tank;
o->funct.write = write_tank;
o->funct.hit = hit_tank;
```

The Kotlin rewrite uses **class inheritance** with Kubriko's `Actor` interface. Each actor type is a Kotlin class (`ServerTankActor`, `ServerTurretActor`, etc.) with a serializable `State` data class.

**Impact:** The Kotlin approach is safer, serializable, and testable. The C approach is more flexible but prone to pointer corruption.

---

## 4. Missing Features (Not Yet Implemented)

### High Priority (Core Gameplay)
1. **Wall destruction with chain reaction** — The recursive `hit_wall()` in `hitwall.c` allows buildings to collapse in L/R/U/D directions, with different explosion types per wall type. This is a hallmark of Tank Arena.
2. **Bonus objects** — When buildings are destroyed, random bonuses spawn (fuel, ammo, shield, invulnerability, smoke).
3. **Civilian (Man) objects** — Civilians flee destroyed buildings and add visual atmosphere.
4. **Rail system for trains** — Trains in the original follow specific rail tiles (`RAILS_H`, `RAILS_V`, `RAILS_CR`, `RAILS_TL_A` through `RAILS_BR_D`, etc.). The rewrite uses linear movement.
5. **Ramp physics** — Small and big ramps give vertical impulse to tanks, enabling jumps over gaps.
6. **Per-tank-type physics** — 23 tank types with distinct mass, acceleration, friction, and turn rates.

### Medium Priority
7. **Heavy mines** — The original has two mine types. Heavy mines fire 8 sub-rockets in all directions. The rewrite only has light mines.
8. **Multiple turret types** — The original has 9 turret types with configurable fire rate, power, radius, and targeting. The rewrite has 1 generic type.
9. **DualVC network mode** — The original supports networked 2-player. The rewrite protocol supports it in theory but no network transport is implemented.
10. **Mayhem/supermarket mode** — A timed mode with random product spawns and a cash system.

### Low Priority (Polish)
11. **Night mode** — Separate palette, light sources on tanks/turrets.
12. **Bush opacity** — Semi-transparent wall rendering.
13. **Bridge collapse** — Destroying bridge layer 0 reveals water underneath.
14. **Joystick support** — Original uses assembly-level joystick polling.
15. **MIDI music** — Background music tracks.
16. **Zeppelin animation** — The original shares a 32×16 multi-tile animated sprite (shareware easter egg).
17. **Panel bar rendering** — Bottom status bar with icons.
18. **B52 lights** — Animated landing lights on the B52.
19. **Effects rendering** — 100+ particle effects (dust, smoke, fire, explosions) as rendered sprites. The rewrite emits `GameEvent.Sound` but not visual effects.

---

## 5. Code Quality Assessment

### Kotlin Strengths
- **Testability**: 25+ test files with comprehensive coverage of core mechanics (tank movement, collision, AI, mines, rockets, mortars, flags, goals, missions).
- **Serialization**: All actors implement `Serializable.State` — replays and saves are built-in.
- **Type safety**: No pointer arithmetic, no magic numbers (most are `const val`), exhaustive `when` expressions.
- **Modular**: 6 well-separated modules with clear boundaries.
- **Replay support**: Protocol version tracking with `PROTOCOL_VERSION`.
- **Deterministic**: Fixed 10ms ticks, no frame-rate dependence.

### Areas Where Kotlin Diverges
- **Physics uniformity**: All tanks share the same movement constants — loses the original's variety.
- **Simplified terrain**: The rewrite's terrain system handles speed multipliers and kill effects but doesn't model the full 3-layer map system.
- **No pixel collision**: AABB is cleaner but changes how tanks interact with wall corners.
- **Simplified AI**: Pathfinding is more robust than fuzzy logic but lacks the original's configurability.

---

## 6. Progress Summary

### Overall Completion: ~55-60%

| Category | Coverage |
|----------|----------|
| **Core tank physics & controls** | ✅ 90% |
| **Firing & weapons (5 types)** | ✅ 90% |
| **Game objects (tanks, turrets, mines, rockets, mortars)** | ✅ 80% |
| **Secondary objects (B52, train, zeppelin, warp, destroyer, enforcer, flag, goal)** | ✅ 60% |
| **Wall system (collision + destruction)** | ⚠️ 30% (AABB collision works, destruction missing) |
| **Terrain effects (speed, water, lava, pits)** | ✅ 80% |
| **AI** | ✅ 70% (pathfinding works, no fuzzy logic) |
| **Map loading & parsing** | ✅ 85% |
| **Rendering (sprites, HUD, radar, menus)** | ✅ 60% |
| **Audio (SFX)** | ✅ 70% |
| **Audio (Music)** | ❌ 0% |
| **Game modes (single)** | ✅ 70% |
| **Game modes (dual)** | ⚠️ 30% |
| **Game modes (DualVC, mayhem)** | ❌ 0% |
| **Map editor** | ❌ 10% (stub) |
| **Bonus/civilian objects** | ❌ 0% |
| **Night mode** | ❌ 0% |

### What Works End-to-End Today
1. Load a legacy `.MAP` file → parse terrain + objects
2. Start a single-player mission
3. Control a tank (move, turn, aim, fire 5 weapons)
4. AI tanks pursue and fire at player
5. Turrets target and fire at tanks
6. Walls block movement (AABB)
7. Terrain affects speed and can kill (water, lava, pits)
8. Warps teleport tanks
9. Flags can be picked up and carried
10. Goals can be captured
11. Missions win/lose based on goals, kills, and elimination
12. Radar shows allies/enemies/goals
13. HUD displays armor, fuel, lives, score, kills, time, weapon
14. Menu system (main menu, mission select, debrief)

### What Needs More Work
1. **Wall destruction chain reactions** — the most iconic gameplay element missing
2. **Bonus/civilian spawning** from destroyed buildings
3. **Per-tank-type physics profiles** to restore movement variety
4. **Heavy mines and multiple turret types**
5. **Rail system for trains**
6. **Ramp physics**
7. **Night mode**
8. **Visual particle effects** (explosions, smoke, dust)
9. **Dual game mode completion**
10. **Map editor** (currently only a stub in `game-editor`)

---

## 7. Conclusions

The Kotlin rewrite faithfully captures the **core simulation** of Tank Arena: tanks move, aim, fire, collide, take damage, respawn, and can win or lose missions. The protocol-bound architecture is a significant improvement over the original's monolithic design, enabling replays, serialization, and potential network play.

Where it deviates most significantly is in **physics fidelity** (uniform tank behavior vs 23 profiles), **collision precision** (AABB vs pixel overlap), **AI sophistication** (pathfinding vs fuzzy logic), and **visual effects** (no particle rendering yet).

The most impactful missing feature is **wall destruction with chain reactions** — the recursive building collapse is central to Tank Arena's gameplay identity. Without it, the game feels more like a generic top-down shooter than Tank Arena.

The code quality and test coverage of the Kotlin rewrite are excellent and represent a substantial improvement over the C original. The remaining ~40% of work is mostly in rendering polish, secondary game objects, and terrain interaction details rather than fundamental architecture changes.
