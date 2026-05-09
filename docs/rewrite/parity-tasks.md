# Parity Task List

A flat, ordered list of implementation tasks toward the "feature-complete" bar in [`full-rewrite-status.md`](../full-rewrite-status.md) §8. Each task is self-contained: an autonomous agent should be able to pick one up given the linked spec docs and acceptance criteria without further conversation.

**Conventions for every task:**
- **Goal** — one sentence; what "done" means.
- **Spec** — authoritative behavior reference under [`docs/game/`](../game/).
- **Touch** — modules / files expected to change.
- **Acceptance** — observable, testable outcomes.
- **Tests** — what to add under `:game-server/test` (or sibling test source set).
- **Depends on** — other task IDs that must land first.

Tasks are ordered so dependencies flow forward. An agent may pick the lowest-numbered open task whose dependencies are satisfied.

**Progress (as of 2026-05-07):** Phase 1 + Phase 3 closed — T01–T06, T08–T16 done on `rewrite`. T07 deliberately deferred while later phases continue to lean on `CanonicalMapDefinition` / `LegacyMapImporter` for fixtures and bootstrap.

---

## Phase 0 — Foundation reconciliation

These unblock every parity task by removing inconsistencies in the engine substrate.

### T01 — Reconcile simulation tick rate to 100 Hz ✅ done (10478d4 / 93e22b3)
- **Goal:** the authoritative server simulates at 100 Hz everywhere; all `*_TICKS` constants are interpreted in 100 Hz units.
- **Spec:** [`mechanics.md`](../game/mechanics.md) §"Tick Rate"; [ADR 0002](adrs/0002-fixed-step-50hz.md).
- **Touch:** `game-protocol/.../FixedStepClock.kt` (set `TICKS_PER_SECOND = 100`); `game-server/.../ServerMatchPrototype.kt` (set `MILLIS_PER_TICK = 10`); audit every `*_TICKS` constant on `ServerTankActor`, `ServerProjectileActor`, `ServerTurretActor` against the legacy values referenced in `docs/game/`.
- **Acceptance:** all three locations agree; existing tests still pass after re-tuning numeric constants; `FixedStepClock.MILLIS_PER_TICK == 10`.
- **Tests:** add `FixedStepClockTest` asserting the 100 Hz constants; update existing cooldown/TTL tests to expect the new tick counts.
- **Depends on:** —

### T02 — Add a version field to scene metadata and `WorldSnapshot` ✅ done (903661c)
- **Goal:** wire/scene compatibility is detectable at load.
- **Spec:** [`risk-register.md`](risk-register.md) "Server/Client Replication Coupling".
- **Touch:** `game-protocol/.../snapshot/WorldSnapshot.kt`; `game-content/.../MapSceneSidecar.kt` (or scene JSON header); deserialization paths in `:game-server` and `:game-client`.
- **Acceptance:** `WorldSnapshot` carries `protocolVersion: Int`; sidecar carries `schemaVersion: Int`; loading a scene whose version is newer than the runtime fails fast with a clear error.
- **Tests:** version-mismatch deserialization test (sidecar + snapshot).
- **Depends on:** —

### T03 — Editor boundary guard ✅ done (10478d4)
- **Goal:** `:game-editor` cannot transitively reference `CollisionManager`, `TerrainSlideManager`, or the mission evaluator from `:game-server`.
- **Spec:** [ADR 0009](adrs/0009-editor-wraps-kubriko-scene-editor.md) §"Boundary rule".
- **Touch:** add a test in `:game-editor` that scans the runtime classpath for forbidden symbols, or a build assertion in its `module.yaml`.
- **Acceptance:** introducing such an import fails the build/tests.
- **Tests:** the guard itself is the test.
- **Depends on:** —

### T04 — Round-trip test for every `Server*Actor` ✅ done (427338d)
- **Goal:** silent schema drift in actor `save()` is caught.
- **Touch:** `game-server/test/.../ActorRoundTripTest.kt`.
- **Acceptance:** for each `ServerWallActor`, `ServerTankActor`, `ServerTurretActor`, `ServerGoalActor`, `ServerProjectileActor`, the test constructs a non-default instance, calls `save()`, deserializes via `tankArenaSerializableMetadata`, and asserts equality on observable state.
- **Tests:** the test itself is the deliverable.
- **Depends on:** —

