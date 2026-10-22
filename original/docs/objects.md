# Tank Arena — World Objects

Everything that is not a map tile is an object (`objectstruct`, `src/include/types.h`) in one linked
list sorted by height `h`. Each object has a type, a position (top-left corner; the centre is 16 px
further), a height, a hit radius `r` (0 = cannot be hit), a goal-counter share `gc`, owner and target
pointers, and function pointers `control`, `write`, `mwrite` (editor/overview drawing), `hit`,
`remove` and `light`. `control_objects()` calls every object's `control` once per game step
(`src/objects/objects.c:137`). When an object is removed, its `gc` is applied to the goal counter
(`objects.c:129`).

Weapons that become objects (rockets, mines, mortar blasts, A-bombs) are described in `weapons.md`.

## Object types

From `src/include/define.h:226-245`:

| Value | Constant | Value | Constant |
|---|---|---|---|
| 0 | `TANK_TYPE` | 10 | `PLAYER_TYPE` (start pad) |
| 1 | `B52_TYPE` | 11 | `WARP_TYPE` |
| 2 | `MAN_TYPE` | 12 | `MORTAR_TYPE` |
| 3 | `TURRET_TYPE` | 13 | `FLAG_TYPE` |
| 4 | `ROCKET_TYPE` | 14 | `LOCK_TYPE` |
| 5 | `MINE_TYPE` | 15 | `GOAL_TYPE` |
| 6 | `ABOMB_TYPE` | 16 | `DESTROYER_TYPE` |
| 7 | `BONUS_TYPE` | 17 | `ENFORCER_TYPE` |
| 8 | `TRAIN_TYPE` | 18 | `ZEPPELIN_TYPE` |
| 9 | `WAGON_TYPE` | 19 | `PRODUCT_TYPE` |

Rockets, A-bombs and mortar blasts exist only at runtime. Bonuses are created at runtime from the
map's layers (below).

## Goal counter

`modify_goal_counter()` (`objects.c:223-245`) adds positive shares to the good counter and negative
shares to the bad counter. At 100 % good the player wins; at 100 % bad the player loses; the end
comes 300 ticks later (`game.end_round`). It does nothing in player vs player (`DUAL`) and once a
winner is set, and `-NOFAIL` suppresses the bad side. Shares come from removed objects (`o->gc`),
from destroyed buildings (the signed goal layer, see `terrain.md`), and from losing the last human
tank (−100).

## Goal

Removed, and so applies its share, when something reaches its radius `r` (default 30)
(`src/objects/goal.c`):

| `who` | Completed when |
|---|---|
| 0 | A good tank is within `r` — on supermarket maps only after all products are collected (`goal.c:22`) |
| 1 | An evil tank is within `r` |
| 2 | A train engine or wagon is within `r` |
| 3 | Its linked target object is within `r` |

## Flag

Player-vs-player flag hunt (`src/objects/flag.c`). `type` 0 is player one's flag, 1 player two's.

- Each flag gets a number per type at load; the game picks one flag per player at random
  (`game.flag_no`) and removes the others. Outside `DUAL` (and the editor) all flags are removed.
- A flag is picked up by driving over it, follows its carrier, and drops where the carrier dies.
- A tank that carries the **other** player's flag into its **own** base (within 7 px of its start
  pad) wins the round (`flag.c:29-33`).
- In a flag hunt players have unlimited lives.

## Start pad (`PLAYER_TYPE`)

Player-vs-player spawn point (`src/objects/player.c`). Each pad has a vehicle kind (0 tank or car,
1 helicopter, 2 plane, 3 race tank), a player and optional weapons (`weap.active`, `weap.nw[12]`)
that every vehicle from the pad gets.

- 4 lives per pad (`player.c:116`). A new vehicle appears 300 ticks after the last one died
  (`tnk.motion.dead`).
- When the building under the pad is destroyed, the pad drops to its last life.
- A vehicle within 7 px of its own pad is "in base" and is refilled (weapons, fuel, armor).

## Products (supermarket mayhem)

Any one-player or two-players-vs-computer map that contains products is a supermarket mayhem map
(`prepare_product()` sets `game.supermarket_mayhem`, `src/objects/product.c`). There are 15 product
kinds (washing powder, chocolate bar, apple, wine, … spaghetti).

