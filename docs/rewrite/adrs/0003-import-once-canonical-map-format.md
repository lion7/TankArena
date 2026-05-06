# ADR 0003: Import Legacy Maps Once

- **Status:** Accepted (revised 2026-05-06).
- **Decision:** legacy `.MAP` files are converted into Kubriko scene JSON + `MapSceneSidecar` pairs and are not the long-term runtime source. The legacy parsing surface is **transitional**: once content is committed it is removed from the build entirely.

## Context

The original game ships maps as a bespoke binary `.MAP` format. The rewrite consumes Kubriko scene JSON at runtime ([ADR 0008](0008-kubriko-and-scene-json.md)). The `.MAP` format is not extensible by us, contains hardware-era encoding choices, and does not map cleanly onto Kubriko actors. Treating it as a runtime input would mean carrying a parser into shipped builds forever for content that never changes after import.

## Decision

- Legacy `.MAP` files are import-only. The full pipeline (raw byte parsing in `:game-content`, object/canonical conversion in `:game-server`, scene JSON emission via `LegacyToSceneJson`, CLI driver in `:tools-mapconv`) exists to migrate the 121 shipped maps once.
- Authored runtime content is the resulting scene JSON + sidecar pairs in `:game-content/resources/scenes/`. They are committed and treated as canonical.
- The legacy parsing surface is **scheduled for removal**. The drop trigger is:
  1. Every legacy object family enumerated in [`docs/game/objects.md`](../../game/objects.md) is parsed by `LegacyObjectParser`.
  2. Every `.MAP` under `original/` round-trips through `LegacyMapImporter` with an empty `MapSceneSidecar.importNotes` (asserted by an integration test).
  3. The regenerated 121 scene JSON + sidecar pairs are committed.
- When the trigger is satisfied, remove:
  - `:game-content/.../LegacyMap*` raw byte parsing (`LegacyMapParser`, `LegacyMapData`, `LegacyMapHeader`).
  - `:game-server/.../legacy/*` (`LegacyObjectParser`, `LegacyCanonicalConverter`, `LegacyToSceneJson`, `LegacyMapImporter`).
  - The `tools-mapconv scene` and `scene-all` subcommands. `:tools-mapconv` may then be deleted or repurposed for non-legacy tooling.
  - The `original/` raw `.MAP` inputs are moved out of the build's source set into a sibling archive directory; they remain in version control as historical reference but are not consumed by any module.

## Consequences

- Runtime builds shrink: no legacy parser, no raw `.MAP` blobs.
- The migration is irreversible from the build's perspective. Any future content changes must happen in the editor against scene JSON, not by re-running a `.MAP` import.
- The risk that drives this decision (legacy import coverage) is tracked as **Sunsetting** in [`risk-register.md`](../risk-register.md) and disappears when the drop lands.
