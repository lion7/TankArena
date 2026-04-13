# Tank Arena Rewrite Status

## Current State
- The Kotlin rewrite is no longer just a plan. A desktop-first multi-module codebase exists and builds successfully through Amper.
- The project already has working foundations for:
  - canonical content models
  - legacy `.MAP` import
  - deterministic fixed-step simulation
  - Compose desktop shell
  - Kubriko gameplay viewport
  - content-backed rendering from `unpacked/` assets
  - generated legacy picture catalog extraction from `src/data/pictures.c`
- The current implementation is a functional first vertical slice, not gameplay parity.

## What Has Been Implemented

### Project And Module Structure
- Amper multi-module Kotlin project created.
- Current modules:
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
- Compose Multiplatform and Kubriko are wired into the desktop/runtime modules only.
- Shared gameplay modules remain engine-agnostic.

### Legacy Import And Canonical Format
- Legacy `.MAP` parsing implemented in `:game-legacy`.
- Imported data includes:
  - header and mission metadata
  - map dimensions
  - terrain layers
  - mission text
  - supported subset of legacy object blob contents
- Supported legacy object import currently includes:
  - player starts
  - turrets
  - flags
  - goals
  - locks
  - warps
  - products
  - destroyers
  - enforcers
- Canonical rewrite map/content format implemented in `:game-content`.
- Legacy `.MAP` is treated as import-once input, not runtime source of truth.

### Simulation
- Deterministic fixed-step simulation scaffold implemented in `:game-sim`.
- Current sim includes:
  - world bounds
  - tank state
  - turret state
  - projectile state
  - simple projectile spawning and TTL
  - wraparound coordinates
  - fixed-step ticking
- `SimulationFactory` can build sim state from canonical imported maps.
- Tanks and turrets now carry visual variant data from imported legacy objects:
  - `tankType`
  - `turretType`
  - `direction`

### Desktop Runtime
- Compose desktop shell implemented in `:app-desktop`.
- Desktop app can:
  - start without a map and run prototype world state
  - load a legacy `.MAP`
  - load a canonical JSON map
  - run the sim at fixed tick cadence
  - capture keyboard input
  - host the Kubriko gameplay viewport

### Rendering
- Kubriko viewport implementation exists in `:game-render-kubriko`.
- Current renderer supports:
  - camera follow on the first tank
  - terrain drawing from canonical layers
  - tank rendering
  - turret rendering
  - projectile rendering
- Rendering uses extracted source assets from `unpacked/sprites`.
- Current asset-backed sheets in use:
  - `floors.png`
  - `walls.png`
  - `building.png`
  - `trees.png`
  - `tank1.png` to `tank12.png`
  - `towers.png`
  - `animations.png`
- A desktop bitmap cache loads and reuses PNGs from `unpacked/`.

### Legacy Asset Registry
- Rendering is no longer driven by placeholder colors or raw modulo indexing alone.
- A generated legacy picture catalog now exists:
  - source: `src/data/pictures.c`
  - generated file: `game-content/.../GeneratedLegacyPictureCatalog.kt`
- Extraction is automated through `:tools-mapconv`.
- The runtime uses a registry to:
  - resolve legacy tile IDs to picture names
  - select extracted sheets by picture-name family
  - map tank frames by legacy-style `TANxx-n` group semantics
  - map turret frames by legacy-style `TURx-y` slot semantics
- This is a meaningful step toward parity, but not final visual parity.

### Tools
- `:tools-mapconv` can now:
  - convert legacy `.MAP` to canonical JSON
  - extract a generated legacy picture catalog from `src/data/pictures.c`

### Tests And Build Health
- Build currently passes through Amper.
- Covered test areas currently include:
  - legacy object parsing
  - simulation bootstrap from canonical maps
  - generated legacy asset registry behavior
- Verified commands have included:
  - `./amper build`
  - `./amper task :app-desktop:compileJvm`
  - targeted JVM test tasks such as `:game-content:testJvm`, `:game-sim:testJvm`, and `:game-legacy:testJvm`

## What Is Stable Enough To Build On
- Module boundaries
- Desktop-first app structure
- Legacy import-once pipeline
- Canonical content schema
- Deterministic fixed-step sim scaffold
- Compose shell + Kubriko viewport integration
- Content-backed asset loading from `unpacked/`
- Generated picture catalog workflow

## What Is Still Prototype-Level
- Collision rules
- Tank handling feel
- Weapon behavior beyond the first projectile path
- AI
- HUD/radar accuracy
- audio runtime
- map/object parity breadth
- exact frame-to-frame legacy visual fidelity
- split-screen and multiple cameras
- editor implementation
- save/config UX

## Remaining Steps

### 1. Tank Rendering Semantics
- Extend tank state to separate:
  - body direction
  - turret direction
  - elevation/height rendering state if needed
