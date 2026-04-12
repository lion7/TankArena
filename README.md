# Tank Arena

This repository now contains both:
- the original legacy C codebase (reference behavior), and
- a new Kotlin rewrite foundation for Kotlin Multiplatform + KorGE.

## Rewrite modules

- `game-core`: deterministic, engine-agnostic gameplay simulation.
- `game-client-korge`: KorGE runtime client (desktop-first, web-ready architecture).
- `game-protocol`: shared DTOs for future multiplayer/replay pipelines.
- `game-editor`: early editor scaffold for future in-game editing mode.

See `docs/rewrite-plan.md` for architecture and migration mapping.

## Run the rewrite client

Requirements:
- JDK 21 (recommended for current Kotlin/Gradle setup)
- OpenGL runtime libraries available on the host


```bash
gradle :game-client-korge:run
```

Controls:
- Move: `WASD` or arrow keys
- Fire placeholder projectile: `Space`

## Run tests

```bash
gradle :game-core:jvmTest
```

## Notes

- The current vertical slice intentionally uses placeholder shapes instead of migrated art.
- Current slice adds: projectile-vs-tank collision damage, projectile TTL explosions, AI-driven enemy movement/fire, weapon behaviors (`basic_shell`, `triple_spread`), a basic last-tank-standing winner rule, tile-structure blocking for tanks/projectiles, snapshot DTO mapping, and a client-side keyboard action mapper class.
- Gameplay parity with legacy systems (weapons/AI/modes/editor) is TODO and tracked in the rewrite plan.


## Port status

The port is **not complete yet**. Current rewrite covers a vertical slice only. Still missing for full parity:
- legacy weapons and damage model
- collision and wall-hit systems parity
- AI behaviors
- game modes (deathmatch/race/CTF/co-op/missions)
- map compatibility loader/saver for legacy `.MAP` content
- audio parity and HUD/radar fidelity
- local multiplayer and replay/protocol integration
- usable editor implementation
