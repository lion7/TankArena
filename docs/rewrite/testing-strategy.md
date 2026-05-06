# Testing Strategy

The bulk of meaningful coverage sits in `:game-server`, which owns authoritative simulation. `:game-content` covers parsing and the asset registry; `:game-protocol` is mostly data classes.

## `:game-server`
- Match bootstrap from generated scene JSON files.
- Tick round-trip: snapshot → state mutation → snapshot.
- `TerrainSlideManager` axis-by-axis sliding response against walls.
- Tank firing (cooldown gating, projectile owner immunity, TTL).
- Turret target acquisition and cooldown-gated fire.
- Goal capture (once-only claim, contribution accumulation, `MissionWon`).
- Tank lifecycle (damage → destruction → respawn countdown → respawn; mission win/loss conditions).
- First-pass AI motion and fire decisions.
- `PlayerView` HUD/radar wiring.
- Scene JSON serialization round-trips through the shared `tankArenaSerializableMetadata` registry.

## `:game-content`
- Legacy raw-byte parsing (`LegacyMapParser`).
- Generated legacy picture catalog behavior.
- Legacy object parsing for the supported subset.
- `MapSceneSidecar` round-trips.

## Determinism
The 100 Hz fixed-step simulation tick (ADR 0002) is the basis for replay. Replay storage decimates to 10 Hz keyframe snapshots plus per-tick `InputFrame`s and `GameEvent`s — the server is deterministic given inputs, so dense snapshots are redundant. The replay protocol in `:game-protocol` is in place but full replay recording/playback and checksum validation are still pending — see `full-rewrite-status.md`.

## Smoke
The `:game-client` Compose/Kubriko viewport is exercised by hand for now; UI smoke tests are pending.

## Parity reference
Test cases for gameplay rules should match the behavior described in [`docs/game/mechanics.md`](../game/mechanics.md), [`weapons.md`](../game/weapons.md), [`vehicles.md`](../game/vehicles.md), [`terrain.md`](../game/terrain.md), [`objects.md`](../game/objects.md), and [`ai.md`](../game/ai.md).
