# ADR 0022: A Native, Readable Map Format with `.MAP` Converters

- **Status:** Accepted (2026-10-19). The layout below is the design; details may change while it is
  implemented, and this ADR is updated when they do.
- **Supersedes:** the scene JSON + sidecar format of [ADR 0008](0008-kubriko-and-scene-json.md).
- **Decides:** [ADR 0003](0003-legacy-map-import.md) — the native format is the source of truth.

## Context

Today a map is two files: a Kubriko scene JSON (map objects, plus 40,263 wall entries over the 69 maps
that exist only for Kubriko's editor) and a sidecar whose tile layers are long JSON lists of picture
numbers. Neither is pleasant to read or diff, both are tied to Kubriko, and picture numbers mean
nothing without the catalog. The original `.MAP` is compact but binary, with raw C structs.

Wanted: one file per map that is small, readable and diffable, loads fast with kotlinx.serialization
on every platform, and converts losslessly to and from `.MAP`, so old maps can be imported and new maps
can still be played in the original under DOSBox.

## Decision

**One UTF-8 JSON file per map, `<name>.tamap`**, pretty-printed so that each map row is one line:

```json
{
  "format": 1,
  "header": { "name": "BEGIN1", "code": "BEGIN1", "next": "BEGIN2", "world": "TILE",
              "night": false, "type": "SINGLE", "contents": [], "publicCode": false,
              "randomBonus": true, "size": [12, 12] },
  "texts": { "briefing": "This is your first mission: ...", "success": "...", "failure": "..." },
  "legend": { "..": null, "a1": "BACK1", "w1": "WAT1", "h1": "HUIS1H", "h2": "HUIS1V" },
  "floor":  [ "a1a1a1w1w1a1 ...", "..." ],
  "solid":  [ "....h1h2.... ...", "..." ],
  "top":    [ "...", "..." ],
  "goals":  [ { "at": [4, 7], "share": -15 } ],
  "bonuses":[ { "at": [2, 3], "bonus": "FUEL" } ],
  "men":    [ { "at": [5, 5], "weapon": "CHAIN", "count": 4 } ],
  "objects":[ { "type": "turret", "at": [140, 66], "kind": 1, "dir": 4, "shootAt": "ANY" },
              { "type": "tank", "at": [33, 33], "vehicle": "STANDARD", "side": "GOOD", "player": 1 } ]
}
```

- **Picture layers as text grids.** A per-map `legend` maps two-character codes to picture *names*
  from the original catalog (`src/data/pictures.c`); each layer is a list of rows, each row the codes
  of its cells, `..` for none. A 200 × 200 layer is 200 lines of 400 characters. The shape of the map
  is visible in a text editor, diffs show which tiles changed, and maps survive any renumbering of
  pictures.
- **Sparse layers as lists.** The goal, bonus and men layers are mostly empty, so they are lists of
  the cells that have a value.
- **Objects as typed records** with named fields, positions in pixels (centres), enums by name, and
  default values omitted. Links (lock targets, goal targets, train couplings, turret wagons) use
  object ids within the file.
- **Ruleset data** that the original does not have (Extended-only settings, team pads for four
  players) goes in an optional `extended` section that `.MAP` export drops
  ([ADR 0023](0023-classic-and-extended-rulesets.md), [ADR 0027](0027-four-players-two-teams.md)).
- **Size:** typically smaller than today's scene + sidecar pair; files compress well (maps are
  bundled compressed in packages if needed).

**Converters** (`tools-mapconv` and the in-game editor):

- `import <MAP> <tamap>` / `import-all <MAPS> <dir>`: `.MAP` → native. Unknown bytes in object
  records are kept in a per-object `legacy` field so that export can write them back.
- `export <tamap> <MAP>`: native → `.MAP` (version 6, unprotected), playable by the original.
- **Round-trip tests:** for every shipped map, `.MAP` → native → `.MAP` → native gives the identical
  native file, and the exported `.MAP` produces the same match as the original file in the rewrite.

## Consequences

- The 69 shipped maps are converted once into `game-content/resources/maps/*.tamap`; from then on
  these files are the source, edited by the in-game editor ([ADR 0021](0021-editor-in-the-game.md)).
  `MAPS/*.MAP` stays in the repository as the original release and as round-trip test input.
- `.MAP` import is a permanent feature (players can bring maps from their old install); it is no
  longer how shipped content is produced.
- The scene/sidecar files, `CanonicalSceneBuilder`, `LegacyToSceneJson`, the canonical intermediate
  and the Kubriko serialization registry are removed; tests build fixtures as native map documents.
- `SCENE_SCHEMA_VERSION` is replaced by the file's `format` number.
