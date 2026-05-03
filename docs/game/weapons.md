# Tank Arena — Weapons (12 + 1 conditional)

Source: `src/tanks/fire.c`, `src/world/bullet.c`, `src/objects/rocket.c`, `src/objects/mortar.c`, `src/objects/mine.c`,
`src/objects/abomb.c`

## Weapon Index Table

| Index | Name                | Bullet Type | Hit Type    | Range          | Fuel Cost     | Description                                            |
|-------|---------------------|-------------|-------------|----------------|---------------|--------------------------------------------------------|
| 0     | Main Cannon         | `HT_MAIN`   | `HT_MAIN`   | 250/500 px     | Per shot      | Primary projectile. Reflects off FIELD walls.          |
| 1     | Chain Gun           | `HT_CHAIN`  | `HT_CHAIN`  | 5×33 px        | Per shot      | Rapid fire, multi-barrel. Short range.                 |
| 2     | Flamethrower        | `HT_FLAME`  | `HT_FLAME`  | 120 px         | 40/use        | Continuous stream. High fuel consumption.              |
| 3     | Mines               | `HT_MINE`   | `HT_MINE`   | —              | Per placement | Deployed as objects. Two types: light and heavy.       |
| 4     | Rocket              | `HT_ROCKET` | `HT_ROCKET` | 3300 px        | Per shot      | Guided, lock-on. Homes toward target.                  |
| 5     | Mortar              | `HT_MORTAR` | `HT_MORTAR` | Arc trajectory | Per shot      | Indirect fire. Explodes in expanding radius (5→60).    |
| 6     | A-Bomb              | `HT_ABOMB`  | `HT_ABOMB`  | Radius 210     | Per shot      | Nuclear. 30-tick countdown. Massive area damage.       |
| 7     | Men w/ Chain Gun    | `HT_SROCK`  | `HT_CHAIN`  | 75 bullets     | Per squad     | Deployed men walk toward target, firing chain guns.    |
| 8     | Men w/ Flamethrower | `HT_SROCK`  | `HT_FLAME`  | —              | Per squad     | Deployed men walk toward target, firing flamethrowers. |
| 9     | Smoke Screen        | —           | —           | —              | Per use       | Creates smoke cloud for concealment.                   |
| 10    | Invisibility        | —           | —           | —              | Per use       | 2000-tick invisibility. Visual flash on toggle.        |
| 11    | Extra Speed         | —           | —           | —              | Per use       | 1000-tick speed boost.                                 |
| 12    | Light               | —           | —           | —              | —             | Night mode only. Illuminates area around tank.         |

## Detailed Weapon Descriptions

### 0 — Main Cannon (`HT_MAIN`)

- **Range**: 250 px (layer 0), 500 px (layer 1)
- **Behavior**: Fires a bullet in the gun's current direction. Bullet reflects off FIELD_H, FIELD_V, FIELD_P, FIELD_N
  wall tiles.
- **Damage**: Per-tank power value (e.g., 70 for STANDARD, 110 for BULLDOZER).
- **Source**: `src/world/bullet.c` (`make_bullet()`), `src/tanks/fire.c`

### 1 — Chain Gun (`HT_CHAIN`)

- **Range**: 5 × 33 = 165 px
- **Behavior**: Rapid-fire, multi-barrel. Fires multiple bullets in quick succession.
- **Damage**: Low per shot (typically 10).
- **Source**: `src/tanks/fire.c`

### 2 — Flamethrower (`HT_FLAME`)

- **Range**: 120 px
- **Fuel cost**: 40 per use
- **Behavior**: Continuous flame stream in gun direction. Creates FIRE effects.
- **Damage**: Low per tick (typically 6), but continuous.
- **Source**: `src/tanks/fire.c`, `src/world/bullet.c`

### 3 — Mines (`HT_MINE`)

Deployed as stationary objects. Two types:

| Type       | Constant     | Behavior                                                   |
|------------|--------------|------------------------------------------------------------|
| Light Mine | `LIGHT_MINE` | Radius 30 explosion, `HT_MINE` hit type.                   |
| Heavy Mine | `HEAVY_MINE` | Fires 16 directional `HT_SROCK` bullets in all directions. |

- Hidden when underwater.
- Activation delay: 100 ticks after placement (immediate in prepared maps).
- **Source**: `src/objects/mine.c`

### 4 — Guided Rocket (`HT_ROCKET`)

