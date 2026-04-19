# Tank Arena Asset Storage Audit & Migration Plan

## Purpose
This document inventories how legacy Tank Arena stores **maps, tiles, sprites/pictures, and audio**, and proposes a practical migration/reuse strategy for the Kotlin/KorGE port.

---

## 1) Legacy asset inventory at a glance

### Repository-level asset locations
- `DATA/` (binary runtime containers and palettes)
- `MAPS/` (`.MAP` levels + optional mission text sidecar files like `.0/.1/.2`)
- `unpacked/` (already extracted modern assets: png/bmp/wav)

### Current extracted asset counts in repo (observed)
- `unpacked/sprites`: 36 png files
- `unpacked/sound`: 29 wav files
- `unpacked/backgrounds`: 14 bmp files
- `MAPS`: 223 map files

---

## 2) Maps: binary structure, semantics, and migration

## 2.1 Header and memory model in C
Legacy map header type is `maptype` (packed), with key fields:
- mission code strings (`this_mission_code`, `next_mission_code`)
- map size (`sx`, `sy`)
- mission text offsets (`mission_text[4]`)
- gameplay flags (`random_bonus`, `night`, `map_type`, `map_contents`, `public_pwd`, `shareware`, `lock`)
- compatibility fields (`map_version`, `sizeofobj`)

Map memory layout in runtime:
- `m[3]` = 3 tile layers (`short` each)
- `g` = goal/overlay byte layer
- `b` = background byte layer
- `man[2]` = two extra byte layers

The loader reads these blocks in fixed order after header.

## 2.2 On-disk `.MAP` loading/saving behavior
From `load_map` / `save_map`:
1. Read header (`maptype`)
2. Read layer blocks:
   - 3 × `short[sx*sy]`
   - 1 × `byte[sx*sy]` (`g`)
   - 1 × `byte[sx*sy]` (`b`)
   - 2 × `byte[sx*sy]` (`man[0..1]`)
3. Mission texts are in-band in `.MAP`, located via `mission_text[]` offsets
4. Serialized object list starts at `mission_text[3]`, each entry is `sizeof(objectstruct)` bytes

## 2.3 Map compatibility and filtering bits
- `MAP_VERSION` is `6`
- `map_type` values constrain allowed game modes (single/dual/etc.)
- `map_contents` bitmask indicates what starts/goals content exists:
  - `MAPC_TANK=1`, `MAPC_CHOP=2`, `MAPC_PLANE=4`, `MAPC_RACE=8`, `MAPC_FLAG=16`

## 2.4 Map locking/encryption detail
If `map.lock` is set, header/object data are obfuscated via XOR in `protect`:
- XOR each 32-bit word with constant `0x34312e33`

This is not cryptographic encryption; it is reversible obfuscation.

## 2.5 Migration recommendations for maps
### Phase A (safe/read-only reuse)
- Implement Kotlin parser for:
  - `maptype` header
  - tile/layer blocks
  - mission text segments
- Defer binary object blob decoding initially; preserve raw bytes as opaque payload per map.

### Phase B (playable parity)
- Decode subset of object types needed for first parity modes (player starts, flags, goals, static hazards).
- Normalize map schema to a Kotlin `MapDefinitionV2`:
  - dimensions
  - tile layers
  - gameplay metadata
  - object list (typed)
  - mission script/text

### Phase C (authoring)
- Store canonical rewrite maps in JSON/Kotlinx serialization format.
- Keep a converter path: legacy `.MAP` -> canonical rewrite map.

---

## 3) Tiles & pictures/sprites: storage and migration

## 3.1 Legacy picture container format
Core files:
- `DATA/PICTURES.DAT` (raw concatenated 33x33 indexed images)
- `DATA/PICTURES.IDX` (sorted index records: 12-byte name + byte address)

Builder utility `src/utils/dat.c` confirms:
- tile/sprite block size is fixed (`b_size=33`, `picsize=33*33=1089`, padded constant in code as 1092)
- index keys are uppercase-ish short names (`TANxx-yy`, `FACE`, `BACK`, `WAGO`, etc.)
- loader binary-searches the index by name and then returns pointer into `PICTURES.DAT`

## 3.2 Runtime loading pattern
`init_pictures` resolves many symbolic names to pointers:
- backgrounds (`BACK*`)
- tank animation frames (`TAN..`)
- faces (`FACE..`)
- train wagons (`WAGO..`)
- b52 frames
- turret frames
- start markers

This confirms the sprite system is **name-indexed atlas chunks**, not one monolithic bitmap.

## 3.3 Palette model
- `DATA/PALETTE.DAT` is 768 bytes (256 * RGB triplets)
- legacy renderer is 8-bit indexed palette-based
- `DATA/NIGHT.PAL` and `DATA/NIGHT.LNK` provide night-mode remap pipeline

## 3.4 Sprite extraction pipeline (current implementation)
The Compose desktop port now ships every legacy 33x33 picture as a Compose
Multiplatform drawable resource. The build-time pipeline lives in
[`tools-mapconv`](../tools-mapconv) and runs as:

```
amper run -m tools-mapconv -- extract-pictures-png \
    DATA \
    game-render-kubriko/composeResources/drawable \
    game-content/src/com/tankarena/content/GeneratedLegacyPictureCatalog.kt \
    src/data/pictures.c
```

Steps performed by `LegacyPicturesPngExtractor`:
1. Decode `DATA/PALETTE.DAT` (Allegro 6-bit RGB → 8-bit ARGB).
2. Walk `DATA/PICTURES.IDX` (16-byte records: 12-byte ASCII name + 4-byte
   little-endian offset). 2 960 sprites total.
