# Assets

Every file of the original release and where it lives in the rewrite. The goal is that the game
ships all original assets from its own class path, converted by repeatable tools, and that nothing
at runtime reads the repository's legacy folders.

Status on 2026-10-19: all assets the game needs at runtime are converted and bundled. What is not
converted belongs to the map editor, the DOS setup program or the DOS distribution itself.

## Inventory

### Pictures, palettes and fonts (`DATA/`)

| Original | In the rewrite | Made by |
|---|---|---|
| `PICTURES.DAT`, `PICTURES.IDX`, `PALETTE.DAT` | 2,960 transparent PNGs `pic_*.png` in `game-client/composeResources/drawable/`, plus `GeneratedLegacyPictureCatalog.kt` | `extract-pictures-png` |
| Picture attributes in `src/data/pictures.c` (type, speed, strengths, explosion, links) | `GeneratedLegacyPictureTerrain.kt` | `extract-picture-terrain` |
| Opaque pixels of every picture | `game-content/resources/masks/pictures.mask` | `extract-masks` |
| `NIGHT.PAL`, `NIGHT.LNK` (+ `PALETTE.DAT`) | `night_lut.png` (64³ colour lookup table) | `extract-night` |
| `LIGHT.DAT` (15 light cones, LZSS-packed Allegro datafile) | `light_{s,m,l}light1..5.png` | `extract-night` |
| `src/graph/5x5font.s`, `SETUP_FONT` in `SETSOUND.DAT`, `PALETTE.DAT` | `GeneratedLegacyFonts.kt` (client) | `extract-fonts` |
| `bitmaps.dat`: `PANEL`, `BLACK1`–`BLACK11`, `CREDITS`, `CREDITS2` | `game-client/resources/backgrounds/*.bmp` | Copied (from `unpacked/backgrounds/`) |

The `pics/`, `2hpics/`, `2vpics/`, `23pics/`, `32pics/`, `3pics/`, `4pics/`, `4hpics/` folders are
the original's drawing sources from which `PICTURES.DAT` was built (`bat/*.bat`, `src/utils/`); their
content reaches the rewrite through `PICTURES.DAT`.

### Sound, music, animations, text

| Original | In the rewrite | Made by |
|---|---|---|
| `SOUND.DAT` (29 samples) | `game-content/resources/sounds/*.wav` | Copied from `unpacked/sound/` (no extractor) |
| `TUNE.MID`, `Belved.mid` | `game-content/resources/music/TUNE.MID`, `BELVED.MID` | Copied |
| `FLI/LOGO.FLI`, `FLI/TITLE.FLI` | `game-client/resources/fli/` | Copied; decoded at runtime by `FliAnimation` |
| `VICTORY.FLI`, `THUNDER.FLI` | — | Not in the original release either; the game skips them like the original |
| `KEYS.HLP`, `TANK.HLP` | `game-client/resources/help/` | Copied; shown by `TextPages` |

### Maps

| Original | In the rewrite | Made by |
|---|---|---|
| `MAPS/*.MAP` (69, incl. `SELECT.MAP`) | `game-content/resources/maps/*.tamap` and `index.txt` | `import-all` ([content-format.md](content-format.md)) |
| `MAPS/*.0`, `*.1`, `*.2` (mission text sources) | — | Not needed: the texts are embedded in each `.MAP` and imported from there |
| `MAPS/extra/` (53 maps not in the release) | — | Deliberately not converted ([ADR 0015](adrs/0015-ship-the-original-69-maps.md)) |

### Player data

| Original | In the rewrite |
|---|---|
| `TANK.CFG` | `settings.json` in the user's config folder (options and victory count so far) |
| `PASSWD.DAT` | `passwd.json` in the user's config folder |

See [ADR 0020](adrs/0020-user-data-as-json.md). Neither original file is in the repository.

### Not converted

| Original | Why | Needed for |
|---|---|---|
| `DATA/MAP_0.DAT` … `MAP_3.DAT` | World templates for new maps in the original editor | Editor parity ("new map from a world") |
| `SETSOUND.DAT`: `BACKGROUND`, `SETUP_PAL`, `TEST_SAMPLE` | Belong to the DOS sound setup program | Nothing (sound setup is not ported; the JDK picks the output) |
| `Tutorial/` (editor tutorial pages and pictures) | Documentation of the original editor | Possibly the editor's help |
| `readme.txt`, `cheat.txt`, `*.BAT`, `bat/`, `src/utils/code.dat` | DOS distribution and build files | Nothing at runtime; `cheat.txt` documents the switches to port |
| `unpacked/sprites/` (36 sheets), `unpacked/lights/` | Early hand extractions, superseded by the extractors | Nothing; can be deleted |

## tools-mapconv

All converters run through `:tools-mapconv`:

```bash
./kotlin run -m tools-mapconv -- <subcommand> <arguments>
```

| Subcommand | Reads | Writes |
|---|---|---|
| `import <MAP> <dir>` | One `.MAP` | Its `.tamap` |
| `import-all <MAPS> <dir>` | Every `*.MAP` at the top of `MAPS/` | All `.tamap` files and `index.txt` |
| `map <MAP> <json>` | One `.MAP` | The importer's intermediate JSON (debugging) |
| `extract-pictures-png <DATA> <drawable> <catalog.kt> <pictures.c>` | `PICTURES.DAT/IDX`, `PALETTE.DAT` | PNGs and the picture catalog |
| `extract-pictures <pictures.c> <kt>` | `src/data/pictures.c` | The picture catalog only |
| `extract-picture-terrain <pictures.c> <kt>` | `src/data/pictures.c` | `GeneratedLegacyPictureTerrain.kt` |
| `extract-vehicles <tanks.c> <kt>` | `src/data/tanks.c` | `GeneratedLegacyVehicles.kt` |
| `extract-masks <drawable> <mask>` | The extracted PNGs | `pictures.mask` |
| `extract-night <DATA> <drawable>` | `NIGHT.PAL`, `NIGHT.LNK`, `PALETTE.DAT`, `LIGHT.DAT` | `night_lut.png`, light cones |
| `extract-fonts <5x5font.s> <DATA> <kt>` | Font source, `SETSOUND.DAT`, `PALETTE.DAT` | `GeneratedLegacyFonts.kt` |

`extract-night` and `extract-fonts` are missing from the tool's usage text.

## Open asset work

- Add an `extract-sounds` command (from `SOUND.DAT`) and an `extract-bitmaps` command (from
  `bitmaps.dat`) so that sounds and backgrounds are generated like everything else instead of copied
  from hand extractions.
- Convert `MAP_0..3.DAT` when the editor gets "new map from a world template".
- Delete `unpacked/` once nothing refers to it (only the copies above came from it).
- Rename `TankArenaWorld.NIGHT`: background 3 is the original's fourth world ("MARKET" in the
  editor's menu); night is a separate map flag.