- **Range**: 3300 px
- **Behavior**: Lock-on targeting. Rocket homes toward target, accelerating to speed 150. Creates exhaust trail effects.
- **On hit**: Large explosion (`LEXPLOSION`), "INCOMING MISSILE!" message.
- **Inherits**: Owner's velocity at launch (`HT_SROCK` bullet type inherits parent velocity).
- **Source**: `src/objects/rocket.c`, `src/tanks/fire.c`

### 5 — Mortar (`HT_MORTAR`)

- **Trajectory**: Arc (indirect fire). Bullet follows parabolic path.
- **Explosion**: Expanding radius from 5 to 60 px. Hits objects and walls with `HT_MORTAR`.
- **Effects**: Creates `PT_MORTAR` parts on explosion.
- **Source**: `src/objects/mortar.c`, `src/world/bullet.c`

### 6 — A-Bomb (`HT_ABOMB`)

- **Countdown**: 30 ticks with visual countdown (3 frames).
- **Explosion radius**: Expands from 0 to 210 px.
- **Power**: 4, hit type `HT_ABOMB`.
- **Tracking**: Nuke total tracked for palette effects (screen flash).
- **Special**: ATOMIC TANK (index 21) spawns an A-bomb at its death location.
- **Source**: `src/objects/abomb.c`, `src/tanks/tank.c`

### 7 — Men with Chain Guns (`HT_CHAIN` via `HT_SROCK`)

- **Behavior**: Deploys men objects that walk toward target position.
- **Ammunition**: 75 bullets max per squad.
- **Fire type**: Chain gun bullets (`HT_CHAIN`).
- **Removal**: Killed by any hit, water, or pits. Death creates BLOOD effect + scream sound.
- **Source**: `src/objects/man.c`

### 8 — Men with Flamethrowers (`HT_FLAME` via `HT_SROCK`)

- Same as men with chain guns, but fire flamethrower.
- **Source**: `src/objects/man.c`

### 9 — Smoke Screen

- Creates a smoke cloud effect (`SMOKE_SCREEN`) around the tank.
- Used for concealment.
- **Source**: `src/tanks/fire.c`

### 10 — Invisibility

- Duration: 2000 ticks.
- Visual flash effect on toggle (`invis.flash`, `invis.nflash`).
- Tank becomes invisible to enemies and AI targeting.
- **Source**: `src/tanks/fire.c`, `src/tanks/control.c`

### 11 — Extra Speed

- Duration: 1000 ticks.
- Increases tank movement speed.
- **Source**: `src/tanks/fire.c`

### 12 — Light (Night Mode Only)

- Activated automatically in night mode maps.
- Creates illumination around the tank.
- Uses `LIGHT_SRC` picture type for light source structures.
- **Source**: `src/tanks/fire.c`

## Bullet Behavior

### Wall Reflection

Main cannon bullets reflect off field wall tiles:

- `FIELD_H` — horizontal reflection
- `FIELD_V` — vertical reflection
- `FIELD_P` — positive diagonal reflection
- `FIELD_N` — negative diagonal reflection

### Bullet Types Summary

| Bullet Kind   | Hit Type       | Source          | Notes                      |
|---------------|----------------|-----------------|----------------------------|
| Main bullet   | `HT_MAIN`      | Tank gun        | Reflects off walls         |
| Chain bullet  | `HT_CHAIN`     | Tank gun        | Short range, multi-barrel  |
| Flame bullet  | `HT_FLAME`     | Tank gun        | Continuous, high fuel cost |
| Small rocket  | `HT_SROCK`     | Men, heavy mine | Inherits owner velocity    |
| Mortar bullet | `HT_MORTAR`    | Tank gun        | Arc trajectory             |
| Plane bomb    | `HT_PLANEBOMB` | B52, planes     | Dropped from altitude      |

## Weapon Power Per Tank

Each tank has per-weapon power values in `weap.power[num_weapon]`. Key variations:

| Tank        | Main Pwr | Chain Pwr | Flame Pwr |
|-------------|----------|-----------|-----------|
| STANDARD    | 70       | 10        | 6         |
| DEVASTATOR  | 50       | 10        | 6         |
| MOUSE       | 80       | 10        | 6         |
| CROCODILE   | 30       | 10        | 6         |
| BULLDOZER   | 110      | 10        | 6         |
| ATOMIC TANK | 50       | 10        | 15        |

## References

- `src/tanks/fire.c` — `fire()` function, weapon switching
- `src/world/bullet.c` — `make_bullet()`, bullet types, wall reflection
- `src/objects/rocket.c` — guided rocket homing logic
- `src/objects/mortar.c` — mortar explosion expansion
- `src/objects/mine.c` — light and heavy mine behavior
- `src/objects/abomb.c` — A-bomb countdown and explosion
- `src/objects/man.c` — deployed men behavior
