# ADR 0023: Two Rulesets — Classic and Extended

- **Status:** Accepted (2026-10-19).

## Context

The rewrite has two jobs that pull in different directions:

- be a faithful reproduction of the original, checkable side by side against DOSBox
  ([ADR 0010](0010-port-the-original-rules.md), [ADR 0013](0013-original-presentation.md));
- grow beyond it: networked play, four players, gamepads, Android, a friendlier menu, replays.

Building two games would double the work and let them drift. Sprinkling "modern" changes through the
faithful code would erode the reference.

## Decision

One game core with a **ruleset** chosen at start: **Classic** or **Extended**.

| | Classic | Extended |
|---|---|---|
| Rules | Exactly the original, faithful quirks included | Classic rules plus explicit, flagged changes |
| Players | 1 or 2 on one screen | 1–4 local, and networked play |
| Teams | Good and evil as in the original | Two teams ([ADR 0027](0027-four-players-two-teams.md)) |
| Screen | 640 × 480 with the original window layout | Scalable, wider view, 2 × 2 split for four |
| Menus | The original menus, unchanged | A new, clearer menu (below) |
| Controls | The original keys and control modes | Remappable keys, gamepads, touch on Android |

- **Choosing:** a first screen (and a `--classic` / `--extended` command-line option, remembered in
  `settings.json`) picks the ruleset. The map editor is a menu entry in both
  ([ADR 0021](0021-editor-in-the-game.md)), not a separate choice.
- **Classic menu:** stays exactly the original's (main menu, game modes, codes, options, help).
- **Extended menu:** single player campaign (codes and a list of reached missions), local multiplayer
  (2–4 players, teams, mode, map), network game (host, join, LAN list), **replays** (browse, watch,
  rename, delete recorded matches, with pause, speed and free camera), map editor, settings
  (controls per device, video, audio), help.
- **Rule changes are flags** of the `Ruleset` object, each named and documented (for example
  "stacked computer tanks do not destroy each other", "wider view"), never ad-hoc `if`s. Classic has
  every flag off.
- **Versions per ruleset:** recordings store the ruleset; `GAME_RULES_VERSION` exists per ruleset, so
  an Extended-only change does not invalidate Classic recordings.

## Consequences

- Parity work, the DOSBox comparison and the recorded playthroughs target Classic. Extended may change
  behaviour, but only through flags.
- Network play, four players and gamepad-first menus exist only in Extended; Classic stays a museum
  piece that is easy to verify.
- Map files may carry Extended-only data in an optional section ([ADR 0022](0022-native-map-format.md)).
