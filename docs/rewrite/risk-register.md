# Risk Register

Each risk carries a **Status**:
- **Accepted** — known, deliberately carried; mitigations are about detection, not avoidance.
- **Active** — being mitigated as the rewrite progresses.
- **Sunsetting** — time-limited; the risk goes away once a specific milestone lands.

## Kubriko Version Coupling
- **Status:** Accepted.
- **Risk:** the rewrite depends on Kubriko `0.1.2` from Maven Central, including the headless `Kubriko.newInstance` + `tick(Int)` API and the scene serialization format. A breaking change in a future Kubriko release could ripple through the server tick path and the shipped scene files.
- **Why accepted:** Kubriko's serialization format is not expected to churn often, and regenerating the 121 shipped scenes is cheap (`tools-mapconv scene-all`). Tracking an upstream fork would cost more than occasional regeneration.
- **Mitigations:**
  - Pin the Kubriko version in every `module.yaml`. Bumps are deliberate, not floating.
  - On every Kubriko bump, run `tools-mapconv scene-all` and the `:game-server` test suite before committing.
  - Keep at least one canonical scene + sidecar pair under version control as a smoke target so format drift surfaces on the first bump rather than at runtime in a player's hands.
  - See [ADR 0008](adrs/0008-kubriko-and-scene-json.md). The "Scene JSON format drift" subcase is the same root cause and is covered here.

## Gameplay Parity
- **Status:** Active.
- **Risk:** rewrite gameplay drifts from the original feel as systems land piecemeal.
- **Mitigations:**
  - Drive parity work from [`docs/game/`](../game/) (mechanics, vehicles, weapons, terrain, objects, AI). Treat it as the spec, not the rewrite source.
  - Track the gap list explicitly in [`full-rewrite-status.md`](../full-rewrite-status.md) §5–§6 so unfinished parity is visible per release.
  - Match the legacy 100 Hz simulation tick ([ADR 0002](adrs/0002-fixed-step-50hz.md)) so per-tick numbers (cooldowns, projectile TTL, respawn delays) transfer directly without rescale.
  - Add server-side tests that pin numeric behavior (cooldown ticks, projectile TTL, respawn delay, turret rotation cadence) against the values in [`mechanics.md`](../game/mechanics.md) and [`weapons.md`](../game/weapons.md).
  - Add replay/checksum coverage as `:game-protocol` snapshots harden, so divergence between releases is detectable.

## Server/Client Replication Coupling
- **Status:** Active.
- **Risk:** server actors double as scene-load and per-tick replication payloads through a single `Serializable<T>.save()`. A schema-breaking change ripples through scenes, replays, and the wire at once.
- **Mitigations:**
  - Keep `ActorState` types small and additive; default new fields where possible so older scenes still load.
  - Gate schema changes through `:game-protocol` review — that module is the contract.
  - Add a version field to `WorldSnapshot` (or scene metadata) so wire/scene compat can be detected at load. Currently neither carries one.
  - Add a server test that round-trips every `Server*Actor` through `save()` / scene-load and asserts equality on observable state, to catch silent drift.

## Editor Scope
- **Status:** Active (low).
- **Risk:** sharing actor state with `:game-server` to power the editor blurs the rule that the editor must not run server simulation.
- **Mitigations:**
  - The current `Editable<T>` boundary is clean: the editor consumes server actor *classes* and their `State`, not server managers. Preserve this — see [ADR 0009](adrs/0009-editor-wraps-kubriko-scene-editor.md).
  - Add a build-time guard (or a test) that the editor module does not transitively reference `CollisionManager`, `TerrainSlideManager`, or the mission evaluator from `:game-server`.

## Legacy Import Coverage
- **Status:** Sunsetting.
- **Risk:** legacy object families not yet handled by `LegacyObjectParser` are silently dropped on import; runtime behavior in shipped scenes diverges from the original.
- **Direction:** the legacy importer is transitional. Once all 121 shipped maps are committed as scene JSON + sidecar with empty `importNotes`, the entire legacy parsing surface is removed (see [ADR 0003](adrs/0003-import-once-canonical-map-format.md)).
- **Mitigations:**
  - **Finish phase.** Extend `LegacyObjectParser` to cover the remaining object families enumerated in [`docs/game/objects.md`](../game/objects.md) — currently nine types are mapped; flags/products are partial; trains, zeppelin, B52, and any destroyer/enforcer sub-variants need real coverage.
  - `MapSceneSidecar.importNotes` records skipped/unmapped objects, so coverage gaps stay visible. Add an integration test that walks every `.MAP` under `original/` through `LegacyMapImporter` and asserts each result has an empty `importNotes`.
  - Commit the regenerated 121 scene JSON + sidecar pairs as the canonical content.
  - **Drop phase.** Once content is committed and stable, remove `:game-content/.../LegacyMap*` raw parsing, `:game-server/.../legacy/*`, the `tools-mapconv scene` / `scene-all` subcommands, and move the `original/` raw `.MAP` inputs out of the build's source set into a sibling archive directory. The drop trigger is documented in [ADR 0003](adrs/0003-import-once-canonical-map-format.md).

## Tick-Rate Drift
- **Status:** Active.
- **Risk:** three places currently disagree on the simulation tick rate — [ADR 0002](adrs/0002-fixed-step-50hz.md) says 100 Hz, `FixedStepClock.kt` says 60 Hz, `ServerMatchPrototype.kt` advances 33 ms per tick (~30 Hz). Per-tick parity numbers are therefore being interpreted against three different clocks depending on which file is the source of truth.
- **Mitigations:**
  - Reconcile the three values in code per the follow-up listed at the end of [ADR 0002](adrs/0002-fixed-step-50hz.md).
  - Have all `*_TICKS` constants on server actors derive from a single `FixedStepClock.TICKS_PER_SECOND` constant rather than literal numbers, so future rate changes are one-line.
