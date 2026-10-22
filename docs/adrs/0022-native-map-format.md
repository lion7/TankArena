# ADR 0022: A Native, Readable Map Format with `.MAP` Converters

- **Status:** Accepted (2026-10-19; `.MAP` export option C chosen and built 2026-10-20).
- **Decides:** [ADR 0003](0003-legacy-map-import.md): the native format is the source of truth.
- **Details of the format as built:** [content-format.md](../content-format.md).

## Context

A map needed one file that is small, readable and diffable, loads with kotlinx.serialization on every
platform, and converts to and from the original's `.MAP`, so that old maps can come in and new maps can
still be played in the original under DOSBox. The original `.MAP` is compact but binary, with raw C
structs; the earlier converted files (a scene JSON plus a sidecar with long lists of picture numbers,
29 MB for the 69 maps) were neither readable nor small.

## Options considered

For the container:

| Option | For | Against |
|---|---|---|
| **One JSON file (chosen)** | kotlinx.serialization everywhere, no new library; standard tools | No comments; quotes and commas |
| Own text format | Most readable, comments | Own reader, writer and errors to maintain |
| YAML or TOML | Readable, comments | Third-party library, uneven Android/WASM support |
| A folder per map | Each layer a plain text file | Many files per map; awkward to share |
| Binary (CBOR, protobuf) | Smallest, fastest | Not readable |

For the picture layers (sizes for RACE3's three layers):

| Option | Size | For | Against |
|---|---|---|---|
| **Legend grid, 2-character codes (chosen)** | ~95 KB | Shape visible, columns aligned, small diffs | Needs the legend |
| Number grid | ~235 KB | No legend | Very long lines; numbers mean nothing |
| Name grid | ~330 KB | Self-describing | Very wide, columns do not align |
| Run-length rows | ~30–60 KB | Small for empty maps | Shape not visible; one tile rewrites a row |
| Base64 numbers | ~125 KB | Simple, fast | Unreadable, huge diffs |

## Decision

**One UTF-8 JSON file per map, `<name>.tamap`**, written so that each map row is one line:

- **Picture layers as text grids.** A per-map `legend` gives each picture the map uses a
  two-character code; each of the three layers is a list of rows, two characters per cell, `..` for
  none. The first character comes from the picture's terrain type (`w` water, `r` roads, `h`
  buildings, `#` indestructible structures, …), so the grids read like a sketch of the map. A legend
  entry is `[picture index, "NAME"]`: the index identifies the picture (the same name occurs at
  several indices with different properties) and the name is checked when the file is read.
- **Sparse layers as lists:** the goal, bonus and men layers list only the cells that have a value.
- **Objects as records** of the map objects' own fields by name, after `type`, `at` (top-left corner
  in pixels) and `size`; default values are left out. Walls are not objects.
- **Data the original does not have** is an ordinary field that `.MAP` export leaves out: start pads
  for players 3 and 4 are tank records with `padPlayer` 2 or 3
  ([ADR 0027](0027-four-players-two-teams.md)).
- **Versioning:** `format` (`MAP_FORMAT_VERSION`, 1); readers refuse newer files.

**Converters**, in `:game-sim` (`sim.map`), used by `tools-mapconv` and the editor:

- **Import** (`LegacyMapImport`, `tools-mapconv import` / `import-all`): `.MAP` → `.tamap`.
- **Export** (`LegacyMapExport`, `tools-mapconv export`, the editor's Ctrl+E), **option C**: write a
  `.MAP` (version 6, not locked) the way the original editor would. Each object's 696-byte record
  starts from the original's `make_*()` defaults with the map's values on top. What the rewrite does
  not model (pointers and timers saved by the original editor, text left after a code, fields the
  game does not use such as a mine's goal share or a pad's laps) is kept per imported map in an
  optional `legacy` section: byte runs `[offset, "hex"]` for the header and each object, and the texts
  as they were in the file. The editor drops the runs over bytes an edit changes, so edited objects
  export with their new values; new objects have none.

## Consequences

- The 69 shipped maps take 2.7 MB as `.tamap` (4.0 MB as `.MAP`), the `legacy` sections included;
  RACE3 is 100 KB against 165 KB.
- Every original `.MAP` imports to exactly its committed `.tamap` and exports back to exactly its own
  bytes (unlocked); both are tested.
- Exported maps have not yet been tried in the original under DOSBox
  ([manual checklist](../editor-test-checklist.md)).
- Object records follow the map objects' `State` classes; renaming or removing a field needs a format
  bump or a migration.
