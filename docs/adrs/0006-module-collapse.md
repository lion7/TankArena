# ADR 0006: Six Modules

- **Status:** Accepted (2026-04-25; revised 2026-10-19). Describes the current layout; it will be
  replaced by the multiplatform layout of [ADR 0025](0025-multiplatform-modules-and-android.md).

## Context

The first plan split the rewrite into eleven modules (`:game-core`, `:game-content`, `:game-input`,
`:game-sim`, `:game-protocol`, `:game-legacy`, `:game-render-kubriko`, `:game-ui-compose`,
`:app-desktop`, `:app-editor`, `:tools-mapconv`). Many held a handful of files, the simulation and
the renderer each re-expressed the same actor model, and there was no seam where a server could sit.

## Decision

Six modules, with dependencies pointing one way only:

```text
game-protocol  <-  game-content  <-  game-server  <-  game-client
                                                  <-  game-editor
                                                  <-  tools-mapconv
```

- `:game-core` and `:game-input` folded into `:game-protocol`.
- `:game-legacy` and the converter library code folded into `:game-content`; `:tools-mapconv` remains
  as a thin command-line module because the build tool builds one product per module.
- `:game-sim` was deleted; the simulation lives in `:game-server`.
- `:game-render-kubriko`, `:game-ui-compose` and `:app-desktop` folded into `:game-client`.
- `:app-editor` became `:game-editor`.

Responsibilities and rules per module are in [architecture.md](../architecture.md#modules).

## Consequences

- Few, clear boundaries; the server/client seam of [ADR 0005](0005-network-ready-input-protocol-boundary.md)
  is a module boundary.
- `:game-content` holds both runtime content code and the converter library (extractors under
  `com.tankarena.tools.mapconv`), so the shipped client carries converter classes it never calls.
  That is accepted; moving them into `:tools-mapconv` is a possible cleanup.
