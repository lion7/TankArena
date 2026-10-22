# Tank Arena — Terrain and Effects

Tile size: 33 pixels. Maps have 3 picture layers. Layer 0 is the ground; layers 1 and 2 hold structures, each with
per-hit-kind strength values. Each picture has a type, a speed factor, a strength per hit kind, an explosion effect and
link flags (`pictureinfotype`, `src/data/pictures.c`). Every world (background) has its own picture set.

## Which Terrain Counts

- **Terrain type** under a vehicle: the type of the layer 0 picture; when layer 0 is empty or `NOTHING`, the type of the
  layer 1 picture. Layer 2 never counts (`get_underground()`, `src/tanks/control.c:331-340`). Pits use the same rule
  (`src/check/pit.c:21-22`).
- **Speed**: for vehicles at `h ≤ 1` the thrust is multiplied by the speed of the layer 0 picture and, once the layer 1
  structure on the tile is destroyed, also by the speed of the layer 1 picture (`src/tanks/control.c:62-67`).
- **Blocking**: at `h < 2` a standing layer 1 structure blocks pixel by pixel; at `h` 2–7 a layer 2 structure on a
  standing layer 1 structure blocks; from `h = 8` nothing blocks (`src/check/overlap.c`).

## Terrain Types

| Constant                             | Layer | Effect                                                          |
|--------------------------------------|-------|-----------------------------------------------------------------|
| `WATER`                              | 0     | Speed 0.2, hides the hull, limits weapons, splashes             |
| `LAVA_PUT`                           | 0     | Speed 0.9, `HT_LAVA` power 2 every 10 ticks, hides the hull     |
| `ROAD`                               | 0     | Faster: 1.1–1.5 depending on the picture                        |
| `RUNWAY`                             | 0     | Speed 1.3; the only place where planes may touch down           |
| `HELISITE`                           | 0     | Speed 0.9                                                       |
| `OIL`                                | 0     | Speed 1.0 (0.8 in the TILE world); oil splashes                 |
| `RAILS_*`                            | 0     | Train tracks (see `objects.md`)                                 |
| `PIT_*`, `S_PIT_*`                   | 0     | Big and small pits; vehicles that sink to `h = -4` are destroyed |
| `S_RAMP_*`, `B_RAMP_*`               | 0     | Ramps that launch ground vehicles                               |
| `T_LEFT`, `T_RIGHT`, `T_UP`, `T_DOWN` | 0    | Conveyors: 60 px/s                                              |
| `ROTATE_L`, `ROTATE_R`               | 0     | Turntables: one full turn per second                            |
| `START`, `CHECK_P1_*`, `CHECK_P2_*`  | 0     | Race start line and checkpoints                                 |
| `BUSHES`, `BUSH_SIDE`                | 1     | Speed 1.0; destructible, not reached by linked damage           |
| `BRIDGE_H`, `BRIDGE_V`, `BRIDGE_CR`  | 1     | Destroyed: water appears under it                               |
| `SHELTER`                            | 1     | Protects tanks on the tile from A-bombs                         |
| `FUEL_DUMP`                          | 1     | Destroyed: throws burning debris                                |
| `ABOX`                               | 1     | Destroyed: A-bomb blast at once                                 |
| `FIELD_H`, `FIELD_V`, `FIELD_P`, `FIELD_N` | 1 | Reflect bullets (see `weapons.md`)                            |
| `LIGHT_SRC`                          | 2     | Light source on night maps                                      |

Speed values are picture properties and differ between worlds; ground without a type (`NOTHING`) ranges from 0.5 to
1.0 (`src/data/pictures.c`).

## Water

Water does not destroy vehicles (`src/data/pictures.c:267`):

- Speed factor 0.2.
- At `h ≤ 1` the hull is not drawn, only the turret (`src/tanks/tank.c:15-29`).
- Weapons are limited (see `weapons.md`, `src/tanks/fire.c:111-139`).
- Entering or leaving water makes splashes and `SND_SPLASH`; moving in water makes more splashes.
- Men die in water (`src/objects/man.c:37`). Mines, A-bombs and B52 wrecks are not drawn on water.

## Pits

`in_pit()` checks the tile type and the position `x2`, `y2` (0–32) of the vehicle's centre inside the tile
(`src/check/pit.c:17-108`). `PIT_C` is pit everywhere. Edge and corner tiles are pit only on the inner side of the rim:
for example `PIT_T` where `y2 > 8`, `PIT_B` where `y2 < 24`; corners combine both axes. Small pits use 14 and 18. These
numbers are rim offsets, not heights.

| Pit Size  | Types              | Pit area inside an edge tile        |
|-----------|--------------------|-------------------------------------|
| Big pit   | `PIT_*` (9 types)  | beyond 8 px from the outer edge     |
| Small pit | `S_PIT_*` (8 types) | beyond 14 px from the outer edge   |

