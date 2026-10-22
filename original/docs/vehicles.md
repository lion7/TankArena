# Tank Arena — Vehicles (23 total)

Source: `src/data/tanks.c`, `src/include/types.h` (`tankinfotype`)

In `DUAL` the players choose from vehicles 0–7, limited by the start pads in the map (`map_contents`); LOSERS TANK is
only offered to a player who is far enough behind (`src/game/seltank.c:103-138`). All other vehicles appear only when a
map places them.

## Type Legend

| Type    | Constant  | Behavior                                                                  |
|---------|-----------|---------------------------------------------------------------------------|
| Car     | `CAR`     | Ground vehicle without weapons; computer cars count as good      |
| Tank    | `TANK`    | Ground vehicle; drives only at `h = 1`, falls with gravity 100            |
| Chopper | `CHOPPER` | Flies at its fixed height `props.h` (7)                                   |
| Plane   | `PLANE`   | Starts at `h = 1`, always thrusts forward, climbs with lift, gravity 10   |

Planes gain height at `lift × forward speed` once faster than 5 px/s. A plane that is at `h ≤ 1` while moving off a
`RUNWAY` tile, or that bumps into a structure, is destroyed (`src/tanks/control.c:114-122`, `:263-264`). Planes and
choppers above the ground are destroyed when their fuel runs out (`src/tanks/control.c:238-243`).

## Gun Modes

`turret.main_static` decides where the main gun, flamethrower and mortar point (`src/tanks/fire.c:173-174`):

| Mode   | Constant      | Description                                                                    |
|--------|---------------|--------------------------------------------------------------------------------|
| Static | `MAIN_STATIC` | Gun fixed to the hull direction                                                |
| Turret | `MAIN_TURRET` | Turret (`motion.ldir`) turned separately with the aim keys                     |
| Rotate | `MAIN_ROTATE` | Rotor that spins by itself; the guns fire along the hull                       |

A `MAIN_ROTATE` rotor turns one step every `turret.auto_rotation` ticks; 0 means every game step
(`src/tanks/control.c:421-424`). The chain gun always fires along the hull.

## Reading the Tables

- **Turn delay** (`motion.rotation`): ticks between 1/16 turns of the hull and of the turret
  (`src/tanks/control.c:299-322`).
- **Track frame** (`motion.change`): pixels driven per track-animation frame; 0 switches the frame every step, which
  animates propellers and rotors (`src/tanks/control.c:231-236`).
- **Collision factor** (`motion.crash`): bounce factor when hitting a structure, and the share of momentum passed on in a
  tank collision.
- **Ram power** (`general.crash`): ramming a structure hits it with `HT_CRASH` and power `|v|/25 + ram power`
  (0 = no damage). Above 4, ramming a tank also hits it with `HT_TANK_CRASH`, power 2 (`src/tanks/control.c:272-276`,
  `src/check/collide.c:51-54`).
- **Main gun fires**: bullet type of the main gun. `HT_SROCK` fires small rockets.
- **Ammo** (`weap.num`): starting amount and refill level. The chain gun uses 5 rounds per burst
  (`src/tanks/fire.c:155-156`). The flamethrower uses 40 fuel per shot and is available when `weap.flame` is set.
- **Refire** (`weap.speed`): ticks before a player tank can fire again (`src/tanks/fire.c:168`). Computer tanks use
  `computer.fire_delay` instead, which defaults to the refire of their initial weapon (`src/tanks/tank.c:200`).
- **Power** (`weap.power`): damage per hit, copied into the tank at creation (`src/tanks/tank.c:252`) and passed to
  the bullet, mine, rocket or man (`src/tanks/fire.c:184`). Mortar, A-bomb and the equipment have fixed or no damage.
- On night maps every vehicle also has weapon 12, the Light (see `weapons.md`).

Weapons with ammo 0 are not listed; they become usable after picking up a matching bonus.

## Vehicle Statistics

### 0 — STANDARD

Balanced all-rounder; the reference tank.

| Stat | Value |
|-------|-------|
| Type | `TANK` |
| Gun mode | `MAIN_TURRET` |
| Height / radius | 1 / 10 |
| Armor | 30 |
| Max fuel | 30000 |
| Mass / acceleration | 1000 / 100000 |
| Friction `ff_0` / `ff_v` | 2000 / 1000 |
| Friction `fs_0` / `fs_v` | 500000 / 0 |
| Collision factor (`crash`) | 0.3 |
| Turn delay (`rotation`) | 7 ticks |
| Track frame (`change`) | 1 px |
| Ram power (`general.crash`) | 1 |
| Exhaust smoke | yes |
| Main gun fires | `HT_MAIN` |
| Initial weapon | 0 (Main gun) |

