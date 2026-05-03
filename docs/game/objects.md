# Tank Arena — World Objects

Objects are stored in a height-sorted linked list (`h` field). Each object has function pointers: `control`, `write`,
`hit`, `remove`, `light`. Objects are processed each frame in draw order.

## Object Type Index

| Constant       | Value | Description                     |
|----------------|-------|---------------------------------|
| `TANK_TYPE`    | 0     | Player or AI tank/chopper/plane |
| `BONUS_TYPE`   | 1     | Pickup bonus item               |
| `DESTROY_TYPE` | 2     | Area destroyer trigger          |
| `ENFORCE_TYPE` | 3     | AI weapon enforcer              |
| `FLAG_TYPE`    | 4     | Capture-the-flag                |
| `GOAL_TYPE`    | 5     | Win condition goal              |
| `LOCK_TYPE`    | 6     | Conditional trigger lock        |
| `WARP_TYPE`    | 7     | Teleport warp point             |
| `TRAIN_TYPE`   | 8     | Train engine or wagon           |
| `B52_TYPE`     | 9     | B52 bomber                      |
| `ZEPP_TYPE`    | 10    | Zeppelin (unregistered)         |
| `TURRET_TYPE`  | 11    | Stationary turret (9 variants)  |
| `MAN_TYPE`     | 12    | Walking man with weapon         |
| `MINE_TYPE`    | 13    | Light or heavy mine             |
| `ROCK_TYPE`    | 14    | Guided rocket                   |
| `MORTAR_TYPE`  | 15    | Mortar explosion                |
| `ABOMB_TYPE`   | 16    | Atomic bomb                     |
| `PRODUCT_TYPE` | 17    | Supermarket product             |
| `SMOKE_TYPE`   | 18    | Smoke screen cloud              |
| `LIGHT_TYPE`   | 19    | Light source (night mode)       |

## Bonus Items

Spawned randomly or placed in maps. 14 types (`b.which`):

| Index | Item            | Effect                         |
|-------|-----------------|--------------------------------|
| 0     | Grenades        | Extra main cannon ammo         |
| 1     | Extra Gun       | Additional weapon slot         |
| 2     | Fuel            | Restores fuel to max           |
| 3     | Mines           | Extra mine ammo                |
| 4     | Rockets         | Extra rocket ammo              |
| 5     | Mortar          | Extra mortar ammo              |
| 6     | Nuke            | A-bomb ammo (ATOMIC TANK only) |
| 7     | Gunners         | Men w/ Chain Gun ammo          |
| 8     | Flamers         | Men w/ Flamethrower ammo       |
| 9     | Smoke           | Smoke Screen ammo              |
| 10    | Cloak           | Invisibility ammo              |
| 11    | Speed Up        | Extra Speed ammo               |
| 12    | Invulnerability | Temporary invulnerability      |
| 13    | Armor           | Restores armor to max          |

Random bonus distribution uses weighted probabilities. Pickup restores the corresponding weapon count.

**Ref:** `src/objects/bonus.c`

## Destroyer

Destroys a large area when triggered.

| Field         | Description                                 |
|---------------|---------------------------------------------|
| `d.r`         | Destruction radius                          |
| `d.immediate` | If set, activates immediately on map load   |
| `d.what`      | What to destroy: 1=walls, 2=objects, 3=both |

`remove_destroyer()` calls `hit_wall_big()` and `check_obj_hit_radius()` for area destruction.

**Ref:** `src/objects/destroy.c`

## Enforcer

Forces AI tanks within radius to use a specific weapon.

| Field      | Description                                                      |
|------------|------------------------------------------------------------------|
| `e.r`      | Enforcement radius                                               |
| `e.good`   | Target good AI tanks                                             |
| `e.bad`    | Target evil AI tanks                                             |
| `e.weapon` | Weapon index to enforce                                          |
| `e.delay`  | Auto-fire delay (ticks)                                          |
| `e.change` | If set, changes weapon slot; otherwise just fires current weapon |

**Ref:** `src/objects/enforce.c`

## Flag

Capture-the-flag object. Two types (`f.which`): 0=Player 1, 1=Player 2.

- Flag follows its carrier tank.
- Game ends when enemy carries flag into your base zone (`und.in_base`).
- Sets `game.flag_hunt = TRUE` in DUAL/MAP modes.

**Ref:** `src/objects/flag.c`

## Goal

Win condition trigger. Four types (`g.who`):

| Value | Trigger                      |
|-------|------------------------------|
| 0     | Good tank enters radius      |
| 1     | Evil tank enters radius      |
| 2     | Train/wagon enters radius    |
| 3     | Tracked target enters radius |

Goal is removed on completion and modifies the goal counter (`gc_good`/`gc_bad`). 100% = win/loss.

**Ref:** `src/objects/goal.c`

## Lock

