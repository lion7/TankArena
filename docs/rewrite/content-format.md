# Content Format

How a map is stored and loaded in the rewrite: the native `.tamap` format of
[ADR 0022](adrs/0022-native-map-format.md). Where the files come from and every other asset are in
[assets.md](assets.md); the original `.MAP` format is in [`original/docs/game/map-format.md`](../../original/docs/game/map-format.md).

## One file per map

The shipped maps are `game-content/resources/maps/<name>.tamap`, listed in `index.txt` (69 entries,
one per map of the original release, including `select`, the tank selection arena). `<name>` is the
`.MAP` file name in lower case, with characters other than `a-z0-9_-` replaced by `_` (for example
`3_vs_3.tamap`). All 69 maps take 2.3 MB.

A `.tamap` file is UTF-8 JSON, written so that people can read and diff it: one map row per line,
short records on one line. Top-level fields, in file order:

| Field | Contents |
|---|---|
| `format` | Format version (1); omitted while it is the current one. Newer files are refused. |
| `header` | `name`, `size` [width, height] in tiles, `code`, `next`, `world`, `night`, `type`, `contents`, `publicCode`, `randomBonus`, `legacyVersion` |
| `texts` | `briefing`, `success`, `failure` |
| `legend` | Two-character code → `[picture index, "NAME"]` in the world's picture table |
| `floor`, `solid`, `top` | The three picture layers (legacy `mapm.m[0..2]`): one string per row, two characters per cell, `..` for none |
| `goals` | `{"at": [x, y], "share": n}` for every cell whose building has a goal-counter share (signed) |
| `bonuses` | `{"at": [x, y], "bonus": n}` for every cell with a bonus |
| `men` | `{"at": [x, y], "type": n, "count": n}`: paratroopers in a building |
| `objects` | The map objects, in file order |

Fields with their default value are left out (for example `night: false`).

### Legend and rows

Each picture a map uses gets a code when it first appears. The first character tells the kind of tile,
so the rows read like a sketch of the map:

| Letter | Tiles | Letter | Tiles |
|---|---|---|---|
| `g` | plain ground | `p` | pits |
| `h` | buildings (destructible) | `j` | ramps |
| `#` | indestructible structures | `c` | conveyors, turntables |
| `r` | roads, runways | `f` | force fields |
| `=` | rails | `x` | warps |
| `w` | water | `k` | race checkpoints, start |
| `b` | bridges | `s` | shelters |
| `l` | lava | `d` | fuel dumps |
| `o` | oil | `a` | A-bomb boxes |
| `t` | bushes, trees | `n` | barriers and walls |
| `i` | lamps | `e` | helipads |

The second character counts up (`1`–`9`, `a`–`z`, `A`–`Z`); when a kind runs out, the upper-case
letter and then other free characters are used. The legend keeps the picture index because the same
picture appears at several indices with different properties; the name is checked against the index
when the file is read.

### Objects

Each object is a record whose `type` is one of `tank` (map tanks and start pads), `turret`, `mine`,
`train` (engines and wagons), `lock`, `product`, `warp`, `flag`, `goal`, `b52`, `destroyer`,
`enforcer` and `zeppelin`. `at` is the object's top-left corner in pixels and `size` its box (left out
for mines, which have none). The other fields are the object's own, by name (for a tank: `tankType`,
`armor`, `ammo`, `power`, `controllerId`, `computer`, the `ai*` fields, `wayPoints`, …); fields with
default values are left out.

Counts over the 69 shipped maps: 1,252 mines, 718 tanks, 672 turrets, 244 train cars, 148 locks,
84 products, 70 warps, 42 flags, 36 goals, 6 B52s, 2 destroyers; enforcers and zeppelins exist as types
but no shipped map uses them. Rockets, A-bombs and mortars are runtime-only. Walls are not objects:
buildings are the solid and top layers.

### Example (BEGIN1, shortened)

```json
{
  "header": { "name": "BEGIN1", "size": [12, 12], "code": "BEGIN1", "next": "BEGIN2", "world": "CITY",
              "type": "SINGLE", "legacyVersion": 6 },
  "legend": { "r1": [159, "WEGRB"], "r2": [147, "WEGH"], "g1": [460, "PLATFORM"], "n1": [235, "WALLLT"] },
  "floor": [
    "........................",
    "..r1r2r2r2r2r2r2r2r2r3..",
    "..r4......g1g1......r4.."
  ],
  "objects": [
    { "type": "tank", "at": [184, 182], "size": [29, 29], "controllerId": 1, "computer": false,
      "tankType": 1, "ammo": [50, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], "maxArmor": 30 }
  ]
}
```

## Loading

1. `ShippedMaps` (game-sim, `nl.tankarena.sim.map`) reads `maps/index.txt` and each `.tamap` from
   the class path once (`MapFile.read`).
2. `Match.fromMap(document, mode, dual)` builds a match: `MapConversion.sidecarOf` gives the header,
   texts and layers, `MapConversion.objectsOf` gives fresh map objects (a match changes them, so every
   match restores its own).
3. The terrain grid and the `StructureGrid` are built from the layers; the client draws the terrain
   from the same layers, and destroyed tiles arrive as `TileState`s.

The client never reads `.MAP` files.

## Making the files

The `.tamap` files are the source of the shipped maps ([ADR 0003](adrs/0003-legacy-map-import.md)).
`.MAP` files are converted with:

```bash
./kotlin run -m tools-mapconv -- import <file.MAP> <out-dir>
./kotlin run -m tools-mapconv -- import-all "$PWD/original/MAPS" "$PWD/game-content/resources/maps"
```

`import-all` converts every `*.MAP` at the top level of `original/MAPS/` (not `original/MAPS/extra/`) and writes
`index.txt`. The importer chain: `LegacyMapParser` (game-content) → `LegacyObjectParser` and
`LegacyCanonicalConverter` (game-sim, `sim.legacy`) → `CanonicalSceneBuilder.buildActors` → map
objects → `MapConversion.toDocument` → `MapFile.write`.

`MapDocumentTest` requires every `.MAP` of the original release to import without notes to exactly
its committed `.tamap`, so a change to the importer shows up as a failing test until `import-all` is run
and the new files are committed. Until the in-game editor exists ([ADR 0021](adrs/0021-editor-in-the-game.md)),
maps are changed by changing the importer or the `.MAP` file, not by hand-editing `.tamap` files.

Export back to `.MAP` is not built yet (see [ADR 0022](adrs/0022-native-map-format.md)).

## Versioning

- `MAP_FORMAT_VERSION` (`nl.tankarena.sim.map`, 1) is bumped when the format changes incompatibly;
  readers refuse newer files.
- The object records follow the map objects' `State` classes (`nl.tankarena.sim`); a renamed or
  removed field needs a format bump or a migration.

## Known import gaps

Fields the importer parses but drops, or does not read at all, are listed under
[parity.md → Content and import](parity.md#content-and-import).