| # | Weapon | Ammo | Refire | Power |
|---|--------------|------|--------|-------|
| 0 | Main gun | 30 | 70 | 10 |
| 1 | Chain gun | 1500 | 10 | 1 |
| 2 | Flamethrower | fuel | 6 | 1 |
| 3 | Mines | 7 | 30 | 7 |
| 4 | Rockets | 3 | 200 | 10 |
| 5 | Mortar | 3 | 100 | — |
| 6 | A-bomb | 2 | 50 | — |
| 7 | Men with CG | 5 | 50 | 1 |
| 8 | Men with FL | 5 | 50 | 1 |
| 9 | Smoke screen | 1 | 100 | — |
| 10 | Invisibility | 1 | 100 | — |
| 11 | Extra speed | 1 | 50 | — |

### 1 — DEVASTATOR

Fixed gun; faster main-gun refire (50) than STANDARD.

| Stat | Value |
|-------|-------|
| Type | `TANK` |
| Gun mode | `MAIN_STATIC` |
| Height / radius | 1 / 10 |
| Armor | 30 |
| Max fuel | 30000 |
| Mass / acceleration | 1000 / 100000 |
| Friction `ff_0` / `ff_v` | 2000 / 900 |
| Friction `fs_0` / `fs_v` | 300000 / 0 |
| Collision factor (`crash`) | 0.4 |
| Turn delay (`rotation`) | 7 ticks |
| Track frame (`change`) | 10 px |
| Ram power (`general.crash`) | 1 |
| Exhaust smoke | yes |
| Main gun fires | `HT_MAIN` |
| Initial weapon | 0 (Main gun) |

| # | Weapon | Ammo | Refire | Power |
|---|--------------|------|--------|-------|
| 0 | Main gun | 30 | 50 | 10 |
| 1 | Chain gun | 1500 | 10 | 1 |
| 2 | Flamethrower | fuel | 6 | 1 |
| 3 | Mines | 7 | 30 | 7 |
| 4 | Rockets | 3 | 200 | 10 |
| 5 | Mortar | 3 | 100 | — |
| 6 | A-bomb | 2 | 50 | — |
| 7 | Men with CG | 5 | 50 | 1 |
| 8 | Men with FL | 5 | 50 | 1 |
| 9 | Smoke screen | 1 | 100 | — |
| 10 | Invisibility | 1 | 100 | — |
| 11 | Extra speed | 1 | 50 | — |

### 2 — MOUSE

Small and light, low armor and fuel; slowest main-gun refire (80).

| Stat | Value |
|-------|-------|
| Type | `TANK` |
| Gun mode | `MAIN_STATIC` |
| Height / radius | 1 / 6 |
| Armor | 20 |
| Max fuel | 20000 |
| Mass / acceleration | 500 / 100000 |
| Friction `ff_0` / `ff_v` | 2000 / 600 |
| Friction `fs_0` / `fs_v` | 300000 / 0 |
| Collision factor (`crash`) | 0.6 |
| Turn delay (`rotation`) | 7 ticks |
| Track frame (`change`) | 1 px |
| Ram power (`general.crash`) | 1 |
| Exhaust smoke | yes |
| Main gun fires | `HT_MAIN` |
| Initial weapon | 0 (Main gun) |

| # | Weapon | Ammo | Refire | Power |
|---|--------------|------|--------|-------|
| 0 | Main gun | 20 | 80 | 10 |
| 1 | Chain gun | 1000 | 10 | 1 |
| 2 | Flamethrower | fuel | 6 | 1 |
| 3 | Mines | 7 | 30 | 7 |
| 4 | Rockets | 3 | 200 | 10 |
| 5 | Mortar | 3 | 100 | — |
| 6 | A-bomb | 2 | 50 | — |
| 7 | Men with CG | 5 | 50 | 1 |
| 8 | Men with FL | 5 | 50 | 1 |
| 9 | Smoke screen | 1 | 100 | — |
| 10 | Invisibility | 1 | 100 | — |
| 11 | Extra speed | 1 | 50 | — |

