package com.tankarena.sim.actor

import com.pandulapeter.kubriko.actor.body.PointBody
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.CollisionDetector
import com.pandulapeter.kubriko.collision.mask.BoxCollisionMask
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneSize
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.WeaponType
import com.tankarena.core.Int2
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.sim.ProjectileOwnerKind
import com.tankarena.sim.SimulationEvent
import com.tankarena.sim.TankState
import kotlin.math.abs
import kotlin.math.sign
import kotlin.reflect.KClass

internal const val TANK_HALF: Int = LEGACY_TILE_SIZE / 2 - 2
internal const val PRIMARY_COOLDOWN_TICKS: Int = 8
private const val PROJECTILE_SPEED = 8

/**
 * Runtime wrapper around [TankState]. Holds mutable simulation state and a
 * [BoxCollisionMask] sized to the legacy tank hitbox. Mutators are package-private
 * so the only externally callable mutation entry point stays
 * `SimulationHost.tick(...)`.
 */
internal class TankActor(
    val id: Long,
    val playerIndex: Int,
    val tankType: Int,
    val spawnPoint: Int2,
    initialPosition: Int2,
    initialFacing: Int2,
    initialTurretFacing: Int2,
    initialVelocity: Int2,
    initialArmor: Int,
    initialFuel: Int,
    initialWeapon: WeaponType,
    initialLives: Int,
) : CollisionDetector {

    var positionX: Int = initialPosition.x
    var positionY: Int = initialPosition.y
    var facing: Int2 = initialFacing
    var turretFacing: Int2 = initialTurretFacing
    var velocity: Int2 = initialVelocity
    var armor: Int = initialArmor
    var fuel: Int = initialFuel
    var selectedWeapon: WeaponType = initialWeapon
    var primaryCooldownTicks: Int = 0
    var isAlive: Boolean = true
    var respawnInTicks: Int = 0
    var lives: Int = initialLives

    private var pendingFire: Boolean = false
    private var pendingResolveX: Int = 0
    private var pendingResolveY: Int = 0
    private var pendingDamageThisTick: Int = 0

    override val body: PointBody = PointBody(
        initialPosition = sceneOffsetOf(positionX, positionY),
    )

    override val collisionMask: BoxCollisionMask = BoxCollisionMask(
        initialPosition = sceneOffsetOf(positionX, positionY),
        initialSize = SceneSize(
            width = (TANK_HALF * 2).toFloat().sceneUnit,
            height = (TANK_HALF * 2).toFloat().sceneUnit,
        ),
    )

    override val collidableTypes: List<KClass<out Collidable>> = listOf(
        WallActor::class,
        TankActor::class,
        GoalActor::class,
    )

    fun applyIntent(input: PlayerIntentFrame) {
        if (!isAlive) return

        val bodyFacing = if (input.steer != 0 || input.throttle != 0) {
            Int2(input.steer.sign, input.throttle.sign)
        } else {
            facing
        }

        val newTurretFacing = if (input.aimX != 0 || input.aimY != 0) {
            Int2(input.aimX.sign, input.aimY.sign)
        } else {
            turretFacing
        }

        velocity = Int2(
            x = input.steer.coerceIn(-1, 1) * 3,
            y = input.throttle.coerceIn(-1, 1) * 4,
        )
        facing = bodyFacing
        turretFacing = newTurretFacing
        pendingFire = input.firePrimary
    }

    /**
     * Tank movement step. Advances position by [velocity] and decrements the
     * primary cooldown. If the tank wants to fire and is ready, emits a
     * `FireProjectile` event into [events].
     */
    fun step(events: MutableList<SimulationEvent>) {
        if (!isAlive) {
            primaryCooldownTicks = 0
            pendingFire = false
            return
        }
        positionX += velocity.x
        positionY += velocity.y
        syncMaskPosition()

        primaryCooldownTicks = (primaryCooldownTicks - 1).coerceAtLeast(0)
        if (pendingFire && primaryCooldownTicks == 0) {
            val origin = Int2(
                x = positionX + turretFacing.x * (TANK_HALF + 2),
                y = positionY + turretFacing.y * (TANK_HALF + 2),
            )
            val (vx, vy) = projectileVelocity(turretFacing)
            events += SimulationEvent.FireProjectile(
                ownerId = id,
                ownerKind = ProjectileOwnerKind.TANK,
                origin = origin,
                velocity = Int2(vx, vy),
                damage = 25,
            )
            primaryCooldownTicks = PRIMARY_COOLDOWN_TICKS
        }
        pendingFire = false
    }

    override fun onCollisionDetected(collidables: List<Collidable>) {
        if (!isAlive) return
        for (other in collidables) {
            when (other) {
                is WallActor -> resolveAabbVsAabb(
                    otherCx = other.centerX,
                    otherCy = other.centerY,
                    otherHalfW = other.halfWidth,
                    otherHalfH = other.halfHeight,
                    selfFraction = 1f,
                )
                is TankActor -> if (other !== this && other.isAlive) {
                    // Each tank handles its own resolution; halve the push so the
                    // pair separates by the full penetration cooperatively.
                    resolveAabbVsAabb(
                        otherCx = other.positionX,
                        otherCy = other.positionY,
                        otherHalfW = TANK_HALF,
                        otherHalfH = TANK_HALF,
                        selfFraction = 0.5f,
                    )
                }
                is GoalActor -> Unit // Goal collection is resolved in the post-collision pass.
                else -> Unit
            }
        }
    }

    private fun resolveAabbVsAabb(
        otherCx: Int,
        otherCy: Int,
        otherHalfW: Int,
        otherHalfH: Int,
        selfFraction: Float,
    ) {
        val dx = positionX - otherCx
        val dy = positionY - otherCy
        val combinedHalfW = TANK_HALF + otherHalfW
        val combinedHalfH = TANK_HALF + otherHalfH
        val overlapX = combinedHalfW - abs(dx)
        val overlapY = combinedHalfH - abs(dy)
        if (overlapX <= 0 || overlapY <= 0) return
        // Push back along the axis of least penetration.
        if (overlapX < overlapY) {
            val sign = if (dx >= 0) 1 else -1
            val push = (overlapX * selfFraction).toInt().coerceAtLeast(1) * sign
            pendingResolveX += push
        } else {
            val sign = if (dy >= 0) 1 else -1
            val push = (overlapY * selfFraction).toInt().coerceAtLeast(1) * sign
            pendingResolveY += push
        }
    }

    fun applyResolutions(worldWidthPixels: Int, worldHeightPixels: Int) {
        if (pendingResolveX == 0 && pendingResolveY == 0 && pendingDamageThisTick == 0) return
        positionX = (positionX + pendingResolveX).coerceIn(TANK_HALF, worldWidthPixels - TANK_HALF - 1)
        positionY = (positionY + pendingResolveY).coerceIn(TANK_HALF, worldHeightPixels - TANK_HALF - 1)
        if (pendingDamageThisTick > 0) {
            armor = (armor - pendingDamageThisTick).coerceAtLeast(0)
        }
        pendingResolveX = 0
        pendingResolveY = 0
        pendingDamageThisTick = 0
        syncMaskPosition()
    }

    /**
     * Called by `ProjectileActor.onCollisionDetected` when a hostile projectile
     * connects with this tank. Damage is queued and applied during
     * [applyResolutions] so all per-tick state mutations happen at one point.
     */
    fun queueDamage(amount: Int) {
        if (amount <= 0) return
        pendingDamageThisTick += amount
    }

    fun syncMaskPosition() {
        val offset = sceneOffsetOf(positionX, positionY)
        body.position = offset
        collisionMask.position = offset
    }

    fun position(): Int2 = Int2(positionX, positionY)

    fun teleportTo(point: Int2) {
        positionX = point.x
        positionY = point.y
        syncMaskPosition()
    }

    fun toState(): TankState = TankState(
        id = id,
        playerIndex = playerIndex,
        tankType = tankType,
        position = Int2(positionX, positionY),
        facing = facing,
        turretFacing = turretFacing,
        velocity = velocity,
        armor = armor,
        fuel = fuel,
        selectedWeapon = selectedWeapon,
        primaryCooldownTicks = primaryCooldownTicks,
        isAlive = isAlive,
        respawnInTicks = respawnInTicks,
        lives = lives,
    )

    private fun projectileVelocity(direction: Int2): Pair<Int, Int> =
        direction.x.sign * PROJECTILE_SPEED to direction.y.sign * PROJECTILE_SPEED

    companion object {
        fun fromState(state: TankState, spawnPoint: Int2): TankActor = TankActor(
            id = state.id,
            playerIndex = state.playerIndex,
            tankType = state.tankType,
            spawnPoint = spawnPoint,
            initialPosition = state.position,
            initialFacing = state.facing,
            initialTurretFacing = state.turretFacing,
            initialVelocity = state.velocity,
            initialArmor = state.armor,
            initialFuel = state.fuel,
            initialWeapon = state.selectedWeapon,
            initialLives = state.lives,
        ).apply {
            primaryCooldownTicks = state.primaryCooldownTicks
            isAlive = state.isAlive
            respawnInTicks = state.respawnInTicks
        }
    }
}
