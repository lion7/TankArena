# Tank Arena — Terrain and Effects

Tile size: 33 pixels. Maps use 3 terrain layers for rendering depth. Structure data tracks two destructible layers with
per-hit-type strength values.

## Terrain Types

| Constant                                      | Category   | Effect                                                                                           |
|-----------------------------------------------|------------|--------------------------------------------------------------------------------------------------|
| `WATER`                                       | Impassable | Kills tanks and men. Mines hidden underneath.                                                    |
| `RUNWAY`                                      | Surface    | Planes can land and take off. Off-runway landing = crash.                                        |
| `RAILS_H` / `RAILS_V` / `RAILS_CR`            | Surface    | Train tracks. Horizontal, vertical, and curve variants.                                          |
| `BRIDGE_H` / `BRIDGE_V`                       | Surface    | Bridge over water. Destruction reveals water below.                                              |
| `PIT_*` (9 variants)                          | Hazard     | Center, top, bottom, left, right, 4 corners. Big pits kill at h < -4, small pits at h < -14/-18. |
| `LAVA_PUT`                                    | Hazard     | Lava terrain. Creates `LAVA_EFFECT`. Damages tanks.                                              |
| `OIL`                                         | Surface    | Speed multiplier. Creates `OIL_SPLASH` effect.                                                   |
| `BUSHES`                                      | Surface    | Visual obstruction. Speed reduction.                                                             |
| `FUEL_DUMP`                                   | Surface    | Creates flame effect when hit.                                                                   |
| `ABOX`                                        | Surface    | Spawns A-bomb when destroyed.                                                                    |
| `FIELD_H` / `FIELD_V` / `FIELD_P` / `FIELD_N` | Surface    | Reflective wall tiles for bullet bouncing.                                                       |

## Pit Detection

`in_pit()` checks tile type and position within tile.

| Pit Size  | Detection          | Kill Height                           |
|-----------|--------------------|---------------------------------------|
| Big pit   | y2 > 8 or y2 < 24  | h < -4                                |
| Small pit | y2 > 14 or y2 < 18 | h < -14 (top/bottom), h < -18 (sides) |

9 variants each: center, top, bottom, left, right, 4 corners. Ramp detection for small/big ramps in 4 directions allows
escape.

**Ref:** `src/check/pit.c`

## Structure Strength

Two layers (layer 0 and 1). Each tile has `strength[layer][hit_type]` value.

- Strength initialized from picture data on map load.
- Hits reduce strength by hit type.
- At 0: tile is destroyed, linked structures chain-react recursively.
- Lock objects set strength to 1000 on prepare (near-indestructible).
- Rendering: structure strength affects picture frame selection (intact → damaged → destroyed).

Linked structures use direction flags (UP/DOWN/LEFT/RIGHT) for chain destruction.

**Ref:** `src/game/mapfiles.c`, `src/check/hitwall.c`, `src/edit/struct.c`

## Rendering Layers

Bottom layer: terrain + structures layer 0/1.
Top layer: layer 2 structures with shadows.

Window management supports 1 or 2 player views.

**Ref:** `src/world/under.c`

## Terrain Speed Multipliers

Terrain affects tank movement speed:

| Terrain       | Effect                   |
|---------------|--------------------------|
| Normal ground | 1.0x                     |
| Oil           | Increased speed          |
| Bushes        | Reduced speed            |
| Water         | Impassable               |
| Runway        | Normal (planes can land) |
| Rails         | Normal                   |

AI evaluates terrain speed when choosing paths.

## Special Terrain Behaviors

### Bridge Destruction

When bridge tiles are destroyed, water appears below. Tanks fall into water and die.

### Fuel Dump

Hit fuel dumps create flame effects and damage nearby tanks.

### ABOX

Destroyed ABOX tiles spawn an A-bomb object at the location.

### Lava

Continuous damage to tanks standing on lava. Creates `LAVA_EFFECT` particles.

## Backgrounds / Worlds

The `background` field in the map header selects the visual world theme. Affects palette and background rendering.

## Effect Types

| Effect ID        | Description                        |
|------------------|------------------------------------|
| `DUST`           | Dust cloud from movement           |
| `SMOKE`          | General smoke                      |
| `FIRE`           | Fire from explosions               |
| `EXPLOSION`      | Standard explosion                 |
| `LEXPLOSION`     | Large explosion (rockets, A-bombs) |
| `RUIN`           | Structure destruction debris       |
| `TELEPORT_SPARK` | Warp point activation              |
| `BLOOD`          | Man death effect                   |
| `SPLASH`         | Water impact                       |
| `LAVA_EFFECT`    | Lava particles                     |
| `OIL_SPLASH`     | Oil terrain effect                 |
| `EXHAUST`        | Rocket/vehicle exhaust trail       |
| `SMOKE_SCREEN`   | Weapon 9 smoke cloud               |
| `CHAIN`          | Chain link effect                  |
| `ROOK`           | Rocket-related effect              |
