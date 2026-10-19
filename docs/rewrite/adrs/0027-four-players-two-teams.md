# ADR 0027: Four Players Means Two Teams of Two

- **Status:** Accepted (2026-10-19); not implemented. Extended ruleset only.

## Context

The original's world has two sides (good and evil) and two players (`contr.num` 0 and 1). Start pads,
flags, flag bases, turret `shootat`, enforcers, goals, the goal counters, the HUD and the opponent
arrow all assume that. Four players in a free-for-all would need player-numbered pads, new win rules
and new maps.

## Decision

- Up to four players play as **two teams of up to two**. There is no free-for-all.
- Team A takes the role of the original's player one (pads, flag, base, `shootat` 3), team B that of
  player two. Existing two-player maps therefore work for 2 vs 2 without changes: both players of a
  team spawn from that team's pads (each with their own vehicle and lives).
- Missions against the computer (single and two-vs-computer in the original) allow up to four players
  on the good side.
- Each player has their own view (2 × 2 split screen locally), HUD and controls; the opponent arrow
  points at the nearest opposing player.
- Map files may add extra pads per team in the `extended` section ([ADR 0022](0022-native-map-format.md));
  without them, team members share their team's pads.

## Consequences

- No new map rules are needed to play four players on the existing maps.
- Network play ([ADR 0024](0024-networking.md)) carries a player id and a team id per input and view.
- Rounds, wins, flag hunts and races are scored per team.
