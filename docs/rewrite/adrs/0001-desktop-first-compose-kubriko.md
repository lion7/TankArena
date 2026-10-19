# ADR 0001: Desktop-First with Compose Multiplatform and Kubriko

- **Status:** Accepted (2026-04; revised 2026-10-19).
- **Related:** [ADR 0019](0019-remove-kubriko-from-the-server.md) narrows where Kubriko is used.

## Context

The original Tank Arena is a DOS program written in C on Allegro 3, with hand-written assembly
blitters and direct hardware access. The rewrite needed a Kotlin stack that can draw a tile-and-sprite
game at the original's resolution, host menus and a status display, and later reach more targets.

An engine survey in 2026-04 (deleted since; see [history](../history.md)) recommended KorGE. The
project chose Kubriko instead: it is built on Compose Multiplatform, so menus, the in-game status
display and the playfield share one UI toolkit and one rendering backend (Skia).

## Decision

- **Desktop (JVM) is the only target until parity.** All modules build for the JVM only.
- **Compose Multiplatform** draws the shell: intro, menus, prompts, text pages, the status display
  (HUD), fades and screen-wide colour effects.
- **Kubriko** draws the playfield: one Kubriko instance per player window, with render actors synced
  from server snapshots and Kubriko's sprite manager loading the extracted pictures.
- Networked play and a WASM/web target remain **post-parity goals**. Module boundaries must keep them
  possible: no desktop-only APIs in `:game-protocol` or `:game-content`, and gameplay stays out of the
  client.

## Consequences

- One UI toolkit for everything the player sees; Skia runtime shaders are available for the palette
  effects the original did with VGA palette tricks (night, colour cycling, gamma; see
  [ADR 0013](0013-original-presentation.md)).
- Kubriko is a young, single-maintainer engine. Its version is pinned and bumps are deliberate (see
  the [risk register](../risk-register.md)).
- Audio and MIDI use the JDK (`javax.sound`), which is desktop-only; a web target will need another
  backend behind the existing `AudioBackend` interface.
