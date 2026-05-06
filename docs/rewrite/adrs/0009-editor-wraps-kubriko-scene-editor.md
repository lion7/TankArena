# ADR 0009: Editor Wraps Kubriko's `SceneEditor`

- **Status:** Accepted (2026-04).
- **Decision:** `:game-editor` is a thin Compose Desktop wrapper around Kubriko's `SceneEditor` composable, reusing the server's actor classes via `Editable<T>`.

## Context

The original plan ([ADR 0004](0004-separate-editor-app.md)) called for a bespoke editor with custom palette/inspector/validation UI. After the move to a Kubriko-native authoritative server (see [ADR 0007](0007-authoritative-headless-kubriko-server.md)), the same Kubriko serialization machinery the server uses can drive scene editing for free — provided the editor reuses the server's actor classes.

## Decision

- `:game-editor` embeds Kubriko's `SceneEditor` composable from `io.github.pandulapeter.kubriko:tool-scene-editor-desktop`.
- The editor uses the same `tankArenaSerializableMetadata` registry as the server, so every actor type round-trips between editor → JSON → server with no translation layer.
- Server actors that are hand-placed (`ServerWallActor`, `ServerTankActor`, `ServerTurretActor`, `ServerGoalActor`) implement `Editable<T>`, exposing their `State` data class as the editor's authoring vocabulary. `ServerProjectileActor` is plain `Serializable<T>` — never hand-placed.
- `:game-editor` depends on `:game-server` purely for those actor classes and `Editable<T>` state. No server managers (`CollisionManager`, `TerrainSlideManager`, mission evaluator) run inside the editor's Kubriko instance — the editor's Kubriko hosts only authoring state.
- The editor opens `game-content/resources/scenes/` by default so existing imported scenes can be edited round-trip.

## Consequences

- Editor scope shrinks dramatically: viewport, palette, inspector, save/load come from Kubriko's `SceneEditor`.
- An intentional dependency from `:game-editor` to `:game-server` exists. The earlier guidance that the editor must not depend on the server is relaxed in favor of sharing `State` definitions; the rule is now narrower — *no server runtime inside the editor*, but server actor *types* are shared.
- Validation and playtest support remain editor-side concerns, layered on top of `SceneEditor`.

## Boundary rule (enforced)

The editor must not transitively reference `CollisionManager`, `TerrainSlideManager`, or the mission evaluator from `:game-server`. Server *managers* belong to the authoritative simulation; only actor classes and their `Editable<T>` `State` are shared. A build-time guard or test in `:game-editor` should fail the build if these symbols leak in. This is also tracked as the "Editor Scope" mitigation in [`risk-register.md`](../risk-register.md).
