package com.tankarena.sim.map

import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.sim.B52
import com.tankarena.sim.Body
import com.tankarena.sim.BoxBody
import com.tankarena.sim.CanonicalSceneBuilder
import com.tankarena.sim.Destroyer
import com.tankarena.sim.Enforcer
import com.tankarena.sim.Flag
import com.tankarena.sim.Goal
import com.tankarena.sim.LegacyVehicles
import com.tankarena.sim.Lock
import com.tankarena.sim.Mine
import com.tankarena.sim.PointBody
import com.tankarena.sim.Product
import com.tankarena.sim.SceneObject
import com.tankarena.sim.ScenePoint
import com.tankarena.sim.Tank
import com.tankarena.sim.Train
import com.tankarena.sim.Turret
import com.tankarena.sim.VehicleKind
import com.tankarena.sim.Warp
import com.tankarena.sim.Zeppelin
import com.tankarena.sim.legacy.AuthoredObject
import com.tankarena.sim.legacy.ObjectKinds

/** An object of a map being edited: the game's own saved state of it. */
typealias MapObject = SceneObject.State<*>

/** The values a property takes. */
sealed interface PropertyValues {
    /** [min]..[max] in steps of [step]; [format] shows a value (seconds, pixels, …). */
    data class Range(val min: Int, val max: Int, val step: Int, val format: (Int) -> String = { "$it" }) : PropertyValues

    /** One of [labels], by index. */
    data class Choice(val labels: List<String>) : PropertyValues

    /** 0 off, 1 on. */
    data object Toggle : PropertyValues
}

/** One setting of an object, as the original editor's menus offer it. */
class ObjectProperty(
    val label: String,
    val values: PropertyValues,
    val value: Int,
    /** The object with the property set to a new value. */
    val with: (Int) -> MapObject,
) {
    val text: String
        get() = when (val v = values) {
            is PropertyValues.Range -> v.format(value)
            is PropertyValues.Choice -> v.labels.getOrElse(value) { "?" }
            PropertyValues.Toggle -> if (value != 0) "yes" else "no"
        }

    /** The next value up ([up]) or down, wrapping round for choices and toggles. */
    fun stepped(up: Boolean): Int = when (val v = values) {
        is PropertyValues.Range -> (value + if (up) v.step else -v.step).coerceIn(v.min, v.max)
        is PropertyValues.Choice -> (value + if (up) 1 else -1).mod(v.labels.size)
        PropertyValues.Toggle -> 1 - value
    }
}

/** What a click on the map sets for the selected object, besides its position. */
enum class ObjectPoint(val label: String) {
    WARP_EXIT("warp exit"),
    LOCK_KEY("what the lock waits for"),
    WAYPOINT("waypoint"),
    GOAL_TARGET("the object to reach"),
    FORBIDDEN_DIRECTION("forbidden direction"),
}

/**
 * The map editor's object rules (ADR 0021, legacy `src/edit/`): what can be added, where an object
 * is, how it moves and turns, and the properties each kind has.
 */
object MapObjects {
    private fun seconds(value: Int) = if (value < 0) "default" else "%.1f s".format(value / 100f)
    private fun pixels(value: Int) = if (value < 0) "default" else "$value px"

    /** Legacy `weapon_mnu`: the weapons a vehicle carries, by `weap.nw` index (the flame thrower is not set here). */
    val WEAPONS: List<Triple<String, Int, PropertyValues.Range>> = listOf(
        Triple("Main gun", 0, PropertyValues.Range(0, 100, 1)),
        Triple("Chain gun", 1, PropertyValues.Range(0, 5000, 100)),
        Triple("Mines", 3, PropertyValues.Range(0, 50, 1)),
        Triple("Rockets", 4, PropertyValues.Range(0, 50, 1)),
        Triple("Mortars", 5, PropertyValues.Range(0, 50, 1)),
        Triple("A-bombs", 6, PropertyValues.Range(0, 50, 1)),
        Triple("Men with chain guns", 7, PropertyValues.Range(0, 50, 1)),
        Triple("Men with flame throwers", 8, PropertyValues.Range(0, 50, 1)),
        Triple("Smoke", 9, PropertyValues.Range(0, 50, 1)),
        Triple("Cloak", 10, PropertyValues.Range(0, 50, 1)),
        Triple("Speed-up", 11, PropertyValues.Range(0, 50, 1)),
    )

