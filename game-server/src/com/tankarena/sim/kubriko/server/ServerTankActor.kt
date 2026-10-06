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
import com.tankarena.content.LegacySpriteResources
import com.tankarena.content.PictureMasks
import com.tankarena.core.LegacyDirections
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.protocol.snapshot.EffectKind
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.protocol.snapshot.SoundKind
import com.tankarena.sim.kubriko.Resolvable
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

/** Seconds per server tick; the legacy physics (src/tanks/control.c) works in px/s with `dt = 1/sec`. */
private const val TICK_SECONDS: Float = 0.01f
/** Legacy `weapon_left()/right()`: `tinit(15)` between two weapon changes. */
private const val WEAPON_CYCLE_COOLDOWN_TICKS: Int = 15
/** Legacy tank_fire(): an empty weapon clicks and blocks firing for 50 ticks. */
private const val EMPTY_WEAPON_DELAY_TICKS: Int = 50
/** Legacy create_bullet(): bullets leave at 200 px/s plus the tank's own speed, but never slower than 75. */
private const val BULLET_SPEED: Float = 200f
private const val MIN_BULLET_SPEED: Float = 75f
/** Legacy create_bullet() ranges: the main gun reaches 250 px (500 in player-vs-player), the chain gun 5 tiles. */
private const val MAIN_GUN_RANGE_PX: Int = 250
private const val MAIN_GUN_DUAL_RANGE_PX: Int = 500
private const val CHAIN_GUN_RANGE_PX: Int = 5 * LEGACY_TILE_SIZE
/** Legacy tank_fire() case 1: each chain-gun burst uses 5 rounds. */
private const val CHAIN_ROUNDS_PER_BURST: Int = 5
/** Legacy HT_FLAME: 75 px/s for 120 px; each burst burns 40 fuel. */
private const val FLAME_SPEED: Float = 75f
internal const val FLAME_RANGE_PX: Int = 120
private const val FLAME_FUEL_COST: Int = 40
/** Legacy create_mortar(): a blast growing from 5 to 60 px that hits for 5 per step, i.e. up to 60 at the centre. */
private const val MORTAR_DAMAGE: Int = 60
/** Legacy create_bullet(HT_MORTAR): 75 px/s for 120 px, over walls. */
private const val MORTAR_SPEED: Float = 0.75f
// Legacy tank_marker_control(): the marker moves every 3 ticks, starting at 5 px per step and
// speeding up to 20, and must stay within 450 px of the tank outside player-vs-player.
private const val LOCK_STEP_TICKS: Int = 3
private const val LOCK_START_SPEED: Int = 5
private const val LOCK_MAX_SPEED: Int = 20
internal const val LOCK_MAX_RANGE_PX: Int = 450
/** Legacy `lock.fired = tinit(50)`: the camera stays on the marker this long after launch. */
private const val LOCK_CAMERA_HOLD_TICKS: Int = 50
// Legacy control_tank(): HT_LAVA hits for 2 every 10 ticks.
private const val LAVA_DAMAGE: Int = 2
private const val LAVA_INTERVAL_TICKS: Int = 10
internal const val RESPAWN_DELAY_TICKS: Int = 300
/** Legacy `shield.power`: starts at 3000, drains 60 per tick while held, recharges 10 per tick up to 6000. */
internal const val SHIELD_START_POWER: Int = 3000
private const val SHIELD_MAX_POWER: Int = 6000
private const val SHIELD_DRAIN_PER_TICK: Int = 60
private const val SHIELD_RECHARGE_PER_TICK: Int = 10
/** Legacy `shield.icount = 15` blinks of `tinit(5)`: a fresh human tank cannot be hurt for 75 ticks. */
internal const val START_SHIELD_TICKS: Int = 15 * 5
/** Legacy new_position()/check_obj_collide(): crash damage at most every 10 ticks; a heavy rammer hits for 2. */
private const val CRASH_INTERVAL_TICKS: Int = 10
private const val RAM_DAMAGE: Int = 2
/** Legacy control_player(): a tank counts as in its base within 7 px of its start pad. */
private const val BASE_RADIUS_PX: Int = 7
/** Legacy new_velocity(): gravity in px/s² and the depth (`lowest`) at which a pit destroys a tank. */
private const val PIT_GRAVITY: Float = 100f
private const val PIT_BOTTOM_HEIGHT: Float = -4f
/** Legacy `contr.disable = tinit(100)`: a destroyed tank keeps exploding this long before it is a wreck. */
private const val DYING_TICKS: Int = 100
/** Legacy WRECK_LIFE_TIME: a wreck smokes (ROOK every 500..800 ticks) for 30 s. */
private const val WRECK_SMOKE_MIN_TICKS: Int = 500
private const val WRECK_SMOKE_SPREAD_TICKS: Int = 300

const val WEAPON_MAIN: Int = 0
const val WEAPON_CHAIN: Int = 1
const val WEAPON_FLAME: Int = 2
const val WEAPON_MINE: Int = 3
const val WEAPON_ROCKET: Int = 4
const val WEAPON_MORTAR: Int = 5
/** Legacy `num_weapon`. */
const val WEAPON_COUNT: Int = 12

