# ADR 0001: Kotlin and Compose Multiplatform

- **Status:** Accepted (2026-04).
- **Related:** [ADR 0019](0019-plain-kotlin-no-game-engine.md) (no game engine),
  [ADR 0025](0025-multiplatform-modules-and-android.md) (modules and Android).

## Context

The original Tank Arena is a DOS program written in C on Allegro 3, with hand-written assembly
blitters and direct hardware access. The rewrite needed a Kotlin stack that can draw a tile-and-sprite
game at the original's resolution, host menus and a status display, and reach more platforms than
the desktop.

An engine survey in 2026-04 recommended KorGE for a web-first port. The project chose a
desktop-first stack instead, in which menus, the status display and the playfield share one UI
toolkit and one renderer.

## Decision

- **Kotlin with Compose Multiplatform** for everything the player sees: menus, the status display,
  the playfield (drawn on a Compose `Canvas` by the game's own renderer) and the editor.
- **Platforms:** desktop (JVM) first, then Android; the web (WASM) stays possible. Gameplay,
  protocol and content code is common Kotlin with no platform APIs.

## Consequences

- One UI toolkit everywhere. Skia runtime shaders on the desktop and AGSL runtime shaders on Android
  carry the palette effects the original did with VGA tricks ([ADR 0013](0013-original-presentation.md)).
- Platform services (audio, MIDI, storage, gamepads, screenshots) sit behind small interfaces with
  one implementation per platform (`GamePlatform`, [ADR 0025](0025-multiplatform-modules-and-android.md)).
- The project owns its renderer: a few thousand lines, but exactly what the game needs.
- Today the game ships for Linux, Windows and Android. macOS and the web are not built or tested.