---

## Phase 1 — Finish legacy import, then drop it

### T05 — Parse remaining legacy object families ✅ done (32c8eab)
- **Note on scope landed:** `LegacyObjectParser` now branches on every authored type the legacy `load_map()` switch handles (`TANK`, `B52`, `MAN`, `MINE`, `BONUS`, `TRAIN`, `WAGON`, `ZEPPELIN`, plus the existing turret/player/warp/flag/lock/goal/destroyer/enforcer/product set). Runtime-spawned types (`ROCKET`, `ABOMB`, `MORTAR`) are silently dropped. New `Server*Actor` types for the new families are intentionally **not** added here — `CanonicalSceneBuilder` falls through to `else -> Unit` for those kinds, and the corresponding actors land in T10/T11/T12/T25 alongside their runtime behavior.
- **Goal:** every type listed in [`objects.md`](../game/objects.md) §"Object Type Index" is mapped by `LegacyObjectParser` (or explicitly classified as runtime-spawned and therefore skipped) — no shipped map produces a non-empty `MapSceneSidecar.importNotes`.
- **Spec:** [`objects.md`](../game/objects.md) (full document).
- **Touch:** `game-server/.../legacy/LegacyObjectParser.kt`, `LegacyCanonicalConverter.kt`, `LegacyToSceneJson.kt`. Add `AuthoredObject` variants and matching `Server*Actor` types where missing (mines, rockets, mortars, B52, zeppelin, train, man, light source, smoke, bonus, A-bomb).
- **Acceptance:** running `tools-mapconv scene-all` over all 121 maps produces sidecars with `importNotes.isEmpty() == true`.
- **Tests:** `AllShippedMapsImportClean` integration test that walks `original/*.MAP`, runs `LegacyMapImporter`, and asserts empty `importNotes` for every result.
- **Depends on:** T01 (cooldown/TTL semantics).

### T06 — Commit regenerated scenes ✅ done (0df24ed)
- **Goal:** the 121 scene + sidecar pairs in `:game-content/resources/scenes/` reflect the full importer.
- **Touch:** run `tools-mapconv scene-all`; commit the resulting JSON.
- **Acceptance:** `:game-server` test suite green against the new scenes.
- **Depends on:** T05.

### T07 — Drop the legacy parsing surface ⏸ deferred
- **Status:** intentionally on hold. Phase 2+ tests and `ServerMatchPrototype.fromCanonicalMap` continue to consume `CanonicalMapDefinition` / `LegacyMapImporter` for fixtures. Revisit once weapon/AI/object behavior is stable enough to retire those code paths.
- **Goal:** legacy parsing is removed from the build.
- **Spec:** [ADR 0003](adrs/0003-import-once-canonical-map-format.md) §"Decision" drop list.
- **Touch:** delete `game-content/.../LegacyMap*`, `game-server/.../legacy/*`, `tools-mapconv` scene/scene-all subcommands; move `original/` outside the build's source set; update `architecture.md`, `content-format.md`, `history.md`, `full-rewrite-status.md` to note legacy parsing is gone.
- **Acceptance:** full project build green; no symbol named `Legacy*` remains under `game-content/src` or `game-server/src`.
- **Depends on:** T06.

---

## Phase 2 — Combat parity on `:game-server`

### T08 — Tank-vs-tank collision polish ✅ done (3eb8533)
- **Goal:** tanks slide cleanly when they push into each other; no jitter, no stuck-overlap.
- **Spec:** [`mechanics.md`](../game/mechanics.md), [`vehicles.md`](../game/vehicles.md).
- **Touch:** `TerrainSlideManager` + `ServerTankActor` collision response.
- **Acceptance:** a head-on push between two tanks resolves with both sliding apart; a perpendicular nudge slides along the contact axis.
- **Tests:** scenario tests in `:game-server` covering head-on, perpendicular, and three-tank pile-up.
- **Depends on:** T01.

