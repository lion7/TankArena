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
The [roadmap](roadmap.md) orders that work. Built so far: the native map format, the ruleset
choice with a first Extended menu and a replay viewer, and a first version of the editor.

## Status by area

| Area | Status | Detail |
|---|---|---|
| Rules: vehicles, weapons, structures, objects, AI | Done (rules v14); one detail unverified | [parity.md](parity.md) |
| Modes: one player, two vs computer, player vs player (deathmatch, flags, race, rounds), supermarket mayhem | Done | [parity.md → Modes](parity.md#modes) |
| Presentation: screen, HUD, radar, night, effects, debris | Done | [ADR 0013](adrs/0013-original-presentation.md) |
| Audio and music | Done | [ADR 0018](adrs/0018-audio-and-music.md) |
| Shell: intro, menus, codes, options, help, in-game keys, controls setup, cheats | Done; joysticks in phase 6 | [parity.md → Shell](parity.md#shell-and-meta-game) |
| Map editor | Open, paint picture layers, undo, save, play-test; the rest missing | [roadmap phase 4](roadmap.md) |
| Rulesets and Extended menu | Ruleset choice; Extended's own look: home, campaign, local game, replays (naming, deleting, free camera), settings; Extended plays like Classic | [roadmap phase 5](roadmap.md) |
| Assets | All runtime assets converted and bundled | [assets.md](assets.md) |
| Content | 69 maps as `.tamap` (2.3 MB), imported from `.MAP` without notes | [content-format.md](content-format.md) |
| Tests | 324 green (server 250, client 65, content 9); recordings to be played again (rules v14); every map boots; 9 reference renders | [testing-strategy.md](testing-strategy.md) |
| Build and packaging | Kotlin toolchain 0.13.0; Linux and Windows packages | [build.md](build.md), [ADR 0017](adrs/0017-desktop-packaging.md) |
| CI | GitHub Actions on every push (green), nightly idle scan and tick benchmark | [testing-strategy.md](testing-strategy.md#continuous-integration) |

Versions: `GAME_RULES_VERSION` 14 (Classic), `EXTENDED_RULES_VERSION` 4, `PROTOCOL_VERSION` 9,
`MAP_FORMAT_VERSION` 1.

## Open decisions

None. Decided on 2026-10-19: the legacy step for both rulesets
([ADR 0002](adrs/0002-fixed-step-100hz.md)); `.MAP` export deferred to the editor work, then
option C of [ADR 0022](adrs/0022-native-map-format.md).

## Next steps

The [roadmap](roadmap.md) has the full plan. Next, in order:

1. **Phase 0, safety net:** done except for recordings: the rules changed to v14, so BEGIN1–4, more
   missions and player-vs-player matches need playing (the v11 ones are in `recordings/v11`).
2. **Phases 1 to 3:** done — plain Kotlin ([ADR 0019](adrs/0019-plain-kotlin-no-kubriko.md)),
   the native map format ([ADR 0022](adrs/0022-native-map-format.md); `.MAP` export moves to
   phase 4) and Classic parity ([parity.md](parity.md)).
3. **Now, side by side:** the in-game editor (phase 4: objects, the other layers, header and texts,
   new maps, `.MAP` export, validation) and the Extended menu (phase 5: the remaining screens in the new look, the presentation, the
   first Extended rules).
4. **Then:** input layer, gamepads and four players (phase 6), Android (7), networked play (8).

## Documents

- [`architecture.md`](architecture.md) — modules, a match step by step, replication,
  threads, switches, debt.
- [`parity.md`](parity.md) — what matches the original, what does not, deviations and
  exclusions.
- [`roadmap.md`](roadmap.md) — phases from today to the Classic and Extended goals.
- [`adrs/`](adrs/README.md) — decisions 0001–0027.
- [`content-format.md`](content-format.md) — the `.tamap` map format and the importer.
- [`assets.md`](assets.md) — every original asset and its converted form;
  `tools-mapconv`.
- [`build.md`](build.md) — building, running, packaging.
- [`testing-strategy.md`](testing-strategy.md) — tests that exist and the plan.
- [`risk-register.md`](risk-register.md) — risks and mitigations.
- [`legacy-system-map.md`](legacy-system-map.md) — C source to Kotlin.
- [`history.md`](history.md) — how the rewrite got here; deleted documents.
- [`original/docs/`](../original/docs/) — the original game: overview, mechanics, vehicles, weapons, terrain, objects, AI,
  map format. This is the parity spec, checked against `original/src/`.