    val TURRET_NAMES = listOf(
        "Big round gray", "Rocket launcher", "Big round yellow", "Chain turret", "Small round gray",
        "Shielded gray", "Square yellow", "Small yellow", "Ship turret",
    )

    /** Legacy `ti[]`: each turret kind's delay, power, radius, whom it shoots at. */
    private val TURRET_DEFAULTS = listOf(
        listOf(150, 5, 200, 3), listOf(200, 5, 200, 4), listOf(100, 5, 200, 1), listOf(10, 1, 200, 1),
        listOf(150, 5, 200, 1), listOf(150, 5, 200, 1), listOf(150, 5, 200, 1), listOf(150, 5, 200, 1),
        listOf(150, 5, 200, 1),
    )

    val WAGON_NAMES = listOf(
        "Blue roof", "Empty", "With boxes", "With fuel", "With mud", "Brown roof", "With missiles", "With turret",
    )

    val PAD_NAMES = listOf("Tank P1", "Tank P2", "Chopper P1", "Chopper P2", "Plane P1", "Plane P2", "Race P1", "Race P2")

    private val SHOOT_AT = listOf("All vehicles", "Good vehicles", "Evil vehicles", "All but player 1", "All but player 2")
    private val ENFORCER_WEAPONS = listOf(
        "Main", "Chain gun", "Flame thrower", "Mines", "Rockets", "Mortars", "A-bombs", "Men with chain guns",
        "Men with flame throwers", "Smoke", "Cloak", "Speed-up",
    )

    /** Something the editor can add: [create] builds it with its centre on a map point. */
    class Template(val group: String, val label: String, val create: (x: Int, y: Int, objects: List<MapObject>) -> MapObject?)

    /** Legacy `addobj_mnu` and below. */
    val TEMPLATES: List<Template> = buildList {
        val userTanks = listOf("Standard", "Devastator", "Mouse", "Crocodile", "Fairchild", "Chopper", "Race tank", "Losers tank")
        for ((i, name) in userTanks.withIndex()) add(Template("Player vehicle", name) { x, y, objects -> vehicle(i, x, y, player = true, objects) })
        for (i in 8 until 22) add(Template("Computer vehicle", LegacyVehicles.spec(i).name.lowercase().replaceFirstChar { it.uppercase() }) { x, y, objects -> vehicle(i, x, y, player = false, objects) })
        add(Template("Player vehicle", "Shopping car") { x, y, objects -> vehicle(22, x, y, player = true, objects) })
        for ((i, name) in PAD_NAMES.withIndex()) add(Template("Two players: start", name) { x, y, _ -> build(ObjectKinds.PLAYER_START, x, y, "startType" to "$i") })
        // Extended (ADR 0027): pads of players 3 and 4, on their side (3 with player 1, 4 with 2).
        for (player in 2..3) for (kind in 0..3) {
            val name = listOf("Tank", "Chopper", "Plane", "Race")[kind] + " P${player + 1}"
            add(Template("Teams (Extended): start", name) { x, y, _ ->
                (build(ObjectKinds.PLAYER_START, x, y, "startType" to "${kind * 2 + (player and 1)}") as? Tank.State)?.copy(padPlayer = player)
            })
        }
        for (i in 0..1) add(Template("Two players: start", "Flag P${i + 1}") { x, y, objects ->
            build(ObjectKinds.FLAG, x, y, "flagType" to "$i", "number" to "${objects.count { it is Flag.State && it.flagType == i }}")
        })
        for ((i, name) in TURRET_NAMES.withIndex()) add(Template("Turret", name) { x, y, _ -> turret(i, x, y) })
        add(Template("Train", "Locomotive") { x, y, objects -> train(x / LEGACY_TILE_SIZE, y / LEGACY_TILE_SIZE, 0, 0, null, objects) })
        add(Template("Other", "B52") { x, y, _ -> build(ObjectKinds.B52, x, y) })
        add(Template("Mine", "Light mine") { x, y, _ -> build(ObjectKinds.MINE, x, y, "minePower" to "7") })
        add(Template("Mine", "Heavy mine") { x, y, _ -> build(ObjectKinds.MINE, x, y, "mineType" to "1", "minePower" to "7") })
        add(Template("Other", "Warp") { x, y, _ -> build(ObjectKinds.WARP, x, y, "targetX" to "$x", "targetY" to "$y") })
        add(Template("Other", "Lock") { x, y, _ -> build(ObjectKinds.LOCK, x, y, "lockX" to "$x", "lockY" to "$y") })
        add(Template("Other", "Goal") { x, y, _ -> build(ObjectKinds.GOAL, x, y, "radius" to "30", "contribution" to "0") })
        add(Template("Other", "Destroyer") { x, y, _ -> build(ObjectKinds.DESTROYER, x, y, "radius" to "30", "immediate" to "1", "what" to "3") })
        add(Template("Other", "Enforcer") { x, y, _ ->
            build(ObjectKinds.ENFORCER, x, y, "radius" to "30", "weapon" to "6", "delay" to "500", "evil" to "1", "fire" to "1")
        })
        add(Template("Other", "Product") { x, y, _ -> build(ObjectKinds.PRODUCT, x, y, "productType" to "0", "price" to "1") })
    }