### 3 — CROCODILE

Large and heavy with a turret; fastest main-gun refire (30) and 60 shells.

| Stat | Value |
|-------|-------|
| Type | `TANK` |
| Gun mode | `MAIN_TURRET` |
| Height / radius | 1 / 13 |
| Armor | 40 |
| Max fuel | 30000 |
| Mass / acceleration | 2000 / 200000 |
| Friction `ff_0` / `ff_v` | 20000 / 2500 |
| Friction `fs_0` / `fs_v` | 1000000 / 0 |
| Collision factor (`crash`) | 0.2 |
| Turn delay (`rotation`) | 7 ticks |
| Track frame (`change`) | 1 px |
| Ram power (`general.crash`) | 4 |
| Exhaust smoke | yes |
| Main gun fires | `HT_MAIN` |
| Initial weapon | 0 (Main gun) |

| # | Weapon | Ammo | Refire | Power |
|---|--------------|------|--------|-------|
| 0 | Main gun | 60 | 30 | 10 |
| 1 | Chain gun | 3000 | 10 | 1 |
| 2 | Flamethrower | fuel | 6 | 1 |
| 3 | Mines | 7 | 30 | 7 |
| 4 | Rockets | 3 | 200 | 10 |
| 5 | Mortar | 3 | 100 | — |
| 6 | A-bomb | 2 | 50 | — |
| 7 | Men with CG | 5 | 50 | 1 |
| 8 | Men with FL | 5 | 50 | 1 |
| 9 | Smoke screen | 1 | 100 | — |
| 10 | Invisibility | 1 | 100 | — |
| 11 | Extra speed | 1 | 50 | — |

### 4 — THUNDERBOLT

Plane. The main gun fires small rockets; weapon 5 drops plane bombs instead of mortar shells. Lands only on runways.

| Stat | Value |
|-------|-------|
| Type | `PLANE` |
| Gun mode | `MAIN_STATIC` |
| Height / radius | 12 / 8 |
| Armor | 40 |
| Max fuel | 30000 |
| Mass / acceleration | 500 / 60000 |
| Friction `ff_0` / `ff_v` | 3000 / 400 |
| Friction `fs_0` / `fs_v` | 100000 / 0 |
| Collision factor (`crash`) | 0.9 |
| Lift | 0.15 |
| Turn delay (`rotation`) | 20 ticks |
| Track frame (`change`) | 0 px |
| Ram power (`general.crash`) | 0 |
| Exhaust smoke | yes |
| Main gun fires | `HT_SROCK` |
| Initial weapon | 0 (Main gun) |
| AI wave delay | 200 ticks |

| # | Weapon | Ammo | Refire | Power |
|---|--------------|------|--------|-------|
| 0 | Main gun | 100 | 40 | 10 |
| 1 | Chain gun | 3000 | 10 | 1 |
| 5 | Plane bombs | 7 | 100 | — |
| 9 | Smoke screen | 1 | 100 | — |
| 10 | Invisibility | 1 | 100 | — |
| 11 | Extra speed | 1 | 50 | — |

### 5 — CHOPPER

Helicopter. The rotor (`MAIN_ROTATE`) spins by itself; the guns fire along the hull. The main gun fires small rockets.

| Stat | Value |
|-------|-------|
| Type | `CHOPPER` |
| Gun mode | `MAIN_ROTATE` |
| Height / radius | 7 / 7 |
| Armor | 30 |
| Max fuel | 30000 |
| Mass / acceleration | 500 / 100000 |
| Friction `ff_0` / `ff_v` | 2000 / 600 |
| Friction `fs_0` / `fs_v` | 100000 / 0 |
| Collision factor (`crash`) | 0.9 |
| Turn delay (`rotation`) | 15 ticks |
| Track frame (`change`) | 0 px |
| Ram power (`general.crash`) | 0 |
| Exhaust smoke | yes |
| Main gun fires | `HT_SROCK` |
| Initial weapon | 0 (Main gun) |
| AI wave delay | 200 ticks |

| # | Weapon | Ammo | Refire | Power |
|---|--------------|------|--------|-------|
| 0 | Main gun | 100 | 50 | 10 |
| 1 | Chain gun | 1500 | 10 | 1 |
| 4 | Rockets | 3 | 200 | 10 |
| 9 | Smoke screen | 1 | 100 | — |
| 10 | Invisibility | 1 | 100 | — |
| 11 | Extra speed | 1 | 50 | — |

