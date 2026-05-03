# Tank Arena — Engine Mechanics

## Tick Rate

100 Hz game tick (10 ms per tick). Defined as `sec = 100` in `define.h`.

```c
// src/hardware/timer.c
install_int_ex(game_tick, BPS_TO_TIMER(100));  // 10ms/tick
```

Timer macros:

| Macro      | Expands To           | Purpose                         |
|------------|----------------------|---------------------------------|
| `tinit(x)` | `timer + x`          | Schedule event x ticks from now |
| `tdone(x)` | `timer >= x`         | Check if scheduled time elapsed |
| `twait(x)` | `while (x >= timer)` | Block until time reached        |

### Game Loop

`game_control()` runs each tick:

```
read_controls()
  → transportation()        // Vehicle movement
  → control_effects()       // Particle effects
  → control_bullets()       // Bullet movement
  → control_parts()         // Debris physics
  → check_obj_overlap()     // Collision detection
  → control_objects()       // Object AI/logic
  → palette_effects()       // Color cycling (water, lava, glow)
```

Main loop pattern (fade-in/out wraps the game loop):

```c
do {
  game_control();
  write_screen();
  done = !do_fade_step();
} while (!tdone(game.end_round) && !game.exit_game);
```

**Ref:** `src/hardware/timer.c`, `src/game/mainloop.c`

## Rendering Pipeline

Mode 13h: 320×200, 256 colors. Double-buffered with page flipping.

### Per-Viewport Draw Order

```
1. bottom_layer(tn, ssx, ssy)       // Ground tiles (33×33 blocks)
2. write_objects(NULL)              // Depth-sorted by height (h=-4..24)
   ├── h < 4:  underground objects
   ├── h == 4: top_layer() + write_parts() + write_bullets()
   ├── h > 4:  above-ground objects
   └── night:  light functions
3. tank_write_marker(o)             // Lock-on reticle
4. b_convert(scr, gray, ...)        // Grayscale overlay if player lost
5. write_radar(o)                   // Radar overlay
6. write_status(o)                  // HUD/status bar
7. blit(scrbuf, screen, ...)        // Blit to display buffer
8. scroll_screen()                  // Page flip
```

### Depth Sorting

Objects are drawn in height order from `h = -4` to `h = 24`. At `h == 4`, the top terrain layer, parts, and bullets are
rendered (placing them between underground and above-ground objects).

```c
// src/objects/objects.c
for (h = -4; h < 25; h++) {
if (h == 4) {
top_layer(ssx, ssy); write_parts(); write_bullets(); }
for (; e != NULL && e->h == h; e = e->next)
b_add_layer(e->x - ssx, e->y - ssy, eff[e->pn].ptr);
for (; o != NULL && o->h == h; o = o->next)
o->funct.write(o);
}
```

### Sprite Primitives (Assembly)

All sprites are 33×33 with `0xFF` as mask color.

| Function               | Purpose                          |
|------------------------|----------------------------------|
| `b_first_layer`        | Solid blit (no mask)             |
| `b_add_layer`          | Masked blit (skips 0xFF)         |
| `b_add_shadow`         | Blit through shadow lookup table |
| `b_set_layer`          | Sets high bit (0x80) on pixels   |
| `b_add_layer_set_high` | Masked blit + high bit           |
| `b_add_layer_x`        | Horizontally flipped             |
| `b_add_layer_y`        | Vertically flipped               |
| `b_add_layer_xy`       | Both-axis flipped                |

### Terrain Layers

| Layer            | Content                                |
|------------------|----------------------------------------|
| Layer 0 (bottom) | Water, rails, pits, lava, base terrain |
| Layer 1 (middle) | Structures, bridges                    |
| Layer 2 (top)    | Overhangs, shadows, rendered at h=4    |

Structure strength affects picture frame selection: intact → damaged → destroyed.

### Palette Effects

Continuous color cycling for:

- Water animation
- Lava glow
- Explosion flicker
- Nuke total palette shift

**Ref:** `src/game/mainloop.c`, `src/objects/objects.c`, `src/world/under.c`, `src/graph/sprites.s`,
`src/graph/palette.c`

## Sound System

Distance-attenuated stereo audio via Allegro digitized samples.

### Distance Attenuation

```
d = sqrt((sx - px)² + (sy - py)²)    // Euclidean distance

if d < 100:       volume = 255        // Full volume (near field)
if 100 ≤ d < 612: volume = (612 - d) / 2  // Linear falloff
if d ≥ 612:      volume = 0          // Silent (max range)
```

Max audible range: 612 pixels (~18.5 tiles).

### Stereo Panning (Dual Mode)

```
pan = -(612 - d1) / 4 + (612 - d2) / 4 + 128
clamped to [0, 255]
```

`d1` = distance to player 1, `d2` = distance to player 2. Sound uses minimum distance for volume, panning based on
relative position.

### Pitch Variation

Each sound plays at `900 + rnd(200)`, giving a pitch range of 900–1100. Adds variation to repeated sounds.

Volume threshold: sounds with calculated volume ≤ 32 are silenced.

### Sound Samples

| Constant        | Trigger               | Source            |
|-----------------|-----------------------|-------------------|
| `SND_MAIN`      | Main bullet hit       | `world/bullet.c`  |
| `SND_CHAIN`     | Chain gun hit         | `world/bullet.c`  |
| `SND_FLAME`     | Flamethrower hit      | `world/bullet.c`  |
| `SND_SROCKET`   | Small rocket hit      | `world/bullet.c`  |
| `SND_MORTAR`    | Mortar impact         | `world/bullet.c`  |
| `SND_ROCKET`    | Rocket launch         | `tanks/fire.c`    |
| `SND_EMPTY`     | Empty weapon          | `tanks/fire.c`    |
| `SND_SMOKESCR`  | Smoke screen deploy   | `tanks/fire.c`    |
| `SND_INVISIBLE` | Invisibility activate | `tanks/fire.c`    |
| `SND_SPEEDUP`   | Speed boost activate  | `tanks/fire.c`    |
| `SND_LIGHT`     | Light pickup          | `tanks/fire.c`    |
| `SND_EXPLODE`   | Tank destroyed        | `tanks/control.c` |
| `SND_CRASH`     | Vehicle crash         | `tanks/control.c` |
| `SND_SPLASH`    | Water impact          | `tanks/control.c` |

Sounds loaded from `DATA/SOUND.DAT` via Allegro `load_datafile()`. Background music via `play_midi()`.

**Ref:** `src/hardware/sound.c`