- A vehicle over a pit area may sink to `h = -4` (gravity), otherwise its floor is `h = 1` (`src/tanks/control.c:94`).
- Every pit destroys a vehicle at `h = -4` with `HT_DESTROY` (`src/tanks/control.c:135-136`).
- Below `h = 1` a tank cannot move out of the pit area (`src/tanks/control.c:250-253`).
- Big pit pictures only exist in the TILE world (background 2).
- Ramps: a ground vehicle at `h = 1` that leaves a ramp tile in the ramp's direction gains vertical speed `|v|/10`
  (small ramp) or `|v|/5` (big ramp), which can carry it over a pit (`src/tanks/control.c:285-294`).

## Structure Strength

Map layers 1 and 2 have `strength[layer][kind]`, with three kinds: 0 normal, 1 crash (`HT_CRASH`), 2 flame
(`HT_FLAME`) (`hit[]`, `src/check/hitwall.c:10-27`).

- Strengths are set from the picture's `str[3]` on map load (`src/game/mapfiles.c:218-225`).
- A hit lowers the strength of its kind by the power. A kind with strength 0 is immune: such a hit does nothing
  (`src/check/hitwall.c:168-175`).
- Linked structures (direction flags `UP`, `DOWN`, `LEFT`, `RIGHT`) get the same new strength, recursively; `STOP`
  stops the chain and bushes are never reached.
- When a kind reaches 0 the structure is destroyed and drawn with its destroyed frame.
- Strength 1000 is indestructible; only `HT_REMOVE` (from a lock) breaks it. A picture with strength 1000 makes both
  structure layers of its tile indestructible (`src/game/mapfiles.c:227-235`). A lock that targets a structure sets
  its normal strength to 1000 (`src/objects/lock.c:191-202`).

When a layer 1 structure is destroyed (`src/check/hitwall.c:193-257`):

- the layer 2 structure above it goes too;
- the goal layer value `mapm.g` of the tile is applied to the goal counter;
- a bonus may appear (see `objects.md`);
- the men stored in the men layers come out and walk towards whoever destroyed it, or a random point within 100 px;
- bridges, fuel dumps and `ABOX` do their special action; other pictures with an `EXPLOSION` effect throw debris.

**Ref:** `src/game/mapfiles.c`, `src/check/hitwall.c`, `src/edit/struct.c`

## Rendering Layers

The bottom layer (layers 0 and 1) is cached per window and redrawn per tile when a structure changes
(`rewrite_block()`). The top layer (layer 2) is drawn with a shadow at `h = 4`, only while the layer 1 structure below
it stands (`src/world/under.c`).

## Special Terrain Behaviors

### Bridge Destruction

A destroyed `BRIDGE_H`, `BRIDGE_V` or `BRIDGE_CR` turns layer 0 into water and removes the bridge; a destroyed
`BRIDGE` clears layer 0 (`src/check/hitwall.c:230-240`). Vehicles on it end up in water, which slows them down.

### Fuel Dump

A destroyed fuel dump throws 10 `PT_FLAME` parts at 20–50 px/s for 50–70 px. Each part that touches an object hits it
with `HT_PARTS_TNK`, power 7, and each part that touches a structure hits it with `HT_PARTS_WALL`, power 10
(`src/world/parts.c:25-28`, `:93-113`). There is no separate area damage.

### ABOX

A destroyed `ABOX` starts an A-bomb blast at once (`src/check/hitwall.c:246-248`).

### Lava

Vehicles at `h < 2` on lava get `HT_LAVA`, power 2, every 10 ticks; `LAVA_EFFECT` particles appear when they enter
or leave (`src/tanks/control.c:352-362`).

### Conveyors and Turntables

Every object below `h = 2`, except start pads, on a conveyor tile moves 60 px/s in its direction; on a turntable its
position turns around the tile centre at one full turn per second (`ROTATE_L` counter-clockwise). The vehicle's heading
does not change. A tank is not moved if it would then touch another tank (`src/world/transp.c`).

## Backgrounds / Worlds

The `background` field in the map header selects the world: 0 DESERT, 1 GRASS, 2 TILE, 3 MARKET. It selects the
picture set (`pictures[background]`) and the music; moving over empty ground raises dust only in the DESERT world
(`src/tanks/control.c:377-381`). See `overview.md`.

## Effect Types

| Effect ID        | Description                                      |
|------------------|--------------------------------------------------|
| `DUST`           | Dust from driving on empty desert ground         |
| `SMOKE`          | Exhaust smoke of vehicles                        |
| `FIRE`           | Muzzle flash of the main gun                     |
| `EXPLOSION`      | Standard explosion                               |
| `LEXPLOSION`     | Large explosion (rockets, mines)                 |
| `RUIN`           | Dust of crumbling structures and crashes         |
| `TELEPORT_SPARK` | Warp used                                        |
| `BLOOD`          | Man dies                                         |
| `SPLASH`         | Water splash                                     |
| `LAVA_EFFECT`    | Lava particles                                   |
| `OIL_SPLASH`     | Oil splash                                       |
| `EXHAUST`        | Rocket and small-rocket trail                    |
| `SMOKE_SCREEN`   | Weapon 9 smoke cloud                             |
| `CHAIN`          | Chain gun bullet hitting a structure             |
| `ROOK`           | Smoke puff (train chimney, wrecks)               |
