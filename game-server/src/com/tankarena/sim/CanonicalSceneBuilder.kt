package com.tankarena.sim

import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.MissionText
import com.tankarena.content.TileLayers
import com.tankarena.sim.legacy.AuthoredObject
import com.tankarena.sim.legacy.CanonicalMapDefinition
import com.tankarena.sim.legacy.ObjectKinds

private const val TURRET_FOOTPRINT: Int = LEGACY_TILE_SIZE
private const val GOAL_FOOTPRINT: Int = 16
/** Fallback armor for authored objects without one (the STANDARD tank's legacy `props.armor`). */
private const val DEFAULT_ARMOR: Int = Tank.DEFAULT_TANK_ARMOR
/** `make_player()` in src/objects/player.c gives dual-mode pads four lives. */
internal const val LEGACY_PAD_LIVES: Int = 4
private const val LIGHT_MINE_DAMAGE: Int = 7

object CanonicalSceneBuilder {

    fun buildActors(map: CanonicalMapDefinition): List<SceneObject<*>> {
        val actors = mutableListOf<SceneObject<*>>()
        appendAuthoredObjects(map, actors)
        return actors
    }

    fun buildSceneJson(map: CanonicalMapDefinition): String = SceneFile.write(buildActors(map))

    /** One object as the import builds it (the map editor's new objects); null for kinds without one. */
    fun buildObject(obj: AuthoredObject): SceneObject<*>? = mutableListOf<SceneObject<*>>().also { sink ->
        appendAuthoredObjects(CanonicalMapDefinition(EDITOR_METADATA, EDITOR_LAYERS, MissionText(), listOf(obj)), sink)
    }.singleOrNull()

    private val EDITOR_METADATA = MapMetadata(name = "editor", widthTiles = 1, heightTiles = 1, missionCode = "")
    private val EDITOR_LAYERS = TileLayers(listOf(-1), listOf(-1), listOf(-1), listOf(0), listOf(255), listOf(0), listOf(0))