- Port the legacy `dir` / `ldir` concept into sim/render state.
- Render tanks with independent turret rotation where applicable.
- Distinguish static-body, rotating-turret, and helicopter/plane presentation logic.

### 2. Tile And Object Visual Parity
- Replace remaining family-based frame fallback with exact frame tables where possible.
- Build explicit mappings for:
  - walls
  - roads
  - bridges/runways
  - bushes/trees
  - buildings and shelters
  - special structures and decorations
- Extend the generated-catalog pipeline so render mappings can be data-driven instead of hand-coded.
- Cover night world assets properly instead of treating them as a shallow special case.

### 3. Gameplay Collision And Terrain Rules
- Implement terrain solidity and movement interaction.
- Port key collision rules:
  - tank vs terrain
  - tank vs tank
  - projectile vs terrain
  - projectile vs object
  - wraparound edge interactions
- Introduce terrain/material metadata needed by sim, not just render.
- Start deriving collision/material behavior from legacy picture metadata, not only visual families.

### 4. Combat Basics
- Flesh out weapons beyond the current basic projectile path.
- Add:
  - main cannon feel
  - chain gun
  - mines
  - rocket/mortar path distinctions
  - explosion damage
  - death/respawn loop
- Wire sim event output to proper effect spawning and audio events.

### 5. Tank Definitions And Content
- Import or reconstruct richer tank definitions from legacy data.
- Carry tank gameplay properties into canonical content:
  - armor
  - speed/turning
  - weapon mounts
  - turret style
  - special capabilities
- Stop relying on placeholder/default tank behavior in the sim.

### 6. Turrets And Other Object Families
- Expand turret logic beyond placement and rendering.
- Add actual targeting, cooldown, and firing behavior.
- Implement more imported object families as gameplay participants:
  - goals
  - flags
  - warps
  - locks
  - destroyers
  - enforcers
  - pickups/products

### 7. Modes And Mission Logic
- Implement mission flow and objective evaluation.
- Add representative game modes:
  - single-player mission flow
  - duel
  - race
  - flag/capture variants
- Preserve mission text and progression behavior in desktop shell UX.

### 8. AI
- Introduce controller abstraction that emits the same intents as players.
- Port first-pass tank AI behavior:
  - activation
  - target acquisition
  - basic steering
  - firing decisions
  - waypoint following
- Add sanity tests and replay-based checks.

### 9. HUD, Radar, And Overlay
- Replace the current debug-like status panel with proper in-game HUD behavior.
- Implement:
  - health/ammo/fuel indicators
  - radar/minimap
  - target/status overlays
  - mission feedback
- Decide which overlay elements stay in Kubriko vs Compose shell.

### 10. Audio
- Add event-driven audio runtime.
- Use `unpacked/sound/*.wav` as the first backend source.
- Port spatialization/panning logic conceptually from legacy code.
- Keep audio backend isolated from simulation.

### 11. Editor
- Start `:app-editor` as a real standalone tool rather than only a module shell.
- Implement:
  - viewport
  - palette panels
  - property inspector
  - validation
  - canonical map save/load
  - playtest launch
- Keep raw legacy map editing out of scope; use import-convert-edit.

### 12. Replay / Protocol Foundations
- Formalize frame logs and checksums in `:game-protocol`.
- Add replay recording for deterministic smoke scenarios.
- Keep this input-driven so networking can be added later without redesign.

### 13. Testing Expansion
- Add tests for:
  - collision behavior
  - weapon rules
  - map parsing round-trips
  - replay determinism
  - content registry correctness
  - representative import fixtures
- Add renderer smoke coverage for more imported maps.

### 14. Asset And Data Tooling
- Extend the converter to generate more canonical support artifacts from legacy sources.
- Consider generated registries for:
  - picture metadata
  - terrain/material behavior
  - sound ID mapping
  - tank/turret symbolic frame tables
- Reduce handwritten parity logic where legacy source data can be extracted instead.

## Recommended Immediate Sequence
1. Add separate body/turret direction to tank sim state.
2. Render independent tank turret rotation using explicit legacy frame semantics.
3. Implement terrain and collision rules.
4. Complete the first combat loop with real damage and explosions.
5. Add one objective/mode loop end-to-end on imported maps.
6. Start replay-based parity checks.

## Current Risks
- Visual parity is still approximate even though rendering is now asset-backed.
- Simulation rules are still far behind the render/import progress.
- Generated picture catalogs solve lookup drift, but not yet collision/material metadata drift.
- Editor scope can still grow too quickly unless constrained to canonical maps and playtest.
- Kubriko integration is working, but stress points like split-screen, effects, and overlay complexity still need proof.

## Acceptance Bar For The Next Major Milestone
- One imported map renders with stable content-backed visuals.
- One tank moves and fires with terrain interaction.
- One turret can target and attack.
- Damage and explosion flow works.
- Mission/objective shell can start and end a representative encounter.
- Build and tests stay green.
