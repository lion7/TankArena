# Tank Arena — Game Overview

DOS-era tactical vehicular combat game, written in C with Allegro. Tile size: 33 pixels. Maps hold at most
200 × 200 = 40,000 cells (the limit is on the cell count, not on each side).

Menus, prompts and text pages run in 320×200 mode 13h (`src/game/main.c:149`, `src/game/single.c:111`). The game
itself runs in the mode chosen in SETUP: 320×200 VGA, 320×200 or 360×270 Mode X, or 640×480 VESA, which is the default
(`src/setup/setup.c:70-79`, `src/game/init.c:21`). Each player gets a map window of
`buf_x = (res_x - res_x*win_size/20)/players - 1` by `buf_y = res_y - res_y*win_size/20` pixels, clipped to the map
size and placed by `write_screen()` (`src/world/under.c:20-30`, `src/game/mainloop.c:43-55`).

## Game Modes

| Mode             | Constant | Description                                                                 |
|------------------|----------|-----------------------------------------------------------------------------|
| Single Player    | `SINGLE` | Mission campaign. Mission codes, briefing before, result text after.        |
| Dual Player      | `DUAL`   | Local 2 players, split screen. Map and tank selection, best-of-N rounds.    |
| Dual vs Computer | `DUALVC` | Mission campaign for two players (ids 1 and 2) against computer tanks.      |
| Map Editor       | `MAP`    | Level editor. Place structures, objects and terrain. Save/load `.MAP` files. |

The map type in the header restricts which modes may load a map (`check_suitable()`, `src/game/mapfiles.c:107-137`):

| Map type            | Value | Accepted by           |
|---------------------|-------|-----------------------|
| `MAPT_DONTCARE`     | 0     | every mode            |
| `MAPT_DUAL`         | 1     | `DUAL`                |
| `MAPT_SINGLE`       | 2     | `SINGLE`              |
| `MAPT_DUALVC`       | 3     | `DUALVC`              |
| `MAPT_SINGLEORDUAL` | 4     | `SINGLE` and `DUALVC` |

A map with products (see `objects.md`) is played as "supermarket mayhem" in `SINGLE` or `DUALVC`; it is not a separate
mode.

## Core Mechanics

### Health / Armor

- Each tank has `shield.armor` (current) and `tankinfotype.props.armor` (max).
- Damage reduces armor by the hit power. At 0 armor the tank is destroyed (`hit_tank()`, `src/tanks/tank.c:106`).
- A destroyed tank burns until it is on the ground and 100 ticks have passed (`contr.disable`), then becomes a wreck
  (`src/tanks/tank.c:136`, `src/tanks/control.c:138-146`). `contr.disable` only times this; it does not lock the
  controls. The wreck is removed after 3000 ticks while the round is still undecided (`src/tanks/control.c:14-38`).
- Start pads only exist in `DUAL`. There a start pad makes a new tank 300 ticks after the death (`motion.dead`) while the
  player has lives left (4 lives; unlimited in flag hunts) (`src/objects/player.c:67-93`).
- In `SINGLE` and `DUALVC` nothing respawns. Control passes to another player tank with the same id, or else to any
  other live player tank (`src/tanks/control.c:150-172`). When the last player tank dies the goal counter gets
  -100, which loses the mission (`src/tanks/tank.c:141-150`).
- Start shield: a new player tank cannot be hurt for 200 ticks, then blinks for 15 × 5 ticks (275 ticks in total;
  `shield.invun`, `shield.icount`, `src/tanks/tank.c:229-231`, `src/tanks/control.c:400-404`). Computer tanks get no
  start shield; supermarket maps switch it off (`src/tanks/tank.c:293`).
- Shield key (`contr.shield`): while held and `shield.power` is not 0, every hit except rockets and `HT_DESTROY` is
  blocked. Power starts at 3000, drains 60 per tick while held and recharges 10 per tick up to 6000
  (`src/tanks/tank.c:118`, `src/tanks/control.c:406-413`).

### Fuel

- Each tank has `motion.fuel` and `tankinfotype.props.max_fuel`.
- Player tanks use 1 fuel per pixel moved; computer tanks use none (`src/tanks/control.c:228-229`).
- The flamethrower uses 40 fuel per shot and does not fire with 40 fuel or less (`src/tanks/fire.c:161-166`).
- The fuel bonus fills the tank to max.
- Without fuel a tank cannot accelerate; a plane or chopper above the ground is destroyed (`src/tanks/control.c:238-243`).

### Base Refill

Only in `DUAL`: a tank within 7 pixels of its own start pad has `und.in_base` set (`src/objects/player.c:96-102`). If
`weap.reload` is also set, ammo is raised to the starting amounts and fuel and armor are restored to max
(`src/tanks/control.c:442-462`). `weap.reload` is set on every tank of the other side when a tank dies
(`src/tanks/tank.c:141-144`). If the building on the start pad tile is destroyed, the player is down to the last life
and gets no refill.

### Direction System