    private fun build(kind: String, x: Int, y: Int, vararg properties: Pair<String, String>): MapObject? =
        CanonicalSceneBuilder.buildObject(AuthoredObject("editor", kind, x, y, properties.toMap()))?.save()

    /** Legacy make_tank() as the editor calls it: player-driven (`control` 1) or a computer's (-1). */
    private fun vehicle(type: Int, x: Int, y: Int, player: Boolean, objects: List<MapObject>): MapObject? {
        val kind = LegacyVehicles.spec(type).kind
        return build(
            ObjectKinds.AI_TANK, x, y,
            "tankType" to "$type",
            "computer" to if (player) "0" else "1",
            "good" to if (player || kind == VehicleKind.CAR) "1" else "0",
            "controllerId" to if (player) "${freeControllerId(objects)}" else "-1",
            "attackAt" to if (kind == VehicleKind.CAR) "0" else "150",
            "attackTanks" to if (kind == VehicleKind.CAR) "0" else "100",
            "attackGoals" to "60",
            "activationRadius" to "250",
            "stayActive" to if (kind == VehicleKind.PLANE) "1" else "0",
        )
    }

    /** Legacy find_free_id(): the lowest of 1..9 no other vehicle has. */
    fun freeControllerId(objects: List<MapObject>, except: MapObject? = null): Int {
        val used = objects.filter { it !== except }.mapNotNull { (it as? Tank.State)?.controllerId }.toSet()
        return (1 until 10).firstOrNull { it !in used } ?: 10
    }

    /** Legacy make_turret(x, y, 0, 4, type): facing north, with its kind's settings. */
    private fun turret(type: Int, x: Int, y: Int, direction: Int = 0, ownerRef: Int = 0): MapObject? {
        val (delay, power, radius, shootAt) = TURRET_DEFAULTS[type]
        return build(
            ObjectKinds.TURRET, x, y,
            "turretType" to "$type", "direction" to "$direction", "delay" to "$delay", "power" to "$power",
            "radius" to "$radius", "shootAt" to "$shootAt", "ownerRef" to "$ownerRef",
        )
    }

    /** A reference no object of the map has yet (legacy objects pointed at each other). */
    fun freeRef(objects: List<MapObject>): Int = (objects.maxOfOrNull { ref(it) } ?: 0).coerceAtLeast(0) + 1

