# Tank Arena Full Rewrite Status

As of 2026-05-06.

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
- 100 Hz authoritative server tick to match the legacy game; replay/network snapshots decimated to ~10 Hz. See [ADR 0002](rewrite/adrs/0002-fixed-step-50hz.md).

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
- Generated legacy picture catalog from `src/data/pictures.c`.
- Extracted sprite sheets in active use: `floors.png`, `walls.png`, `building.png`, `trees.png`, `tank1.png`–`tank12.png`, `towers.png`, `animations.png`.

### Server (`:game-server`)
- Headless Kubriko per match; authoritative simulation.
- `TerrainSlideManager` provides axis-by-axis MTV sliding response.
- Server actors: `ServerTankActor`, `ServerTurretActor`, `ServerProjectileActor`, `ServerWallActor`, `ServerGoalActor` — each `Collidable` + `Serializable<T>`; the placed ones are also `Editable<T>`.
- Tank: hull/turret directions, acceleration, friction, primary-fire cooldown, MTV collision response against walls and other tanks.
- Projectile: owner-kind tagging, TTL, out-of-bounds flushing, owner immunity.
- Turret: target acquisition, rotation via 16-step compass, cooldown-gated fire using imported delay/power.
- Goal capture: once-only claim, contribution accumulation, `MissionWon` at 100%.
- Tank lifecycle: damage → `DamageTaken`; destruction → `TankDestroyed` (with lives decrement); 60-tick respawn countdown → `TankSpawned`.
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

### Replication (`:game-protocol`)
- `WorldSnapshot`, `ActorState` sealed hierarchy keyed by stable `actorId: Long`, `GameEvent`, `PlayerView`, `HudState`, `RadarContact`, `ServerFrame`.
- `PlayerIntentFrame` / `InputFrame` for client → server input.
- Replay protocol scaffolding present.

### Tooling (`:tools-mapconv`)
- `scene` (single legacy `.MAP` → scene JSON + sidecar) and `scene-all` (regenerate full set).
- Generated legacy picture catalog extraction.

### Tests
- Server: bootstrap, tick round-trip, sliding collision, firing/cooldown, wall cleanup, turret aim, goal capture, tank lifecycle, AI motion/fire, `PlayerView` HUD/radar wiring, scene-JSON round trips, scene-bootstrap. ~40 tests green.
- Content: legacy parsing, generated asset registry, supported subset of object parsing.

## 5. What Is Still Prototype-Level

- Movement feel: no terrain-material modifiers; fixed acceleration/friction model.
- Tank-vs-tank collision works, but per-vehicle handling differences (helicopters/planes) are absent.
- Weapons: only the primary cannon. Missing chain gun, mines, rockets, mortars, area damage, real explosion sprites/effects.
- Damage model is straight armor-decrement; no shield, invulnerability, or fuel-consumption rules yet.
- Terrain semantics: top-layer is decoration; no per-material rules (mud/ice/water/sand/runway), no bridge/runway/pit logic.
- World object behavior: only goals, walls, and turrets execute. Flags, products, locks, warps, destroyers, enforcers, trains, zeppelin, B52 are imported but inert.
- AI: only "pick nearest, steer, fire when aligned". No pathing, line-of-sight, waypoints, or per-mode behavior.
- Mission/objective evaluation beyond goal-capture and tank-elimination is not wired.
- HUD/radar parity: first-pass only (no scoring panel, no weapon select, no debrief polish).
- Audio: no runtime backend.
- Split-screen, multiple cameras, gamepad, action-mapping/remapping UI: absent.
- Replay recording/playback and deterministic checksums: scaffolded, not implemented.
- Save/config/profile UX: absent.
- Editor validation/playtest layered on top of `SceneEditor`: not yet wired.

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
