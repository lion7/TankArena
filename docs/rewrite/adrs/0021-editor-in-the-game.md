# ADR 0021: Map Editor Inside the Game

- **Status:** Accepted (2026-10-19).
  First version built 2026-10-19: open, paint the picture layers, undo, save (roadmap phase 4).
  2026-10-20: new maps, structures, the goal, bonus and men layers, objects, settings and texts.
- **Supersedes:** [ADR 0004](0004-separate-editor-app.md) (separate editor app) and
  [ADR 0009](0009-editor-wraps-kubriko-scene-editor.md) (editor on Kubriko's `SceneEditor`).

## Context

The original's editor (`src/edit/`) is linked into `TANK.EXE` and started from the main menu. The
rewrite's `:game-editor` is a separate 73-line wrapper around Kubriko's `SceneEditor` that edits four
object types and none of the tile layers or the header. Most of what an editor for Tank Arena needs —
painting three structure layers with the structure library and links, the goal, bonus and men layers,
the header, texts, the structure libraries — is specific to this game; a generic object inspector covers only
a small part. Kubriko is removed ([ADR 0019](0019-plain-kotlin-no-kubriko.md)), and `:game-editor` with it.

## Decision

- The editor is a **screen of the game**, started from the main menu ("Map editor") in both rulesets
  ([ADR 0023](0023-classic-and-extended-rulesets.md)), as in the original. No separate application
  and no launch switch.
- It edits the native map document ([ADR 0022](0022-native-map-format.md)) directly and draws it with
  the game's renderer, so what you edit is exactly what you play.
- Scope (everything the original editor can do): new map from a world and size (empty, with a
  barrier border, or all water); the three picture layers with the world's structure library
  (`DATA/MAP_0..3.DAT`: ready-made linked cells, placed singly or over a region); the goal, bonus and men
  layers; every object type with its properties, including computer-tank settings and waypoints, pad
  weapons, lock and goal links, train setup; the header (codes, map type and contents, public and
  random-bonus flags, world, night); mission texts; whole-map overview.
- Additions over the original: validation (unreachable pads, broken links, goals that can never reach
  100 %), undo, **play-test** (start a local match from the open document and return), import and
  export of `.MAP` files.
- Works with keyboard and mouse on desktop; Android gets it later with touch support.

## Consequences

- `:game-editor` and its boundary-guard test are deleted. The rule "the editor never runs the
  simulation" becomes "the editor only runs a match through the normal match host when play-testing".
- The editor shares the renderer, picture atlas and map model with the game; no adapters.
- It is the largest single parity item; it is planned after the plain-Kotlin core and the native map
  format exist ([roadmap](../roadmap.md)).