| Rule | Value (`src/include/define.h:32-34`) |
|---|---|
| Products needed | 20 (`max_mayhem_products`) |
| Starting cash | $40 (`max_mayhem_cash`); each product costs its `price` (1) |
| Time limit | 180 s (`max_mayhem_time`) |

- Picking up a product costs its price; if the cash goes below 0 the bad counter gets −100.
- When the time runs out the bad counter gets −100 (`src/world/status.c:162-174`).
- `who = 0` goals stay closed until all needed products are collected.
- The display shows products needed, time left and cash, and a strip with the count of each product;
  the radar is hidden; human tanks get no start shield (`src/tanks/tank.c:293`).

## Lock

Makes one thing happen when another thing is destroyed (`src/objects/lock.c`).

| `lock_activate` | Fires when |
|---|---|
| 0 ("KEY") | The building under the lock is destroyed |
| 1 ("OBJ") | The nearest object at start (its owner) is gone, or is a tank or B52 with no armor left |

| `lock_target` | Action at `(l.x, l.y)` |
|---|---|
| 0 ("STR LOCK") | The structure there is removed (`HT_REMOVE`); until then it is indestructible (1000) |
| 1 ("OBJ DEST") | The nearest object there is destroyed (`HT_DESTROY`); this sets off destroyers |
| 2 ("OBJ REMV") | The nearest object there is removed |

Only target 0 makes anything indestructible (`prepare_lock()`).

## Destroyer

A map-making tool that wipes out an area when it is removed (`src/objects/destroy.c`). Defaults:
radius 30, immediate, `what` 3.

- `what` bit 1: structures within `r` are hit with `HT_DESTROY` 1000; bit 2: objects within `r` too.
- `immediate`: it removes itself on its first step, so it fires at the start of the game. Otherwise it
  fires when it is hit or removed, typically by a lock with target 1.

## Enforcer

Makes computer tanks of a side use a weapon (`src/objects/enforce.c`). Defaults: radius 30, evil
tanks only, weapon 6 (A-bomb), fire on, change off, delay 500.

- Applies to computer tanks within `r` of the chosen sides (`enf_good`, `enf_evil`).
- `fire`: every `delay` ticks the tank fires one round of `weapon` (the enforcer gives it the ammo).
- `change`: the tank keeps the weapon selected; otherwise it switches back after firing.

## Warp

Teleports tanks, men, B52s and rockets that come within 15 px of its entrance to its exit `(w.x,
w.y)`, with `TELEPORT_SPARK` effects and `SND_TELEPORT` at both ends; there is no cooldown
(`src/objects/warp.c`).

## Turret

A gun on a building (`src/objects/turret.c`). Turrets have no hit radius: they cannot be shot or
targeted by rockets. A turret without an owner disappears (with its goal share) as soon as the
building under it is destroyed — so turrets placed beside buildings vanish at the start.

Per turret: `type` (0..8, picture `TUR<type+1>`), `dir` (16 directions), `delay`, `power`, `radius`,
`shootat`, `fixed`, `forbid_dir`. Kind defaults (`ti[]`, `turret.c:7-15`):

| Kind | Delay | Power | Radius | Shoots at | Gun |
|---|---|---|---|---|---|
| 0 | 150 | 5 | 200 | 3 | Bullet, barrel 15 px |
| 1 | 200 | 5 | 200 | 4 | Guided rocket |
| 2 | 100 | 5 | 200 | 1 | Bullet |
| 3 | 10 | 1 | 200 | 1 | Chain gun (power below 2) |
| 4–7 | 150 | 5 | 200 | 1 | Bullet |
| 8 | 150 | 5 | 200 | 1 | Bullet, two barrels in turn |

- **Target:** the closest living, visible (not cloaked) tank allowed by `shootat`, at any range:
  0 any, 1 good, 2 evil, 3 any except player one, 4 any except player two.
- **Turning:** unless `fixed`, one direction step every 35 ticks toward the target, only while the
  target is within `radius`.
- **Firing:** when the target is within `radius`, the turret points exactly at it, `delay` has passed,
  and (unless fixed) the direction is not in the `forbid_dir` bit mask. Power below 2 fires the chain
  gun (`HT_CHAIN`).

## Men

