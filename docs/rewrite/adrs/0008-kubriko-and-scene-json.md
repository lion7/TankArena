# ADR 0008: Kubriko Dependency and Scene JSON Map Format

- **Status:** Superseded on 2026-10-19: Kubriko is removed ([ADR 0019](0019-plain-kotlin-no-kubriko.md))
  and maps move to a native format ([ADR 0022](0022-native-map-format.md)). This describes the format
  in use until that migration lands.

## Context

The first plan kept a bespoke canonical map schema (`CanonicalMapDefinition`, `AuthoredObject`,
`ObjectKinds`) next to whatever the renderer and editor used. Once the map objects became Kubriko
actors on the server, Kubriko's `SerializationManager` could save and load them, and the editor
(Kubriko's `SceneEditor`) could edit the same files.

## Decision

- Depend on a **published Kubriko release** from Maven Central (`io.github.pandulapeter.kubriko:*`),
  pinned in every `module.yaml`. Currently **0.7.3** (upgraded from 0.1.2 on 2026-10-04). No fork.
- A shipped map is a **pair of files** in `game-content/resources/scenes/`:
  - `scene_<name>.json`: Kubriko scene JSON, a list of `{typeId, state}` map objects (tanks, turrets,
    goals, flags, locks, warps, mines, products, trains, B52s, destroyers, enforcers, and so on);
  - `metadata_<name>.json`: the `MapSceneSidecar` with the header fields, mission texts and all tile
    layers.
- No parallel canonical schema at runtime. `CanonicalMapDefinition` survives only inside the importer
  and test fixtures.

Details: [content-format.md](../content-format.md).

## Consequences

- The shipped content depends on Kubriko's serialization format; a Kubriko bump may require
  regenerating every scene (`tools-mapconv scene-all`).
- The scene files still contain one `tankArenaWall` object per solid tile plus four boundary walls,
  only so that Kubriko's `SceneEditor` shows the walls. The match filters them out; structures come
  from the sidecar's tile layers ([ADR 0011](0011-structure-grid-and-pixel-masks.md)).
- When the server drops Kubriko (ADR 0019) it can no longer read this format through Kubriko's
  `SerializationManager`; a plain `kotlinx.serialization` reader or a new format is needed.
