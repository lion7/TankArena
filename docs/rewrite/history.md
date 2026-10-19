# Tank Arena Rewrite — History

How the rewrite got where it is. Nothing here describes the current state; for that see
[`full-rewrite-status.md`](../full-rewrite-status.md). Deleted documents are still in git history.

## Timeline

| When | What |
|---|---|
| 2026-04 | Engine survey (recommended KorGE); Kubriko chosen instead ([ADR 0001](adrs/0001-desktop-first-compose-kubriko.md)). Eleven-module plan. |
| 2026-04-25/26 | Module collapse to six modules; authoritative headless Kubriko server; scene JSON as map format; canonical map schema removed ([ADR 0006](adrs/0006-module-collapse.md), [0007](adrs/0007-authoritative-headless-kubriko-server.md), [0008](adrs/0008-kubriko-and-scene-json.md)). Gradle replaced by Amper. |
| 2026-05 | Kubriko 0.1.2 from Maven Central replaces a local fork. 100 Hz tick. Parity tasks T01–T32 implemented from descriptions. |
| 2026-09-25 | Code-verified status audit: the game as played was ~20–25 % of parity. Shipped scenes made playable end to end; split screen, real audio. |
| 2026-10-04 | Kubriko 0.7.3. Testing strategy written. |
| 2026-10-04 to 06 | Direction change: port the original's rules from the C source ([ADR 0010](adrs/0010-port-the-original-rules.md)): vehicle table, structures and pixel collisions ([0011](adrs/0011-structure-grid-and-pixel-masks.md)), aircraft, all weapons, bonuses, soldiers, trains, locks, destroyers, enforcers, presentation, match recordings. |
| 2026-10-07 | B52 and zeppelin, force fields, ramps, pilot faces, night maps; Amper replaced by the Kotlin toolchain ([0016](adrs/0016-kotlin-toolchain-build.md)). |
| 2026-10-18 | Original screen layout and fonts ([0013](adrs/0013-original-presentation.md)); simulation thread ([0014](adrs/0014-simulation-thread-and-recordings.md)); computer AI ported ([0012](adrs/0012-port-the-computer-ai.md)); player-vs-player rounds, flags, races; signed goal layer; only the 69 original maps ([0015](adrs/0015-ship-the-original-69-maps.md)); intro, options, music. |
| 2026-10-19 | Turret kinds; packaging ([0017](adrs/0017-desktop-packaging.md)); documentation rebuilt, ADRs 0010–0020 written, Kubriko to leave the server ([0019](adrs/0019-remove-kubriko-from-the-server.md)). |

## The eleven-module plan (2026-04)

`:game-core` (math, IDs, timing), `:game-content` (canonical map data), `:game-input` (intents),
`:game-sim` (authoritative world), `:game-protocol` (replay/network DTOs), `:game-legacy` (the only
module that understood `.MAP`), `:game-render-kubriko`, `:game-ui-compose`, `:app-desktop`,
`:app-editor`, `:tools-mapconv`. Phases: analyse legacy → scaffold → vertical slice → combat → modes
and AI → content breadth → editor and tools → polish.

The collapse (eight steps, 2026-04-25/26): headless server prototype; server actors with a
`TerrainSlideManager`; replication types and `LocalMatchClient`; `:game-server` extracted; core and
input folded into protocol; importer rewritten to emit scene JSON, legacy and converter library folded
into content; client modules folded into `:game-client` and `:game-sim` deleted; `:app-editor` became
`:game-editor` on Kubriko's `SceneEditor`.

`:game-sim` had: a deterministic fixed-step loop, wraparound coordinates, a passability grid from the
solid layer, axis-by-axis sliding, projectile and tank collisions, respawn, and turrets.

## The parity task list (2026-05, deleted 2026-10-19)

`docs/rewrite/parity-tasks.md` listed tasks T01–T43 plus TFN1/TFN2, each with goal, spec, files,
acceptance and tests, meant for autonomous agents one task at a time. T01–T06 and T08–T32 were marked
done by 2026-05-11; T07 (drop the importer) was deferred.

The 2026-09-25 audit found many "done" tasks unreachable in real play (terrain only on a test path,
destroyers turned into tanks, no waypoints, stub audio, wrong damage values). Most of T08–T32 was
later replaced by ports of the C source: A*/line of sight/waypoints (T26–T28) by `ComputerTankAi`,
box walls by `StructureGrid`, the replay plan (T36–T38) by input recordings. Open items that survived
are in [parity.md](parity.md) and the [testing strategy](testing-strategy.md).

## Other deleted documents (2026-10-19)

- `docs/rewrite/status-audit.md` (2026-09-25): the code-verified audit and its milestone log;
  superseded by [parity.md](parity.md) and the status document.
- `docs/rewrite/parity-fixtures.md`: a "first parity pool" (BEGIN1, FLAGISLE, RACE1, MISSION1,
  FLAGRACE) of 121 maps; superseded by recordings and shipped-scene tests. MISSION1 is no longer
  shipped.
- `docs/COMPARISON.md` (2026-05-19, refreshed 2026-10-07): a C-versus-Kotlin comparison estimating
  55–60 % completion; outdated after the October ports.
- `docs/asset-migration-audit.md`: an asset inventory written for a KorGE port; superseded by
  [assets.md](assets.md).
- `docs/deep-research-report.md`: the 2026-04 survey of Kotlin multiplatform engines that recommended
  KorGE (with LittleKt as alternative) for a web-first port; the project chose a desktop-first
  Compose and Kubriko stack instead (ADR 0001).

## Superseded decisions, in short

| Earlier decision | Replaced by |
|---|---|
| 50 Hz tick | 100 Hz ([ADR 0002](adrs/0002-fixed-step-100hz.md)) |
| 10 Hz snapshot keyframes for replays, `ReplayProtocol` | Input recordings ([ADR 0014](adrs/0014-simulation-thread-and-recordings.md)) |
| Import maps once, then delete the importer | Open question ([ADR 0003](adrs/0003-legacy-map-import.md)) |
| One wall actor per tile, sliding collisions | Structure grid with pixel masks ([ADR 0011](adrs/0011-structure-grid-and-pixel-masks.md)) |
| A*, line of sight, waypoint follower | Port of `computer.c` ([ADR 0012](adrs/0012-port-the-computer-ai.md)) |
| Kubriko managers run the server | Plain-Kotlin server planned ([ADR 0019](adrs/0019-remove-kubriko-from-the-server.md)) |
| Gradle, then Amper | Kotlin toolchain ([ADR 0016](adrs/0016-kotlin-toolchain-build.md)) |
| All 121/122 maps in `MAPS/` | The 69 of the original release ([ADR 0015](adrs/0015-ship-the-original-69-maps.md)) |
