# Tank Arena — AI System

Fuzzy logic controller for computer-controlled tanks. Evaluates multiple factors and applies weighted rules for
steering, speed, gun aiming, and firing decisions.

## Fuzzy Logic Basics

Membership values:

| Constant  | Value | Meaning            |
|-----------|-------|--------------------|
| `F_TRUE`  | 255   | Full membership    |
| `F_HALF`  | 127   | Partial membership |
| `F_FALSE` | 0     | No membership      |

Operators: `NOT`, `AND`, `OR` — standard fuzzy logic combinators.

## Target Selection

AI evaluates proximity to:

1. **Enemy tanks** — primary combat targets
2. **Goal objects** — mission objectives
3. **Structures** — cover and destruction targets
4. **Pits** — hazards to avoid
5. **Terrain** — speed-affecting surfaces
6. **Waypoints** — navigation targets

## Evaluation Functions

Each factor returns a fuzzy membership value (0–255) for positional relationships:

| Position | Description            |
|----------|------------------------|
| Close    | Target is nearby       |
| Left     | Target is to the left  |
| Front    | Target is ahead        |
| Right    | Target is to the right |

Evaluated per category: objects, buildings, pits, goals, terrain, waypoints.

## Steering Rules

Priority order:

1. **Danger avoidance** — steer away from pits, lava, water, enemy fire
2. **Goal pursuit** — steer toward goal objects when close
3. **Terrain optimization** — prefer faster terrain (oil, flat ground)
4. **Waypoint navigation** — follow predefined path points

## Speed Control

- AI never stops unless danger is detected.
- Reduces speed near hazards (pits, water edges).
- Increases speed on favorable terrain.
- Planes maintain attack wave patterns.

## Gun Aiming

- Aims at closest valid target (enemy tank, structure, goal).
- Considers bullet travel time and target movement.
- Adjusts for 16-way direction system.

## Firing Rules

- Fires when target is in range and line of sight is clear.
- Weapon selection based on enforcer objects and available ammo.
- Auto-fire delay prevents spam.

## Plane-Specific Behavior

- Executes attack waves.
- Maintains altitude.
- Drops bombs in patterns (B52: 10 bombs, 500-tick interval).
- Crashes if armor reaches 0.

## Chopper-Specific Behavior

- Uses lift for hovering.
- Can land on heli sites.
- More maneuverable than ground vehicles.

**Ref:** `src/tanks/computer.c`
