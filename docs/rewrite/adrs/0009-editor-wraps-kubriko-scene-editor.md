# ADR 0009: Editor Built on Kubriko's SceneEditor

- **Status:** Accepted (2026-04; reaffirmed 2026-10-19).
- **Related:** [ADR 0004](0004-separate-editor-app.md) (separate app), [ADR 0003](0003-legacy-map-import.md)
  (map source of truth).

## Context

The original's editor (`src/edit/`, ~23 files) edits structures per layer with links, every object
type through property menus, the goal, men and bonus layers, the map header ("SPECIAL" menu: map
type, codes, day/night, public/shareware), creates maps from the world templates `DATA/MAP_0..3.DAT`
and shows a whole-map overview. Rebuilding that from scratch in Compose is a large job. Kubriko ships
a generic `SceneEditor` (viewport, palette, property inspector, load/save) that works on the same
serialized objects the game reads.

On 2026-10-19 the choice was revisited: porting the original editor screen by screen was rejected in
favour of extending the Kubriko-based editor until it can do everything the original can.

## Decision

- `:game-editor` wraps Kubriko's `SceneEditor` (`tool-scene-editor-desktop`) and opens
  `game-content/resources/scenes/` by default.
- It reuses the server's map object classes and their `State` through Kubriko's `Editable` interface,
  so editor and game read the same files with no translation.
- **Boundary rule:** the editor never runs the simulation. It must not reference `ServerMatchPrototype`,
  `CollisionManager` or `TerrainSlideManager`; `EditorBoundaryGuardTest` (in
  `game-server/test/kotlin/com/tankarena/app/editor/`) fails the build if it does.
- To reach parity the editor has to be extended with (tracked in [parity.md](../parity.md#map-editor)):
  every object type registered as editable (today only wall, tank, turret and goal), painting of the
  three structure layers with links, the goal/men/bonus layers, editing the sidecar header and mission
  texts, new maps from the world templates, validation, play-testing, and launch from the client menu.

## Consequences

- The viewport, selection, inspector and load/save come from Kubriko; the project writes only Tank
  Arena specific tools.
- The editor is tied to Kubriko and to the scene format. [ADR 0019](0019-remove-kubriko-from-the-server.md)
  moves the map object types out of Kubriko on the server, so the editor will need its own Kubriko
  wrappers (or editable adapters) around plain map data.
- Today (73 lines, four object types, no tile layers or sidecar) the editor cannot change most of
  what a map holds. Until it can, maps can only be fixed through `.MAP` + `scene-all` (ADR 0003).