    fun ref(state: MapObject): Int = when (state) {
        is Tank.State -> state.ref
        is Train.State -> state.ref
        else -> 0
    }

    /**
     * Legacy make_train() and train_change(): a locomotive ([picture] 0) or wagon (1..8) on tile
     * ([tileX], [tileY]) facing [direction] (0, 4, 8, 12), coupled behind [front].
     */
    fun train(tileX: Int, tileY: Int, picture: Int, direction: Int, front: Train.State?, objects: List<MapObject>): Train.State {
        val engine = picture == 0
        val t = LEGACY_TILE_SIZE
        // Where train_change() puts the car's top-left corner, and its rails.
        val (left, top) = when (direction) {
            0 -> tileX * t to tileY * t + 16
            4 -> tileX * t - 16 to tileY * t
            8 -> tileX * t to tileY * t - 16
            else -> tileX * t + 16 to tileY * t
        }
        val vertical = direction == 0 || direction == 8
        val railsDir = if (direction == 4 || direction == 8) 1 else -1
        val (endX, endY) = when (direction) {
            0 -> tileX to tileY - 1
            4 -> tileX + 1 to tileY
            8 -> tileX to tileY + 1
            else -> tileX - 1 to tileY
        }
        val cx = left + 16
        val cy = top + 16
        return Train.State(
            body = BoxBody(ScenePoint((cx - 16).toFloat(), (cy - 16).toFloat()), com.tankarena.sim.SceneExtent(32f, 32f)),
            isEngine = engine,
            armor = if (engine) 60 else 40,
            x = cx,
            y = cy,
            ref = freeRef(objects),
            frontRef = front?.ref ?: 0,
            driving = engine,
            direction = direction,
            picture = picture,
            smokes = engine,
            railsStart = if (railsDir == 1) -16f else 16f,
            railsType = if (vertical) 1 else 0,
            railsDir = railsDir,
            railsX = tileX * t + 16,
            railsY = tileY * t + 16,
            endType = if (vertical) 1 else 0,
            endDir = railsDir,
            endX = endX,
            endY = endY,
        )
    }

    /** A wagon of [picture] (1..8) coupled behind [front], on the tile behind it; with its turret when it has one. */
    fun wagon(front: Train.State, picture: Int, objects: List<MapObject>): List<MapObject> {
        val (tx, ty) = trainTile(front)
        val (bx, by) = when (front.direction) {
            0 -> tx to ty + 1
            4 -> tx - 1 to ty
            8 -> tx to ty - 1
            else -> tx + 1 to ty
        }
        val wagon = train(bx, by, picture, front.direction, front, objects)
        // Legacy make_turret(o->x, o->y, 2, 2, 5, o): wagon 8 carries a turret.
        val turret = if (picture == 8) turret(5, wagon.x, wagon.y, direction = 4, ownerRef = wagon.ref) else null
        return listOfNotNull(wagon, turret)
    }

    /** The tile a train car was put on (the inverse of [train]). */
    fun trainTile(state: Train.State): Pair<Int, Int> {
        val t = LEGACY_TILE_SIZE
        val left = state.x - 16
        val top = state.y - 16
        return when (state.direction) {
            0 -> left / t to (top - 16) / t
            4 -> (left + 16) / t to top / t
            8 -> left / t to (top + 16) / t
            else -> (left - 16) / t to top / t
        }
    }

    /** The object's centre on the map. */
    fun centre(state: MapObject): Pair<Int, Int> {
        val body = body(state) ?: return 0 to 0
        return when (body) {
            is BoxBody -> (body.position.x + body.size.width / 2).toInt() to (body.position.y + body.size.height / 2).toInt()
            is PointBody -> body.position.x.toInt() to body.position.y.toInt()
        }
    }

