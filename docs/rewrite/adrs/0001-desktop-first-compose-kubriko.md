# ADR 0001: Kotlin and Compose Multiplatform; Desktop First, Android Next

- **Status:** Accepted (2026-04; revised 2026-10-19). The original decision used Kubriko for the
  playfield; [ADR 0019](0019-plain-kotlin-no-kubriko.md) removed it.

## Context

The original Tank Arena is a DOS program written in C on Allegro 3, with hand-written assembly
blitters and direct hardware access. The rewrite needed a Kotlin stack that can draw a tile-and-sprite
game at the original's resolution, host menus and a status display, and reach more platforms later.

An engine survey in 2026-04 (deleted since; see [history](../history.md)) recommended KorGE. The
project chose Compose Multiplatform with the Kubriko engine on top, so that menus, the status display
and the playfield share one UI toolkit and one renderer (Skia). By 2026-10 the game drew almost
everything itself and used Kubriko only for bookkeeping, so Kubriko was removed (2026-10-19).

## Decision

- **Kotlin with Compose Multiplatform** for everything the player sees: menus, HUD, the playfield
  (drawn on a Compose Canvas by the game's own renderer), the editor.
- **Platforms, in order:** desktop (JVM; Linux and Windows packages today), then Android
  ([ADR 0025](0025-multiplatform-modules-and-android.md)), then possibly the web (WASM). Gameplay and
  protocol code is common Kotlin with no platform APIs.
- **Networked play** is a goal of the Extended ruleset ([ADR 0024](0024-networking.md)).

## Consequences

- One UI toolkit everywhere; Skia (desktop) and Android runtime shaders carry the palette effects the
  original did with VGA tricks ([ADR 0013](0013-original-presentation.md)).
- Platform-specific services (audio, MIDI, storage, gamepads, the loop driver) sit behind small
  interfaces with one implementation per platform.
- No game-engine dependency: the project owns its renderer, which is a few thousand lines but exactly
  what the game needs.
