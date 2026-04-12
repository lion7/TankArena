# Legacy System Map

## Runtime
- `src/game`: boot, menus, mode flow, map load/save, main loop.
- `src/tanks`: vehicle movement, fire control, AI.
- `src/objects`: special gameplay actors and world objects.
- `src/world`: terrain rendering, bullets, effects, HUD.
- `src/check`: collisions and damage propagation.
- `src/graph`, `src/hardware`: Allegro-era rendering/input/audio/platform code.

## Authoring
- `src/edit`: editor and map setup tooling.

## Data
- `MAPS/*.MAP`: binary maps with header, layers, mission text, object blob.
- `unpacked/`: extracted PNG/WAV/BMP assets approved for rewrite use.

