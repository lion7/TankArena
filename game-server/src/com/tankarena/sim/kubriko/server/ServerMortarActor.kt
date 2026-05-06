package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.actor.body.PointBody
import com.pandulapeter.kubriko.actor.traits.Dynamic
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.mask.CircleCollisionMask
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.serialization.Serializable
import com.pandulapeter.kubriko.serialization.typeSerializers.SerializablePointBody
import com.pandulapeter.kubriko.types.SceneOffset
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

internal const val MORTAR_TRAVEL_TICKS: Int = 50
internal const val MORTAR_MIN_RADIUS_PX: Int = 5
internal const val MORTAR_MAX_RADIUS_PX: Int = 60

class ServerMortarActor internal constructor(state: State) :
    Collidable,
    Dynamic,
    Serializable<ServerMortarActor> {

    override val body: PointBody = state.body
    override val collisionMask: CircleCollisionMask = CircleCollisionMask(
        initialPosition = body.position,
        initialRadius = 2f.sceneUnit,
    )

    val ownerActorId: Long = state.ownerActorId
    val damage: Int = state.damage
    val maxRadius: Int = state.maxRadius

    var positionX: Float = body.position.x.raw
        private set
    var positionY: Float = body.position.y.raw
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
    internal var ownerRef: ServerTankActor? = null

    override fun update(deltaTimeInMilliseconds: Int) {
        if (isDead) return
        positionX += velocityX
        positionY += velocityY
        // Slight gravity-like vertical drift to evoke the lob arc; the resolver only
        // checks final impact position so this is purely cosmetic.
        velocityY += 0.05f
        val offset = SceneOffset(positionX.sceneUnit, positionY.sceneUnit)
        body.position = offset
        collisionMask.position = offset
        travelTicksRemaining -= 1
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
        if (isDead) return
        isDead = true
        explosionPending = true
    }

    fun drainExplosion(): Explosion? {
        if (!explosionPending) return null
        explosionPending = false
        return Explosion(positionX.toInt(), positionY.toInt(), maxRadius, damage)
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
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: SerializablePointBody = PointBody(),
        @SerialName("ownerActorId") val ownerActorId: Long = 0L,
        @SerialName("damage") val damage: Int = 0,
        @SerialName("maxRadius") val maxRadius: Int = MORTAR_MAX_RADIUS_PX,
        @SerialName("velocityX") val velocityX: Float = 0f,
        @SerialName("velocityY") val velocityY: Float = 0f,
        @SerialName("travelTicksRemaining") val travelTicksRemaining: Int = MORTAR_TRAVEL_TICKS,
    ) : Serializable.State<ServerMortarActor> {
        override fun restore(): ServerMortarActor = ServerMortarActor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}
