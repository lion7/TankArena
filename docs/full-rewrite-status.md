# Tank Arena Full Rewrite Status

As of 2026-05-11. Phase 1 + Phase 3 + Phase 4 + Phase 5 + Phase 6 closed — parity tasks T01–T06, T08–T28 landed on `rewrite`. T07 (drop legacy parsing surface) deliberately deferred — `:game-content` `LegacyMap*` and `:game-server` `legacy/*` still ship while downstream tasks lean on `CanonicalMapDefinition` for fixtures and bootstrap.

This is the single high-level status reference for the Kotlin rewrite of Tank Arena. It states current state only. For background and supporting detail:

- Architecture, module boundaries, replication contract: [`docs/rewrite/architecture.md`](rewrite/architecture.md)
- Map/content format: [`docs/rewrite/content-format.md`](rewrite/content-format.md)
- Build commands: [`docs/rewrite/build.md`](rewrite/build.md)
- Risks: [`docs/rewrite/risk-register.md`](rewrite/risk-register.md)
- Testing: [`docs/rewrite/testing-strategy.md`](rewrite/testing-strategy.md)
- Parity fixtures: [`docs/rewrite/parity-fixtures.md`](rewrite/parity-fixtures.md)
- ADRs: [`docs/rewrite/adrs/`](rewrite/adrs/)
- Earlier plans, deleted modules, collapse step log: [`docs/rewrite/history.md`](rewrite/history.md)

The parity target — what "feature complete" means in gameplay terms — is described in [`docs/game/`](game/): `overview.md`, `mechanics.md`, `vehicles.md`, `weapons.md`, `terrain.md`, `objects.md`, `ai.md`, `map-format.md`.

## 1. Goals

The rewrite is a full Kotlin rewrite of the original C/Allegro Tank Arena, not a line-by-line port. The goals:

- Preserve gameplay intent, content breadth, and feel of the original.
- Remove Allegro- and hardware-era architectural baggage.
- Keep the codebase maintainable, testable, and portable.
- Keep authoritative gameplay independent of rendering and UI frameworks.
- Desktop-first, with future WASM and networking unblocked.
- Rebuild the editor and tooling as first-class parts of the project.

## 2. Stack

- Kotlin, JVM-only at present.
- Compose Multiplatform for shell, menus, HUD, editor UI.
- Kubriko `0.1.2` from Maven Central for the gameplay viewport and the headless server tick.
- Amper for builds.
- 100 Hz authoritative server tick (`FixedStepClock.TICKS_PER_SECOND = 100`, `MILLIS_PER_TICK = 10`) — landed in T01, every server `*_TICKS` constant is now grounded in the legacy C source (`define.h sec=100`, `tank.weap.speed[]`, `mine.active`). Replay/network snapshots decimate to ~10 Hz. See [ADR 0002](rewrite/adrs/0002-fixed-step-50hz.md).
- Schema versioning at the protocol/sidecar boundary (T02): `WorldSnapshot.protocolVersion`, `MapSceneSidecar.schemaVersion`. Loaders reject payloads from a future runtime.

## 3. Module Layout

Six modules. See [architecture.md](rewrite/architecture.md) for responsibilities and boundaries.

- `:game-protocol`
- `:game-content`
- `:game-server`
- `:game-client`
- `:game-editor`
- `:tools-mapconv`

## 4. What Works

### Build & content
- Amper multi-module build is the only build system; Gradle is gone.
- All 121 shipped legacy maps imported into `:game-content/resources/scenes/` as `(scene_*.json, metadata_*.json)` pairs.
- T05/T06: `LegacyObjectParser` covers every authored type (`TANK`, `B52`, `MAN`, `MINE`, `BONUS`, `TRAIN`, `WAGON`, `ZEPPELIN`, plus the existing turret/player/warp/flag/lock/goal/destroyer/enforcer/product set). Runtime-only types (rocket, abomb, mortar) are silently skipped per the original `load_map` switch. `AllShippedMapsImportCleanTest` pins `MapSceneSidecar.importNotes.isEmpty()` for every shipped map; the regenerated sidecars carry no informational drift notes.
- Generated legacy picture catalog from `src/data/pictures.c`.
- Extracted sprite sheets in active use: `floors.png`, `walls.png`, `building.png`, `trees.png`, `tank1.png`–`tank12.png`, `towers.png`, `animations.png`.