### 6 — RACE TANK

Created by race start pads in DUAL races (`src/objects/player.c:24`).

| Stat | Value |
|-------|-------|
| Type | `TANK` |
| Gun mode | `MAIN_STATIC` |
| Height / radius | 1 / 7 |
| Armor | 15 |
| Max fuel | 30000 |
| Mass / acceleration | 500 / 100000 |
| Friction `ff_0` / `ff_v` | 2000 / 450 |
| Friction `fs_0` / `fs_v` | 200000 / 0 |
| Collision factor (`crash`) | 0.6 |
| Turn delay (`rotation`) | 7 ticks |
| Track frame (`change`) | 1 px |
| Ram power (`general.crash`) | 1 |
| Exhaust smoke | yes |
| Main gun fires | `HT_MAIN` |
| Initial weapon | 1 (Chain gun) |

| # | Weapon | Ammo | Refire | Power |
|---|--------------|------|--------|-------|
| 1 | Chain gun | 1000 | 10 | 1 |
| 3 | Mines | 7 | 30 | 7 |

### 7 — LOSERS TANK

Only selectable in DUAL by a player who is far enough behind (`src/game/seltank.c:104,138`). Ram power 7 damages tanks it rams.

| Stat | Value |
|-------|-------|
| Type | `TANK` |
| Gun mode | `MAIN_STATIC` |
| Height / radius | 1 / 11 |
| Armor | 50 |
| Max fuel | 30000 |
| Mass / acceleration | 1000 / 100000 |
| Friction `ff_0` / `ff_v` | 2000 / 1000 |
| Friction `fs_0` / `fs_v` | 500000 / 0 |
| Collision factor (`crash`) | 0.2 |
| Turn delay (`rotation`) | 7 ticks |
| Track frame (`change`) | 1 px |
| Ram power (`general.crash`) | 7 |
| Exhaust smoke | yes |
| Main gun fires | `HT_MAIN` |
| Initial weapon | 0 (Main gun) |

| # | Weapon | Ammo | Refire | Power |
|---|--------------|------|--------|-------|
| 0 | Main gun | 50 | 30 | 10 |
| 1 | Chain gun | 3000 | 10 | 1 |
| 2 | Flamethrower | fuel | 6 | 1 |
| 3 | Mines | 7 | 30 | 7 |
| 4 | Rockets | 3 | 200 | 10 |
| 5 | Mortar | 3 | 100 | — |
| 6 | A-bomb | 2 | 50 | — |
| 7 | Men with CG | 5 | 50 | 1 |
| 8 | Men with FL | 5 | 50 | 1 |
| 9 | Smoke screen | 1 | 100 | — |
| 10 | Invisibility | 1 | 100 | — |
| 11 | Extra speed | 1 | 50 | — |

### 8 — LIZZARD

Heavy turret tank without smoke, cloak or speed-up.

| Stat | Value |
|-------|-------|
| Type | `TANK` |
| Gun mode | `MAIN_TURRET` |
| Height / radius | 1 / 13 |
| Armor | 30 |
| Max fuel | 30000 |
| Mass / acceleration | 2000 / 100000 |
| Friction `ff_0` / `ff_v` | 10000 / 3000 |
| Friction `fs_0` / `fs_v` | 1000000 / 0 |
| Collision factor (`crash`) | 0.3 |
| Turn delay (`rotation`) | 7 ticks |
| Track frame (`change`) | 1 px |
| Ram power (`general.crash`) | 4 |
| Exhaust smoke | yes |
| Main gun fires | `HT_MAIN` |
| Initial weapon | 0 (Main gun) |

| # | Weapon | Ammo | Refire | Power |
|---|--------------|------|--------|-------|
| 0 | Main gun | 60 | 70 | 5 |
| 1 | Chain gun | 3000 | 10 | 1 |
| 2 | Flamethrower | fuel | 6 | 1 |
| 3 | Mines | 7 | 30 | 7 |
| 4 | Rockets | 3 | 200 | 10 |
| 5 | Mortar | 3 | 100 | — |
| 6 | A-bomb | 2 | 50 | — |
| 7 | Men with CG | 5 | 50 | 1 |
| 8 | Men with FL | 5 | 50 | 1 |

### 9 — COMPUTER DRONE

Tiny and weak. No exhaust smoke.

