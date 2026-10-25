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
        // Extended's controls and keys help.
        val withSecond = nl.tankarena.ui.compose.menu.GameOptions(keyboardSecond = true)
        still(out, "ext-controls.png") { nl.tankarena.ui.compose.extended.ExtendedControlsPage(withSecond, {}, {}) }
        still(out, "ext-keys.png") {
            nl.tankarena.ui.compose.extended.ExtendedTextPage("Keys", nl.tankarena.ui.compose.extended.extendedKeysText(withSecond), {})
        }
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
        // Extended's menus in the original's look, on a desktop window and on a phone.
        val home = nl.tankarena.ui.compose.extended.ExtendedHomeActions({}, {}, {}, {}, {}, {}, {}, {})
        still(out, "ext-home.png") { nl.tankarena.ui.compose.extended.ExtendedHomeScreen(home) }
        still(out, "ext-settings.png") { nl.tankarena.ui.compose.extended.ExtendedSettingsPage(GameOptions(), {}, {}, {}) }
        val codes = listOf("FLAGISLE", "BEGIN1", "BEGIN3")
        still(out, "ext-campaign.png") { nl.tankarena.ui.compose.extended.CampaignScreen(missions, codes, "BEGIN1", {}, {}) }
        still(out, "ext-versus.png") { nl.tankarena.ui.compose.extended.VersusMapsScreen(missions, GameOptions(), "flagisle", {}, {}, {}) }
        still(out, "phone-home.png", 2340, 1080, 2.75f) { nl.tankarena.ui.compose.extended.ExtendedHomeScreen(home) }
        still(out, "phone-campaign.png", 2340, 1080, 2.75f) { nl.tankarena.ui.compose.extended.CampaignScreen(missions, codes, "BEGIN1", {}, {}) }
        still(out, "phone-upright-campaign.png", 1080, 2340, 2.75f) { nl.tankarena.ui.compose.extended.CampaignScreen(missions, codes, "BEGIN1", {}, {}) }
        still(out, "phone-settings.png", 2340, 1080, 2.75f) { nl.tankarena.ui.compose.extended.ExtendedSettingsPage(GameOptions(), {}, {}, {}) }
        still(out, "net-lobby.png") {
            nl.tankarena.ui.compose.extended.NetworkLobbyScreen(
                "FLAGISLE",
                listOf(nl.tankarena.net.LobbyMember("desktop", listOf(0)), nl.tankarena.net.LobbyMember("Pixel 7", listOf(1, 2))),
                hosting = true, addresses = listOf("192.168.1.20"), minPlayers = 2, teams = true,
                internet = "Internet: game code K7Q-4MX, private (P: list it publicly)", onTogglePublic = {}, onStart = {}, onBack = {},
            )
        }
    }

    private fun still(out: String, name: String, width: Int = 1280, height: Int = 960, density: Float = 1f, content: @androidx.compose.runtime.Composable () -> Unit) {
        ImageComposeScene(width, height, density = androidx.compose.ui.unit.Density(density), content = content).use { scene ->
            val start = System.nanoTime()
            repeat(40) {
                scene.render(System.nanoTime() - start)
                Thread.sleep(16)
            }
            File(out, name).writeBytes(scene.render(System.nanoTime() - start).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}
