# ADR 0003: Legacy Map Import — Source of Truth

- **Status:** Open (reopened 2026-10-19). The 2026-05-06 decision "import once, then delete the
  importer" (task T07) is suspended until this ADR is decided.
- **Related:** [ADR 0008](0008-kubriko-and-scene-json.md) (scene format),
  [ADR 0015](0015-ship-the-original-69-maps.md) (which maps ship),
  [ADR 0019](0019-remove-kubriko-from-the-server.md) (Kubriko leaves the server).

## Context

The original ships its maps as binary `.MAP` files (format in
[`docs/game/map-format.md`](../../game/map-format.md)). The rewrite plays converted files: a Kubriko
scene JSON with the map's objects plus a `MapSceneSidecar` with the tile layers, mission texts and
header fields ([content format](../content-format.md)). `tools-mapconv scene-all` regenerates all of
them from `MAPS/*.MAP`.

The 2026-05 version of this ADR treated the importer as transitional: once every object family was
parsed and the scenes were committed, the parser (`LegacyMapParser`), the server's `legacy/*`
package, `CanonicalSceneBuilder` and the `scene`/`scene-all` commands would be deleted.

In practice the importer has been re-run in most gameplay commits since 2026-09: every time a rule
was ported faithfully, it needed a field the importer had not read yet (signed goal layer, start pad
weapons, AI attack fields and waypoints, train state, lock and enforcer fields, map contents). The
committed scenes are therefore generated output, and `MAPS/*.MAP` is the de facto source.

## Options

1. **`MAPS/*.MAP` stays the source.** The importer is a permanent part of the content pipeline; the
   scene and sidecar files are generated and committed (or generated at build time). New or changed
   maps are made by the editor writing `.MAP`, or by editing scenes that are never regenerated.
2. **The converted files become the source.** Finish import fidelity (see the parity gaps in
   [parity.md](../parity.md#content-and-import)), regenerate once more, then delete the legacy
   parsing surface. From then on, maps are only changed through the editor
   ([ADR 0009](0009-editor-wraps-kubriko-scene-editor.md)), which first has to edit tile layers and
   the sidecar.

## Current state (while open)

- The client never reads `.MAP` files; it loads the committed scenes from the classpath
  (`ShippedScenes`, `game-content/resources/scenes/index.txt`).
- The importer lives in `:game-content` (`legacy/LegacyMapParser.kt`) and `:game-server`
  (`legacy/*`, `CanonicalSceneBuilder`, `LegacyToSceneJson`); it is used by `:tools-mapconv` and by
  tests, not at runtime.
- Any change to the importer must be followed by `scene-all` and a commit of the regenerated files.

## What decides it

- Option 2 needs an editor that can change everything a `.MAP` holds; until then option 1 is the only
  way to fix a map.
- [ADR 0019](0019-remove-kubriko-from-the-server.md) changes the scene format anyway; deciding this ADR
  together with the new format avoids converting twice.