### Server (`:game-server`)
- Headless Kubriko per match; authoritative simulation.
- `TerrainSlideManager` provides axis-by-axis MTV sliding response.
- Server actors: `ServerTankActor`, `ServerTurretActor`, `ServerProjectileActor`, `ServerWallActor`, `ServerGoalActor`, `ServerMineActor`, `ServerRocketActor`, `ServerMortarActor`, `ServerFlagActor`, `ServerProductActor`, `ServerLockActor`, `ServerWarpActor`, `ServerDestroyerActor`, `ServerEnforcerActor`, `ServerTrainActor`, `ServerZeppelinActor`, `ServerB52Actor` — each `Collidable` + `Serializable<T>`; the placed ones are also `Editable<T>`. `ActorRoundTripTest` (T04) pins `save()`/`restore()` for every `Server*Actor` so silent schema drift fails fast.
- AI subsystem (T26–T28): `TilePathfinder` (A* over solid layer), `LineOfSight` (Bresenham raycast), `WaypointFollower` (path-following between waypoints), `AiModeDispatcher` (mode-specific patrol/aggressive behavior). AI navigates around walls, fires only when target is visible, and patrols waypoints when no enemy is present.
- Tank: hull/turret directions, acceleration, friction, MTV collision response against walls and other tanks. Tank-vs-tank collision (T08) zeros only the into-contact velocity component, preserving tangential motion — head-on / perpendicular-nudge / three-tank pile-up are exercised in tests.
- Weapons (T09–T12): `WEAPON_MAIN`, `WEAPON_CHAIN`, `WEAPON_MINE`, `WEAPON_ROCKET`, `WEAPON_MORTAR`, all routed through `firePrimary` with per-weapon cooldowns and ammo. `cycleWeaponLeft/Right` advances through owned weapons, skipping any with empty ammo.
  - Main cannon: 70-tick refire (legacy `weap.speed[0]=70`), TTL=31.
  - Chain gun: 10-tick refire (`weap.speed[1]=10`), 165 px range (`5*b_size`, TTL=21), 10 damage/shot, 1500 initial ammo.
  - Mines: deployable, 100-tick activation grace period (owner-immunity window), proximity trigger via `triggerMineContacts` over (mine.radius + tank-half).
  - Rockets: nearest-enemy lock at fire-time, accelerate to ROCKET_MAX_SPEED with a per-tick turn-rate clamp, TTL=330 matching the legacy 3300 px range.
  - Mortars: arc travel (`MORTAR_TRAVEL_TICKS=50`), then linear-falloff area damage to `MORTAR_MAX_RADIUS_PX=60`.
- Area-of-effect (T13): mines, mortars, and rockets all route their detonation through a shared `AreaDamageResolver` that emits a single `GameEvent.Explosion(x, y, radius, kind)` (`ExplosionKind.MINE/MORTAR/ROCKET/ABOMB`) and applies linear falloff from the tank's hull edge so grazing hits still register. Owner immunity is opt-in per weapon (rockets immune, mortars/mines not).
- Damage model (T14): tank state carries `shield`, `invulnerableTicks`, and existing `armor`/`fuel`. Damage routes through invulnerability (full block) → shield (absorb) → armor; shield decays over time (`SHIELD_DECAY_TICKS=50`); fuel exhaustion blocks acceleration (already enforced); both fields exposed on `HudState` (`shield`, `invulnerableTicks`) and `TankState.invulnerable`. Respawn refills armor/fuel and clears shield/invuln.
- Projectile: owner-kind tagging, per-weapon TTL, out-of-bounds flushing, owner immunity.
- Turret: target acquisition, rotation via 16-step compass, cooldown-gated fire using imported delay/power.
- Goal capture: once-only claim, contribution accumulation, `MissionWon` at 100%.
- Flag capture (T19): pickup within 16px radius, flag follows carrier, returns to home on carrier death. `GameEvent.FlagCaptured`, `FlagReturned`, `FlagDelivered`.
- Product pickup (T20): within 16px radius, marked collected, idempotent. `GameEvent.ProductCollected` with price.
- Warps (T22): 15px radius teleport, 30-tick cooldown to prevent loops.
- Destroyers (T23): immediate-mode triggers on first tick with area damage via `AreaDamageResolver`.
- Enforcers (T24): AI tanks within radius adopt enforced weapon (`applyEnforcedWeapon`).
- Trains (T25): forward motion with world-wrap. Zeppelins sweep horizontally. B52 flies and drops bombs every 500 ticks (10 max, radius 60, damage 20).
- Tank lifecycle: damage → `DamageTaken`; destruction → `TankDestroyed` (with lives decrement); 300-tick respawn countdown (`tnk.motion.dead=tinit(300)`) → `TankSpawned`.
- Mission win when all enemy tanks/turrets are dead; mission loss when all player lives are spent.
- First-pass AI for mobile enemy tanks: nearest opposing tank, body+turret steering via `PlayerIntentFrame`, fire when aligned and in range.
- `PlayerView` generation: camera on controlled tank, HUD (armor/fuel/lives/mission progress/code/status), radar of tanks/turrets/goals.
- Bootstraps from scene JSON via `ServerMatchPrototype.fromSceneJson(scene, metadata)`.

