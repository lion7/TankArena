# Tank Arena Rewrite Architecture

## Goals
- Desktop-first Kotlin rewrite with a small, defensible module boundary.
- Server-authoritative gameplay so single-player and (future) networked play share a single code path.
- Compose Multiplatform for shell/menu/HUD/editor UI; Kubriko for the gameplay viewport and the headless server tick.
- The legacy `.MAP` files are import-only inputs; runtime content is Kubriko scene JSON plus a metadata sidecar.

The parity target — what "feature complete" means in gameplay terms — is captured in [`docs/game/`](../game/). Those documents (`overview.md`, `mechanics.md`, `vehicles.md`, `weapons.md`, `terrain.md`, `objects.md`, `ai.md`, `map-format.md`) describe the original game and act as the reference the rewrite is matched against.

## Modules

The project ships six modules. All are JVM-only at present.

- **`:game-protocol`** — pure Kotlin wire/contract types. Math, IDs, fixed-step timing, `PlayerIntentFrame` / `InputFrame`, `WorldSnapshot`, the `ActorState` sealed hierarchy keyed by stable `actorId: Long`, `GameEvent`, `PlayerView`, `HudState`, `RadarContact`, `ServerFrame`. No Compose, no Kubriko.
- **`:game-content`** — extracted assets (sprite sheets, sounds), the generated legacy picture catalog, scene JSON files (`resources/scenes/scene_*.json`), per-map metadata sidecars (`metadata_*.json`), and the legacy `.MAP` raw byte parser (`LegacyMapParser`/`LegacyMapData`/`LegacyMapHeader`). Owns the `MapSceneSidecar` schema (tile layers, mission text, mode compatibility, import notes). No Compose, no Kubriko.
- **`:game-server`** — authoritative simulation. Runs a headless Kubriko instance per match with `ActorManager`, `CollisionManager`, `SerializationManager`, a custom `TerrainSlideManager` for axis-by-axis sliding response, and a mission evaluator. Server actors (`ServerTankActor`, `ServerTurretActor`, `ServerProjectileActor`, `ServerWallActor`, `ServerGoalActor`) implement Kubriko `Serializable<T>` so each `save()` produces the matching `:game-protocol` `ActorState`. Hosts the legacy `.MAP` → scene JSON importer (`LegacyMapImporter`, server-internal). Depends on `:game-content` and `:game-protocol`.
- **`:game-client`** — Compose Desktop app. Menus, mode/map selection, HUD/radar overlays, and the Kubriko-hosted gameplay viewport. Client actors render only; each tick `ClientScene.sync(snapshot)` matches actors by stable `actorId`. Input capture converts to `InputFrame` and submits via `MatchClient`. Single-player boots `LocalMatchClient(ServerMatchPrototype)` in-process; networked play will substitute a `RemoteMatchClient` at the same seam.
- **`:game-editor`** — Compose Desktop app embedding Kubriko's `SceneEditor` composable. Reuses the same `tankArenaSerializableMetadata` registry as the server, so placed actors round-trip through the server and client. Server actors implement `Editable<T>` to expose their `State` for the editor; the editor depends on `:game-server` for those actor classes only — no server managers run inside the editor's Kubriko instance.
- **`:tools-mapconv`** — thin Amper `jvm/app` module containing a CLI (`Main.kt`) that calls into `:game-content`'s import library and `:game-server`'s `LegacyToSceneJson`. Subcommands convert single legacy `.MAP` files or batch-regenerate the full scene set into `:game-content/resources/scenes/`.

## Boundaries

- `:game-protocol` and `:game-content` must remain free of Compose and Kubriko. They are the shared substrate for server, client, and editor.
- `:game-server` is the only module that runs authoritative simulation. The client never holds gameplay truth — it renders snapshots and submits intents.
- `:game-editor` does not run server managers. It uses server actors purely as scene-authoring vocabulary via `Editable<T>`.
- `:tools-mapconv` is a CLI shell over library code; the importer itself lives in `:game-server` (legacy parsing intermediate) and `:game-content` (raw byte parsing and the `MapSceneSidecar` schema).

## Replication contract

Every server tick:
1. The server walks `actorManager.allActors`.
2. For each `Serializable` actor it calls `save()`, producing a `:game-protocol` `ActorState`.
3. The server wraps the results in a `WorldSnapshot` plus per-player `PlayerView`s (camera, HUD, radar) into a `ServerFrame`.
4. The client keeps `Map<Long, ClientActor>`. It spawns new render actors for unseen `actorId`s, calls `sync(state)` on existing ones, and removes actors whose IDs vanished.

The same `ActorState` types serve both scene loading (initial deserialization from JSON) and per-tick replication. There is no separate snapshot format.

## Map format

A Tank Arena map *is* a Kubriko scene JSON, produced by `SerializationManager.serializeActors` and loaded with `deserializeActors(json)`. There is no parallel canonical map schema; the gameplay-relevant metadata that does not live in actors (tile layers, mission text, mode compatibility) is carried by `MapSceneSidecar` alongside the scene JSON. See [`content-format.md`](content-format.md) and [ADR 0008](adrs/0008-kubriko-and-scene-json.md).

## Related ADRs
- [0001 — Desktop-first Compose + Kubriko](adrs/0001-desktop-first-compose-kubriko.md)
- [0002 — Fixed-step simulation tick (100 Hz local, decimated for replay/network)](adrs/0002-fixed-step-50hz.md)
- [0003 — Import legacy maps once](adrs/0003-import-once-canonical-map-format.md)
- [0004 — Editor app shape](adrs/0004-separate-editor-app.md)
- [0005 — Network-ready input/protocol boundary](adrs/0005-network-ready-input-protocol-boundary.md)
- [0006 — Module collapse 11 → 6](adrs/0006-module-collapse.md)
- [0007 — Authoritative headless Kubriko server](adrs/0007-authoritative-headless-kubriko-server.md)
- [0008 — Kubriko dependency and scene JSON map format](adrs/0008-kubriko-and-scene-json.md)
- [0009 — Editor wraps Kubriko `SceneEditor`](adrs/0009-editor-wraps-kubriko-scene-editor.md)
