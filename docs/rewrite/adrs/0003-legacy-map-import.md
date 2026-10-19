# ADR 0003: Legacy Map Import — Source of Truth

- **Status:** Decided 2026-10-19: the native map format is the source of truth; `.MAP` import and
  export are permanent converters. Earlier versions: 2026-05-06 "import once, then delete the
  importer" (task T07); reopened and left open earlier on 2026-10-19.
- **Related:** [ADR 0022](0022-native-map-format.md) (the format and converters),
  [ADR 0021](0021-editor-in-the-game.md) (the editor that changes maps),
  [ADR 0015](0015-ship-the-original-69-maps.md) (which maps ship).

## Context

The original ships its maps as binary `.MAP` files (format in
[`docs/game/map-format.md`](../../game/map-format.md)). The rewrite plays converted files, currently
a Kubriko scene JSON plus a `MapSceneSidecar` ([content format](../content-format.md)), regenerated
from `MAPS/*.MAP` by `tools-mapconv scene-all` (until 2026-10-19).

The 2026-05 decision treated the importer as transitional and planned to delete it. In practice it
was re-run in most gameplay commits since 2026-09, because every faithfully ported rule needed a field
the importer had not read yet. So `MAPS/*.MAP` became the de facto source and the converted files
generated output — the opposite of the plan, and a trap: a hand edit of a converted file is lost at the
next regeneration.

## Options considered

1. Keep `MAPS/*.MAP` as the source and the converted files as generated output. Maps could only be
   changed with an editor that writes `.MAP`, and every format detail of the rewrite would have to fit
   the original's binary layout.
2. Make the converted files the source and delete the importer. Old maps could no longer be brought
   in, and nothing could be checked against the original files.
3. **Chosen:** a native format is the source, and `.MAP` conversion stays in both directions.

## Decision

- Shipped maps live in the native format ([ADR 0022](0022-native-map-format.md)) and are edited with
  the in-game editor ([ADR 0021](0021-editor-in-the-game.md)).
- `.MAP` → native import and native → `.MAP` export are permanent features: players can import maps
  from their own installation, and maps made in the rewrite can be played in the original.
- `MAPS/*.MAP` (the 69 originals) and `MAPS/extra/` stay in the repository as the original release and
  as test input for round-trip tests; they are no longer converted as part of the build.
- Done on 2026-10-19: the 69 maps are `.tamap` files; the scene files are gone. Until the editor exists,
  an importer change is followed by `import-all` and a commit of the regenerated maps (a test enforces
  it). Export to `.MAP` is still to be built (ADR 0022).

## Consequences

- Import fidelity remains important (it is how the 69 maps get converted one last time and how player
  maps come in), but it is no longer on the path of every gameplay change.
- Once the 69 maps are converted, fixing a map means editing it in the editor; changes are visible in
  diffs of the readable map files.
