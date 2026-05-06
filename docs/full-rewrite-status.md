# Tank Arena Full Rewrite Status

As of 2026-05-06. Reflects parity tasks T01–T06 and T08–T12 landed on `rewrite`. T07 (drop legacy parsing surface) deliberately deferred — `:game-content` `LegacyMap*` and `:game-server` `legacy/*` still ship while downstream tasks lean on `CanonicalMapDefinition` for fixtures and bootstrap.

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
- Server actors: `ServerTankActor`, `ServerTurretActor`, `ServerProjectileActor`, `ServerWallActor`, `ServerGoalActor`, `ServerMineActor`, `ServerRocketActor`, `ServerMortarActor` — each `Collidable` + `Serializable<T>`; the placed ones are also `Editable<T>`. `ActorRoundTripTest` (T04) pins `save()`/`restore()` for every `Server*Actor` so silent schema drift fails fast.
- Tank: hull/turret directions, acceleration, friction, MTV collision response against walls and other tanks. Tank-vs-tank collision (T08) zeros only the into-contact velocity component, preserving tangential motion — head-on / perpendicular-nudge / three-tank pile-up are exercised in tests.
- Weapons (T09–T12): `WEAPON_MAIN`, `WEAPON_CHAIN`, `WEAPON_MINE`, `WEAPON_ROCKET`, `WEAPON_MORTAR`, all routed through `firePrimary` with per-weapon cooldowns and ammo. `cycleWeaponLeft/Right` advances through owned weapons, skipping any with empty ammo.
  - Main cannon: 70-tick refire (legacy `weap.speed[0]=70`), TTL=31.
  - Chain gun: 10-tick refire (`weap.speed[1]=10`), 165 px range (`5*b_size`, TTL=21), 10 damage/shot, 1500 initial ammo.
  - Mines: deployable, 100-tick activation grace period (owner-immunity window), proximity trigger via `triggerMineContacts` over (mine.radius + tank-half).
  - Rockets: nearest-enemy lock at fire-time, accelerate to ROCKET_MAX_SPEED with a per-tick turn-rate clamp, TTL=330 matching the legacy 3300 px range.
  - Mortars: arc travel (`MORTAR_TRAVEL_TICKS=50`), then linear-falloff area damage to `MORTAR_MAX_RADIUS_PX=60`.
- Projectile: owner-kind tagging, per-weapon TTL, out-of-bounds flushing, owner immunity.
- Turret: target acquisition, rotation via 16-step compass, cooldown-gated fire using imported delay/power.
- Goal capture: once-only claim, contribution accumulation, `MissionWon` at 100%.
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
- Server: bootstrap, tick round-trip, sliding collision (head-on / perpendicular / three-tank pile-up), per-weapon firing/cooldown, chain-gun rate + ammo, mine activation grace + proximity detonation, rocket steering / TTL / impact, mortar travel + falloff, wall cleanup, turret aim, goal capture, tank lifecycle, AI motion/fire, `PlayerView` HUD/radar wiring, scene-JSON round trips, scene-bootstrap, per-actor `save()`/`restore()` round-trip, schema-version reject, every-shipped-map import-clean, editor boundary guard. 68 tests green.
- Content: legacy parsing, generated asset registry, supported subset of object parsing.

## 5. What Is Still Prototype-Level

- Movement feel: no terrain-material modifiers; fixed acceleration/friction model.
- Tank-vs-tank collision slides cleanly, but per-vehicle handling differences (helicopters/planes) are absent.
- Weapons: main, chain, mines, rockets, mortars are wired (T09–T12); flamethrower, A-bomb, smoke screen, invisibility, extra-speed, light, deployed-men weapons are still missing. Real explosion sprites/effects and the unified `AreaDamageResolver` + `GameEvent.Explosion` from T13 are not landed yet — each new weapon presently runs its own per-tick area-damage pass.
- Damage model is straight armor-decrement; no shield, invulnerability, or fuel-consumption rules yet.
- Terrain semantics: top-layer is decoration; no per-material rules (mud/ice/water/sand/runway), no bridge/runway/pit logic.
- World object behavior: only goals, walls, and turrets execute. Flags, products, locks, warps, destroyers, enforcers, trains, zeppelin, B52, men, bonuses are imported by `LegacyObjectParser` but emit no scene actors yet — `CanonicalSceneBuilder` falls through to `else -> Unit` for these kinds.
- AI: only "pick nearest, steer, fire when aligned". No pathing, line-of-sight, waypoints, or per-mode behavior.
- Mission/objective evaluation beyond goal-capture and tank-elimination is not wired.
- HUD/radar parity: first-pass only (no scoring panel, no weapon select, no debrief polish).
- Audio: no runtime backend.
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
2. Audio event plumbing and a basic backend.
3. Terrain/material rules (mud/ice/water/fuel/armor pickups, bridges, runways).
4. One end-to-end mission mode with objectives, win/loss evaluation, debrief.
5. AI for mobile units in that mode (navigation, line-of-sight).
6. Broaden imported object family behavior (flags, warps, locks, destroyers, enforcers, products, etc.).
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
