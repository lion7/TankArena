# Extra maps

These 53 maps were in the repository but not in the original release of Tank Arena, which shipped
the 69 maps one level up. The game only offers the maps in `game-content/resources/maps`, which
`mapconv import-all` makes from the top level of `MAPS`, so the maps here are not playable.

To make one playable again, move it back to `MAPS` and import the maps again:

```bash
git mv "MAPS/extra/MISSION3.MAP" MAPS/
./kotlin run -m tools-mapconv -- import-all "$PWD/MAPS" "$PWD/game-content/resources/maps"
```
