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
- [ ] Play the recordings again under rules v12 (the legacy step made the v11 ones obsolete): BEGIN1–4,
      more single-player missions, a two-vs-computer mission and player-vs-player matches (deathmatch,
      flag hunt, race).
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
- The legacy step every 3rd tick: done in phase 3 (rules v12).
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

## Phase 3 — Classic parity (done 2026-10-20, rules v14)

Goal: Classic matches the original everywhere listed in [parity.md](parity.md). Each item cites the C
source, gets a test, bumps the Classic rules version when it changes play, and refreshes recordings.

1. [x] **Legacy step** (rules v12): one step every third tick, timers run out on steps, per-step rules
   once per step, keys latched between steps, the step in `game_control()`'s order
   ([ADR 0002](adrs/0002-fixed-step-100hz.md)).
2. [x] **Rule differences:** heavy mines, mine trigger and visibility, the growing mortar blast, rocket
   targets and exhaust, warps, wrecks and respawns, map wrap, fuel, AI `fire_delay`, the radar,
   F-keys in two-vs-computer, step order.
3. [x] **Missing rules:** supermarket mayhem; goal `who = 3`; conveyors and turntables; debris and
   fuel-dump flames; terrain effects and their sounds; ricochets; sound distance from the vehicle.
4. [x] **Shell:** known codes and last codes ([ADR 0020](adrs/0020-user-data-as-json.md)); in-game keys
   (pause, F11 gamma, F12 radar, screenshot, window size); controls setup with RELATIVE/DIRECT modes;
   command-line cheats and `-CODES`; "EXIT TO DOS? (Y/N)"; the no-vehicle static view.

Left, by design or for later phases: joysticks (phase 6, with the gamepad layer); a vehicle pushing
against another is unverified against footage; developer switches (`-KEYTEST`, `-DEBUG`, `-INFO`,
`-MISSING`, `-UNUSED`, `-AUTOLOCK`, `-UNLOCK`) are not ported. Recordings must be played again under
rules v14.

## Phase 4 — In-game editor ([ADR 0021](adrs/0021-editor-in-the-game.md))

Goal: everything the original editor can do, inside the game, plus validation, undo and play-test.

- [x] Editor from the main menu in both rulesets ("Map editor"): open a shipped or saved map; the map
      drawn with the game's pictures, scrolling and zoom; the three picture layers painted with a
      searchable palette of the world's pictures (right button picks one up); undo and redo per stroke;
      saving to the player's maps (`~/.local/share/tankarena/maps`); object boxes shown.
      `MapEditor` (`sim.map`) holds the editing rules and is tested without a screen; `EditorRenderTest`
      draws the editor offscreen.
- [x] Saved maps in the game's map lists (a saved map replaces the shipped one of the same name,
      as saving over a file in the original's `MAPS` did); play-test from the editor (Play or F5) in
      the map's mode, unrecorded, back to the editor with its undo history.
- [ ] New map from world templates (convert `DATA/MAP_0..3.DAT`) and size; the structure library
      and links; goal, bonus and men layers.
- [ ] Every object type with its properties; links; computer-tank settings and waypoints; pad weapons.
- [ ] Header and mission texts; export `.MAP` (ADR 0022, option C); validation; overview.

Depends on: phases 1 and 2.

## Phase 5 — Rulesets and the Extended menu ([ADR 0023](adrs/0023-classic-and-extended-rulesets.md))

Goal: choose Classic or Extended; Extended has its own clearer menu.

- [x] `Ruleset` (`com.tankarena.sim`): Classic and Extended, a rules version each
      (`GAME_RULES_VERSION`, `EXTENDED_RULES_VERSION`); stored in recordings (old ones read as Classic)
      and in `settings.json`; first-start choice, `--classic` / `--extended`, and "Switch to Classic"
      in Extended's settings. Named flags come with the first Extended rule change.
- [x] Extended's own look (dark, sand accent; `ui.compose.extended`), keyboard or mouse: home
      (campaign, local game, network game later, replays, map editor, settings, help, quit).
- [x] Campaign: the codes reached with each mission's world, size and briefing, a code field, starting
      on the last code; after a game Extended returns to where it was started from.
- [x] Local game: together against the computer (its own mission list) or against each other on a map
      (with what the map has room for), with rounds, laps and the losers tank changed in place.
- [x] Replays: newest first with mission, mode, result, length, date and rules; naming (F2) and
      deleting; the viewer with pause, 1/4×–8× speed and a free camera (C, arrow keys).
- [x] Settings on one page: controls, window size, gamma, radar, music, sound, match rules, switch to
      Classic. Help: how to play, the keys.
- [ ] Still in the original's look: the controls setup, the briefing and mission texts, the tank
      selection and the first-start choice.
- [x] Extended presentation (each a setting, on by default): the picture fills the window; on a wide
      window the screen grows to up to 1280 legacy pixels across, so the map windows show more of the
      map; smooth motion draws vehicles, shots and the cameras between the 33 steps a second, one step
      (30 ms) later, snapping on jumps.
- [x] The controls setup, the mission texts (briefing, success, failure) and the first-start choice in
      Extended's look; the tank selection keeps the original's arena on purpose.
- [x] First Extended rule flags (`RuleFlags`, Extended rules v5): overlapping computer vehicles survive;
      one vehicle per player in player vs player.

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
