# Roadmap

The order in which the rewrite gets from today (see [`full-rewrite-status.md`](../full-rewrite-status.md))
to the goals decided on 2026-10-19:

- a faithful **Classic** ruleset that matches the original, including its menus and editor;
- an **Extended** ruleset with a clearer menu, replays, up to four players in two teams, gamepads and
  networked play;
- plain Kotlin throughout, no Kubriko; a readable native map format; desktop, then Android.

Each phase lists its goal, the work, what it depends on and how we know it is done. The decisions
behind them are ADRs 0019–0027 ([index](adrs/README.md)). Phases 0–2 are sequential; after that,
several tracks can run side by side.

```text
0 Safety net -> 1 Plain-Kotlin core -> 2 Native maps -+-> 3 Classic parity (ongoing)
                                                      +-> 4 In-game editor
                                                      +-> 5 Rulesets + Extended menu -> 6 Input, 4 players
                                                                                    -> 7 Android
                                                                                    -> 8 Networking
```

## Phase 0 — Safety net

Goal: any behaviour change shows up in a test before it reaches a playtest.

- [x] Commit recorded playthroughs: BEGIN1 ×2, BEGIN2 won and lost, BEGIN3, COMBAT1 lost (`9f816d5`).
- [ ] Re-record BEGIN4 under rules v11; add more single-player missions, a two-vs-computer mission and
      player-vs-player matches (deathmatch, flag hunt, race).
- [x] CI on GitHub Actions (`.github/workflows/ci.yml`): build and all tests on every push and pull
      request; nightly idle scan and tick benchmark. `.gitignore` lets `.github/` through. Green on
      GitHub since 2026-10-19.
- [x] "Every shipped map boots in every allowed mode" test (`ShippedMapsBootTest`, 143 combinations).
- [x] Reference renders (`ReferenceRenderTest`): eight moments of recorded and idle matches drawn
      through the real game screen, kept as the baseline for the new renderer.

Done when: CI is green with at least eight recordings and the reference renders committed.

## Phase 1 — Plain-Kotlin core, no Kubriko ([ADR 0019](adrs/0019-plain-kotlin-no-kubriko.md))

Goal: the same game, with no Kubriko anywhere and a simulation that can run on any platform.
**Done on 2026-10-19**, with every test, recording and reference render unchanged:

- [x] Simulation and importer without Kubriko: `MatchObjectList` (objects updated in Kubriko's
      order), `SceneFile` (the same scene JSON; all 69 scenes regenerate byte for byte).
- [x] Package `com.tankarena.sim` with plain names (`Match`, `Tank`, …); `Match`'s rules split into
      ten `Match*.kt` files by subsystem.
- [x] All trigonometry through `DetMath` (`StrictMath`, identical on every JVM).
- [x] `SimulationLoop` moved into the simulation module.
- [x] The game's own renderer: `RenderScene` on a Compose `Canvas` and `PictureCache`; the reference
      renders are byte-identical.
- [x] Removed: Kubriko from every module, `:game-editor`, `TerrainSlideManager`, the serialization
      registry, `ScenePreloadManager`, the protocol leftovers (`ReplayProtocol`, `PlayerInput.kt`,
      unused ids, events and `WallState`).

Carried over:

- `MatchHost` in common code and drivers per platform: with the module layout of phase 7.
- Splitting `Tank` (1,760 lines) like `Match`: when vehicle rules are next reworked.
- The legacy step every 3rd tick: with its first rule, in phase 3.
- `Wall` and the wall entries in scene files: with the native map format, phase 2.

## Phase 2 — Native map format ([ADR 0022](adrs/0022-native-map-format.md), [ADR 0003](adrs/0003-legacy-map-import.md))

Goal: one readable file per map, and permanent `.MAP` import and export.

- [x] Map document model and `.tamap` reader/writer; legend grids, sparse cells, typed object records
      ([content-format.md](content-format.md)).
- [x] `tools-mapconv import` and `import-all`; the 69 maps in `game-content/resources/maps/` (2.3 MB).
- [x] The game, client and tests load `.tamap` (`ShippedMaps`, `Match.fromMap`); the scene and sidecar
      files, `ShippedScenes`, `LegacyToSceneJson`, the `scene` commands and `Wall` are gone. Every
      recording, reference render and boot test is unchanged.
- [x] Tests: every original `.MAP` imports without notes to exactly its committed `.tamap`; every map
      reads back to the same text and objects.
- [ ] `export` to `.MAP`, round-trip tests through `.MAP`, spot checks in DOSBox — waiting for the
      decision on how to keep the bytes the rewrite does not model (ADR 0022); needed by phase 4.

## Phase 3 — Classic parity (ongoing track)