/** Legacy `props.max_fuel` for almost every vehicle in `src/data/tanks.c`; fuel is measured in pixels driven. */
const val LEGACY_MAX_FUEL: Int = 30000

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

    // Structures and other vehicles are handled by the legacy movement rules in advancePosition().
    override val collidableTypes: List<KClass<out Collidable>> = listOf(ServerGoalActor::class)

    var bodyDirection: Int = state.bodyDirection
        private set
    var turretDirection: Int = state.turretDirection
        private set
    /** Human player driving this tank, or -1. Mutable so control can move between friendly tanks. */
    var playerIndex: Int = state.playerIndex
        internal set
    /** Legacy `contr.id` (the F1–F10 tank id); -1 when the map did not assign one. */
    val controllerId: Int = state.controllerId
    /** Legacy `contr.computer`: when false the tank only moves while a player controls it. */
    val computer: Boolean = state.computer
    /** Legacy `o->gc`: goal-counter percentage applied when this tank is destroyed. */
    var goalContribution: Int = state.goalContribution
        internal set
    /** Legacy `computer.attack_tanks`: 0 means this computer tank never goes after tanks (e.g. cars). */
    val aiAttackTanks: Int = state.aiAttackTanks
    /** Legacy `computer.activation_rad`; -1 keeps the tank permanently active. */
    val aiActivationRadius: Int = state.aiActivationRadius
    /** Legacy `computer.activator`: 0 = woken by good tanks, 1 = by evil tanks. */
    val aiActivator: Int = state.aiActivator
    val aiStayActive: Boolean = state.aiStayActive
    var aiActive: Boolean = state.aiActive
        internal set
    /** Legacy player start pad type (`type & 1` = player, `type >> 1` = vehicle kind); -1 for map tanks. */
    val startPadType: Int = state.startPadType
    val tankType: Int = state.tankType
    val spec: VehicleSpec = LegacyVehicles.spec(tankType)
    val turretMode: TurretMode = spec.turretMode
    /** Legacy `shield.armor`; maps store each tank's own value, the vehicle table its maximum. */
    var armor: Int = state.armor
    /** Legacy `shield.power`: the pool the shield key draws from. */
    var shield: Int = state.shield
        private set
    /** Ticks of the legacy start shield (`shield.icount`) left; the tank cannot be hurt meanwhile. */
    var invulnerableTicks: Int = state.invulnerableTicks
        private set
    /** True while the shield key is held and the pool is not empty (legacy `contr.shield && shield.power`). */
    var shieldActive: Boolean = false
        private set
    var fuel: Int = state.fuel
    var lives: Int = state.lives
        private set
    val team: Int = state.team
    /** Legacy `weap.nf`: one refire timer shared by all weapons. */
    var primaryCooldownTicks: Int = state.primaryCooldownTicks
        private set
    /** Legacy `weap.nw`: ammo per weapon number. */
    private val ammo: IntArray = state.ammo.toWeaponArray()
    /** Legacy `weap.power`: hit power per weapon number, as stored with the tank in the map. */
    private val power: IntArray = state.power.toWeaponArray()
    private val spawnAmmo: List<Int> = state.ammo
    var currentWeapon: Int = state.currentWeapon
        private set
    private var weaponCycleCooldownTicks: Int = 0
    var respawnInTicks: Int = state.respawnInTicks
        private set

    val maxArmor: Int = if (state.maxArmor > 0) state.maxArmor else spec.armor.coerceAtLeast(state.armor).coerceAtLeast(1)
    val maxFuel: Int = if (state.maxFuel > 0) state.maxFuel else state.fuel.coerceAtLeast(0)
    private val spawnX: Int = if (state.spawnX >= 0) state.spawnX
        else (body.position.x.raw + body.size.width.raw / 2f).toInt()
    private val spawnY: Int = if (state.spawnY >= 0) state.spawnY
        else (body.position.y.raw + body.size.height.raw / 2f).toInt()
    private val spawnBodyDirection: Int = state.spawnBodyDirection.takeIf { it >= 0 } ?: state.bodyDirection
    private val spawnTurretDirection: Int = state.spawnTurretDirection.takeIf { it >= 0 } ?: state.turretDirection

    /** Sub-pixel position (legacy `o->fx`, `o->fy`); [positionX]/[positionY] are its rounded values. */
    private var exactX: Float = body.position.x.raw + body.size.width.raw / 2f
    private var exactY: Float = body.position.y.raw + body.size.height.raw / 2f
    var positionX: Int = exactX.roundToInt()
        private set
    var positionY: Int = exactY.roundToInt()
        private set
    /** Velocity in px/s (legacy `motion.vx`, `motion.vy`). */
    var velocityX: Float = state.velocityX
        private set
    var velocityY: Float = state.velocityY
        private set

    /** Rocket lock-on marker (legacy `tnk.lock`): active while the fire key is held with rockets selected. */
    var lockActive: Boolean = false
        private set
    var lockX: Int = 0
        private set
    var lockY: Int = 0
        private set
    private var lockVelocityX: Int = 0
    private var lockVelocityY: Int = 0
    private var lockStepCooldownTicks: Int = 0
    /** Ticks the camera keeps showing the marker; legacy `lock.fired`. */
    var lockCameraTicks: Int = 0
        private set
    private var lockMaxX: Int = Int.MAX_VALUE
    private var lockMaxY: Int = Int.MAX_VALUE
    /** Legacy `game.mode == DUAL`: longer main-gun range and no marker range limit. */
    private var dualMode: Boolean = false
    private var lavaCooldownTicks: Int = 0
    /** Legacy `weap.reload`: set when a tank of another side dies; the next base visit refills this tank. */
    private var refillAllowed: Boolean = false
    /** Legacy `o->fh`: 1 on the ground; a tank that drives into a pit sinks to -4 and is destroyed there. */
    private var exactHeight: Float = 1f
    private var verticalSpeed: Float = 0f
    /** Legacy `o->h`, rounded from the exact height; below 1 while the tank falls into a pit. */
    val height: Int get() = exactHeight.roundToInt()
    val falling: Boolean get() = exactHeight < 1f
    private var dyingTicks: Int = 0
    private var smokeCooldownTicks: Int = 0

    private var pendingIntent: PlayerIntentFrame = PlayerIntentFrame()
    private var hullTurnCooldownTicks: Int = 0
    private var turretTurnCooldownTicks: Int = 0
    private var terrainSpeedMultiplier: Float = 1.0f
    var enforcedWeapon: Int = -1
    private var pendingResolveX: Int = 0
    private var pendingResolveY: Int = 0
    private var pendingDamageThisTick: Int = 0
    private var pendingRocketProofDamage: Int = 0
    private var pendingDestroy: Boolean = false

    private val pendingFireRequests = mutableListOf<FireRequest>()
    /** Legacy `weap.activegun`: the main-gun barrel that fires next. */
    private var activeGun: Int = 0
    private var pendingMineRequest: MineRequest? = null
    private var pendingRocketRequest: RocketRequest? = null
    private var pendingMortarRequest: MortarRequest? = null
    private var destroyedThisTick: Boolean = false
    private var spawnedThisTick: Boolean = false
    private var damageEventAmount: Int = 0
    private val pendingEffects = mutableListOf<GameEvent>()
    private val random = Random(tankType * 7919 + spawnX * 31 + spawnY)

    /**
     * A bullet or flame leaving the tank. Speeds are in px per tick; [rangePx] is how far it flies
     * (legacy `b->dist`).
     */
    data class FireRequest(
        val originX: Int,
        val originY: Int,
        val direction: Int,
        val speed: Float,
        val rangePx: Int,
        val damage: Int,
        val weaponKind: Int = WEAPON_MAIN,
    ) {
        val velocityX: Float get() = LegacyDirections.unitVector(direction).first * speed
        val velocityY: Float get() = LegacyDirections.unitVector(direction).second * speed
        val ttlTicks: Int get() = (rangePx / speed).roundToInt().coerceAtLeast(1)
    }

    data class MineRequest(
        val originX: Int,
        val originY: Int,
        val damage: Int,
        val radius: Int,
    )

    data class RocketRequest(
        val originX: Int,
        val originY: Int,
        val targetX: Int,
        val targetY: Int,
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

    /** Damage the shield key cannot stop, though the start shield still does (legacy HT_ROCKET). */
    fun queueUnshieldableDamage(amount: Int) {
        if (amount <= 0) return
        pendingRocketProofDamage += amount
    }

    /** Legacy HT_DESTROY (pits, crashes): kills through every shield. */
    fun queueDestroy() {
        pendingDestroy = true
    }

    fun grantShield(amount: Int) {
        if (amount <= 0) return
        shield = (shield + amount).coerceAtMost(SHIELD_MAX_POWER)
    }

    fun grantInvulnerability(ticks: Int) {
        if (ticks <= 0) return
        invulnerableTicks = invulnerableTicks.coerceAtLeast(ticks)
    }

    /** Ammo left for a legacy weapon number. */
    fun ammoFor(weapon: Int): Int = ammo.getOrElse(weapon) { 0 }

    /** All ammo counts, indexed by legacy weapon number. */
    val ammoCounts: List<Int> get() = ammo.toList()

    /** Hit power of a legacy weapon number for this tank. */
    fun powerFor(weapon: Int): Int = power.getOrElse(weapon) { 0 }

    /** Map limits for the rocket marker and the player-vs-player rules (legacy `game.mode == DUAL`). */
    internal fun configureForMatch(maxX: Int, maxY: Int, dual: Boolean) {
        lockMaxX = maxX
        lockMaxY = maxY
        dualMode = dual
    }

    /** Legacy lava contact: [LAVA_DAMAGE] on entering and then every [LAVA_INTERVAL_TICKS] while inside. */
    internal fun stepLava(inLava: Boolean) {
        if (lavaCooldownTicks > 0) lavaCooldownTicks -= 1
        if (!inLava || lavaCooldownTicks > 0) return
        queueDamage(LAVA_DAMAGE)
        lavaCooldownTicks = LAVA_INTERVAL_TICKS
    }

    /** Legacy new_velocity() with `in_pit()`: start sinking into the pit under the tank. */
    internal fun startFalling() {
        if (falling || armor <= 0) return
        exactHeight = 0.99f
        verticalSpeed = 0f
    }

    internal fun allowBaseRefill() {
        refillAllowed = true
    }

    /**
     * Legacy tank_refill(): a player-vs-player tank parked on its own start pad (within 7 px) after
     * an opponent died gets its vehicle's full ammo, fuel and armor back, once per death.
     */
    private fun stepBaseRefill() {
        if (!refillAllowed || startPadType < 0 || armor <= 0) return
        val dx = positionX - spawnX
        val dy = positionY - spawnY
        if (dx * dx + dy * dy > BASE_RADIUS_PX * BASE_RADIUS_PX) return
        spec.ammo.forEachIndexed { weapon, count -> if (weapon < WEAPON_COUNT && ammo[weapon] < count) ammo[weapon] = count }
        fuel = spec.maxFuel
        armor = spec.armor
        refillAllowed = false
    }

    /** Direction the main gun, flamer and mortar fire in: the turret only for `MAIN_TURRET` vehicles. */
    val aimDirection: Int
        get() = if (turretMode == TurretMode.TURRET) turretDirection else bodyDirection

    /** Speed in px/s (legacy `motion.v`). */
    val speed: Float get() = hypot(velocityX, velocityY)

    /** Set terrain speed multiplier from the terrain grid. Called each tick by ServerMatchPrototype. */
    fun setTerrainSpeedMultiplier(multiplier: Float) {
        terrainSpeedMultiplier = multiplier.coerceAtLeast(0f)
    }

    /** Enforce a specific weapon from an enforcer actor. */
    internal fun applyEnforcedWeapon(weapon: Int) {
        this.enforcedWeapon = weapon
    }

    /** Teleport the tank to a new position (used by warp actors). */
    fun teleportTo(x: Int, y: Int) {
        exactX = x.toFloat()
        exactY = y.toFloat()
        positionX = x
        positionY = y
        lastOverlap = Int.MAX_VALUE
        syncBodyFromPosition()
    }

    override fun update(deltaTimeInMilliseconds: Int) {
        if (falling && armor > 0) {
            stepFall()
            pendingIntent = PlayerIntentFrame()
            return
        }
        if (armor <= 0) {
            velocityX = 0f
            velocityY = 0f
            stepWreck()
            if (respawnInTicks > 0) {
                respawnInTicks -= 1
                if (respawnInTicks == 0) respawn()
            }
            lockActive = false
            lockCameraTicks = 0
            shieldActive = false
            pendingIntent = PlayerIntentFrame()
            return
        }
        stepShields()
        stepSmoke()
        stepBaseRefill()
        if (lockCameraTicks > 0) lockCameraTicks -= 1
        if (lockActive) {
            // Legacy control_tank(): while the marker is out the drive keys steer it, so the tank
            // neither turns nor accelerates and cannot switch weapons.
            stepVelocity(thrust = false)
            advancePosition()
            stepCooldowns()
            stepRocketLock()
        } else {
            stepWeaponCycle()
            stepHullTurn()
            stepTurretTurn()
            stepVelocity(thrust = true)
            advancePosition()
            stepFire()
        }
        pendingIntent = PlayerIntentFrame()
    }

    private fun respawn() {
        exactX = spawnX.toFloat()
        exactY = spawnY.toFloat()
        positionX = spawnX
        positionY = spawnY
        lastOverlap = Int.MAX_VALUE
        bodyDirection = spawnBodyDirection
        turretDirection = spawnTurretDirection
        armor = maxArmor
        fuel = maxFuel
        shield = SHIELD_START_POWER
        invulnerableTicks = if (computer) 0 else START_SHIELD_TICKS
        velocityX = 0f
        velocityY = 0f
        primaryCooldownTicks = 0
        respawnInTicks = 0
        dyingTicks = 0
        exactHeight = 1f
        verticalSpeed = 0f
        spawnAmmo.forEachIndexed { index, count -> if (index < WEAPON_COUNT) ammo[index] = count }
        lockActive = false
        lockCameraTicks = 0
        spawnedThisTick = true
        syncBodyFromPosition()
    }

    fun drainLifecycleEvents(actorId: Long): List<GameEvent> {
        if (!destroyedThisTick && !spawnedThisTick && damageEventAmount == 0 && pendingEffects.isEmpty()) return emptyList()
        val events = mutableListOf<GameEvent>()
        events += pendingEffects
        pendingEffects.clear()
        if (damageEventAmount > 0) {
            events += GameEvent.DamageTaken(actorId, damageEventAmount)
            damageEventAmount = 0
        }
        if (destroyedThisTick) {
            events += GameEvent.TankDestroyed(actorId)
            events += GameEvent.Sound(kind = SoundKind.EXPLODE, x = positionX, y = positionY)
            destroyedThisTick = false
        }
        if (spawnedThisTick) {
            events += GameEvent.TankSpawned(actorId)
            spawnedThisTick = false
        }
        return events
    }

    fun drainFireRequests(): List<FireRequest> {
        if (pendingFireRequests.isEmpty()) return emptyList()
        val requests = pendingFireRequests.toList()
        pendingFireRequests.clear()
        return requests
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

    override fun onCollisionDetected(collidables: List<Collidable>) = Unit

    override fun applyPendingResolutions(worldWidthPixels: Int, worldHeightPixels: Int) {
        if (pendingResolveX == 0 && pendingResolveY == 0 && pendingDamageThisTick == 0 && pendingRocketProofDamage == 0 && !pendingDestroy) return
        if (pendingResolveX != 0 || pendingResolveY != 0) {
            positionX = (positionX + pendingResolveX)
                .coerceIn(SERVER_TANK_HALF, worldWidthPixels - SERVER_TANK_HALF - 1)
            positionY = (positionY + pendingResolveY)
                .coerceIn(SERVER_TANK_HALF, worldHeightPixels - SERVER_TANK_HALF - 1)
            exactX = positionX.toFloat()
            exactY = positionY.toFloat()
        }
        // Legacy hit_tank(): the start shield stops everything, the shield key everything but rockets.
        var incoming = 0
        if (invulnerableTicks <= 0) {
            incoming += pendingRocketProofDamage
            if (!shieldActive) incoming += pendingDamageThisTick
        }
        if (pendingDestroy) incoming = armor
        if (incoming > 0 && armor > 0) {
            val dealt = incoming.coerceAtMost(armor)
            armor = (armor - incoming).coerceAtLeast(0)
            if (armor == 0) {
                destroyedThisTick = true
                dyingTicks = DYING_TICKS
                smokeCooldownTicks = WRECK_SMOKE_MIN_TICKS + random.nextInt(WRECK_SMOKE_SPREAD_TICKS)
                lives = (lives - 1).coerceAtLeast(0)
                if (lives > 0) respawnInTicks = RESPAWN_DELAY_TICKS
                velocityX = 0f
                velocityY = 0f
            } else if (dealt > 0) {
                damageEventAmount += dealt
            }
        }
        pendingResolveX = 0
        pendingResolveY = 0
        pendingDamageThisTick = 0
        pendingRocketProofDamage = 0
        pendingDestroy = false
        syncBodyFromPosition()
    }

    override fun save(): State = State(
        body = body,
        bodyDirection = bodyDirection,
        turretDirection = turretDirection,
        playerIndex = playerIndex,
        controllerId = controllerId,
        computer = computer,
        goalContribution = goalContribution,
        startPadType = startPadType,
        aiAttackTanks = aiAttackTanks,
        aiActivationRadius = aiActivationRadius,
        aiActivator = aiActivator,
        aiStayActive = aiStayActive,
        aiActive = aiActive,
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
        ammo = ammo.toList(),
        power = power.toList(),
        currentWeapon = currentWeapon,
        respawnInTicks = respawnInTicks,
        maxArmor = maxArmor,
        maxFuel = maxFuel,
        spawnX = spawnX,
        spawnY = spawnY,
        spawnBodyDirection = spawnBodyDirection,
        spawnTurretDirection = spawnTurretDirection,
    )

    /** Legacy left_right(): one 1/16 turn every `motion.rotation` ticks. */
    private fun stepHullTurn() {
        hullTurnCooldownTicks = (hullTurnCooldownTicks - 1).coerceAtLeast(0)
        if (hullTurnCooldownTicks > 0) return
        when {
            pendingIntent.turnLeft && !pendingIntent.turnRight -> {
                bodyDirection = LegacyDirections.stepLeft(bodyDirection)
                hullTurnCooldownTicks = spec.motion.rotation
                resetOverlap()
            }

            pendingIntent.turnRight && !pendingIntent.turnLeft -> {
                bodyDirection = LegacyDirections.stepRight(bodyDirection)
                hullTurnCooldownTicks = spec.motion.rotation
                resetOverlap()
            }
        }
    }

    private fun stepTurretTurn() {
        when (turretMode) {
            // Legacy aim_left()/aim_right() only act on MAIN_TURRET guns.
            TurretMode.STATIC -> {
                turretDirection = bodyDirection
                return
            }
            // Legacy tank_effects(): MAIN_ROTATE tops spin one step per tick.
            TurretMode.ROTATE -> {
                turretDirection = LegacyDirections.stepRight(turretDirection)
                return
            }
            TurretMode.TURRET -> Unit
        }
        turretTurnCooldownTicks = (turretTurnCooldownTicks - 1).coerceAtLeast(0)
        if (turretTurnCooldownTicks > 0) return
        when {
            pendingIntent.aimLeft && !pendingIntent.aimRight -> {
                turretDirection = LegacyDirections.stepLeft(turretDirection)
                turretTurnCooldownTicks = spec.motion.rotation
            }

            pendingIntent.aimRight && !pendingIntent.aimLeft -> {
                turretDirection = LegacyDirections.stepRight(turretDirection)
                turretTurnCooldownTicks = spec.motion.rotation
            }
        }
    }

    /**
     * Legacy new_velocity(): thrust `acc / mass` along the hull while a drive key is held, then
     * friction `(ff_v·|v| + ff_0) / mass` along and `(fs_v·|v| + fs_0) / mass` across the hull.
     */
    private fun stepVelocity(thrust: Boolean) {
        val motion = spec.motion
        val (fx, fy) = LegacyDirections.unitVector(bodyDirection)
        var along = velocityX * fx + velocityY * fy
        var across = velocityX * -fy + velocityY * fx
        val driving = pendingIntent.forward != pendingIntent.reverse
        if (thrust && driving && fuel > 0) {
            val dv = TICK_SECONDS * motion.acc / motion.mass * terrainSpeedMultiplier
            along += if (pendingIntent.forward) dv else -dv
        }
        val alongFriction = (motion.ffV * abs(along) + motion.ff0) * TICK_SECONDS / motion.mass
        along = decay(along, alongFriction)
        val acrossFriction = (motion.fsV * abs(across) + motion.fs0) * TICK_SECONDS / motion.mass
        across = decay(across, acrossFriction)
        velocityX = along * fx + across * -fy
        velocityY = along * fy + across * fx
    }

    /** Legacy new_position(): move by the velocity and burn one unit of fuel per pixel (humans only). */
    /**
     * Legacy new_position() (src/tanks/control.c): try the move this tick's velocity asks for.
     * It is refused when the vehicle's picture would overlap standing structures by more pixels
     * than it does now (it then bounces, [staticCollide]), or when it would come within the two
     * vehicles' radii of another vehicle ([collideWithVehicle]).
     */
    private fun advancePosition() {
        val dx = velocityX * TICK_SECONDS
        val dy = velocityY * TICK_SECONDS
        val nextX = exactX + dx
        val nextY = exactY + dy
        val surroundings = surroundings
        if (surroundings != null && (dx != 0f || dy != 0f)) {
            val roundedX = nextX.roundToInt()
            val roundedY = nextY.roundToInt()
            if (lastOverlap == Int.MAX_VALUE) resetOverlap()
            val hitTile = IntArray(2)
            val overlap = structureOverlap(roundedX, roundedY, hitTile)
            if (overlap > lastOverlap) {
                staticCollide()
                crashInto(hitTile[0], hitTile[1])
                return
            }
            lastOverlap = overlap
            val other = surroundings.tanks().firstOrNull { other ->
                other !== this && abs(other.height - height) < 2 &&
                    inRadius(other.positionX - roundedX, other.positionY - roundedY, spec.radius + other.spec.radius)
            }
            if (other != null) {
                collideWithVehicle(other)
                return
            }
        }
        exactX = nextX.coerceIn(0f, surroundings?.worldWidth?.toFloat() ?: Float.MAX_VALUE)
        exactY = nextY.coerceIn(0f, surroundings?.worldHeight?.toFloat() ?: Float.MAX_VALUE)
        positionX = exactX.roundToInt()
        positionY = exactY.roundToInt()
        if (playerIndex >= 0 && (dx != 0f || dy != 0f)) {
            fuelDebt += hypot(dx, dy)
            val whole = fuelDebt.toInt()
            if (whole > 0) {
                fuel = (fuel - whole).coerceAtLeast(0)
                fuelDebt -= whole
            }
        }
        syncBodyFromPosition()
    }

    private var fuelDebt: Float = 0f

    /** The structures and other vehicles this tank moves among; set by the match. */
    internal var surroundings: TankSurroundings? = null
    /** Legacy `motion.overlap`: pixels the picture overlaps structures by at the current spot. */
    private var lastOverlap: Int = Int.MAX_VALUE
    /** Legacy `motion.nhit`: crash damage is dealt at most every 10 ticks. */
    private var crashCooldownTicks: Int = 0

    private fun bodyMask(): PictureMasks.Mask? =
        PictureMasks.mask(LegacySpriteResources.nameForTankBody(tankType, bodyDirection.mod(16)))

    /** Legacy overlap() for this tank's body picture centred on ([x], [y]). */
    private fun structureOverlap(x: Int, y: Int, firstHit: IntArray? = null): Int {
        val grid = surroundings?.structures ?: return 0
        if (height >= 2) return 0
        val mask = bodyMask() ?: return 0
        return grid.overlap(mask, x - LEGACY_TILE_SIZE / 2, y - LEGACY_TILE_SIZE / 2, firstHit)
    }

    /** Legacy left_right(): turning is always allowed and only resets the overlap baseline. */
    private fun resetOverlap() {
        lastOverlap = structureOverlap(positionX, positionY)
    }

    /**
     * Legacy static_collide(): running into a structure bounces the vehicle back with `crash` of
     * its speed, with a crash sound above 10 px/s.
     */
    private fun staticCollide() {
        velocityX *= -spec.motion.crash
        velocityY *= -spec.motion.crash
        if (speed > 10f) pendingEffects += GameEvent.Sound(kind = SoundKind.CRASH, x = positionX, y = positionY)
    }

    /**
     * Legacy new_position(): a vehicle with `general.crash` damages the structure it rams (crash
     * strength, `|v| / 25 + crash`), at most every 10 ticks.
     */
    private fun crashInto(tileX: Int, tileY: Int) {
        val surroundings = surroundings ?: return
        if (spec.crashDamage <= 0 || crashCooldownTicks > 0 || height >= 2) return
        if (surroundings.structures.strength(tileX, tileY, StructureLayer.SOLID, HitKind.CRASH) == 0) return
        val power = (speed / 25f).toInt() + spec.crashDamage
        val tileCentreX = tileX * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
        val tileCentreY = tileY * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2
        pendingEffects += GameEvent.Effect(
            EffectKind.RUIN,
            (tileCentreX + positionX) / 2 - 4 + random.nextInt(8),
            (tileCentreY + positionY) / 2 - 4 + random.nextInt(8),
        )
        surroundings.damageStructure(tileX, tileY, StructureLayer.SOLID, HitKind.CRASH, power)
        crashCooldownTicks = CRASH_INTERVAL_TICKS
    }

    /**
     * Legacy tanks_collide(): this vehicle ran into [other]; the relative velocity is shared by
     * the mass ratio and this vehicle's `crash` factor. A heavy rammer (`general.crash > 4`) also
     * hits the other for 2 every 10 ticks. A wreck simply stops the vehicle.
     */
    private fun collideWithVehicle(other: ServerTankActor) {
        if (other.armor <= 0) {
            velocityX = 0f
            velocityY = 0f
            return
        }
        if (crashCooldownTicks == 0 && spec.crashDamage > 4) {
            other.queueDamage(RAM_DAMAGE)
            crashCooldownTicks = CRASH_INTERVAL_TICKS
        }
        val relativeX = velocityX - other.velocityX
        val relativeY = velocityY - other.velocityY
        val loss = spec.motion.crash
        val massRatio = spec.motion.mass / other.spec.motion.mass
        velocityX -= relativeX * (1 - loss) / massRatio
        velocityY -= relativeY * (1 - loss) / massRatio
        other.push(relativeX * loss * massRatio, relativeY * loss * massRatio)
    }

    internal fun push(dvx: Float, dvy: Float) {
        velocityX += dvx
        velocityY += dvy
    }

    private fun inRadius(dx: Int, dy: Int, radius: Int): Boolean = dx * dx + dy * dy < radius * radius

    private fun stepCooldowns() {
        primaryCooldownTicks = (primaryCooldownTicks - 1).coerceAtLeast(0)
        crashCooldownTicks = (crashCooldownTicks - 1).coerceAtLeast(0)
    }

    /**
     * Legacy tank_fire(): one refire timer for all weapons (`weap.speed[w]` ticks). Human tanks
     * spend ammo and click when a weapon is empty; computer tanks fire without ammo.
     */
    private fun stepFire() {
        stepCooldowns()
        if (!pendingIntent.firePrimary || armor <= 0 || primaryCooldownTicks > 0) return
        val weapon = currentWeapon
        if (!computer) {
            if (weapon == WEAPON_FLAME) {
                if (!spec.hasFlamer && ammo[WEAPON_FLAME] <= 0) return
                if (fuel <= FLAME_FUEL_COST) return
                fuel -= FLAME_FUEL_COST
            } else {
                if (ammoFor(weapon) <= 0) {
                    pendingEffects += GameEvent.Sound(kind = SoundKind.EMPTY, x = positionX, y = positionY)
                    primaryCooldownTicks = EMPTY_WEAPON_DELAY_TICKS
                    return
                }
                ammo[weapon] = (ammo[weapon] - if (weapon == WEAPON_CHAIN) CHAIN_ROUNDS_PER_BURST else 1).coerceAtLeast(0)
            }
        }
        primaryCooldownTicks = spec.refireTicks.getOrElse(weapon) { 50 }
        when (weapon) {
            WEAPON_MAIN -> fireMainCannon()
            WEAPON_CHAIN -> fireChainGun()
            WEAPON_FLAME -> fireFlamer()
            WEAPON_MINE -> deployMine()
            WEAPON_ROCKET -> fireRocket()
            WEAPON_MORTAR -> fireMortar()
        }
    }

    /** A point on the hull: [offset] (north-facing, from the centre) turned with the hull. */
    private fun onHull(offset: Pair<Int, Int>): Pair<Int, Int> {
        val (x, y) = rotate(bodyDirection, offset)
        return positionX + x to positionY + y
    }

    /** A point on the turret, which sits at `turret.loc` on the hull and points along [aimDirection]. */
    private fun onTurret(offset: Pair<Int, Int>): Pair<Int, Int> {
        val (tx, ty) = rotate(bodyDirection, spec.turretOffset)
        val (x, y) = rotate(aimDirection, offset)
        return positionX + tx + x to positionY + ty + y
    }

    /** Legacy tank_fire(): the main gun, flamer and mortar fire from the main-gun barrels in turn. */
    private fun nextMainBarrel(): Pair<Int, Int> {
        val guns = spec.mainGuns.ifEmpty { listOf(0 to -12) }
        val barrel = onTurret(guns[activeGun % guns.size])
        activeGun = (activeGun + 1) % guns.size
        return barrel
    }

    private fun fireMortar() {
        val (fx, fy) = LegacyDirections.unitVector(aimDirection)
        val (ox, oy) = nextMainBarrel()
        pendingMortarRequest = MortarRequest(
            originX = ox,
            originY = oy,
            velocityX = fx * MORTAR_SPEED,
            velocityY = fy * MORTAR_SPEED,
            damage = MORTAR_DAMAGE,
            maxRadius = MORTAR_MAX_RADIUS_PX,
        )
        pendingEffects += GameEvent.Effect(EffectKind.MORTAR_FIRE, ox, oy)
    }

    /**
     * Legacy tank_fire() case 4: a human player gets a lock-on marker at the tank, steered with
     * the drive keys until fire is released (see [stepRocketLock]); a computer tank launches
     * straight at its target.
     */
    private fun fireRocket() {
        lockActive = true
        lockX = positionX
        lockY = positionY
        lockVelocityX = 0
        lockVelocityY = 0
        lockStepCooldownTicks = 0
        lockCameraTicks = LOCK_CAMERA_HOLD_TICKS
    }

    /** Fire a rocket straight at a point, as legacy computer tanks do (`tank_fire_locked()`). */
    internal fun fireRocketAt(targetX: Int, targetY: Int) {
        if (primaryCooldownTicks > 0) return
        primaryCooldownTicks = spec.refireTicks[WEAPON_ROCKET]
        launchRocket(targetX, targetY)
    }

    /** Legacy tank_marker_control(). */
    private fun stepRocketLock() {
        lockCameraTicks = LOCK_CAMERA_HOLD_TICKS
        if (!pendingIntent.firePrimary) {
            lockActive = false
            launchRocket(lockX, lockY)
            return
        }
        if (lockStepCooldownTicks > 0) {
            lockStepCooldownTicks -= 1
            return
        }
        lockVelocityY = markerVelocity(lockVelocityY, negative = pendingIntent.forward, positive = pendingIntent.reverse)
        lockVelocityX = markerVelocity(lockVelocityX, negative = pendingIntent.turnLeft, positive = pendingIntent.turnRight)
        val nextX = (lockX + lockVelocityX).coerceIn(0, lockMaxX)
        val nextY = (lockY + lockVelocityY).coerceIn(0, lockMaxY)
        val dx = (nextX - positionX).toLong()
        val dy = (nextY - positionY).toLong()
        if (dualMode || dx * dx + dy * dy < LOCK_MAX_RANGE_PX.toLong() * LOCK_MAX_RANGE_PX) {
            lockX = nextX
            lockY = nextY
        } else {
            lockVelocityX = 0
            lockVelocityY = 0
        }
        lockStepCooldownTicks = LOCK_STEP_TICKS - 1
    }

    /** One axis of the legacy marker: jump to 5 px per step in the pressed direction, then speed up to 20. */
    private fun markerVelocity(current: Int, negative: Boolean, positive: Boolean): Int = when {
        negative -> if (current >= 0) -LOCK_START_SPEED else (current - 1).coerceAtLeast(-LOCK_MAX_SPEED)
        positive -> if (current <= 0) LOCK_START_SPEED else (current + 1).coerceAtMost(LOCK_MAX_SPEED)
        else -> 0
    }

    private fun launchRocket(targetX: Int, targetY: Int) {
        val (ox, oy) = onTurret(spec.rocketOffset)
        pendingRocketRequest = RocketRequest(
            originX = ox,
            originY = oy,
            targetX = targetX,
            targetY = targetY,
            damage = powerFor(WEAPON_ROCKET),
        )
    }

    private fun deployMine() {
        pendingMineRequest = MineRequest(
            originX = positionX,
            originY = positionY,
            damage = powerFor(WEAPON_MINE),
            radius = LIGHT_MINE_RADIUS_PX,
        )
    }

    private fun fireMainCannon() {
        val range = if (dualMode) MAIN_GUN_DUAL_RANGE_PX else MAIN_GUN_RANGE_PX
        val (ox, oy) = nextMainBarrel()
        pendingFireRequests += bulletRequest(ox, oy, aimDirection, range, powerFor(WEAPON_MAIN), WEAPON_MAIN)
        pendingEffects += GameEvent.Effect(EffectKind.FIRE, ox, oy)
    }

    private fun fireChainGun() {
        // Legacy tank_fire() case 1: every hull-mounted chain gun fires one round per burst.
        for (gun in spec.chainGuns.ifEmpty { listOf(0 to -12) }) {
            val (ox, oy) = onHull(gun)
            pendingFireRequests += bulletRequest(ox, oy, bodyDirection, CHAIN_GUN_RANGE_PX, powerFor(WEAPON_CHAIN), WEAPON_CHAIN)
            pendingEffects += GameEvent.Effect(EffectKind.SFIRE, ox, oy)
        }
    }

    /** Legacy HT_FLAME: a slow flame that ignores the tank's own speed. */
    private fun fireFlamer() {
        val (ox, oy) = nextMainBarrel()
        pendingFireRequests += FireRequest(
            originX = ox,
            originY = oy,
            direction = aimDirection,
            speed = FLAME_SPEED * TICK_SECONDS,
            rangePx = FLAME_RANGE_PX,
            damage = powerFor(WEAPON_FLAME),
            weaponKind = WEAPON_FLAME,
        )
    }

    /**
     * Legacy create_bullet(): 200 px/s plus the tank's own velocity (but at least 75 px/s), with
     * the range scaled by the same factor.
     */
    private fun bulletRequest(ox: Int, oy: Int, direction: Int, range: Int, damage: Int, weapon: Int): FireRequest {
        val (fx, fy) = LegacyDirections.unitVector(direction)
        val bulletSpeed = hypot(velocityX + fx * BULLET_SPEED, velocityY + fy * BULLET_SPEED).coerceAtLeast(MIN_BULLET_SPEED)
        return FireRequest(
            originX = ox,
            originY = oy,
            direction = direction,
            speed = bulletSpeed * TICK_SECONDS,
            rangePx = (range * bulletSpeed / BULLET_SPEED).roundToInt(),
            damage = damage,
            weaponKind = weapon,
        )
    }

    /** Legacy shield_effects(): the start shield blinks away and the shield key draws on its pool. */
    private fun stepShields() {
        if (invulnerableTicks > 0) invulnerableTicks -= 1
        shieldActive = pendingIntent.shield && shield > 0
        shield = if (pendingIntent.shield) {
            (shield - SHIELD_DRAIN_PER_TICK).coerceAtLeast(0)
        } else {
            (shield + SHIELD_RECHARGE_PER_TICK).coerceAtMost(SHIELD_MAX_POWER)
        }
    }

    /**
     * Legacy new_velocity(): gravity pulls the tank down at 100 px/s² (`vz -= dt * 100`); at the
     * pit's bottom (`h == -4`) it is destroyed (HT_DESTROY). A falling tank cannot drive or fire.
     */
    private fun stepFall() {
        velocityX = 0f
        velocityY = 0f
        verticalSpeed -= PIT_GRAVITY * TICK_SECONDS
        exactHeight = (exactHeight + verticalSpeed * TICK_SECONDS).coerceAtLeast(PIT_BOTTOM_HEIGHT)
        if (exactHeight <= PIT_BOTTOM_HEIGHT) queueDestroy()
    }

    /** Legacy tank_effects(): moving vehicles that smoke puff more often once badly damaged. */
    private fun stepSmoke() {
        if (smokeCooldownTicks > 0) {
            smokeCooldownTicks -= 1
            return
        }
        if (!spec.smokes || speed < 1f || spec.smokePoints.isEmpty()) return
        val (sx, sy) = onHull(spec.smokePoints[random.nextInt(spec.smokePoints.size)])
        pendingEffects += GameEvent.Effect(EffectKind.SMOKE, sx, sy)
        smokeCooldownTicks = (if (armor <= maxArmor / 3) 5 else 30) + random.nextInt(10)
    }

    /** Legacy control_tank()/control_wreck(): explosions while dying, then occasional smoke from the wreck. */
    private fun stepWreck() {
        // A tank that fell into a pit lies at the bottom without burning.
        if (height < 1) return
        if (dyingTicks > 0) {
            dyingTicks -= 1
            pendingEffects += GameEvent.Effect(
                EffectKind.EXPLOSION,
                positionX - 8 + random.nextInt(16),
                positionY - 8 + random.nextInt(16),
            )
            return
        }
        if (smokeCooldownTicks > 0) {
            smokeCooldownTicks -= 1
            return
        }
        pendingEffects += GameEvent.Effect(EffectKind.ROOK, positionX - 4 + random.nextInt(8), positionY - 4 + random.nextInt(8))
        smokeCooldownTicks = WRECK_SMOKE_MIN_TICKS + random.nextInt(WRECK_SMOKE_SPREAD_TICKS)
    }

    /**
     * Legacy weapon_left()/weapon_right(): step to the next weapon with ammo, the flamethrower
     * counting as available when the vehicle has one.
     */
    private fun stepWeaponCycle() {
        weaponCycleCooldownTicks = (weaponCycleCooldownTicks - 1).coerceAtLeast(0)
        if (weaponCycleCooldownTicks > 0) return
        val step = when {
            pendingIntent.cycleWeaponRight && !pendingIntent.cycleWeaponLeft -> 1
            pendingIntent.cycleWeaponLeft && !pendingIntent.cycleWeaponRight -> -1
            else -> return
        }
        var weapon = currentWeapon
        repeat(WEAPON_COUNT) {
            weapon = (weapon + step).mod(WEAPON_COUNT)
            if (isAvailable(weapon)) {
                currentWeapon = weapon
                weaponCycleCooldownTicks = WEAPON_CYCLE_COOLDOWN_TICKS
                return
            }
        }
    }

    private fun isAvailable(weapon: Int): Boolean = ammoFor(weapon) != 0 || (weapon == WEAPON_FLAME && spec.hasFlamer)

    /** Pushes this tank out of an overlap; returns true when they overlapped. */
    private fun resolveAabbVsAabb(
        otherCx: Int,
        otherCy: Int,
        otherHalfW: Int,
        otherHalfH: Int,
        selfFraction: Float,
    ): Boolean {
        val dx = positionX - otherCx
        val dy = positionY - otherCy
        val combinedHalfW = SERVER_TANK_HALF + otherHalfW
        val combinedHalfH = SERVER_TANK_HALF + otherHalfH
        val overlapX = combinedHalfW - abs(dx)
        val overlapY = combinedHalfH - abs(dy)
        if (overlapX <= 0 || overlapY <= 0) return false
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
        return true
    }

    private fun syncBodyFromPosition() {
        val topLeft = SceneOffset(
            (positionX - SERVER_TANK_HALF).toFloat().sceneUnit,
            (positionY - SERVER_TANK_HALF).toFloat().sceneUnit,
        )
        body.position = topLeft
        collisionMask.position = topLeft
    }

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: SerializableBoxBody = BoxBody(),
        @SerialName("bodyDirection") val bodyDirection: Int = 0,
        @SerialName("turretDirection") val turretDirection: Int = 0,
        @SerialName("playerIndex") val playerIndex: Int = -1,
        @SerialName("controllerId") val controllerId: Int = -1,
        @SerialName("computer") val computer: Boolean = true,
        @SerialName("goalContribution") val goalContribution: Int = 0,
        @SerialName("startPadType") val startPadType: Int = -1,
        @SerialName("aiAttackTanks") val aiAttackTanks: Int = 100,
        @SerialName("aiActivationRadius") val aiActivationRadius: Int = -1,
        @SerialName("aiActivator") val aiActivator: Int = 0,
        @SerialName("aiStayActive") val aiStayActive: Boolean = false,
        @SerialName("aiActive") val aiActive: Boolean = false,
        @SerialName("tankType") val tankType: Int = 0,
        @SerialName("armor") val armor: Int = DEFAULT_TANK_ARMOR,
        @SerialName("shield") val shield: Int = SHIELD_START_POWER,
        @SerialName("invulnerableTicks") val invulnerableTicks: Int = 0,
        @SerialName("fuel") val fuel: Int = LEGACY_MAX_FUEL,
        @SerialName("lives") val lives: Int = 1,
        @SerialName("team") val team: Int = 0,
        @SerialName("velocityX") val velocityX: Float = 0f,
        @SerialName("velocityY") val velocityY: Float = 0f,
        @SerialName("primaryCooldownTicks") val primaryCooldownTicks: Int = 0,
        /** Legacy `weap.nw`; the default is the STANDARD tank's loadout from src/data/tanks.c. */
        @SerialName("ammo") val ammo: List<Int> = DEFAULT_AMMO,
        /** Legacy `weap.power`. */
        @SerialName("power") val power: List<Int> = DEFAULT_POWER,
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

    companion object {
        /** Legacy STANDARD tank `props.armor`. */
        const val DEFAULT_TANK_ARMOR: Int = 30
        val DEFAULT_AMMO: List<Int> = listOf(30, 1500, 0, 7, 3, 3, 2, 5, 5, 1, 1, 1)
        val DEFAULT_POWER: List<Int> = listOf(10, 1, 1, 7, 10, 0, 0, 1, 1, 0, 0, 0)
    }
}

private fun List<Int>.toWeaponArray(): IntArray = IntArray(WEAPON_COUNT) { getOrElse(it) { 0 } }

private fun decay(value: Float, amount: Float): Float = when {
    value > 0f -> (value - amount).coerceAtLeast(0f)
    value < 0f -> (value + amount).coerceAtMost(0f)
    else -> 0f
}
