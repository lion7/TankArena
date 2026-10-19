# Tank Arena — Legacy `.MAP` Binary Format

Map version: 6 (`MAP_VERSION`). Tile size: 33 pixels. All values are little-endian; C booleans are stored as -1
(`TRUE`) and 0 (`FALSE`). Maps live in `MAPS/<name>.MAP`.

The game reserves memory for 200 × 200 = 40,000 cells; the editor limits `sx × sy` to that, not each side
(`src/edit/file.c:22`). `RACE3.MAP`, for example, is 300 × 52.

## Header (84 bytes)

`maptype`, packed (`src/include/types.h:12-26`):

| Offset | Size | Field                  | Description                                          |
|--------|------|------------------------|------------------------------------------------------|
| 0      | 22   | `this_mission_code`    | Mission code of this map (zero-terminated string)    |
| 22     | 22   | `next_mission_code`    | Code of the next mission, given on victory           |
| 44     | 4    | `sx`                   | Width in tiles (`long`)                              |
| 48     | 4    | `sy`                   | Height in tiles (`long`)                             |
| 52     | 16   | `mission_text[4]`      | Absolute file offsets (`long`), see below            |
| 68     | 2    | `random_bonus`         | Random bonuses (`short` boolean)                     |
| 70     | 1    | `background`           | World 0–3 (`char`)                                   |
| 71     | 1    | `public_pwd`           | Mission code is public (`char` boolean)              |
| 72     | 2    | `night`                | Night map (`short` boolean)                          |
| 74     | 1    | `map_type`             | `MAPT_*` (`char`)                                    |
| 75     | 1    | `map_version`          | Format version (`unsigned char`), currently 6        |
| 76     | 2    | `map_contents`         | `MAPC_*` flags (`short`)                             |
| 78     | 2    | `sizeofobj`            | Size of one object record (`short`)                  |
| 80     | 1    | `shareware`            | Playable in the shareware version (`char` boolean)   |
| 81     | 1    | `__extra_char__`       | Padding                                              |
| 82     | 2    | `lock`                 | Header is XOR-protected (`short` boolean)            |

`public_pwd` makes the mission code known to the code list cheat; `DUALVC` codes are always known
(`src/game/listcod.c:19`).

## Header Protection

When the editor saves with map locking on, it sets `lock` and XORs the whole header, as 21 32-bit words, with
`0x34312E33` (`src/game/mapfiles.c:432-441`, `:464-469`). `read_header()` tests the stored `lock` field, which is
non-zero both in plain and in protected form, and undoes the XOR (`src/game/mapfiles.c:100-101`).

```c
void protect(void *ptr, long size)
{ size/=4;

  for (; size>0; size--, ptr+=4)
   *(long *)ptr^=0x34312e33;
}
```

## Layer Data (after header)

| Layer           | Type          | Size  | Description                                          |
|-----------------|---------------|-------|------------------------------------------------------|
| Picture layer 0 | `short`       | W×H×2 | Ground (water, roads, rails, pits, lava, conveyors)  |
| Picture layer 1 | `short`       | W×H×2 | Structures                                           |
| Picture layer 2 | `short`       | W×H×2 | Upper parts of structures                            |
| Goal layer      | `signed char` | W×H   | Goal value -100..100 of the tile's building          |
| Bonus layer     | `signed char` | W×H   | Bonus index 0–13, -1 = none                          |
| Men layer 0     | `char`        | W×H   | Men type: `HT_CHAIN`, `HT_FLAME` or `HT_SROCK`       |
| Men layer 1     | `char`        | W×H   | Men count, 0–50                                      |

Cells are stored row by row (`x + sx × y`). Picture values index the world's picture set; -1 is empty. Total layer
size: `W × H × 10` bytes (`src/game/mapfiles.c:204-212`, `src/edit/men.c`).

## Mission Text Sections

`mission_text[0..3]` are absolute file offsets (`src/game/mapfiles.c:405-420`). Each text runs from its offset to the
next one; the texts are not zero-terminated. On save the editor copies `MAPS/<name>.0`, `.1` and `.2` (up to 10,000
bytes each) into the file.

| Index | Purpose                                                       |
|-------|---------------------------------------------------------------|
| 0     | Briefing, shown before the mission (`src/game/single.c:63`)   |
| 1     | Victory text                                                  |
| 2     | Defeat text                                                   |
| 3     | Not a text: start of the object blob                          |

The texts are shown with `view_file()` by the mode code; `load_map()` does not read them.

## Object Blob

From `mission_text[3]` to the end of the file: records of `sizeofobj` bytes each, the raw `objectstruct` with its type
union (`src/game/mapfiles.c:237-248`). Every record has the same size, whatever the object type.

- `id` must be `OBJ_MAGIC` (`0x4A424F54`), or loading stops with an error.
- `x`, `y` are the top-left corner; add 16 for the centre, for every type. `h`, `r` and `gc` follow.
- Pointers (`owner`, `target`, train links, wagon turret) hold the memory addresses at save time. `this_structure`
  holds each object's own old address, and `relocate()` maps the pointers to the loaded objects
  (`src/game/mapfiles.c:450-462`).
- Function pointers in the record are meaningless; `prepare_*()` sets them per type. Only the types in the
  `load_map()` switch may appear (see `objects.md`).

## Map Type Constants

| Constant            | Value | Description                  |
|---------------------|-------|------------------------------|
| `MAPT_DONTCARE`     | 0     | Works in any mode            |
| `MAPT_DUAL`         | 1     | Dual player only             |
| `MAPT_SINGLE`       | 2     | Single player only           |
| `MAPT_DUALVC`       | 3     | Dual vs computer only        |
| `MAPT_SINGLEORDUAL` | 4     | Single player or dual vs computer |

Values from `src/include/define.h:258-262`; see `overview.md` for which mode accepts which type.

## Map Content Flags

Set by the editor on save from the start pads and flags (`src/game/mapfiles.c:363-376`). `DUAL` tank selection only
offers vehicle classes the map has pads for.

| Constant     | Value | Set when the map has         |
|--------------|-------|------------------------------|
| `MAPC_TANK`  | 1     | Tank start pads (types 0, 1) |
| `MAPC_CHOP`  | 2     | Chopper pads (types 2, 3)    |
| `MAPC_PLANE` | 4     | Plane pads (types 4, 5)      |
| `MAPC_RACE`  | 8     | Race pads (types 6, 7)       |
| `MAPC_FLAG`  | 16    | Flags                        |

## Loading Order

`load_map()` (`src/game/mapfiles.c:171-342`):

1. Read the 84-byte header; undo the protection if `lock` is set.
2. Refuse non-shareware maps in the shareware version, and maps with `map_version > 6` (`:190-200`). Nothing else is
   checked.
3. Read the 3 picture layers, the goal and bonus layers and the 2 men layers.
4. Select the picture set of `background` and load its pictures.
5. Set structure strengths from the pictures; a strength of 1000 on either structure layer makes both layers of the
   tile indestructible (`:218-235`).
6. If objects are wanted: seek to `mission_text[3]`, read records until end of file, relocate the pointers and call the
   `prepare_*()` function of each type.
7. Outside the editor: place the bonuses, add the zeppelin (shareware only) and pick the flags for a flag hunt.
8. Start the music of the world.

**Ref:** `src/game/mapfiles.c`, `src/include/types.h`