### T09 — Chain gun ✅ done (5d6b080)
- **Goal:** chain gun weapon fires a fast burst with the legacy damage/cooldown values.
- **Spec:** [`weapons.md`](../game/weapons.md) "Chain Gun".
- **Touch:** new `ProjectileKind.ChainGun` (or weapon select on existing projectile), `ServerTankActor` weapon switching, ammo tracking on `TankState`.
- **Acceptance:** firing chain gun matches legacy fire rate / damage / range; ammo decrements; out-of-ammo silences.
- **Tests:** weapon-fire test pinning fire interval and damage to `weapons.md` values.
- **Depends on:** T01, T05 (bonus pickups for ammo).

### T10 — Mines ✅ done (e7632c2)
- **Goal:** mines deploy under a tank, persist, and detonate on contact with any tank that isn't the owner.
- **Spec:** [`weapons.md`](../game/weapons.md) "Mines"; [`objects.md`](../game/objects.md) §"Mines".
- **Touch:** `ServerMineActor` (new), spawn flow on `ServerTankActor`, collision rules, area damage on detonation.
- **Acceptance:** placed mine remains static; contact triggers explosion within the legacy radius/damage; owner-immunity for a short grace period.
- **Tests:** mine placement, owner immunity, detonation radius.
- **Depends on:** T01.

### T11 — Rockets (guided) ✅ done (a73ba9e)
- **Goal:** rockets accelerate toward an aim point with the legacy turn rate.
- **Spec:** [`weapons.md`](../game/weapons.md) "Rockets"; [`objects.md`](../game/objects.md) §"Rocket".
- **Touch:** `ServerRocketActor` (new), guidance update step, fuel/TTL.
- **Acceptance:** rocket steering, max turn rate, TTL, and impact damage match `weapons.md`.
- **Tests:** turn-rate test, TTL expiry, terrain impact.
- **Depends on:** T01.

### T12 — Mortars ✅ done (219843c)
- **Goal:** lobbed projectiles with arc + impact area damage.
- **Spec:** [`weapons.md`](../game/weapons.md) "Mortar".
- **Touch:** `ServerMortarActor` (new), impact resolver shared with mines/rockets.
- **Acceptance:** travel time, blast radius, damage falloff match `weapons.md`.
- **Tests:** parametric test over impact radii.
- **Depends on:** T01.

### T13 — Area damage + explosion event ✅ done
- **Goal:** a single shared "apply area damage at point" routine; emits a `GameEvent.Explosion` consumable by client effects + audio.
- **Spec:** [`mechanics.md`](../game/mechanics.md), [`weapons.md`](../game/weapons.md).
- **Touch:** new `AreaDamageResolver` in `:game-server`; `GameEvent.Explosion(x, y, radius, kind)` in `:game-protocol`.
- **Acceptance:** mine, mortar, rocket, and A-bomb all route through the same resolver; events reach the client.
- **Tests:** resolver applies falloff correctly across multiple targets.
- **Depends on:** T10, T11, T12.

### T14 — Damage model: shield, invulnerability, fuel ✅ done
- **Goal:** tanks track shield + invulnerability timer + fuel; damage routes through shield first; fuel drains while moving.
- **Spec:** [`mechanics.md`](../game/mechanics.md) §"Damage", [`vehicles.md`](../game/vehicles.md).
- **Touch:** `ServerTankActor` state + tick step; `TankState` + `HudState` to expose new fields.
- **Acceptance:** shield depletes before armor; invulnerability ticks down and blocks damage while active; running out of fuel disables movement.
- **Tests:** damage routing, invulnerability window, fuel-out immobilization.
- **Depends on:** T01.

---

## Phase 3 — Audio

