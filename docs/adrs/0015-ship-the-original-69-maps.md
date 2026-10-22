# ADR 0015: Ship the 69 Maps of the Original Release

- **Status:** Accepted (2026-10-18).

## Context

The repository held 122 `.MAP` files, but the original release (the user's installation, run in
DOSBox) ships only 69 of them. The other 53 (MISSION1–4, BASE1/2, PLANES1/2, ASSUALT1–7, CAR WAR,
TRAVEL, CITY B/IN, ENEMY B, MASTER*, LAVA*, DOGF*, 3 VS 3, …) were development or unreleased maps.
They cannot be compared with the original, some never worked, and they made the mission lists and
code chains differ from what players of the original know.

## Decision

- The game ships exactly the 69 original maps, including `SELECT.MAP` (the tank selection arena), as
  `.tamap` files ([ADR 0022](0022-native-map-format.md)).
- The 53 extra maps live in `original/MAPS/extra/` with a README. `tools-mapconv import-all` reads only
  the top level of `original/MAPS/`, so they are not converted. Moving one up and running
  `import-all` makes it playable.
- Parity is judged against these 69 maps only.

## Consequences

- Mission codes, known-code lists and player-vs-player map lists match the original.
- `RACE3` (300 × 52 tiles) converts because the size check limits the cell count to 200 × 200, like the
  original's `init.c`, not each side.
- Tests use the original maps or small fixtures, never the extra maps.
