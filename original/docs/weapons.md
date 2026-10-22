# Tank Arena — Weapons (12 + 1 on night maps)

Source: `src/tanks/fire.c`, `src/world/bullet.c`, `src/objects/rocket.c`, `src/objects/mortar.c`, `src/objects/mine.c`,
`src/objects/abomb.c`, `src/objects/man.c`

## Weapon Index Table

| Index | Name (in game)  | Creates                          | Uses          | Power used          |
|-------|-----------------|----------------------------------|---------------|---------------------|
| 0     | MAIN CANNON     | `weap.main` bullet               | 1 shell       | `weap.power[0]`     |
| 1     | CHAIN GUN       | 1 `HT_CHAIN` bullet per barrel   | 5 rounds      | `weap.power[1]`     |
| 2     | FLAME THROWER   | `HT_FLAME` bullet                | 40 fuel       | `weap.power[2]`     |
| 3     | MINES           | Light mine object                | 1 mine        | `weap.power[3]`     |
| 4     | ROCKET          | Guided rocket (lock marker)      | 1 rocket      | `weap.power[4]`     |
| 5     | MORTAR          | `HT_MORTAR` (planes: plane bomb) | 1 shell       | fixed 5 per step    |
| 6     | A-BOMB          | A-bomb object                    | 1 bomb        | fixed 4 per step    |
| 7     | MEN WITH CG     | Man with chain gun (lock marker) | 1 man         | `weap.power[7]`     |
| 8     | MEN WITH FL     | Man with flamethrower            | 1 man         | `weap.power[8]`     |
| 9     | SMOKE SCREEN    | 100 smoke puffs                  | 1             | —                   |
| 10    | INVISIBILITY    | 2300 ticks invisible             | 1             | —                   |
| 11    | EXTRA SPEED     | 1000 ticks of 1.5× thrust        | 1             | —                   |
| 12    | LIGHT           | Toggles the tank's light         | —             | —                   |

Names from `src/tanks/fire.c:12-24`. Ammo, refire delay and power per vehicle are listed in `vehicles.md`.

## Selecting and Firing

- The weapon keys step to the next or previous weapon that has ammo; the flamethrower counts when the vehicle has
  `weap.flame`, the Light only on night maps. There are 15 ticks between switches, and no switching while the lock
  marker is in use (`src/tanks/fire.c:26-78`).
- A tank fires only when its refire time is over and it is at `h ≥ 1` (`src/tanks/fire.c:108`).
- In water (`und.nw == WATER`, `h < 2`) a `MAIN_STATIC` or `MAIN_ROTATE` vehicle can only use invisibility and extra
  speed; a `MAIN_TURRET` vehicle can also use the main gun, flamethrower, rockets and mortar
  (`src/tanks/fire.c:111-139`).
- A player tank with no ammo plays `SND_EMPTY` and waits 50 ticks. Otherwise the ammo goes down (5 for the chain gun)
  and the next shot waits `weap.speed[w]` ticks (`src/tanks/fire.c:143-168`).
- Computer tanks use no ammo and wait `computer.fire_delay` ticks between shots (`src/tanks/fire.c:170-171`).

## Detailed Weapon Descriptions

### 0 — Main Cannon

- **Bullet**: `weap.main` of the vehicle, `HT_MAIN` or `HT_SROCK` (small rocket). It leaves from the current barrel
  (barrels take turns) in the gun direction (`src/tanks/fire.c:177-200`).
- **Speed**: 200 px/s plus the owner's velocity, at least 75 px/s (`src/world/bullet.c:84-95`).
- **Range**: 250 px, 500 px in `DUAL`, scaled by the same speed factor (`src/tanks/fire.c:184`).
- **Small rocket main guns** ignore the first 30 px of flight and leave an exhaust trail.
- **Damage**: `weap.power[0]` (10, or 5 for several vehicles). A tank that is hit also gets a push in the bullet's
  direction (`src/check/hitobj.c:108`).

### 1 — Chain Gun (`HT_CHAIN`)

- **Burst**: one bullet from each chain-gun barrel (`chain_gun[0]`), plus one more after the Extra Gun bonus when the
  vehicle has fewer than 4 barrels. Always along the hull (`src/tanks/fire.c:202-214`).
- **Range**: 5 × 33 = 165 px. **Damage**: 1 per bullet. Uses 5 rounds per burst.

### 2 — Flamethrower (`HT_FLAME`)

- **Bullet**: 75 px/s for 120 px; it does not get the owner's velocity (`src/world/bullet.c:72-76`).
- **Hits** every object within 15 px of its path, and the first structure it meets; it is never reflected.
- **Damage**: 1 per hit (`weap.power[2]`). The refire delay is 6 ticks (15 for ATOMIC TANK).
- **Fuel**: 40 per shot; no shot with 40 fuel or less.

### 3 — Mines (`HT_MINE`)