### T15 — Audio event plumbing ✅ done (491e2e1)
- **Goal:** `:game-protocol` carries audio events; `:game-client` exposes a pluggable backend.
- **Spec:** [`mechanics.md`](../game/mechanics.md) §"Sound System" (distance attenuation, stereo panning, pitch variation).
- **Touch:** `GameEvent.Sound(kind, x, y)` in `:game-protocol`; client-side `AudioBackend` interface + a stub.
- **Acceptance:** firing, explosion, pickup all emit `GameEvent.Sound`; client receives them.
- **Tests:** server emits the right events; client routes them to the backend.
- **Depends on:** T13.

### T16 — Default audio backend (desktop) ✅ done (491e2e1)
- **Goal:** working sound playback on desktop with legacy distance/pan/pitch math.
- **Spec:** [`mechanics.md`](../game/mechanics.md) §"Sound System" (Euclidean distance, 612 px max range, pan formula, pitch 900–1100, ≤32 silenced).
- **Touch:** `:game-client` audio backend implementation.
- **Acceptance:** at-distance attenuation matches the formulas in `mechanics.md` within rounding.
- **Tests:** unit-test the distance/pan math; manual smoke for actual playback.
- **Depends on:** T15.

---

## Phase 4 — Terrain materials

### T17 — Terrain material rules
- **Goal:** ground tiles modulate vehicle motion (mud/ice/water/sand/runway).
- **Spec:** [`terrain.md`](../game/terrain.md).
- **Touch:** `:game-server` motion step in `ServerTankActor`; `MapSceneSidecar.tileLayers` already carries the data; introduce a `TerrainMaterial` enum + lookup.
- **Acceptance:** acceleration/friction differ per material per `terrain.md`; water with a non-amphibious vehicle triggers a splash event.
- **Tests:** parametric test over materials.
- **Depends on:** T01.

### T18 — Bridge / runway / pit logic
- **Goal:** crossings, takeoff strips, and pits behave per legacy.
- **Spec:** [`terrain.md`](../game/terrain.md) §"Bridges", §"Runways", §"Pits".
- **Touch:** `:game-server` collision/motion; `:game-content` material classification.
- **Acceptance:** entering a bridge changes pass/blocking semantics correctly; runways enable plane takeoff; pits trap or destroy ground vehicles per spec.
- **Tests:** scenario tests per terrain feature.
- **Depends on:** T17.

---

## Phase 5 — World object behaviors

Each task adds runtime behavior for an imported but currently inert object family. All depend on T05 (placement is parsed) and T13 (where damage applies).

### T19 — Flags
- **Spec:** [`objects.md`](../game/objects.md) §"Flag"; [`mechanics.md`](../game/mechanics.md) capture rules.
- **Touch:** `ServerFlagActor` (or extend goal logic), `GameEvent.FlagCaptured`.
- **Acceptance:** capture/return rules match spec; HUD updates.

### T20 — Products
- **Spec:** [`objects.md`](../game/objects.md) §"Product".
- **Touch:** `ServerProductActor` pickup → tank inventory delta.
- **Acceptance:** legacy bonus distribution preserved.

### T21 — Locks
- **Spec:** [`objects.md`](../game/objects.md) §"Lock".
- **Touch:** `ServerLockActor`, conditional trigger evaluation.
- **Acceptance:** lock fires once condition met; downstream effects propagate.

### T22 — Warps
- **Spec:** [`objects.md`](../game/objects.md) §"Warp".
- **Touch:** `ServerWarpActor`, teleport step in `ServerTankActor`.
- **Acceptance:** entering a warp moves the tank to its paired exit; cooldown prevents loops.

### T23 — Destroyers
- **Spec:** [`objects.md`](../game/objects.md) §"Destroyer".
- **Touch:** `ServerDestroyerActor`, area destroy on trigger using T13's resolver.
- **Acceptance:** `d.what` switch (walls/objects/both) honored; immediate-mode triggers on map load.

### T24 — Enforcers
- **Spec:** [`objects.md`](../game/objects.md) §"Enforcer".
- **Touch:** `ServerEnforcerActor`; AI override path in T26.
- **Acceptance:** AI tanks within radius adopt enforced weapon; auto-fire at delay.

