package com.tankarena.client

import com.tankarena.core.Entity
import com.tankarena.core.ExplosionEntity
import com.tankarena.core.GameSimulation
import com.tankarena.core.ProjectileEntity
import com.tankarena.core.TankEntity
import korlibs.image.color.Colors
import korlibs.korge.Korge
import korlibs.korge.scene.Scene
import korlibs.korge.scene.sceneContainer
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.fixedSizeContainer
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
    private val inputMapper = KeyboardActionMapper(playerId = 0)
    private lateinit var entityLayer: Container

    private var state = simulation.initialState()

    override suspend fun SContainer.sceneMain() {
        fixedSizeContainer(Size(320, 240), clip = true) {
            solidRect(320, 240, Colors["#203030"]) // background
            entityLayer = Container().also { addChild(it) } // world/entities
            addChild(Container()) // overlay/UI placeholder
        }

        while (true) {
            state = simulation.step(state, listOf(inputMapper.sample(keys)))
            renderState(state.entities)
            delay(16L)
        }
    }

    private fun renderState(entities: List<Entity>) {
        entityLayer.removeChildren()

        entities.filterIsInstance<TankEntity>().forEach { tank ->
            val color = when {
                !tank.alive -> Colors["#666666"]
                tank.playerId == 0 -> Colors.GREEN
                else -> Colors["#52a7ff"]
            }
            entityLayer.solidRect(14, 10, color).xy(tank.position.x - 7f, tank.position.y - 5f)
        }

        entities.filterIsInstance<ProjectileEntity>().forEach { projectile ->
            entityLayer.solidRect(2, 2, Colors.YELLOW).xy(projectile.position.x, projectile.position.y)
        }

        entities.filterIsInstance<ExplosionEntity>().forEach { explosion ->
            entityLayer.solidRect(6, 6, Colors.ORANGE).xy(explosion.position.x - 3f, explosion.position.y - 3f)
        }
    }
}
