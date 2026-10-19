# Tank Arena — Engine Mechanics

## Tick Rate

The timer runs at 100 Hz (10 ms per tick); `sec = 100` in `define.h`. The interrupt handler only increments `timer`
(and stops while `game_pause` is set).

```c
// src/hardware/timer.c
install_int_ex(game_tick, BPS_TO_TIMER(sec));  // 100 ticks per second
```

Timer macros (`src/include/function.h:295-297`):

| Macro      | Expands To           | Purpose                         |
|------------|----------------------|---------------------------------|
| `tinit(x)` | `timer + x`          | Schedule event x ticks from now |
| `tdone(x)` | `timer >= x`         | Check if scheduled time elapsed |
| `twait(x)` | `while (x >= timer)` | Block until the timer passes x  |

All durations in these documents are in timer ticks unless stated otherwise.

### Game Loop

`game_control()` does one game step (`src/game/mainloop.c:17-32`):

```c
twait(np);          // wait until timer > np
np = tinit(2);
read_controls();
transportation();   // conveyor and rotating tiles move objects
control_effects();  // advance effect animations
control_bullets();  // move bullets, hit walls and objects
control_parts();    // move debris, hit walls and objects
check_obj_overlap();// tanks that overlap each other are destroyed
control_objects();  // control function of every object (tanks, AI, mines, ...)
palette_effects();  // colour cycling and the A-bomb flash
```

Because `twait()` waits until `timer > np` and `np` is set to `timer + 2` right after the wait, two game steps are at
least 3 timer ticks apart: about 33 steps per second, fewer when drawing takes longer. Movement code takes the
elapsed time from the timer (`dt`), so speeds in pixels per second do not depend on the step rate. Counters that change
by a fixed amount per call do: the guided rocket gains 1 px/s per step (`src/objects/rocket.c:29`), and a rotor with
`auto_rotation` 0 turns one step per game step.

Every mode runs the same loop; fade-in and fade-out wrap it (`src/game/single.c:82-94`):

```c
do {
  game_control();
  write_screen();
  done = !do_fade_step();
} while (!tdone(game.end_round) && !game.exit_game);
```

## Rendering Pipeline

The game screen uses the mode chosen in SETUP (default 640×480, see `overview.md`); menus use 320×200 mode 13h. Each
map window is drawn into `scrbuf` and blitted to the screen. With 2 video pages the pages are flipped
(`src/game/mainloop.c:105-115`).

### Per-Viewport Draw Order

```text
1. ssx/ssy centred on the tank, or on the lock marker while it is in use (clamped to the map)
2. bottom_layer(tn, ssx, ssy)       // layers 0 and 1 (cached background)
3. write_objects(NULL)              // objects and effects by height, h = -4..24
   ├── h == 4: top_layer() + write_parts() + write_bullets()
   └── night:  light functions
4. tank_write_marker(o)             // lock-on marker
5. b_convert(scr, gray, ...)        // grey picture if this player lost (not on night maps)
6. write_radar(o)                   // radar, not on supermarket maps
7. write_status(o)                  // bars, weapons, messages
8. blit(scrbuf, screen, ...)
```

Without a tank to follow, the window shows random static noise (`src/game/mainloop.c:81-91`).

### Depth Sorting

Objects are drawn in height order from `h = -4` to `h = 24`. At `h == 4` the top terrain layer, parts and bullets are
drawn, between low objects and high objects (`src/objects/objects.c:157-187`).

