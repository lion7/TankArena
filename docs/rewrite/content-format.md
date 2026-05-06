# Content Format

## Runtime format

A Tank Arena map is a **Kubriko scene JSON file** plus a **metadata sidecar**. There is no parallel canonical map schema.

- `:game-content/resources/scenes/scene_<name>.json` — Kubriko scene JSON. Produced by `SerializationManager.serializeActors`, loaded on the server via `deserializeActors(json)`. Contains the full set of `Server*Actor` instances for the map (walls, turrets, goals, tank spawn placeholders).
- `:game-content/resources/scenes/metadata_<name>.json` — `MapSceneSidecar`. Holds the gameplay/authoring metadata that does not belong on actors:
  - `metadata: MapMetadata` (name, dimensions, mode compatibility, etc.)
  - `tileLayers: TileLayers` (floor, walls, top — the painted terrain grid)
  - `missionText: MissionText` (briefing/victory/defeat strings)
  - `importNotes`

Both files are loaded together. The runtime client passes them to `LocalMatchClient.fromSceneJson(scene, metadata)`; the editor opens the same pair for round-trip authoring.

All 121 shipped legacy maps have been imported and live in `:game-content/resources/scenes/`.

## Types in `:game-content`

The data types every module needs:
- `MapMetadata`
- `MissionText`
- `TileLayers`
- `GameModeCompatibility`
- `TankArenaWorld`
- `MapSceneSidecar`
- Legacy raw-byte parsing: `LegacyMapParser`, `LegacyMapData`, `LegacyMapHeader`

## Removed types

The earlier "canonical map" schema has been removed:
- `CanonicalMapDefinition`
- `AuthoredObject`
- `ObjectKinds`

These existed when the rewrite still planned a bespoke runtime map schema parallel to Kubriko scenes. After the 2026-04 module collapse the runtime consumes scene JSON directly. The canonical-map intermediate now lives only inside `:game-server`'s legacy importer (`CanonicalLegacyMap` + `LegacyCanonicalConverter` + `LegacyObjectParser`) as an internal step on the way to scene JSON, and is not exported across module boundaries.

## Legacy `.MAP` import

`.MAP` files are import-only. The pipeline:
1. `:game-content`'s `LegacyMapParser` reads the raw bytes (header, layers, mission text, object blob).
2. `:game-server`'s `LegacyObjectParser` + `LegacyCanonicalConverter` produce a server-internal `CanonicalLegacyMap`.
3. `LegacyToSceneJson.convert(canonical)` emits `(sceneJson, sidecar)`.
4. `LegacyMapImporter` is the public facade: `(bytes, name) -> ImportedMission(sidecar, sceneJson)`.

`tools-mapconv scene` and `tools-mapconv scene-all` drive this pipeline as a CLI.

## Versioning

Because the runtime map format is Kubriko's scene JSON, content is coupled to the Kubriko serialization format. If the format changes between Kubriko releases, every shipped scene must be regenerated. The legacy importer is therefore kept as a first-class tool, not a one-shot migration. See [ADR 0008](adrs/0008-kubriko-and-scene-json.md).
