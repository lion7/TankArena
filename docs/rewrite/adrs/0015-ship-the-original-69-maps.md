# ADR 0015: Ship the 69 Maps of the Original Release

- **Status:** Accepted (2026-10-18, commit `faff4d6`).

## Context

The repository's `MAPS/` held 122 `.MAP` files, but the original release (the user's install, run in
DOSBox) ships only 69 of them. The other 53 (MISSION1–4, BASE1/2, PLANES1/2, ASSUALT1–7, CAR WAR,
TRAVEL, CITY B/IN, ENEMY B, MASTER*, LAVA*, DOGF*, 3 VS 3, …) were development or unreleased maps.
They cannot be compared with the original, some never worked, and they made the mission lists and
code chains differ from what players of the original know.

## Decision

- The game offers exactly the 69 original maps, including `SELECT.MAP` (the tank selection arena).
- The 53 extra maps live in `MAPS/extra/` with a README. `tools-mapconv scene-all` reads only the top
  level of `MAPS/`, so they are not converted. Moving one back and re-running `scene-all` makes it
  playable again.
- Parity is judged against these 69 maps only.

## Consequences

- Mission codes, known-code lists and player-vs-player map lists match the original.
- `RACE3` (300×52 tiles) had to convert: the header check now limits the cell count to 200×200 like
  the original's `init.c`, not each side.
- Tests that used extra maps moved to original ones (WAR, NUKE2, BAY) or to fixtures.
