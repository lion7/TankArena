# Content Format

How a map is stored and loaded in the rewrite **today**; the planned native format is
[ADR 0022](adrs/0022-native-map-format.md). Where the files come from and every other asset are
in [assets.md](assets.md). The original `.MAP` format is in
[`docs/game/map-format.md`](../game/map-format.md).

## A map is two files

Each shipped map is a pair in `game-content/resources/scenes/`, listed in `index.txt` (69 entries,
one per map of the original release, including `select`, the tank selection arena):

| File | Contents | Read by |
|---|---|---|
| `scene_<name>.json` | Kubriko scene JSON: the map's objects | Kubriko `SerializationManager` on the server; the editor |
| `metadata_<name>.json` | `MapSceneSidecar`: header, mission texts, tile layers | `ShippedScenes` (kotlinx.serialization) |

`<name>` is the `.MAP` file name in lower case, with characters other than `a-z0-9_-` replaced by `_`
(for example `metadata_3_vs_3.json`).

### Scene file

A JSON list of `{typeId, state}` entries. Each `state` is the `State` data class of a server map
object (`Server*Actor.State`). Type ids and their counts over the 69 shipped maps on 2026-10-19:

| `typeId` | Server class | Count | Notes |
|---|---|---|---|
| `tankArenaTank` | `ServerTankActor` | 718 | Map tanks and start pads (`startPadType`, `padAmmo`) |
| `tankArenaTurret` | `ServerTurretActor` | 672 | |
| `tankArenaMine` | `ServerMineActor` | 1252 | Authored mines |
| `tankArenaTrain` | `ServerTrainActor` | 244 | Engines and wagons with their rail state |
| `tankArenaLock` | `ServerLockActor` | 148 | |
| `tankArenaProduct` | `ServerProductActor` | 84 | |
| `tankArenaWarp` | `ServerWarpActor` | 70 | |
| `tankArenaFlag` | `ServerFlagActor` | 42 | |
| `tankArenaGoal` | `ServerGoalActor` | 36 | |
| `tankArenaB52` | `ServerB52Actor` | 6 | |
| `tankArenaDestroyer` | `ServerDestroyerActor` | 2 | |
| `tankArenaWall` | `ServerWallActor` | 40,263 | Editor only; dropped when a match starts |

Enforcer and zeppelin types exist but no shipped map uses them. Placed men and bonuses are parsed
by the importer but not written (none of the 69 maps has them as objects; men and bonuses come from
the tile layers). Rockets, A-bombs and mortars are runtime-only, as in the original's `load_map()`.

All positions are object centres in pixels (the original stores top-left corners; the importer adds
16). Tiles are 33 pixels (`LEGACY_TILE_SIZE`).

### Sidecar (`MapSceneSidecar`, schema version 1)

```text
MapSceneSidecar
  schemaVersion          1 (SCENE_SCHEMA_VERSION); newer files are rejected
  metadata: MapMetadata
    name, widthTiles, heightTiles
    missionCode, nextMissionCode, publicPassword
    world (DESERT, TEMPERATE, CITY, NIGHT = legacy background 0..3), night
    modeCompatibility (legacy map_type), mapContents (MAPC_* bits)
    randomBonus, legacyMapVersion
  missionText: briefing, success, failure
  tileLayers: base, solid, top          picture index per tile, -1 = none (legacy mapm.m[0..2])
              goalLayer                 signed -100..100 (legacy mapm.g)
              bonusLayer                bonus per tile, -1 = none
              manTypeLayer, manAmountLayer  paratroopers per tile (type and count)
  importNotes            importer warnings; empty for every shipped map
```

Layers are row-major lists of `widthTiles × heightTiles` integers.

## Loading

1. The client asks `ShippedScenes.load(name)` for the pair (class path, cached).
2. `ServerMatchPrototype.fromSceneJson` deserializes the scene's map objects, drops the wall entries,
   builds the terrain grid (`TerrainGridBuilder`, material and speed per tile from the generated
   picture terrain table) and the `StructureGrid` from the tile layers.
3. The client draws the terrain from the same sidecar layers; destroyed tiles arrive as `TileState`s.

The client never reads `.MAP` files.

## Producing the files

`tools-mapconv scene-all <MAPS> <out>` converts every `*.MAP` at the top level of `MAPS/` (not
`MAPS/extra/`) and rewrites `index.txt`:

```text
.MAP bytes
  -> LegacyMapParser          (game-content)  header, 7 layers, texts, object blob
  -> LegacyObjectParser       (game-server)   object records by type
  -> LegacyCanonicalConverter (game-server)   CanonicalLegacyMap
  -> CanonicalSceneBuilder    (game-server)   scene JSON (+ wall entries for the editor)
  -> LegacyToSceneJson        (game-server)   scene JSON + MapSceneSidecar
```

```bash
./kotlin run -m tools-mapconv -- scene-all "$PWD/MAPS" "$PWD/game-content/resources/scenes"
```

This format is being replaced by a native, readable `.tamap` file per map with `.MAP` import and
export ([ADR 0022](adrs/0022-native-map-format.md)); the native files then become the source
([ADR 0003](adrs/0003-legacy-map-import.md)). Until then: after any importer change, run `scene-all`
and commit the regenerated files together with the change.

## Versioning

- Bump `SCENE_SCHEMA_VERSION` when the sidecar layout changes incompatibly.
- The scene file follows Kubriko's serialization format; a Kubriko upgrade may require `scene-all`
  ([ADR 0008](adrs/0008-kubriko-and-scene-json.md)).
- Both formats are replaced by the native format ([ADR 0022](adrs/0022-native-map-format.md)) when
  Kubriko is removed ([ADR 0019](adrs/0019-plain-kotlin-no-kubriko.md)).

## Known import gaps

Fields the importer parses but drops, or does not read at all, are listed under
[parity.md → Content and import](parity.md#content-and-import).