A tank lays a light mine at its own position with power `weap.power[3]` (7) (`src/tanks/fire.c:228-230`). Heavy mines
are only placed in the map editor.

| Type       | Constant     | Trigger distance | When it goes off                                     |
|------------|--------------|------------------|------------------------------------------------------|
| Light mine | `LIGHT_MINE` | `r + 3` px       | Hits objects within 30 px and the structure under it |
| Heavy mine | `HEAVY_MINE` | `r + 50` px      | Fires 8 small rockets: power 10, range 100 px        |

- A mine goes off when an object of radius `r` comes within the trigger distance at a height difference below 2
  (`src/check/collide.c:33-40`), or when it is hit.
- The heavy mine fires in every second of the 16 directions (`for (i = 0; i < 16; i += 2)`,
  `src/objects/mine.c:21-24`).
- Laid mines arm after 100 ticks; mines placed in the map are armed at once (`src/objects/mine.c:54,63`).
- Light mines are never drawn during play. Heavy mines are drawn, except on water (`src/objects/mine.c:7-10`).

### 4 — Guided Rocket (`HT_ROCKET`)

- **Lock marker** (player): pressing fire starts a marker at the tank. While fire is held, the direction keys move it
  every 3 ticks by 5 px, growing by 1 per move up to 20 px. It stays within 450 px of the tank, except in `DUAL`. The
  tank does not drive while aiming and the view follows the marker. Releasing fire launches the rocket at the marker;
  the view stays there for 50 more ticks (`src/tanks/fire.c:232-320`).
- **Target**: the nearest object with a radius within 15 px of the marker; the rocket homes on it. Without one it flies
  to the marker point (`src/objects/objects.c:189-205`).
- **Flight**: starts at 0 px/s and does not get the owner's velocity. It gains 1 px/s per game step up to 150 px/s and
  flies at `h = 6` over everything for at most 3300 px (`src/objects/rocket.c:15-38`, `:63-69`).
- **Impact**: within 7 px of the target it hits objects within 10 px of the target with `HT_ROCKET`, power
  `weap.power[4]` (10); without a target it hits the structure at the point. Rockets hit tanks even under the
  shield. It explodes with `LEXPLOSION`.
- **Warning**: the target gets the message "INCOMING MISSILE!" and `SND_INCOMING` at launch.
- A rocket can be shot down. Computer tanks fire straight at their target, without a marker.

### 5 — Mortar (`HT_MORTAR`)

- **Shell**: flies in a straight line at 75 px/s for 120 px, over walls and objects. The arc is only drawn
  (`src/world/bullet.c:72-76`, `:169`, `:256-260`). Firing pushes the tank back (impulse 50).
- **Explosion**: `PT_MORTAR` debris and `SND_NUKE`, then a blast that grows from 5 to 60 px in steps of 5 every
  3 ticks. Each step hits objects and structures within the radius with `HT_MORTAR`, power 5 (`src/objects/mortar.c`).
- **Planes** drop a plane bomb (`HT_PLANEBOMB`) instead: it falls for `10 × h` px (120 px at `h = 12`) at 75 px/s and
  explodes like a mortar shell (`src/world/bullet.c:78-82`).

### 6 — A-Bomb (`HT_ABOMB`)

- **Countdown**: dropped at the tank with count 3; the count drops every 60 ticks, so the blast starts 240 ticks later.
  The bomb shows the countdown frames and is hidden on water (`src/objects/abomb.c:79-133`).
- **Blast**: 30 steps of 6 ticks. Step `n` hits structures and objects within `n × 7` px (up to 210 px) with
  `HT_ABOMB`, power 4.
- Tanks on a `SHELTER` tile are not hurt (`src/tanks/tank.c:115`). A-bombs are not hit by other A-bombs; before the
  blast any other hit removes the bomb.
- The palette fades towards white and back during the blast.
- ATOMIC TANK (when destroyed), the nuclear wagon and `ABOX` tiles start a blast at once (count 0).

### 7 and 8 — Men

- **Deploy**: aimed with the lock marker like the rocket. The man appears 20 px from the tank towards the marker and
  walks at 5 px/s to the nearest object within 15 px of the marker, or else to the point; he stops within 20 px
  (`src/tanks/fire.c:94-100`, `src/objects/man.c`).
- **Weapon**: weapon 7 gives a chain gun (`HT_CHAIN`), weapon 8 a flamethrower (`HT_FLAME`), with power
  `weap.power[7]` or `[8]` (1). He fires towards his target every 40–80 ticks; range 165 px (flame: 120 px).
- **Removal**: after 75 bullets, on any hit, in water, in a pit, when a tank, train or low B52 runs into him, or when he walks
  into a structure (which he hits with `HT_CHAIN`, power 10). Death leaves `BLOOD` and plays `SND_SCREAM`.

### 9 — Smoke Screen

