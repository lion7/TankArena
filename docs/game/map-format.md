# Tank Arena — Legacy `.MAP` Binary Format

Map version: 6 (`MAP_VERSION`). Max dimensions: 200×200 tiles. Tile size: 33 pixels.

## Header (84 bytes)

| Offset | Size | Field                    | Description                                 |
|--------|------|--------------------------|---------------------------------------------|
| 0      | 4    | `mission_code`           | Mission identifier (uint32)                 |
| 4      | 4    | `next_mission_code`      | Next mission on victory (uint32)            |
| 8      | 2    | `width_tiles`            | Map width in tiles (uint16, 1–200)          |
| 10     | 2    | `height_tiles`           | Map height in tiles (uint16, 1–200)         |
| 12     | 2    | `mission_text_offset[0]` | Offset to mission briefing text             |
| 14     | 2    | `mission_text_offset[1]` | Offset to victory text                      |
| 16     | 2    | `mission_text_offset[2]` | Offset to defeat text                       |
| 18     | 1    | `random_bonus`           | Enable random bonus spawning (bool)         |
| 19     | 1    | `background`             | World theme index                           |
| 20     | 1    | `public_password`        | Shareware password flag                     |
| 21     | 1    | `night`                  | Night mode (enables light weapon, index 12) |
| 22     | 2    | `map_type`               | Mode compatibility (`MAPT_*`)               |
| 24     | 2    | `map_version`            | Format version (0–64, current = 6)          |
| 26     | 2    | `map_contents`           | Content flags (`MAPC_*`)                    |
| 28     | 4    | `object_size`            | Size of object blob in bytes (uint32)       |
| 32     | 1    | `shareware`              | Shareware restriction flag                  |
| 33     | 1    | `lock`                   | Header XOR protection enabled               |
| 34     | 2    | _(padding)_              | —                                           |

Bytes 36–83 contain additional fields used by the editor and game logic.

## Header Protection

When `lock` is set, the header is XOR-encrypted: each 4-byte word XORed with `0x34312E33`.

```c
void protect(maptype *m) {
    for (int i = 0; i < sizeof(maptype) / 4; i++)
        ((uint32*)m)[i] ^= 0x34312e33;
}
```

**Ref:** `src/game/mapfiles.c`, `game-content/src/com/tankarena/legacy/LegacyMapParser.kt:unlockHeader()`

## Layer Data (after header)

Layers are stored sequentially after the 84-byte header.

| Layer           | Type    | Size  | Description                               |
|-----------------|---------|-------|-------------------------------------------|
| Terrain Layer 0 | `short` | W×H×2 | Bottom terrain (water, rails, pits, lava) |
| Terrain Layer 1 | `short` | W×H×2 | Middle terrain (structures, bridges)      |
| Terrain Layer 2 | `short` | W×H×2 | Top terrain (overhangs, shadows)          |
| Goal Layer      | `byte`  | W×H   | Goal trigger tiles                        |
| Bonus Layer     | `byte`  | W×H   | Bonus spawn tiles                         |
| Man Layer 0     | `byte`  | W×H   | Man spawn positions (team 0)              |
| Man Layer 1     | `byte`  | W×H   | Man spawn positions (team 1)              |

Total layer size: `W × H × (2 + 2 + 2 + 1 + 1 + 1 + 1) = W × H × 10` bytes.

## Mission Text Sections

Three CStrings at offsets specified in `mission_text_offset[0..2]`:

| Index | Purpose                                     |
|-------|---------------------------------------------|
| 0     | Mission briefing (shown before game starts) |
| 1     | Victory text (shown on goal completion)     |
| 2     | Defeat text (shown on loss)                 |

Offsets are relative to the start of the text section (after layer data). Null-terminated strings.

## Object Blob

Raw binary blob of size `object_size`. Contains serialized `objectstruct` unions for all placed objects.

- Objects are relocated on load to fix cross-references (warp pairs, train wagons, lock targets).
- Each object includes type, position, and type-specific data.
- Object size varies by type (tanks are largest, bonuses are smallest).

## Map Type Constants

| Constant            | Value | Description           |
|---------------------|-------|-----------------------|
| `MAPT_DONTCARE`     | 0     | Works in any mode     |
| `MAPT_DUAL`         | 1     | Dual player only      |
| `MAPT_SINGLE`       | 2     | Single player only    |
| `MAPT_DUALVC`       | 4     | Dual vs computer only |
| `MAPT_SINGLEORDUAL` | 8     | Single or dual        |

## Map Content Flags

`MAPC_*` flags indicate what content the map contains (objects, structures, special terrain, etc.).

## Loading Order

1. Read and validate 84-byte header (width/height 1–200, version 0–64).
2. Unlock header if `lock` is set (XOR with `0x34312E33`).
3. Read 3 terrain layers (short values).
4. Read goal, bonus, and man layers (byte values).
5. Read mission text CStrings from offsets.
6. Read raw object blob.
7. Initialize structure strength from picture data.
8. Relocate object cross-references.
9. Prepare lock objects (set strength to 1000).

**Ref:** `src/game/mapfiles.c`, `game-content/src/com/tankarena/legacy/LegacyMapParser.kt`
