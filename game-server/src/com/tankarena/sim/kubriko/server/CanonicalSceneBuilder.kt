package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.actor.body.PointBody
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.serialization.Serializable
import com.pandulapeter.kubriko.serialization.SerializableMetadata
import com.pandulapeter.kubriko.serialization.SerializationManager
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.sim.kubriko.server.legacy.CanonicalMapDefinition
import com.tankarena.sim.kubriko.server.legacy.ObjectKinds

private const val TANK_FOOTPRINT: Int = LEGACY_TILE_SIZE - 4
private const val TURRET_FOOTPRINT: Int = LEGACY_TILE_SIZE
private const val GOAL_FOOTPRINT: Int = 16
private const val DEFAULT_ARMOR: Int = 100
/** `make_player()` in src/objects/player.c gives dual-mode pads four lives. */
private const val LEGACY_PAD_LIVES: Int = 4
private const val LIGHT_MINE_DAMAGE: Int = 7
private const val HEAVY_MINE_DAMAGE: Int = 20

object CanonicalSceneBuilder {

    fun buildActors(map: CanonicalMapDefinition): List<Serializable<*>> {
        val actors = mutableListOf<Serializable<*>>()
        appendSolidWalls(map, actors)
        appendBoundaryWalls(map, actors)
        appendAuthoredObjects(map, actors)
        return actors
    }

    fun buildSceneJson(
        map: CanonicalMapDefinition,
        serializationManager: SerializationManager<SerializableMetadata<*>, Serializable<*>>,
    ): String = serializationManager.serializeActors(buildActors(map))

    private fun appendSolidWalls(map: CanonicalMapDefinition, sink: MutableList<Serializable<*>>) {
        val width = map.metadata.widthTiles
        val height = map.metadata.heightTiles
        for (y in 0 until height) {
            for (x in 0 until width) {
                val solid = map.layers.solid.getOrElse(x + y * width) { -1 }
                if (solid < 0) continue
                sink += wallActor(
                    cx = x * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    cy = y * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2,
                    width = LEGACY_TILE_SIZE,
                    height = LEGACY_TILE_SIZE,
                )
            }
        }
    }

    private fun appendBoundaryWalls(map: CanonicalMapDefinition, sink: MutableList<Serializable<*>>) {
        val w = map.metadata.widthTiles * LEGACY_TILE_SIZE
        val h = map.metadata.heightTiles * LEGACY_TILE_SIZE
        val t = LEGACY_TILE_SIZE
        sink += wallActor(cx = w / 2, cy = -t / 2, width = w + 2 * t, height = t)
        sink += wallActor(cx = w / 2, cy = h + t / 2, width = w + 2 * t, height = t)
        sink += wallActor(cx = -t / 2, cy = h / 2, width = t, height = h + 2 * t)
        sink += wallActor(cx = w + t / 2, cy = h / 2, width = t, height = h + 2 * t)
    }

    private fun appendAuthoredObjects(map: CanonicalMapDefinition, sink: MutableList<Serializable<*>>) {
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
                        sink += tankActor(
                            cx = obj.x,
                            cy = obj.y,
                            playerIndex = padType and 1,
                            bodyDirection = direction,
                            turretDirection = direction,
                            tankType = 0,
                            armor = props.int("armor") ?: DEFAULT_ARMOR,
                            lives = (props.int("lives") ?: 0).takeIf { it > 0 } ?: LEGACY_PAD_LIVES,
                            team = 0,
                            computer = false,
                            startPadType = padType,
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
                        lives = props.int("lives") ?: 1,
                        team = if (good) 0 else 1,
                        computer = computer,
                        controllerId = props.int("controllerId") ?: -1,
                        goalContribution = goalContribution,
                        aiAttackTanks = props.int("attackTanks") ?: 100,
                        aiActivationRadius = props.int("activationRadius") ?: -1,
                        aiActivator = props.int("activator") ?: 0,
                        aiStayActive = props.int("stayActive") == 1,
                        aiActive = props.int("active") == 1,
                    )
                }

