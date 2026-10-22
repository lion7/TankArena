# Tank Arena Rewrite — History

How the rewrite got where it is. Nothing here describes the current state; for that see
[`full-rewrite-status.md`](full-rewrite-status.md). Deleted documents and ADRs are still in git
history.

## Timeline

| When | What |
|---|---|
| 2026-04 | Engine survey (recommended KorGE); Compose with the Kubriko game engine chosen instead. Eleven-module plan, collapsed to six modules on 2026-04-25/26 with an authoritative headless server and the engine's scene JSON as map format. Gradle replaced by Amper. |
| 2026-05 | 100 Hz tick ([ADR 0002](adrs/0002-fixed-step-100hz.md)). Parity tasks T01–T32 implemented from descriptions. |
| 2026-09-25 | Code-verified audit: the game as played was about 20–25 % of parity. Shipped maps made playable end to end; split screen, real audio. |
| 2026-10-04 to 06 | Direction change: port the original's rules from the C source ([ADR 0010](adrs/0010-port-the-original-rules.md)): vehicle table, structures and pixel collisions ([0011](adrs/0011-structure-grid-and-pixel-masks.md)), aircraft, all weapons, bonuses, soldiers, trains, locks, destroyers, enforcers, presentation, match recordings. |
| 2026-10-07 | B52 and zeppelin, force fields, ramps, pilot faces, night maps; Amper replaced by the Kotlin toolchain ([0016](adrs/0016-kotlin-toolchain-build.md)). |
| 2026-10-18 | Original screen layout and fonts ([0013](adrs/0013-original-presentation.md)); simulation thread ([0014](adrs/0014-simulation-thread-and-recordings.md)); computer AI ported ([0012](adrs/0012-port-the-computer-ai.md)); player-vs-player rounds, flags, races; signed goal layer; only the 69 original maps ([0015](adrs/0015-ship-the-original-69-maps.md)); intro, options, music. |
| 2026-10-19 | Documentation rebuilt; direction decided (ADRs 0019–0027) and a roadmap of nine phases written. Phases 0–2 done the same day: CI, the game engine removed, the native map format. The legacy step (rules v12). Packaging ([0017](adrs/0017-desktop-packaging.md)). |
| 2026-10-20 | Phase 3 (Classic parity, rules v14) finished; most of phase 4 (editor, `.MAP` export), phase 5 (Extended menu and presentation), phase 6 (input, gamepads, four players), phase 7 (Android) and phase 8 (network play, dedicated server, relay). |
| 2026-10-21 | Extended controls: keyboard and mouse, twin sticks ([0029](adrs/0029-extended-controls.md)). Extended named "Next Kombat"; the original's files moved to `original/`. Android packaging script. |
| 2026-10-22 | Docs flattened into `docs/` and `original/docs/`. The relay image on GHCR and the public relay at `wss://relay.tankarena.nl`; the website at www.tankarena.nl. The `rewrite` branch merged (PR #3); the default branch renamed `main`. Documentation audited against the code. |

## The roadmap of 2026-10-19

The roadmap written on 2026-10-19 had nine phases. Phases 0–2 were sequential; 3–8 ran side by side
in one checkout. What each delivered:

- **Phase 0, safety net:** CI on GitHub Actions (build and all tests on every push, a nightly idle
  scan and tick benchmark); `ShippedMapsBootTest` (every map in every allowed mode); reference renders;
  six recordings (BEGIN1 ×2, BEGIN2 won and lost, BEGIN3, COMBAT1 lost, commit `9f816d5`). The
  legacy step and the rest of phase 3 made those recordings obsolete; they are kept in
  `recordings/v11/` as input for the reference renders.
- **Phase 1, plain-Kotlin core** ([ADR 0019](adrs/0019-plain-kotlin-no-game-engine.md)): the engine
  removed from every module with every recording and reference render unchanged; `MatchObjectList`
  updates objects in the engine's former order; `Match` split into ten `Match*.kt` files; all
  trigonometry through `DetMath`; `SimulationLoop` moved into the simulation; the game's own renderer
  (`RenderScene`, `PictureCache`) drew the reference renders byte for byte. Removed with it: the
  separate editor app, the slide manager, the serialization registry, the preload manager and the
  protocol leftovers (`ReplayProtocol`, `PlayerInput.kt`, unused ids and events, `WallState`).
- **Phase 2, native maps** ([ADR 0022](adrs/0022-native-map-format.md)): the `.tamap` reader and
  writer, `import` and `import-all`, the 69 maps converted; the scene and sidecar files,
  `ShippedScenes`, `LegacyToSceneJson`, the `scene` commands and `Wall` deleted.
- **Phase 3, Classic parity** (rules v12 to v14): the legacy step; heavy mines, mine trigger and
  visibility, the growing mortar blast, rocket targets and exhaust, rockets and mines that can be hit,
  warps, wrecks and respawns, map wrap, fuel, the AI's fire delay, the radar, F-keys in two-vs-computer,
  the step order; supermarket mayhem, goal `who = 3`, conveyors and turntables, debris and fuel-dump
  flames, ground effects and their sounds, ricochets, sound distance from the vehicle; known and last
  codes, in-game keys, the controls setup, command-line cheats, "EXIT TO DOS? (Y/N)", the static view.
- **Phase 4, in-game editor** ([ADR 0021](adrs/0021-editor-in-the-game.md)): painting, structures,
  the sparse layers, objects, settings and texts, undo, play-test, saved maps in the game's lists,
  `.MAP` export (option C). Validation, the overview and a DOSBox check of exported maps were left.
- **Phase 5, rulesets and the Extended menu** ([ADR 0023](adrs/0023-classic-and-extended-rulesets.md)):
  the ruleset choice, Extended's look and screens, replays with a free camera, the presentation
  options, the first rule flags (Extended rules v5).
- **Phase 6, input and four players** ([ADR 0026](adrs/0026-input-and-gamepads.md),
  [0027](adrs/0027-four-players-two-teams.md), [0029](adrs/0029-extended-controls.md)): the input
  layer, Jamepad gamepads as joysticks, press to join, menus with a pad, two teams of two
  (Extended v6), pads for players 3 and 4, keyboard-and-mouse and twin-stick controls (Extended v7).
- **Phase 7, Android** ([ADR 0025](adrs/0025-multiplatform-modules-and-android.md)): the module
  layout and package `nl.tankarena`; the game as `TankArenaApp` with a `GamePlatform` per app;
  `PlatformGraphics`; Android audio, MIDI, storage and pads; touch controls; app icon; less garbage
  per frame (6.3 to 3.3 MB/s on the emulator). Tried on the emulator only.
- **Phase 8, network play** ([ADR 0024](adrs/0024-networking.md),
  [0028](adrs/0028-internet-relay-and-lobby.md)): `game-net`, host and join from Extended, the LAN
  list, delta frames, several players per machine, rounds over the network, `app-server`, the relay
  and its public deployment.

What was left of each phase is in the [roadmap](roadmap.md).

## The eleven-module plan (2026-04)

`:game-core` (maths, ids, timing), `:game-content` (canonical map data), `:game-input` (intents),
`:game-sim` (authoritative world), `:game-protocol` (replay and network DTOs), `:game-legacy` (the only
module that understood `.MAP`), a render module on the engine, `:game-ui-compose`, `:app-desktop`,
`:app-editor`, `:tools-mapconv`. The collapse of 2026-04-25/26 folded them into six:
`game-protocol`, `game-content`, `game-server`, `game-client`, `game-editor` and `tools-mapconv`.
ADR 0025 replaced those with today's ten in 2026-10.

## The parity task list (2026-05, deleted 2026-10-19)

`docs/rewrite/parity-tasks.md` listed tasks T01–T43 plus TFN1/TFN2, each with goal, spec, files,
acceptance and tests, meant for autonomous agents one task at a time. T01–T06 and T08–T32 were marked
done by 2026-05-11; T07 (drop the importer) was deferred.

The 2026-09-25 audit found many "done" tasks unreachable in real play (terrain only on a test path,
destroyers turned into tanks, no waypoints, stub audio, wrong damage values). Most of T08–T32 was
later replaced by ports of the C source: A*, line of sight and waypoints (T26–T28) by
`ComputerTankAi`, box walls by `StructureGrid`, the replay plan (T36–T38) by input recordings.

## Other deleted documents

- `docs/rewrite/status-audit.md` (2026-09-25): the code-verified audit and its milestone log.
- `docs/rewrite/parity-fixtures.md`: a "first parity pool" (BEGIN1, FLAGISLE, RACE1, MISSION1,
  FLAGRACE) of 121 maps; replaced by recordings and shipped-map tests.
- `docs/COMPARISON.md` (2026-05-19, refreshed 2026-10-07): a C-versus-Kotlin comparison estimating
  55–60 % completion; outdated after the October ports.
- `docs/asset-migration-audit.md`: an asset inventory written for a KorGE port; replaced by
  [assets.md](assets.md).
- `docs/deep-research-report.md`: the 2026-04 survey of Kotlin multiplatform engines.
- `docs/rewrite/` and `original/docs/game/` were flattened into `docs/` and `original/docs/` on
  2026-10-22.

## Withdrawn decisions

ADRs deleted on 2026-10-22 because later ones replaced them; their numbers are not reused.

- **0004, the editor as a separate app** (2026-04): its own desktop program next to the game, sharing
  the content and map object types. Replaced by the editor inside the game
  ([ADR 0021](adrs/0021-editor-in-the-game.md)).
- **0006, six modules** (2026-04-25): the collapse of the eleven-module plan described above.
  Replaced by the multiplatform layout ([ADR 0025](adrs/0025-multiplatform-modules-and-android.md)).
- **0008, the engine's scene JSON plus a sidecar as the map format** (2026-04): one file of map objects
  (with a wall object per solid tile, for the engine's editor) and one of header, texts and tile
  layers; 29 MB for the 69 maps. Replaced by the native `.tamap` format
  ([ADR 0022](adrs/0022-native-map-format.md)).
- **0009, the editor on the engine's generic scene editor** (2026-04, reaffirmed and then reversed on
  2026-10-19): it could edit four object types and none of the tile layers. Replaced by
  [ADR 0021](adrs/0021-editor-in-the-game.md).

## Superseded decisions, in short

| Earlier decision | Replaced by |
|---|---|
| 50 Hz tick | 100 Hz with a game step every third tick ([ADR 0002](adrs/0002-fixed-step-100hz.md)) |
| 10 Hz snapshot keyframes for replays | Input recordings ([ADR 0014](adrs/0014-simulation-thread-and-recordings.md)) |
| Import maps once, then delete the importer | Native format as source, `.MAP` import and export kept ([ADR 0003](adrs/0003-legacy-map-import.md)) |
| One wall object per tile, sliding collisions | Structure grid with pixel masks ([ADR 0011](adrs/0011-structure-grid-and-pixel-masks.md)) |
| A*, line of sight, waypoint follower | Port of `computer.c` ([ADR 0012](adrs/0012-port-the-computer-ai.md)) |
| A game engine for the server's objects and the playfield | Plain Kotlin and the game's own renderer ([ADR 0019](adrs/0019-plain-kotlin-no-game-engine.md)) |
| A separate editor app, then a generic scene editor | Editor inside the game ([ADR 0021](adrs/0021-editor-in-the-game.md)) |
| Scene JSON plus sidecar | Native `.tamap` format ([ADR 0022](adrs/0022-native-map-format.md)) |
| Gradle, then Amper | Kotlin toolchain ([ADR 0016](adrs/0016-kotlin-toolchain-build.md)) |
| All 122 maps in `original/MAPS/` | The 69 of the original release ([ADR 0015](adrs/0015-ship-the-original-69-maps.md)) |
| A design with `World`, `MatchHost` and a picture atlas (ADR 0019 as planned) | `Match`, `SimulationLoop` and `HostedMatch` as built |
| Snapshots against an acknowledged one, cut to what is near each view (ADR 0024 as planned) | Deltas of the previous frame, whole world ([ADR 0024](adrs/0024-networking.md)) |