| Stat | Value |
|-------|-------|
| Type | `TANK` |
| Gun mode | `MAIN_STATIC` |
| Height / radius | 1 / 4 |
| Armor | 10 |
| Max fuel | 30000 |
| Mass / acceleration | 500 / 63000 |
| Friction `ff_0` / `ff_v` | 1300 / 600 |
| Friction `fs_0` / `fs_v` | 300000 / 0 |
| Collision factor (`crash`) | 0.6 |
| Turn delay (`rotation`) | 7 ticks |
| Track frame (`change`) | 1 px |
| Ram power (`general.crash`) | 1 |
| Exhaust smoke | no |
| Main gun fires | `HT_MAIN` |
| Initial weapon | 1 (Chain gun) |

| # | Weapon | Ammo | Refire | Power |
|---|--------------|------|--------|-------|
| 1 | Chain gun | 1000 | 10 | 1 |
| 3 | Mines | 7 | 30 | 7 |

### 10 — LAUNCHER

Turret tank whose main gun fires small rockets.

| Stat | Value |
|-------|-------|
| Type | `TANK` |
| Gun mode | `MAIN_TURRET` |
| Height / radius | 1 / 7 |
| Armor | 20 |
| Max fuel | 30000 |
| Mass / acceleration | 1000 / 40000 |
| Friction `ff_0` / `ff_v` | 800 / 1000 |
| Friction `fs_0` / `fs_v` | 500000 / 0 |
| Collision factor (`crash`) | 0.3 |
| Turn delay (`rotation`) | 7 ticks |
| Track frame (`change`) | 1 px |
| Ram power (`general.crash`) | 1 |
| Exhaust smoke | yes |
| Main gun fires | `HT_SROCK` |
| Initial weapon | 0 (Main gun) |

| # | Weapon | Ammo | Refire | Power |
|---|--------------|------|--------|-------|
| 0 | Main gun | 100 | 70 | 5 |
| 1 | Chain gun | 1500 | 10 | 1 |
| 2 | Flamethrower | fuel | 6 | 1 |
| 4 | Rockets | 3 | 200 | 10 |

### 11 — SIDE-ARM

Heavy turret tank without mortar, smoke, cloak or speed-up.

| Stat | Value |
|-------|-------|
| Type | `TANK` |
| Gun mode | `MAIN_TURRET` |
| Height / radius | 1 / 10 |
| Armor | 30 |
| Max fuel | 30000 |
| Mass / acceleration | 2000 / 100000 |
| Friction `ff_0` / `ff_v` | 10000 / 2300 |
| Friction `fs_0` / `fs_v` | 1000000 / 0 |
| Collision factor (`crash`) | 0.3 |
| Turn delay (`rotation`) | 7 ticks |
| Track frame (`change`) | 1 px |
| Ram power (`general.crash`) | 1 |
| Exhaust smoke | yes |
| Main gun fires | `HT_MAIN` |
| Initial weapon | 0 (Main gun) |

| # | Weapon | Ammo | Refire | Power |
|---|--------------|------|--------|-------|
| 0 | Main gun | 30 | 70 | 5 |
| 1 | Chain gun | 1500 | 10 | 1 |
| 2 | Flamethrower | fuel | 6 | 1 |
| 3 | Mines | 7 | 30 | 7 |
| 4 | Rockets | 3 | 200 | 10 |
| 6 | A-bomb | 2 | 50 | — |
| 7 | Men with CG | 5 | 50 | 1 |
| 8 | Men with FL | 5 | 50 | 1 |

### 12 — GREEN PLANE

Plane; starts with the chain gun selected.

| Stat | Value |
|-------|-------|
| Type | `PLANE` |
| Gun mode | `MAIN_STATIC` |
| Height / radius | 12 / 8 |
| Armor | 20 |
| Max fuel | 30000 |
| Mass / acceleration | 500 / 50000 |
| Friction `ff_0` / `ff_v` | 2000 / 600 |
| Friction `fs_0` / `fs_v` | 100000 / 0 |
| Collision factor (`crash`) | 0.9 |
| Lift | 0.2 |
| Turn delay (`rotation`) | 20 ticks |
| Track frame (`change`) | 0 px |
| Ram power (`general.crash`) | 0 |
| Exhaust smoke | yes |
| Main gun fires | `HT_SROCK` |
| Initial weapon | 1 (Chain gun) |
| AI wave delay | 200 ticks |

