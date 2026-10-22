# ADR 0021: Map Editor Inside the Game

- **Status:** Accepted (2026-10-19). Replaces the earlier plans for a separate editor application
  (2026-04) and for an editor built on a generic scene editor (see [history](../history.md)).

## Context

The original's editor (`original/src/edit/`) is linked into `TANK.EXE` and started from the main
menu. Most of what a Tank Arena editor needs — painting three picture layers with each world's
structure library and its links; the goal, bonus and men layers; the header and texts; every object
with its own settings — is specific to this game. A generic object inspector covers only a small part
of it.

## Decision

- The editor is a **screen of the game**, started from the main menu ("Map editor") in both rulesets
  ([ADR 0023](0023-classic-and-extended-rulesets.md)), as in the original. There is no separate
  application and no launch switch.
- It edits the native map document ([ADR 0022](0022-native-map-format.md)) and draws it with the
  game's renderer, so what you edit is what you play. The editing rules live in `:game-sim`
  (`MapEditor`, `MapObjects`, `sim.map`) and are tested without a screen; the screens are in
  `:game-ui` (`EditorScreens`, `EditorMapPage`, `EditorObjects`).
- **Scope: everything the original editor can do.** A new map from a world and size (empty, a barrier
  border or all water); the three picture layers with a searchable palette and each world's structure
  library (`original/DATA/MAP_0..3.DAT`, ready-made linked cells, placed singly or over a region); the
  goal, bonus and men layers; every object kind the original editor adds, with its `make_*()`
  defaults and the settings of the original's menus, including computer-vehicle settings and
  waypoints, warp exits, lock and goal links, turret forbidden directions and trains; the header
  (codes, map type and contents, public code, random bonuses, night); the mission texts.
- **Additions:** undo and redo for every change; **play-test** from the open map (F5) and back with
  the undo history; saving to the player's maps, which then appear in the game's lists (a saved map
  replaces the shipped one of the same name, as saving over a file in the original's `MAPS` did);
  **export to `.MAP`** (Ctrl+E); start pads for players 3 and 4 ([ADR 0027](0027-four-players-two-teams.md)).
- Planned additions, not built: validation (unreachable pads, broken links, goals that can never
  reach 100 %), a whole-map overview, `.MAP` import, touch editing on Android.
- Left out on purpose: the original's structure-library editor (under F12, "not supposed to be used by
  other people"); the libraries ship as they are.

## Consequences

- The editor shares the renderer and the map model with the game; there are no adapters.
- The editor only runs a match when play-testing, through the normal match client (unrecorded).
- What only a person can check (the feel of the tools, exported maps in DOSBox) is in the
  [manual test checklist](../editor-test-checklist.md).
