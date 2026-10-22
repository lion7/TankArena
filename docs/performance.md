# Performance

How the game's speed and memory use are measured, what was found and fixed, and what is left for
later. Earlier work (2026-10-18) made the simulation fast enough: its own thread with a fixed-step
clock, A* with a heap and per-tick connected areas, and one sound line with a software mixer.

## Measuring

### Simulation time

```bash
TICK_BENCH=1 ./kotlin test -m game-sim       # per-map tick timings; budget 10 ms per tick
```

`TANKARENA_PERF=1` logs ticks, frame gaps and garbage collection once a second while the desktop
game runs (it opens a window).

### Allocation on the desktop

```bash
ALLOC_BENCH=1 ./kotlin test -m game-ui --platform jvm \
    --include-classes=nl.tankarena.app.AllocationBenchmarkTest
```

`AllocationBenchmarkTest` replays a recorded match of BEGIN1 and prints the bytes allocated per
simulation tick and per drawn frame at a phone's 2400×1080. For the allocation sites, run it under
Java Flight Recorder with small TLABs, or the samples are too sparse:

```bash
JAVA_TOOL_OPTIONS="-XX:-ResizeTLAB -XX:TLABSize=16k -XX:FlightRecorderOptions=stackdepth=1024 \
-XX:StartFlightRecording=filename=alloc.jfr,settings=profile" ALLOC_BENCH=1 ./kotlin test ...
jfr print --json --events jdk.ObjectAllocationSample --stack-depth 1024 alloc.jfr > alloc.json
```

The desktop is a poor model of Android here:

- Compose on the desktop wraps the bitmap in a new Skia `Image` for every `drawImage` call; that is
  about two thirds of a desktop frame's garbage and does not happen on Android, which hands the
  `Bitmap` to its canvas directly.
- The desktop JVM removes short-lived iterators, boxed numbers and pairs (escape analysis); Android's
  runtime allocates them. The simulation allocates far more per tick on Android than on the desktop.

### Allocation on Android

- The rate: `android.os.Debug.getRuntimeStat("art.gc.bytes-allocated")` read once a second from a
  temporary thread in `MainActivity`, logged with `Log.i`.
- The sites: Perfetto's Java heap sampling on the debug APK (Android 14 and later), the
  `perfetto` Python package to read the trace. Count samples per call site; the sizes are raw.

```text
data_sources { config { name: "android.heapprofd" heapprofd_config {
    sampling_interval_bytes: 4096  process_cmdline: "nl.tankarena"
    heaps: "com.android.art"  shmem_size_bytes: 33554432  block_client: true } } }
duration_ms: 10000
```

```bash
adb push hp.cfg /data/local/tmp/hp.cfg
adb shell "cat /data/local/tmp/hp.cfg | perfetto --txt -c - -o /data/misc/perfetto-traces/hp.pftrace"
adb pull /data/misc/perfetto-traces/hp.pftrace
```

## Allocation churn (2026-10-20)

On the emulator a mission allocated 6.3 MB/s (the menus nothing), and the garbage collector freed
about 24 MB every 4 seconds. Of the samples, 43 % came from the simulation, 35 % from drawing, 8 %
from the HUD, 10 % from Compose itself and 3 % from the touch controls.

Fixed, which brought it to 3.3 MB/s (−47 %):

1. Picture names are made once instead of with string templates on every lookup: tank bodies and
   turrets (`LegacySpriteResources`, also used by the simulation's collision masks), background
   tiles, products, flames, shells, bonuses, bombs, flags and the HUD's pictures and numbers.
2. The viewport keeps one set of every picture the scene has asked for; a tick only loads what is
   new instead of building and preloading the whole set again.
3. The HUD's picture names and the touch controls' labels are made and measured once.
4. Drawing reuses its list of visible things and sorts it with one comparator without boxing; the
   roofs are drawn in two passes instead of a list of triples; changed tiles are an `IntObjectMap`
   (no boxed indices); `ClientScene` keeps its id sets (`MutableLongSet`) between ticks;
   `TankActor` draws without lists.

## Later: the simulation's garbage

The simulation runs 100 ticks a second whatever the frame rate, and on Android it is now the
largest part of what is left. The work is mechanical but touches many places in `:game-sim`; the
rule tests and the recorded playthroughs guard behaviour. The sites the Android profile showed,
largest first:

- `ComputerTankAi`: `determinePit`, `determineBuilding`, `determineTerrain` and `centreOf` box
  numbers (`Integer.valueOf`) and make pairs; `ruleSteering` and `control` make a `Run` object and
  lists per tank per tick.
- `MatchAi.aiObjects` builds a new list of objects for every computer tank every tick.
- `MatchTrains.trainCollisions` makes an iterator per wagon per check.
- `MatchMission.evaluateMission` and `collectGoals` build lists every tick.
- The per-tick views for the screen: `Match.buildPlayerViews`, `buildRadar`, `buildSnapshot` and
  `smallObjectStates`. Part of this is inherent (a new immutable frame per tick), but the radar and
  lists can be filled into reused arrays.
- `MatchRecorder.record` makes map entries per tick.

The approach for each: loops by index over lists instead of `for (x in list)` and `forEach` (no
iterator), `IntArray`/`LongArray` or `androidx.collection` maps instead of boxed collections,
results written into reused buffers instead of new lists, no `Pair`/`Triple` in hot paths. Measure
again on Android afterwards; the desktop benchmark will hardly show the gain.

Smaller drawing leftovers: `StepInterpolation.interpolate` makes a new frame and maps per drawn
frame, `visibleTiles` returns a pair of ranges, and rockets build their picture names per tick.
