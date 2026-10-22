# ADR 0025: Multiplatform Module Layout and an Android Package

- **Status:** Accepted (2026-10-19). First step done 2026-10-20: `game-server` became `game-sim`,
  `game-client` was split into `game-ui` and `app-desktop`, the extractors moved to `tools-mapconv`,
  packages and artifacts are `nl.tankarena`. Then (same day) the libraries target Android too, the
  game became `TankArenaApp` in `game-ui` with a `GamePlatform` per app, graphics go through
  `PlatformGraphics` (Skia / Android bitmaps and AGSL), the mixer is shared (`MixerAudioBackend`
  with a `PcmOutput` per platform), and `app-android` builds an APK. `game-net` and `app-server` come
  with phase 8. Replaces the module list of [ADR 0006](0006-module-collapse.md).

## Context

All six modules are JVM-only today. The game should also ship as an Android package, keep a WASM/web
target possible, and get a headless dedicated server ([ADR 0024](0024-networking.md)). Platform code
is mixed into shared code in a few places: `javax.sound` audio and MIDI, class-path resource loading,
XDG folders, the desktop simulation thread.

## Decision

Target layout (Kotlin toolchain, [ADR 0016](0016-kotlin-toolchain-build.md)):

| Module | Kind | Contents |
|---|---|---|
| `game-protocol` | common library | Inputs, snapshots, events, versions |
| `game-sim` | common library | `World`, systems, rulesets, deterministic maths, `MatchHost` |
| `game-content` | common library | Native maps, picture catalog and terrain tables, masks; `.MAP` import and export |
| `game-net` | common library | Ktor WebSocket client and server around `MatchHost` |
| `game-ui` | common library (Compose) | Renderer, HUD, both menus, editor, replays, input layer |
| `app-desktop` | desktop app | Window, desktop audio, gamepads, storage, packaging |
| `app-android` | Android app | Activity, Android audio and MIDI, controllers, touch, storage |
| `app-server` | JVM app | Headless dedicated server |
| `tools-mapconv` | JVM app | Asset extraction, `.MAP` import/export in bulk |

- Platform services behind small interfaces in common code: `AudioBackend`, `MusicPlayer`, `Storage`,
  `GamepadSource`, `Clock`/loop driver.
- Android specifics: audio through `AudioTrack` fed by the shared software mixer; MIDI through
  `MediaPlayer` (Android plays `.mid` files natively); the night and colour-cycling shaders as AGSL
  runtime shaders (Android 13 and later; older versions get a simplified night look); app-private
  storage for settings, codes and recordings.
- WASM stays possible: nothing in the common modules uses JVM-only APIs; networking there uses
  WebSockets.

## Consequences

- Most code moves once more; the move is mechanical and happens after the plain-Kotlin core
  ([ADR 0019](0019-plain-kotlin-no-kubriko.md)) so it does not mix with behaviour changes.
- `tools-mapconv`'s extractors move out of the runtime content module, so shipped apps no longer
  carry converter code.
- Android needs its own packaging (APK/AAB, signing) next to [ADR 0017](0017-desktop-packaging.md).