### T25 — Trains, Zeppelin, B52
- **Spec:** [`objects.md`](../game/objects.md) §"Train", §"Zeppelin", §"B52".
- **Touch:** `ServerTrainActor`, `ServerZeppelinActor`, `ServerB52Actor`; B52 bombing loop.
- **Acceptance:** scripted paths, drop patterns match spec.
- **Tests:** scripted path; drop interval; damage radius.

---

## Phase 6 — AI

### T26 — Pathing on the tile grid
- **Goal:** AI tanks navigate around walls.
- **Spec:** [`ai.md`](../game/ai.md) §"Navigation".
- **Touch:** `:game-server` AI; pathing helper in `:game-server` (A* over the passability grid in `MapSceneSidecar.tileLayers`).
- **Acceptance:** AI reaches targets across non-trivial maps without wall-bumping.
- **Tests:** path success on hand-crafted obstacle maps.
- **Depends on:** T08.

### T27 — Line-of-sight
- **Goal:** AI fires only when target is visible.
- **Spec:** [`ai.md`](../game/ai.md) §"Targeting".
- **Touch:** raycast against passability grid + walls.
- **Acceptance:** AI does not fire through walls; engages once LOS is clear.
- **Tests:** LOS unit tests against fixture grids.
- **Depends on:** T26.

### T28 — Waypoints + per-mode behavior
- **Goal:** AI follows authored waypoints; mode-specific behaviors trigger.
- **Spec:** [`ai.md`](../game/ai.md), [`overview.md`](../game/overview.md) modes.
- **Touch:** `WaypointFollower` AI step, mode dispatcher in mission evaluator.
- **Acceptance:** AI follows authored paths; modes dispatch their entry behavior.
- **Tests:** waypoint advancement; mode entry events.
- **Depends on:** T27.

---

## Phase 7 — Mission evaluation, HUD, debrief

### T29 — Full mission/objective evaluation
- **Goal:** mission states beyond goal-capture and tank-elimination evaluate per mode.
- **Spec:** [`overview.md`](../game/overview.md) modes; [`mechanics.md`](../game/mechanics.md) §"Mission".
- **Touch:** mission evaluator in `:game-server`; emits `MissionWon` / `MissionLost` with reason.
- **Acceptance:** every mode listed in `overview.md` reaches a win/loss state per spec.
- **Tests:** one scenario per mode.
- **Depends on:** T19, T22, T23, T28.

### T30 — Scoring panel
- **Goal:** HUD shows score, kills, captures, time, per spec.
- **Spec:** [`mechanics.md`](../game/mechanics.md) HUD section.
- **Touch:** `HudState` + `:game-client` overlay.
- **Acceptance:** scoring panel renders and updates each tick.
- **Depends on:** T29.

### T31 — Weapon select UI
- **Goal:** HUD lets the player switch weapons via input.
- **Touch:** `InputFrame.weaponSelect`, `ServerTankActor` weapon switching, client overlay.
- **Acceptance:** each owned weapon is selectable; HUD reflects selection.
- **Depends on:** T09–T12.

### T32 — Debrief polish
- **Goal:** end-of-mission debrief shows per-spec stats; not just a "you won" flash.
- **Touch:** `:game-client` debrief screen; `MissionResult` payload from server.
- **Acceptance:** debrief lists score, time, kills, captures, accuracy.
- **Depends on:** T29, T30.

---

## Phase 8 — Multiplayer / input variety

### T33 — Split-screen + multiple cameras
- **Goal:** local two-player split-screen renders two `PlayerView`s side-by-side.
- **Spec:** [`overview.md`](../game/overview.md) "Dual" / "Dual vs Computer".
- **Touch:** `ServerFrame` already carries per-player views; `:game-client` viewport composition.
- **Acceptance:** dual mode plays end-to-end with two cameras + two HUDs + per-player audio pan.
- **Tests:** snapshot test on layout; manual gameplay smoke.
- **Depends on:** T16, T29.

### T34 — Gamepad input
- **Goal:** gamepad mapped to `InputFrame`.
- **Touch:** `:game-client` input layer.
- **Acceptance:** gameplay playable with a gamepad on desktop.
- **Depends on:** —