### Client (`:game-client`)
- Compose Desktop shell: main menu, mode selection, mission selection, debrief.
- In-process `LocalMatchClient` boot path (`fromSceneJson`).
- Kubriko-hosted gameplay viewport with `ScenePreloadManager` for sprite preload.
- `ClientScene.sync(snapshot)` matches client render actors to server `actorId`s, spawning/removing as needed.
- HUD overlay (ARMOR / FUEL / LIVES / MISSION / TANK + mission code) and radar overlay, both driven by `PlayerView`.
- Keyboard movement + space-fire input; mouse pointer drives 8-way turret aim (with dead zone).
- Camera follows the controlled tank.
- Audio (T15/T16): `AudioBackend` interface with `DesktopAudioBackend` implementation — distance attenuation (Euclidean, 612 px max range), stereo panning, pitch variation (900–1100), ≤32 silenced voices. `AudioManager` routes `GameEvent.Sound` events from the server to the backend. `AudioMathTest` pins the distance/pan/pitch formulas.
- Terrain (T17/T18): `TerrainMaterial` enum + `TerrainGrid` in `:game-protocol`; `PictureCatalog` in `:game-content` maps (world, picture-index) → (material, speed) from `src/data/pictures.c`; `TerrainGridBuilder` constructs per-map grids from `TileLayers.base` + `.top` with speed multiplication and top-layer material override. `ServerMatchPrototype` builds the grid from `CanonicalMapDefinition` and calls `applyTerrainEffects()` each tick: terrain speed multiplier clamps max forward/reverse speed, lava deals 1 damage/tick, water kills instantly, big/small pits kill when tank center enters the danger zone. `TerrainMaterialTest` (32 tests) + `TerrainGridBuilderTest` (12 tests) pin catalog resolution, layer merging, and grid lookup.

### Editor (`:game-editor`)
- Thin Compose Desktop app embedding Kubriko's `SceneEditor`.
- Reuses `tankArenaSerializableMetadata` so placed actors round-trip with the server and client.
- Opens `:game-content/resources/scenes/` by default for round-trip editing of shipped scenes.
- T03 boundary guard: `EditorBoundaryGuardTest` scans `:game-editor/src` for forbidden simulation symbols (`CollisionManager`, `TerrainSlideManager`, `ServerMatchPrototype`); accidentally introducing such an import fails the build.

### Replication (`:game-protocol`)
- `WorldSnapshot`, `ActorState` sealed hierarchy keyed by stable `actorId: Long`, `GameEvent`, `PlayerView`, `HudState`, `RadarContact`, `ServerFrame`.
- `PlayerIntentFrame` / `InputFrame` for client → server input.
- Replay protocol scaffolding present.

### Tooling (`:tools-mapconv`)
- `scene` (single legacy `.MAP` → scene JSON + sidecar) and `scene-all` (regenerate full set).
- Generated legacy picture catalog extraction.

### Tests
- Server: bootstrap, tick round-trip, sliding collision (head-on / perpendicular / three-tank pile-up), per-weapon firing/cooldown, chain-gun rate + ammo, mine activation grace + proximity detonation, rocket steering / TTL / impact, mortar travel + falloff, shield/invulnerability/fuel-out damage routing (T14), wall cleanup, turret aim, goal capture, tank lifecycle, AI motion/fire, `PlayerView` HUD/radar wiring, scene-JSON round trips, scene-bootstrap, per-actor `save()`/`restore()` round-trip, schema-version reject, every-shipped-map import-clean, editor boundary guard, terrain material catalog resolution (32 tests), terrain grid builder layer merging (12 tests), flag capture/return (8 tests), product pickup (6 tests), A* pathfinding (9 tests), line-of-sight raycast (8 tests), waypoint follower (5 tests), AI mode dispatcher (9 tests). 161 tests green.
- Content: legacy parsing, generated asset registry, supported subset of object parsing.

## 5. What Is Still Prototype-Level

