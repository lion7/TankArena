# Tank Arena — AI System

Computer tanks are driven by `control_computer_tank()` (`src/tanks/computer.c:750-838`), called from `control_tank()`
every game step. It resets the four drive controls, chooses a target, aims and fires, and then uses fuzzy logic for
steering and throttle. Computer tanks use no ammo or fuel (`src/tanks/fire.c:143`, `src/tanks/control.c:228`).

## Settings Per Tank

Defaults from `make_tank()` (`src/tanks/tank.c:197-218`); the map editor can change them per tank.

| Field                     | Default                 | Meaning                                          |
|---------------------------|-------------------------|--------------------------------------------------|
| `computer.activator`      | 0                       | Side that wakes it up: 0 good tanks, 1 evil tanks |
| `computer.activation_rad` | 250                     | Wake-up radius (px)                              |
| `computer.stay_active`    | TRUE for planes         | Never go back to sleep                           |
| `computer.attack_tanks`   | 100 (cars 0)            | Preference for enemy tanks (0 = never)           |
| `computer.attack_goals`   | 60                      | Preference for goal objects and buildings        |
| `computer.attack_at`      | 150 (cars 0)            | Firing range (px)                                |
| `computer.fire_delay`     | refire of initial weapon | Ticks between shots                             |
| `computer.wave_delay`     | `tankinfotype` value    | Attack-wave length for planes (ticks)            |
| `way.type`                | `WAY_INACTIVE`          | Waypoint mode                                    |

A tank is good (`contr.good`) when a player controls it or it is a `CAR`; all other computer tanks are evil.

## Fuzzy Logic Basics

Membership values (`src/tanks/computer.c:14-18`):

| Constant     | Value | Meaning            |
|--------------|-------|--------------------|
| `F_TRUE`     | 255   | Full membership    |
| `F_THREEQ`   | 191   | Three quarters     |
| `F_TWOTHIRD` | 170   | Two thirds         |
| `F_HALF`     | 127   | Half               |
| `F_FALSE`    | 0     | No membership      |

Operators: `NOT(x) = 255 - x`, `AND = min`, `OR = max`, `DEG_OF_SUP(p, v) = p × v / 100`. `MODIFY()` adds a value and
clamps to 0–255.

## Each Step

1. **Waypoints**: when the current waypoint is within 30 px, go to the next one (see below).
2. **Target**: every 100 + rnd(100) ticks choose a new target; in between, follow the current target object.
3. **Activation**: an inactive tank stops here; it keeps rolling but does not steer, aim or fire.
4. **Aim and fire** (non-fuzzy).
5. Only when the hull may turn again (`motion.ndir`): compute the fuzzy inputs and apply the steering and throttle
   rules.

## Activation

Each time a new target is chosen, a tank without `stay_active` first becomes inactive. It becomes active again when a
live tank of the activator side is within `activation_rad` (`src/tanks/computer.c:114-131`). Tanks therefore fall
asleep when no such tank is near.

## Target Selection

`determine_new_target()` (`src/tanks/computer.c:106-177`) scores candidates by distance × (150 - preference) / 50 and
takes the lowest score:

- **Enemy tanks** (if `attack_tanks > 0`): live tanks of the other side, scored with `attack_tanks`. Invisibility is
  not checked.
- **Goal objects** (if `attack_goals > 0`): any object whose `gc` is positive (good tank) or negative (evil tank),
  scored with `attack_goals`. A goal with `who = 3` only counts for the tank it targets.
- **Goal buildings** (if `attack_goals > 0`): standing layer 1 structures within 15 tiles whose goal layer value has
  the right sign, scored with `attack_goals`. The target is then a map position, not an object.

## Waypoints