    private fun appendAuthoredObjects(map: CanonicalMapDefinition, sink: MutableList<SceneObject<*>>) {
        var nextPlayerIndex = 0
        for (obj in map.objects) {
            val props = obj.properties
            val goalContribution = props.int("goalContribution") ?: 0
            when (obj.kind) {
                ObjectKinds.PLAYER_START -> {
                    val padType = props.int("startType")
                    if (padType != null) {
                        // Legacy dual-mode start pad: type & 1 is the player, type >> 1 the vehicle kind,
                        // and the pad direction is a quarter turn (0..3) of the 16-step compass.
                        val direction = ((props.int("direction") ?: 0) * 4).mod(16)
                        val padAmmo = props.intList("ammo")
                        sink += tankActor(
                            cx = obj.x,
                            cy = obj.y,
                            playerIndex = padType and 1,
                            bodyDirection = direction,
                            turretDirection = direction,
                            tankType = 0,
                            armor = LegacyVehicles.spec(0).armor,
                            lives = (props.int("lives") ?: 0).takeIf { it > 0 } ?: LEGACY_PAD_LIVES,
                            team = 0,
                            computer = false,
                            startPadType = padType,
                            ammo = padAmmo,
                            padAmmo = padAmmo,
                        )
                    } else {
                        sink += tankActor(
                            cx = obj.x,
                            cy = obj.y,
                            playerIndex = nextPlayerIndex++,
                            bodyDirection = props.int("direction") ?: 0,
                            turretDirection = props.int("direction") ?: 0,
                            tankType = props.int("tankType") ?: 0,
                            armor = props.int("armor") ?: DEFAULT_ARMOR,
                            fuel = props.int("fuel") ?: LEGACY_MAX_FUEL,
                            ammo = props.intList("ammo"),
                            power = props.intList("power"),
                            lives = props.int("lives") ?: 1,
                            team = 0,
                            computer = false,
                        )
                    }
                }

                ObjectKinds.AI_TANK -> {
                    // Legacy map tank. Human-drivable tanks (contr.computer == 0) are always on the
                    // good side; which one a player drives is decided per mode at match start.
                    val computer = props["computer"] != "0"
                    val good = props["good"] == "1" || !computer
                    sink += tankActor(
                        cx = obj.x,
                        cy = obj.y,
                        playerIndex = -1,
                        bodyDirection = props.int("direction") ?: 0,
                        turretDirection = props.int("direction") ?: 0,
                        tankType = props.int("tankType") ?: 0,
                        armor = props.int("armor") ?: DEFAULT_ARMOR,
                        ammo = props.intList("ammo"),
                        power = props.intList("power"),
                        lives = props.int("lives") ?: 1,
                        team = if (good) 0 else 1,
                        computer = computer,
                        controllerId = props.int("controllerId") ?: -1,
                        ref = props.int("ref") ?: 0,
                        goalContribution = goalContribution,
                        aiAttackTanks = props.int("attackTanks") ?: 100,
                        aiActivationRadius = props.int("activationRadius") ?: -1,
                        aiActivator = props.int("activator") ?: 0,
                        aiStayActive = props.int("stayActive") == 1,
                        aiActive = props.int("active") == 1,
                        aiAttackAt = props.int("attackAt") ?: -1,
                        aiAttackGoals = props.int("attackGoals") ?: 60,
                        aiWaveDelay = props.int("waveDelay") ?: -1,
                        aiFireDelay = props.int("fireDelay") ?: -1,
                        wayType = props.int("wayType") ?: 0,
                        wayCurrent = props.int("wayCurrent") ?: 0,
                        wayPoints = props["waypoints"].orEmpty().split(';').filter { it.isNotBlank() }
                            .flatMap { it.split(',').map(String::trim).map(String::toInt) },
                    )
                }

                ObjectKinds.TURRET -> {
                    // Legacy `tur.shootat` 2: a turret that only shoots evil vehicles is on the good side.
                    val shootAt = props.int("shootAt") ?: 1
                    sink += turretActor(
                        cx = obj.x,
                        cy = obj.y,
                        turretDirection = props.int("direction") ?: 0,
                        team = if (shootAt == 2) 0 else 1,
                        turretType = props.int("turretType") ?: 0,
                        shootAt = shootAt,
                        fixed = props["fixed"] == "true",
                        forbidDirections = props.int("forbidDirections") ?: 0,
                        armor = props.int("armor") ?: Turret.DEFAULT_TURRET_ARMOR,
                        goalContribution = goalContribution,
                        power = props.int("power"),
                        delay = props.int("delay"),
                        radius = props.int("radius"),
                        ownerRef = props.int("ownerRef") ?: 0,
                    )
                }

                ObjectKinds.GOAL -> {
                    sink += goalActor(
                        cx = obj.x,
                        cy = obj.y,
                        who = props.int("who") ?: 0,
                        contribution = props.int("contribution") ?: props.int("goalContribution") ?: 100,
                        radius = props.int("radius") ?: GOAL_FOOTPRINT,
                        targetRef = props.int("targetRef") ?: 0,
                    )
                }

                ObjectKinds.FLAG -> {
                    sink += flagActor(
                        cx = obj.x,
                        cy = obj.y,
                        flagType = props.int("flagType") ?: 0,
                        number = props.int("number") ?: 0,
                    )
                }

                ObjectKinds.PRODUCT -> {
                    sink += productActor(
                        cx = obj.x,
                        cy = obj.y,
                        productType = props.int("productType") ?: 0,
                        price = props.int("price") ?: 1,
                    )
                }

                ObjectKinds.LOCK -> {
                    sink += lockActor(
                        cx = obj.x,
                        cy = obj.y,
                        activation = props.int("activation") ?: 0,
                        target = props.int("target") ?: 0,
                        lockX = props.int("lockX") ?: 0,
                        lockY = props.int("lockY") ?: 0,
                        goalContribution = goalContribution,
                    )
                }

                ObjectKinds.WARP -> {
                    sink += warpActor(
                        cx = obj.x,
                        cy = obj.y,
                        targetX = props.int("targetX") ?: 0,
                        targetY = props.int("targetY") ?: 0,
                    )
                }

                ObjectKinds.DESTROYER -> {
                    sink += destroyerActor(
                        cx = obj.x,
                        cy = obj.y,
                        radius = props.int("radius") ?: 0,
                        what = props.int("what") ?: 3,
                        immediate = props.int("immediate")?.let { it != 0 } ?: false,
                        goalContribution = goalContribution,
                    )
                }

                ObjectKinds.ENFORCER -> {
                    sink += enforcerActor(
                        cx = obj.x,
                        cy = obj.y,
                        radius = props.int("radius") ?: 0,
                        weapon = props.int("weapon") ?: 0,
                        delay = props.int("delay") ?: 0,
                        good = props.int("good")?.let { it != 0 } ?: false,
                        // The legacy struct calls this field `enf_evil`; older fixtures used `bad`.
                        bad = (props.int("evil") ?: props.int("bad"))?.let { it != 0 } ?: false,
                        fire = props.int("fire")?.let { it != 0 } ?: true,
                        change = props.int("change")?.let { it != 0 } ?: false,
                    )
                }

                ObjectKinds.MINE -> {
                    // Legacy prepare_mine(): map mines are armed from the start; `mi.power` is the
                    // light mine's blast (the editor writes 7).
                    sink += Mine(
                        Mine.State(
                            body = PointBody(position = ScenePoint(obj.x.toFloat(), obj.y.toFloat())),
                            ownerActorId = 0L,
                            damage = props.int("minePower") ?: LIGHT_MINE_DAMAGE,
                            radius = LIGHT_MINE_RADIUS_PX,
                            activationTicksRemaining = 0,
                            heavy = props.int("mineType") == 1,
                        ),
                    )
                }

                ObjectKinds.TRAIN, ObjectKinds.WAGON -> {
                    sink += trainActor(obj.x, obj.y, isEngine = obj.kind == ObjectKinds.TRAIN, props, goalContribution)
                }

                ObjectKinds.ZEPPELIN -> {
                    sink += zeppelinActor(cx = obj.x, cy = obj.y)
                }

                ObjectKinds.B52 -> {
                    sink += b52Actor(
                        cx = obj.x,
                        cy = obj.y,
                        heading = (props.int("heading") ?: 0).mod(4),
                        armor = props.int("armor")?.takeIf { it > 0 } ?: 30,
                        goalContribution = goalContribution,
                    )
                }

                // Men and bonuses have no runtime actor yet.
                else -> Unit
            }
        }
    }

