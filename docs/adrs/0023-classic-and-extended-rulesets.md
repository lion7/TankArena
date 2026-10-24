# ADR 0023: Two Rulesets — Classic and Extended ("Next Kombat")

- **Status:** Accepted (2026-10-19). Extended is shown to players as **Tank Arena: Next Kombat**
  (named 2026-10-21; the initials spell TANK).

## Context

The rewrite has two jobs that pull in different directions:

- be a faithful reproduction of the original, checkable side by side against DOSBox
  ([ADR 0010](0010-port-the-original-rules.md), [ADR 0013](0013-original-presentation.md));
- grow beyond it: networked play, four players, gamepads, mouse controls, Android, a friendlier menu,
  replays.

Building two games would double the work and let them drift. Sprinkling modern changes through the
faithful code would erode the reference.

## Decision

One game core with a **ruleset**, `Ruleset.CLASSIC` or `Ruleset.EXTENDED` (`:game-sim`):

| | Classic | Extended |
|---|---|---|
| Rules | Exactly the original, quirks included | Classic plus named rule flags |
| Players | 1 or 2 on one screen | 1–4 on one screen in two teams, and network games |
| Screen | 640 × 480, the original's window layout | Fills the window, wider view, 2 × 2 split for four |
| Menus | The original's, in 320 × 200 | Its own look (dark, sand accent), mouse or keys |
| Controls | The original's keys, modes and joysticks | Keyboard and mouse, twin-stick gamepads, touch |

- **Choosing:** a screen at the first start, `--classic` / `--extended` on the command line, "Switch
  to Classic" on Extended's home screen and "Switch to Extended" in Classic's main menu (a deliberate
  addition to the original's menu, [parity.md](../parity.md#intentional-deviations)). The choice is
  kept in `settings.json`.
- **Extended's menu** (`ui.compose.extended`): home with campaign (the reached codes with each
  mission's world, size and briefing), local game (first the choice: against each other or together
  against the computer, then who plays, then a map with rounds, laps and the losers tank, or a
  mission; after a game the list comes back with the same players, and against each other the
  rounds each side has won over every map count as a series: the losers tank goes by it; each map
  shows its record in the series, matches played and matches and rounds won per team, and each
  campaign mission its record kept across restarts, played, won and lost, alone and together apart;
  a mission played both ways shows the other way's record under its own), network game
  ([ADR 0024](0024-networking.md)), replays, map editor, settings on one page, help, quit. The
  controls setup, the mission texts and the first-start choice have Extended's look; the tank
  selection keeps the original's arena on purpose.
- **Replays:** every recorded match, newest first, with mission, mode, result, length, date and rules;
  naming and deleting; a viewer with pause, ¼× to 8× speed and a free camera (moved by keys or
  dragging, zoomed ¼× to 4× by the wheel or Page Up/Down).
- **Extended's presentation** (each a setting, on by default): the picture fills the window; on a
  wide window the virtual screen grows to up to 1280 legacy pixels across, so the map windows show
  more of the map; **smooth motion** draws vehicles, shots and cameras between the 33 steps a second,
  one step (30 ms) late, snapping on jumps (`StepInterpolation`).
- **Rule changes are flags** of `RuleFlags`, each named and documented, never ad-hoc `if`s. Classic has
  every flag off. Extended has six:
  - `stackedComputerVehiclesSurvive`: computer vehicles that overlap at the start do not destroy each
    other;
  - `oneVehiclePerPlayer`: in player vs player each player gets one vehicle, not one per pad;
  - `teams`: up to four players as two teams ([ADR 0027](0027-four-players-two-teams.md));
  - `pointerMarker`: the rocket marker follows the mouse ([ADR 0029](0029-extended-controls.md)).
  - `shortRockets`: a rocket gives up after 1200 px (about 8 s) instead of 3300 px (about 24 s);
  - `sheltersStopRockets`: a vehicle on a shelter tile takes no damage from a rocket.
- **A rules version per ruleset:** `GAME_RULES_VERSION` (Classic) and `EXTENDED_RULES_VERSION`. A
  Classic change bumps both; an Extended-only change bumps only Extended. Recordings store the
  ruleset (older ones read as Classic).

## Consequences

- Parity work, the DOSBox comparison and the recordings target Classic.
- Network play, four players, mouse and twin-stick controls and the replay viewer exist only in
  Extended; Classic stays a museum piece that is easy to verify.
- Extended's keys help is made from the current settings; the original's `KEYS.HLP` describes
  Classic.
