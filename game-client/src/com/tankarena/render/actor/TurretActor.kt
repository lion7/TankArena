package com.tankarena.render.actor

import com.tankarena.render.PictureCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.LegacySpriteResources
import com.tankarena.protocol.snapshot.TurretState
import com.tankarena.render.LegacySpriteCatalog
import com.tankarena.render.RuntimeSnapshot
import org.jetbrains.compose.resources.DrawableResource

internal class TurretActor(
    id: Long,
    snapshot: RuntimeSnapshot,
    private val pictures: PictureCache,
    private val sprites: LegacySpriteCatalog,
) : RenderActor(
    id = id,
    snapshot = snapshot,
    width = LEGACY_TILE_SIZE,
    height = LEGACY_TILE_SIZE,
) {
    private var turretType: Int = 0
    private var turretDirection: Int = 0
    private var alive: Boolean = true

    fun sync(state: TurretState) {
        alive = state.alive
        turretType = state.turretType
        turretDirection = state.turretDirection
        setCenter(
            x = snapshot.renderOffsetX + state.x,
            y = snapshot.renderOffsetY + state.y,
        )
    }

    fun collectSpriteResources(target: MutableSet<DrawableResource>) {
        val frame = directionalFrame(turretDirection)
        sprites.findResource(LegacySpriteResources.nameForTurret(turretType, frame.index))
            ?.let(target::add)
    }

    override fun DrawScope.draw() {
        if (!alive) return // destroyed turrets leave only the ruined tile underneath
        // Legacy write_turret(): b_add_layer_d() with `ti[type].pic`, five pictures mirrored for 16 directions.
        val frame = directionalFrame(turretDirection)
        val image = sprites.findResource(LegacySpriteResources.nameForTurret(turretType, frame.index))
            ?.let(pictures::get)
        if (image == null) {
            drawRect(
                color = Color(0xFFFFB454),
                topLeft = Offset((LEGACY_TILE_SIZE - 20) / 2f, (LEGACY_TILE_SIZE - 20) / 2f),
                size = Size(20f, 20f),
            )
            return
        }
        drawLegacyPicture(image, 0, 0, frame.flipHorizontal, frame.flipVertical)
    }
}
