package com.tankarena.render.kubriko.actor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pandulapeter.kubriko.sprites.SpriteManager
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.LegacySpriteResources
import com.tankarena.protocol.ActorView
import com.tankarena.render.kubriko.LegacySpriteCatalog
import com.tankarena.render.kubriko.RuntimeSnapshot
import com.tankarena.render.kubriko.drawSprite
import org.jetbrains.compose.resources.DrawableResource

internal class TurretActor(
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
    private var turretType: Int = 0
    private var turretDirection: Int = 0

    fun sync(actor: ActorView) {
        turretType = actor.tankType
        turretDirection = actor.turretDirection
        setCenter(
            x = snapshot.renderOffsetX + actor.x,
            y = snapshot.renderOffsetY + actor.y,
        )
    }

    fun collectSpriteResources(target: MutableSet<DrawableResource>) {
        val frame = LegacySpriteResources.turretFrameForDirection(turretDirection)
        sprites.findResource(LegacySpriteResources.nameForTurret(turretType, frame))
            ?.let(target::add)
    }

    override fun DrawScope.draw() {
        val frame = LegacySpriteResources.turretFrameForDirection(turretDirection)
        val image = sprites.findResource(LegacySpriteResources.nameForTurret(turretType, frame))
            ?.let(spriteManager::get)
        if (image == null) {
            drawRect(
                color = Color(0xFFFFB454),
                topLeft = Offset((LEGACY_TILE_SIZE - 20) / 2f, (LEGACY_TILE_SIZE - 20) / 2f),
                size = Size(20f, 20f),
            )
            return
        }
        drawSprite(image, x = 0f, y = 0f, width = LEGACY_TILE_SIZE, height = LEGACY_TILE_SIZE)
    }
}
