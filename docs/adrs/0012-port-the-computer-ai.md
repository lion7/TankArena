# ADR 0012: Port the Original Computer AI

- **Status:** Accepted (2026-10-18). Replaces the A* / line-of-sight / waypoint-follower AI of
  2026-05.

## Context

The 2026-05 AI used A* over the solid layer, Bresenham line of sight and a waypoint follower. It
played differently from the original: computer tanks routed perfectly around buildings, never
panicked after a crash, ignored the per-tank attack weights and waves stored in the maps, and could
not be tuned by the map author's fields.

The original's `control_computer_tank()` (`original/src/tanks/computer.c`) is a fuzzy-logic
controller with no path finding: it blends dangers (objects, buildings, pits) and wants (target,
waypoints, faster terrain) into steering, picks a new target every 100 + rnd(100) ticks weighted by
`attack_tanks` and `attack_goals`, follows up to ten waypoints, attacks in waves (`wave_delay`), and
panics after crashing into something that is not its target.

## Decision

- `ComputerTankAi` (`game-sim/src/nl/tankarena/sim/ComputerTankAi.kt`) ports `computer.c` rule by
  rule, including the fire delay (`computer.fire_delay`, `original/src/tanks/fire.c:171`).
- The importer reads every AI field the original stores (`attack_at`, `attack_goals`, `wave_delay`,
  `fire_delay`, the waypoint mode and all ten slots, the activation fields), and the editor edits them.
- The A* path finder, line of sight and waypoint follower were deleted.

## Consequences

- Computer tanks behave like the original's, including their weaknesses: they can get stuck, they
  never brake, they do not lead their shots.
- AI behaviour depends on per-map fields, so import fidelity matters for AI parity.
