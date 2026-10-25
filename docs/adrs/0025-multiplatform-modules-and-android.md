# ADR 0025: Multiplatform Module Layout and an Android App

- **Status:** Accepted (2026-10-19; built 2026-10-20). Replaces the six JVM-only modules of 2026-04.

## Context

The first layout had six JVM-only modules (`game-protocol`, `game-content`, `game-server`,
`game-client`, `game-editor`, `tools-mapconv`). The game should also ship for Android, keep a web
target possible, and run headless as a dedicated server ([ADR 0024](0024-networking.md)). Platform
code was mixed into shared code in a few places: `javax.sound` audio and MIDI, class-path resources,
XDG folders, the window.

## Decision

Ten modules (Kotlin toolchain, [ADR 0016](0016-kotlin-toolchain-build.md)); packages and artifacts
under `nl.tankarena`. Dependencies point one way:

```text
game-protocol <- game-content <- game-sim <- game-net <- game-ui <- app-desktop, app-android
                                                 game-net <- app-server, app-relay
                                 game-sim <- tools-mapconv
```

| Module | Kind | Contents |
|---|---|---|
| `game-protocol` | kmp library (JVM, Android) | Inputs, snapshots, events, versions, deterministic maths |
| `game-content` | kmp library | Picture catalog, terrain tables, masks, structure libraries, sounds, music, the shipped maps, the `.MAP` reader |
| `game-sim` | kmp library | The simulation, rulesets, recordings, the map format, `.MAP` import and export, the editor's rules |
| `game-net` | kmp library | Network messages, the host with its lobby, the remote client, the relay |
| `game-ui` | kmp library (Compose) | The whole game (`TankArenaApp`): screens, renderer, status display, both menus, editor, replays, input layer, mixer |
| `app-desktop` | JVM app | Window, `javax.sound` output, Java MIDI, Jamepad gamepads, screenshots |
| `app-android` | Android app | Activity, `AudioTrack` output, `MediaPlayer` MIDI, pads, touch, app storage |
| `app-server` | JVM app | The headless dedicated server |
| `app-relay` | JVM app | The internet relay and lobby ([ADR 0028](0028-internet-relay-and-lobby.md)) |
| `tools-mapconv` | JVM app | Asset extractors and `.MAP` conversion from the command line |

- **Platform services** behind small interfaces in common code: `GamePlatform` (audio backend,
  music, gamepads, menu keys from pads, touch screen, device name, broadcast lock, quit, screenshot),
  `AppDirs` (folders), `GameKeyHook` (every key first), `PcmOutput` under the shared
  `MixerAudioBackend`, and `PlatformGraphics` (`expect`/`actual`: Skia runtime effects on the
  desktop, Android bitmaps and AGSL runtime shaders).
- **Android:** min SDK 26. The night, colour-cycling and gamma shaders need Android 13 (API 33);
  older versions draw without them. Gamepads come from key and motion events (A and START are Enter,
  B, BACK and the back button Esc in menus). On a touch screen without a pad, player one gets a
  virtual controller over the game (`TouchControls`, reworked 2026-10-25):
  - sizes in dp (a stick 120 dp across, buttons at least 48 dp; a size setting, capped by the
    screen's height), in the strips beside the map window when there is room, else in the corners;
    the original's palette and 5×5 font; idle at a set visibility, lit while held;
  - two floating sticks with a 15% dead zone (the vehicles drive or not, so no half speed); the aim
    stick pushed past 70% fires too (a setting);
  - in Extended a finger on the map aims and fires there like the mouse (the rocket marker follows
    it); FIRE, SHIELD, the weapon buttons showing the weapon they switch to, NEXT only when there is
    another vehicle; PAUSE opens the pause box with Go on and Leave (in a network game the leave
    question); a left-handed mirror; a short buzz on firing and hits (Android, a setting);
  other players need gamepads. Taps go through the intro, texts, messages, yes/no questions, the
  tank selection and every Extended page; Classic is played with the keyboard.
- **Packages:** `packaging/package-android.sh` makes debug and release APKs and a release bundle
  (R8-shrunk) in `build/dist/android`. Without a `KEYSTORE` the release build is signed with the
  debug key.

## Consequences

- The shipped apps carry no converter code; extractors live in `tools-mapconv`.
- Common modules use only JVM APIs that Android also has (`java.io.File`, threads). A web target
  would need those replaced; it is not planned.
- Open: release signing with a real key, a try on a real phone, and the simulation's per-tick
  garbage on Android ([performance.md](../performance.md)).
