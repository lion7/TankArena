package nl.tankarena.ui.compose.legacy

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import nl.tankarena.ui.compose.DualMatch
import nl.tankarena.ui.compose.menu.Credits
import nl.tankarena.ui.compose.menu.CreditsScreen
import nl.tankarena.ui.compose.menu.GameOptions
import nl.tankarena.ui.compose.menu.GameOptionsScreen
import nl.tankarena.ui.compose.menu.MissionCatalog
import nl.tankarena.ui.compose.menu.TankSelectScreen
import java.io.File
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat

/** Not a test: `DUMP_DIR=... ./kotlin test -m game-ui` renders screens offscreen to PNG. */
class ScreenDumpTest {
    @Test
    fun dump() {
        val out = System.getenv("DUMP_DIR") ?: return
        val missions = runBlocking { MissionCatalog.load() }
        val flagIsle = missions.first { it.baseName == "flagisle" }
        val select = missions.first { it.baseName == "select" }
        ImageComposeScene(1280, 960) {
            TankSelectScreen(DualMatch(flagIsle), select, listOf(0, 5), { null }, {}, {})
        }.use { scene ->
            val start = System.nanoTime()
            for (shot in 1..3) {
                repeat(60) {
                    scene.render(System.nanoTime() - start)
                    Thread.sleep(16)
                }
                val image = scene.render(System.nanoTime() - start)
                File(out, "select$shot.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            }
        }
        still(out, "options.png") { GameOptionsScreen(GameOptions(rounds = GameOptions.ENDLESS, losersTank = 1), {}, {}) }
        still(out, "credits.png") { CreditsScreen("credits.bmp", 70, 40, Credits.CREDITS, {}) }
        still(out, "thanks.png") { CreditsScreen("credits2.bmp", 160, 100, Credits.THANKS, {}) }
        // The network game's screens.
        val round = nl.tankarena.net.ServerMessage.RoundOver(round = 2, winner = 1, wins = listOf(1, 1), missionWon = null, matchOver = false)
        still(out, "net-result.png") { nl.tankarena.ui.compose.extended.NetworkResultScreen(round, mySide = 1, hosting = false, {}, {}) }
        val decided = round.copy(round = 3, winner = 0, wins = listOf(2, 1), matchOver = true)
        still(out, "net-result-match.png") { nl.tankarena.ui.compose.extended.NetworkResultScreen(decided, mySide = 1, hosting = true, {}, {}) }
        val beacon = nl.tankarena.net.LanBeacon(3, 9, 6, "living room", nl.tankarena.net.DEFAULT_PORT, "flagisle", 1, 4)
        still(out, "net-join.png") {
            nl.tankarena.ui.compose.extended.NetworkAddressScreen(
                listOf(nl.tankarena.net.LanGame("192.168.1.20", beacon)),
                listOf(nl.tankarena.net.RelayGame("K7Q4MX", "Ana's laptop", "chaos4u", 2, 4, 3, 9, 6)),
                "", false, null, {}, {},
            )
        }
        still(out, "net-lobby.png") {
            nl.tankarena.ui.compose.extended.NetworkLobbyScreen(
                "FLAGISLE",
                listOf(nl.tankarena.net.LobbyMember("desktop", listOf(0)), nl.tankarena.net.LobbyMember("Pixel 7", listOf(1, 2))),
                hosting = true, addresses = listOf("192.168.1.20"), minPlayers = 2, teams = true,
                internet = "Internet: game code K7Q-4MX, private (P: list it publicly)", onTogglePublic = {}, onStart = {}, onBack = {},
            )
        }
    }

    private fun still(out: String, name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        ImageComposeScene(1280, 960, content = content).use { scene ->
            val start = System.nanoTime()
            repeat(40) {
                scene.render(System.nanoTime() - start)
                Thread.sleep(16)
            }
            File(out, name).writeBytes(scene.render(System.nanoTime() - start).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}
