# ADR 0004: Editor as a Separate App

- **Status:** Superseded on 2026-10-19 by [ADR 0021](0021-editor-in-the-game.md): the editor becomes a
  screen of the game, as in the original. Kept for the record.

## Context

In the original, the map editor (`original/src/edit/`) is linked into `TANK.EXE`: the main menu's "Map
editor" entry calls `edit_map()` (`original/src/game/menu.c:43`, `original/src/edit/main.c:620`). It edits `.MAP`
files directly. The rewrite deliberately departs from that and makes the editor its own program.

## Decision

- The editor is its own desktop application, `:game-editor` (`jvm/app`, main class
  `com.tankarena.app.editor.MainKt`), separate from the game client `:game-client`.
- It shares `:game-content`, `:game-protocol` and the map object types of `:game-server`, but never
  runs the simulation (see the boundary rule in ADR 0009).
- The client's "Map editor" menu entry is meant to start it, as in the original. Today the entry is
  disabled; wiring it is part of the editor work in [parity.md](../parity.md#map-editor).

## Consequences

- The shipped game does not carry editor code, and editor work cannot break gameplay.
- Play-testing a map from the editor needs a way to start a match in the client (or an embedded
  match); that is open work.
