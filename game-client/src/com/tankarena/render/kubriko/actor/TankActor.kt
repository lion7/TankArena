package com.tankarena.render.kubriko.actor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pandulapeter.kubriko.sprites.SpriteManager
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.LegacySpriteResources
import com.tankarena.protocol.Team
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
    private var team: Team = Team.NEUTRAL
    private var tankType: Int = 0
    private var bodyDirection: Int = 0
    private var turretDirection: Int = 0

    fun sync(state: TankState) {
        team = state.team
        tankType = state.tankType
        bodyDirection = state.bodyDirection
        turretDirection = state.turretDirection
        setCenter(
            x = snapshot.renderOffsetX + state.x,
            y = snapshot.renderOffsetY + state.y,
        )
    }

    fun collectSpriteResources(target: MutableSet<DrawableResource>) {
        val bodyFrame = LegacySpriteResources.tankBodyFrameForFacing(bodyDirection)
        sprites.findResource(LegacySpriteResources.nameForTankBody(tankType, bodyFrame))
            ?.let(target::add)
        val turretFrame = LegacySpriteResources.turretFrameForDirection(turretDirection)
        sprites.findResource(LegacySpriteResources.nameForTurret(tankType, turretFrame))
            ?.let(target::add)
    }

    override fun DrawScope.draw() {
        val bodyFrame = LegacySpriteResources.tankBodyFrameForFacing(bodyDirection)
        val bodyImage = sprites.findResource(LegacySpriteResources.nameForTankBody(tankType, bodyFrame))
            ?.let(spriteManager::get)
        if (bodyImage != null) {
            drawSprite(bodyImage, x = 0f, y = 0f, width = LEGACY_TILE_SIZE, height = LEGACY_TILE_SIZE)
        } else {
            drawRect(
                color = if (team == Team.PLAYER) Color(0xFF6DD3FF) else Color(0xFFFF6D6D),
                topLeft = Offset((LEGACY_TILE_SIZE - 24) / 2f, (LEGACY_TILE_SIZE - 24) / 2f),
                size = Size(24f, 24f),
            )
        }

        val turretFrame = LegacySpriteResources.turretFrameForDirection(turretDirection)
        val turretImage = sprites.findResource(LegacySpriteResources.nameForTurret(tankType, turretFrame))
            ?.let(spriteManager::get)
            ?: return
        drawSprite(turretImage, x = 0f, y = 0f, width = LEGACY_TILE_SIZE, height = LEGACY_TILE_SIZE)
    }
}
