# ADR 0007: Authoritative Headless Kubriko Server

- **Status:** Accepted (2026-04).
- **Decision:** the rewrite runs an authoritative simulation on a headless Kubriko instance inside `:game-server`. Single-player and (future) networked play share the same code path.

## Context

Pre-collapse, the rewrite had a pure-Kotlin `:game-sim` plus a `:game-render-kubriko` projection layer. That meant two parallel actor models — one for sim, one for render — with custom glue between them. It also left no obvious place to host a future networked server, since the client owned the gameplay loop in-process.

## Decision

- `:game-server` spins up a headless Kubriko via `Kubriko.newInstance` + `tick(Int)` per match.
- Server actors (`ServerTankActor`, `ServerTurretActor`, `ServerProjectileActor`, `ServerWallActor`, `ServerGoalActor`) implement `Collidable` plus `Serializable<T>`. Their `save()` produces a `:game-protocol` `ActorState`; the same `ActorState` types are used both for scene loading and for per-tick replication.
- A `WorldSnapshot` is built each tick by walking `actorManager.allActors`, calling `save()` on every `Serializable`, and tagging entries with the stable `actorId: Long` assigned at spawn.
- A custom `TerrainSlideManager` runs after Kubriko's `CollisionManager.onUpdate` to provide axis-by-axis MTV sliding response (Kubriko's collision plugin only detects overlap).
- `MatchClient` is the seam: `LocalMatchClient(ServerMatchPrototype)` runs the server in-process for single-player; a future `RemoteMatchClient` will swap in for networked play without touching the client viewport.
- The client never holds gameplay truth. It receives `ServerFrame` (snapshot + per-player views) and renders.

`PhysicsManager` (Box2D) is deliberately deferred — it fights grid-locked kinematic tanks. Reserved for explosive debris/knockback.

## Consequences

- One simulation code path, exercised by both single-player today and networked play later.
- `ActorState` is the single replication contract: scene-load and per-tick wire are the same shape.
- Server and client are decoupled enough that the client can be rewritten without touching gameplay rules.
- The server depends on Kubriko's published headless API; see [ADR 0008](0008-kubriko-and-scene-json.md).