    fun body(state: MapObject): Body? = when (state) {
        is Tank.State -> state.body
        is Turret.State -> state.body
        is Goal.State -> state.body
        is Flag.State -> state.body
        is Product.State -> state.body
        is Lock.State -> state.body
        is Warp.State -> state.body
        is Destroyer.State -> state.body
        is Enforcer.State -> state.body
        is Train.State -> state.body
        is Zeppelin.State -> state.body
        is B52.State -> state.body
        is Mine.State -> state.body
        else -> null
    }

    private fun BoxBody.shifted(dx: Int, dy: Int) = BoxBody(ScenePoint(position.x + dx, position.y + dy), size, pivot, scale, rotation)

    /**
     * The object with its centre moved to ([x], [y]) (legacy new_position()); what goes with it moves
     * along (a vehicle's respawn point, a flag's home), what it points at stays. Train cars move by
     * whole tiles, as on rails.
     */
    fun movedTo(state: MapObject, x: Int, y: Int): MapObject {
        if (state is Train.State) {
            val placed = train(x / LEGACY_TILE_SIZE, y / LEGACY_TILE_SIZE, state.picture, state.direction, null, emptyList())
            return placed.copy(ref = state.ref, frontRef = state.frontRef, rearRef = state.rearRef, goalContribution = state.goalContribution, armor = state.armor)
        }
        val (cx, cy) = centre(state)
        val dx = x - cx
        val dy = y - cy
        return when (state) {
            is Tank.State -> state.copy(
                body = state.body.shifted(dx, dy),
                spawnX = if (state.spawnX >= 0) state.spawnX + dx else state.spawnX,
                spawnY = if (state.spawnY >= 0) state.spawnY + dy else state.spawnY,
            )
            is Turret.State -> state.copy(body = state.body.shifted(dx, dy))
            is Goal.State -> state.copy(body = state.body.shifted(dx, dy))
            is Flag.State -> state.copy(body = state.body.shifted(dx, dy), homeX = state.homeX + dx, homeY = state.homeY + dy)
            is Product.State -> state.copy(body = state.body.shifted(dx, dy))
            is Lock.State -> state.copy(body = state.body.shifted(dx, dy))
            is Warp.State -> state.copy(body = state.body.shifted(dx, dy))
            is Destroyer.State -> state.copy(body = state.body.shifted(dx, dy))
            is Enforcer.State -> state.copy(body = state.body.shifted(dx, dy))
            is Zeppelin.State -> state.copy(body = state.body.shifted(dx, dy), x = state.x + dx, y = state.y + dy)
            is B52.State -> state.copy(body = state.body.shifted(dx, dy), x = state.x + dx, y = state.y + dy)
            is Mine.State -> state.copy(body = PointBody(ScenePoint(x.toFloat(), y.toFloat())))
            else -> state
        }
    }

    /** The object turned one step (legacy `*_new_direction()`), or null when it has no direction. */
    fun turned(state: MapObject): MapObject? = when (state) {
        is Tank.State -> {
            // A start pad turns by quarters (legacy `ply.dir`), a vehicle by sixteenths.
            val step = if (state.startPadType >= 0) 4 else 1
            val direction = (state.bodyDirection + step).mod(16)
            state.copy(bodyDirection = direction, turretDirection = direction, spawnBodyDirection = direction, spawnTurretDirection = direction)
        }
        is Turret.State -> state.copy(turretDirection = (state.turretDirection + 1).mod(16))
        is B52.State -> state.copy(heading = (state.heading + 1).mod(4))
        is Train.State -> {
            val (tx, ty) = trainTile(state)
            train(tx, ty, state.picture, (state.direction + 4).mod(16), null, emptyList())
                .copy(ref = state.ref, frontRef = state.frontRef, rearRef = state.rearRef, goalContribution = state.goalContribution, armor = state.armor)
        }
        else -> null
    }

