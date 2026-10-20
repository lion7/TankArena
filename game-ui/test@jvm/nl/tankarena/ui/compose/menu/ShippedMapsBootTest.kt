package nl.tankarena.ui.compose.menu

import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.protocol.snapshot.TankState
import nl.tankarena.sim.DualSetup
import nl.tankarena.sim.LocalMatchClient
import nl.tankarena.sim.MatchMode
import nl.tankarena.ui.compose.GameMode
import kotlin.test.Test
import kotlin.test.fail
import kotlinx.coroutines.runBlocking

/**
 * Every shipped map starts in every mode its map type allows (legacy `check_suitable()`), and in
 * player vs player with every vehicle its contents allow (legacy select_tanks()): each player gets a
 * vehicle, and [TICKS] idle ticks run without an exception and with every vehicle inside the world.
 */
class ShippedMapsBootTest {

    @Test
    fun `every shipped map boots in every mode it allows`() {
        val missions = runBlocking { MissionCatalog.load() }
        val failures = mutableListOf<String>()
        var boots = 0
        for (mode in GameMode.entries) {
            for (mission in missions.playableIn(mode)) {
                if (mission.baseName == "select") continue // the tank selection arena, not a playable map
                for (dual in setupsFor(mission, mode)) {
                    boots++
                    val label = "${mission.baseName} $mode${if (mode == GameMode.PLAYER_VS_PLAYER) " vehicles ${dual.tankTypes}" else ""}"
                    runCatching { boot(mission, mode, dual) }
                        .onFailure { failures += "$label: ${it.message ?: it}" }
                }
            }
        }
        println("Booted $boots map/mode/vehicle combinations")
        if (boots < MIN_BOOTS) fail("only $boots boots; the shipped maps did not load")
        if (failures.isNotEmpty()) fail("${failures.size} of $boots boots failed:\n" + failures.joinToString("\n"))
    }

    /** One setup per vehicle the map allows in player vs player; a single default setup otherwise. */
    private fun setupsFor(mission: MissionEntry, mode: GameMode): List<DualSetup> {
        if (mode != GameMode.PLAYER_VS_PLAYER) return listOf(DualSetup())
        val allowed = allowedTanks(mission.sidecar.metadata.mapContents, wins = listOf(0, 0))
        return allowed.indices.filter { allowed[it] }.map { DualSetup(tankTypes = listOf(it, it)) }
    }

    private fun boot(mission: MissionEntry, mode: GameMode, dual: DualSetup) {
        val client = LocalMatchClient.fromMap(mission.document, mode.toMatchMode(), dual = dual)
        try {
            val players = if (mode == GameMode.SINGLE_PLAYER_VS_COMPUTER) listOf(0) else listOf(0, 1)
            val first = client.currentServerFrame()
            for (player in players) {
                val view = first.playerViews.firstOrNull { it.playerId == player }
                    ?: error("player ${player + 1} has no view")
                val tank = first.world.actors.firstOrNull { it is TankState && it.actorId == view.controlledActorId }
                    ?: error("player ${player + 1} has no vehicle")
                check((tank as TankState).alive) { "player ${player + 1}'s vehicle starts dead" }
            }
            val width = mission.sidecar.metadata.widthTiles * LEGACY_TILE_SIZE
            val height = mission.sidecar.metadata.heightTiles * LEGACY_TILE_SIZE
            repeat(TICKS) {
                val frame = client.tick()
                for (tank in frame.world.actors.filterIsInstance<TankState>()) {
                    check(tank.x in -LEGACY_TILE_SIZE..width + LEGACY_TILE_SIZE && tank.y in -LEGACY_TILE_SIZE..height + LEGACY_TILE_SIZE) {
                        "tank ${tank.actorId} left the world at (${tank.x}, ${tank.y}) on tick ${frame.tick}"
                    }
                    check(tank.vx.isFinite() && tank.vy.isFinite()) { "tank ${tank.actorId} has speed (${tank.vx}, ${tank.vy})" }
                    check(tank.armor >= 0) { "tank ${tank.actorId} has armor ${tank.armor}" }
                }
            }
        } finally {
            client.dispose()
        }
    }

    private fun GameMode.toMatchMode(): MatchMode = when (this) {
        GameMode.SINGLE_PLAYER_VS_COMPUTER -> MatchMode.SINGLE
        GameMode.DUAL_PLAYER_VS_COMPUTER -> MatchMode.DUAL_VS_COMPUTER
        GameMode.PLAYER_VS_PLAYER -> MatchMode.DUAL
    }

    private companion object {
        const val TICKS = 200
        /** 68 playable maps, some in two or three modes or with several vehicles. */
        const val MIN_BOOTS = 68
    }
}
