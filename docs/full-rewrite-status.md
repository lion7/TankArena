# Tank Arena Rewrite Status

As of **2026-10-19**, branch `rewrite` at `bd6a711`. This page states the current status only and
points to the documents that hold the detail. It is updated whenever a milestone lands.

## In one paragraph

The Kotlin rewrite plays all 69 maps of the original release in every mode, with the original's
rules ported from the C source, its screen layout, pictures, fonts, night lighting, sounds and music,
and it ships as self-contained Linux and Windows packages. What is left for full parity: the map
editor (largely missing), supermarket mayhem, conveyors and rotating platforms, debris, terrain
effects, the original's in-game keys, controls setup and command-line switches, and a list of smaller
rule differences. All 295 automated tests pass; there is no CI and no committed playthrough recording
yet.

## Status by area

| Area | Status | Detail |
|---|---|---|
| Rules: vehicles, weapons, structures, objects, AI | Done, with listed differences | [parity.md](rewrite/parity.md) |
| Modes: one player, two vs computer, player vs player (deathmatch, flags, race, rounds) | Done; supermarket mayhem partial | [parity.md → Modes](rewrite/parity.md#modes) |
| Presentation: screen, HUD, radar, night, effects | Done; some effects missing | [ADR 0013](rewrite/adrs/0013-original-presentation.md) |
| Audio and music | Done; a few sounds missing | [ADR 0018](rewrite/adrs/0018-audio-and-music.md) |
| Shell: intro, menus, codes, options, help | Done; in-game keys, controls setup, switches missing | [parity.md → Shell](rewrite/parity.md#shell-and-meta-game) |
| Map editor | Mostly missing | [parity.md → Map editor](rewrite/parity.md#map-editor) |
| Assets | All runtime assets converted and bundled | [assets.md](rewrite/assets.md) |
| Content | 69 maps imported without notes | [content-format.md](rewrite/content-format.md) |
| Tests | 295 green (server 226, client 60, content 9); no recordings committed | [testing-strategy.md](rewrite/testing-strategy.md) |
| Build and packaging | Kotlin toolchain 0.13.0; Linux and Windows packages | [build.md](rewrite/build.md), [ADR 0017](rewrite/adrs/0017-desktop-packaging.md) |
| CI | None | [testing-strategy.md](rewrite/testing-strategy.md#what-is-not-there-yet) |

Versions: `GAME_RULES_VERSION` 11, `PROTOCOL_VERSION` 7, `SCENE_SCHEMA_VERSION` 1.

## Open decisions

- **Map source of truth** — keep `MAPS/*.MAP` and the importer, or make the converted files the source
  and delete the importer ([ADR 0003](rewrite/adrs/0003-legacy-map-import.md)). To be decided together
  with the new map format of ADR 0019.
- **Kubriko leaves the server** — direction agreed, design and implementation open
  ([ADR 0019](rewrite/adrs/0019-remove-kubriko-from-the-server.md)).
- **Known-codes details** — whether public and two-player codes are saved, the per-mode filter and
  remembering the last code ([parity.md → Modes](rewrite/parity.md#modes)).

## Remaining work toward parity

In rough order of value; each item links to its detail.

1. Commit recorded playthroughs of BEGIN1–4, then more missions, and add CI
   ([testing strategy](rewrite/testing-strategy.md#what-is-not-there-yet)).
2. Rule differences found by the 2026-10-19 audit: rules the original advances per game step (one
   step per 3 timer ticks) run about 3× too fast, confirmed for rocket acceleration
   ([ADR 0002](rewrite/adrs/0002-fixed-step-100hz.md#the-originals-game-step-is-not-its-timer-tick));
   heavy mines, mine trigger and visibility, mortar blast steps, rocket targets, warps, respawn wreck,
   map wrap, AI fire delay, radar and cloak, F-key mapping in two players vs computer
   ([parity.md](rewrite/parity.md)).
3. Supermarket mayhem rules and display; goal `who = 3`.
4. Conveyors and rotating platforms; debris (fuel dumps); terrain effects and their sounds.
5. In-game keys (pause, gamma, radar, screenshot, window size); controls setup (remapping,
   control modes, gamepads); command-line switches and cheats.
6. The map editor: all object types, tile layers, header and texts, templates, validation, play-test,
   menu launch ([ADR 0009](rewrite/adrs/0009-editor-wraps-kubriko-scene-editor.md)).
7. Remove Kubriko from the server and split `ServerMatchPrototype`
   ([ADR 0019](rewrite/adrs/0019-remove-kubriko-from-the-server.md)); delete the dead code listed in
   [architecture.md](rewrite/architecture.md#known-structural-debt).

Post-parity: networked play and a WASM/web target.

## Documents

- [`rewrite/architecture.md`](rewrite/architecture.md) — modules, a match step by step, replication,
  threads, switches, debt.
- [`rewrite/parity.md`](rewrite/parity.md) — what matches the original, what does not, deviations and
  exclusions.
- [`rewrite/adrs/`](rewrite/adrs/README.md) — decisions 0001–0020.
- [`rewrite/content-format.md`](rewrite/content-format.md) — map files and the importer.
- [`rewrite/assets.md`](rewrite/assets.md) — every original asset and its converted form;
  `tools-mapconv`.
- [`rewrite/build.md`](rewrite/build.md) — building, running, packaging.
- [`rewrite/testing-strategy.md`](rewrite/testing-strategy.md) — tests that exist and the plan.
- [`rewrite/risk-register.md`](rewrite/risk-register.md) — risks and mitigations.
- [`rewrite/legacy-system-map.md`](rewrite/legacy-system-map.md) — C source to Kotlin.
- [`rewrite/history.md`](rewrite/history.md) — how the rewrite got here; deleted documents.
- [`game/`](game/) — the original game: overview, mechanics, vehicles, weapons, terrain, objects, AI,
  map format. This is the parity spec, checked against `src/`.
