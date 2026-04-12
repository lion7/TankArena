# Migration Plan

## Implemented Foundation
- Multi-module Kotlin workspace scaffolded.
- Canonical map schema introduced in `:game-content`.
- Legacy `.MAP` parser implemented in `:game-legacy`.
- Supported legacy object import added for player starts, turrets, flags, goals, locks, warps, products, destroyers, and enforcers.
- Import CLI added in `:tools-mapconv`.
- First deterministic simulation scaffold added in `:game-sim`, including canonical-map-based simulation bootstrap.

## Next Steps
1. Replace placeholder desktop main with Compose shell and embedded Kubriko viewport.
2. Render imported tile layers from canonical maps.
3. Expand simulation from prototype entities to more imported authored objects and map rules.
4. Add fixture-based tests for legacy import and deterministic simulation.