3. Read 1 089 bytes from `DATA/PICTURES.DAT` per entry, treating palette
   index `0xFF` as transparent (matches `tank->pic[dir]+x2+y2*b_size != 0xff`
   in `src/check/hitobj.c`).
4. Emit one PNG per legacy name to
   `game-render-kubriko/composeResources/drawable/pic_<safe>.png`, where the
   safe key is produced by `LegacyResourceNaming.safeKey` (single-underscore
   escapes for `@`, `_`, `~`, `^`, `.`, `-`).
5. Regenerate `GeneratedLegacyPictureCatalog` so each `pc{world}` entry from
   `src/data/pictures.c` ships all three variant names (intact / damaged /
   dead) plus a `legacyName → resourceKey` map. Lists are emitted as chunked
   builder methods to stay under JVM's 64 KB per-method bytecode limit.

At runtime, `LegacySpriteResources` (in `game-content`) maps simulation
state to legacy picture names following `init_pictures` (`TAN%02ld-%ld`,
`TUR%ld-%ld`, `EXP%d`, …). The `LegacySpriteCatalog` in
`game-render-kubriko` resolves those names to `DrawableResource` handles
through `Res.allDrawableResources` and feeds Kubriko's stock
`SpriteManager` for preload/get; tiles and entities draw with
`FilterQuality.None` so the pixel art stays crisp.

---

## 4) Sound and music: storage and migration

## 4.1 Legacy sound effects
- Effects container: `DATA/SOUND.DAT`
- Accessed through Allegro `load_datafile` and indexed constants in `src/include/sound.h`
- Sample IDs are stable (`SND_BEEP`, `SND_MAIN`, `SND_ROCKET`, etc.)

The `unpacked/sound/*.wav` directory contains one-to-one wav files matching these IDs by name (`beep.wav`, `rocket.wav`, etc.).

## 4.2 Legacy music
- MIDI tunes referenced directly from files, e.g. `DATA/TUNE.MID`, `DATA/BELVED.MID`
- Runtime switches tune based on context/map background

## 4.3 Spatial audio behavior currently implemented in C
`create_sound_absolute` computes volume/pan from distance to one or two tanks and mode (single/dual split).
This logic can be ported independently of container format.

### Recommended sound/music migration
1. Use `unpacked/sound/*.wav` immediately in KorGE audio.
2. Create Kotlin `SoundId` enum mirroring `sound.h` numeric ordering for deterministic mapping.
3. Port distance/pan formula from legacy C as pure `game-core` audio event metadata (not playback itself).
4. Decide MIDI strategy:
   - keep original `.MID` for desktop targets where supported,
   - optionally transcode to OGG for web portability.

---

## 5) Other legacy data containers worth noting
- `DATA/bitmaps.dat` and `DATA/SETSOUND.DAT`: Allegro datafile containers for UI bitmaps/fonts/setup resources.
- `DATA/LIGHT.DAT`: night mission light masks consumed with `NIGHT.PAL/NIGHT.LNK`.

Recommendation: only migrate resources actually needed by rewrite UI/HUD first; avoid full datafile decoder unless parity requires it.

---

## 6) Practical migration/reuse strategy (ordered)

## Step 1 — Use extracted assets now
- Sprites/backgrounds/sounds from `unpacked/`.
- Fastest path to visible parity without reverse-engineering every Allegro container.

## Step 2 — Formalize mapping registries
- `LegacyTileId -> AtlasRegion`
- `LegacySoundId -> SoundAssetPath`
- `LegacyMapType/Contents flags -> Kotlin enums`

## Step 3 — Implement legacy map reader in Kotlin
- Header + layers + mission text first.
- Optional object decoding behind feature flag.

## Step 4 — Validate with golden fixtures
- For 5–10 known maps, compare:
  - dimensions/layer checksums
  - spawn positions/goals
  - flag/race compatibility bits

## Step 5 — Canonical rewrite format
- Introduce new map format for editor/runtime.
- Keep import converter from legacy `.MAP`.

---

## 7) Risks and mitigation

### Risk: implicit behavior in object blobs
Objectstruct binary compatibility is compiler/packing sensitive.
- Mitigation: parse only required object subset first; keep raw opaque remainder.

### Risk: palette-era visuals differ in RGBA pipeline
- Mitigation: preserve palette-index references for regression tests; add optional palette shader/remap if needed.

### Risk: MIDI portability on web
- Mitigation: pre-convert soundtrack to compressed modern format while retaining original MIDI as archival source.

---

## 8) Recommended immediate implementation tasks (next coding chunks)
1. Add `legacy-map-reader` package in `game-core` or `game-editor` with parser for header+layers — **done** in `game-legacy/LegacyMapParser`.
2. Add a name-indexed sprite registry — **done** as
   `LegacySpriteResources` (data) + `LegacySpriteCatalog` (Compose
   resources). Replaces the previous heuristic `LegacyAssetRegistry` /
   `SpriteSheets` model, which has been deleted.
3. Replace placeholder rectangles with extracted sprite assets in the
   Compose desktop renderer — **done** in `TankArenaViewport` via the
   Kubriko `SpriteManager` plugin.
4. Drive the Kubriko `ViewportManager` from the legacy 640x400 playfield
   so small maps render at the same on-screen tile size as the original —
   **done** (`LEGACY_PLAYFIELD_ASPECT_RATIO`).
5. Add `SoundId` enum + event-driven audio API boundary (`game-core`
   emits, client plays).
6. Add map import CLI task in Amper for batch conversion and verification
   report.
