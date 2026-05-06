# ADR 0008: Kubriko Dependency and Scene JSON Map Format

- **Status:** Accepted (2026-04, revised 2026-05).
- **Decision:** the rewrite depends on a published Kubriko release from Maven Central, and uses Kubriko scene JSON as the shipped map format with no parallel canonical schema.

## Context

Two coupled choices:

1. The authoritative server (see [ADR 0007](0007-authoritative-headless-kubriko-server.md)) needs a headless tick API. Kubriko's `Kubriko.newInstance` + `tick(Int)` are part of the public release.
2. The earlier plan kept a bespoke `CanonicalMapDefinition` + `AuthoredObject` schema parallel to whatever the renderer/editor consumed. After moving to Kubriko-native server actors, that parallel schema duplicated information that already round-tripped through Kubriko's `SerializationManager`.

## Decision

- Depend on Kubriko `0.1.2` from Maven Central (`io.github.pandulapeter.kubriko:*-desktop:0.1.2`). No fork, no `mavenLocal` workflow.
- Drop the canonical map schema. The runtime map format is:
  - `scene_<name>.json` — Kubriko scene JSON, produced by `SerializationManager.serializeActors` and loaded with `deserializeActors(json)`.
  - `metadata_<name>.json` — `MapSceneSidecar` (tile layers, mission text, mode compatibility, import notes), holding the gameplay/authoring metadata that does not belong on actors.
- Both files are loaded together by `LocalMatchClient.fromSceneJson(scene, metadata)`.

## Consequences

- Shipped content is coupled to Kubriko's serialization format. If the format changes between Kubriko versions, every shipped scene under `:game-content/resources/scenes/` must be regenerated.
- The legacy `.MAP` importer (`LegacyMapImporter` in `:game-server`, plus `:game-content`'s raw byte parser) is kept as a first-class, repeatable tool — not a one-shot migration. `tools-mapconv scene-all` regenerates the full set.
- `CanonicalMapDefinition`, `AuthoredObject`, and `ObjectKinds` were removed from `:game-content` on 2026-04-26. The legacy canonical intermediate (`CanonicalLegacyMap` + `LegacyCanonicalConverter` + `LegacyObjectParser`) survives only inside `:game-server`'s importer and is not exported.
- `:game-content` retains only the small set every module needs: `MapMetadata`, `MissionText`, `TileLayers`, `GameModeCompatibility`, `TankArenaWorld`, `MapSceneSidecar`, and the legacy raw-byte parser.

## History

The 2026-04 version of this ADR depended on a local Kubriko fork at `/var/projects/kubriko` because the headless `tick(Int)` API was not yet on a public release. Kubriko `0.1.2` (released May 2026, https://github.com/pandulapeter/kubriko/releases/tag/0.1.2) ships that API publicly, so the fork dependency was dropped and `mavenLocal` repository entries were removed from the module YAMLs.
