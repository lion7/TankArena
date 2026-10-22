# Map editor: manual test checklist

What to try by hand in the in-game map editor (roadmap phase 4,
[ADR 0021](adrs/0021-editor-in-the-game.md)) and with maps exported to the original
([ADR 0022](adrs/0022-native-map-format.md), option C). The automated tests cover the editing rules,
the round trips and the byte-exact export of the 69 original maps; this list covers what only a
person at the screen or in DOSBox can check.

## Getting there

Open the editor from the main menu ("Map editor", in Classic and in Extended).

Keys in the editor:

| Key | What it does |
|-----|--------------|
| 1, 2, 3 | Paint the floor, solid or top picture layer |
| 4 | Structures from the world's library |
| 5, 6, 7 | Goal shares, bonuses, men in buildings |
| 8 | Objects |
| 9 | The map's settings and texts |
| Ctrl+Z, Ctrl+Y | Undo, redo |
| Ctrl+S | Save |
| Ctrl+E | Export an original `.MAP` |
| F5 | Play-test |
| Esc | Leave (twice with unsaved changes) |

Files:

- Saved maps: `~/.local/share/tankarena/maps/NAME.tamap`
- Exported maps: `~/.local/share/tankarena/maps/export/NAME.MAP`
- The original install: `~/Games/dos/TankArena`

## Test cases

### 1. A new map

- [ ] Choose "- NEW MAP -", name it TEST2, pick a world, size 30 × 20, start "barriers all round".
- [ ] The size becomes 32 × 20 (rounded up to blocks of 4) with barriers all round.
- [ ] Esc warns once before leaving: the new map is not saved yet.
- [ ] A name with other characters than letters and digits, or longer than 8, is refused.

### 2. Painting and structures (keys 1–4)

- [ ] Paint floor tiles and a building on the solid layer; the layers above the painted one are faded.
- [ ] Right click picks up the picture under the cursor.
- [ ] Typing digits in "Find picture" filters the pictures and does not switch tools.
- [ ] With 4, place a one-cell and a multi-cell structure.
- [ ] Right click removes a building; on a cell without one it removes the floor.
- [ ] Shift and drag with the left button fills a rectangle; with the right button it clears it.
- [ ] Ctrl+Z undoes a whole stroke or region at once; Ctrl+Y redoes it.

### 3. Goal, bonus and men layers (keys 5–7)

- [ ] Goals: left adds 10, right takes 10 off, with Shift 1; the share stays within -100..100.
- [ ] Bonuses: left puts the chosen bonus, right removes it.
- [ ] Men: left adds one with the chosen weapon (at most 50), right removes one; another weapon
  changes the men already there without adding one.
- [ ] The values show on their cells.

### 4. Objects (key 8)

- [ ] Add a player vehicle ("Standard"), a computer vehicle, a turret, a warp and a goal.
- [ ] Drag them; right click turns the selected one; C clones it at the cursor; Delete removes it.
- [ ] Placing a start pad of a kind that is already there moves the existing one.
- [ ] Computer vehicle: "Click sets the waypoint", slot 0 and click, slot 1 and click; right click
  clears a slot; "Waypoints" set to "followed round". The waypoints show as a numbered line.
- [ ] Warp: set the exit; a line goes from the warp to it.
- [ ] Goal: "Click sets the object to reach", click the computer vehicle; a line joins them.
- [ ] Turret: change the fire radius with − and +; the circle follows. Click forbidden directions;
  red lines show them.
- [ ] Train: add a locomotive, couple wagons (also "With turret"), turn the locomotive.

### 5. Settings and texts (key 9)

- [ ] Set "one player", a code and a next code.
- [ ] Type a briefing, a text for a won and one for a lost mission.
- [ ] A line longer than 51 characters gets a red warning.
- [ ] A "Race P1" pad together with a "Tank P1" pad gives the start point conflict warning.

### 6. Play-test (F5)

- [ ] The briefing shows, then the map plays in its mode.
- [ ] The turret fires.
- [ ] The computer vehicle wakes when you come near and follows its waypoints.
- [ ] The warp takes you to its exit.
- [ ] Destroying the building with goal share 100 wins the mission.
- [ ] Back in the editor afterwards, with undo still working.

### 7. Saving and the game's lists

- [ ] Ctrl+S saves; leave and open TEST2 again from the list (marked `*`): everything is there.
- [ ] TEST2 is in the game's mission lists and can be started by its code.

### 8. Export and the original in DOSBox

Export with Ctrl+E (or "Export .MAP") and copy the file into the original's maps:

```bash
cp ~/.local/share/tankarena/maps/export/TEST2.MAP ~/Games/dos/TankArena/MAPS/
```

- [ ] In the original, a one-player game with the code you set (or TEST2) loads without
  "invalid object type".
- [ ] The objects are where you put them and face the way you turned them.
- [ ] The turret fires, the waypoints are followed, the warp and the goal work.
- [ ] The briefing and the texts after the mission show.
- [ ] The original's own editor (F3) loads the map and shows the objects.

The most useful case, because it checks edited objects that came from an original map:

- [ ] Open BEGIN3 (or another shipped map), move a turret, change a computer vehicle's settings and
  the mission code; export; play it in DOSBox. The turret is at its new place and the changes hold.

When something is wrong, note the map, what you did and what happened; the exported file and the
`.tamap` are enough to compare the records with the original's.

### 9. Edge cases

- [ ] A train on rails you painted runs in the play-test and in DOSBox.
- [ ] A map at the largest size (200 × 200) can be made, saved, play-tested and exported.
- [ ] A night-world map exports and plays.

## Results

| Date | Case | Result | Notes |
|------|------|--------|-------|
|      |      |        |       |
