package com.tankarena.client

import com.tankarena.core.Entity
import com.tankarena.core.ExplosionEntity
import com.tankarena.core.GameSimulation
import com.tankarena.core.PlayerAction
import com.tankarena.core.PlayerIntent
import com.tankarena.core.ProjectileEntity
import com.tankarena.core.TankEntity
import korlibs.event.Key
import korlibs.image.color.Colors
import korlibs.korge.Korge
import korlibs.korge.scene.Scene
import korlibs.korge.scene.sceneContainer
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.fixedSizeContainer
import korlibs.korge.view.position
import korlibs.korge.view.solidRect
import korlibs.korge.view.xy
import korlibs.math.geom.Size
import kotlinx.coroutines.delay

suspend fun main() = Korge(
    windowSize = Size(960, 720),
    virtualSize = Size(320, 240),
    backgroundColor = Colors["#101420"],
    title = "Tank Arena Rewrite",
) {
    sceneContainer().changeTo { TankArenaScene() }
}

private class TankArenaScene : Scene() {
    private val simulation = GameSimulation()
    private lateinit var entityLayer: Container
    private lateinit var tankView: korlibs.korge.view.SolidRect

    private var fireHeldLastFrame: Boolean = false
    private var state = simulation.initialState()

    override suspend fun SContainer.sceneMain() {
        fixedSizeContainer(Size(320, 240), clip = true) {
            solidRect(320, 240, Colors["#203030"]) // background
            entityLayer = Container().also { addChild(it) } // world/entities
            addChild(Container()) // overlay/UI placeholder
        }

        tankView = entityLayer.solidRect(14, 10, Colors.GREEN)

        while (true) {
            state = simulation.step(state, listOf(buildIntent()))
            renderState(state.entities)
            delay(16L)
        }
    }

    private fun buildIntent(): PlayerIntent {
        val actions = linkedSetOf<PlayerAction>()

        if (keys[Key.W] || keys[Key.UP]) actions += PlayerAction.MoveUp
        if (keys[Key.S] || keys[Key.DOWN]) actions += PlayerAction.MoveDown
        if (keys[Key.A] || keys[Key.LEFT]) actions += PlayerAction.MoveLeft
        if (keys[Key.D] || keys[Key.RIGHT]) actions += PlayerAction.MoveRight

        val fireDown = keys[Key.SPACE]
        if (fireDown && !fireHeldLastFrame) actions += PlayerAction.FirePrimary
        fireHeldLastFrame = fireDown

        return PlayerIntent(playerId = 0, activeActions = actions)
    }

    private fun renderState(entities: List<Entity>) {
        val tank = entities.filterIsInstance<TankEntity>().firstOrNull() ?: return
        tankView.position(tank.position.x - 7f, tank.position.y - 5f)

        entityLayer.removeChildren()
        entityLayer.addChild(tankView)

        entities.filterIsInstance<ProjectileEntity>().forEach { projectile ->
            entityLayer.solidRect(2, 2, Colors.YELLOW).xy(projectile.position.x, projectile.position.y)
        }

        entities.filterIsInstance<ExplosionEntity>().forEach { explosion ->
            entityLayer.solidRect(6, 6, Colors.ORANGE).xy(explosion.position.x - 3, explosion.position.y - 3)
        }
    }
}
