# Roadmap

What is left to build, in order of priority within each section. The rewrite plays all 69 maps with
the original's rules in Classic, and Extended ("Next Kombat") adds its menu, controls, four players,
Android and network play; the nine phases that got it there are summarised in the
[history](history.md#the-roadmap-of-2026-10-19). The current state is in
[`full-rewrite-status.md`](full-rewrite-status.md).

## 1. Safety net

- [ ] **Play new recordings** under Classic rules v16: BEGIN1–4, more single-player missions, a
      two-vs-computer mission, player-vs-player matches (deathmatch, flag hunt, race), and copy them
      to `game-sim/testResources/recordings/`. Then some Extended ones (four players, mouse). Until
      then no committed recording is replayed by `RecordedPlaythroughTest`
      ([testing strategy](testing-strategy.md#recorded-playthroughs)).
- [ ] Desktop smoke test in CI: the packaged game under `xvfb-run` with `TANKARENA_AUTOSTART` and
      `TANKARENA_QUIT_AFTER`.
- [ ] The other items under [what is not there yet](testing-strategy.md#what-is-not-there-yet).

## 2. Classic parity

The open points in [parity.md](parity.md), most important first:

- [ ] **Conveyors and turntables carry everything below height 2** except start pads, as
      `transportation()` does (`original/src/world/transp.c:23`): today only vehicles and soldiers
      move; mines, bonuses, flags, products and A-bombs stay put.
- [ ] Compare a vehicle pushing against another with footage of the original (unverified).
- [ ] Within a step the rewrite updates objects grouped by kind, not in the original's list order by
      height; check whether any interaction depends on that order.

## 3. Map editor ([ADR 0021](adrs/0021-editor-in-the-game.md))

- [ ] Try exported maps in the original under DOSBox ([manual checklist](editor-test-checklist.md)).
- [ ] Validation: unreachable pads, broken links, goals that can never reach 100 %.
- [ ] Whole-map overview.
- [ ] Import a `.MAP` from an old installation inside the game (today only `tools-mapconv import`).
- [ ] Editing by touch on Android.

## 4. Platforms and delivery

- [ ] Publish the desktop and Android packages under `releases/` on www.tankarena.nl.
- [ ] Android: release signing with a real key; a try on a real phone.
- [ ] Android: the simulation's per-tick garbage (iterators, boxing, lists in the AI, trains, mission
      checks and views); sites and approach in [performance.md](performance.md#later-the-simulations-garbage).
- [ ] Windows: player data under `%APPDATA%` instead of `%USERPROFILE%\.config`
      ([ADR 0020](adrs/0020-user-data-as-json.md)).
- [ ] macOS: packages are not built and the game has not been tried there.

## 5. Network play ([ADR 0024](adrs/0024-networking.md), [ADR 0028](adrs/0028-internet-relay-and-lobby.md))

- [ ] A game between two real machines on a LAN, and a desktop host with a guest from outside.
- [ ] A whole player-vs-player series to its end over the network in the UI (covered by tests only).
- [ ] Own-vehicle prediction, only if play over the internet shows it is needed.

## 6. Controls

- [ ] Pause a local game when a gamepad disconnects ([ADR 0026](adrs/0026-input-and-gamepads.md)).

## 7. Assets ([assets.md](assets.md#open-asset-work))

- [ ] `extract-sounds` (from `SOUND.DAT`) and `extract-bitmaps` (from `bitmaps.dat`), so every
      bundled asset is made by a tool; then delete `original/unpacked/`.
- [ ] List `extract-night` and `extract-fonts` in the tool's usage text.

## 8. Code health

- [ ] Split `Tank.kt` (1,965 lines) by subsystem, like `Match`.
- [ ] Rename `MapSceneSidecar` (a map's header, texts and layers) to what it is, e.g. `MapInfo`;
      rename `TankArenaWorld.NIGHT` (background 3 is the original's fourth world, "MARKET").
- [ ] Build test fixtures as map documents instead of through the importer's
      `CanonicalSceneBuilder` (`Match.fromCanonicalMap`).

## Later and ideas

- Computer players filling empty places in Extended games (`ComputerTankAi`).
- Spectating a network game; a kill-cam from the recording.
- Map sharing: a simple in-game map browser.
- Data-driven vehicles for Extended (the vehicle table as a data file).
- A web (WASM) build: the common modules would need `java.io.File` and threads replaced.
- Accessibility settings in Extended.
