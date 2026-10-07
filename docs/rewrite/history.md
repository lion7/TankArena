# Tank Arena Rewrite — History

This document captures the plan-of-record at earlier points in the rewrite. It is not part of the current architecture documentation and should not be used to derive current state — see [`full-rewrite-status.md`](../full-rewrite-status.md) for that.

## 1. Original Module Plan (pre-2026-04)

The original rewrite plan was an 11-module split with a strict separation between simulation, content, runtime rendering, UI, and legacy import tooling:

- `:game-core` — shared math, IDs, fixed-step timing, basic utility types.
- `:game-content` — canonical authored data: maps, layers, object definitions, asset IDs, render mapping data.
- `:game-input` — device-agnostic player intents and control abstractions.
- `:game-sim` — authoritative world state, systems, rules, AI, mode logic, events.
- `:game-protocol` — replay- and network-ready DTOs and serialization contracts.
- `:game-legacy` — the only module that understood legacy binary map/object formats.
- `:game-render-kubriko` — runtime projection of sim state into Kubriko rendering.
- `:game-ui-compose` — menus, shell flows, editor-side UI state, and Compose-facing models.
- `:app-desktop` — desktop game host application.
- `:app-editor` — standalone editor application.
- `:tools-mapconv` — import-once conversion tools and content extractors.

The phased delivery plan was: analyze legacy → scaffold → vertical slice → combat basics → modes/AI → asset/map breadth → editor/tools → polish/release.

This split was collapsed to six modules in 2026-04 — see [ADR 0006](adrs/0006-module-collapse.md).

## 2. Pure-Kotlin `:game-sim` (deleted 2026-04)

Before the authoritative-server move, the simulation lived in a pure-Kotlin `:game-sim` module with its own actor model, separate from the renderer. It implemented:

- deterministic fixed-step simulation scaffold
- world bounds, tank/turret/projectile state
- wraparound world coordinates
- tile-based passability grid built from the legacy solid layer
- axis-by-axis sliding tank-vs-terrain collision
- projectile-vs-terrain and projectile-vs-tank collision with explosion events
- tank death and respawn lifecycle
- map-placed turrets that rotate toward the nearest in-range tank and fire on cooldown

`:game-sim` was deleted in step 7 of the module collapse, once `:game-client` consumed server snapshots exclusively. The same combat behaviors are now implemented as Kubriko `Server*Actor` types in `:game-server` — see [ADR 0007](adrs/0007-authoritative-headless-kubriko-server.md).

## 3. Canonical Map Schema (removed 2026-04-26)

`:game-content` previously owned a bespoke canonical map schema:

- `CanonicalMapDefinition`
- `AuthoredObject`
- `ObjectKinds`

Plus `MapSceneSidecar` carrying mission text and import notes. The runtime client and editor both consumed these types alongside Kubriko data.

After the move to scene-JSON-as-format ([ADR 0008](adrs/0008-kubriko-and-scene-json.md)), the canonical types were removed and `MapSceneSidecar` absorbed `tileLayers`, `metadata`, `missionText`, and `importNotes`. The legacy importer kept its `CanonicalLegacyMap` intermediate but moved it inside `:game-server` as a server-internal step.

## 4. Module Collapse Step Log (2026-04)

The eight-step collapse plan, completed 2026-04-25 to 2026-04-26:

1. **Headless server prototype.** `ServerMatchPrototype` spun up a per-match headless Kubriko host. `CanonicalSceneBuilder` converted legacy-imported canonical maps into scene JSON on the fly.
2. **Server actors and managers.** `TerrainSlideManager` for axis-by-axis MTV sliding; tank/turret/projectile/goal/wall server actors implementing `Collidable` + `Serializable<T>`; mission evaluator wired to `GameEvent`.
3. **Replication contract and `MatchClient`.** `WorldSnapshot` / `ServerFrame` / `PlayerView` / `HudState` / `RadarContact` types in `:game-protocol`. `LocalMatchClient` runs the server in-process.
4. **`:game-server` extracted** as its own module; `:app-desktop` switched from `LocalMatchHost` (sim-based) to `LocalMatchClient.fromCanonicalMap`.
5. **`:game-core` and `:game-input` folded into `:game-protocol`** (mechanical move; package paths preserved).
6. **Legacy importer rewritten in `:game-server`** as `LegacyToSceneJson.convert(canonical)` emitting scene JSON + `MapSceneSidecar`. `tools-mapconv scene` and `scene-all` subcommands regenerated all 121 shipped maps. `:game-legacy` and `:tools-mapconv`'s library code folded into `:game-content`; `:tools-mapconv` retained as a thin CLI module (Amper is one-product-per-module).
7. **Client collapse.** `:game-render-kubriko`, `:game-ui-compose`, `:app-desktop` folded into `:game-client` (`jvm/app`, `mainClass com.tankarena.app.desktop.MainKt`). `composeResources/drawable/` moved with them. `ReplicatedActorScene` renamed to `ClientScene`. `:game-sim` deleted.
8. **Editor collapse.** `:app-editor` renamed `:game-editor` and rewired around Kubriko's `SceneEditor`. Server actors (Wall, Tank, Turret, Goal) implement `Editable<T>`. The "editor must not depend on `:game-server`" rule was narrowed: actor *types* are shared, but no server managers run in the editor's Kubriko instance.

Deferred cleanup (`CanonicalMap*` deletion) landed 2026-04-26.

## 5. `tools-mapconv` Folding Note

The original step 6 ambition was to fold `:tools-mapconv` itself "inside `:game-content` as an Amper product". This was not done — Amper is one-product-per-module, so `:tools-mapconv` remains as a one-file CLI module that calls into `:game-content`'s import library. The effective layout is the same (importer code lives in `:game-content`) without fighting the build system.