| # | Weapon | Ammo | Refire | Power |
|---|--------------|------|--------|-------|
| 0 | Main gun | 100 | 50 | 10 |
| 1 | Chain gun | 1500 | 10 | 1 |

### 13 — JET

Plane; faster main-gun refire (40) than GREEN PLANE (50).

| Stat | Value |
|-------|-------|
| Type | `PLANE` |
| Gun mode | `MAIN_STATIC` |
| Height / radius | 12 / 8 |
| Armor | 20 |
| Max fuel | 30000 |
| Mass / acceleration | 500 / 60000 |
| Friction `ff_0` / `ff_v` | 3000 / 400 |
| Friction `fs_0` / `fs_v` | 100000 / 0 |
| Collision factor (`crash`) | 0.9 |
| Lift | 0.2 |
| Turn delay (`rotation`) | 20 ticks |
| Track frame (`change`) | 0 px |
| Ram power (`general.crash`) | 0 |
| Exhaust smoke | yes |
| Main gun fires | `HT_SROCK` |
| Initial weapon | 0 (Main gun) |
| AI wave delay | 200 ticks |

| # | Weapon | Ammo | Refire | Power |
|---|--------------|------|--------|-------|
| 0 | Main gun | 100 | 40 | 10 |
| 1 | Chain gun | 1500 | 10 | 1 |

### 14 — CAR

Civilian car, no weapons.

| Stat | Value |
|-------|-------|
| Type | `CAR` |
| Gun mode | `MAIN_STATIC` |
| Height / radius | 1 / 10 |
| Armor | 10 |
| Max fuel | 30000 |
| Mass / acceleration | 1000 / 30000 |
| Friction `ff_0` / `ff_v` | 700 / 1000 |
| Friction `fs_0` / `fs_v` | 500000 / 0 |
| Collision factor (`crash`) | 0.3 |
| Turn delay (`rotation`) | 7 ticks |
| Track frame (`change`) | 1 px |
| Ram power (`general.crash`) | 0 |
| Exhaust smoke | no |
| Main gun fires | `HT_MAIN` |
| Initial weapon | 1 (Chain gun) |

No weapons.

### 15 — BOB'S BUS

Civilian bus, no weapons.

| Stat | Value |
|-------|-------|
| Type | `CAR` |
| Gun mode | `MAIN_STATIC` |
| Height / radius | 1 / 10 |
| Armor | 10 |
| Max fuel | 30000 |
| Mass / acceleration | 1000 / 30000 |
| Friction `ff_0` / `ff_v` | 700 / 1000 |
| Friction `fs_0` / `fs_v` | 500000 / 0 |
| Collision factor (`crash`) | 0.3 |
| Turn delay (`rotation`) | 7 ticks |
| Track frame (`change`) | 1 px |
| Ram power (`general.crash`) | 0 |
| Exhaust smoke | no |
| Main gun fires | `HT_MAIN` |
| Initial weapon | 1 (Chain gun) |

No weapons.

### 16 — BATTLE CHOPPER

Helicopter with a rotor like CHOPPER; heavier, carries mortar shells instead of smoke, cloak and speed-up. No wave delay.

| Stat | Value |
|-------|-------|
| Type | `CHOPPER` |
| Gun mode | `MAIN_ROTATE` |
| Height / radius | 7 / 7 |
| Armor | 30 |
| Max fuel | 30000 |
| Mass / acceleration | 1000 / 30000 |
| Friction `ff_0` / `ff_v` | 1000 / 250 |
| Friction `fs_0` / `fs_v` | 100000 / 0 |
| Collision factor (`crash`) | 0.6 |
| Turn delay (`rotation`) | 12 ticks |
| Track frame (`change`) | 0 px |
| Ram power (`general.crash`) | 0 |
| Exhaust smoke | yes |
| Main gun fires | `HT_SROCK` |
| Initial weapon | 0 (Main gun) |

| # | Weapon | Ammo | Refire | Power |
|---|--------------|------|--------|-------|
| 0 | Main gun | 100 | 50 | 10 |
| 1 | Chain gun | 1500 | 10 | 1 |
| 4 | Rockets | 3 | 200 | 10 |
| 5 | Mortar | 3 | 100 | — |

### 17 — BUG LAUNCHER

Turret tank whose main gun fires small rockets; starts with rockets selected.

