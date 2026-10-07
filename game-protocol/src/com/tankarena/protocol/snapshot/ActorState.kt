package com.tankarena.protocol.snapshot

import com.tankarena.protocol.Team
import kotlinx.serialization.Serializable

/**
 * Per-actor wire state. Each concrete subtype doubles as the Kubriko `State` produced by
 * the matching server actor's `save()` and consumed by the client actor's `sync()`.
 */
@Serializable
sealed interface ActorState {
    val actorId: Long
}

@Serializable
data class TankState(
    override val actorId: Long,
    val team: Team = Team.NEUTRAL,
    val tankType: Int = 0,
    val x: Int,
    val y: Int,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val bodyDirection: Int = 0,
    val turretDirection: Int = 0,
    val armor: Int = 0,
    val alive: Boolean = true,
    val invulnerable: Boolean = false,
    val controlled: Boolean = false,
    val primaryCooldownTicks: Int = 0,
    val currentWeapon: Int = 0,
    /** Legacy `o->h`: 1 on the ground, 0 down to -4 while sinking into a pit (and lying at its bottom). */
    val height: Int = 1,
    /** Legacy ARMOR overlay: the shield key is held, or the start shield is blinking on. */
    val shielded: Boolean = false,
    /** Legacy CHOPPER: its rotor (the turret frames) casts a shadow too. */
    val chopper: Boolean = false,
    /** Legacy `invis.count != 0`: other players do not see this vehicle at all. */
    val cloaked: Boolean = false,
    /** Legacy `invis.flash`: the driver of a cloaked vehicle sees it blink. */
    val cloakFlash: Boolean = true,
) : ActorState

@Serializable
data class TurretState(
    override val actorId: Long,
    val team: Team = Team.NEUTRAL,
    val turretType: Int = 0,
    val x: Int,
    val y: Int,
    val turretDirection: Int = 0,
    val alive: Boolean = true,
    val primaryCooldownTicks: Int = 0,
) : ActorState

@Serializable
data class ProjectileState(
    override val actorId: Long,
    val ownerId: Long,
    val x: Int,
    val y: Int,
    val vx: Int = 0,
    val vy: Int = 0,
    val ownerKind: ProjectileOwnerKind = ProjectileOwnerKind.TANK,
    val kind: ProjectileKind = ProjectileKind.BULLET,
    /** Pixels left to fly (legacy `b->dist`); flames and mortar shells are drawn by it. */
    val rangeLeft: Int = 0,
    /** Flight direction (16 steps) of the shells that show it (small rockets). */
    val direction: Int = 0,
    /** Height a plane's bomb was dropped from (legacy `b->h`), for its shadow. */
    val dropHeight: Int = 0,
) : ActorState

/** A bonus lying on the map (legacy BONUS_TYPE, src/objects/bonus.c), drawn as `ICON(bonusType + 1)`. */
@Serializable
data class BonusState(
    override val actorId: Long,
    val x: Int,
    val y: Int,
    val bonusType: Int,
) : ActorState

/** A soldier (legacy MAN_TYPE, src/objects/man.c), drawn as `MAN-(frame + 1)` turned to [direction]. */
@Serializable
data class ManState(
    override val actorId: Long,
    val x: Int,
    val y: Int,
    val direction: Int = 0,
    val frame: Int = 0,
) : ActorState

/**
 * An A-bomb (legacy ABOMB_TYPE, src/objects/abomb.c). While it counts down it shows
 * `ABOMB(4 - countdown)`; once it goes off nothing is drawn but the blast and the white flash.
 */
@Serializable
data class AbombState(
    override val actorId: Long,
    val x: Int,
    val y: Int,
    val countdown: Int,
    val exploding: Boolean = false,
    /** Legacy: an A-bomb lying in water is not drawn. */
    val hidden: Boolean = false,
) : ActorState

@Serializable
data class MineState(
    override val actorId: Long,
    val x: Int,
    val y: Int,
    val armed: Boolean = false,
) : ActorState

@Serializable
data class GoalState(
    override val actorId: Long,
    val team: Team = Team.NEUTRAL,
    val x: Int,
    val y: Int,
    val captured: Boolean = false,
) : ActorState

@Serializable
data class WallState(
    override val actorId: Long,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) : ActorState

@Serializable
data class FlagState(
    override val actorId: Long,
    val flagType: Int = 0,
    val number: Int = 0,
    val x: Int,
    val y: Int,
    val isCarried: Boolean = false,
) : ActorState

@Serializable
data class ProductState(
    override val actorId: Long,
    val productType: Int = 0,
    val price: Int = 1,
    val x: Int,
    val y: Int,
    val isCollected: Boolean = false,
) : ActorState

@Serializable
data class LockState(
    override val actorId: Long,
    val activation: Int = 0,
    val target: Int = 0,
    val x: Int,
    val y: Int,
    val isFired: Boolean = false,
) : ActorState

@Serializable
data class WarpState(
    override val actorId: Long,
    val targetX: Int = 0,
    val targetY: Int = 0,
    val x: Int,
    val y: Int,
    val cooldownTicks: Int = 0,
) : ActorState

@Serializable
data class DestroyerState(
    override val actorId: Long,
    val radius: Int = 0,
    val what: Int = 3,
    val immediate: Boolean = false,
    val x: Int,
    val y: Int,
    val isFired: Boolean = false,
) : ActorState

@Serializable
data class EnforcerState(
    override val actorId: Long,
    val radius: Int = 0,
    val weapon: Int = 0,
    val delay: Int = 0,
    val good: Boolean = false,
    val bad: Boolean = false,
    val x: Int,
    val y: Int,
) : ActorState

@Serializable
data class TrainState(
    override val actorId: Long,
    val x: Int,
    val y: Int,
    val isEngine: Boolean = true,
    val armor: Int = 60,
    val alive: Boolean = true,
    /** Legacy `info.picture`: WAGO0 for the engine, WAGO1..8 for the wagons. */
    val picture: Int = 0,
    /** Legacy `motion.dir` (16 steps). */
    val direction: Int = 0,
) : ActorState

@Serializable
data class ZeppelinState(
    override val actorId: Long,
    val x: Int,
    val y: Int,
    val alive: Boolean = true,
    /** Legacy `z.img`: ZEPxx (0) or ZEP2xx (1). */
    val frame: Int = 0,
) : ActorState

@Serializable
data class B52State(
    override val actorId: Long,
    val x: Int,
    val y: Int,
    val armor: Int = 30,
    val alive: Boolean = true,
    val bombCount: Int = 0,
    /** Legacy `b52.dir`: 0 north, 1 east, 2 south, 3 west. */
    val heading: Int = 0,
    /** Legacy `o->h`: shadow offset, and drawn above the top layer from 4 up. */
    val height: Int = 1,
    /** Legacy `b52.bomb`: bay frames 0..3, 4 shut. */
    val bombBay: Int = 4,
    val wreck: Boolean = false,
    /** Legacy write_b52_wreck(): a wreck in water is not drawn. */
    val onWater: Boolean = false,
) : ActorState

@Serializable
enum class ProjectileOwnerKind { TANK, TURRET, MAN }

@Serializable
enum class ProjectileKind {
    BULLET, CHAIN, ROCKET, MORTAR, FLAME,
    /** Legacy HT_SROCK: the small rocket that aircraft and launchers fire as their main gun. */
    SROCK,
    /** Legacy HT_PLANEBOMB: a plane's mortar, falling from [ProjectileState.dropHeight]. */
    PLANEBOMB,
}