- Movement feel: terrain-material speed multipliers are wired (T17) — oil slows, road/race track speeds up, lava/water/pit deal damage. Per-vehicle handling differences (helicopters/planes) are absent.
- Tank-vs-tank collision slides cleanly, but per-vehicle handling differences (helicopters/planes) are absent.
- Weapons: main, chain, mines, rockets, mortars are wired (T09–T12) and now share `AreaDamageResolver` + `GameEvent.Explosion` (T13). Flamethrower, A-bomb, smoke screen, invisibility, extra-speed, light, and deployed-men weapons are still missing; client-side explosion sprites/SFX are not yet wired off the new event.
- Damage model carries shield + invulnerability + fuel-out (T14), but pickups that grant shield/invuln/fuel (Phase 5 objects T20) and shield-pickup HUD affordance are not yet landed.
- Terrain semantics (T17/T18): per-material speed multipliers, lava damage, water kill, pit kill are wired. Bridge destruction → water reveal, runway takeoff, and fuel dump flame effects are deferred to later phases.
- World object behavior: flags, products, locks, warps, destroyers, enforcers, trains, zeppelins, B52 all produce scene actors and execute (T19–T25). Men, bonuses, and authored mines are imported but inert. Lock downstream effects (blow structure/destroy object) are deferred.
- AI: pathing (A* over solid grid, T26), line-of-sight (Bresenham raycast, T27), waypoint patrol (T28), per-mode behavior (SINGLE/DUALVC patrol, DUAL aggressive). AI navigates around walls, fires only when target is visible, and follows waypoints when no enemy is present.
- Mission/objective evaluation beyond goal-capture and tank-elimination is not wired.
- HUD/radar parity: first-pass only (no scoring panel, no weapon select, no debrief polish).

- Split-screen, multiple cameras, gamepad, action-mapping/remapping UI: absent.
- Replay recording/playback and deterministic checksums: scaffolded, not implemented.
- Save/config/profile UX: absent.
- Editor validation/playtest layered on top of `SceneEditor`: not yet wired.
- T07 (drop legacy parsing surface) deliberately deferred while later phases lean on `CanonicalMapDefinition`, `LegacyMapImporter`, and the `tools-mapconv scene*` subcommands for fixtures.

## 6. Gaps Against Parity

For the legacy gameplay rules that still need to land, the authoritative descriptions live in [`docs/game/`](game/):

- Armor/fuel/shield/invulnerability rules — [`mechanics.md`](game/mechanics.md)
- Tank/helicopter/plane families — [`vehicles.md`](game/vehicles.md)
- Weapon families and projectile rules — [`weapons.md`](game/weapons.md)
- Terrain materials and special tiles — [`terrain.md`](game/terrain.md)
- Object families (goals, flags, warps, locks, destroyers, enforcers, products, trains, zeppelin, B52) — [`objects.md`](game/objects.md)
- AI activation, navigation, targeting — [`ai.md`](game/ai.md)
- Map structure and the legacy `.MAP` format — [`map-format.md`](game/map-format.md)
- Modes (single, dual, dual-vs-computer, editor) — [`overview.md`](game/overview.md)

## 7. Suggested Delivery Sequence

1. Broaden combat on `:game-server` actors: tank-vs-tank polish, additional weapons (chain gun, mines, rockets, mortars), area damage, explosion/effect sprites.
2. ~~Audio event plumbing and a basic backend~~ — done (T15/T16).
3. ~~Terrain/material rules (mud/ice/water/fuel/armor pickups, bridges, runways)~~ — done (T17/T18, 3cba470).
4. One end-to-end mission mode with objectives, win/loss evaluation, debrief.
5. AI for mobile units in that mode (navigation, line-of-sight).
6. ~~Broaden imported object family behavior (flags, warps, locks, destroyers, enforcers, products, etc.)~~ — done (T19–T25, 12223de/fc778db/489444f).
7. Editor validation + in-editor playtest.
8. Replay recording/playback, checksum determinism.
9. Save/config UX, packaging, performance pass.
10. Future-target work: networked play (drop in `RemoteMatchClient`), WASM enablement.

## 8. "Feature-Complete" Bar

The rewrite is feature-complete when:
- Representative legacy maps import and play end-to-end.
- All vehicle, weapon, turret, and common object families behave per `docs/game/`.
- Single-player missions and core multiplayer modes function.
- AI is good enough to support the original gameplay loops.
- Rendering, HUD, and audio are coherent and recognizable as Tank Arena.
- The editor authors and saves canonical scenes via `SceneEditor`.
- The desktop game is stable, packaged, and testable.
- The architecture still preserves a clean path to networking and additional targets.