Goal: Classic matches the original everywhere listed in [parity.md](parity.md). Each item cites the C
source, gets a test, bumps the Classic rules version when it changes play, and refreshes recordings.

1. **Legacy step:** move every per-step rule into it (rocket acceleration confirmed; audit rotors,
   effects and every `++`/`--` in control functions).
2. **Rule differences:** heavy mines, mine trigger and visibility, mortar blast steps, rocket targets,
   warps (all object kinds, no cooldown, sparks and sound), respawn leaving the wreck, map wrap, AI
   `fire_delay` (import it), radar hiding cloaked tanks, F-key mapping in two-vs-computer.
3. **Missing rules:** supermarket mayhem; goal `who = 3`; conveyors and turntables; debris and
   fuel-dump flames; terrain effects and their sounds.
4. **Shell:** remember the last code per mode and the last map across restarts; Classic's known-codes
   list shows every known code ([ADR 0020](adrs/0020-user-data-as-json.md)); in-game keys (pause, F11
   gamma, F12 radar, screenshot, window size); the original key
   setup, control modes and joysticks ([ADR 0026](adrs/0026-input-and-gamepads.md)); command-line
   switches and cheats; Exit confirmation.

Depends on: phase 1 for item 1 (cleaner with the new core); the rest can start any time.

## Phase 4 — In-game editor ([ADR 0021](adrs/0021-editor-in-the-game.md))

Goal: everything the original editor can do, inside the game, plus validation, undo and play-test.

- Editor screen from the main menu (both rulesets); map view with the game renderer, zoom and overview.
- New map from world templates (convert `DATA/MAP_0..3.DAT`) and size; picture layers with the
  structure library and links; goal, bonus and men layers.
- Every object type with its properties; links; computer-tank settings and waypoints; pad weapons.
- Header and mission texts; import and export `.MAP`; validation; undo; play-test and return.

Depends on: phases 1 and 2.

## Phase 5 — Rulesets and the Extended menu ([ADR 0023](adrs/0023-classic-and-extended-rulesets.md))

Goal: choose Classic or Extended; Extended has its own clearer menu.

- `Ruleset` object with named flags; rules version per ruleset; ruleset stored in recordings and
  settings; first-run choice and `--classic` / `--extended`.
- Extended menu: campaign (codes and list of reached missions), local multiplayer, network game
  (placeholder until phase 8), **replays** (browse, watch with pause, speed and free camera, rename,
  delete), map editor, settings (controls per device, video, audio), help.
- Extended presentation: scalable view, wider field of view on widescreen, optional smooth camera.
- First Extended flags, each documented (for example: stacked computer tanks do not destroy each
  other).

Depends on: phase 1 (renderer for the replay viewer).

## Phase 6 — Input layer, gamepads, four players ([ADR 0026](adrs/0026-input-and-gamepads.md), [ADR 0027](adrs/0027-four-players-two-teams.md))

- Devices → actions → `InputFrame` per player; bindings per device in `settings.json`.
- Gamepads on desktop through Jamepad (SDL2), several Bluetooth pads at once; "press to join".
- Menus fully usable with a gamepad in Extended.
- Four local players as two teams on existing maps; 2 × 2 split screen; per-team scoring; optional
  extra pads per team in map files.

Depends on: phase 5.

## Phase 7 — Android ([ADR 0025](adrs/0025-multiplatform-modules-and-android.md))

- Multiplatform module layout (`game-protocol`, `game-sim`, `game-content`, `game-net`, `game-ui`,
  `app-desktop`, `app-android`, `app-server`, `tools-mapconv`).
- Android services: audio through `AudioTrack`, MIDI through `MediaPlayer`, AGSL shaders, storage,
  controllers, on-screen controls for phones.
- Android packaging and signing.

Depends on: phases 1 and 6 (the move is mechanical once the core is platform-free).

## Phase 8 — Networked play ([ADR 0024](adrs/0024-networking.md))

1. LAN: listen server from the menu, join by LAN list, tick-stamped inputs, delta snapshots near each
   player's views, client interpolation; 2–4 players in two teams.
2. Dedicated headless `app-server`.
3. Internet: join by address, then a small relay/lobby server; own-vehicle prediction only if needed.

Depends on: phases 5 and 6; Android-to-desktop play after phase 7.

## Later and ideas

- Computer players filling empty slots in Extended multiplayer (`ComputerTankAi`).
- Spectating a network game; kill-cam from the recording.
- Map sharing: import maps from an old installation, a simple in-game map browser.
- Data-driven vehicles for Extended (vehicle table as a data file).
- WASM/web build.
- Gamma/scaling options and accessibility settings in Extended.
- `extract-sounds` and `extract-bitmaps` converters so every asset is generated, not copied
  ([assets.md](assets.md)); delete `unpacked/`.
