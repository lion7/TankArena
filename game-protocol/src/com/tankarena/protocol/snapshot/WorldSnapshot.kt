package com.tankarena.protocol.snapshot

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire-protocol version carried by every [WorldSnapshot]. Bump on any breaking change to
 * the snapshot, actor-state, event, or input-frame payloads. Mismatches between client
 * and server versions must fail fast at decode time rather than producing silent drift.
 */
const val PROTOCOL_VERSION: Int = 6

/**
 * Full authoritative world snapshot emitted by the server every server tick.
 * Clients replicate this onto existing render actors by matching [ActorState.actorId].
 *
 * For now this is a full-state snapshot per tick; the shape accommodates per-actor deltas
 * or binary framing later without schema churn on the consumer side.
 */
@Serializable
data class WorldSnapshot(
    val tick: Long,
    val actors: List<ActorState> = emptyList(),
    val events: List<GameEvent> = emptyList(),
    /** Map tiles whose pictures changed since the start (destroyed buildings, bridges turned to water). */
    val changedTiles: List<TileState> = emptyList(),
    /** Legacy `nuke_total`: 0..63 added to every palette colour while an A-bomb goes off. */
    val flash: Int = 0,
    val protocolVersion: Int = PROTOCOL_VERSION,
)

private val snapshotJson = Json { ignoreUnknownKeys = true }

/**
 * Decode a [WorldSnapshot] from JSON, failing fast when the encoded snapshot's
 * [WorldSnapshot.protocolVersion] is newer than [PROTOCOL_VERSION]. Older snapshots
 * are accepted so legacy replays remain readable.
 */
fun decodeWorldSnapshot(json: String): WorldSnapshot {
    val snapshot = snapshotJson.decodeFromString(WorldSnapshot.serializer(), json)
    require(snapshot.protocolVersion <= PROTOCOL_VERSION) {
        "WorldSnapshot protocolVersion=${snapshot.protocolVersion} is newer than the runtime " +
            "protocolVersion=$PROTOCOL_VERSION; refusing to load."
    }
    return snapshot
}

@Serializable
data class PlayerView(
    val playerId: Int,
    val controlledActorId: Long?,
    val cameraCenterX: Int,
    val cameraCenterY: Int,
    val cameraWidth: Int = 640,
    val cameraHeight: Int = 400,
    val hud: HudState,
    val radar: List<RadarContact> = emptyList(),
    /** Rocket lock-on marker while it is being steered or just after launch; null otherwise. */
    val lockMarker: LockMarker? = null,
)

/** The legacy rocket marker (`tank_write_marker()` in src/game/mainloop.c), in world pixels. */
@Serializable
data class LockMarker(
    val x: Int,
    val y: Int,
    val state: LockMarkerState,
)

/** Which of the four legacy marker pictures (`MARKER + l`) to draw. */
@Serializable
enum class LockMarkerState {
    /** Steering, nothing under the marker. */
    SEARCHING,

    /** Steering, a target is under the marker. */
    ON_TARGET,

    /** The rocket is on its way. */
    LAUNCHED,

    /** Steering, but further than 400 px from the tank. */
    OUT_OF_RANGE,
}

@Serializable
data class HudState(
    val armor: Int = 0,
    val shield: Int = 0,
    val invulnerableTicks: Int = 0,
    val fuel: Int = 0,
    val lives: Int = 0,
    val missionProgress: Int = 0,
    val missionCode: String = "",
    val statusText: String = "",
    val score: Int = 0,
    val kills: Int = 0,
    val time: Long = 0,
    val currentWeapon: Int = 0,
    /** Ammo per legacy weapon number (`weap.nw`). */
    val ammo: List<Int> = emptyList(),
    /** Legacy `weap.flame`: the flamethrower is available without ammo. */
    val hasFlamer: Boolean = false,
    /** Legacy `game.id`: the F1–F10 id of the tank this player drives (-1 for none). */
    val tankId: Int = -1,
    val maxArmor: Int = 0,
    val maxFuel: Int = 0,
    val missionProgressBad: Int = 0,
    /** Legacy `msg`: the pilot's message lines and the steps (29 → 0) it has left. */
    val messageLine1: String = "",
    val messageLine2: String = "",
    val messageSteps: Int = 0,
    /** The vehicle whose pilot speaks (`tank[n].face`), and whether it still lives (face 5 if not). */
    val vehicleType: Int = 0,
    val alive: Boolean = true,
)

@Serializable
data class RadarContact(
    val actorId: Long,
    val approximateX: Int,
    val approximateY: Int,
    val kind: RadarContactKind,
    val team: com.tankarena.protocol.Team = com.tankarena.protocol.Team.NEUTRAL,
)

@Serializable
enum class RadarContactKind { TANK, TURRET, PROJECTILE, GOAL }

/**
 * Top-level envelope broadcast to all connected clients each server tick.
 * Pairs a world-authoritative snapshot with per-player views (camera, HUD, radar).
 */
@Serializable
data class ServerFrame(
    val tick: Long,
    val world: WorldSnapshot,
    val playerViews: List<PlayerView> = emptyList(),
)

/**
 * The current pictures of one map tile (legacy `mapm.m[0..2]`) and whether its solid and top
 * structures still stand. A fallen solid structure shows its ruined picture and takes the top
 * layer with it; a fallen top-layer piece shows its ruined picture.
 */
@Serializable
data class TileState(
    val index: Int,
    val base: Int,
    val solid: Int,
    val top: Int,
    val solidStanding: Boolean,
    val topIntact: Boolean,
)
