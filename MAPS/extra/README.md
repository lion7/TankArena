# Extra maps

These 53 maps were in the repository but not in the original release of Tank Arena, which shipped
the 69 maps one level up. The game only offers the maps in `game-content/resources/scenes`, which
`mapconv scene-all` generates from the top level of `MAPS`, so the maps here are not playable.

To make one playable again, move it back to `MAPS` and regenerate the scenes:

```bash
git mv "MAPS/extra/MISSION3.MAP" MAPS/
./kotlin run -m tools-mapconv -- scene-all "$PWD/MAPS" "$PWD/game-content/resources/scenes"
```
