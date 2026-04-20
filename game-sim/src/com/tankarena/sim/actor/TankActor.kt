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
import com.tankarena.core.LegacyDirections
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.sim.ProjectileOwnerKind
import com.tankarena.sim.SimulationEvent
import com.tankarena.sim.TankState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.reflect.KClass

internal const val TANK_HALF: Int = LEGACY_TILE_SIZE / 2 - 2
internal const val PRIMARY_COOLDOWN_TICKS: Int = 8

private const val PROJECTILE_SPEED = 8f
private const val FORWARD_ACCELERATION = 0.45f
private const val REVERSE_ACCELERATION = 0.28f
private const val LONGITUDINAL_FRICTION = 0.10f
private const val LATERAL_FRICTION = 0.18f
private const val MAX_FORWARD_SPEED = 4.75f
private const val MAX_REVERSE_SPEED = 2.25f
private const val TURN_COOLDOWN_TICKS = 2
private const val TURRET_TURN_COOLDOWN_TICKS = 2
private const val COLLISION_DAMPING = 0.35f

internal class TankActor(
    val id: Long,
    val playerIndex: Int,
    val tankType: Int,
    val spawnPoint: Int2,
    initialPosition: Int2,
    initialBodyDirection: Int,
    initialTurretDirection: Int,
    initialVelocityX: Float,
    initialVelocityY: Float,
    initialArmor: Int,
    initialFuel: Int,
    initialWeapon: WeaponType,
    initialLives: Int,
    initialTeam: Int,
) : CollisionDetector {

    var positionX: Int = initialPosition.x
    var positionY: Int = initialPosition.y
    var bodyDirection: Int = LegacyDirections.normalize(initialBodyDirection)
    var turretDirection: Int = LegacyDirections.normalize(initialTurretDirection)
    var velocityX: Float = initialVelocityX
    var velocityY: Float = initialVelocityY
    var armor: Int = initialArmor
    var fuel: Int = initialFuel
    var selectedWeapon: WeaponType = initialWeapon
    var primaryCooldownTicks: Int = 0
    var isAlive: Boolean = true
    var respawnInTicks: Int = 0
    var lives: Int = initialLives
    var team: Int = initialTeam

    private var pendingInput: PlayerIntentFrame = PlayerIntentFrame()
    private var pendingResolveX: Int = 0
    private var pendingResolveY: Int = 0
    private var pendingDamageThisTick: Int = 0
    private var hullTurnCooldownTicks: Int = 0
    private var turretTurnCooldownTicks: Int = 0

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
        pendingInput = input
    }

    fun step(events: MutableList<SimulationEvent>) {
        if (!isAlive) {
            primaryCooldownTicks = 0
            pendingInput = PlayerIntentFrame()
            return
        }

        stepHullTurn()
        stepTurretTurn()
        applyAcceleration()
        applyFriction()
        advancePosition()

        primaryCooldownTicks = (primaryCooldownTicks - 1).coerceAtLeast(0)
        if (pendingInput.firePrimary && primaryCooldownTicks == 0) {
            val barrelFacing = LegacyDirections.toFacing(turretDirection)
            val origin = Int2(
                x = positionX + barrelFacing.x * (TANK_HALF + 2),
                y = positionY + barrelFacing.y * (TANK_HALF + 2),
            )
            val (vx, vy) = LegacyDirections.toVelocityStep(turretDirection, PROJECTILE_SPEED)
            events += SimulationEvent.FireProjectile(
                ownerId = id,
                ownerKind = ProjectileOwnerKind.TANK,
                origin = origin,
                velocity = Int2(vx.roundToInt(), vy.roundToInt()),
                damage = 25,
            )
            primaryCooldownTicks = PRIMARY_COOLDOWN_TICKS
        }

        pendingInput = PlayerIntentFrame()
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
                    resolveAabbVsAabb(
                        otherCx = other.positionX,
                        otherCy = other.positionY,
                        otherHalfW = TANK_HALF,
                        otherHalfH = TANK_HALF,
                        selfFraction = 0.5f,
                    )
                }
                is GoalActor -> Unit
                else -> Unit
            }
        }
    }

    private fun stepHullTurn() {
        hullTurnCooldownTicks = (hullTurnCooldownTicks - 1).coerceAtLeast(0)
        if (hullTurnCooldownTicks > 0) return
        when {
            pendingInput.turnLeft && !pendingInput.turnRight -> {
                bodyDirection = LegacyDirections.stepLeft(bodyDirection)
                hullTurnCooldownTicks = TURN_COOLDOWN_TICKS
            }
            pendingInput.turnRight && !pendingInput.turnLeft -> {
                bodyDirection = LegacyDirections.stepRight(bodyDirection)
                hullTurnCooldownTicks = TURN_COOLDOWN_TICKS
            }
        }
    }

    private fun stepTurretTurn() {
        turretTurnCooldownTicks = (turretTurnCooldownTicks - 1).coerceAtLeast(0)
        if (turretTurnCooldownTicks > 0) return
        when {
            pendingInput.aimLeft && !pendingInput.aimRight -> {
                turretDirection = LegacyDirections.stepLeft(turretDirection)
                turretTurnCooldownTicks = TURRET_TURN_COOLDOWN_TICKS
            }
            pendingInput.aimRight && !pendingInput.aimLeft -> {
                turretDirection = LegacyDirections.stepRight(turretDirection)
                turretTurnCooldownTicks = TURRET_TURN_COOLDOWN_TICKS
            }
        }
    }

    private fun applyAcceleration() {
        if (fuel <= 0) return
        val longitudinalVelocity = forwardSpeed()
        when {
            pendingInput.forward && !pendingInput.reverse -> {
                val (ax, ay) = LegacyDirections.toVelocityStep(bodyDirection, FORWARD_ACCELERATION)
                velocityX += ax
                velocityY += ay
                if (forwardSpeed() > MAX_FORWARD_SPEED) {
                    val scale = MAX_FORWARD_SPEED / forwardSpeed().coerceAtLeast(0.001f)
                    velocityX *= scale
                    velocityY *= scale
                }
                fuel = (fuel - 1).coerceAtLeast(0)
            }
            pendingInput.reverse && !pendingInput.forward -> {
                val (ax, ay) = LegacyDirections.toVelocityStep(bodyDirection, REVERSE_ACCELERATION)
                velocityX -= ax
                velocityY -= ay
                if (longitudinalVelocity < -MAX_REVERSE_SPEED) {
                    val desired = -MAX_REVERSE_SPEED
                    val facing = LegacyDirections.toFacing(bodyDirection)
                    val lateral = lateralSpeed()
                    velocityX = facing.x * desired - facing.y * lateral
                    velocityY = facing.y * desired + facing.x * lateral
                }
                fuel = (fuel - 1).coerceAtLeast(0)
            }
        }
    }

    private fun applyFriction() {
        val (fx, fy) = LegacyDirections.unitVector(bodyDirection)
        val longitudinal = velocityX * fx + velocityY * fy
        val lateral = velocityX * -fy + velocityY * fx

        val adjustedLongitudinal = decay(longitudinal, LONGITUDINAL_FRICTION)
        val adjustedLateral = decay(lateral, LATERAL_FRICTION)

        velocityX = adjustedLongitudinal * fx + adjustedLateral * -fy
        velocityY = adjustedLongitudinal * fy + adjustedLateral * fx
    }

    private fun advancePosition() {
        positionX += velocityX.roundToInt()
        positionY += velocityY.roundToInt()
        syncMaskPosition()
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
        if (overlapX < overlapY) {
            val sign = if (dx >= 0) 1 else -1
            val push = (overlapX * selfFraction).toInt().coerceAtLeast(1) * sign
            pendingResolveX += push
            velocityX *= -COLLISION_DAMPING
        } else {
            val sign = if (dy >= 0) 1 else -1
            val push = (overlapY * selfFraction).toInt().coerceAtLeast(1) * sign
            pendingResolveY += push
            velocityY *= -COLLISION_DAMPING
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

    fun stopMotion() {
        velocityX = 0f
        velocityY = 0f
    }

    fun toState(): TankState = TankState(
        id = id,
        playerIndex = playerIndex,
        tankType = tankType,
        position = Int2(positionX, positionY),
        bodyDirection = bodyDirection,
        turretDirection = turretDirection,
        facing = LegacyDirections.toFacing(bodyDirection),
        turretFacing = LegacyDirections.toFacing(turretDirection),
        velocity = Int2(velocityX.roundToInt(), velocityY.roundToInt()),
        velocityX = velocityX,
        velocityY = velocityY,
        armor = armor,
        fuel = fuel,
        selectedWeapon = selectedWeapon,
        primaryCooldownTicks = primaryCooldownTicks,
        isAlive = isAlive,
        respawnInTicks = respawnInTicks,
        lives = lives,
        team = team,
    )

    private fun forwardSpeed(): Float {
        val (fx, fy) = LegacyDirections.unitVector(bodyDirection)
        return velocityX * fx + velocityY * fy
    }

    private fun lateralSpeed(): Float {
        val (fx, fy) = LegacyDirections.unitVector(bodyDirection)
        return velocityX * -fy + velocityY * fx
    }

    companion object {
        fun fromState(state: TankState, spawnPoint: Int2): TankActor = TankActor(
            id = state.id,
            playerIndex = state.playerIndex,
            tankType = state.tankType,
            spawnPoint = spawnPoint,
            initialPosition = state.position,
            initialBodyDirection = state.bodyDirection,
            initialTurretDirection = state.turretDirection,
            initialVelocityX = state.velocityX,
            initialVelocityY = state.velocityY,
            initialArmor = state.armor,
            initialFuel = state.fuel,
            initialWeapon = state.selectedWeapon,
            initialLives = state.lives,
            initialTeam = state.team,
        ).apply {
            primaryCooldownTicks = state.primaryCooldownTicks
            isAlive = state.isAlive
            respawnInTicks = state.respawnInTicks
        }

        private fun decay(value: Float, amount: Float): Float {
            return when {
                value > 0f -> (value - amount).coerceAtLeast(0f)
                value < 0f -> (value + amount).coerceAtMost(0f)
                else -> 0f
            }
        }
    }
}
