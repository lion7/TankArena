# ADR 0026: Input Layer, Remapping and Gamepads

- **Status:** Accepted (2026-10-19; built 2026-10-20). Extended's own controls are in
  [ADR 0029](0029-extended-controls.md); this ADR is the shared layer and Classic's controls.

## Context

The original has remappable keys for two players, RELATIVE and DIRECT control modes and two
joysticks, all saved in `TANK.CFG`; that is part of Classic parity. Extended adds gamepads (several at
once, for four players), the mouse, touch on Android, and network players. All of them must end up as
the same `InputFrame` ([ADR 0005](0005-network-ready-input-protocol-boundary.md)).

## Decision

- **Devices → actions → `InputFrame`.** Keys, pads, the mouse and touch produce one `InputState` per
  player per tick (`nl.tankarena.input`); `ControlMapping` turns it into the original's controls with
  RELATIVE steering (turn and drive) or DIRECT steering (the vehicle turns the short way to where the
  keys or the stick point and drives forward; the turret follows an aim direction), with a dead zone
  for sticks. Network inputs, recordings and the AI produce the same frames.
- **Bindings** are kept per player in `settings.json` ([ADR 0020](0020-user-data-as-json.md)):
  ten keys and the mode, KEYBOARD or JOYSTICK, the four joystick buttons (FIRE, AIM, WEAPON, SHIELD;
  several pressed together count as one) and the sensitivity. Classic's controls setup has the
  original's pages (keys, then joystick on Tab); Extended has its own controls page.
- **Desktop gamepads** through Jamepad 2.30.0.0 (SDL2's game-controller layer: Xbox, PlayStation,
  Switch and generic pads, wired or Bluetooth, several at once). **Android** pads come from key and
  motion events. Both give a `PadReading` per pad.
- **Classic** treats a pad as the original's joystick: the stick drives, a button turns it into aiming.
- **Menus with a pad** (both rulesets, outside games, `GamepadMenuKeys`): stick or D-pad are the arrow
  keys with repeat, A and START Enter, B and BACK Esc, LB and RB Page Up and Down.
- **Assigning players** (Extended): before a local game a "press to join" screen gives each pad the
  next free place after player one (keyboard and mouse, [ADR 0029](0029-extended-controls.md)); the
  `Seat`s hold for the session, and the tank selection reads each player's pad.

## Consequences

- Classic's controls parity (remapping, control modes, joysticks) and Extended's gamepads share one
  implementation.
- Jamepad brings SDL2's native library per platform into the desktop packages.
- Not built: pausing a local game when a pad disconnects.