Soldiers with a gun (`src/objects/man.c`), dropped by tanks (weapons 7 and 8, see `weapons.md`) or
jumping out of destroyed buildings (the men layers: weapon type `HT_CHAIN`, `HT_FLAME` or `HT_SROCK`,
up to 50 per tile). Paratroopers head for whoever destroyed the building.

- Walk at 5 px/s toward their target point; stop within 20 px.
- Fire every 40–80 ticks, range 5 tiles, 75 rounds.
- Die when hit, when out of rounds, in water, in a pit, or when they walk into a building; they leave
  `BLOOD` and scream.

## Bonuses

Pickups (`src/objects/bonus.c`). They are never stored in maps:

- On maps without random bonuses, every tile without a standing building gets the bonus of the
  bonus layer (signed, −1 = none) at the start (`create_all_bonusses()`).
- A destroyed building drops a bonus: on random-bonus maps by a roll of `rnd(10000)` (12.5 % chance
  in total, table below), otherwise the bonus layer's.
- Only tanks and cars pick them up; a shot destroys them.

| `type` | Pickup | Effect | Random chance |
|---|---|---|---|
| 0 | 10 GRENADES | Main gun +10 | 3 % |
| 1 | EXTRA GUN | Extra chain gun; if already present, 500 ROUNDS | 3 % |
| 2 | FUEL | Fuel to maximum | 1 % |
| 3 | 5 MINES | Mines +5 | 1 % |
| 4 | 3 ROCKETS | Rockets +3 | 0.5 % |
| 5 | A MORTAR SHELL | Mortar +1 | 0.5 % |
| 6 | NUCLEAR BOMB | A-bomb +1 | 0.5 % |
| 7 | 5 GUNNERS | Gunners +5 | 0.5 % |
| 8 | 5 FLAMERS | Flamers +5 | 0.5 % |
| 9 | SMOKE SCREEN | Smoke +1 | 0.5 % |
| 10 | A CLOAKING DEVICE | Cloak +1 | 0.5 % |
| 11 | SPEEDUP | Speed-up +1 | 0.5 % |
| 12 | INVULNERABILITY | 2000 ticks + 15 blinks | 0.25 % |
| 13 | ARMOR! | Armor +10, up to the vehicle's maximum | 0.25 % |

## Train and wagons

Trains run on rail tiles (`src/objects/train.c`): straight rails, curves of radius 49 px, crossings
and the fixed switch pictures (`RAILS_S_*`, `RAILS_W_*`; nothing ever changes a switch). There are no
stations. Each car stores its rail segment and the next tile.

| Property | Value |
|---|---|
| Engine armor / wagon armor | 60 / 40 |
| Engine speed | +5 px/s every 100 ticks up to 50; a wagon at the front slows by 5 |
| Wagon distance | Wagons close up to 31 px |

- A train ploughs through buildings, losing 20 px/s (down to 5) per hit; an indestructible building
  wrecks it. It crushes vehicles and wrecks other trains it runs into.
- Wagon picture 7 carries an A-bomb that goes off when the wagon is destroyed; picture 8 carries a
  turret (kind 5) owned by the wagon.
- A wagon without an engine never moves (as in `BASE1`).

## B52 bomber

(`src/objects/b52.c`)

- Placed on the map with a heading; waits 500–1000 ticks, then takes off: speed +1 every 80 ticks up
  to 25, and above 20 px/s height +1 every 60 ticks up to 19.
- Flies straight in one of 4 directions; after leaving the map by 3 tiles it re-enters over a random
  edge.
- Bombs: a salvo of 10 bombs every 4 × 30 + 500 = 620 ticks. Each bomb that hits nothing explodes on
  the ground and, with a chance of 1 in 10, leaves a crater on empty floor or a road
  (`b52.c:118-145`).
- Armor 30. Shot down, it sinks one height step per 60 ticks with explosions, and burns as a wreck on
  the ground. Below height 4 it crashes into buildings.
- On night maps its lights blink.

## Zeppelin

Only in the shareware build: the game itself creates one when a map is loaded (`mapfiles.c:324`); maps
do not place it. It flies west at height 20 and 10 px/s, wraps to the east edge at a random height,
and switches between 2 pictures every 100 ticks (`src/objects/zeppelin.c`).
