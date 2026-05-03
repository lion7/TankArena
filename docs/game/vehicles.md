# Tank Arena — Vehicles (23 total)

Source: `src/data/tanks.c`, `src/include/types.h` (`tankinfotype`)

## Type Legend

| Type    | Constant  | Behavior                                                   |
|---------|-----------|------------------------------------------------------------|
| Car     | `CAR`     | Ground vehicle, no turret rotation                         |
| Tank    | `TANK`    | Ground vehicle, turret (static, rotating, or follows body) |
| Chopper | `CHOPPER` | Flying vehicle, hover at fixed height, no gravity          |
| Plane   | `PLANE`   | Flying vehicle, has lift, crashes if landing off RUNWAY    |

## Turret Modes

| Mode   | Constant      | Description                     |
|--------|---------------|---------------------------------|
| Static | `MAIN_STATIC` | Gun fixed to body direction     |
| Turret | `MAIN_TURRET` | Gun rotates independently       |
| Rotate | `MAIN_ROTATE` | Entire vehicle rotates with gun |

## Vehicle Statistics

### 0 — STANDARD

| Stat            | Value                                                                                                                                                                         |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Type            | TANK                                                                                                                                                                          |
| Turret          | MAIN_TURRET                                                                                                                                                                   |
| Height          | 1                                                                                                                                                                             |
| Radius          | 10                                                                                                                                                                            |
| Armor           | 30                                                                                                                                                                            |
| Max Fuel        | 30000                                                                                                                                                                         |
| Mass            | 1000                                                                                                                                                                          |
| Acceleration    | 100000                                                                                                                                                                        |
| Default weapons | Main Cannon (30, pwr 70), Chain Gun (1500, pwr 10), Flamethrower (0), Mines (7), Rockets (3), Mortar (3), A-Bomb (2), Men-CG (5), Men-FL (5), Smoke (1), Cloak (1), Speed (1) |

Balanced all-rounder. Reference tank.

### 1 — DEVASTATOR

| Stat            | Value                                                                                                                                                                         |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Type            | TANK                                                                                                                                                                          |
| Turret          | MAIN_STATIC                                                                                                                                                                   |
| Height          | 1                                                                                                                                                                             |
| Radius          | 10                                                                                                                                                                            |
| Armor           | 30                                                                                                                                                                            |
| Max Fuel        | 30000                                                                                                                                                                         |
| Mass            | 1000                                                                                                                                                                          |
| Acceleration    | 100000                                                                                                                                                                        |
| Default weapons | Main Cannon (30, pwr 50), Chain Gun (1500, pwr 10), Flamethrower (0), Mines (7), Rockets (3), Mortar (3), A-Bomb (2), Men-CG (5), Men-FL (5), Smoke (1), Cloak (1), Speed (1) |

Fixed gun. Lower main cannon power (50 vs 70).

### 2 — MOUSE

| Stat            | Value                                                                                                                                                                         |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Type            | TANK                                                                                                                                                                          |
| Turret          | MAIN_STATIC                                                                                                                                                                   |
| Height          | 1                                                                                                                                                                             |
| Radius          | 6                                                                                                                                                                             |
| Armor           | 20                                                                                                                                                                            |
| Max Fuel        | 20000                                                                                                                                                                         |
| Mass            | 500                                                                                                                                                                           |
| Acceleration    | 100000                                                                                                                                                                        |
| Default weapons | Main Cannon (20, pwr 80), Chain Gun (1000, pwr 10), Flamethrower (0), Mines (7), Rockets (3), Mortar (3), A-Bomb (2), Men-CG (5), Men-FL (5), Smoke (1), Cloak (1), Speed (1) |

Small, fast, low armor. Higher main cannon power (80).

### 3 — CROCODILE

| Stat            | Value                                                                                                                                                                         |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Type            | TANK                                                                                                                                                                          |
| Turret          | MAIN_TURRET                                                                                                                                                                   |
| Height          | 1                                                                                                                                                                             |
| Radius          | 13                                                                                                                                                                            |
| Armor           | 40                                                                                                                                                                            |
| Max Fuel        | 30000                                                                                                                                                                         |
| Mass            | 2000                                                                                                                                                                          |
| Acceleration    | 200000                                                                                                                                                                        |
| Default weapons | Main Cannon (60, pwr 30), Chain Gun (3000, pwr 10), Flamethrower (0), Mines (7), Rockets (3), Mortar (3), A-Bomb (2), Men-CG (5), Men-FL (5), Smoke (1), Cloak (1), Speed (1) |

Large, heavy, high armor. Low main cannon power (30), high ammo count (60).