                ObjectKinds.TURRET -> {
                    sink += turretActor(
                        cx = obj.x,
                        cy = obj.y,
                        turretDirection = props.int("direction") ?: 0,
                        team = 1,
                        armor = props.int("armor") ?: DEFAULT_ARMOR,
                        goalContribution = goalContribution,
                    )
                }

                ObjectKinds.GOAL -> {
                    sink += goalActor(
                        cx = obj.x,
                        cy = obj.y,
                        who = props.int("who") ?: 0,
                        contribution = props.int("contribution") ?: props.int("goalContribution") ?: 100,
                        radius = props.int("radius") ?: GOAL_FOOTPRINT,
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
                        immediate = props.int("immediate") == 1,
                    )
                }

                ObjectKinds.ENFORCER -> {
                    sink += enforcerActor(
                        cx = obj.x,
                        cy = obj.y,
                        radius = props.int("radius") ?: 0,
                        weapon = props.int("weapon") ?: 0,
                        delay = props.int("delay") ?: 0,
                        good = props.int("good") == 1,
                        // The legacy struct calls this field `enf_evil`; older fixtures used `bad`.
                        bad = (props.int("evil") ?: props.int("bad")) == 1,
                    )
                }

                ObjectKinds.MINE -> {
                    val heavy = props.int("mineType") == 1
                    sink += ServerMineActor(
                        ServerMineActor.State(
                            body = PointBody(initialPosition = SceneOffset(obj.x.toFloat().sceneUnit, obj.y.toFloat().sceneUnit)),
                            ownerActorId = 0L,
                            damage = if (heavy) HEAVY_MINE_DAMAGE else LIGHT_MINE_DAMAGE,
                            radius = LIGHT_MINE_RADIUS_PX,
                            activationTicksRemaining = 0,
                        ),
                    )
                }

                ObjectKinds.TRAIN, ObjectKinds.WAGON -> {
                    sink += trainActor(
                        cx = obj.x,
                        cy = obj.y,
                        isEngine = obj.kind == ObjectKinds.TRAIN,
                    )
                }

                ObjectKinds.ZEPPELIN -> {
                    sink += zeppelinActor(cx = obj.x, cy = obj.y)
                }

                ObjectKinds.B52 -> {
                    sink += b52Actor(cx = obj.x, cy = obj.y)
                }

