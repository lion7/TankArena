package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.mask.BoxCollisionMask
import com.pandulapeter.kubriko.sceneEditor.Editable
import com.pandulapeter.kubriko.serialization.Serializable
import com.pandulapeter.kubriko.serialization.typeSerializers.SerializableBoxBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

/**
 * Supermarket product pickup.
 *
 * `productType`: 0–14 (15 product types).
 * `price`: cash cost when picked up.
 */
class ServerProductActor internal constructor(state: State) : Collidable, Editable<ServerProductActor> {

    override val body: BoxBody = state.body
    override val collisionMask: BoxCollisionMask = BoxCollisionMask(
        initialSize = body.size,
        initialPosition = body.position,
        initialRotation = body.rotation,
    )

    val productType: Int = state.productType
    val price: Int = state.price
    var isCollected: Boolean = state.isCollected

    override fun save(): State = State(
        body = body,
        productType = productType,
        price = price,
        isCollected = isCollected,
    )

    @kotlinx.serialization.Serializable
    data class State(
        @SerialName("body") val body: SerializableBoxBody = BoxBody(),
        @SerialName("productType") val productType: Int = 0,
        @SerialName("price") val price: Int = 1,
        @SerialName("isCollected") val isCollected: Boolean = false,
    ) : Serializable.State<ServerProductActor> {
        override fun restore(): ServerProductActor = ServerProductActor(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}
