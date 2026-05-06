package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.actor.traits.Dynamic
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.CollisionDetector
import com.pandulapeter.kubriko.collision.mask.BoxCollisionMask
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.sceneEditor.Editable
import com.pandulapeter.kubriko.serialization.Serializable
import com.pandulapeter.kubriko.serialization.typeSerializers.SerializableBoxBody
import com.pandulapeter.kubriko.types.SceneOffset
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.core.LegacyDirections
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.sim.kubriko.Resolvable
import kotlin.math.abs
import kotlin.reflect.KClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

private const val FORWARD_ACCELERATION: Float = 0.45f
private const val REVERSE_ACCELERATION: Float = 0.28f
private const val LONGITUDINAL_FRICTION: Float = 0.10f
private const val LATERAL_FRICTION: Float = 0.18f
private const val MAX_FORWARD_SPEED: Float = 4.75f
private const val MAX_REVERSE_SPEED: Float = 2.25f
private const val TURN_COOLDOWN_TICKS: Int = 7
private const val TURRET_TURN_COOLDOWN_TICKS: Int = 7
private const val COLLISION_DAMPING: Float = 0.35f
private const val PRIMARY_COOLDOWN_TICKS: Int = 70
private const val PROJECTILE_SPEED: Float = 8f
private const val PRIMARY_DAMAGE: Int = 25
internal const val RESPAWN_DELAY_TICKS: Int = 300

internal const val SERVER_TANK_FOOTPRINT: Int = LEGACY_TILE_SIZE - 4
internal const val SERVER_TANK_HALF: Int = SERVER_TANK_FOOTPRINT / 2

