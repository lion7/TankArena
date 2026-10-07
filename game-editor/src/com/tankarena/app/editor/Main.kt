package com.tankarena.app.editor

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.sceneEditor.EditableMetadata
import com.pandulapeter.kubriko.sceneEditor.SceneEditor
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.sim.kubriko.server.ServerGoalActor
import com.tankarena.sim.kubriko.server.ServerTankActor
import com.tankarena.sim.kubriko.server.ServerTurretActor
import com.tankarena.sim.kubriko.server.ServerWallActor
import com.tankarena.sim.kubriko.server.TankArenaTypeIds
import kotlinx.serialization.json.Json
import java.io.File

private val sceneJson = Json { ignoreUnknownKeys = true }

private fun tileBody(position: SceneOffset, tilesWide: Int = 1, tilesHigh: Int = 1): BoxBody = BoxBody(
    initialPosition = position,
    initialSize = SceneSize(
        width = (tilesWide * LEGACY_TILE_SIZE).toFloat().sceneUnit,
        height = (tilesHigh * LEGACY_TILE_SIZE).toFloat().sceneUnit,
    ),
)

private val tankArenaEditableMetadata: Array<EditableMetadata<*>> = arrayOf(
    EditableMetadata<ServerWallActor>(
        typeId = TankArenaTypeIds.WALL,
        deserializeState = { sceneJson.decodeFromString<ServerWallActor.State>(it) },
        instantiate = { ServerWallActor.State(body = tileBody(it)) },
    ),
    EditableMetadata<ServerTankActor>(
        typeId = TankArenaTypeIds.TANK,
        deserializeState = { sceneJson.decodeFromString<ServerTankActor.State>(it) },
        instantiate = { ServerTankActor.State(body = tileBody(it)) },
    ),
    EditableMetadata<ServerTurretActor>(
        typeId = TankArenaTypeIds.TURRET,
        deserializeState = { sceneJson.decodeFromString<ServerTurretActor.State>(it) },
        instantiate = { ServerTurretActor.State(body = tileBody(it)) },
    ),
    EditableMetadata<ServerGoalActor>(
        typeId = TankArenaTypeIds.GOAL,
        deserializeState = { sceneJson.decodeFromString<ServerGoalActor.State>(it) },
        instantiate = { ServerGoalActor.State(body = tileBody(it)) },
    ),
)

private fun resolveScenesFolder(): String {
    val candidates = sequenceOf(
        "../game-content/resources/scenes",
        "game-content/resources/scenes",
    )
    for (candidate in candidates) {
        val dir = File(candidate).absoluteFile
        if (dir.isDirectory) return dir.path
    }
    return File("../game-content/resources/scenes").absoluteFile.path
}

fun main() {
    val serializationManager = EditableMetadata.newSerializationManagerInstance(
        editableMetadata = tankArenaEditableMetadata,
    )
    SceneEditor.show(
        defaultSceneFilename = null,
        defaultSceneFolderPath = resolveScenesFolder(),
        serializationManager = serializationManager,
        title = "Tank Arena Scene Editor",
    )
}
