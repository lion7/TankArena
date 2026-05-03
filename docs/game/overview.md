# Tank Arena — Game Overview

DOS-era tactical vehicular combat game. Mode 13h graphics (320×200), Allegro, C codebase. Tile size: 33 pixels. Max map:
200×200 tiles.

## Game Modes

| Mode             | Constant | Description                                                                                                                                       |
|------------------|----------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| Single Player    | `SINGLE` | Mission-based campaign. Password/mission code system. Briefing text before each mission, victory/defeat text after. Next mission code on victory. |
| Dual Player      | `DUAL`   | Local 2-player. Map selection → tank selection → best-of-N rounds. Win tracking. Optional flag hunt. Victory FLI animation.                       |
| Dual vs Computer | `DUALVC` | Same as single-player mission system, but with two player IDs (1 and 2). AI controls enemy tanks.                                                 |
| Map Editor       | `MAP`    | In-game level editor. Place structures, objects, terrain. Save/load `.MAP` files.                                                                 |

Map types control mode compatibility (`MAPT_DONTCARE`, `MAPT_DUAL`, `MAPT_SINGLE`, `MAPT_DUALVC`, `MAPT_SINGLEORDUAL`).

## Core Mechanics

### Health / Armor

- Each tank has `shield.armor` (current) and `tankinfotype.props.armor` (max).
- Damage reduces armor by hit power. Death at 0 armor.
- On death: 300-tick disable period, then respawn at base.
- Invulnerability (`shield.invun`): temporary immunity, visual flash effect.
- Shield (`shield.power`): absorbs damage before armor, depletes over time (`shield.pcount`).

### Fuel

- Each tank has `motion.fuel` and `tankinfotype.props.max_fuel`.
- Movement consumes fuel proportional to distance traveled.
- Flamethrower consumes 40 fuel per use.
- Fuel pickups restore to max.
- Running out of fuel immobilizes the tank.

### Base Refill

Tanks inside their base zone (`und.in_base`) with `weap.reload` flag get weapons, fuel, and armor restored to max
values.

### Direction System

16-way compass (0–15). Stored as `a16` values. Main gun and turret rotate independently.

### Height System

Tanks have a height value (`h`). Objects are sorted by height for proper draw order in a linked list. Pits kill tanks
that reach `h=-4`.

### Timer System

`tinit(ms)` returns a timer value; `tdone()` checks if the elapsed time has passed. Used for delays, cooldowns, and
effect durations.

### Goal Counter

`gc_good` and `gc_bad` track percentages (0–100). Reaching 100% triggers win/loss conditions. Goal objects modify these
counters when completed.

### Structure Strength

Two layers (layer 0 and 1). Each tile has `strength[layer][hit_type]` values. Linked structures chain-react on
destruction. Strength value of 1000 marks a structure as prepared (invulnerable until triggered by lock objects).

## Win/Loss Conditions

- **Goal counter**: `gc_good` or `gc_bad` reaching 100%.
- **Flag capture**: Enemy carries your flag into your base zone.
- **All tanks destroyed**: If all tanks on a side are dead and cannot respawn.

## Backgrounds / Worlds

| ID | Name      | Music             |
|----|-----------|-------------------|
| 0  | Temperate | `DATA/TUNE.MID`   |
| 1  | Desert    | `DATA/TUNE.MID`   |
| 2  | City      | `DATA/TUNE.MID`   |
| 3  | Night     | `DATA/BELVED.MID` |

Night mode enables light sources (`LIGHT_SRC` picture type) and the Light weapon (index 12).

## Hit Types (18 total)

| Constant        | Description                    |
|-----------------|--------------------------------|
| `HT_MAIN`       | Main cannon bullet             |
| `HT_CHAIN`      | Chain gun bullet               |
| `HT_FLAME`      | Flamethrower                   |
| `HT_ROCKET`     | Guided rocket                  |
| `HT_ABOMB`      | A-bomb explosion               |
| `HT_CRASH`      | Vehicle crash                  |
| `HT_MINE`       | Mine explosion                 |
| `HT_BOMB`       | B52 bomb                       |
| `HT_DESTROY`    | Destroyer object               |
| `HT_TANK_CRASH` | Tank-to-tank crash             |
| `HT_SROCK`      | Small rocket (men, heavy mine) |
| `HT_PARTS_TNK`  | Tank parts debris              |
| `HT_PARTS_WALL` | Wall debris                    |
| `HT_TRAIN`      | Train collision                |
| `HT_MORTAR`     | Mortar explosion               |
| `HT_REMOVE`     | Instant removal                |
| `HT_PLANEBOMB`  | Plane bomb                     |
| `HT_LAVA`       | Lava damage                    |

## Effect Types

DUST, SMOKE, FIRE, EXPLOSION, LEXPLOSION (large), RUIN, TELEPORT_SPARK, BLOOD, SPLASH, LAVA_EFFECT, OIL_SPLASH, EXHAUST,
SMOKE_SCREEN, CHAIN, ROOK.

## References

- `src/include/types.h` — complete type system
- `src/include/define.h` — constants and enums
- `src/game/single.c` — single-player mode
- `src/game/dual.c` — dual-player mode
- `src/game/dualvc.c` — dual vs computer mode
