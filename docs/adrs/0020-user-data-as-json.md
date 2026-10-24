# ADR 0020: Player Data as JSON in the Player's Folders

- **Status:** Accepted (2026-10-19).
- **Related:** [ADR 0023](0023-classic-and-extended-rulesets.md) (rulesets),
  [ADR 0026](0026-input-and-gamepads.md) (bindings).

## Context

The original keeps its settings in `TANK.CFG` (keys, controllers, options, last mission codes, last
map, victory count, …) and the reached mission codes in `PASSWD.DAT`, both binary files in the game
folder. A packaged game must not write into its install folder, and the binary layouts serve no
purpose for the rewrite.

## Decision

- Player data is JSON in the player's folders (`AppDirs`). On the desktop these are the XDG folders,
  falling back to `~/.config` and `~/.local/share` under `user.home` (also on Windows, as
  `%USERPROFILE%\.config\…`). Android uses the app's own storage (`filesDir/config`, and the app's
  external files folder for data).
- Config folder (`~/.config/tankarena/`):
  - `settings.json` (`GameOptionsStore`): the game options (laps, rounds, losers tank, gamma, music and
    sound volume), the victory count, the ruleset, the last code per mode and the last
    player-vs-player map, radar on or off, window size, screenshot number, each player's controls
    (keys, RELATIVE or DIRECT, keyboard or joystick, buttons, sensitivity), Extended's screen and
    control options, and the relay address;
  - `passwd.json` (`PasswordStore`, a port of `original/src/game/passwd.c`): the reached mission codes.
  - `mission-stats.json` (`MissionStatsStore`, not in the original): per way of playing and mission,
    how often it was played, won and lost (a mission left with ESC counts as played only).
- Data folder (`~/.local/share/tankarena/`): `maps/` (maps saved in the editor; exported `.MAP`
  files in `maps/export/`), `recordings/` ([ADR 0014](0014-simulation-thread-and-recordings.md)) and
  `screenshots/` (`TANK<n>.png`).
- Original `TANK.CFG` and `PASSWD.DAT` files are not read or written.
- Every read or write failure falls back to defaults; it never ends the game.
- **Known codes:** public and two-vs-computer codes are added to the list on the fly and never saved
  (the original writes them into `PASSWD.DAT`; the list is the same). **Classic** lists every known
  code in every mode, like the original (a code for the other mode is accepted and then refused with
  "THIS MAP IS NOT SUITABLE"); **Extended** lists only the codes that work in the current mode.

## Consequences

- An intentional deviation from the original, listed in
  [parity.md](../parity.md#intentional-deviations).
- On Windows the folders are not the usual `%APPDATA%`; moving them is an open cleanup
  ([roadmap](../roadmap.md)).
