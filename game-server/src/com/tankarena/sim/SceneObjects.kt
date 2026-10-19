package com.tankarena.sim

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A map object as the scene file stores it: [save] turns it into its [State], and a State read from
 * the file is turned back into the object with [State.restore]. Scene files are lists of
 * `{"typeId": ..., "state": "<the state's JSON>"}` (see [SceneFile]).
 */
interface SceneObject<T : SceneObject<T>> {
    fun save(): State<T>

    interface State<T : SceneObject<T>> {
        fun restore(): T
        fun serialize(): String
    }
}

/** A map object with something to do every tick (called once per tick, in the order objects were added). */
interface Steppable {
    fun update(deltaTimeInMilliseconds: Int)
}

/** A tank applies its queued damage and pushes after every object has moved (see [MatchObjectList.step]). */
interface Resolvable {
    fun applyPendingResolutions(worldWidthPixels: Int, worldHeightPixels: Int)
}

/** A position in world pixels. */
@Serializable
data class ScenePoint(val x: Float, val y: Float) {
    companion object {
        val Zero = ScenePoint(0f, 0f)
    }
}

@Serializable
data class SceneExtent(val width: Float, val height: Float)

@Serializable
data class BodyScale(val horizontal: Float, val vertical: Float)

/** Where a map object is. */
sealed interface Body {
    var position: ScenePoint
}

/** A map object with a position in the world. */
interface Placed {
    val body: Body
}

/**
 * Where a map object is and how large it is. Stored with every field, as the scene files have
 * always had them; pivot, scale and rotation are kept only so the files stay the same.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
class BoxBody(
    @EncodeDefault override var position: ScenePoint = ScenePoint.Zero,
    @EncodeDefault val size: SceneExtent = SceneExtent(0f, 0f),
    @EncodeDefault val pivot: ScenePoint = ScenePoint(size.width / 2f, size.height / 2f),
    @EncodeDefault val scale: BodyScale = BodyScale(1f, 1f),
    @EncodeDefault val rotation: Float = 0f,
) : Body

/** The position of a map object without a size. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
class PointBody(
    @EncodeDefault override var position: ScenePoint = ScenePoint.Zero,
) : Body

/** Every map object type with its id in scene files. */
object TankArenaTypeIds {
    const val WALL: String = "tankArenaWall"
    const val TANK: String = "tankArenaTank"
    const val TURRET: String = "tankArenaTurret"
    const val GOAL: String = "tankArenaGoal"
    const val PROJECTILE: String = "tankArenaProjectile"
    const val MINE: String = "tankArenaMine"
    const val ROCKET: String = "tankArenaRocket"
    const val MORTAR: String = "tankArenaMortar"
    const val FLAG: String = "tankArenaFlag"
    const val PRODUCT: String = "tankArenaProduct"
    const val LOCK: String = "tankArenaLock"
    const val WARP: String = "tankArenaWarp"
    const val DESTROYER: String = "tankArenaDestroyer"
    const val ENFORCER: String = "tankArenaEnforcer"
    const val TRAIN: String = "tankArenaTrain"
    const val ZEPPELIN: String = "tankArenaZeppelin"
    const val B52: String = "tankArenaB52"
}

/** Reads and writes scene files: a JSON list of `{typeId, state}` entries, the state itself JSON. */
object SceneFile {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Entry(val typeId: String, val state: String)

