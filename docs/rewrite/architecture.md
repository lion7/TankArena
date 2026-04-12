# Tank Arena Rewrite Architecture

## Goals
- Desktop-first Kotlin rewrite with a maintainable module boundary.
- Pure gameplay simulation with no dependency on rendering or UI toolkits.
- Compose reserved for shell/editor UX.
- Kubriko reserved for game/runtime rendering.

## Modules
- `:game-core`: math, IDs, fixed-step timing.
- `:game-content`: canonical authored data.
- `:game-input`: device-agnostic player intents.
- `:game-sim`: authoritative simulation and events.
- `:game-protocol`: replay/network-ready DTOs.
- `:game-legacy`: import-only parser for legacy map formats.
- `:game-render-kubriko`: runtime projection layer for Kubriko integration.
- `:game-ui-compose`: shell/editor UI state and Compose-facing models.
- `:app-desktop`: desktop game host.
- `:app-editor`: standalone editor host.
- `:tools-mapconv`: legacy `.MAP` conversion CLI.

## Boundaries
- `:game-sim` must not depend on Compose or Kubriko.
- `:game-legacy` is the only place that understands raw legacy binary layouts.
- `:game-render-kubriko` consumes sim state but does not mutate authoritative rules.
- `:game-ui-compose` controls shell flows, not gameplay truth.

