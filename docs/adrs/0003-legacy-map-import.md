# ADR 0003: The Native Map Format Is the Source of Truth

- **Status:** Accepted (2026-10-19). Replaces the 2026-05 plan to import the maps once and then delete
  the importer.
- **Related:** [ADR 0022](0022-native-map-format.md) (the format and the converters),
  [ADR 0021](0021-editor-in-the-game.md) (the editor), [ADR 0015](0015-ship-the-original-69-maps.md)
  (which maps ship).

## Context

The original ships its maps as binary `.MAP` files (format in
[`original/docs/map-format.md`](../../original/docs/map-format.md)). The 2026-05 plan treated the
importer as transitional. In practice it was re-run in most gameplay commits, because every
faithfully ported rule needed a field the importer had not read yet. So `original/MAPS/*.MAP` became
the de facto source and the converted files generated output. That was a trap: a hand edit of a
converted file was lost at the next conversion.

## Options considered

1. Keep `.MAP` as the source and the converted files as generated output. Maps could only be changed
   with an editor that writes `.MAP`, and every detail of the rewrite's format would have to fit the
   original's binary layout.
2. Make the converted files the source and delete the importer. Old maps could no longer be brought
   in, and nothing could be checked against the original files.
3. **Chosen:** a native format is the source, and `.MAP` conversion stays in both directions.

## Decision

- The shipped maps are `.tamap` files in `game-content/resources/maps/`
  ([ADR 0022](0022-native-map-format.md)), changed with the in-game editor
  ([ADR 0021](0021-editor-in-the-game.md)).
- `.MAP` import and `.MAP` export are permanent features, so that maps from an old installation
  can come in and maps made in the rewrite can be played in the original. Export is in the editor
  and in `tools-mapconv`; import is in `tools-mapconv` only, an in-game import is still open
  ([roadmap](../roadmap.md)).
- `original/MAPS/*.MAP` stays in the repository as the original release and as test input. It is not
  part of the build.

## Consequences

- `MapDocumentTest` requires every original `.MAP` to import without notes to exactly its committed
  `.tamap`. An importer change therefore needs `tools-mapconv import-all` and a commit of the maps.
- Fixing a shipped map means editing it in the editor; the change shows in a readable diff.
- Import fidelity still matters, both for player maps and for the round trip to `.MAP`.
