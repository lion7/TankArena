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
private const val PRIMARY_COOLDOWN_TICKS: Int = 70
private const val CHAIN_COOLDOWN_TICKS: Int = 10
private const val WEAPON_CYCLE_COOLDOWN_TICKS: Int = 12
private const val PROJECTILE_SPEED: Float = 8f
private const val PRIMARY_DAMAGE: Int = 25
private const val CHAIN_DAMAGE: Int = 10
private const val CHAIN_PROJECTILE_TTL: Int = 21
private const val MINE_DAMAGE: Int = 7
private const val MINE_DEPLOY_COOLDOWN_TICKS: Int = 30
private const val ROCKET_DAMAGE: Int = 10
private const val ROCKET_FIRE_COOLDOWN_TICKS: Int = 200
private const val MORTAR_DAMAGE: Int = 15
private const val MORTAR_FIRE_COOLDOWN_TICKS: Int = 100
private const val MORTAR_LAUNCH_SPEED: Float = 6f
internal const val RESPAWN_DELAY_TICKS: Int = 300
private const val SHIELD_DECAY_TICKS: Int = 50

const val WEAPON_MAIN: Int = 0
const val WEAPON_CHAIN: Int = 1
const val WEAPON_MINE: Int = 3
const val WEAPON_ROCKET: Int = 4
const val WEAPON_MORTAR: Int = 5
internal const val INITIAL_CHAIN_AMMO: Int = 1500
internal const val INITIAL_MINE_AMMO: Int = 7
internal const val INITIAL_ROCKET_AMMO: Int = 3
internal const val INITIAL_MORTAR_AMMO: Int = 3

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
    var shield: Int = state.shield
        private set
    var invulnerableTicks: Int = state.invulnerableTicks
        private set
    var fuel: Int = state.fuel
    var lives: Int = state.lives
        private set
    private var shieldDecayCounter: Int = 0
    val team: Int = state.team
    var primaryCooldownTicks: Int = state.primaryCooldownTicks
        private set
    var chainCooldownTicks: Int = state.chainCooldownTicks
        private set
    var chainAmmo: Int = state.chainAmmo
        private set
    var mineAmmo: Int = state.mineAmmo
        private set
    var mineDeployCooldownTicks: Int = state.mineDeployCooldownTicks
        private set
    var rocketAmmo: Int = state.rocketAmmo
        private set
    var rocketCooldownTicks: Int = state.rocketCooldownTicks
        private set
    var mortarAmmo: Int = state.mortarAmmo
        private set
    var mortarCooldownTicks: Int = state.mortarCooldownTicks
        private set
    var currentWeapon: Int = state.currentWeapon
        private set
    private var weaponCycleCooldownTicks: Int = 0
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
    private var pendingMineRequest: MineRequest? = null
    private var pendingRocketRequest: RocketRequest? = null
    private var pendingMortarRequest: MortarRequest? = null
    private var destroyedThisTick: Boolean = false
    private var spawnedThisTick: Boolean = false
    private var damageEventAmount: Int = 0

    data class FireRequest(
        val originX: Int,
        val originY: Int,
        val velocityX: Int,
        val velocityY: Int,
        val damage: Int,
        val ttlTicks: Int,
        val weaponKind: Int = WEAPON_MAIN,
    )

    data class MineRequest(
        val originX: Int,
        val originY: Int,
        val damage: Int,
        val radius: Int,
    )

    data class RocketRequest(
        val originX: Int,
        val originY: Int,
        val initialVelocityX: Float,
        val initialVelocityY: Float,
        val damage: Int,
    )

    data class MortarRequest(
        val originX: Int,
        val originY: Int,
        val velocityX: Float,
        val velocityY: Float,
        val damage: Int,
        val maxRadius: Int,
    )

    fun applyIntent(intent: PlayerIntentFrame) {
        pendingIntent = intent
    }

    fun queueDamage(amount: Int) {
        if (amount <= 0) return
        pendingDamageThisTick += amount
    }

    fun grantShield(amount: Int) {
        if (amount <= 0) return
        shield = (shield + amount).coerceAtMost(255)
    }

    fun grantInvulnerability(ticks: Int) {
        if (ticks <= 0) return
        invulnerableTicks = invulnerableTicks.coerceAtLeast(ticks)
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
        stepInvulnAndShield()
        stepWeaponCycle()
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
        shield = 0
        invulnerableTicks = 0
        shieldDecayCounter = 0
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
            // Emit explosion sound at tank position
            events += com.tankarena.protocol.snapshot.GameEvent.Sound(
                kind = com.tankarena.protocol.snapshot.SoundKind.EXPLODE,
                x = positionX,
                y = positionY,
            )
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

    fun drainMineRequest(): MineRequest? {
        val request = pendingMineRequest
        pendingMineRequest = null
        return request
    }

    fun drainRocketRequest(): RocketRequest? {
        val request = pendingRocketRequest
        pendingRocketRequest = null
        return request
    }

    fun drainMortarRequest(): MortarRequest? {
        val request = pendingMortarRequest
        pendingMortarRequest = null
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
            var remaining = pendingDamageThisTick
            if (invulnerableTicks > 0) {
                remaining = 0
            } else if (shield > 0) {
                val absorbed = remaining.coerceAtMost(shield)
                shield -= absorbed
                remaining -= absorbed
            }
            if (remaining > 0) {
                val dealt = remaining.coerceAtMost(armor)
                armor = (armor - remaining).coerceAtLeast(0)
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
        shield = shield,
        invulnerableTicks = invulnerableTicks,
        fuel = fuel,
        lives = lives,
        team = team,
        velocityX = velocityX,
        velocityY = velocityY,
        primaryCooldownTicks = primaryCooldownTicks,
        chainCooldownTicks = chainCooldownTicks,
        chainAmmo = chainAmmo,
        mineAmmo = mineAmmo,
        mineDeployCooldownTicks = mineDeployCooldownTicks,
        rocketAmmo = rocketAmmo,
        rocketCooldownTicks = rocketCooldownTicks,
        mortarAmmo = mortarAmmo,
        mortarCooldownTicks = mortarCooldownTicks,
        currentWeapon = currentWeapon,
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
        chainCooldownTicks = (chainCooldownTicks - 1).coerceAtLeast(0)
        mineDeployCooldownTicks = (mineDeployCooldownTicks - 1).coerceAtLeast(0)
        rocketCooldownTicks = (rocketCooldownTicks - 1).coerceAtLeast(0)
        mortarCooldownTicks = (mortarCooldownTicks - 1).coerceAtLeast(0)
        if (!pendingIntent.firePrimary || armor <= 0) return
        when (currentWeapon) {
            WEAPON_MAIN -> fireMainCannon()
            WEAPON_CHAIN -> fireChainGun()
            WEAPON_MINE -> deployMine()
            WEAPON_ROCKET -> fireRocket()
            WEAPON_MORTAR -> fireMortar()
        }
    }

    private fun fireMortar() {
        if (mortarCooldownTicks > 0 || mortarAmmo <= 0) return
        val (fx, fy) = LegacyDirections.unitVector(turretDirection)
        val barrelOffset = SERVER_TANK_HALF + 2
        pendingMortarRequest = MortarRequest(
            originX = positionX + (fx * barrelOffset).toInt(),
            originY = positionY + (fy * barrelOffset).toInt(),
            velocityX = fx * MORTAR_LAUNCH_SPEED,
            velocityY = fy * MORTAR_LAUNCH_SPEED,
            damage = MORTAR_DAMAGE,
            maxRadius = MORTAR_MAX_RADIUS_PX,
        )
        mortarCooldownTicks = MORTAR_FIRE_COOLDOWN_TICKS
        mortarAmmo -= 1
    }

    private fun fireRocket() {
        if (rocketCooldownTicks > 0 || rocketAmmo <= 0) return
        val (fx, fy) = LegacyDirections.unitVector(turretDirection)
        val launchSpeed = 2f
        val barrelOffset = SERVER_TANK_HALF + 2
        pendingRocketRequest = RocketRequest(
            originX = positionX + (fx * barrelOffset).toInt(),
            originY = positionY + (fy * barrelOffset).toInt(),
            initialVelocityX = fx * launchSpeed,
            initialVelocityY = fy * launchSpeed,
            damage = ROCKET_DAMAGE,
        )
        rocketCooldownTicks = ROCKET_FIRE_COOLDOWN_TICKS
        rocketAmmo -= 1
    }

    private fun deployMine() {
        if (mineDeployCooldownTicks > 0 || mineAmmo <= 0) return
        pendingMineRequest = MineRequest(
            originX = positionX,
            originY = positionY,
            damage = MINE_DAMAGE,
            radius = LIGHT_MINE_RADIUS_PX,
        )
        mineAmmo -= 1
        mineDeployCooldownTicks = MINE_DEPLOY_COOLDOWN_TICKS
    }

    private fun fireMainCannon() {
        if (primaryCooldownTicks > 0) return
        pendingFireRequest = buildFireRequest(damage = PRIMARY_DAMAGE, ttl = PROJECTILE_TTL_TICKS, weapon = WEAPON_MAIN)
        primaryCooldownTicks = PRIMARY_COOLDOWN_TICKS
    }

    private fun fireChainGun() {
        if (chainCooldownTicks > 0 || chainAmmo <= 0) return
        pendingFireRequest = buildFireRequest(damage = CHAIN_DAMAGE, ttl = CHAIN_PROJECTILE_TTL, weapon = WEAPON_CHAIN)
        chainCooldownTicks = CHAIN_COOLDOWN_TICKS
        chainAmmo -= 1
    }

    private fun buildFireRequest(damage: Int, ttl: Int, weapon: Int): FireRequest {
        val (fx, fy) = LegacyDirections.unitVector(turretDirection)
        val (vx, vy) = LegacyDirections.toVelocityStep(turretDirection, PROJECTILE_SPEED)
        val barrelOffset = SERVER_TANK_HALF + 2
        return FireRequest(
            originX = positionX + (fx * barrelOffset).toInt(),
            originY = positionY + (fy * barrelOffset).toInt(),
            velocityX = vx.toInt().let { if (it == 0 && vx != 0f) (if (vx > 0) 1 else -1) else it },
            velocityY = vy.toInt().let { if (it == 0 && vy != 0f) (if (vy > 0) 1 else -1) else it },
            damage = damage,
            ttlTicks = ttl,
            weaponKind = weapon,
        )
    }

    private fun stepInvulnAndShield() {
        if (invulnerableTicks > 0) invulnerableTicks -= 1
        if (shield > 0) {
            shieldDecayCounter += 1
            if (shieldDecayCounter >= SHIELD_DECAY_TICKS) {
                shieldDecayCounter = 0
                shield -= 1
            }
        } else {
            shieldDecayCounter = 0
        }
    }

    private fun stepWeaponCycle() {
        weaponCycleCooldownTicks = (weaponCycleCooldownTicks - 1).coerceAtLeast(0)
        if (weaponCycleCooldownTicks > 0) return
        when {
            pendingIntent.cycleWeaponRight && !pendingIntent.cycleWeaponLeft -> {
                currentWeapon = nextOwnedWeapon(currentWeapon, +1)
                weaponCycleCooldownTicks = WEAPON_CYCLE_COOLDOWN_TICKS
            }

            pendingIntent.cycleWeaponLeft && !pendingIntent.cycleWeaponRight -> {
                currentWeapon = nextOwnedWeapon(currentWeapon, -1)
                weaponCycleCooldownTicks = WEAPON_CYCLE_COOLDOWN_TICKS
            }
        }
    }

    private fun nextOwnedWeapon(current: Int, step: Int): Int {
        val cycle = buildList {
            add(WEAPON_MAIN)
            if (chainAmmo > 0) add(WEAPON_CHAIN)
            if (mineAmmo > 0) add(WEAPON_MINE)
            if (rocketAmmo > 0) add(WEAPON_ROCKET)
            if (mortarAmmo > 0) add(WEAPON_MORTAR)
        }
        if (cycle.size <= 1) return WEAPON_MAIN
        val index = cycle.indexOf(current).takeIf { it >= 0 } ?: 0
        val next = ((index + step) % cycle.size + cycle.size) % cycle.size
        return cycle[next]
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
            velocityX = if (sign > 0) velocityX.coerceAtLeast(0f) else velocityX.coerceAtMost(0f)
        } else {
            val sign = if (dy >= 0) 1 else -1
            val push = (overlapY * selfFraction).toInt().coerceAtLeast(1) * sign
            pendingResolveY += push
            velocityY = if (sign > 0) velocityY.coerceAtLeast(0f) else velocityY.coerceAtMost(0f)
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
        @SerialName("shield") val shield: Int = 0,
        @SerialName("invulnerableTicks") val invulnerableTicks: Int = 0,
        @SerialName("fuel") val fuel: Int = 100,
        @SerialName("lives") val lives: Int = 1,
        @SerialName("team") val team: Int = 0,
        @SerialName("velocityX") val velocityX: Float = 0f,
        @SerialName("velocityY") val velocityY: Float = 0f,
        @SerialName("primaryCooldownTicks") val primaryCooldownTicks: Int = 0,
        @SerialName("chainCooldownTicks") val chainCooldownTicks: Int = 0,
        @SerialName("chainAmmo") val chainAmmo: Int = INITIAL_CHAIN_AMMO,
        @SerialName("mineAmmo") val mineAmmo: Int = INITIAL_MINE_AMMO,
        @SerialName("mineDeployCooldownTicks") val mineDeployCooldownTicks: Int = 0,
        @SerialName("rocketAmmo") val rocketAmmo: Int = INITIAL_ROCKET_AMMO,
        @SerialName("rocketCooldownTicks") val rocketCooldownTicks: Int = 0,
        @SerialName("mortarAmmo") val mortarAmmo: Int = INITIAL_MORTAR_AMMO,
        @SerialName("mortarCooldownTicks") val mortarCooldownTicks: Int = 0,
        @SerialName("currentWeapon") val currentWeapon: Int = WEAPON_MAIN,
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
