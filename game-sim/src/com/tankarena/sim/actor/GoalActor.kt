package com.tankarena.sim.actor

import com.pandulapeter.kubriko.actor.body.PointBody
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.mask.CircleCollisionMask
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.tankarena.core.Int2
import com.tankarena.sim.GoalState

internal class GoalActor(
    val id: Long,
    val position: Int2,
    val radius: Int,
    val who: Int,
    val contribution: Int,
    initialClaimed: Boolean = false,
) : Collidable {

    var isClaimed: Boolean = initialClaimed

    override val body: PointBody = PointBody(initialPosition = position.toSceneOffset())

    override val collisionMask: CircleCollisionMask = CircleCollisionMask(
        initialPosition = position.toSceneOffset(),
        initialRadius = radius.toFloat().sceneUnit,
    )

    fun toState(): GoalState = GoalState(
        id = id,
        position = position,
        radius = radius,
        who = who,
        contribution = contribution,
        isClaimed = isClaimed,
    )

    companion object {
        fun fromState(state: GoalState): GoalActor = GoalActor(
            id = state.id,
            position = state.position,
            radius = state.radius,
            who = state.who,
            contribution = state.contribution,
            initialClaimed = state.isClaimed,
        )
    }
}
