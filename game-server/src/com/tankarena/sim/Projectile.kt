package com.tankarena.sim

import com.tankarena.core.DetMath
import com.tankarena.protocol.snapshot.ProjectileOwnerKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

internal const val PROJECTILE_TTL_TICKS: Int = 31
private const val PROJECTILE_RADIUS_PX: Float = 1.5f
/** Legacy `b->safety = 4` after a bounce off a force field. */
private const val FIELD_SAFETY_PX: Float = 4f

class Projectile internal constructor(state: State) : Steppable,
    SceneObject<Projectile>, Placed {

    override val body: PointBody = state.body


    val ownerActorId: Long = state.ownerActorId
    val ownerKind: ProjectileOwnerKind = state.ownerKind
    val damage: Int = state.damage
    val weaponKind: Int = state.weaponKind
    /** Velocity in px per tick; a force field turns it. */
    var velocityX: Float = state.velocityX
        private set
    var velocityY: Float = state.velocityY
        private set

    private var exactX: Float = body.position.x
    private var exactY: Float = body.position.y
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
    private val trailRandom = kotlin.random.Random(state.ownerActorId * 31 + state.body.position.x.toLong())

    /** Where a small rocket left a puff of exhaust this tick, if it did. */
    fun drainTrail(): Pair<Int, Int>? = pendingTrail.also { pendingTrail = null }

    /** Where the projectile was before this tick's move. */
    var previousPosition: Pair<Int, Int> = positionX to positionY
        private set

    /**
     * Legacy control_bullets(): a shot that hits a force field bounces off it from ([x], [y]) in
     * [direction], belongs to nobody any more (so it can hit whoever fired it) and flies on 4 px
     * before it can hit anything again.
     */
    internal fun deflect(x: Int, y: Int, direction: Int) {
        val speed = DetMath.hypot(velocityX, velocityY)
        val (fx, fy) = com.tankarena.core.LegacyDirections.unitVector(direction)
        velocityX = fx * speed
        velocityY = fy * speed
        exactX = x.toFloat()
        exactY = y.toFloat()
        positionX = x
        positionY = y
        previousPosition = x to y
        safetyLeft = FIELD_SAFETY_PX
        ownerRef = null
        ownerTurretRef = null
        ownerSoldierRef = null
        syncBody()
    }

    /** Stop at a structure it ran into. */
    internal fun stopAt(x: Int, y: Int) {
        if (isDead) return
        positionX = x
        positionY = y
        markDead(x, y)
    }

    /** Pixels left to fly (legacy `b->dist`). */
    val rangeLeft: Int get() = (ttlTicks * DetMath.hypot(velocityX, velocityY)).toInt()

    private var explosionX: Int = 0
    private var explosionY: Int = 0
    private var explosionPending: Boolean = false
    /** True when the projectile ran out of range instead of hitting something. */
    private var expired: Boolean = false
    private var tankHitRef: Tank? = null
    internal var ownerRef: Tank? = null
    internal var ownerTurretRef: Turret? = null
    /** The soldier who fired this; his comrades from the same vehicle are not hit by it. */
    internal var ownerSoldierRef: Soldier? = null

    override fun update(deltaTimeInMilliseconds: Int) {
        if (isDead) return
        previousPosition = positionX to positionY
        // Velocities are per tick; a step covers STEP_TICKS of them (legacy add_angle()).
        exactX += velocityX * STEP_TICKS
        exactY += velocityY * STEP_TICKS
        positionX = exactX.toInt()
        positionY = exactY.toInt()
        syncBody()
        if (safetyLeft > 0f) {
            safetyLeft -= DetMath.hypot(velocityX, velocityY) * STEP_TICKS
        } else if (weaponKind == SHOT_SMALL_ROCKET) {
            // Legacy control_bullets(): a small rocket trails exhaust every 5..10 ticks.
            trailCooldownTicks = trailCooldownTicks.afterStep()
            if (trailCooldownTicks == 0) {
                pendingTrail = previousPosition
                trailCooldownTicks = 5 + trailRandom.nextInt(5)
            }
        }
        ttlTicks -= STEP_TICKS
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

    /**
     * Legacy line_obj_hit_point(): the shot hit [target] at ([x], [y]); the vehicle takes the
     * damage and is pushed along the shot (tank_add_impulse()).
     */
    internal fun hitVehicle(target: Tank, x: Int, y: Int) {
        if (isDead) return
        target.queueDamage(damage)
        val speed = DetMath.hypot(velocityX, velocityY)
        if (speed > 0f) target.addImpulse(velocityX / speed, velocityY / speed, damage)
        tankHitRef = target
        positionX = x
        positionY = y
        markDead(x, y)
    }

    /** Legacy line_obj_hit_radius(): a flame stops at the first thing it burns. */
    internal fun burn(target: Tank?) {
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

    data class Explosion(val x: Int, val y: Int, val tankHit: Tank?, val expired: Boolean = false)

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
        val offset = ScenePoint(positionX.toFloat(), positionY.toFloat())
        body.position = offset
    }

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: PointBody = PointBody(),
        @SerialName("ownerActorId") val ownerActorId: Long = 0L,
        @SerialName("ownerKind") val ownerKind: ProjectileOwnerKind = ProjectileOwnerKind.TANK,
        @SerialName("damage") val damage: Int = 0,
        @SerialName("velocityX") val velocityX: Float = 0f,
        @SerialName("velocityY") val velocityY: Float = 0f,
        @SerialName("ttlTicks") val ttlTicks: Int = PROJECTILE_TTL_TICKS,
        @SerialName("weaponKind") val weaponKind: Int = WEAPON_MAIN,
        @SerialName("safetyPx") val safetyPx: Int = 0,
    ) : SceneObject.State<Projectile> {
        override fun restore(): Projectile = Projectile(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

