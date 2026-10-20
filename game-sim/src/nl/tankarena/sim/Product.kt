package nl.tankarena.sim

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

/**
 * Supermarket product pickup.
 *
 * `productType`: 0–14 (15 product types).
 * `price`: cash cost when picked up.
 */
class Product internal constructor(state: State) : SceneObject<Product>, Placed {

    override val body: BoxBody = state.body

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
        @SerialName("body") val body: BoxBody = BoxBody(),
        @SerialName("productType") val productType: Int = 0,
        @SerialName("price") val price: Int = 1,
        @SerialName("isCollected") val isCollected: Boolean = false,
    ) : SceneObject.State<Product> {
        override fun restore(): Product = Product(this)
        override fun serialize(): String = Json.encodeToString(this)
    }
}
