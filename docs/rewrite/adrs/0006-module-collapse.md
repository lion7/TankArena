# ADR 0006: Module Collapse 11 → 6

- **Status:** Accepted (2026-04, completed 2026-04-25).
- **Decision:** collapse the original 11-module layout to six modules: `:game-protocol`, `:game-content`, `:game-server`, `:game-client`, `:game-editor`, `:tools-mapconv`.

## Context

The original plan split the project into 11 modules: `:game-core`, `:game-content`, `:game-input`, `:game-sim`, `:game-protocol`, `:game-legacy`, `:game-render-kubriko`, `:game-ui-compose`, `:app-desktop`, `:app-editor`, `:tools-mapconv`. In practice that split produced more boundary cost than value:

- Many modules held only a handful of files.
- `:game-sim` and `:game-render-kubriko` each had to re-express the same actor model on either side of the wire.
- There was no single seam where a future networked server could slot in.
- The split discouraged sharing the Kubriko serialization machinery between server and editor.

## Decision

- `:game-core` and `:game-input` fold into `:game-protocol`.
- `:game-legacy` and `:tools-mapconv`'s library code fold into `:game-content`. `:tools-mapconv` survives as a one-file CLI module because Amper is one-product-per-module.
- `:game-sim` is deleted; the authoritative simulation moves to a new `:game-server` running headless Kubriko.
- `:game-render-kubriko`, `:game-ui-compose`, and `:app-desktop` fold into `:game-client`.
- `:app-editor` is renamed `:game-editor` and rewired around Kubriko's `SceneEditor`.

## Consequences

- Six modules, each with a clear responsibility.
- Server and client share actor *types* via `:game-protocol`, not actor *implementations* — server has `Server*Actor`, client has rendering actors that consume `ActorState` via `sync()`.
- The editor reuses server actor classes through `Editable<T>` for round-trip authoring, accepting a deliberate dependency from `:game-editor` to `:game-server` (see [ADR 0009](0009-editor-wraps-kubriko-scene-editor.md)).
- Folding `:tools-mapconv`'s library code into `:game-content` keeps the importer reachable from any module that needs it; the CLI stays as its own Amper product purely for entry-point reasons.
