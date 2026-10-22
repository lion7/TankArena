# Content Format

How a map is stored and loaded in the rewrite: the native `.tamap` format of
[ADR 0022](adrs/0022-native-map-format.md). Where the files come from and every other asset are in
[assets.md](assets.md); the original `.MAP` format is in [`original/docs/map-format.md`](../original/docs/map-format.md).

## One file per map

The shipped maps are `game-content/resources/maps/<name>.tamap`, listed in `index.txt` (69 entries,
one per map of the original release, including `select`, the tank selection arena). `<name>` is the
`.MAP` file name in lower case (`2FAST.MAP` becomes `2fast.tamap`); characters other than `a-z0-9_-`
would be replaced by `_`, which none of the 69 needs. All 69 maps take 2.7 MB. Maps saved in the editor are `.tamap` files too, in the
player's `maps/` folder ([ADR 0020](adrs/0020-user-data-as-json.md)); one with the name of a shipped
map replaces it.

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
| `legacy` | Imported maps only: the original bytes the rewrite does not model, for a byte-exact `.MAP` export (below) |

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
default values are left out. Start pads for players 3 and 4 (Extended) are tank records with
`padPlayer` 2 or 3 ([ADR 0027](adrs/0027-four-players-two-teams.md)).

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

The `.tamap` files are the source of the shipped maps ([ADR 0003](adrs/0003-legacy-map-import.md)),
changed with the in-game editor ([ADR 0021](adrs/0021-editor-in-the-game.md)). `.MAP` files are
converted with:

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
and the new files are committed. Running `import-all` overwrites edits made to shipped maps in the
editor, so after the first edit a shipped map should only be changed in the editor.

## Export to `.MAP`

```bash
./kotlin run -m tools-mapconv -- export <file.tamap> <file.MAP>
```

The editor does the same with Ctrl+E, into the player's `maps/export/` folder. `LegacyMapExport`
writes a version 6 `.MAP`, not locked: the header, the seven layers, the texts and every object as a
696-byte record that starts from the original editor's `make_*()` defaults with the map's values on
top. Start pads for players 3 and 4 are left out.

The `legacy` section of an imported map holds what the rewrite does not model, as byte runs
`[offset, "hex"]` for the header (`head`) and per object (`objects`, by index), and the texts as they
were in the file (`texts`). Export puts those bytes back, so every original map exports to exactly its
own file. The editor drops the runs over bytes an edit changes; new objects have none
([ADR 0022](adrs/0022-native-map-format.md)).

## Versioning

- `MAP_FORMAT_VERSION` (`nl.tankarena.sim.map`, 1) is bumped when the format changes incompatibly;
  readers refuse newer files.
- The object records follow the map objects' `State` classes (`nl.tankarena.sim`); a renamed or
  removed field needs a format bump or a migration.

## Known import gaps

Fields the importer parses but drops, or does not read at all, are listed under
[parity.md → Content and import](parity.md#content-and-import).