Each tank has 10 waypoint slots (`way.point[10]`, `x = -1` = unused) and a mode: `WAY_INACTIVE`, `WAY_ACTIVE` (visit
the points once, then inactive) or `WAY_CIRCULAR` (start again at the first point). The next point is chosen once the
current one is within 30 px (`src/tanks/computer.c:181-196`). At load the first used slot becomes the current one
(`src/tanks/tank.c:295-297`).

## Aiming and Firing

- **Aim** (`rule_aim_gun()`, `src/tanks/computer.c:544-556`): turns the turret one step towards the target's current
  position. There is no lead for target movement or bullet travel time. Only `MAIN_TURRET` vehicles can aim.
- **Fire** (`rule_fire()`, `src/tanks/computer.c:560-573`): fires when the gun direction equals the direction to the
  target and the target is within `attack_at`, or when the target is within 30 px in any direction (if
  `attack_at` is above 30). There is no line-of-sight check. The gun direction is the turret for `MAIN_TURRET` vehicles (except
  with chain gun or flamethrower), otherwise the hull.
- The weapon is the selected one; only an enforcer changes it (`src/objects/enforce.c`). Rockets and men go straight
  to the target, without a lock marker.
- Shots are `computer.fire_delay` ticks apart.

## Fuzzy Inputs

Each input is a `fuzzy_position` with `close`, `left`, `front` and `right` (0–255). Directions come from the angle
between the hull and the object: `front` within ±20°, `left`/`right` up to a critical angle (90° for objects, 220° for
goal and waypoint). The look-ahead range `tnk_range` is the speed in px/s, at least 50.

| Input          | Source                                                                       |
|----------------|------------------------------------------------------------------------------|
| `object`       | Other objects with a radius at the same height, closer than `tnk_range`      |
| `building`     | Structures the tank would overlap, probed along 5 directions                 |
| `pit`          | Pits and lava ahead (ground vehicles only)                                   |
| `goal`         | The target; close from 100 px (full) to 1000 px (none)                       |
| `fast_terrain`, `slow_terrain` | Average terrain speed ahead, left and right, against the current tile |
| `wayp`         | The current waypoint; close from 100 px to 1500 px                           |

`danger` is `OR(object, building, pit)` per direction. After a crash (`computer.crashed`, set for 4 × turn delay
ticks) `danger.front` gets +170: a panic reaction (`src/tanks/computer.c:819-825`).

## Steering

`rule_control_steering()` (`src/tanks/computer.c:622-746`) blends all wishes; there is no priority order:

- The stronger of `wayp.close` and `goal.close` is followed fully, the other at 40%.
- Fast terrain is followed only when neither goal nor waypoint is wanted.
- `want_left = AND(NOT danger.left, OR(danger.front, goal.left × goal wish, wayp.left × waypoint wish, fast terrain
  left, slow terrain ahead but not left))`, and likewise for right.
- `steering_required = OR(danger.front, want_left, want_right)`.
- The last turn direction gets +170 for 50 ticks; then a random -32..+31 is added to each value.
- The tank turns (left if `want_left > want_right`, else right) when `steering_required > 170`, the attack wave is
  over, and a plane is above `h = 10`.

## Throttle

- Ground vehicles and choppers (`rule_control_speed()`): full throttle, released when faster than 20 px/s with
  `danger.front > 127`, or when `pit.front > 127`. They never brake or reverse.
- Planes: always full throttle (`rule_control_speed_plane()`).

## Attack Waves (Planes)

When the target is within 80 px, steering is blocked for `computer.wave_delay` ticks, so the plane flies over the
target and turns later (`rule_attack_wave()`, `src/tanks/computer.c:581-585`). Tanks that collide with another tank
while in a wave or not targeting it get the crash panic (`src/check/collide.c:56-59`).

## Not in the AI

The original AI has none of these: lead aiming, line-of-sight checks, weapon choice by ammo, speeding up, landing on
heli sites, or bombing patterns. B52s are separate objects with their own fixed behavior (see `objects.md`).

**Ref:** `src/tanks/computer.c`
