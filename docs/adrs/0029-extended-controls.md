# ADR 0029: Extended Controls: Keyboard and Mouse, Twin-Stick Gamepads

- **Status:** Accepted (2026-10-21). Extended only; Classic keeps the original's controls
  ([ADR 0026](0026-input-and-gamepads.md)).

## Context

The original is played by two players at one keyboard, or with joysticks that mimic the keys. In
Extended that is awkward: two players share the keyboard, and the gamepads (ADR 0026) still behave
like the original's joystick, where a button turns the stick from driving into aiming. The touch
controls on Android (phase 7) already showed the modern form: one stick to drive the way it points,
one to aim.

## Decision

- **Player one plays with the keyboard and the mouse**, always, without joining:
  - WASD (and the arrow keys when nobody else is on the keyboard) drive the way they point (direct
    steering; the original's "turn and drive" is a setting);
  - the turret turns towards the mouse pointer, at the vehicle's own turning speed, so it is no
    advantage over the other controls; vehicles without a turret of their own fire along the hull
    and are aimed by driving, as in the original;
  - left button or Space fires, right button or Shift shields, the wheel or Q/E switch weapons, Z/C
    aim without a mouse; the keys can be changed on Extended's controls page;
  - while fire is held with rockets or soldiers selected, the marker goes where the pointer is
    (within its range): a rule change, Extended rules v7 (`RuleFlags.pointerMarker`). Without a
    pointed position the keys steer the marker as before, so older inputs play the same.
- **Players two to four play with gamepads as twin sticks:** the left stick drives the way it points,
  the right stick aims, the right trigger (or A) fires, the left trigger (or X) shields, LB and RB
  switch weapons. The original's joystick behaviour stays as a setting.
- **A second keyboard player** (player two's keys, the cursor block by default) can be switched on for
  those without a gamepad; then the arrow keys are theirs, not player one's.
- **Next vehicle:** besides F1–F10 (which pick a vehicle by its number), each player has one control
  that takes the next friendly vehicle nobody else has picked, round to the first: Tab for player one
  (and Enter while the arrow keys are theirs), Enter for the second keyboard player, Y on a twin-stick
  pad and BACK on a pad as the original's joystick. Like F1–F10 it does nothing when the players play
  against each other. Classic keeps F1–F10 only.
- **Joining:** before a local game player one's place is taken; the others press A, or the second
  keyboard player its fire key.
- **On a touch screen** the virtual controller stays player one's; a mouse there works as on the
  desktop.

## How it works

- `InputFrame` and `PlayerIntentFrame` carry `pointX`/`pointY`, the map position pointed at, only
  while fire is held (so recordings stay small); the simulation uses it only under
  `RuleFlags.pointerMarker`.
- The game screen reads the mouse before the touch controls on top of it (`Modifier.mouseInput`); the
  player's view turns the cursor into a map position and draws a crosshair (`MouseLayer`).
- `DesktopControls` combines a seat's keys, mouse and pad: the mouse becomes an aim direction from the
  player's vehicle (the same `aimX`/`aimY` the touch controls use), the wheel one weapon switch per
  notch. `PadReading` has the right stick and the triggers (Jamepad on the desktop, the motion axes
  on Android).
- `InputFrame` and `PlayerIntentFrame` carry `nextTank`, a one-shot request (`DesktopControls` sends
  it once per press of the key or button); the simulation turns it into the next id for
  `switchToTank` (`switchToNextTank`). It is not a rule change: Classic never sends it, and older
  peers ignore the field.

## Consequences

- The pointer position made the rules change of Extended rules v7; v6 recordings replay the same.
- Network games carry the new fields (`NET_VERSION` 4).
- The original's KEYS.HLP describes Classic; Extended's keys help is made from the current settings.
