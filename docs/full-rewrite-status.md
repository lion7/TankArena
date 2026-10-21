# Tank Arena Rewrite Status

As of **2026-10-20**, branch `rewrite`. This page states the current status only and
points to the documents that hold the detail. It is updated whenever a milestone lands.

## In one paragraph

The Kotlin rewrite plays all 69 maps of the original release in every mode, with the original's
rules ported from the C source, its screen layout, pictures, fonts, night lighting, sounds and music,
and it ships as self-contained Linux and Windows packages. Classic parity (roadmap phase 3) was finished on
2026-10-20: the original's game step and timing, every listed rule difference and missing rule,
supermarket mayhem, and the shell's keys, controls setup, cheats and exit question. What is left for
full parity is the map editor (painting the picture layers works) and joysticks. It is plain Kotlin
and Compose, with no game engine. All 324 automated tests pass, including a boot test of every map
in every mode and nine reference renders; CI runs on GitHub Actions.

The direction beyond parity was decided on 2026-10-19: plain Kotlin with no Kubriko, a readable
native map format, the editor inside the game, and two rulesets — **Classic** (the original) and
**Extended** (new menu, replays, up to four players in two teams, gamepads, networked play, Android).
The [roadmap](rewrite/roadmap.md) orders that work. Built so far: the native map format, the ruleset
choice with a first Extended menu and a replay viewer, and a first version of the editor.

## Status by area

| Area | Status | Detail |
|---|---|---|
| Rules: vehicles, weapons, structures, objects, AI | Done (rules v14); one detail unverified | [parity.md](rewrite/parity.md) |
| Modes: one player, two vs computer, player vs player (deathmatch, flags, race, rounds), supermarket mayhem | Done | [parity.md → Modes](rewrite/parity.md#modes) |
| Presentation: screen, HUD, radar, night, effects, debris | Done | [ADR 0013](rewrite/adrs/0013-original-presentation.md) |
| Audio and music | Done | [ADR 0018](rewrite/adrs/0018-audio-and-music.md) |
| Shell: intro, menus, codes, options, help, in-game keys, controls setup, cheats | Done; joysticks in phase 6 | [parity.md → Shell](rewrite/parity.md#shell-and-meta-game) |
| Map editor | Open, paint picture layers, undo, save, play-test; the rest missing | [roadmap phase 4](rewrite/roadmap.md) |
| Rulesets and Extended menu | Ruleset choice; Extended's own look: home, campaign, local game, replays (naming, deleting, free camera), settings; Extended plays like Classic | [roadmap phase 5](rewrite/roadmap.md) |
| Assets | All runtime assets converted and bundled | [assets.md](rewrite/assets.md) |
| Content | 69 maps as `.tamap` (2.3 MB), imported from `.MAP` without notes | [content-format.md](rewrite/content-format.md) |
| Tests | 324 green (server 250, client 65, content 9); recordings to be played again (rules v14); every map boots; 9 reference renders | [testing-strategy.md](rewrite/testing-strategy.md) |
| Build and packaging | Kotlin toolchain 0.13.0; Linux and Windows packages | [build.md](rewrite/build.md), [ADR 0017](rewrite/adrs/0017-desktop-packaging.md) |
| CI | GitHub Actions on every push (green), nightly idle scan and tick benchmark | [testing-strategy.md](rewrite/testing-strategy.md#continuous-integration) |

Versions: `GAME_RULES_VERSION` 14 (Classic), `EXTENDED_RULES_VERSION` 4, `PROTOCOL_VERSION` 9,
`MAP_FORMAT_VERSION` 1.

## Open decisions

None. Decided on 2026-10-19: the legacy step for both rulesets
([ADR 0002](rewrite/adrs/0002-fixed-step-100hz.md)); `.MAP` export deferred to the editor work, then
option C of [ADR 0022](rewrite/adrs/0022-native-map-format.md).

## Next steps

The [roadmap](rewrite/roadmap.md) has the full plan. Next, in order:

1. **Phase 0, safety net:** done except for recordings: the rules changed to v14, so BEGIN1–4, more
   missions and player-vs-player matches need playing (the v11 ones are in `recordings/v11`).
2. **Phases 1 to 3:** done — plain Kotlin ([ADR 0019](rewrite/adrs/0019-plain-kotlin-no-kubriko.md)),
   the native map format ([ADR 0022](rewrite/adrs/0022-native-map-format.md); `.MAP` export moves to
   phase 4) and Classic parity ([parity.md](rewrite/parity.md)).
3. **Now, side by side:** the in-game editor (phase 4: objects, the other layers, header and texts,
   new maps, `.MAP` export, validation) and the Extended menu (phase 5: the remaining screens in the new look, the presentation, the
   first Extended rules).
4. **Then:** input layer, gamepads and four players (phase 6), Android (7), networked play (8).

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
- [`game/`](../original/docs/game/) — the original game: overview, mechanics, vehicles, weapons, terrain, objects, AI,
  map format. This is the parity spec, checked against `original/src/`.
