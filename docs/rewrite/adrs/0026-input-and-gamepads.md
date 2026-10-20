# ADR 0026: Input Layer, Remapping and Gamepads

- **Status:** Accepted (2026-10-19). Built 2026-10-20: the input layer (`com.tankarena.input`:
  `InputState`, `ControlMapping` for relative and direct steering with keys and sticks) and gamepads
  through Jamepad 2.30.0.0 as the original's joysticks; press to join (`JoinState`, session `Seat`s)
  and menus with a pad (`GamepadMenuKeys`); up to four players join ([ADR 0027](0027-four-players-two-teams.md)).

## Context

Keys are hard-coded in `KeyBinding` (`game-client/.../app/desktop/Main.kt`). The original has
remappable keys for two players, RELATIVE and DIRECT control modes and two joysticks, all saved in
`TANK.CFG`; that is part of Classic parity. Extended adds gamepads (including several Bluetooth
controllers at once, for four players), touch on Android, and per-device settings.

## Decision

- **Devices → actions → `InputFrame`.** Every source (keyboard, gamepad, touch, network, recording,
  AI) produces the same actions (drive, turn, turret, fire, weapon left/right, shield, tank select);
  each local player has one `InputFrame` per tick built from the devices assigned to them.
- **Bindings** are per device profile and stored in `settings.json`
  ([ADR 0020](0020-user-data-as-json.md)). Classic offers the original's key setup and control modes;
  Extended offers a settings screen per device with defaults for common controllers.
- **Desktop gamepads:** Jamepad (SDL2's game-controller layer for the JVM) on Windows, Linux and
  macOS; it handles Xbox, PlayStation, Switch and generic pads, wired or Bluetooth, several at once,
  with the SDL controller database.
- **Android:** controllers through key and motion events, which Android supports natively for
  several Bluetooth pads; on phones an on-screen stick and buttons.
- **Assigning players:** in Extended, "press a button to join" assigns a device to the next free
  player slot; disconnects pause local games.
- Analog sticks map to the original's 16 directions in Extended (stick points where to go); Classic
  keeps relative steering unless DIRECT is chosen.

## Consequences

- Classic parity for controls (remapping, control modes, joysticks) and Extended's gamepad support
  share one implementation.
- Jamepad brings a native SDL2 library per platform into the desktop packages.
- The menus must be fully usable with a gamepad in Extended (focus navigation).