    /** What the object is, for lists and the status line. */
    fun describe(state: MapObject): String = when (state) {
        is Tank.State -> when {
            state.padPlayer >= 2 -> "Start pad: ${listOf("Tank", "Chopper", "Plane", "Race").getOrElse(state.startPadType shr 1) { "?" }} P${state.padPlayer + 1} (Extended)"
            state.startPadType >= 0 -> "Start pad: ${PAD_NAMES.getOrElse(state.startPadType) { "?" }}"
            else -> LegacyVehicles.spec(state.tankType).name.lowercase().replaceFirstChar { it.uppercase() } +
                when {
                    !state.computer -> " (player ${state.controllerId})"
                    state.team == 0 -> " (good)"
                    else -> " (evil)"
                }
        }
        is Turret.State -> "Turret: ${TURRET_NAMES.getOrElse(state.turretType) { "?" }}" + if (state.ownerRef != 0) " (on a wagon)" else ""
        is Train.State -> if (state.isEngine) "Locomotive" else "Wagon: ${WAGON_NAMES.getOrElse(state.picture - 1) { "?" }}"
        is Mine.State -> if (state.heavy) "Heavy mine" else "Light mine"
        is Flag.State -> "Flag P${state.flagType + 1}"
        is Goal.State -> "Goal"
        is Lock.State -> "Lock"
        is Warp.State -> "Warp"
        is Destroyer.State -> "Destroyer"
        is Enforcer.State -> "Enforcer"
        is Product.State -> "Product ${state.productType}"
        is B52.State -> "B52"
        is Zeppelin.State -> "Zeppelin"
        else -> "?"
    }

    /** The map points the object has besides its position. */
    fun points(state: MapObject): List<ObjectPoint> = when (state) {
        is Warp.State -> listOf(ObjectPoint.WARP_EXIT)
        is Lock.State -> listOf(ObjectPoint.LOCK_KEY)
        is Tank.State -> if (state.computer) listOf(ObjectPoint.WAYPOINT) else emptyList()
        is Goal.State -> listOf(ObjectPoint.GOAL_TARGET)
        is Turret.State -> listOf(ObjectPoint.FORBIDDEN_DIRECTION)
        else -> emptyList()
    }

