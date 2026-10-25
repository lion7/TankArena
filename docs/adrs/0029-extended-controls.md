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
  - while aiming that way the view holds still (the original's camera follows the marker, which
    would then run away from under the pointer): it scrolls only while the pointer is in the outer
    20% of the view, faster nearer the edge, up to the marker's range from the vehicle, and moves
    along when the keys steer the marker out of sight. Client only (`AimCamera`): no rule change.
- **Players two to four play with gamepads as twin sticks:** the left stick drives the way it points,
  the right stick aims, the right trigger (or A) fires, the left trigger (or X) shields, LB and RB
  switch weapons. The original's joystick behaviour stays as a setting.
- **One device per player** (since 2026-10-25): player one has this machine's keyboard, mouse and
  touch screen (alone, also the first gamepad), every other player a gamepad. The arrow keys always
  drive player one. A second keyboard player was a setting until then; it was dropped because the
  mouse is player one's (a keyboard player two never aims with a pointer), keyboards lose keys when
  two players hold several, and Classic already is the two-on-one-keyboard game.
- **Next vehicle:** besides F1–F10 (which pick a vehicle by its number), each player has one control
  that takes the next friendly vehicle nobody else has picked, round to the first: Tab for player one
  (changeable on Extended's controls page, an 11th key that Classic's setup does not show), NEXT on
  the touch controller, Y on a twin-stick pad and BACK on a pad as the original's joystick. Like
  F1–F10 it does nothing when the players play against each other. Classic keeps F1–F10 only.
- **Joining:** before a local game player one's place is taken; the others press A on their pad.
- **On a touch screen** the virtual controller stays player one's; a mouse there works as on the
  desktop. A finger on the map works as the mouse does: the turret turns there, it fires, and the
  rocket marker follows the finger (`MousePointer` fed by `TouchControls`;
  [ADR 0025](0025-multiplatform-modules-and-android.md) has the controller's layout).

## How it works

- `InputFrame` and `PlayerIntentFrame` carry `pointX`/`pointY`, the map position pointed at, only
  while fire is held (so recordings stay small); the simulation uses it only under
  `RuleFlags.pointerMarker`.
- The game screen reads the mouse before the touch controls on top of it (`Modifier.mouseInput`); the
  player's view turns the cursor into a map position and draws a crosshair (`MouseLayer`).
- A `Seat` is `Own(pad)` (player one) or `Pad(n)`. `DesktopControls` combines player one's keys,
  mouse, touch and (alone) pad: the mouse becomes an aim direction from the
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
