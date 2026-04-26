# Tank Arena Full Rewrite Status

As of April 26, 2026.

> **2026-04 update:** the module layout described in section 3 has been
> collapsed from 11 modules down to the 6-module target (`:game-protocol`,
> `:game-content`, `:game-server`, `:game-client`, `:game-editor`,
> `:tools-mapconv`), and the authoritative simulation is running on a
> headless Kubriko instance. `:game-editor` embeds Kubriko's `SceneEditor`
> against the same actor metadata the server uses. The `CanonicalMap*`
> schema has been removed from `:game-content`; the runtime client and
> editor now consume `MapSceneSidecar` (with embedded `TileLayers`) plus
> Kubriko scene JSON directly. See
> [section 15](#15-module-collapse-and-authoritative-server-2026-04) for the
> current plan, what has already landed, and what is still pending. Earlier
> sections are retained as the original intent and are annotated inline
> where the new direction supersedes them.

This document is the single high-level reference for the Kotlin rewrite of Tank Arena. It combines:
- the original rewrite intent
- the current implementation status
- the remaining work required for a full feature-complete rewrite

It is intended to be read alongside the more focused rewrite docs under `docs/rewrite/`, but it should be sufficient on its own to understand where the project stands and what remains.

## 1. Rewrite Goal

Tank Arena is being rewritten as a new Kotlin codebase. This is a full rewrite, not a line-by-line port of the C code.

The rewrite goals are:
- preserve gameplay intent, content breadth, and the feel of the original game
- remove legacy Allegro- and hardware-era architectural baggage
- make the new codebase maintainable, testable, and portable
- keep simulation logic independent from rendering and UI frameworks
- support desktop first, while keeping future WASM and networking possible
- rebuild the editor and tooling as first-class parts of the project

## 2. Chosen Technology Direction

The project is intentionally desktop-first.

Current stack decisions:
- Kotlin for the rewrite language
- Compose Multiplatform for launcher, menus, shell UX, and editor/tooling UI
- Kubriko for the actual gameplay viewport and runtime rendering layer
- Amper as the build system

Important boundary decisions:
- Compose must not leak into core gameplay logic.
- Kubriko must not own authoritative game state.
- The simulation must remain pure Kotlin and engine-agnostic.
- Legacy `.MAP` files are import-only and must be converted into a canonical rewrite format.
- `unpacked/` is the approved source of truth for extracted assets.
- Networking is deferred, but the architecture must not block it later.

## 3. What The Original Plan Was

> Superseded in 2026-04 by the module-collapse plan — see [section 15](#15-module-collapse-and-authoritative-server-2026-04).
> The 11-module split below is kept for historical context; the target layout
> is now five modules (`:game-protocol`, `:game-content`, `:game-server`,
> `:game-client`, `:game-editor`).

The original rewrite plan was to build a disciplined modular codebase with a strict split between simulation, content, runtime rendering, UI, and legacy import tooling.

The planned module graph was:
- `:game-core`
- `:game-content`
- `:game-input`
- `:game-sim`
- `:game-protocol`
- `:game-legacy`
- `:game-render-kubriko`
- `:game-ui-compose`
- `:app-desktop`
- `:app-editor`
- `:tools-mapconv`

The intended responsibilities were:
- `:game-core`
  Shared math, IDs, fixed-step timing, basic utility types.
- `:game-content`
  Canonical authored data: maps, layers, object definitions, asset IDs, render mapping data.
- `:game-input`
  Device-agnostic player intents and control abstractions.
- `:game-sim`
  Authoritative world state, systems, rules, AI, mode logic, events.
- `:game-protocol`
  Replay- and network-ready DTOs and serialization contracts.
- `:game-legacy`
  The only module that understands legacy binary map/object formats.
- `:game-render-kubriko`
  Runtime projection of sim state into Kubriko rendering.
- `:game-ui-compose`
  Menus, shell flows, editor-side UI state, and Compose-facing models.
- `:app-desktop`
  Desktop game host application.
- `:app-editor`
  Standalone editor application.
- `:tools-mapconv`
  Import-once conversion tools and content extractors.

The original phased delivery plan was:
1. analyze the legacy repo and data formats
2. scaffold the Kotlin project and module boundaries
3. build a first playable vertical slice
4. add combat basics
5. add game modes and AI
6. add broader asset/map/object parity
7. rebuild the editor and tools
8. polish for release and future platform expansion

## 4. What Was Preserved From The Legacy Design

The rewrite intentionally preserves these legacy concepts:
- fixed-step style gameplay progression
- layered maps and layered rendering
- map-driven authored gameplay objects
- multiple game modes and mission-driven content
- strong local multiplayer assumptions
- the editor as a core workflow, not an afterthought
- content import and conversion as a real requirement

The rewrite intentionally replaces these legacy technical patterns:
- global state as the main architecture
- function-pointer polymorphism in C structs
- raw serialized C object blobs as a long-term content format
- Allegro-era rendering/audio/input assumptions
- platform-specific timing and input code inside gameplay logic

## 5. Current Project State

The rewrite has moved beyond planning. A real Kotlin codebase exists and currently builds through Amper.

Current build/runtime facts:
- the Gradle-based scaffold has been replaced by Amper
- the project builds through `project.yaml` and per-module `module.yaml`
- desktop is the only supported runtime target right now
- the existing code is a first vertical slice, not full gameplay parity

## 6. What Is Already Implemented

### 6.1 Build And Project Structure

Implemented:
- Amper-based multi-module workspace
- module boundaries matching the planned architecture
- production code moved to Amper-style `src/`
- tests moved to Amper-style `test/`

Status:
- stable enough to build on

### 6.2 Core Foundations

Implemented in `:game-core`:
- fixed-step timing primitives
- IDs and basic math types
- integer vector helpers and wraparound helpers

Status:
- good enough for current prototype simulation
- still minimal compared to what the full game will need

### 6.3 Canonical Content Model

Implemented in `:game-content`:
- canonical map definition
- layer representation
- asset IDs
- visual asset descriptors
- legacy picture catalog support
- render lookup registry for tiles, tanks, turrets, and projectiles

Status:
- enough for import and first-pass rendering
- not yet a complete gameplay/content schema for all tank/object families

### 6.4 Legacy Import Pipeline

Implemented in `:game-legacy`:
- legacy `.MAP` parsing
- mission text extraction
- map header and layer extraction
- legacy object parsing for a supported subset of object types

Currently imported authored objects include:
- player starts
- turrets
- flags
- goals
- locks
- warps
- products
- destroyers
- enforcers

Status:
- a real import-once pipeline exists
- import coverage is still partial relative to the original game

### 6.5 Conversion Tooling

Implemented in `:tools-mapconv`:
- legacy `.MAP` to canonical JSON conversion
- legacy `pictures.c` extraction into generated Kotlin catalog data

Status:
- useful and working
- still focused on the current implemented slice rather than full conversion parity

### 6.6 Simulation

> The authoritative Kubriko-based simulation now lives in the
> `:game-server` module and is the only simulation in the tree. The old
> `:game-sim` module described below was deleted in step 7 once
> `:game-client` consumed server snapshots exclusively (see
> [section 15](#15-module-collapse-and-authoritative-server-2026-04)).
> The summary below is retained as historical context for the original
> pure-Kotlin slice it described.

Implemented in `:game-sim`:
- deterministic fixed-step simulation scaffold
- world bounds
- tank state with separate body and turret facing, armor, fuel, life/respawn lifecycle, primary cooldown
- turret state with import-driven fire delay, range, and damage
- projectile state with owner kind (tank or turret) and damage
- wraparound world coordinates retained as fallback when no map is bound
- projectile spawn and TTL
- simulation bootstrap from canonical imported maps
- tile-based passability grid built from the canonical solid layer
- axis-by-axis sliding tank-vs-terrain collision
- projectile-vs-terrain collision producing explosion events
- projectile-vs-tank collision producing TankHit / armor decrement
- tank death and respawn at original spawn point with TankDestroyed / TankRespawned events
- map-placed turrets that rotate toward the nearest in-range tank and fire on cooldown using the imported delay/power/radius

Current simulation behavior:
- multiple controllable tanks can coexist with shared collision rules
- map turrets actively engage the player rather than standing idle
- projectiles deal damage and clean up on impact instead of only despawning by TTL
- the combat model is now a real loop (move, aim, fire, hit, die, respawn) rather than a render demo

Status:
- first real combat slice in place; the gameplay loop is no longer a flythrough
- still missing weapon variety (chain gun, mines, rockets, mortars), area damage, fuel consumption, real handling feel, and AI for non-turret enemies

### 6.7 Input

Implemented in `:game-input` and `:app-desktop`:
- `PlayerIntentFrame`
- keyboard-driven player control path for desktop (WASD / arrows for movement, space to fire)
- mouse-driven turret aim, quantized to an 8-way direction vector with a small dead zone
- viewport pointer/size tracking that converts mouse position into the aim component of `PlayerIntentFrame`

Status:
- enough for single local control with combined keyboard movement + mouse aim
- not yet a complete action-mapping or multiplayer device abstraction

### 6.8 Desktop Runtime And Shell

Implemented in `:app-desktop` and `:game-ui-compose`:
- Compose desktop shell
- main menu flow
- game mode selection flow
- mission selection flow
- mission catalog loading
- in-game transition into a Kubriko-hosted viewport
- in-arena HUD overlay rendering ARMOR / FUEL / MISSION / TANK plus the active mission code, driven directly from `WorldState`

Current shell behavior:
- can load a mission from discovered map content
- can construct simulation state from a selected canonical map
- can return to the main menu from gameplay
- shows live combat state on the HUD (armor, lives, mission code) during play

Status:
- real, usable shell exists with a first HUD slice
- still closer to a prototype front end than a complete game UX

### 6.9 Rendering

Implemented in `:game-render-kubriko`:
- Kubriko viewport integration
- camera follow on the first tank
- terrain/layer drawing from canonical map data
- entity rendering for tanks, turrets, and projectiles
- separate body and turret sprite layers per tank, driven by independent body and turret facing
- placeholder explosion glyph for dead tanks awaiting respawn
- desktop bitmap cache for extracted PNG assets

Current extracted sheets in active use:
- `floors.png`
- `walls.png`
- `building.png`
- `trees.png`
- `tank1.png` to `tank12.png`
- `towers.png`
- `animations.png`

Status:
- the renderer is content-backed and no longer placeholder-only
- rendering fidelity is still first-pass rather than exact legacy parity

### 6.10 Legacy Visual Mapping

Implemented in `:game-content`:
- generated legacy picture catalog from `src/data/pictures.c`
- runtime lookup by legacy picture name
- tile family-to-sheet routing
- explicit legacy-style tank frame grouping
- explicit legacy-style turret frame slot mapping

Status:
- much stronger than heuristic modulo rendering
- still not a complete one-to-one legacy visual reproduction

### 6.11 Tests

Implemented test coverage includes:
- legacy object parsing
- simulation bootstrap from canonical maps
- generated asset registry behavior
- passability grid construction from canonical maps and out-of-bounds handling
- tank-vs-terrain collision (wall stop and slide)
- projectile-vs-terrain collision and explosion event
- projectile-vs-tank damage, death, and respawn lifecycle
- map turret target acquisition and firing at in-range tanks (and not firing on out-of-range tanks)

Status:
- enough to keep the new combat core from regressing
- still far from comprehensive parity or full systems coverage

## 7. What Is Stable Enough To Rely On

These parts should be treated as real foundations, not throwaway prototypes:
- Amper project structure
- module boundaries
- desktop-first app split
- canonical import-once content direction
- legacy `.MAP` parsing foundation
- generated legacy picture catalog workflow
- pure Kotlin simulation boundary
- Compose shell plus Kubriko viewport split
- extracted asset loading from `unpacked/`
- tile-based passability and the sliding collision/damage/respawn loop in `:game-sim`
- mouse-aim plumbing from desktop input through `PlayerIntentFrame`
- HUD overlay reading directly from `WorldState`

## 8. What Is Still Prototype-Level

These areas exist only as a first slice and should not be mistaken for finished systems:
- movement and handling feel (no acceleration model, no terrain modifiers, fixed step velocity)
- collision rules beyond the solid layer (no per-material rules, no tank-vs-tank, top layer ignored)
- damage and destruction beyond instant armor decrement and timed respawn
- weapons beyond the basic primary projectile path
- AI for non-turret enemies (turrets target but don't navigate; nothing else targets at all)
- mission/objective execution
- multiplayer support beyond basic local input assumptions
- HUD/radar parity (only ARMOR / FUEL / MISSION / TANK strip exists; no radar, no scoring panel)
- audio runtime
- split-screen
- editor implementation
- broader object-family parity
- save/config/profile UX
- replay and network support

## 9. Gap Analysis By Subsystem

### 9.1 Game Loop And Simulation Model

Done:
- fixed-step simulation exists
- world state is separate from rendering

Missing:
- interpolation/presentation model refinement
- pause/play/editor session orchestration
- deterministic checksums and stronger replay hooks
- richer event model

### 9.2 Tanks

Done:
- basic tank state
- movement and firing path
- imported visual type data
- separate body direction and turret direction in both sim and renderer
- armor and life/respawn lifecycle wired through the tick loop
- primary fire cooldown gate

Missing:
- full tank definitions from legacy data (per-type stats)
- fuel consumption, handling differences, special capabilities
- better movement feel and terrain response (acceleration, friction, surface modifiers)
- vehicle subclasses such as helicopters and planes

### 9.3 Weapons And Projectiles

Done:
- one basic projectile path shared by tanks and turrets
- per-projectile damage and owner-kind tagging
- primary-cannon cooldown gating
- collision-based hit resolution against terrain and tanks

Missing:
- main cannon feel tuning (muzzle velocity, accuracy spread, recoil feedback)
- chain gun
- mines
- rockets
- mortars
- area damage
- per-weapon reload/cooldown beyond the primary cannon
- visual/audio effect coupling

### 9.4 Collision And Terrain Rules

Done:
- wraparound coordinate helpers (retained as fallback)
- tile-based passability grid built from the canonical solid layer
- tank vs terrain collision with axis-by-axis sliding
- projectile vs terrain collision producing explosion events
- projectile vs tank collision producing damage and lifecycle events

Missing:
- tank vs tank collision (currently tanks can overlap)
- projectile vs map-object collision for non-tank entities (flags, products, destroyers, etc.)
- terrain/material semantics (mud, ice, water, sand, runway)
- bridge, runway, pit, and other special terrain rules
- top-layer collision semantics (currently top-layer decoration is always passable)

### 9.5 World Objects

Done:
- subset import of authored world objects
- some object states represented canonically

Missing:
- active gameplay logic for most imported object families
- trains
- zeppelin
- B52
- pickups and products with real behavior
- map triggers and special actors

### 9.6 Modes And Missions

Done:
- mission list shell flow
- map selection and loading

Missing:
- mission evaluation logic
- password/progression systems
- single-player mission flow
- PvP duel rules
- race rules
- capture/flag rules
- debriefing and victory/failure screens

### 9.7 AI

Done:
- map turret target acquisition (nearest alive tank within imported range)
- per-tick rotation toward the target using the legacy 16-step compass
- cooldown-gated firing using the imported delay/power values
- first-pass intent-driven AI for mobile enemy tanks on the new
  `:game-server` prototype: they pick the nearest opposing tank, steer
  body + turret toward it via `PlayerIntentFrame`, and fire when aligned
  and in range (see [section 15](#15-module-collapse-and-authoritative-server-2026-04))

Missing:
- navigation and waypoint logic
- combat decisions beyond "shoot the closest tank"
- per-mode AI behavior
- line-of-sight and obstacle-aware targeting

### 9.8 Rendering Fidelity

Done:
- real sheets are loaded
- legacy picture catalog influences rendering
- separate body and turret sprite layers per tank
- first-pass HUD strip overlay (ARMOR / FUEL / MISSION / TANK)

Missing:
- exact frame tables for many families
- night-world parity
- better actor layering and occlusion rules
- effects, smoke, debris, and proper explosion sprites (currently a placeholder rectangle)
- radar parity and full HUD breadth (score, weapon select, minimap, debrief)
- split-screen and multiple camera support

### 9.9 Input And Local Multiplayer

Done:
- one desktop keyboard path for movement and primary fire
- mouse pointer drives an 8-way turret aim independent of body movement

Missing:
- action mapping system
- remapping UI
- gamepad support
- multiple player/device binding
- better shell/input focus behavior
- clean future protocol alignment for multiplayer/networking

### 9.10 Audio

Done:
- no real backend yet

Missing:
- sound effect playback
- music playback
- audio event system
- panning/spatialization rules
- runtime audio backend boundary

### 9.11 Editor And Tools

Done:
- editor app module exists
- conversion tooling exists

Missing:
- real standalone editor UX
- map viewport editing
- object placement
- property inspector
- validation
- canonical save/load authoring workflow
- in-editor playtest

### 9.12 Protocol / Replay / Future Networking

Done:
- protocol module exists
- architecture reserves room for future networking

Missing:
- actual replay recording/playback
- deterministic checksum validation
- authoritative snapshot format
- transport layer
- desync debugging tools

## 10. What Is Left For A Full Feature-Complete Rewrite

To call the rewrite feature-complete, the project still needs all of the following at a minimum.

### Phase A: Finish The Core Runtime Slice
- [done] tile-based passability and tank-vs-terrain sliding collision
- [done] projectile-vs-terrain and projectile-vs-tank collision with damage and respawn
- [done] separate tank body direction from turret direction (sim and renderer)
- [done] mouse-driven turret aim and primary-cannon cooldown
- [done] active map turrets that engage the player using imported delay/range/power
- [done] first-pass in-game HUD basics (ARMOR / FUEL / MISSION / TANK strip)
- still to do: tank-vs-tank collision, terrain/material modifiers, fuel consumption, real explosion sprites
- still to do: weapon variety beyond the primary cannon (chain gun, mines, rockets, mortars)
- still to do: audio event plumbing

### Phase B: Reach Core Gameplay Parity
- implement all major tank gameplay definitions
- implement active turret behavior
- support mines, rockets, mortars, and core explosion logic
- add respawn/death lifecycle
- support more imported gameplay object families
- bring visual layering closer to the original

### Phase C: Implement Modes And Objectives
- single-player missions
- victory/failure evaluation
- PvP duel rules
- race mode
- flag/capture mode
- mission text and progression UX

### Phase D: Add AI
- intent-driven AI controllers
- basic combat and navigation logic
- sanity tests and scenario fixtures

### Phase E: Asset And Map Breadth
- support more of the legacy map/object space
- improve exact render mapping
- cover night content and special worlds
- validate a parity fixture pack of representative maps

### Phase F: Rebuild The Editor
- standalone editor application
- terrain/layer editing
- object palette and inspector
- validation
- save/load in canonical format
- playtest support

### Phase G: Productization
- save/config UX
- profile and settings flows
- packaging and release flow
- performance profiling
- replay tooling
- stronger automated test coverage

### Phase H: Future-Oriented Work
- networking implementation
- web/WASM target enablement
- platform-specific polish for additional targets

## 11. Recommended Remaining Delivery Sequence

The most practical sequence from here is:
1. [done in Phase A combat slice] tile collision, body/turret split, active map turrets, basic damage and HUD
2. broaden combat: tank-vs-tank collision, additional weapons (chain gun, mines, rockets, mortars), area damage, real explosion/effect sprites
3. add audio event plumbing and a basic audio backend
4. terrain/material rules (mud, ice, water, fuel/armor pickups, special tiles like bridges and runways)
5. implement one complete end-to-end mission mode (objectives, win/loss evaluation, debrief)
6. add AI for mobile units in that mode
7. broaden map/object parity (more imported object families with active behavior)
8. build the standalone editor on the canonical format
9. harden tests, replay hooks, and product UX

## 12. Suggested Definition Of “Feature-Complete”

The rewrite should only be considered feature-complete when all of the following are true:
- representative legacy maps can be imported and played end-to-end
- core weapons, tanks, turrets, and common object families behave correctly
- single-player missions and core multiplayer modes function correctly
- AI is good enough to support the original gameplay loops
- rendering, HUD, and audio are coherent and recognizable as Tank Arena
- the new editor can author and save canonical maps
- the desktop game is stable, packaged, and testable
- the architecture still preserves a clean path for future networking and additional targets

## 13. Current Practical Summary

The rewrite is no longer hypothetical. The repo already contains:
- a real Kotlin multi-module codebase
- a real Amper build
- a real import pipeline
- a real simulation boundary
- a real Compose shell
- a real Kubriko viewport
- a real content-backed render path
- a real combat loop (collision, damage, death, respawn, active turrets, HUD, mouse-aimed turret)

What it does not yet contain is the full game.

The current state should be viewed as:
- architecture established
- first combat slice implemented (Phase A core runtime largely complete)
- core delivery risk reduced
- gameplay breadth (weapon variety, audio, AI for mobile units, mission modes, editor) still ahead

## 14. Related Documents

For more focused detail, see:
- `docs/rewrite/architecture.md`
- `docs/rewrite/migration-plan.md`
- `docs/rewrite/first-milestone.md`
- `docs/rewrite/implementation-status.md`
- `docs/rewrite/legacy-system-map.md`
- `docs/rewrite/testing-strategy.md`
- `docs/rewrite/risk-register.md`
- `docs/rewrite/amper-migration.md`

## 15. Module Collapse And Authoritative Server (2026-04)

### 15.1 Why The Layout Is Changing

The 11-module split described in section 3 was scaffolding. In practice it
produced more boundary cost than value: many modules held only a handful of
files, `:game-sim` and `:game-render-kubriko` each had to re-express the
same actor model on their side of the wire, and there was no single seam
where a future networked server could slot in. The rewrite is now
consolidating to five modules with a clear client/server split, and making
the server authoritative on a headless Kubriko instance so single-player
and networked play share the same code path.

### 15.2 Target Module Layout

Five modules replace the previous eleven:

- `:game-protocol` — absorbs `:game-core` and `:game-input`. Pure Kotlin
  wire types: math, timing, IDs, `PlayerIntentFrame` / `InputFrame`,
  `WorldSnapshot`, the `ActorState` sealed hierarchy keyed by stable
  `actorId: Long`, `GameEvent`, `PlayerView` / `HudState` / `RadarContact`
  / `ServerFrame`. No Compose or Kubriko dependencies.
- `:game-content` — absorbs `:game-legacy` and `:tools-mapconv`. Owns
  sprite sheets, the generated legacy picture catalog, sounds, scene JSON
  files, per-map metadata sidecars, and the legacy `.MAP` importer (now
  emitting Kubriko scenes plus metadata rather than a bespoke canonical
  map schema). Also owns the shared `SerializableMetadata` typeId
  registry used by server, client, and editor.
- `:game-server` — new. Headless Kubriko instance running
  `ActorManager`, `CollisionManager`, `SerializationManager`, a custom
  `TerrainSlideManager` (axis-by-axis sliding response, since Kubriko's
  `CollisionManager` only detects overlap), and a mission evaluator.
  Server actors (`ServerTankActor`, `ServerTurretActor`,
  `ServerProjectileActor`, `ServerWallActor`, `ServerGoalActor`)
  implement `Collidable` plus Kubriko `Serializable<T>`, so each `save()`
  produces the matching `:game-protocol` `ActorState` — one type that
  plays both the scene-load and the per-tick replication role.
  `PhysicsManager` is deferred to when explosives land (Box2D floats fight
  grid-locked kinematic tanks; reserved for debris/knockback later).
- `:game-client` — absorbs `:game-render-kubriko`, `:game-ui-compose`,
  and `:app-desktop`. Client-side Kubriko instance whose actors only
  render/audio and get mutated each tick by `ClientScene.sync(snapshot)`,
  matching actors by stable `actorId`. Input capture converts to
  `InputFrame` and submits via `MatchClient`.
- `:game-editor` — replaces `:app-editor`. Thin Compose-Desktop wrapper
  embedding Kubriko's `SceneEditor` composable (from
  `/var/projects/kubriko/tools/scene-editor`), re-using the same
  `SerializableMetadata` registry so placed actors round-trip with the
  server and client.

Replication contract: the server walks `actorManager.allActors` each tick,
calls `save()` on every `Serializable`, wraps the results in a
`WorldSnapshot` with the stable `actorId` assigned at spawn. The client
keeps `Map<Long, ClientActor>`; it spawns new render actors for unseen
IDs, calls `sync(state)` on existing ones, and removes actors whose IDs
vanished. Single-player boots `LocalMatchClient(ServerMatchPrototype)` in
process; networked play will drop a `RemoteMatchClient` into the same
seam.

### 15.3 Headless Tick And Scene Format Caveats

Two assumptions this plan depends on, both verified against
`/var/projects/kubriko`:

- Kubriko's public headless `tick(Int)` API and `Kubriko.newInstance` are
  a **fork-only** feature in the local Kubriko at `/var/projects/kubriko`.
  Upstream Kubriko does not ship them. A rebase on upstream would block
  this plan; the local fork must stay the source of truth.
- A Tank Arena map **is** a Kubriko scene JSON, produced by the scene
  editor's `SerializationManager.serializeActors` and loaded on a
  headless server with `deserializeActors(json)`. There is no parallel
  canonical map schema. That couples shipped content to the fork's
  serialization format: if the format shifts, every scene regenerates —
  so the legacy `.MAP` importer is kept as a first-class tool, not a
  one-shot.

### 15.4 What Has Landed

Completed as of 2026-04-25 — six of the eight planned steps have shipped
and the module count is down from 11 to 6 (`:game-content`,
`:game-protocol`, `:game-server`, `:game-client`, `:app-editor`,
`:tools-mapconv`):

- headless Kubriko host spun up per match in `ServerMatchPrototype`
- `CanonicalSceneBuilder` converting legacy-imported canonical maps into
  a Kubriko scene JSON on the fly for the prototype (the permanent path
  is the importer writing scene JSON at import time — see 15.5)
- `TerrainSlideManager` implementing axis-by-axis MTV sliding response
  after `CollisionManager.onUpdate`
- tank actor with intent-driven hull and turret direction, acceleration
  and friction, primary-fire cooldown, MTV collision response against
  walls and other tanks
- projectile actor with owner-kind tagging, TTL, out-of-bounds flushing,
  and owner immunity for tank-fired shots
- turret actor driven externally each tick (target acquisition, rotation
  via `stepToward`, cooldown-gated fire in the same projectile pool)
- goal actor capture with once-only claim semantics, contribution
  accumulation, and `GameEvent.MissionWon` on reaching 100%
- tank lifecycle: damage → `GameEvent.DamageTaken`, destruction →
  `GameEvent.TankDestroyed` with lives decrement, 60-tick respawn
  countdown, respawn → `GameEvent.TankSpawned`; mission loss when all
  player tanks exhaust lives; mission win when all enemy tanks/turrets
  are dead
- first-pass AI for mobile enemy tanks — picks nearest opposing tank,
  steers body and turret via `PlayerIntentFrame`, fires when aligned and
  in range
- `PlayerView` generation (camera on controlled tank, HUD mirroring
  armor/fuel/lives/mission progress/mission code/status text, radar of
  other tanks/turrets/goals); `LocalMatchClient` populates
  `ServerFrame.playerViews`
- `:game-server` module extracted with its own `module.yaml` and test
  directory; `:app-desktop` now depends on `:game-server` and boots
  `LocalMatchClient.fromCanonicalMap(map)` in place of `LocalMatchHost`
- test coverage on the server covers bootstrap, tick round-trip,
  collision sliding, firing with cooldown, wall cleanup, turret aim,
  goal capture, tank lifecycle (destroy + respawn + MissionWon),
  AI motion and fire, PlayerView HUD/radar wiring, scene-JSON round
  trips, and bootstrapping from generated scene files (40 tests green)
- `:game-core` and `:game-input` folded into `:game-protocol`
  (mechanical move; package paths unchanged so imports did not churn)
- legacy `.MAP` importer rewritten in `:game-server` as
  `LegacyToSceneJson.convert(canonical)`, emitting a Kubriko scene JSON
  plus a `MapSceneSidecar` (metadata + mission text + import notes);
  `tools-mapconv scene` and `scene-all` subcommands regenerated all 121
  shipped maps into `game-content/resources/scenes/scene_*.json` +
  `metadata_*.json`; `ServerMatchPrototype.fromSceneJson(scene, metadata)`
  is the new bootstrap path that needs no `CanonicalMap*` types
- `:game-legacy` and `:tools-mapconv`'s library code folded into
  `:game-content` (`com.tankarena.legacy.*` and
  `com.tankarena.tools.mapconv.*` now live there). `:tools-mapconv`
  remains as a thin Amper module containing only the CLI `Main.kt`,
  since Amper is one-product-per-module and the entry point still needs
  to be a `jvm/app`
- `:game-render-kubriko`, `:game-ui-compose`, and `:app-desktop` folded
  into `:game-client` (`jvm/app`, `mainClass com.tankarena.app.desktop.MainKt`);
  composeResources/drawable/ moved with them; `ReplicatedActorScene`
  renamed to `ClientScene` (it was already syncing by stable
  `actorId`); `:game-sim` deleted entirely now that no module depends on
  it; `:app-editor` stripped to a content+protocol stub awaiting step 8
- `:app-editor` renamed to `:game-editor` and rewired as a thin Compose-
  Desktop wrapper around Kubriko's `SceneEditor` (step 8). The five
  `Server*Actor` types in `:game-server` (Wall, Tank, Turret, Goal —
  Projectile stays plain `Serializable<T>` since it is never
  hand-placed) now implement `Editable<T>`, which lets the editor reuse
  the same `State` data classes the server bootstraps from. The plan's
  "editor must not depend on `:game-server`" guidance was relaxed in
  favour of sharing the State definitions; `:game-editor` depends on
  `:game-server` purely for the actor classes/typeIds — no server
  managers run in the editor's Kubriko instance. The editor opens
  `game-content/resources/scenes/` by default so existing imported
  scenes can be edited round-trip

### 15.5 What Is Still Pending

The structural rewrite is complete. All eight steps of the collapse plan
plus the deferred `CanonicalMap*` cleanup have landed; the remaining work
is gameplay/feature-level rather than module-shape.

Historical note (resolved): this section previously tracked the
`CanonicalMap*` deletion. It landed on 2026-04-26:

1. `CanonicalMapDefinition`, `AuthoredObject`, and `ObjectKinds` are gone
   from `:game-content`. `MapSceneSidecar` now carries `tileLayers`,
   `metadata`, `missionText`, and `importNotes`, and is the only map
   metadata the runtime client and editor consume. The legacy `.MAP`
   importer survives as a server-internal intermediate in
   `:game-server/.../legacy/` (`CanonicalMapDefinition` +
   `LegacyCanonicalConverter` + `LegacyObjectParser`) — it is no longer
   exported across module boundaries, only used by `LegacyMapImporter`
   (the new `(bytes, name) -> ImportedMission(sidecar, sceneJson)`
   facade) and by the existing `:game-server` test DSL. The client
   menu now stores `(sidecar, sceneJson)` per `MissionEntry` and boots
   the in-process server through `LocalMatchClient.fromSceneJson`. The
   `BEGIN1` playable-mission shim now patches scene JSON directly by
   deserializing → injecting `ServerTankActor` instances → re-serializing
   with the shared `tankArenaSerializableMetadata` registry.
   `:game-content` retains only the data types every module needs:
   `MapMetadata`, `MissionText`, `TileLayers`, `GameModeCompatibility`,
   `TankArenaWorld`, plus `MapSceneSidecar` and the legacy raw-byte
   `LegacyMapParser` / `LegacyMapData` / `LegacyMapHeader`

Folding `tools-mapconv` itself "inside `:game-content` as an Amper
product" (the original step 6 ambition) was not done — Amper is
one-product-per-module, so `:tools-mapconv` remains as a one-file CLI
module that calls into `:game-content`'s import library. That gives
the same effective layout (the importer code is in `:game-content`)
without fighting the build system.

### 15.6 Impact On The Earlier Gap Analysis And Delivery Sequence

Where section 9 and the delivery sequence in sections 10–11 assumed the
original 11-module layout, the new direction changes the following:

- AI for mobile units has moved from "missing" to "first-pass landed"
  (section 9.7 updated inline).
- Tank-vs-tank collision has landed on the server via MTV response
  (section 9.4's "tank vs tank collision" item is resolved on the
  server; the client-side rendering path will inherit it through
  replication once `:game-client` lands).
- The "protocol / replay / future networking" gap in section 9.12 is
  partly addressed: `WorldSnapshot` / `ServerFrame` / `PlayerView` /
  `GameEvent` are now the authoritative tick output, which is the shape
  replay and networking will consume.
- Phase F's editor work (section 10) now reuses Kubriko's scene editor
  rather than building a bespoke one, which narrows scope considerably.
- The delivery sequence in section 11 still stands directionally, but
  items 2–3 (broaden combat, audio plumbing) should be implemented on
  the new `:game-server` actors rather than on the retiring `:game-sim`.