    /** The original editor's settings of the object (legacy tank_mnu, tur_mnu, lock_mnu, …). */
    fun properties(state: MapObject): List<ObjectProperty> = when (state) {
        is Tank.State -> if (state.startPadType >= 0) padProperties(state) else tankProperties(state)
        is Turret.State -> listOf(
            ObjectProperty("Shoots at", PropertyValues.Choice(SHOOT_AT), state.shootAt) {
                // Legacy tur.shootat 2: a turret that only shoots evil vehicles is on the good side.
                state.copy(shootAt = it, team = if (it == 2) 0 else 1)
            },
            ObjectProperty("Fire power", PropertyValues.Range(0, 50, 1), state.power) { state.copy(power = it) },
            ObjectProperty("Fire radius", PropertyValues.Range(50, 500, 10, ::pixels), state.radius) { state.copy(radius = it) },
            ObjectProperty("Fire delay", PropertyValues.Range(10, 990, 10, ::seconds), state.delay) { state.copy(delay = it) },
            ObjectProperty("Fixed direction", PropertyValues.Toggle, if (state.fixed) 1 else 0) { state.copy(fixed = it != 0) },
            goalShare(state.goalContribution) { state.copy(goalContribution = it) },
        )
        is Lock.State -> listOf(
            ObjectProperty("Opens when", PropertyValues.Choice(listOf("a building on its key is blown", "an object at its key is blown")), state.activation) { state.copy(activation = it) },
            ObjectProperty("Then", PropertyValues.Choice(listOf("blows the building", "kills the object", "removes the object")), state.target) { state.copy(target = it) },
            goalShare(state.goalContribution) { state.copy(goalContribution = it) },
        )
        is Goal.State -> listOf(
            ObjectProperty("Reached by", PropertyValues.Choice(listOf("a good vehicle", "an evil vehicle", "a train", "an object")), state.who.coerceIn(0, 3)) {
                state.copy(who = it, targetRef = if (it == 3) state.targetRef else 0)
            },
            ObjectProperty("Radius", PropertyValues.Range(10, 200, 5, ::pixels), state.radius) { state.copy(radius = it) },
            goalShare(state.contribution) { state.copy(contribution = it) },
        )
        is Destroyer.State -> listOf(
            ObjectProperty("Destroys", PropertyValues.Choice(listOf("buildings", "objects", "both")), (state.what - 1).coerceIn(0, 2)) { state.copy(what = it + 1) },
            ObjectProperty("Immediately", PropertyValues.Toggle, if (state.immediate) 1 else 0) { state.copy(immediate = it != 0) },
            ObjectProperty("Range", PropertyValues.Range(10, 200, 5, ::pixels), state.radius) { state.copy(radius = it) },
            goalShare(state.goalContribution) { state.copy(goalContribution = it) },
        )
        is Enforcer.State -> listOf(
            ObjectProperty("Weapon", PropertyValues.Choice(ENFORCER_WEAPONS), state.weapon.coerceIn(0, ENFORCER_WEAPONS.lastIndex)) { state.copy(weapon = it) },
            ObjectProperty("Enforces good", PropertyValues.Toggle, if (state.good) 1 else 0) { state.copy(good = it != 0) },
            ObjectProperty("Enforces evil", PropertyValues.Toggle, if (state.bad) 1 else 0) { state.copy(bad = it != 0) },
            ObjectProperty("Changes for good", PropertyValues.Toggle, if (state.change) 1 else 0) { state.copy(change = it != 0) },
            ObjectProperty("Fires the weapon", PropertyValues.Toggle, if (state.fire) 1 else 0) { state.copy(fire = it != 0) },
            ObjectProperty("Range", PropertyValues.Range(10, 200, 5, ::pixels), state.radius) { state.copy(radius = it) },
            ObjectProperty("Delay", PropertyValues.Range(10, 1000, 10, ::seconds), state.delay) { state.copy(delay = it) },
        )
        is Product.State -> listOf(
            ObjectProperty("Product", PropertyValues.Range(0, PRODUCT_KINDS - 1, 1), state.productType) { state.copy(productType = it) },
            ObjectProperty("Price", PropertyValues.Range(1, 20, 1) { "$$it" }, state.price) { state.copy(price = it) },
        )
        is B52.State -> listOf(goalShare(state.goalContribution) { state.copy(goalContribution = it) })
        is Train.State -> listOf(goalShare(state.goalContribution) { state.copy(goalContribution = it) })
        else -> emptyList()
    }

    /** Legacy `num_products`. */
    private const val PRODUCT_KINDS = 15

    /** Legacy object goals (`o->gc`): the share of the goal counter destroying it gives. */
    private fun goalShare(value: Int, with: (Int) -> MapObject) =
        ObjectProperty("Goal share", PropertyValues.Range(-100, 100, 10) { "$it %" }, value, with)

    private fun padProperties(state: Tank.State): List<ObjectProperty> = buildList {
        val own = state.padAmmo != null
        add(ObjectProperty("Own weapons", PropertyValues.Toggle, if (own) 1 else 0) {
            if (it != 0) state.copy(padAmmo = state.ammo, ammo = state.ammo) else state.copy(padAmmo = null)
        })
        if (own) for ((label, index, range) in WEAPONS) {
            val ammo = state.padAmmo
            add(ObjectProperty(label, range, ammo.getOrElse(index) { 0 }) { value ->
                val changed = ammo.toMutableList().also { list -> while (list.size <= index) list += 0; list[index] = value }
                state.copy(padAmmo = changed, ammo = changed)
            })
        }
    }