```c
// src/objects/objects.c
for (h = -4; h < 25; h++) {
  if (h == 4) { top_layer(ssx, ssy); write_parts(); write_bullets(); }
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

| Layer            | Content                                                        |
|------------------|----------------------------------------------------------------|
| Layer 0 (bottom) | Ground: water, roads, rails, pits, lava, conveyors, ramps      |
| Layer 1 (middle) | Structures (buildings, walls, bushes, bridges)                 |
| Layer 2 (top)    | Upper parts of structures, drawn with a shadow at `h = 4`      |

A structure picture has an intact frame (`ptr[0]`) and a destroyed frame (`ptr[1]`), chosen by the strength of that
layer. Layer 2 is only drawn while the layer 1 structure below it stands. On night maps a `LIGHT_SRC` picture adds its
light mask (`ptr[2]`) (`src/world/under.c:214-254`).

### Palette Effects

`palette_effects()` cycles colour ranges (water, rolling conveyors, teleport, glow, lava). While an A-bomb explodes,
`nuke_total` is added to every colour, which fades the screen towards white and back (`src/graph/palette.c:101-119`).

**Ref:** `src/game/mainloop.c`, `src/objects/objects.c`, `src/world/under.c`, `src/graph/sprites.s`,
`src/graph/palette.c`

## Sound System

Distance-attenuated stereo audio via Allegro digitized samples (`src/hardware/sound.c:60-108`).

### Distance Attenuation

```text
d = sqrt((sx - px)² + (sy - py)²)    // Euclidean distance

if d < 100:       volume = 255        // Full volume (near field)
if 100 ≤ d < 612: volume = (612 - d) / 2  // Linear falloff
if d ≥ 612:      volume = 0          // Silent (max range)
```

Max audible range: 612 pixels (~18.5 tiles).

### Stereo Panning (Dual Mode)

```text
pan = -(612 - d1) / 4 + (612 - d2) / 4 + 128
clamped to [0, 255]
```

`d1` = distance to player 1, `d2` = distance to player 2. Sound uses minimum distance for volume, panning based on
relative position.

### Pitch Variation

Each sound plays at `900 + rnd(200)`, giving a pitch range of 900–1100. Adds variation to repeated sounds.

Volume threshold: sounds with calculated volume ≤ 32 are not played.

### Sound Samples

| Constant        | Trigger                                 | Source             |
|-----------------|-----------------------------------------|--------------------|
| `SND_MAIN`      | Main gun fires (`HT_MAIN`)              | `world/bullet.c`   |
| `SND_CHAIN`     | Chain gun fires                         | `world/bullet.c`   |
| `SND_FLAME`     | Flamethrower fires                      | `world/bullet.c`   |
| `SND_SROCKET`   | Small rocket fired                      | `world/bullet.c`   |
| `SND_MORTAR`    | Mortar fired                            | `world/bullet.c`   |
| `SND_NUKE`      | Mortar or plane bomb lands; A-bomb blast | `objects/mortar.c` |
| `SND_ROCKET`    | Guided rocket launched                  | `tanks/fire.c`     |
| `SND_INCOMING`  | Rocket locked on (at the target)        | `objects/rocket.c` |
| `SND_EMPTY`     | Weapon out of ammo                      | `tanks/fire.c`     |
| `SND_SMOKESCR`  | Smoke screen used                       | `tanks/fire.c`     |
| `SND_INVISIBLE` | Invisibility used                       | `tanks/fire.c`     |
| `SND_SPEEDUP`   | Extra speed used                        | `tanks/fire.c`     |
| `SND_LIGHT`     | Light switched on or off                | `tanks/fire.c`     |
| `SND_EXPLODE`   | Explosion of a hit or a wreck           | `check/hitwall.c`  |
| `SND_COLLAPSE`  | Structure destroyed                     | `check/hitwall.c`  |
| `SND_RICOCHETS` | Chain gun hit (1 in 10)                 | `check/hitwall.c`  |
| `SND_CRASH`     | Vehicle bumps into something            | `tanks/control.c`  |
| `SND_SPLASH`    | Tank enters or leaves water             | `tanks/control.c`  |
| `SND_PICKUP`    | Bonus picked up                         | `objects/bonus.c`  |
| `SND_TELEPORT`  | Warp used                               | `objects/warp.c`   |
| `SND_SCREAM`    | Man dies                                | `objects/man.c`    |
| `SND_BEEP`      | Race checkpoint passed                  | `objects/player.c` |

The firing sounds are played by `create_bullet()` when the bullet is made, not when it hits
(`src/world/bullet.c:98-122`). Sounds are loaded from `DATA/SOUND.DAT` via Allegro `load_datafile()`. Background
music via `play_midi()`.

**Ref:** `src/hardware/sound.c`
