# Tank Arena Rewrite Status

As of **2026-10-19**, branch `rewrite` at `bd6a711` (code) plus the documentation and recordings of
branch `docs/rewrite-docs`. This page states the current status only and
points to the documents that hold the detail. It is updated whenever a milestone lands.

## In one paragraph

The Kotlin rewrite plays all 69 maps of the original release in every mode, with the original's
rules ported from the C source, its screen layout, pictures, fonts, night lighting, sounds and music,
and it ships as self-contained Linux and Windows packages. What is left for full parity: the map
editor (largely missing), supermarket mayhem, conveyors and rotating platforms, debris, terrain
effects, the original's in-game keys, controls setup and command-line switches, and a list of smaller
rule differences. Since 2026-10-19 it is plain Kotlin and Compose, with no game engine. All 291
automated tests pass, including six recorded playthroughs, a boot test of
every map in every mode and eight reference renders; CI is set up on GitHub Actions.

The direction beyond parity was decided on 2026-10-19: plain Kotlin with no Kubriko, a readable
native map format, the editor inside the game, and two rulesets — **Classic** (the original) and
**Extended** (new menu, replays, up to four players in two teams, gamepads, networked play, Android).
The [roadmap](rewrite/roadmap.md) orders that work.

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
| Content | 69 maps as `.tamap` (2.3 MB), imported from `.MAP` without notes | [content-format.md](rewrite/content-format.md) |
| Tests | 291 green (server 220, client 62, content 9); 6 recordings; every map boots; 8 reference renders | [testing-strategy.md](rewrite/testing-strategy.md) |
| Build and packaging | Kotlin toolchain 0.13.0; Linux and Windows packages | [build.md](rewrite/build.md), [ADR 0017](rewrite/adrs/0017-desktop-packaging.md) |
| CI | GitHub Actions on every push (green), nightly idle scan and tick benchmark | [testing-strategy.md](rewrite/testing-strategy.md#continuous-integration) |

Versions: `GAME_RULES_VERSION` 11, `PROTOCOL_VERSION` 8, `MAP_FORMAT_VERSION` 1.

## Open decisions

None at the moment. The last ones were decided on 2026-10-19: known-codes behaviour per ruleset
([ADR 0020](rewrite/adrs/0020-user-data-as-json.md)) and the native map format's container and layer
encoding ([ADR 0022](rewrite/adrs/0022-native-map-format.md)); field-level details of the format are
settled while phase 2 of the roadmap is built.

## Next steps

The [roadmap](rewrite/roadmap.md) has the full plan. Next, in order:

1. **Phase 0, safety net:** done except for recordings: re-record BEGIN4 under rules v11 and record
   more missions and player-vs-player matches.
2. **Phase 1, plain-Kotlin core:** done on 2026-10-19 — no module uses Kubriko any more, the game
   draws itself, and every recording and reference render is unchanged
   ([ADR 0019](rewrite/adrs/0019-plain-kotlin-no-kubriko.md)).
3. **Phase 2, native map format:** the 69 maps are readable `.tamap` files and the game loads only
   those; `.MAP` export is open ([ADR 0022](rewrite/adrs/0022-native-map-format.md)).
4. Then, side by side: Classic parity (starting with rules that run per legacy step, about 3× too
   fast today), the in-game editor, and the Classic/Extended rulesets.

## Documents

- [`rewrite/architecture.md`](rewrite/architecture.md) — modules, a match step by step, replication,
  threads, switches, debt.
- [`rewrite/parity.md`](rewrite/parity.md) — what matches the original, what does not, deviations and
  exclusions.
- [`rewrite/roadmap.md`](rewrite/roadmap.md) — phases from today to the Classic and Extended goals.
- [`rewrite/adrs/`](rewrite/adrs/README.md) — decisions 0001–0027.
- [`rewrite/content-format.md`](rewrite/content-format.md) — the `.tamap` map format and the importer.
- [`rewrite/assets.md`](rewrite/assets.md) — every original asset and its converted form;
  `tools-mapconv`.
- [`rewrite/build.md`](rewrite/build.md) — building, running, packaging.
- [`rewrite/testing-strategy.md`](rewrite/testing-strategy.md) — tests that exist and the plan.
- [`rewrite/risk-register.md`](rewrite/risk-register.md) — risks and mitigations.
- [`rewrite/legacy-system-map.md`](rewrite/legacy-system-map.md) — C source to Kotlin.
- [`rewrite/history.md`](rewrite/history.md) — how the rewrite got here; deleted documents.
- [`game/`](game/) — the original game: overview, mechanics, vehicles, weapons, terrain, objects, AI,
  map format. This is the parity spec, checked against `src/`.