    private fun Map<String, String>.int(key: String): Int? = this[key]?.toIntOrNull()

    private fun Map<String, String>.intList(key: String): List<Int>? =
        this[key]?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.isNotEmpty() }

    private fun tankActor(
        cx: Int,
        cy: Int,
        playerIndex: Int,
        bodyDirection: Int,
        turretDirection: Int,
        tankType: Int,
        armor: Int,
        fuel: Int = LEGACY_MAX_FUEL,
        /** Legacy `weap.nw` / `weap.power` from the map; null means the vehicle's defaults (legacy make_tank()). */
        ammo: List<Int>? = null,
        power: List<Int>? = null,
        lives: Int,
        team: Int,
        computer: Boolean = true,
        controllerId: Int = -1,
        ref: Int = 0,
        goalContribution: Int = 0,
        startPadType: Int = -1,
        padAmmo: List<Int>? = null,
        aiAttackTanks: Int = 100,
        aiActivationRadius: Int = -1,
        aiActivator: Int = 0,
        aiStayActive: Boolean = false,
        aiActive: Boolean = false,
        aiAttackAt: Int = -1,
        aiAttackGoals: Int = 60,
        aiWaveDelay: Int = -1,
        aiFireDelay: Int = -1,
        wayType: Int = 0,
        wayCurrent: Int = 0,
        wayPoints: List<Int> = emptyList(),
    ): Tank = Tank(
        Tank.State(
            body = BoxBody(
                position = ScenePoint((cx - TANK_FOOTPRINT / 2).toFloat(), (cy - TANK_FOOTPRINT / 2).toFloat()),
                size = SceneExtent(TANK_FOOTPRINT.toFloat(), TANK_FOOTPRINT.toFloat()),
            ),
            bodyDirection = bodyDirection,
            turretDirection = turretDirection,
            playerIndex = playerIndex,
            tankType = tankType,
            armor = armor,
            fuel = fuel,
            ammo = ammo ?: LegacyVehicles.spec(tankType).ammo,
            power = power ?: LegacyVehicles.spec(tankType).power,
            currentWeapon = LegacyVehicles.startingWeapon(LegacyVehicles.spec(tankType), ammo ?: LegacyVehicles.spec(tankType).ammo),
            maxArmor = LegacyVehicles.spec(tankType).armor.coerceAtLeast(armor),
            lives = lives,
            team = team,
            computer = computer,
            controllerId = controllerId,
            ref = ref,
            goalContribution = goalContribution,
            startPadType = startPadType,
            padAmmo = padAmmo,
            aiAttackTanks = aiAttackTanks,
            aiActivationRadius = aiActivationRadius,
            aiActivator = aiActivator,
            aiStayActive = aiStayActive,
            aiActive = aiActive,
            aiAttackAt = aiAttackAt,
            aiAttackGoals = aiAttackGoals,
            aiWaveDelay = aiWaveDelay,
            aiFireDelay = aiFireDelay,
            wayType = wayType,
            wayCurrent = wayCurrent,
            wayPoints = wayPoints,
        ),
    )

    private fun turretActor(
        cx: Int,
        cy: Int,
        turretDirection: Int,
        team: Int,
        armor: Int,
        goalContribution: Int,
        power: Int? = null,
        delay: Int? = null,
        radius: Int? = null,
        ownerRef: Int = 0,
        turretType: Int = 0,
        shootAt: Int = 1,
        fixed: Boolean = false,
        forbidDirections: Int = 0,
    ): Turret =
        Turret(
            Turret.State(
                body = BoxBody(
                    position = ScenePoint((cx - TURRET_FOOTPRINT / 2).toFloat(), (cy - TURRET_FOOTPRINT / 2).toFloat()),
                    size = SceneExtent(TURRET_FOOTPRINT.toFloat(), TURRET_FOOTPRINT.toFloat()),
                ),
                turretDirection = turretDirection,
                team = team,
                armor = armor,
                goalContribution = goalContribution,
                power = power ?: Turret.State().power,
                delay = delay?.takeIf { it > 0 } ?: Turret.State().delay,
                radius = radius?.takeIf { it > 0 } ?: Turret.State().radius,
                ownerRef = ownerRef,
                turretType = turretType,
                shootAt = shootAt,
                fixed = fixed,
                forbidDirections = forbidDirections,
            ),
        )

    private fun goalActor(cx: Int, cy: Int, who: Int, contribution: Int, radius: Int, targetRef: Int = 0): Goal =
        Goal(
            Goal.State(
                body = BoxBody(
                    position = ScenePoint((cx - GOAL_FOOTPRINT / 2).toFloat(), (cy - GOAL_FOOTPRINT / 2).toFloat()),
                    size = SceneExtent(GOAL_FOOTPRINT.toFloat(), GOAL_FOOTPRINT.toFloat()),
                ),
                who = who,
                contribution = contribution,
                radius = radius,
                isClaimed = false,
                targetRef = targetRef,
            ),
        )

    private const val FLAG_FOOTPRINT: Int = 16
    private const val PRODUCT_FOOTPRINT: Int = 16
    private const val LOCK_FOOTPRINT: Int = 16
    private const val WARP_FOOTPRINT: Int = 16
    private const val DESTROYER_FOOTPRINT: Int = 16
    private const val ENFORCER_FOOTPRINT: Int = 16
    private const val TRAIN_FOOTPRINT: Int = 32
    private const val ZEPPELIN_FOOTPRINT: Int = 32
    private const val B52_FOOTPRINT: Int = 32

    private fun flagActor(cx: Int, cy: Int, flagType: Int, number: Int): Flag =
        Flag(
            Flag.State(
                body = BoxBody(
                    position = ScenePoint((cx - FLAG_FOOTPRINT / 2).toFloat(), (cy - FLAG_FOOTPRINT / 2).toFloat()),
                    size = SceneExtent(FLAG_FOOTPRINT.toFloat(), FLAG_FOOTPRINT.toFloat()),
                ),
                flagType = flagType,
                number = number,
                isCarried = false,
                carrierActorId = null,
                homeX = cx,
                homeY = cy,
            ),
        )

    private fun productActor(cx: Int, cy: Int, productType: Int, price: Int): Product =
        Product(
            Product.State(
                body = BoxBody(
                    position = ScenePoint((cx - PRODUCT_FOOTPRINT / 2).toFloat(), (cy - PRODUCT_FOOTPRINT / 2).toFloat()),
                    size = SceneExtent(PRODUCT_FOOTPRINT.toFloat(), PRODUCT_FOOTPRINT.toFloat()),
                ),
                productType = productType,
                price = price,
                isCollected = false,
            ),
        )

    private fun lockActor(cx: Int, cy: Int, activation: Int, target: Int, lockX: Int, lockY: Int, goalContribution: Int): Lock =
        Lock(
            Lock.State(
                body = BoxBody(
                    position = ScenePoint((cx - LOCK_FOOTPRINT / 2).toFloat(), (cy - LOCK_FOOTPRINT / 2).toFloat()),
                    size = SceneExtent(LOCK_FOOTPRINT.toFloat(), LOCK_FOOTPRINT.toFloat()),
                ),
                activation = activation,
                target = target,
                lockX = lockX,
                lockY = lockY,
                isFired = false,
                goalContribution = goalContribution,
            ),
        )

    private fun warpActor(cx: Int, cy: Int, targetX: Int, targetY: Int): Warp =
        Warp(
            Warp.State(
                body = BoxBody(
                    position = ScenePoint((cx - WARP_FOOTPRINT / 2).toFloat(), (cy - WARP_FOOTPRINT / 2).toFloat()),
                    size = SceneExtent(WARP_FOOTPRINT.toFloat(), WARP_FOOTPRINT.toFloat()),
                ),
                targetX = targetX,
                targetY = targetY,
            ),
        )

    private fun destroyerActor(cx: Int, cy: Int, radius: Int, what: Int, immediate: Boolean, goalContribution: Int): Destroyer =
        Destroyer(
            Destroyer.State(
                body = BoxBody(
                    position = ScenePoint((cx - DESTROYER_FOOTPRINT / 2).toFloat(), (cy - DESTROYER_FOOTPRINT / 2).toFloat()),
                    size = SceneExtent(DESTROYER_FOOTPRINT.toFloat(), DESTROYER_FOOTPRINT.toFloat()),
                ),
                radius = radius,
                what = what,
                immediate = immediate,
                isFired = false,
                goalContribution = goalContribution,
            ),
        )

    private fun enforcerActor(
        cx: Int,
        cy: Int,
        radius: Int,
        weapon: Int,
        delay: Int,
        good: Boolean,
        bad: Boolean,
        fire: Boolean,
        change: Boolean,
    ): Enforcer =
        Enforcer(
            Enforcer.State(
                body = BoxBody(
                    position = ScenePoint((cx - ENFORCER_FOOTPRINT / 2).toFloat(), (cy - ENFORCER_FOOTPRINT / 2).toFloat()),
                    size = SceneExtent(ENFORCER_FOOTPRINT.toFloat(), ENFORCER_FOOTPRINT.toFloat()),
                ),
                radius = radius,
                weapon = weapon,
                delay = delay,
                good = good,
                bad = bad,
                fire = fire,
                change = change,
            ),
        )

    /** Legacy `trainstruct` as saved in the map (see LegacyObjectParser.parseTrainProperties()). */
    private fun trainActor(cx: Int, cy: Int, isEngine: Boolean, props: Map<String, String>, goalContribution: Int): Train =
        Train(
            Train.State(
                body = BoxBody(
                    position = ScenePoint((cx - TRAIN_FOOTPRINT / 2).toFloat(), (cy - TRAIN_FOOTPRINT / 2).toFloat()),
                    size = SceneExtent(TRAIN_FOOTPRINT.toFloat(), TRAIN_FOOTPRINT.toFloat()),
                ),
                isEngine = isEngine,
                armor = props.int("armor") ?: if (isEngine) 60 else 40,
                alive = true,
                x = cx,
                y = cy,
                ref = props.int("ref") ?: 0,
                frontRef = props.int("frontRef") ?: 0,
                rearRef = props.int("rearRef") ?: 0,
                driving = props.int("driving")?.let { it != 0 } ?: isEngine,
                speed = props.int("speed") ?: 0,
                direction = props.int("direction") ?: 0,
                picture = props.int("picture") ?: if (isEngine) 0 else 1,
                smokes = props.int("smokes")?.let { it != 0 } ?: isEngine,
                railsStart = props["railsStart"]?.toFloatOrNull() ?: 0f,
                railsType = props.int("railsType") ?: 0,
                railsDir = props.int("railsDir") ?: 1,
                railsX = props.int("railsX") ?: cx,
                railsY = props.int("railsY") ?: cy,
                endType = props.int("endType") ?: 0,
                endDir = props.int("endDir") ?: 1,
                endX = props.int("endX") ?: 0,
                endY = props.int("endY") ?: 0,
                goalContribution = goalContribution,
            ),
        )

    private fun zeppelinActor(cx: Int, cy: Int): Zeppelin =
        Zeppelin(
            Zeppelin.State(
                body = BoxBody(
                    position = ScenePoint((cx - ZEPPELIN_FOOTPRINT / 2).toFloat(), (cy - ZEPPELIN_FOOTPRINT / 2).toFloat()),
                    size = SceneExtent(ZEPPELIN_FOOTPRINT.toFloat(), ZEPPELIN_FOOTPRINT.toFloat()),
                ),
                alive = true,
                x = cx,
                y = cy,
            ),
        )

    private fun b52Actor(cx: Int, cy: Int, heading: Int, armor: Int, goalContribution: Int): B52 =
        B52(
            B52.State(
                body = BoxBody(
                    position = ScenePoint((cx - B52_FOOTPRINT / 2).toFloat(), (cy - B52_FOOTPRINT / 2).toFloat()),
                    size = SceneExtent(B52_FOOTPRINT.toFloat(), B52_FOOTPRINT.toFloat()),
                ),
                armor = armor,
                alive = true,
                x = cx,
                y = cy,
                bombCount = 0,
                heading = heading,
                goalContribution = goalContribution,
            ),
        )
}
