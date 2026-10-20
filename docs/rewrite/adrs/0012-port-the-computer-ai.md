# ADR 0012: Port the Original Computer AI

- **Status:** Accepted (2026-10-18, commit `6f2ea97`).
- **Supersedes:** the A* / line-of-sight / waypoint-follower AI of 2026-05 (tasks T26–T28).

## Context

The 2026-05 AI used `TilePathfinder` (A* over the solid layer), `LineOfSight` (Bresenham) and a
`WaypointFollower`, with an `AiModeDispatcher` that turned out to have no callers. It played
differently from the original: computer tanks routed perfectly around buildings, never panicked after
a crash, ignored the per-tank attack weights and waves stored in the maps, and could not be tuned by
the map author's fields.

The original's `control_computer_tank()` (`src/tanks/computer.c`) is a fuzzy-logic controller with
no path finding: it blends dangers (objects, buildings, pits) and wants (target, waypoints, faster
terrain) into steering, picks a new target every 100 + rnd(100) ticks weighted by `attack_tanks` and
`attack_goals`, follows up to ten waypoints, attacks in waves (`wave_delay`), and panics after
crashing into something that is not its target.

## Decision

- `ComputerTankAi` (`game-sim/.../sim/ComputerTankAi.kt`) ports `computer.c` rule by rule.
- The importer reads every AI field the original stores (`attack_at`, `attack_goals`, `wave_delay`,
  waypoint mode and all ten slots, activation fields) and the scenes were regenerated.
- The A* path finder, line of sight, waypoint follower and mode dispatcher were deleted.

## Consequences

- Computer tanks behave like the original's, including their weaknesses (they can get stuck, they
  never brake, they do not lead their shots).
- AI behaviour depends on per-map fields, so import fidelity matters for AI parity.
- Open parity points: `computer.fire_delay` is not imported yet, so computer tanks refire at
  `weap.speed` instead (see [parity.md](../parity.md#ai)).