Conditional trigger. Sets structure strength to 1000 on prepare.

| Field        | Description                                                               |
|--------------|---------------------------------------------------------------------------|
| `l.activate` | Activation: 0=structure destroyed at location, 1=tracked object destroyed |
| `l.target`   | Action: 0=blow structure, 1=destroy object, 2=remove object               |

**Ref:** `src/objects/lock.c`

## Warp

Teleport point. Pairs input and output locations.

- Radius: 15 pixels.
- Teleports tanks, men, B52s, and rockets.
- Creates `TELEPORT_SPARK` effects and sounds at both locations.

**Ref:** `src/objects/warp.c`

## Train / Wagon

Follows rails (`RAILS_H`, `RAILS_V`, `RAILS_CR` variants and curves).

| Property            | Value                        |
|---------------------|------------------------------|
| Wagon spacing       | 31 pixels                    |
| Max speed           | 50                           |
| Engine armor        | 60                           |
| Wagon armor         | 40                           |
| Nuclear picture (7) | Spawns A-bomb on destruction |
| Turret picture (8)  | Mounted turret               |

Linked wagons maintain distance. Hit walls for damage or removal. Triggers goal type 2.

**Ref:** `src/objects/train.c`

## B52 Bomber

Spawns at map edge, lifts to height 19, then flies across screen.

| Property       | Value                               |
|----------------|-------------------------------------|
| Armor          | 30                                  |
| Bombs per run  | 10                                  |
| Bomb interval  | 500 ticks                           |
| Crash behavior | Falls with explosions, leaves wreck |
| Crater chance  | 1 in 100                            |

Creates wreck objects on destruction.

**Ref:** `src/objects/b52.c`

## Zeppelin

Flies across screen at height 20, speed 10. Wraps around map edges. 2-frame animation. Unregistered version only.

**Ref:** `src/objects/zeppelin.c`

## Turret

9 turret types. Configurable:

| Field         | Description                                |
|---------------|--------------------------------------------|
| `t.r`         | Detection radius                           |
| `t.delay`     | Fire delay (ticks)                         |
| `t.dir`       | Firing direction                           |
| `t.power`     | Bullet power                               |
| `t.shoot_at`  | Target filter: good/evil/player 0/player 1 |
| `t.fixed`     | Fixed vs rotating turret                   |
| `t.forbidden` | Forbidden firing directions                |

Fires `HT_MAIN` or `HT_ROCKET` at closest valid target.

**Ref:** `src/objects/turret.c`

## Men

Walking units with weapons.

| Property     | Value                              |
|--------------|------------------------------------|
| Max bullets  | 75                                 |
| Weapons      | Chain gun or guided rocket (SROCK) |
| Removal      | Any hit, water, or pits            |
| Death effect | `BLOOD` + scream sound             |

Walk toward target, fire at enemies.

**Ref:** `src/objects/man.c`

## Mines

Two types:

| Type         | Behavior                       |
|--------------|--------------------------------|
| `LIGHT_MINE` | Radius 30, `HT_MINE` damage    |
| `HEAVY_MINE` | 16 directional `SROCK` bullets |

Hidden underwater. Activate after 100 ticks (immediate in prepared maps).

**Ref:** `src/objects/mine.c`

## Guided Rocket

Homing projectile.

| Property  | Value                                  |
|-----------|----------------------------------------|
| Max speed | 150                                    |
| Range     | 3300 pixels                            |
| Trail     | `EXHAUST` effect                       |
| Explosion | `LEXPLOSION` on target or range expiry |
| Message   | "INCOMING MISSILE!"                    |

Homes toward target with acceleration. Inherits owner velocity on spawn.

**Ref:** `src/objects/rocket.c`

## Mortar Explosion

Expanding radius detonation.

| Property     | Value              |
|--------------|--------------------|
| Start radius | 5                  |
| Max radius   | 60                 |
| Hit type     | `HT_MORTAR`        |
| Parts        | `PT_MORTAR` debris |

Hits objects and walls within radius.

**Ref:** `src/objects/mortar.c`

## A-Bomb

Atomic bomb with countdown.

| Property      | Value                          |
|---------------|--------------------------------|
| Countdown     | 30 ticks                       |
| Radius growth | 0 to 210                       |
| Power         | 4                              |
| Hit type      | `HT_ABOMB`                     |
| Visual        | 3-frame countdown              |
| Tracking      | Nuke total for palette effects |

Spawned by ATOMIC TANK (idx 21) on death, by nuclear train (picture 7), or from ABOX terrain.

**Ref:** `src/objects/abomb.c`

## Products

15 products for Supermarket Mayhem mode.

| Property          | Value                     |
|-------------------|---------------------------|
| Max cash          | 40                        |
| Max product count | 20                        |
| Pickup            | Deducts cash from carrier |

**Ref:** `src/objects/product.c`
