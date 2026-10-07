# Parity Fixtures

All 121 shipped legacy `.MAP` files have been imported into `:game-content/resources/scenes/` as `(scene_<name>.json, metadata_<name>.json)` pairs. Re-run `tools-mapconv scene-all` to regenerate the set.

## Suggested first parity pool

A small representative pool to drive parity work and test coverage:

- `BEGIN1.MAP` — single-player onboarding mission.
- `FLAGISLE.MAP` — flag/capture variant.
- `RACE1.MAP` — race mode.
- `MISSION1.MAP` — early mission with mixed objectives.
- `FLAGRACE.MAP` — combined flag + race.

Each fixture should eventually carry:
- import snapshot (`(sceneJson, sidecar)`)
- expected mode compatibility (from `MapSceneSidecar.metadata`)
- key authored object inventory (turrets, goals, spawns)
- replay scenario coverage once the replay path lands

See [`docs/game/map-format.md`](../game/map-format.md) for what these maps express in legacy terms.