### 4 — THUNDERBOLT

| Stat            | Value                                                                                             |
|-----------------|---------------------------------------------------------------------------------------------------|
| Type            | PLANE                                                                                             |
| Turret          | MAIN_STATIC                                                                                       |
| Height          | 12                                                                                                |
| Radius          | 8                                                                                                 |
| Armor           | 40                                                                                                |
| Max Fuel        | 30000                                                                                             |
| Mass            | 500                                                                                               |
| Acceleration    | 60000                                                                                             |
| Lift            | 0.15                                                                                              |
| Wave Delay      | 200                                                                                               |
| Default weapons | Main Cannon (100, pwr 40), Chain Gun (3000, pwr 10), Rockets (0), Smoke (1), Cloak (1), Speed (1) |

Plane. Crashes if landing off RUNWAY. Attack wave delay: 200 ticks.

### 5 — CHOPPER

| Stat            | Value                                                                                             |
|-----------------|---------------------------------------------------------------------------------------------------|
| Type            | CHOPPER                                                                                           |
| Turret          | MAIN_ROTATE                                                                                       |
| Height          | 7                                                                                                 |
| Radius          | 7                                                                                                 |
| Armor           | 30                                                                                                |
| Max Fuel        | 30000                                                                                             |
| Mass            | 500                                                                                               |
| Acceleration    | 100000                                                                                            |
| Wave Delay      | 200                                                                                               |
| Default weapons | Main Cannon (100, pwr 50), Chain Gun (1500, pwr 10), Rockets (3), Smoke (1), Cloak (1), Speed (1) |

Helicopter. Rotates with gun. Hovers at height 7.

### 6 — RACE TANK

| Stat            | Value                                     |
|-----------------|-------------------------------------------|
| Type            | TANK                                      |
| Turret          | MAIN_STATIC                               |
| Height          | 1                                         |
| Radius          | 7                                         |
| Armor           | 15                                        |
| Max Fuel        | 30000                                     |
| Mass            | 500                                       |
| Acceleration    | 100000                                    |
| Default weapons | Main Cannon (0), Chain Gun (1000, pwr 10) |

Low armor, no weapons except chain gun. Used for race modes.

### 7 — LOSERS TANK

| Stat            | Value                                                                                                                                                       |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Type            | TANK                                                                                                                                                        |
| Turret          | MAIN_STATIC                                                                                                                                                 |
| Height          | 1                                                                                                                                                           |
| Radius          | 11                                                                                                                                                          |
| Armor           | 50                                                                                                                                                          |
| Max Fuel        | 30000                                                                                                                                                       |
| Mass            | 1000                                                                                                                                                        |
| Acceleration    | 100000                                                                                                                                                      |
| Default weapons | Main Cannon (50, pwr 30), Chain Gun (3000, pwr 10), Mines (7), Rockets (3), Mortar (3), A-Bomb (2), Men-CG (5), Men-FL (5), Smoke (1), Cloak (1), Speed (1) |

High armor (50), low main cannon power (30). Large radius (11).

### 8 — LIZZARD

| Stat            | Value                                                                                                                      |
|-----------------|----------------------------------------------------------------------------------------------------------------------------|
| Type            | TANK                                                                                                                       |
| Turret          | MAIN_TURRET                                                                                                                |
| Height          | 1                                                                                                                          |
| Radius          | 13                                                                                                                         |
| Armor           | 30                                                                                                                         |
| Max Fuel        | 30000                                                                                                                      |
| Mass            | 2000                                                                                                                       |
| Acceleration    | 100000                                                                                                                     |
| Default weapons | Main Cannon (60, pwr 70), Chain Gun (3000, pwr 10), Mines (7), Rockets (3), Mortar (3), A-Bomb (2), Men-CG (5), Men-FL (5) |

Heavy tank. No smoke/cloak/speed. High ammo counts.

### 9 — COMPUTER DRONE

| Stat            | Value                                     |
|-----------------|-------------------------------------------|
| Type            | TANK                                      |
| Turret          | MAIN_STATIC                               |
| Height          | 1                                         |
| Radius          | 4                                         |
| Armor           | 10                                        |
| Max Fuel        | 30000                                     |
| Mass            | 500                                       |
| Acceleration    | 63000                                     |
| Default weapons | Main Cannon (0), Chain Gun (1000, pwr 10) |

AI-only tank. Very small, weak armor. No player control.

### 10 — LAUNCHER