    private fun tankProperties(state: Tank.State): List<ObjectProperty> = buildList {
        val controller = when {
            !state.computer -> 0
            state.team == 0 -> 1
            else -> 2
        }
        add(ObjectProperty("Driven by", PropertyValues.Choice(listOf("a player", "the computer, good", "the computer, evil")), controller) {
            when (it) {
                0 -> state.copy(computer = false, team = 0, controllerId = state.controllerId.takeIf { id -> id > 0 } ?: 1)
                1 -> state.copy(computer = true, team = 0, controllerId = -1)
                else -> state.copy(computer = true, team = 1, controllerId = -1)
            }
        })
        if (!state.computer) {
            add(ObjectProperty("Player key (F1–F10)", PropertyValues.Range(1, 10, 1), state.controllerId.coerceAtLeast(1)) { state.copy(controllerId = it) })
        }
        for ((label, index, range) in WEAPONS) {
            add(ObjectProperty(label, range, state.ammo.getOrElse(index) { 0 }) { value ->
                state.copy(ammo = state.ammo.toMutableList().also { list -> while (list.size <= index) list += 0; list[index] = value })
            })
        }
        add(goalShare(state.goalContribution) { state.copy(goalContribution = it) })
        if (!state.computer) return@buildList
        add(ObjectProperty("Starts active", PropertyValues.Toggle, if (state.aiActive) 1 else 0) { state.copy(aiActive = it != 0) })
        add(ObjectProperty("Stays active", PropertyValues.Toggle, if (state.aiStayActive) 1 else 0) { state.copy(aiStayActive = it != 0) })
        add(ObjectProperty("Woken by", PropertyValues.Choice(listOf("good vehicles", "evil vehicles")), state.aiActivator.coerceIn(0, 1)) { state.copy(aiActivator = it) })
        add(ObjectProperty("Wake radius", PropertyValues.Range(-1, 2500, 25, ::pixels), state.aiActivationRadius) { state.copy(aiActivationRadius = it) })
        add(ObjectProperty("Attacks vehicles", PropertyValues.Range(0, 100, 10) { "$it %" }, state.aiAttackTanks) { state.copy(aiAttackTanks = it) })
        add(ObjectProperty("Attacks goals", PropertyValues.Range(0, 100, 10) { "$it %" }, state.aiAttackGoals) { state.copy(aiAttackGoals = it) })
        val weapon = LegacyVehicles.spec(state.tankType).initialWeapon
        add(ObjectProperty("Fire power", PropertyValues.Range(0, 50, 1), state.power.getOrElse(weapon) { 0 }) { value ->
            state.copy(power = state.power.toMutableList().also { list -> while (list.size <= weapon) list += 0; list[weapon] = value })
        })
        add(ObjectProperty("Fire delay", PropertyValues.Range(-1, 500, 10, ::seconds), state.aiFireDelay) { state.copy(aiFireDelay = it) })
        add(ObjectProperty("Fires from", PropertyValues.Range(-1, 300, 10, ::pixels), state.aiAttackAt) { state.copy(aiAttackAt = it) })
        add(ObjectProperty("Wave delay", PropertyValues.Range(-1, 990, 10, ::seconds), state.aiWaveDelay) { state.copy(aiWaveDelay = it) })
        add(ObjectProperty("Waypoints", PropertyValues.Choice(listOf("not used", "followed once", "followed round")), state.wayType.coerceIn(0, 2)) { state.copy(wayType = it) })
    }

    /** The ten waypoint slots of a vehicle as map points (their centres), null for an empty slot. */
    fun waypoints(state: Tank.State): List<Pair<Int, Int>?> = List(Tank.WAYPOINTS) { i ->
        val x = state.wayPoints.getOrElse(i * 2) { -1 }
        val y = state.wayPoints.getOrElse(i * 2 + 1) { -1 }
        if (x < 0) null else x + 16 to y + 16
    }

    /** Legacy edit_waypoint() / remove_waypoint(): slot [slot] at a map point, or emptied with null. */
    fun withWaypoint(state: Tank.State, slot: Int, at: Pair<Int, Int>?): Tank.State {
        val points = MutableList(Tank.WAYPOINTS * 2) { state.wayPoints.getOrElse(it) { -1 } }
        points[slot * 2] = at?.first?.minus(16) ?: -1
        points[slot * 2 + 1] = at?.second?.minus(16) ?: -1
        return state.copy(wayPoints = points)
    }
}
