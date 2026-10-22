# Assets

Every file of the original release and where it lives in the rewrite. The goal is that the game
ships all original assets from its own class path, converted by repeatable tools, and that nothing
at runtime reads the repository's legacy folders.

Status on 2026-10-22: every asset the game needs at runtime is converted and bundled. Most are made
by `tools-mapconv`; the sounds and the menu backgrounds are copies of hand extractions, and the intro
animations, help files and music are copied unchanged. What is not converted belongs to the DOS setup
programs or the DOS distribution itself.

## Inventory

### Pictures, palettes and fonts (`original/DATA/`)

| Original | In the rewrite | Made by |
|---|---|---|
| `PICTURES.DAT`, `PICTURES.IDX`, `PALETTE.DAT` | 2,960 transparent PNGs `pic_*.png` in `game-ui/composeResources/drawable/`, plus `GeneratedLegacyPictureCatalog.kt` | `extract-pictures-png` |
| Picture attributes in `original/src/data/pictures.c` (type, speed, strengths, explosion, links) | `GeneratedLegacyPictureTerrain.kt` | `extract-picture-terrain` |
| Opaque pixels of every picture | `game-content/resources/masks/pictures.mask` | `extract-masks` |
| `NIGHT.PAL`, `NIGHT.LNK` (+ `PALETTE.DAT`) | `night_lut.png` (64³ colour lookup table) | `extract-night` |
| `LIGHT.DAT` (15 light cones, LZSS-packed Allegro datafile) | `light_{s,m,l}light1..5.png` | `extract-night` |
| `original/src/graph/5x5font.s`, `SETUP_FONT` in `SETSOUND.DAT`, `PALETTE.DAT` | `GeneratedLegacyFonts.kt` (client) | `extract-fonts` |
| `bitmaps.dat`: `PANEL`, `BLACK1`–`BLACK11`, `CREDITS`, `CREDITS2` | `game-ui/resources/backgrounds/*.bmp` | Hand extraction, moved here in 2026-10 (no extractor) |

The `original/pics/`, `original/2hpics/`, `original/2vpics/`, `original/23pics/`, `original/32pics/`, `original/3pics/`, `original/4pics/`, `original/4hpics/` folders are
the original's drawing sources from which `PICTURES.DAT` was built (`bat/*.bat`, `original/src/utils/`); their
content reaches the rewrite through `PICTURES.DAT`.

### Sound, music, animations, text

| Original | In the rewrite | Made by |
|---|---|---|
| `SOUND.DAT` (29 samples) | `game-content/resources/sounds/*.wav` | Copied from `original/unpacked/sound/` (no extractor) |
| `TUNE.MID`, `Belved.mid` | `game-content/resources/music/TUNE.MID`, `BELVED.MID` | Copied |
| `FLI/LOGO.FLI`, `FLI/TITLE.FLI` | `game-ui/resources/fli/` | Moved here unchanged (the only copy in the repository); decoded at runtime by `FliAnimation` |
| `VICTORY.FLI`, `THUNDER.FLI` | — | Not in the original release either; the game skips them like the original |
| `KEYS.HLP`, `TANK.HLP` | `game-ui/resources/help/` | Copied; shown by `TextPages` |

### Maps

| Original | In the rewrite | Made by |
|---|---|---|
| `original/MAPS/*.MAP` (69, incl. `SELECT.MAP`) | `game-content/resources/maps/*.tamap` and `index.txt` | `import-all` ([content-format.md](content-format.md)) |
| `original/MAPS/*.0`, `*.1`, `*.2` (mission text sources) | — | Not needed: the texts are embedded in each `.MAP` and imported from there; the editor edits them in the map |
| `original/MAPS/extra/` (53 maps not in the release) | — | Deliberately not converted ([ADR 0015](adrs/0015-ship-the-original-69-maps.md)) |
| `original/DATA/MAP_0.DAT` … `MAP_3.DAT` (the editor's structure library per world, 50×50 linked cells) | `game-content/resources/structures/map_N.dat` | Copied; read by `StructureLibrary` |

### Player data

| Original | In the rewrite |
|---|---|
| `TANK.CFG` | `settings.json` in the player's config folder (options, controls, last codes, victory count, …) |
| `PASSWD.DAT` | `passwd.json` in the player's config folder |

See [ADR 0020](adrs/0020-user-data-as-json.md). Neither original file is in the repository.

### Not converted

| Original | Why | Needed for |
|---|---|---|
| `SETSOUND.DAT`: `BACKGROUND`, `SETUP_PAL`, `TEST_SAMPLE` | Belong to the DOS sound setup program | Nothing (sound setup is not ported; the JDK picks the output) |
| `original/Tutorial/` (editor tutorial pages and pictures) | Documentation of the original editor | Possibly the editor's help |
| `readme.txt`, `cheat.txt`, `*.BAT`, `bat/`, `original/src/utils/code.dat` | DOS distribution and build files | Nothing at runtime; `cheat.txt` documents the switches, which are ported |
| `original/unpacked/sprites/` (36 sheets), `original/unpacked/lights/` | Early hand extractions, superseded by the extractors | Nothing; can be deleted |
| `original/unpacked/sound/` (29 WAVs) | Hand extraction of `SOUND.DAT`, the source of the bundled sounds | Nothing once `extract-sounds` exists |

## tools-mapconv

All converters run through `:tools-mapconv`:

```bash
./kotlin run -m tools-mapconv -- <subcommand> <arguments>
```

| Subcommand | Reads | Writes |
|---|---|---|
| `import <MAP> <dir>` | One `.MAP` | Its `.tamap` |
| `import-all <MAPS> <dir>` | Every `*.MAP` at the top of `original/MAPS/` | All `.tamap` files and `index.txt` |
| `export <tamap> <MAP>` | One `.tamap` | An original `.MAP` (version 6, unlocked) |
| `map <MAP> <json>` | One `.MAP` | The importer's intermediate JSON (debugging) |
| `extract-pictures-png <DATA> <drawable> <catalog.kt> <pictures.c>` | `PICTURES.DAT/IDX`, `PALETTE.DAT` | PNGs and the picture catalog |
| `extract-pictures <pictures.c> <kt>` | `original/src/data/pictures.c` | The picture catalog only |
| `extract-picture-terrain <pictures.c> <kt>` | `original/src/data/pictures.c` | `GeneratedLegacyPictureTerrain.kt` |
| `extract-vehicles <tanks.c> <kt>` | `original/src/data/tanks.c` | `GeneratedLegacyVehicles.kt` |
| `extract-masks <drawable> <mask>` | The extracted PNGs | `pictures.mask` |
| `extract-night <DATA> <drawable>` | `NIGHT.PAL`, `NIGHT.LNK`, `PALETTE.DAT`, `LIGHT.DAT` | `night_lut.png`, light cones |
| `extract-fonts <5x5font.s> <DATA> <kt>` | Font source, `SETSOUND.DAT`, `PALETTE.DAT` | `GeneratedLegacyFonts.kt` |

`extract-night` and `extract-fonts` are missing from the tool's usage text.

## Open asset work

- Add an `extract-sounds` command (from `SOUND.DAT`) and an `extract-bitmaps` command (from
  `bitmaps.dat`) so that sounds and backgrounds are generated like everything else instead of copied
  from hand extractions.
- Delete `original/unpacked/` once the sounds are extracted by a tool (only they still come from it).
- Rename `TankArenaWorld.NIGHT`: background 3 is the original's fourth world ("MARKET" in the
  editor's menu); night is a separate map flag.
