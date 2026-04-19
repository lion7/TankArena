# Tank Arena Full Rewrite Status

As of April 19, 2026.

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

Missing:
- intent-driven AI controller model for mobile units
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