    private val readers: Map<String, (String) -> SceneObject.State<*>> = mapOf(
        TankArenaTypeIds.WALL to { json.decodeFromString<Wall.State>(it) },
        TankArenaTypeIds.TANK to { json.decodeFromString<Tank.State>(it) },
        TankArenaTypeIds.TURRET to { json.decodeFromString<Turret.State>(it) },
        TankArenaTypeIds.GOAL to { json.decodeFromString<Goal.State>(it) },
        TankArenaTypeIds.PROJECTILE to { json.decodeFromString<Projectile.State>(it) },
        TankArenaTypeIds.MINE to { json.decodeFromString<Mine.State>(it) },
        TankArenaTypeIds.ROCKET to { json.decodeFromString<Rocket.State>(it) },
        TankArenaTypeIds.MORTAR to { json.decodeFromString<Mortar.State>(it) },
        TankArenaTypeIds.FLAG to { json.decodeFromString<Flag.State>(it) },
        TankArenaTypeIds.PRODUCT to { json.decodeFromString<Product.State>(it) },
        TankArenaTypeIds.LOCK to { json.decodeFromString<Lock.State>(it) },
        TankArenaTypeIds.WARP to { json.decodeFromString<Warp.State>(it) },
        TankArenaTypeIds.DESTROYER to { json.decodeFromString<Destroyer.State>(it) },
        TankArenaTypeIds.ENFORCER to { json.decodeFromString<Enforcer.State>(it) },
        TankArenaTypeIds.TRAIN to { json.decodeFromString<Train.State>(it) },
        TankArenaTypeIds.ZEPPELIN to { json.decodeFromString<Zeppelin.State>(it) },
        TankArenaTypeIds.B52 to { json.decodeFromString<B52.State>(it) },
    )

    private fun typeIdOf(obj: SceneObject<*>): String? = when (obj) {
        is Wall -> TankArenaTypeIds.WALL
        is Tank -> TankArenaTypeIds.TANK
        is Turret -> TankArenaTypeIds.TURRET
        is Goal -> TankArenaTypeIds.GOAL
        is Projectile -> TankArenaTypeIds.PROJECTILE
        is Mine -> TankArenaTypeIds.MINE
        is Rocket -> TankArenaTypeIds.ROCKET
        is Mortar -> TankArenaTypeIds.MORTAR
        is Flag -> TankArenaTypeIds.FLAG
        is Product -> TankArenaTypeIds.PRODUCT
        is Lock -> TankArenaTypeIds.LOCK
        is Warp -> TankArenaTypeIds.WARP
        is Destroyer -> TankArenaTypeIds.DESTROYER
        is Enforcer -> TankArenaTypeIds.ENFORCER
        is Train -> TankArenaTypeIds.TRAIN
        is Zeppelin -> TankArenaTypeIds.ZEPPELIN
        is B52 -> TankArenaTypeIds.B52
        else -> null
    }

    /** The objects of a scene file, in file order; entries of unknown types are skipped. */
    fun read(text: String): List<SceneObject<*>> =
        json.decodeFromString<List<Entry>>(text).mapNotNull { entry -> readers[entry.typeId]?.invoke(entry.state)?.restore() }

    fun write(objects: List<SceneObject<*>>): String =
        json.encodeToString(objects.mapNotNull { obj -> typeIdOf(obj)?.let { Entry(it, obj.save().serialize()) } })
}

/**
 * The match's map objects in the order they were added, which is the order they are updated in.
 * [all] is an unchanging snapshot, so code may add or remove objects while it walks the list.
 */
class MatchObjectList(initial: List<SceneObject<*>>) {
    var all: List<SceneObject<*>> = initial.toList()
        private set

    fun add(objects: Collection<SceneObject<*>>) {
        if (objects.isNotEmpty()) all = all + objects.filter { it !in all }
    }

    fun remove(objects: Collection<SceneObject<*>>) {
        if (objects.isEmpty()) return
        val doomed = objects.toHashSet()
        all = all.filterNot { it in doomed }
    }

    /**
     * One tick of the objects themselves: every [Steppable] updates, then every [Resolvable] applies
     * what was queued for it.
     */
    fun step(worldWidthPixels: Int, worldHeightPixels: Int) {
        val objects = all
        for (obj in objects) if (obj is Steppable) obj.update(TICK_MILLIS)
        for (obj in objects) if (obj is Resolvable) obj.applyPendingResolutions(worldWidthPixels, worldHeightPixels)
    }

    private companion object {
        const val TICK_MILLIS = 10
    }
}
