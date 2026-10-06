package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.actor.body.PointBody
import com.pandulapeter.kubriko.actor.traits.Dynamic
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.CollisionDetector
import com.pandulapeter.kubriko.collision.mask.CircleCollisionMask
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.serialization.Serializable
import com.pandulapeter.kubriko.serialization.typeSerializers.SerializablePointBody
import com.pandulapeter.kubriko.types.SceneOffset
import com.tankarena.protocol.snapshot.ProjectileOwnerKind
import kotlin.reflect.KClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

internal const val PROJECTILE_TTL_TICKS: Int = 31
private const val PROJECTILE_RADIUS_PX: Float = 1.5f

class ServerProjectileActor internal constructor(state: State) :
    CollisionDetector,
    Dynamic,
    Serializable<ServerProjectileActor> {

    override val body: PointBody = state.body
    override val collisionMask: CircleCollisionMask = CircleCollisionMask(
        initialPosition = body.position,
        initialRadius = PROJECTILE_RADIUS_PX.sceneUnit,
    )
    override val isAlwaysActive: Boolean = true

    override val collidableTypes: List<KClass<out Collidable>> = listOf(
        // Structures are hit by their pixels (ServerMatchPrototype.checkProjectileStructureHits());
        // turrets cannot be hit at all, only the building under them.
        ServerTankActor::class,
    )

    val ownerActorId: Long = state.ownerActorId
    val ownerKind: ProjectileOwnerKind = state.ownerKind
    val damage: Int = state.damage
    val weaponKind: Int = state.weaponKind
    /** Velocity in px per tick. */
    val velocityX: Float = state.velocityX
    val velocityY: Float = state.velocityY

    private var exactX: Float = body.position.x.raw
    private var exactY: Float = body.position.y.raw
    var positionX: Int = exactX.toInt()
        private set
    var positionY: Int = exactY.toInt()
        private set
    var ttlTicks: Int = state.ttlTicks
        private set
    var isDead: Boolean = false
        private set

    /** Legacy `b->safety`: pixels still to fly before the shot can hit anything. */
    private var safetyLeft: Float = state.safetyPx.toFloat()
    /** False while a small rocket is still within its safety distance. */
    val armed: Boolean get() = safetyLeft <= 0f

    private var trailCooldownTicks: Int = 0
    private var pendingTrail: Pair<Int, Int>? = null
    private val trailRandom = kotlin.random.Random(state.ownerActorId * 31 + state.body.position.x.raw.toLong())

    /** Where a small rocket left a puff of exhaust this tick, if it did. */
    fun drainTrail(): Pair<Int, Int>? = pendingTrail.also { pendingTrail = null }

    /** Where the projectile was before this tick's move. */
    var previousPosition: Pair<Int, Int> = positionX to positionY
        private set

    /** Stop at a structure it ran into. */
    internal fun stopAt(x: Int, y: Int) {
        if (isDead) return
        positionX = x
        positionY = y
        markDead(x, y)
    }

    /** Pixels left to fly (legacy `b->dist`). */
    val rangeLeft: Int get() = (ttlTicks * kotlin.math.hypot(velocityX, velocityY)).toInt()

    private var explosionX: Int = 0
    private var explosionY: Int = 0
    private var explosionPending: Boolean = false
    /** True when the projectile ran out of range instead of hitting something. */
    private var expired: Boolean = false
    private var tankHitRef: ServerTankActor? = null
    internal var ownerRef: ServerTankActor? = null
    internal var ownerTurretRef: ServerTurretActor? = null
    /** The soldier who fired this; his comrades from the same vehicle are not hit by it. */
    internal var ownerSoldierRef: Soldier? = null

    override fun update(deltaTimeInMilliseconds: Int) {
        if (isDead) return
        previousPosition = positionX to positionY
        exactX += velocityX
        exactY += velocityY
        positionX = exactX.toInt()
        positionY = exactY.toInt()
        syncBody()
        if (safetyLeft > 0f) {
            safetyLeft -= kotlin.math.hypot(velocityX, velocityY)
        } else if (weaponKind == SHOT_SMALL_ROCKET) {
            // Legacy control_bullets(): a small rocket trails exhaust every 5..10 ticks.
            if (trailCooldownTicks > 0) trailCooldownTicks -= 1
            if (trailCooldownTicks == 0) {
                pendingTrail = previousPosition
                trailCooldownTicks = 5 + trailRandom.nextInt(5)
            }
        }
        ttlTicks -= 1
        if (ttlTicks <= 0) {
            markDead(positionX, positionY)
            expired = true
        }
    }

    fun markOutOfBoundsIfNeeded(worldWidthPixels: Int, worldHeightPixels: Int) {
        if (isDead) return
        if (positionX < 0 || positionY < 0 ||
            positionX >= worldWidthPixels || positionY >= worldHeightPixels
        ) {
            markDead(
                positionX.coerceIn(0, worldWidthPixels - 1),
                positionY.coerceIn(0, worldHeightPixels - 1),
            )
        }
    }

    override fun onCollisionDetected(collidables: List<Collidable>) {
        if (isDead) return
        for (other in collidables) {
            if (isDead) return
            when (other) {
                is ServerTankActor -> {
                    // Flames hit by proximity (ServerMatchPrototype.triggerFlameContacts()).
                    if (weaponKind == WEAPON_FLAME || !armed) continue
                    if (ownerKind == ProjectileOwnerKind.TANK && other === ownerRef) continue
                    // Legacy line_obj_hit_point(): a vehicle sinking into a pit is out of reach.
                    if (other.armor <= 0 || other.height < 1) continue
                    other.queueDamage(damage)
                    // Legacy tank_add_impulse(): the hit pushes the vehicle along the shot.
                    val speed = kotlin.math.hypot(velocityX, velocityY)
                    if (speed > 0f) other.addImpulse(velocityX / speed, velocityY / speed, damage)
                    tankHitRef = other
                    markDead(positionX, positionY)
                }

                else -> Unit
            }
        }
    }

    /** Legacy line_obj_hit_radius(): a flame stops at the first thing it burns. */
    internal fun burn(target: ServerTankActor?) {
        if (isDead) return
        tankHitRef = target
        markDead(positionX, positionY)
    }

    fun drainExplosion(): Explosion? {
        if (!explosionPending) return null
        explosionPending = false
        val hit = tankHitRef
        tankHitRef = null
        return Explosion(explosionX, explosionY, hit, expired)
    }

    data class Explosion(val x: Int, val y: Int, val tankHit: ServerTankActor?, val expired: Boolean = false)

    override fun save(): State = State(
        body = body,
        ownerActorId = ownerActorId,
        ownerKind = ownerKind,
        damage = damage,
        velocityX = velocityX,
        velocityY = velocityY,
        ttlTicks = ttlTicks,
        weaponKind = weaponKind,
        safetyPx = safetyLeft.toInt().coerceAtLeast(0),
    )

    private fun markDead(atX: Int, atY: Int) {
        if (isDead) return
        isDead = true
        explosionX = atX
        explosionY = atY
        explosionPending = true
    }

    private fun syncBody() {
        val offset = SceneOffset(positionX.toFloat().sceneUnit, positionY.toFloat().sceneUnit)
        body.position = offset
        collisionMask.position = offset
    }

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: SerializablePointBody = PointBody(),
        @SerialName("ownerActorId") val ownerActorId: Long = 0L,
        @SerialName("ownerKind") val ownerKind: ProjectileOwnerKind = ProjectileOwnerKind.TANK,
        @SerialName("damage") val damage: Int = 0,
        @SerialName("velocityX") val velocityX: Float = 0f,
        @SerialName("velocityY") val velocityY: Float = 0f,
        @SerialName("ttlTicks") val ttlTicks: Int = PROJECTILE_TTL_TICKS,
        @SerialName("weaponKind") val weaponKind: Int = WEAPON_MAIN,
        @SerialName("safetyPx") val safetyPx: Int = 0,
    ) : Serializable.State<ServerProjectileActor> {
        override fun restore(): ServerProjectileActor = ServerProjectileActor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

