# Tank Arena Rewrite Status

As of **2026-10-22**, branch `main`. This page states the current status only and points to the
documents that hold the detail. It is updated whenever something lands.

## In one paragraph

The Kotlin rewrite plays all 69 maps of the original release in every mode, with the original's
rules ported from the C source, its screen layout, pictures, fonts, night lighting, sounds and music,
and an in-game map editor that exports the original's `.MAP` files. It is plain Kotlin and Compose
Multiplatform with no game engine; every runtime asset is converted from the original's files and
bundled. Two rulesets share one core: **Classic** is the original as it was, and **Extended** (shown
as *Tank Arena: Next Kombat*) adds its own menu, replays, keyboard-and-mouse and twin-stick controls,
up to four players in two teams, network play over the LAN and through a public relay, and an Android
app. Self-contained packages build for Linux, Windows and Android. All 401 automated tests pass.

## Status by area

| Area | Status | Detail |
|---|---|---|
| Rules: vehicles, weapons, terrain, structures, objects, AI | Done (Classic rules v16); conveyors carry too few kinds of object; one detail unverified | [parity.md](parity.md) |
| Modes: one player, two vs computer, player vs player (deathmatch, flags, race, rounds), supermarket mayhem | Done | [parity.md → Modes](parity.md#modes) |
| Presentation: screen, status display, radar, night, effects, debris | Done | [ADR 0013](adrs/0013-original-presentation.md) |
| Audio and music | Done | [ADR 0018](adrs/0018-audio-and-music.md) |
| Shell: intro, menus, codes, options, help, in-game keys, controls setup, joysticks, cheats | Done | [parity.md → Shell](parity.md#shell-and-meta-game) |
| Map editor | Everything the original's can do, plus undo, play-test and `.MAP` export; validation, overview and in-game import open | [ADR 0021](adrs/0021-editor-in-the-game.md) |
| Extended menu, replays, presentation | Done; six rule flags (Extended rules v10) | [ADR 0023](adrs/0023-classic-and-extended-rulesets.md) |
| Controls: input layer, gamepads, mouse, touch | Done | [ADR 0026](adrs/0026-input-and-gamepads.md), [ADR 0029](adrs/0029-extended-controls.md) |
| Four players in two teams | Done (Extended) | [ADR 0027](adrs/0027-four-players-two-teams.md) |
| Network play: LAN, dedicated server, relay | Built and tested; not yet tried between two real machines | [ADR 0024](adrs/0024-networking.md), [ADR 0028](adrs/0028-internet-relay-and-lobby.md) |
| Android | Runs on the emulator; release signing and a real phone open | [ADR 0025](adrs/0025-multiplatform-modules-and-android.md) |
| Assets | All runtime assets converted and bundled; sounds and backgrounds copied, not yet extracted by a tool | [assets.md](assets.md) |
| Maps | 69 maps as `.tamap` (2.7 MB); every `.MAP` imports and exports byte for byte | [content-format.md](content-format.md) |
| Tests | 401 green; no current recordings (the six v11 ones are obsolete); 143 map boots; 9 reference renders | [testing-strategy.md](testing-strategy.md) |
| Build, packages, CI | Kotlin toolchain 0.13.0; Linux, Windows and Android packages (not published yet); GitHub Actions | [build.md](build.md), [ADR 0017](adrs/0017-desktop-packaging.md) |
| Deployment | Relay at `wss://relay.tankarena.nl`; website www.tankarena.nl | [build.md → Relay image](build.md#relay-image) |

Versions: `GAME_RULES_VERSION` 14 (Classic), `EXTENDED_RULES_VERSION` 7, `PROTOCOL_VERSION` 9,
`NET_VERSION` 5, `MAP_FORMAT_VERSION` 1 ([architecture](architecture.md#versions)).

## Open

The [roadmap](roadmap.md) holds every open item. The most important:

1. **New recordings** under rules v16: without them no whole-mission regression test runs.
2. **Conveyors** must carry mines, bonuses, flags and other small objects, as the original's do.
3. **Exported maps tried in DOSBox**, and editor validation and overview.
4. **Network play between two real machines**, and the Android release (signing, a real phone).
5. **Publishing the packages** on the website.

No decisions are pending.

## Documents

- [`architecture.md`](architecture.md) — modules, a match step by step, replication, threads,
  versions, switches, debt.
- [`adrs/`](adrs/README.md) — the decisions, 0001–0029.
- [`parity.md`](parity.md) — what matches the original, what does not, deviations and exclusions.
- [`roadmap.md`](roadmap.md) — what is left.
- [`content-format.md`](content-format.md) — the `.tamap` map format and the converters.
- [`assets.md`](assets.md) — every original asset and its converted form; `tools-mapconv`.
- [`build.md`](build.md) — building, running, packaging, the relay image.
- [`testing-strategy.md`](testing-strategy.md) — tests that exist and what is missing.
- [`editor-test-checklist.md`](editor-test-checklist.md) — manual checks of the editor and of
  exported maps in DOSBox.
- [`performance.md`](performance.md) — measuring speed and memory; what was fixed and what is left.
- [`risk-register.md`](risk-register.md) — risks and mitigations.
- [`legacy-system-map.md`](legacy-system-map.md) — the C source file by file, and where it lives in
  Kotlin.
- [`history.md`](history.md) — how the rewrite got here; deleted documents and withdrawn ADRs.
- [`original/docs/`](../original/docs/) — the original game: overview, mechanics, vehicles, weapons,
  terrain, objects, AI, map format. The parity spec, checked against `original/src/`.