| Stat            | Value                                                            |
|-----------------|------------------------------------------------------------------|
| Type            | TANK                                                             |
| Turret          | MAIN_TURRET                                                      |
| Height          | 1                                                                |
| Radius          | 7                                                                |
| Armor           | 20                                                               |
| Max Fuel        | 30000                                                            |
| Mass            | 1000                                                             |
| Acceleration    | 40000                                                            |
| Default weapons | Main Cannon (100, pwr 70), Chain Gun (1500, pwr 10), Rockets (3) |

Rocket-focused tank. No mines/flame/mortar/A-bomb/men.

### 11 — SIDE-ARM

| Stat            | Value                                                                                                          |
|-----------------|----------------------------------------------------------------------------------------------------------------|
| Type            | TANK                                                                                                           |
| Turret          | MAIN_TURRET                                                                                                    |
| Height          | 1                                                                                                              |
| Radius          | 10                                                                                                             |
| Armor           | 30                                                                                                             |
| Max Fuel        | 30000                                                                                                          |
| Mass            | 2000                                                                                                           |
| Acceleration    | 100000                                                                                                         |
| Default weapons | Main Cannon (30, pwr 70), Chain Gun (1500, pwr 10), Mines (7), Rockets (3), A-Bomb (2), Men-CG (5), Men-FL (5) |

Heavy tank. No smoke/cloak/speed.

### 12 — GREEN PLANE

| Stat            | Value                                               |
|-----------------|-----------------------------------------------------|
| Type            | PLANE                                               |
| Turret          | MAIN_STATIC                                         |
| Height          | 12                                                  |
| Radius          | 8                                                   |
| Armor           | 20                                                  |
| Max Fuel        | 30000                                               |
| Mass            | 500                                                 |
| Acceleration    | 50000                                               |
| Lift            | 0.2                                                 |
| Wave Delay      | 200                                                 |
| Default weapons | Main Cannon (100, pwr 50), Chain Gun (1500, pwr 10) |

Plane. Higher lift (0.2). No special weapons.

### 13 — JET

| Stat            | Value                                               |
|-----------------|-----------------------------------------------------|
| Type            | PLANE                                               |
| Turret          | MAIN_STATIC                                         |
| Height          | 12                                                  |
| Radius          | 8                                                   |
| Armor           | 20                                                  |
| Max Fuel        | 30000                                               |
| Mass            | 500                                                 |
| Acceleration    | 60000                                               |
| Lift            | 0.2                                                 |
| Wave Delay      | 200                                                 |
| Default weapons | Main Cannon (100, pwr 40), Chain Gun (1500, pwr 10) |

Plane. Similar to Thunderbolt but lower main cannon power.

### 14 — CAR

| Stat            | Value       |
|-----------------|-------------|
| Type            | CAR         |
| Turret          | MAIN_STATIC |
| Height          | 1           |
| Radius          | 10          |
| Armor           | 10          |
| Max Fuel        | 30000       |
| Mass            | 1000        |
| Acceleration    | 30000       |
| Default weapons | None        |

Civilian car. No weapons. Two small sprites.

### 15 — BOB'S BUS

| Stat            | Value       |
|-----------------|-------------|
| Type            | CAR         |
| Turret          | MAIN_STATIC |
| Height          | 1           |
| Radius          | 10          |
| Armor           | 10          |
| Max Fuel        | 30000       |
| Mass            | 1000        |
| Acceleration    | 30000       |
| Default weapons | None        |

Civilian bus. No weapons.

### 16 — BATTLE CHOPPER

| Stat            | Value                                                                        |
|-----------------|------------------------------------------------------------------------------|
| Type            | CHOPPER                                                                      |
| Turret          | MAIN_ROTATE                                                                  |
| Height          | 7                                                                            |
| Radius          | 7                                                                            |
| Armor           | 30                                                                           |
| Max Fuel        | 30000                                                                        |
| Mass            | 1000                                                                         |
| Acceleration    | 30000                                                                        |
| Default weapons | Main Cannon (100, pwr 50), Chain Gun (1500, pwr 10), Rockets (3), Mortar (3) |

Armed helicopter. Heavier than standard chopper. No smoke/cloak/speed.

### 17 — BUG LAUNCHER

| Stat            | Value                                                                        |
|-----------------|------------------------------------------------------------------------------|
| Type            | TANK                                                                         |
| Turret          | MAIN_TURRET                                                                  |
| Height          | 1                                                                            |
| Radius          | 9                                                                            |
| Armor           | 30                                                                           |
| Max Fuel        | 30000                                                                        |
| Mass            | 1000                                                                         |
| Acceleration    | 30000                                                                        |
| Default weapons | Main Cannon (100, pwr 50), Chain Gun (1500, pwr 10), Rockets (3), Mortar (3) |