Adds 100 to `und.smokes`. The tank then leaves a `SMOKE_SCREEN` puff behind it every 10–20 ticks until 100 puffs are
made (`src/tanks/control.c:432-437`).

### 10 — Invisibility

- Lasts 2000 + 300 ticks (`invis.count` 2, then 1) (`src/tanks/fire.c:263-264`, `src/tanks/control.c:386-398`).
- Other players' windows do not draw the tank; its own window shows it flickering, fast in the last 300 ticks.
- Turrets, the radar and the `DUAL` arrow ignore it (`src/objects/turret.c:26`, `src/game/radar.c:37`,
  `src/world/status.c:47`). Computer tanks still target it. Its light is off.

### 11 — Extra Speed

Thrust × 1.5 for 1000 ticks (`src/tanks/control.c:60`).

### 12 — Light (Night Maps Only)

The player selects it like a weapon; firing switches the tank's light on or off (`last_min.light_on`, on at start) and
plays `SND_LIGHT` (`src/tanks/fire.c:273-277`, `src/tanks/tank.c:266`). The light follows the turret while the main
gun, flamethrower or mortar is selected. Light sources in the map (`LIGHT_SRC`) shine on their own.

## Bullet Behavior

### Walls and Reflection

Every bullet except flame, mortar shells and plane bombs checks its path against structures
(`src/world/bullet.c:169-199`). On a hit the structure is damaged; then, if the bullet is not a flame and the tile has a
standing layer 1 structure, the bullet moves to the hit point, loses its owner and gets 4 px of safety. On a field tile
it is reflected and flies on:

- `FIELD_H` — direction `8 - dir`
- `FIELD_V` — direction `-dir`
- `FIELD_P` — direction `4 - dir`
- `FIELD_N` — direction `12 - dir`

Otherwise the bullet ends. Bullets from a turret on a building pass through structures within 2 tiles of that turret.

### Bullet Types Summary

| Bullet        | Hit Type       | Fired by                        | Notes                                 |
|---------------|----------------|---------------------------------|---------------------------------------|
| Main bullet   | `HT_MAIN`      | Main gun, turrets               | 200 px/s + owner velocity             |
| Chain bullet  | `HT_CHAIN`     | Chain gun, men, weak turrets    | Range 165 px                          |
| Flame         | `HT_FLAME`     | Flamethrower, men               | 75 px/s, 120 px, not reflected        |
| Small rocket  | `HT_SROCK`     | Main guns, men, heavy mine      | Exhaust trail, owner velocity         |
| Mortar shell  | `HT_MORTAR`    | Mortar                          | 75 px/s, 120 px, over walls           |
| Plane bomb    | `HT_PLANEBOMB` | Planes (weapon 5)               | Falls `10 × h` px                     |

## Main Gun Per Vehicle

`weap.speed[0]` is the refire delay, `weap.power[0]` the damage (`src/include/types.h:107`, `src/data/tanks.c`).
Vehicles without main-gun ammo are left out.

| Vehicle        | Main gun   | Refire | Power |
|----------------|------------|--------|-------|
| STANDARD       | `HT_MAIN`  | 70     | 10    |
| DEVASTATOR     | `HT_MAIN`  | 50     | 10    |
| MOUSE          | `HT_MAIN`  | 80     | 10    |
| CROCODILE      | `HT_MAIN`  | 30     | 10    |
| THUNDERBOLT    | `HT_SROCK` | 40     | 10    |
| CHOPPER        | `HT_SROCK` | 50     | 10    |
| LOSERS TANK    | `HT_MAIN`  | 30     | 10    |
| LIZZARD        | `HT_MAIN`  | 70     | 5     |
| LAUNCHER       | `HT_SROCK` | 70     | 5     |
| SIDE-ARM       | `HT_MAIN`  | 70     | 5     |
| GREEN PLANE    | `HT_SROCK` | 50     | 10    |
| JET            | `HT_SROCK` | 40     | 10    |
| BATTLE CHOPPER | `HT_SROCK` | 50     | 10    |
| BUG LAUNCHER   | `HT_SROCK` | 50     | 5     |
| BULLDOZER      | `HT_MAIN`  | 110    | 5     |

Other weapons are the same for every vehicle: chain gun power 1, flamethrower 1, mines 7, rockets 10, men 1. The
refire delays are chain 10, flame 6 (ATOMIC TANK 15), mines 30, rockets 200, mortar 100, A-bomb 50, men 50, smoke 100,
invisibility 100, speed 50.

## References

- `src/tanks/fire.c` — `tank_fire()`, weapon switching, lock marker
- `src/world/bullet.c` — `create_bullet()`, bullet movement, wall reflection
- `src/objects/rocket.c` — guided rocket homing logic
- `src/objects/mortar.c` — mortar explosion expansion
- `src/objects/mine.c` — light and heavy mine behavior
- `src/objects/abomb.c` — A-bomb countdown and explosion
- `src/objects/man.c` — deployed men behavior
