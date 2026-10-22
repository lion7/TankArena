# ADR 0027: Four Players Means Two Teams of Two

- **Status:** Accepted (2026-10-19; built 2026-10-20). Extended only (`RuleFlags.teams`).

## Context

The original's world has two sides (good and evil) and two players (`contr.num` 0 and 1). Start pads,
flags, flag bases, turret `shootat`, enforcers, goals, the goal counters, the status display and the
opponent arrow all assume that. Four players in a free-for-all would need player-numbered pads, new
win rules and new maps.

## Decision

- Up to four players play as **two teams of up to two**. There is no free-for-all.
- Players 0 and 2 take the role of the original's player one (pads, flag, base, turrets that spare
  player one), players 1 and 3 that of player two. Existing two-player maps work for 2 vs 2 unchanged.
- **Player vs player:** each player starts on the first free pad of their side; team mates sharing a
  pad start on the nearest free tile (`Match.spreadPlayers`). Each has their own vehicle and lives; a
  side is out of lives when all its players are. Rounds, wins, flag hunts and races are scored per
  side.
- **Against the computer:** up to four players on the good side; a player without a map vehicle of
  their own drives a copy of a team mate's.
- **Screens:** each player has their own view (2 × 2 split screen locally), status display and
  controls; the tank selection shows four players in their sides' colours; the opponent arrow points
  at the nearest player of the other side.
- **Map files** may add start pads for players 3 and 4: tank records with `padPlayer` 2 or 3, made in
  the editor ("Tank P3" … "Race P4"). A player starts on their own pad first, then their side's.
  Classic and the `.MAP` export leave these pads out ([ADR 0022](0022-native-map-format.md)).

## Consequences

- No new map rules are needed to play four players on the existing maps.
- Recordings keep the number of players; network play gives each player a place and a side
  ([ADR 0024](0024-networking.md)).
