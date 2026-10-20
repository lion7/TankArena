package nl.tankarena.sim

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

/** Legacy HT_MORTAR shell: 120 px at 75 px/s, i.e. 160 ticks in flight (src/world/bullet.c). */
internal const val MORTAR_TRAVEL_TICKS: Int = 160
internal const val MORTAR_MIN_RADIUS_PX: Int = 5
internal const val MORTAR_MAX_RADIUS_PX: Int = 60
internal const val MORTAR_RING_STEP_PX: Int = 5

class Mortar internal constructor(state: State) : Steppable,
    SceneObject<Mortar>, Placed {

    override val body: PointBody = state.body

    val ownerActorId: Long = state.ownerActorId
    val damage: Int = state.damage
    val maxRadius: Int = state.maxRadius
    /** Legacy HT_PLANEBOMB: the height a plane dropped this bomb from; 0 for a mortar shell. */
    val dropHeight: Int = state.dropHeight

    var positionX: Float = body.position.x
        private set
    var positionY: Float = body.position.y
        private set
    var velocityX: Float = state.velocityX
        private set
    var velocityY: Float = state.velocityY
        private set
    var travelTicksRemaining: Int = state.travelTicksRemaining
        private set
    var isDead: Boolean = false
        private set

    private var explosionPending: Boolean = false
    internal var ownerRef: Tank? = null

    /** Legacy create_mortar(): the shell has landed and its blast grows ring by ring. */
    private var landed: Boolean = false
    /** Legacy `m.expl`: the next ring's radius, 0 before landing. */
    private var blastRadius: Int = 0

    override fun update(deltaTimeInMilliseconds: Int) {
        if (isDead || landed) return
        // The shell flies in a straight line over walls; the lob arc is only drawn.
        // Velocities are per tick; a step covers STEP_TICKS of them.
        positionX += velocityX * STEP_TICKS
        positionY += velocityY * STEP_TICKS
        val offset = ScenePoint(positionX, positionY)
        body.position = offset
        travelTicksRemaining -= STEP_TICKS
        if (travelTicksRemaining <= 0) detonate()
    }

    fun markOutOfBoundsIfNeeded(worldWidthPixels: Int, worldHeightPixels: Int) {
        if (isDead) return
        if (positionX < 0 || positionY < 0 ||
            positionX >= worldWidthPixels || positionY >= worldHeightPixels
        ) {
            detonate()
        }
    }

    internal fun detonate() {
        if (isDead || landed) return
        landed = true
        blastRadius = MORTAR_MIN_RADIUS_PX
        explosionPending = true
    }

    /** The impact, once: where the shell landed. */
    fun drainExplosion(): Explosion? {
        if (!explosionPending) return null
        explosionPending = false
        return Explosion(positionX.toInt(), positionY.toInt(), maxRadius, damage)
    }

    /**
     * Legacy control_mortar(): this step's ring of the blast, 5 px wider every step up to 60 px; the
     * mortar is gone after the last one. Null before landing.
     */
    fun nextRing(): Int? {
        if (!landed || isDead) return null
        val ring = blastRadius
        blastRadius += MORTAR_RING_STEP_PX
        if (blastRadius > MORTAR_MAX_RADIUS_PX) isDead = true
        return ring
    }

    data class Explosion(val x: Int, val y: Int, val radius: Int, val damage: Int)

    override fun save(): State = State(
        body = body,
        ownerActorId = ownerActorId,
        damage = damage,
        maxRadius = maxRadius,
        velocityX = velocityX,
        velocityY = velocityY,
        travelTicksRemaining = travelTicksRemaining,
        dropHeight = dropHeight,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: PointBody = PointBody(),
        @SerialName("ownerActorId") val ownerActorId: Long = 0L,
        @SerialName("damage") val damage: Int = 0,
        @SerialName("maxRadius") val maxRadius: Int = MORTAR_MAX_RADIUS_PX,
        @SerialName("velocityX") val velocityX: Float = 0f,
        @SerialName("velocityY") val velocityY: Float = 0f,
        @SerialName("travelTicksRemaining") val travelTicksRemaining: Int = MORTAR_TRAVEL_TICKS,
        @SerialName("dropHeight") val dropHeight: Int = 0,
    ) : SceneObject.State<Mortar> {
        override fun restore(): Mortar = Mortar(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}