16-way compass (0–15). Stored as `a16` values. The hull direction is `motion.dir`, the turret direction
`motion.ldir`; only `MAIN_TURRET` vehicles aim the turret independently (see `vehicles.md`).

### Height System

Objects have a height `h` from -4 to 24. The object list is kept sorted by height and drawn in that order. Tanks drive at
`h = 1`; a tank that sinks to `h = -4` in a pit is destroyed (`src/tanks/control.c:135`).

### Timer System

`tinit(t)` returns the timer value `t` ticks from now; `tdone(x)` is true once that time has come. Used for delays,
cooldowns and effect durations.

### Goal Counter

`game.gc_good` and `game.gc_bad` count from 0 to 100 (`modify_goal_counter()`, `src/objects/objects.c:222-240`):

- Every removed object applies its own `o->gc` (`src/objects/objects.c:129`).
- A destroyed building applies the signed goal layer `mapm.g` of its tile, -100 to 100; negative values mark the
  player's own buildings (`src/check/hitwall.c:221`).
- Positive values add to `gc_good`, negative values add their size to `gc_bad`.
- `gc_good` reaching 100 wins (`game.winner = 1`), `gc_bad` reaching 100 loses (`game.winner = 2`); the round ends
  300 ticks later.
- The counter is ignored in `DUAL` and once a winner is known (`src/objects/objects.c:224`).
- The command-line switch `-NOFAIL` (registered version only) suppresses the `gc_bad` side
  (`src/objects/objects.c:229`, `src/game/main.c:84`).

### Structure Strength

Map layers 1 and 2 hold the structures; each has `strength[layer][kind]` per tile, with three hit kinds (normal, crash,
flame). Linked structures share damage. A strength of 1000 makes a structure indestructible except by a lock (see
`terrain.md` and `objects.md`).

## Win/Loss Conditions

- **Goal counter** (`SINGLE`, `DUALVC`): `gc_good` or `gc_bad` reaching 100.
- **Last player tank lost** (`SINGLE`, `DUALVC`): the goal counter gets -100.
- **Supermarket mayhem**: the cash runs out or the 180 s time limit expires (-100 each).
- **Lives** (`DUAL`): a player who loses the last life loses the round (`src/objects/player.c:88-93`).
- **Flag hunt** (`DUAL`): a tank that carries the other player's flag into its own base wins the round
  (`src/objects/flag.c:29-33`).
- **Race** (`DUAL`): the first player to finish all laps wins the round (`src/objects/player.c:44-57`).

## Backgrounds / Worlds

| ID | World | Picture set | Music             |
|----|-------|-------------|-------------------|
| 0  | DESERT | `pc0`      | `DATA/TUNE.MID`   |
| 1  | GRASS  | `pc1`      | `DATA/TUNE.MID`   |
| 2  | TILE   | `pc2`      | `DATA/TUNE.MID`   |
| 3  | MARKET | `pc3`      | `DATA/BELVED.MID` |

Names from the editor menu (`src/edit/menus.c:43-46`); music from `src/game/mapfiles.c:337`. Night is not a world but a
separate header flag (`map.night`) that works with any background. Night maps use the night palette, draw light sources
and add the Light weapon (index 12).

## Hit Types (18 total)

| Constant        | Description                                        |
|-----------------|----------------------------------------------------|
| `HT_MAIN`       | Main cannon bullet                                 |
| `HT_CHAIN`      | Chain gun bullet                                   |
| `HT_FLAME`      | Flamethrower                                       |
| `HT_ROCKET`     | Guided rocket                                      |
| `HT_ABOMB`      | A-bomb explosion                                   |
| `HT_CRASH`      | Vehicle ramming a wall                             |
| `HT_MINE`       | Mine explosion                                     |
| `HT_BOMB`       | B52 bomb                                           |
| `HT_DESTROY`    | Instant destruction (destroyer, crashes, pits)     |
| `HT_TANK_CRASH` | Tank ramming a tank                                |
| `HT_SROCK`      | Small rocket (some main guns, men, heavy mine)     |
| `HT_PARTS_TNK`  | Debris hitting an object                           |
| `HT_PARTS_WALL` | Debris hitting a wall                              |
| `HT_TRAIN`      | Train running into a wall                          |
| `HT_MORTAR`     | Mortar explosion                                   |
| `HT_REMOVE`     | Lock removing a structure                          |
| `HT_PLANEBOMB`  | Plane bomb                                         |
| `HT_LAVA`       | Lava damage                                        |

## Effect Types

DUST, SMOKE, RUIN, FIRE, EXPLOSION, CHAIN, FLAME, EXHAUST, BLOOD, PLUF, SPLASH, SFIRE, OIL_SPLASH, ROOK, TELEPORT_SPARK,
SMOKE_SCREEN, LEXPLOSION (large), MORTAR_FIRE, LAVA_EFFECT, PARTS, PARTS_SMOKE (`src/include/define.h:47-67`).

## References

- `src/include/types.h` — complete type system
- `src/include/define.h` — constants and enums
- `src/game/single.c` — single-player mode
- `src/game/dual.c` — dual-player mode
- `src/game/dualvc.c` — dual vs computer mode
