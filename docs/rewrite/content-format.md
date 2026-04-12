# Canonical Content Format

## Current Shape
- `MapMetadata`
- `TileLayers`
- `MissionText`
- `AuthoredObject`
- `CanonicalMapDefinition`

## Direction
- JSON is the initial canonical interchange format for imported maps.
- Tile layers stay explicit and versioned.
- Legacy raw object blobs are not part of the canonical format.
- Typed authored objects will replace raw object storage incrementally.

