# ADR 0013: Reproduce the Original's Presentation

- **Status:** Accepted (2026-10-06 to 2026-10-18). This is Classic's presentation; Extended's own
  menus and screen options are in [ADR 0023](0023-classic-and-extended-rulesets.md).

## Context

The original draws into a 640 × 480 VGA screen (each player's map window is `640/players - 1` by 480,
or the map size if smaller, centred by `compute_window_size()`), with the 40 × 40 `PANEL.BMP` tiled
around it. Menus, prompts, lists and text pages use 320 × 200 mode 13h with the 8 × 16 `SETUP_FONT`
and a 5 × 5 font. Much of its look comes from palette tricks: colour cycling for water, conveyors,
teleporters, glow and lava; a night palette (`NIGHT.PAL` via `NIGHT.LNK`) with light cones from
`LIGHT.DAT`; gamma correction of the palette; a white flash for A-bombs; fades; and a grey view for
the player who lost.

A modern UI with a scalable layout and generic fonts was the earlier plan. Side by side with the
original it did not look like Tank Arena.

## Decision

- **Same screen geometry.** The game draws a 640 × 480 virtual screen scaled by whole numbers; map
  windows are placed like `compute_window_size()` / `write_screen()` (`legacyMapWindows` in
  `RenderSceneSupport.kt`). Menus and text pages are drawn as a 320 × 200 mode 13h screen shown 4:3.
- **Original pictures and fonts**, extracted from `original/DATA/` by `tools-mapconv`
  (`extract-pictures-png`, `extract-fonts`); the status display uses the original bitmaps (meters,
  icons, faces, banners).
- **Palette effects as runtime shaders** instead of a palette: a 64³ colour lookup table
  (`night_lut.png`, made by `extract-night`) for night maps and the night status display,
  palette-index cycling (entries 144–170) by day, and a gamma table. Skia runtime effects on the
  desktop, AGSL on Android 13 and later (`PlatformGraphics`); older Android versions draw without
  them. Fades, greyscale and the A-bomb flash are Compose modifiers.
- **Same flow and timing:** the intro FLIs (`LOGO.FLI`, `TITLE.FLI`, decoded by `FliAnimation`) and
  credits, fades of 0.6 s in a game and 0.3 s for text pages, the success or failure text after a
  mission and back to the mode menu.

## Consequences

- The rewrite looks like the original frame by frame (colour differences against DOSBox footage come
  from DOSBox's scanline filter, not the game). Nine reference renders pin the look
  ([testing strategy](../testing-strategy.md#reference-renders-game-ui)).
- In Classic, resolution independence is limited to whole-number scaling of the 640 × 480 screen.
- A web target must support runtime shaders for the night look.