                // Men and bonuses have no runtime actor yet.
                else -> Unit
            }
        }
    }

    private fun Map<String, String>.int(key: String): Int? = this[key]?.toIntOrNull()

    private fun wallActor(cx: Int, cy: Int, width: Int, height: Int): ServerWallActor =
        ServerWallActor(
            ServerWallActor.State(
                body = BoxBody(
                    initialPosition = SceneOffset((cx - width / 2).toFloat().sceneUnit, (cy - height / 2).toFloat().sceneUnit),
                    initialSize = SceneSize(width.toFloat().sceneUnit, height.toFloat().sceneUnit),
                ),
            ),
        )

    private fun tankActor(
        cx: Int,
        cy: Int,
        playerIndex: Int,
        bodyDirection: Int,
        turretDirection: Int,
        tankType: Int,
        armor: Int,
        fuel: Int = LEGACY_MAX_FUEL,
        lives: Int,
        team: Int,
        computer: Boolean = true,
        controllerId: Int = -1,
        goalContribution: Int = 0,
        startPadType: Int = -1,
        aiAttackTanks: Int = 100,
        aiActivationRadius: Int = -1,
        aiActivator: Int = 0,
        aiStayActive: Boolean = false,
        aiActive: Boolean = false,
    ): ServerTankActor = ServerTankActor(
        ServerTankActor.State(
            body = BoxBody(
                initialPosition = SceneOffset((cx - TANK_FOOTPRINT / 2).toFloat().sceneUnit, (cy - TANK_FOOTPRINT / 2).toFloat().sceneUnit),
                initialSize = SceneSize(TANK_FOOTPRINT.toFloat().sceneUnit, TANK_FOOTPRINT.toFloat().sceneUnit),
            ),
            bodyDirection = bodyDirection,
            turretDirection = turretDirection,
            playerIndex = playerIndex,
            tankType = tankType,
            armor = armor,
            fuel = fuel,
            lives = lives,
            team = team,
            computer = computer,
            controllerId = controllerId,
            goalContribution = goalContribution,
            startPadType = startPadType,
            aiAttackTanks = aiAttackTanks,
            aiActivationRadius = aiActivationRadius,
            aiActivator = aiActivator,
            aiStayActive = aiStayActive,
            aiActive = aiActive,
        ),
    )

    private fun turretActor(cx: Int, cy: Int, turretDirection: Int, team: Int, armor: Int, goalContribution: Int): ServerTurretActor =
        ServerTurretActor(
            ServerTurretActor.State(
                body = BoxBody(
                    initialPosition = SceneOffset((cx - TURRET_FOOTPRINT / 2).toFloat().sceneUnit, (cy - TURRET_FOOTPRINT / 2).toFloat().sceneUnit),
                    initialSize = SceneSize(TURRET_FOOTPRINT.toFloat().sceneUnit, TURRET_FOOTPRINT.toFloat().sceneUnit),
                ),
                turretDirection = turretDirection,
                team = team,
                armor = armor,
                goalContribution = goalContribution,
            ),
        )

    private fun goalActor(cx: Int, cy: Int, who: Int, contribution: Int, radius: Int): ServerGoalActor =
        ServerGoalActor(
            ServerGoalActor.State(
                body = BoxBody(
                    initialPosition = SceneOffset((cx - GOAL_FOOTPRINT / 2).toFloat().sceneUnit, (cy - GOAL_FOOTPRINT / 2).toFloat().sceneUnit),
                    initialSize = SceneSize(GOAL_FOOTPRINT.toFloat().sceneUnit, GOAL_FOOTPRINT.toFloat().sceneUnit),
                ),
                who = who,
                contribution = contribution,
                radius = radius,
                isClaimed = false,
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

    private fun flagActor(cx: Int, cy: Int, flagType: Int, number: Int): ServerFlagActor =
        ServerFlagActor(
            ServerFlagActor.State(
                body = BoxBody(
                    initialPosition = SceneOffset((cx - FLAG_FOOTPRINT / 2).toFloat().sceneUnit, (cy - FLAG_FOOTPRINT / 2).toFloat().sceneUnit),
                    initialSize = SceneSize(FLAG_FOOTPRINT.toFloat().sceneUnit, FLAG_FOOTPRINT.toFloat().sceneUnit),
                ),
                flagType = flagType,
                number = number,
                isCarried = false,
                carrierActorId = null,
                homeX = cx,
                homeY = cy,
            ),
        )

    private fun productActor(cx: Int, cy: Int, productType: Int, price: Int): ServerProductActor =
        ServerProductActor(
            ServerProductActor.State(
                body = BoxBody(
                    initialPosition = SceneOffset((cx - PRODUCT_FOOTPRINT / 2).toFloat().sceneUnit, (cy - PRODUCT_FOOTPRINT / 2).toFloat().sceneUnit),
                    initialSize = SceneSize(PRODUCT_FOOTPRINT.toFloat().sceneUnit, PRODUCT_FOOTPRINT.toFloat().sceneUnit),
                ),
                productType = productType,
                price = price,
                isCollected = false,
            ),
        )

    private fun lockActor(cx: Int, cy: Int, activation: Int, target: Int, lockX: Int, lockY: Int): ServerLockActor =
        ServerLockActor(
            ServerLockActor.State(
                body = BoxBody(
                    initialPosition = SceneOffset((cx - LOCK_FOOTPRINT / 2).toFloat().sceneUnit, (cy - LOCK_FOOTPRINT / 2).toFloat().sceneUnit),
                    initialSize = SceneSize(LOCK_FOOTPRINT.toFloat().sceneUnit, LOCK_FOOTPRINT.toFloat().sceneUnit),
                ),
                activation = activation,
                target = target,
                lockX = lockX,
                lockY = lockY,
                isFired = false,
            ),
        )

    private fun warpActor(cx: Int, cy: Int, targetX: Int, targetY: Int): ServerWarpActor =
        ServerWarpActor(
            ServerWarpActor.State(
                body = BoxBody(
                    initialPosition = SceneOffset((cx - WARP_FOOTPRINT / 2).toFloat().sceneUnit, (cy - WARP_FOOTPRINT / 2).toFloat().sceneUnit),
                    initialSize = SceneSize(WARP_FOOTPRINT.toFloat().sceneUnit, WARP_FOOTPRINT.toFloat().sceneUnit),
                ),
                targetX = targetX,
                targetY = targetY,
                cooldownTicks = 0,
            ),
        )

    private fun destroyerActor(cx: Int, cy: Int, radius: Int, what: Int, immediate: Boolean): ServerDestroyerActor =
        ServerDestroyerActor(
            ServerDestroyerActor.State(
                body = BoxBody(
                    initialPosition = SceneOffset((cx - DESTROYER_FOOTPRINT / 2).toFloat().sceneUnit, (cy - DESTROYER_FOOTPRINT / 2).toFloat().sceneUnit),
                    initialSize = SceneSize(DESTROYER_FOOTPRINT.toFloat().sceneUnit, DESTROYER_FOOTPRINT.toFloat().sceneUnit),
                ),
                radius = radius,
                what = what,
                immediate = immediate,
                isFired = false,
            ),
        )

    private fun enforcerActor(cx: Int, cy: Int, radius: Int, weapon: Int, delay: Int, good: Boolean, bad: Boolean): ServerEnforcerActor =
        ServerEnforcerActor(
            ServerEnforcerActor.State(
                body = BoxBody(
                    initialPosition = SceneOffset((cx - ENFORCER_FOOTPRINT / 2).toFloat().sceneUnit, (cy - ENFORCER_FOOTPRINT / 2).toFloat().sceneUnit),
                    initialSize = SceneSize(ENFORCER_FOOTPRINT.toFloat().sceneUnit, ENFORCER_FOOTPRINT.toFloat().sceneUnit),
                ),
                radius = radius,
                weapon = weapon,
                delay = delay,
                good = good,
                bad = bad,
            ),
        )

    private fun trainActor(cx: Int, cy: Int, isEngine: Boolean): ServerTrainActor =
        ServerTrainActor(
            ServerTrainActor.State(
                body = BoxBody(
                    initialPosition = SceneOffset((cx - TRAIN_FOOTPRINT / 2).toFloat().sceneUnit, (cy - TRAIN_FOOTPRINT / 2).toFloat().sceneUnit),
                    initialSize = SceneSize(TRAIN_FOOTPRINT.toFloat().sceneUnit, TRAIN_FOOTPRINT.toFloat().sceneUnit),
                ),
                isEngine = isEngine,
                armor = if (isEngine) 60 else 40,
                alive = true,
                x = cx,
                y = cy,
            ),
        )

    private fun zeppelinActor(cx: Int, cy: Int): ServerZeppelinActor =
        ServerZeppelinActor(
            ServerZeppelinActor.State(
                body = BoxBody(
                    initialPosition = SceneOffset((cx - ZEPPELIN_FOOTPRINT / 2).toFloat().sceneUnit, (cy - ZEPPELIN_FOOTPRINT / 2).toFloat().sceneUnit),
                    initialSize = SceneSize(ZEPPELIN_FOOTPRINT.toFloat().sceneUnit, ZEPPELIN_FOOTPRINT.toFloat().sceneUnit),
                ),
                alive = true,
                x = cx,
                y = cy,
            ),
        )

    private fun b52Actor(cx: Int, cy: Int): ServerB52Actor =
        ServerB52Actor(
            ServerB52Actor.State(
                body = BoxBody(
                    initialPosition = SceneOffset((cx - B52_FOOTPRINT / 2).toFloat().sceneUnit, (cy - B52_FOOTPRINT / 2).toFloat().sceneUnit),
                    initialSize = SceneSize(B52_FOOTPRINT.toFloat().sceneUnit, B52_FOOTPRINT.toFloat().sceneUnit),
                ),
                armor = 30,
                alive = true,
                x = cx,
                y = cy,
                bombCount = 0,
            ),
        )
}