| Stat | Value |
|-------|-------|
| Type | `TANK` |
| Gun mode | `MAIN_TURRET` |
| Height / radius | 1 / 9 |
| Armor | 30 |
| Max fuel | 30000 |
| Mass / acceleration | 1000 / 30000 |
| Friction `ff_0` / `ff_v` | 700 / 800 |
| Friction `fs_0` / `fs_v` | 500000 / 0 |
| Collision factor (`crash`) | 0.3 |
| Turn delay (`rotation`) | 7 ticks |
| Track frame (`change`) | 1 px |
| Ram power (`general.crash`) | 1 |
| Exhaust smoke | yes |
| Main gun fires | `HT_SROCK` |
| Initial weapon | 4 (Rockets) |

| # | Weapon | Ammo | Refire | Power |
|---|--------------|------|--------|-------|
| 0 | Main gun | 100 | 50 | 5 |
| 1 | Chain gun | 1500 | 10 | 1 |
| 2 | Flamethrower | fuel | 6 | 1 |
| 4 | Rockets | 3 | 200 | 10 |
| 5 | Mortar | 3 | 100 | — |

### 18 — TRUCK

Civilian truck, no weapons; heavy, with ram power 3.

| Stat | Value |
|-------|-------|
| Type | `CAR` |
| Gun mode | `MAIN_STATIC` |
| Height / radius | 1 / 12 |
| Armor | 10 |
| Max fuel | 30000 |
| Mass / acceleration | 2000 / 30000 |
| Friction `ff_0` / `ff_v` | 1000 / 1000 |
| Friction `fs_0` / `fs_v` | 500000 / 0 |
| Collision factor (`crash`) | 0.3 |
| Turn delay (`rotation`) | 7 ticks |
| Track frame (`change`) | 1 px |
| Ram power (`general.crash`) | 3 |
| Exhaust smoke | yes |
| Main gun fires | `HT_MAIN` |
| Initial weapon | 1 (Chain gun) |

No weapons.

### 19 — FLAMER

Flamethrower only.

| Stat | Value |
|-------|-------|
| Type | `TANK` |
| Gun mode | `MAIN_STATIC` |
| Height / radius | 1 / 7 |
| Armor | 20 |
| Max fuel | 30000 |
| Mass / acceleration | 1000 / 30000 |
| Friction `ff_0` / `ff_v` | 700 / 1000 |
| Friction `fs_0` / `fs_v` | 500000 / 0 |
| Collision factor (`crash`) | 0.3 |
| Turn delay (`rotation`) | 7 ticks |
| Track frame (`change`) | 1 px |
| Ram power (`general.crash`) | 1 |
| Exhaust smoke | yes |
| Main gun fires | `HT_MAIN` |
| Initial weapon | 2 (Flamethrower) |

| # | Weapon | Ammo | Refire | Power |
|---|--------------|------|--------|-------|
| 2 | Flamethrower | fuel | 6 | 1 |

### 20 — BULLDOZER

High armor; ram power 9 damages tanks it rams.

| Stat | Value |
|-------|-------|
| Type | `TANK` |
| Gun mode | `MAIN_STATIC` |
| Height / radius | 1 / 10 |
| Armor | 40 |
| Max fuel | 30000 |
| Mass / acceleration | 1000 / 100000 |
| Friction `ff_0` / `ff_v` | 2000 / 1000 |
| Friction `fs_0` / `fs_v` | 500000 / 0 |
| Collision factor (`crash`) | 0.3 |
| Turn delay (`rotation`) | 7 ticks |
| Track frame (`change`) | 1 px |
| Ram power (`general.crash`) | 9 |
| Exhaust smoke | yes |
| Main gun fires | `HT_MAIN` |
| Initial weapon | 0 (Main gun) |

| # | Weapon | Ammo | Refire | Power |
|---|--------------|------|--------|-------|
| 0 | Main gun | 30 | 110 | 5 |
| 1 | Chain gun | 2000 | 10 | 1 |
| 2 | Flamethrower | fuel | 6 | 1 |
| 3 | Mines | 7 | 30 | 7 |
| 4 | Rockets | 3 | 200 | 10 |
| 5 | Mortar | 3 | 100 | — |
| 6 | A-bomb | 2 | 50 | — |
| 7 | Men with CG | 5 | 50 | 1 |
| 8 | Men with FL | 5 | 50 | 1 |
| 9 | Smoke screen | 1 | 100 | — |
| 10 | Invisibility | 1 | 100 | — |
| 11 | Extra speed | 1 | 50 | — |