class ServerTankActor(state: State) :
    CollisionDetector,
    Dynamic,
    Resolvable,
    Editable<ServerTankActor> {

    override val body: BoxBody = state.body
    override val collisionMask: BoxCollisionMask = BoxCollisionMask(
        initialSize = body.size,
        initialPosition = body.position,
        initialRotation = body.rotation,
    )
    override val isAlwaysActive: Boolean = true

    override val collidableTypes: List<KClass<out Collidable>> = listOf(
        ServerWallActor::class,
        ServerTankActor::class,
        ServerGoalActor::class,
    )

    var bodyDirection: Int = state.bodyDirection
        private set
    var turretDirection: Int = state.turretDirection
        private set
    val playerIndex: Int = state.playerIndex
    val tankType: Int = state.tankType
    var armor: Int = state.armor
    var fuel: Int = state.fuel
    var lives: Int = state.lives
        private set
    val team: Int = state.team
    var primaryCooldownTicks: Int = state.primaryCooldownTicks
        private set
    var respawnInTicks: Int = state.respawnInTicks
        private set

    private val maxArmor: Int = if (state.maxArmor > 0) state.maxArmor else state.armor.coerceAtLeast(1)
    private val maxFuel: Int = if (state.maxFuel > 0) state.maxFuel else state.fuel.coerceAtLeast(0)
    private val spawnX: Int = if (state.spawnX >= 0) state.spawnX
        else (body.position.x.raw + body.size.width.raw / 2f).toInt()
    private val spawnY: Int = if (state.spawnY >= 0) state.spawnY
        else (body.position.y.raw + body.size.height.raw / 2f).toInt()
    private val spawnBodyDirection: Int = state.spawnBodyDirection.takeIf { it >= 0 } ?: state.bodyDirection
    private val spawnTurretDirection: Int = state.spawnTurretDirection.takeIf { it >= 0 } ?: state.turretDirection

    var positionX: Int = (body.position.x.raw + body.size.width.raw / 2f).toInt()
        private set
    var positionY: Int = (body.position.y.raw + body.size.height.raw / 2f).toInt()
        private set
    var velocityX: Float = state.velocityX
        private set
    var velocityY: Float = state.velocityY
        private set

    private var pendingIntent: PlayerIntentFrame = PlayerIntentFrame()
    private var hullTurnCooldownTicks: Int = 0
    private var turretTurnCooldownTicks: Int = 0
    private var pendingResolveX: Int = 0
    private var pendingResolveY: Int = 0
    private var pendingDamageThisTick: Int = 0
    private var pendingFireRequest: FireRequest? = null
    private var destroyedThisTick: Boolean = false
    private var spawnedThisTick: Boolean = false
    private var damageEventAmount: Int = 0

    data class FireRequest(
        val originX: Int,
        val originY: Int,
        val velocityX: Int,
        val velocityY: Int,
        val damage: Int,
    )

    fun applyIntent(intent: PlayerIntentFrame) {
        pendingIntent = intent
    }

    fun queueDamage(amount: Int) {
        if (amount <= 0) return
        pendingDamageThisTick += amount
    }

    override fun update(deltaTimeInMilliseconds: Int) {
        if (armor <= 0) {
            velocityX = 0f
            velocityY = 0f
            if (respawnInTicks > 0) {
                respawnInTicks -= 1
                if (respawnInTicks == 0) respawn()
            }
            pendingIntent = PlayerIntentFrame()
            return
        }
        stepHullTurn()
        stepTurretTurn()
        applyAcceleration()
        applyFriction()
        advancePosition()
        stepFire()
        pendingIntent = PlayerIntentFrame()
    }

    private fun respawn() {
        positionX = spawnX
        positionY = spawnY
        bodyDirection = spawnBodyDirection
        turretDirection = spawnTurretDirection
        armor = maxArmor
        fuel = maxFuel
        velocityX = 0f
        velocityY = 0f
        primaryCooldownTicks = 0
        respawnInTicks = 0
        spawnedThisTick = true
        syncBodyFromPosition()
    }

    fun drainLifecycleEvents(actorId: Long): List<com.tankarena.protocol.snapshot.GameEvent> {
        if (!destroyedThisTick && !spawnedThisTick && damageEventAmount == 0) return emptyList()
        val events = mutableListOf<com.tankarena.protocol.snapshot.GameEvent>()
        if (damageEventAmount > 0) {
            events += com.tankarena.protocol.snapshot.GameEvent.DamageTaken(actorId, damageEventAmount)
            damageEventAmount = 0
        }
        if (destroyedThisTick) {
            events += com.tankarena.protocol.snapshot.GameEvent.TankDestroyed(actorId)
            destroyedThisTick = false
        }
        if (spawnedThisTick) {
            events += com.tankarena.protocol.snapshot.GameEvent.TankSpawned(actorId)
            spawnedThisTick = false
        }
        return events
    }

    fun drainFireRequest(): FireRequest? {
        val request = pendingFireRequest
        pendingFireRequest = null
        return request
    }

    override fun onCollisionDetected(collidables: List<Collidable>) {
        for (other in collidables) {
            when (other) {
                is ServerWallActor -> {
                    val position = other.body.position
                    val size = other.body.size
                    val halfW = (size.width.raw / 2f).toInt()
                    val halfH = (size.height.raw / 2f).toInt()
                    val cx = (position.x.raw + size.width.raw / 2f).toInt()
                    val cy = (position.y.raw + size.height.raw / 2f).toInt()
                    resolveAabbVsAabb(cx, cy, halfW, halfH, selfFraction = 1f)
                }

                is ServerTankActor -> if (other !== this) {
                    resolveAabbVsAabb(
                        otherCx = other.positionX,
                        otherCy = other.positionY,
                        otherHalfW = SERVER_TANK_HALF,
                        otherHalfH = SERVER_TANK_HALF,
                        selfFraction = 0.5f,
                    )
                }

                is ServerGoalActor -> Unit
                else -> Unit
            }
        }
    }

    override fun applyPendingResolutions(worldWidthPixels: Int, worldHeightPixels: Int) {
        if (pendingResolveX == 0 && pendingResolveY == 0 && pendingDamageThisTick == 0) return
        positionX = (positionX + pendingResolveX)
            .coerceIn(SERVER_TANK_HALF, worldWidthPixels - SERVER_TANK_HALF - 1)
        positionY = (positionY + pendingResolveY)
            .coerceIn(SERVER_TANK_HALF, worldHeightPixels - SERVER_TANK_HALF - 1)
        if (pendingDamageThisTick > 0) {
            val wasAlive = armor > 0
            val dealt = pendingDamageThisTick.coerceAtMost(armor)
            armor = (armor - pendingDamageThisTick).coerceAtLeast(0)
            if (wasAlive) {
                if (armor == 0) {
                    destroyedThisTick = true
                    lives = (lives - 1).coerceAtLeast(0)
                    if (lives > 0) respawnInTicks = RESPAWN_DELAY_TICKS
                    velocityX = 0f
                    velocityY = 0f
                } else if (dealt > 0) {
                    damageEventAmount += dealt
                }
            }
        }
        pendingResolveX = 0
        pendingResolveY = 0
        pendingDamageThisTick = 0
        syncBodyFromPosition()
    }

    override fun save(): State = State(
        body = body,
        bodyDirection = bodyDirection,
        turretDirection = turretDirection,
        playerIndex = playerIndex,
        tankType = tankType,
        armor = armor,
        fuel = fuel,
        lives = lives,
        team = team,
        velocityX = velocityX,
        velocityY = velocityY,
        primaryCooldownTicks = primaryCooldownTicks,
        respawnInTicks = respawnInTicks,
        maxArmor = maxArmor,
        maxFuel = maxFuel,
        spawnX = spawnX,
        spawnY = spawnY,
        spawnBodyDirection = spawnBodyDirection,
        spawnTurretDirection = spawnTurretDirection,
    )

    private fun stepHullTurn() {
        hullTurnCooldownTicks = (hullTurnCooldownTicks - 1).coerceAtLeast(0)
        if (hullTurnCooldownTicks > 0) return
        when {
            pendingIntent.turnLeft && !pendingIntent.turnRight -> {
                bodyDirection = LegacyDirections.stepLeft(bodyDirection)
                hullTurnCooldownTicks = TURN_COOLDOWN_TICKS
            }

            pendingIntent.turnRight && !pendingIntent.turnLeft -> {
                bodyDirection = LegacyDirections.stepRight(bodyDirection)
                hullTurnCooldownTicks = TURN_COOLDOWN_TICKS
            }
        }
    }

    private fun stepTurretTurn() {
        turretTurnCooldownTicks = (turretTurnCooldownTicks - 1).coerceAtLeast(0)
        if (turretTurnCooldownTicks > 0) return
        when {
            pendingIntent.aimLeft && !pendingIntent.aimRight -> {
                turretDirection = LegacyDirections.stepLeft(turretDirection)
                turretTurnCooldownTicks = TURRET_TURN_COOLDOWN_TICKS
            }

            pendingIntent.aimRight && !pendingIntent.aimLeft -> {
                turretDirection = LegacyDirections.stepRight(turretDirection)
                turretTurnCooldownTicks = TURRET_TURN_COOLDOWN_TICKS
            }
        }
    }

    private fun applyAcceleration() {
        if (fuel <= 0) return
        val longitudinalVelocity = forwardSpeed()
        when {
            pendingIntent.forward && !pendingIntent.reverse -> {
                val (ax, ay) = LegacyDirections.toVelocityStep(bodyDirection, FORWARD_ACCELERATION)
                velocityX += ax
                velocityY += ay
                val speed = forwardSpeed()
                if (speed > MAX_FORWARD_SPEED) {
                    val scale = MAX_FORWARD_SPEED / speed.coerceAtLeast(0.001f)
                    velocityX *= scale
                    velocityY *= scale
                }
                fuel = (fuel - 1).coerceAtLeast(0)
            }

            pendingIntent.reverse && !pendingIntent.forward -> {
                val (ax, ay) = LegacyDirections.toVelocityStep(bodyDirection, REVERSE_ACCELERATION)
                velocityX -= ax
                velocityY -= ay
                if (longitudinalVelocity < -MAX_REVERSE_SPEED) {
                    val (fx, fy) = LegacyDirections.unitVector(bodyDirection)
                    val lateral = lateralSpeed()
                    val desired = -MAX_REVERSE_SPEED
                    velocityX = fx * desired - fy * lateral
                    velocityY = fy * desired + fx * lateral
                }
                fuel = (fuel - 1).coerceAtLeast(0)
            }
        }
    }

    private fun applyFriction() {
        val (fx, fy) = LegacyDirections.unitVector(bodyDirection)
        val longitudinal = velocityX * fx + velocityY * fy
        val lateral = velocityX * -fy + velocityY * fx

        val decayedLongitudinal = decay(longitudinal, LONGITUDINAL_FRICTION)
        val decayedLateral = decay(lateral, LATERAL_FRICTION)

        velocityX = decayedLongitudinal * fx + decayedLateral * -fy
        velocityY = decayedLongitudinal * fy + decayedLateral * fx
    }

    private fun advancePosition() {
        positionX += velocityX.toInt()
        positionY += velocityY.toInt()
        syncBodyFromPosition()
    }

    private fun stepFire() {
        primaryCooldownTicks = (primaryCooldownTicks - 1).coerceAtLeast(0)
        if (!pendingIntent.firePrimary || primaryCooldownTicks > 0 || armor <= 0) return
        val (fx, fy) = LegacyDirections.unitVector(turretDirection)
        val (vx, vy) = LegacyDirections.toVelocityStep(turretDirection, PROJECTILE_SPEED)
        val barrelOffset = SERVER_TANK_HALF + 2
        pendingFireRequest = FireRequest(
            originX = positionX + (fx * barrelOffset).toInt(),
            originY = positionY + (fy * barrelOffset).toInt(),
            velocityX = vx.toInt().let { if (it == 0 && vx != 0f) (if (vx > 0) 1 else -1) else it },
            velocityY = vy.toInt().let { if (it == 0 && vy != 0f) (if (vy > 0) 1 else -1) else it },
            damage = PRIMARY_DAMAGE,
        )
        primaryCooldownTicks = PRIMARY_COOLDOWN_TICKS
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
        val combinedHalfW = SERVER_TANK_HALF + otherHalfW
        val combinedHalfH = SERVER_TANK_HALF + otherHalfH
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

    private fun syncBodyFromPosition() {
        val topLeft = SceneOffset(
            (positionX - SERVER_TANK_HALF).toFloat().sceneUnit,
            (positionY - SERVER_TANK_HALF).toFloat().sceneUnit,
        )
        body.position = topLeft
        collisionMask.position = topLeft
    }

    private fun forwardSpeed(): Float {
        val (fx, fy) = LegacyDirections.unitVector(bodyDirection)
        return velocityX * fx + velocityY * fy
    }

    private fun lateralSpeed(): Float {
        val (fx, fy) = LegacyDirections.unitVector(bodyDirection)
        return velocityX * -fy + velocityY * fx
    }

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: SerializableBoxBody = BoxBody(),
        @SerialName("bodyDirection") val bodyDirection: Int = 0,
        @SerialName("turretDirection") val turretDirection: Int = 0,
        @SerialName("playerIndex") val playerIndex: Int = -1,
        @SerialName("tankType") val tankType: Int = 0,
        @SerialName("armor") val armor: Int = 100,
        @SerialName("fuel") val fuel: Int = 100,
        @SerialName("lives") val lives: Int = 1,
        @SerialName("team") val team: Int = 0,
        @SerialName("velocityX") val velocityX: Float = 0f,
        @SerialName("velocityY") val velocityY: Float = 0f,
        @SerialName("primaryCooldownTicks") val primaryCooldownTicks: Int = 0,
        @SerialName("respawnInTicks") val respawnInTicks: Int = 0,
        @SerialName("maxArmor") val maxArmor: Int = 0,
        @SerialName("maxFuel") val maxFuel: Int = 0,
        @SerialName("spawnX") val spawnX: Int = -1,
        @SerialName("spawnY") val spawnY: Int = -1,
        @SerialName("spawnBodyDirection") val spawnBodyDirection: Int = -1,
        @SerialName("spawnTurretDirection") val spawnTurretDirection: Int = -1,
    ) : Serializable.State<ServerTankActor> {
        override fun restore(): ServerTankActor = ServerTankActor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}

private fun decay(value: Float, amount: Float): Float = when {
    value > 0f -> (value - amount).coerceAtLeast(0f)
    value < 0f -> (value + amount).coerceAtMost(0f)
    else -> 0f
}
