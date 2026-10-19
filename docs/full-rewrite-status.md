# Tank Arena Rewrite Status

As of **2026-10-19**, branch `rewrite` at `bd6a711` (code) plus the documentation and recordings of
branch `docs/rewrite-docs`. This page states the current status only and
points to the documents that hold the detail. It is updated whenever a milestone lands.

## In one paragraph

The Kotlin rewrite plays all 69 maps of the original release in every mode, with the original's
rules ported from the C source, its screen layout, pictures, fonts, night lighting, sounds and music,
and it ships as self-contained Linux and Windows packages. What is left for full parity: the
original's timing (one game step every third timer tick, see Open decisions), most of the map editor
(painting the picture layers works), supermarket mayhem, conveyors and rotating platforms, debris, terrain
effects, the original's in-game keys, controls setup and command-line switches, and a list of smaller
rule differences. Since 2026-10-19 it is plain Kotlin and Compose, with no game engine. All 302
automated tests pass, including six recorded playthroughs, a boot test of
every map in every mode and eight reference renders; CI is set up on GitHub Actions.

The direction beyond parity was decided on 2026-10-19: plain Kotlin with no Kubriko, a readable
native map format, the editor inside the game, and two rulesets — **Classic** (the original) and
**Extended** (new menu, replays, up to four players in two teams, gamepads, networked play, Android).
The [roadmap](rewrite/roadmap.md) orders that work. Built so far: the native map format, the ruleset
choice with a first Extended menu and a replay viewer, and a first version of the editor.

## Status by area

| Area | Status | Detail |
|---|---|---|
| Rules: vehicles, weapons, structures, objects, AI | Done, with listed differences | [parity.md](rewrite/parity.md) |
| Modes: one player, two vs computer, player vs player (deathmatch, flags, race, rounds) | Done; supermarket mayhem partial | [parity.md → Modes](rewrite/parity.md#modes) |
| Presentation: screen, HUD, radar, night, effects | Done; some effects missing | [ADR 0013](rewrite/adrs/0013-original-presentation.md) |
| Audio and music | Done; a few sounds missing | [ADR 0018](rewrite/adrs/0018-audio-and-music.md) |
| Shell: intro, menus, codes, options, help | Done; in-game keys, controls setup, switches missing | [parity.md → Shell](rewrite/parity.md#shell-and-meta-game) |
| Map editor | Open, paint picture layers, undo, save, play-test; the rest missing | [roadmap phase 4](rewrite/roadmap.md) |
| Rulesets and Extended menu | Ruleset choice, Extended menu, replay viewer; Extended plays like Classic | [roadmap phase 5](rewrite/roadmap.md) |
| Assets | All runtime assets converted and bundled | [assets.md](rewrite/assets.md) |
| Content | 69 maps as `.tamap` (2.3 MB), imported from `.MAP` without notes | [content-format.md](rewrite/content-format.md) |
| Tests | 302 green (server 225, client 68, content 9); 6 recordings; every map boots; 8 reference renders | [testing-strategy.md](rewrite/testing-strategy.md) |
| Build and packaging | Kotlin toolchain 0.13.0; Linux and Windows packages | [build.md](rewrite/build.md), [ADR 0017](rewrite/adrs/0017-desktop-packaging.md) |
| CI | GitHub Actions on every push (green), nightly idle scan and tick benchmark | [testing-strategy.md](rewrite/testing-strategy.md#continuous-integration) |

Versions: `GAME_RULES_VERSION` 11 (Classic), `EXTENDED_RULES_VERSION` 1, `PROTOCOL_VERSION` 8,
`MAP_FORMAT_VERSION` 1.

## Open decisions

- **The legacy step** (roadmap phase 3, item 1). The original runs one game step every third timer
  tick and checks its timers only then, so every delay is rounded up to a multiple of 3 ticks: a turn
  delay of 7 lasts 9 ticks, the chain gun's 10 lasts 12. The rewrite counts every tick: tanks turn
  about 29% and the chain gun about 20% faster, and a few per-step rules (rocket acceleration,
  computer steering noise, wagon spacing, B52 and dying-tank explosions) run 3× too often. Fixing it
  for Classic changes how every recording plays out. To decide: does Extended keep today's 100 Hz
  timing (then the six recordings become Extended recordings and stay as tests), or do both rulesets
  get the legacy step (then all six have to be played again)?
- **`.MAP` export:** deferred to the editor work, then option C of
  [ADR 0022](rewrite/adrs/0022-native-map-format.md) (decided 2026-10-19).

## Next steps

The [roadmap](rewrite/roadmap.md) has the full plan. Next, in order:

1. **Phase 0, safety net:** done except for recordings: re-record BEGIN4 under rules v11 and record
   more missions and player-vs-player matches.
2. **Phase 1, plain-Kotlin core:** done on 2026-10-19 — no module uses Kubriko any more, the game
   draws itself, and every recording and reference render is unchanged
   ([ADR 0019](rewrite/adrs/0019-plain-kotlin-no-kubriko.md)).
3. **Phase 2, native map format:** done except `.MAP` export, which moves to phase 4
   ([ADR 0022](rewrite/adrs/0022-native-map-format.md)).
4. **Side by side now:** Classic parity (the legacy step waits for the decision above; known codes
   done), the in-game editor (first version done; objects, other layers, header, new maps, export
   next), and the Extended menu (first version done; campaign list, multiplayer setup, replay
   management and settings next).

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