Rocket and mortar focused. No mines/flame/A-bomb/men.

### 18 — TRUCK

| Stat            | Value       |
|-----------------|-------------|
| Type            | CAR         |
| Turret          | MAIN_STATIC |
| Height          | 1           |
| Radius          | 12          |
| Armor           | 10          |
| Max Fuel        | 30000       |
| Mass            | 2000        |
| Acceleration    | 30000       |
| Default weapons | None        |

Civilian truck. Heavy (2000 mass), large radius (12). No weapons.

### 19 — FLAMER

| Stat            | Value                                                    |
|-----------------|----------------------------------------------------------|
| Type            | TANK                                                     |
| Turret          | MAIN_STATIC                                              |
| Height          | 1                                                        |
| Radius          | 7                                                        |
| Armor           | 20                                                       |
| Max Fuel        | 30000                                                    |
| Mass            | 1000                                                     |
| Acceleration    | 30000                                                    |
| Default weapons | Main Cannon (0), Chain Gun (0), Flamethrower (0 initial) |

Flamethrower-only tank. No other weapons.

### 20 — BULLDOZER

| Stat            | Value                                                                                                                                                        |
|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Type            | TANK                                                                                                                                                         |
| Turret          | MAIN_STATIC                                                                                                                                                  |
| Height          | 1                                                                                                                                                            |
| Radius          | 10                                                                                                                                                           |
| Armor           | 40                                                                                                                                                           |
| Max Fuel        | 30000                                                                                                                                                        |
| Mass            | 1000                                                                                                                                                         |
| Acceleration    | 100000                                                                                                                                                       |
| Default weapons | Main Cannon (30, pwr 110), Chain Gun (2000, pwr 10), Mines (7), Rockets (3), Mortar (3), A-Bomb (2), Men-CG (5), Men-FL (5), Smoke (1), Cloak (1), Speed (1) |

High main cannon power (110). High armor (40).

### 21 — ATOMIC TANK

| Stat            | Value                                                        |
|-----------------|--------------------------------------------------------------|
| Type            | TANK                                                         |
| Turret          | MAIN_STATIC                                                  |
| Height          | 1                                                            |
| Radius          | 13                                                           |
| Armor           | 30                                                           |
| Max Fuel        | 30000                                                        |
| Mass            | 1000                                                         |
| Acceleration    | 20000                                                        |
| Default weapons | Main Cannon (0), Chain Gun (0), Flamethrower (0), A-Bomb (3) |

**Special:** Spawns an A-bomb at its location when destroyed (see `src/tanks/tank.c`). Flamethrower and A-bomb only.
Slow acceleration.

### 22 — SHOPPING CAR

| Stat            | Value       |
|-----------------|-------------|
| Type            | TANK        |
| Turret          | MAIN_TURRET |
| Height          | 1           |
| Radius          | 10          |
| Armor           | 30          |
| Max Fuel        | 30000       |
| Mass            | 1000        |
| Acceleration    | 100000      |
| Default weapons | None        |

Editor-only tank. Same sprite layout as STANDARD. No weapons.

## Physics Summary

| Property                    | Description                     | Source                   |
|-----------------------------|---------------------------------|--------------------------|
| Mass                        | Affects collision push ratio    | `motion.mass`            |
| Acceleration                | Speed increase per tick         | `motion.acc`             |
| Friction (static)           | Resistance at low speed         | `motion.fs_0`            |
| Friction (velocity)         | Resistance scaling with speed   | `motion.fs_v`            |
| Friction (forward static)   | Forward resistance at low speed | `motion.ff_0`            |
| Friction (forward velocity) | Forward resistance scaling      | `motion.ff_v`            |
| Crash                       | Crash damage multiplier         | `motion.crash`           |
| Lift                        | Gravity reduction for planes    | `motion.lift` (0.15–0.9) |
| Rotation                    | Gun rotation speed              | `motion.rotation`        |
| Change                      | Direction change rate           | `motion.change`          |

## Collision

Tank-to-tank collision uses elastic collision with mass ratio (`tanks_collide()` in `src/tanks/control.c`). Larger mass
pushes smaller tanks harder.

Planes crash if they land off RUNWAY tiles, creating an explosion.

Pits kill tanks that reach height `h=-4`. Ramps allow tanks to change height.

## References

- `src/data/tanks.c` — vehicle definitions
- `src/tanks/tank.c` — `make_tank()`, `hit_tank()`, atomic tank death behavior
- `src/tanks/control.c` — `control_tank()`, `new_velocity()`, `tanks_collide()`
- `src/tanks/fire.c` — weapon firing logic
