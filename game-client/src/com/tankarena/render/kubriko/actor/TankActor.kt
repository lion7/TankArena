package com.tankarena.render.kubriko.actor

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pandulapeter.kubriko.sprites.SpriteManager
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.LegacySpriteResources
import com.tankarena.protocol.snapshot.TankState
import com.tankarena.render.kubriko.LegacySpriteCatalog
import com.tankarena.render.kubriko.RuntimeSnapshot
import com.tankarena.render.kubriko.drawSprite
import org.jetbrains.compose.resources.DrawableResource

internal class TankActor(
    id: Long,
    snapshot: RuntimeSnapshot,
    private val spriteManager: SpriteManager,
    private val sprites: LegacySpriteCatalog,
) : RenderActor(
    id = id,
    snapshot = snapshot,
    width = LEGACY_TILE_SIZE,
    height = LEGACY_TILE_SIZE,
) {
    private var tankType: Int = 0
    private var bodyDirection: Int = 0
    private var turretDirection: Int = 0
    private var alive: Boolean = true
    private var trackFrame: Int = 0
    private var lastX: Int = Int.MIN_VALUE
    private var lastY: Int = Int.MIN_VALUE
    private var distanceSinceToggle: Int = 0

    fun sync(state: TankState) {
        tankType = state.tankType
        bodyDirection = state.bodyDirection
        turretDirection = state.turretDirection
        alive = state.alive
        // Legacy new_position(): the body alternates between frames dir and dir+16 every
        // `motion.change` pixels driven.
        if (lastX != Int.MIN_VALUE) {
            distanceSinceToggle += kotlin.math.abs(state.x - lastX) + kotlin.math.abs(state.y - lastY)
            if (distanceSinceToggle >= TRACK_TOGGLE_DISTANCE) {
                distanceSinceToggle = 0
                trackFrame = trackFrame xor TRACK_FRAME_OFFSET
            }
        }
        lastX = state.x
        lastY = state.y
        setCenter(
            x = snapshot.renderOffsetX + state.x,
            y = snapshot.renderOffsetY + state.y,
        )
    }

    fun collectSpriteResources(target: MutableSet<DrawableResource>) {
        spriteNames().forEach { name -> sprites.findResource(name)?.let(target::add) }
    }

    /** Legacy frames (0-based): body `dir` (+16 animated), turret `32 + ldir`, wreck `48`. */
    private fun spriteNames(): List<String> {
        if (!alive) return listOf(LegacySpriteResources.nameForTankBody(tankType, WRECK_FRAME))
        val direction = LegacySpriteResources.tankBodyFrameForFacing(bodyDirection)
        return listOf(
            LegacySpriteResources.nameForTankBody(tankType, direction + trackFrame),
            LegacySpriteResources.nameForTankBody(tankType, TURRET_FRAME_OFFSET + LegacySpriteResources.tankBodyFrameForFacing(turretDirection)),
        )
    }

    override fun DrawScope.draw() {
        val images = spriteNames().map { name -> sprites.findResource(name)?.let(spriteManager::get) }
        // Sprites load asynchronously at mission start; draw nothing until the body frame is ready.
        if (images.firstOrNull() == null) return
        for (image in images) {
            // Vehicles without a separate turret have no frames past 31; skip what is missing.
            image ?: continue
            drawSprite(image, x = 0f, y = 0f, width = LEGACY_TILE_SIZE, height = LEGACY_TILE_SIZE)
        }
    }

    private companion object {
        const val TRACK_FRAME_OFFSET = 16
        const val TRACK_TOGGLE_DISTANCE = 6
        const val TURRET_FRAME_OFFSET = 32
        const val WRECK_FRAME = 48
    }
}