### T35 — Action mapping / remapping UI
- **Goal:** keyboard + gamepad bindings configurable per profile.
- **Touch:** `:game-client` settings screens; persisted profile (T39).
- **Acceptance:** binding changes persist; in-game input respects them.
- **Depends on:** T34, T39.

---

## Phase 9 — Replay + determinism

### T36 — Replay recording
- **Goal:** record `InputFrame`s + 10 Hz keyframes + `GameEvent`s to disk.
- **Spec:** [ADR 0002](adrs/0002-fixed-step-50hz.md) §"replay storage"; `:game-protocol` `ReplayProtocol` scaffold.
- **Touch:** server-side recorder; file format (versioned per T02).
- **Acceptance:** recording a match produces a deterministic file.
- **Tests:** record-then-replay equivalence on a fixture match.
- **Depends on:** T02.

### T37 — Replay playback
- **Goal:** load a replay file and drive a headless server.
- **Touch:** `:game-server` replay driver; `:game-client` replay viewer.
- **Acceptance:** replays of recorded matches reproduce the original outcome.
- **Tests:** parity assertion on hash of final `WorldSnapshot`.
- **Depends on:** T36.

### T38 — Deterministic checksums
- **Goal:** every Nth tick the server computes a canonical hash of `WorldSnapshot`; replay validates it.
- **Touch:** snapshot hashing in `:game-protocol`.
- **Acceptance:** mid-replay divergence is detected and reported.
- **Depends on:** T37.

---

## Phase 10 — Persistence + packaging

### T39 — Save / config / profile UX
- **Goal:** named profiles store settings, key bindings, progress.
- **Touch:** `:game-client` settings screens; on-disk profile under user-config dir.
- **Acceptance:** restarting the app preserves the active profile and settings.
- **Depends on:** —

### T40 — Editor validation
- **Goal:** scene validation surface inside the editor (orphan goals, unreachable spawns, missing tile layers).
- **Touch:** `:game-editor` validation pass over scene + sidecar; non-blocking warnings panel.
- **Acceptance:** opening a known-broken scene reports the specific issues.
- **Tests:** golden tests on hand-crafted broken scenes.
- **Depends on:** T03.

### T41 — Editor in-editor playtest
- **Goal:** editor can launch a `LocalMatchClient` against the current scene without leaving the app.
- **Touch:** `:game-editor` "Playtest" action; reuses `LocalMatchClient.fromSceneJson`.
- **Acceptance:** edit → playtest → return to edit round-trip works.
- **Depends on:** T29.

### T42 — Performance pass
- **Goal:** sustained 100 Hz simulation + render at typical map density on baseline desktop.
- **Touch:** profile the server tick + client render; eliminate hot allocations.
- **Acceptance:** benchmark harness reports sustained 100 Hz with N=50 actors.
- **Tests:** microbenchmark in `:game-server`.
- **Depends on:** T01, T13.

### T43 — Desktop packaging
- **Goal:** distributable desktop bundle (Compose Multiplatform `packageDistributionForCurrentOS`).
- **Touch:** Amper `:game-client` distribution config; bundled assets.
- **Acceptance:** `.dmg` / `.msi` / `.deb` produced and runs without a JDK.
- **Depends on:** T16, T39.

---

## Future-target (post-parity, not parity-required)

- **TFN1 — Networked play.** Implement `RemoteMatchClient`; reuse the same `MatchClient` seam. See [ADR 0007](adrs/0007-authoritative-headless-kubriko-server.md). Depends on T02, T36.
- **TFN2 — WASM enablement.** Port `:game-protocol` and `:game-client` to a Kotlin/WASM target; Kubriko already supports it. Depends on T43.

---

## How to use this list

- One task per agent run. The acceptance criteria + linked spec doc are intended to be the agent's full brief.
- An agent should re-read [`full-rewrite-status.md`](../full-rewrite-status.md) before starting to confirm the task is still open (state may have shifted between runs).
- On completion, the agent updates `full-rewrite-status.md` §4 (What Works) and §5–§6 (gaps) and ticks the task here.