### 21 — ATOMIC TANK

Flamethrower (slow refire) and 3 A-bombs. Sets off an A-bomb where it is destroyed (`src/tanks/tank.c:155`).

| Stat | Value |
|-------|-------|
| Type | `TANK` |
| Gun mode | `MAIN_STATIC` |
| Height / radius | 1 / 13 |
| Armor | 30 |
| Max fuel | 30000 |
| Mass / acceleration | 1000 / 20000 |
| Friction `ff_0` / `ff_v` | 700 / 1000 |
| Friction `fs_0` / `fs_v` | 500000 / 0 |
| Collision factor (`crash`) | 0.3 |
| Turn delay (`rotation`) | 12 ticks |
| Track frame (`change`) | 1 px |
| Ram power (`general.crash`) | 1 |
| Exhaust smoke | yes |
| Main gun fires | `HT_MAIN` |
| Initial weapon | 2 (Flamethrower) |

| # | Weapon | Ammo | Refire | Power |
|---|--------------|------|--------|-------|
| 2 | Flamethrower | fuel | 15 | 1 |
| 6 | A-bomb | 3 | 50 | — |

### 22 — SHOPPING CAR

A `TANK`-type vehicle with no weapons.

| Stat | Value |
|-------|-------|
| Type | `TANK` |
| Gun mode | `MAIN_TURRET` |
| Height / radius | 1 / 10 |
| Armor | 30 |
| Max fuel | 30000 |
| Mass / acceleration | 1000 / 100000 |
| Friction `ff_0` / `ff_v` | 2000 / 1000 |
| Friction `fs_0` / `fs_v` | 500000 / 0 |
| Collision factor (`crash`) | 0.3 |
| Turn delay (`rotation`) | 7 ticks |
| Track frame (`change`) | 1 px |
| Ram power (`general.crash`) | 1 |
| Exhaust smoke | no |
| Main gun fires | `HT_MAIN` |
| Initial weapon | 0 (Main gun) |

No weapons.

## Physics Summary

| Property          | Field                     | Use                                                          |
|-------------------|---------------------------|--------------------------------------------------------------|
| Mass              | `motion.mass`             | Divides thrust and friction; mass ratio in tank collisions   |
| Acceleration      | `motion.acc`              | Thrust: `dv = dt × acc / mass` per step                      |
| Forward friction  | `motion.ff_0`, `ff_v`     | Constant (on the ground or chopper) and speed-proportional   |
| Side friction     | `motion.fs_0`, `fs_v`     | Sideways; airborne tanks only get `ff_v` sideways            |
| Collision factor  | `motion.crash`            | Bounce factor and momentum share in collisions               |
| Lift              | `motion.lift`             | Plane climb rate per unit of forward speed                   |
| Turn delay        | `motion.rotation`         | Ticks per 1/16 turn of hull and turret                       |
| Track frame       | `motion.change`           | Pixels per track-animation frame                             |

Thrust is multiplied by 1.5 with Extra Speed and by the speed of the ground under the tank (see `terrain.md`)
(`src/tanks/control.c:55-70`). The down key reverses the thrust; planes always thrust forward.

## Collision

- Tank against tank (`tanks_collide()`, `src/tanks/control.c:179-200`): with `vx`, `vy` the relative velocity,
  `loss = crash` of the moving tank and `mass = m1 / m2`, the moving tank loses `v × (1 - loss) / mass` and the other
  gains `v × loss × mass`. Against a wreck that died more than 300 ticks ago the moving tank simply stops.
- Tank against structure: velocity is multiplied by `-crash` (`static_collide()`, `src/tanks/control.c:211-218`).
- Two tanks that overlap at the same height are both destroyed unless they still have their start shield
  (`check_obj_overlap()`, `src/check/collide.c:10-23`).
- Pits destroy tanks that sink to `h = -4`. Ramps launch tanks upwards (see `terrain.md`).

## References

- `src/data/tanks.c` — vehicle definitions
- `src/tanks/tank.c` — `make_tank()`, `hit_tank()`, atomic tank death behavior
- `src/tanks/control.c` — `control_tank()`, `new_velocity()`, `tanks_collide()`
- `src/tanks/fire.c` — weapon firing logic
