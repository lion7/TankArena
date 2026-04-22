package com.tankarena.sim.actor

import com.pandulapeter.kubriko.actor.body.PointBody
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.mask.BoxCollisionMask
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneSize

/**
 * Static collidable representing a single solid map tile (or a synthetic
 * boundary segment along the playfield edges). Walls only participate as
 * `Collidable` � the actual terrain rendering is handled in bulk by the
 * client's `TerrainActor`, so we deliberately do NOT implement `Visible`.
 */
internal class WallActor(
    centerX: Int,
    centerY: Int,
    width: Int,
    height: Int,
) : Collidable {

    override val body: PointBody = PointBody(initialPosition = sceneOffsetOf(centerX, centerY))

    override val collisionMask: BoxCollisionMask = BoxCollisionMask(
        initialPosition = sceneOffsetOf(centerX, centerY),
        initialSize = SceneSize(
            width = width.toFloat().sceneUnit,
            height = height.toFloat().sceneUnit,
        ),
    )

    val centerX: Int = centerX
    val centerY: Int = centerY
    val halfWidth: Int = width / 2
    val halfHeight: Int = height / 2
}
