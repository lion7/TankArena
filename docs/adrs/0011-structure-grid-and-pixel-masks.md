# ADR 0011: Structures as a Tile Grid with Pixel Masks

- **Status:** Accepted (2026-10-06, commit `f73a9aa`).
- **Supersedes:** "each solid tile is a `Wall`" with Kubriko box collisions and
  `TerrainSlideManager` sliding (2026-04).

## Context

The original keeps buildings in the map's tile layers, not as objects. Per tile and layer it stores
three strengths (against normal hits, crashes and flames; 1000 means indestructible), links
multi-tile structures so they are damaged as one, swaps in ruin pictures, turns destroyed bridges into
water, sets fuel dumps alight and applies the signed goal layer (`original/src/check/hitwall.c`,
`original/src/game/mapfiles.c`). Vehicles collide with the opaque pixels of standing pictures
(`original/src/check/overlap.c`): a move is refused when the overlap grows, turning is always allowed, and the
vehicle's height decides which layers count.

The 2026-04 design made one Kubriko wall actor per solid tile (55,327 actors over the old map set),
collided by boxes and slid tanks along them. Buildings could not be destroyed, corners felt wrong, and
the actor count was the main performance cost.

## Decision

- **`StructureGrid`** (`game-sim/.../sim/StructureGrid.kt`) holds the solid and top layers with
  their strengths, links, ruins, goal shares and changed tiles, initialised from the sidecar's tile
  layers exactly as `load_map()` does.
- **Pixel masks** of every picture are generated at build time (`tools-mapconv extract-masks` →
  `game-content/resources/masks/pictures.mask`, read by `PictureMasks`) and used for vehicle and shot
  collisions.
- Shots test walls along their path first and only then vehicles (`line_wall_hit_point`,
  `line_obj_hit_point`); vehicles collide with each other by circle and exchange momentum by mass and
  crash factor.
- Destroyed or changed tiles are replicated to the client as `TileState`s in the `WorldSnapshot`.

## Consequences

- Destructible buildings, ruins, bridges, fuel dumps, shelters and locks that protect structures all
  work as in the original.
- No wall actors are simulated. The scene files still carry `tankArenaWall` entries for the editor
  only ([ADR 0008](0008-kubriko-and-scene-json.md)); `Wall`, `WallState` and the
  client's wall rendering branch are dead at runtime and should go when the scene format changes.
- `TerrainSlideManager` lost its sliding role ([ADR 0007](0007-authoritative-headless-kubriko-server.md))
  and was removed with Kubriko in 2026-10 ([ADR 0019](0019-plain-kotlin-no-kubriko.md)).
