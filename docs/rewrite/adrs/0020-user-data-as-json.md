# ADR 0020: Player Data as JSON in the User's Folders

- **Status:** Accepted (2026-10-06 known codes; 2026-10-18 options; confirmed 2026-10-19; known-codes
  behaviour per ruleset decided 2026-10-19).
- **Related:** on Android the same files live in app-private storage
  ([ADR 0025](0025-multiplatform-modules-and-android.md)); key and gamepad bindings join them
  ([ADR 0026](0026-input-and-gamepads.md)), and so does the chosen ruleset.

## Context

The original keeps its settings in `TANK.CFG` (keys, controllers, options, last mission codes, last
map, chosen tanks, victory count, …) and the reached mission codes in `PASSWD.DAT`, both binary files
in the game folder. A packaged desktop game must not write into its install folder, and the binary
layouts serve no purpose for the rewrite.

## Decision

- Player data is JSON in the XDG folders. Without `XDG_*` variables the paths fall back to
  `~/.config` and `~/.local/share` under `user.home`, on Windows too (`%USERPROFILE%\.config\…`);
  using `%APPDATA%` on Windows is an open cleanup.
  - `$XDG_CONFIG_HOME/tankarena/settings.json` (default `~/.config/tankarena/`): the game options
    (laps, rounds, losers tank, gamma, music and sound volume) and the victory count
    (`GameOptionsStore`);
  - `$XDG_CONFIG_HOME/tankarena/passwd.json`: the reached mission codes (`PasswordStore`, port of
    `src/game/passwd.c`);
  - `$XDG_DATA_HOME/tankarena/recordings/` (default `~/.local/share/`): match recordings
    ([ADR 0014](0014-simulation-thread-and-recordings.md)).
- Original `TANK.CFG` and `PASSWD.DAT` files are not read or written.
- Every read or write failure degrades to defaults; it never ends the game.

## Consequences

- This is an intentional deviation from the original (listed in
  [parity.md](../parity.md#intentional-deviations)).
- Settings that the original keeps in `TANK.CFG` but the rewrite does not have yet (key bindings,
  control modes, joysticks, last codes and map, radar toggle, window size) belong in `settings.json`
  when they are built; they are part of parity.
- **Known codes** (decided 2026-10-19), compared with the original's `listcod.c`, `passwd.c` and
  `settings.c`:
  - *Public and two-vs-computer codes:* the original writes them into `PASSWD.DAT` when the list is
    opened; the rewrite adds them to the list on the fly and never saves them. Same list, so this stays
    in both rulesets.
  - *Which codes the list shows:* **Classic** shows every known code in every mode, like the original
    (a code for the other mode is accepted and then rejected with "THIS MAP IS NOT SUITABLE");
    **Extended** shows only codes that work in the current mode.
  - *Last code and map:* both rulesets remember the last code reached per mode, and the last
    player-vs-player map, in `settings.json`, and pre-fill the prompt with it after a restart, as the
    original does with `TANK.CFG` (`lastCodes` in `settings.json`). Built 2026-10-19, together with
    the per-ruleset list.
