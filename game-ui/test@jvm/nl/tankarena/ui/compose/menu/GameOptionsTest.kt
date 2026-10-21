package nl.tankarena.ui.compose.menu

import nl.tankarena.content.MapMetadata
import nl.tankarena.content.MapSceneSidecar
import nl.tankarena.content.MissionText
import nl.tankarena.sim.Ruleset
import nl.tankarena.ui.compose.DesktopShellScreen
import nl.tankarena.ui.compose.DualMatch
import nl.tankarena.ui.compose.GameMode
import nl.tankarena.ui.compose.MissionOutcome
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Legacy game_options() (original/src/game/menu.c), save_settings() and what the options change. */
class GameOptionsTest {

    @Test
    fun `each entry cycles through the original values`() {
        var options = GameOptions()
        assertEquals("Race consists of 3 laps", options.labels[0])
        assertEquals(listOf(4, 5, 6, 1, 2, 3), List(6) { options = options.nextLaps(); options.raceLaps })
        assertEquals(listOf(5, 7, 9, GameOptions.ENDLESS, 1, 3), List(6) { options = options.nextRounds(); options.rounds })
        assertEquals("Match consists of 3 rounds", options.labels[1])
        assertEquals("Match consists of ì rounds", GameOptions(rounds = 9).nextRounds().labels[1])
        assertEquals(
            listOf("Losers tank enabled at 1 loss", "Losers tank enabled at 3 losses", "Losers tank disabled"),
            List(3) { options = options.nextLosersTank(); options.labels[2] },
        )
        assertEquals(listOf(1, 2, 3, -3, -2, -1, 0), List(7) { options = options.nextGamma(); options.gamma })
        assertEquals(listOf(0, 85, 170, 255), List(4) { options = options.nextSoundVolume(); options.soundVolume })
        assertEquals(listOf("Music off", "Music volume - Low"), List(2) { options = options.nextMusicVolume(); options.labels[4] })
        assertEquals("Leave this menu", options.labels.last())
    }

    @Test
    fun `a match is decided by a majority of its rounds, and never when endless`() {
        assertFalse(GameOptions(rounds = 5).matchDecided(listOf(2, 2)))
        assertTrue(GameOptions(rounds = 5).matchDecided(listOf(3, 1)))
        assertTrue(GameOptions(rounds = 1).matchDecided(listOf(0, 1)))
        assertFalse(GameOptions(rounds = GameOptions.ENDLESS).matchDecided(listOf(40, 0)))
    }

    @Test
    fun `the losers tank is there for a player far enough behind`() {
        val tanks = 1
        assertFalse(allowedTanks(tanks, listOf(1, 0), losersTank = 0)[7])
        assertTrue(allowedTanks(tanks, listOf(1, 0), losersTank = 1)[7])
        assertFalse(allowedTanks(tanks, listOf(2, 0), losersTank = 2)[7])
        assertTrue(allowedTanks(tanks, listOf(0, 3), losersTank = 2)[7])
    }

    @Test
    fun `gamma correction follows correct_pal`() {
        assertNull(GameOptions.gammaTable(0))
        val darker = GameOptions.gammaTable(-3)!!
        val lighter = GameOptions.gammaTable(3)!!
        assertEquals(0, darker[0].toInt())
        assertEquals(255, darker[255].toInt() and 0xFF)
        assertTrue((darker[128].toInt() and 0xFF) < 128)
        assertTrue((lighter[128].toInt() and 0xFF) > 128)
    }

    @Test
    fun `the options survive a restart`() {
        val file = Files.createTempDirectory("tankarena-settings").resolve("settings.json").toFile()
        GameSettings(GameOptionsStore(file)).update(GameOptions(raceLaps = 5, rounds = GameOptions.ENDLESS, losersTank = 2))
        assertEquals(GameOptions(raceLaps = 5, rounds = GameOptions.ENDLESS, losersTank = 2), GameSettings(GameOptionsStore(file)).options)
    }

    @Test
    fun `the ruleset is unchosen in old settings and kept once chosen`() {
        val file = Files.createTempDirectory("settings").resolve("settings.json").toFile()
        file.writeText("""{"raceLaps":4}""")
        assertNull(GameSettings(GameOptionsStore(file)).options.ruleset)
        GameSettings(GameOptionsStore(file)).let { it.update(it.options.copy(ruleset = Ruleset.EXTENDED)) }
        assertEquals(GameOptions(raceLaps = 4, ruleset = Ruleset.EXTENDED), GameSettings(GameOptionsStore(file)).options)
    }

    @Test
    fun `every fifth mission won plays THUNDER before the success text`() {
        val mission = MissionEntry(
            "BEGIN1", "begin1", "",
            MapSceneSidecar(
                metadata = MapMetadata("BEGIN1", 10, 10, "BEGIN1", nextMissionCode = "BEGIN2"),
                missionText = MissionText(briefing = "GO", success = "WELL DONE", failure = "TOO BAD"),
            ),
            testMap(),
        )
        val settings = GameSettings()
        val gate = MissionGate(PasswordStore(Files.createTempDirectory("tankarena-gate").resolve("passwd.json").toFile()), settings)
        val mode = GameMode.SINGLE_PLAYER_VS_COMPUTER
        repeat(4) { assertEquals(DesktopShellScreen.AfterMission("WELL DONE"), gate.missionEnded(mission, mode, MissionOutcome.WON)) }
        assertEquals(DesktopShellScreen.AfterMission("TOO BAD", won = false), gate.missionEnded(mission, mode, MissionOutcome.LOST))
        assertEquals(
            DesktopShellScreen.Animation(MissionGate.THUNDER_FLI, DesktopShellScreen.AfterMission("WELL DONE")),
            gate.missionEnded(mission, mode, MissionOutcome.WON),
        )
        assertEquals(0, settings.options.victoryCount)
    }

    @Test
    fun `a decided match plays VICTORY, and its rounds come from the options`() {
        val mission = MissionEntry("FLAGISLE", "flagisle", "", MapSceneSidecar(metadata = MapMetadata("FLAGISLE", 10, 10, "DUAL")), testMap())
        val settings = GameSettings().apply { update(GameOptions(rounds = 1)) }
        val gate = MissionGate(PasswordStore(Files.createTempDirectory("tankarena-gate").resolve("passwd.json").toFile()), settings)
        val select = assertIs<DesktopShellScreen.TankSelect>(gate.afterBriefing(mission, GameMode.PLAYER_VS_PLAYER))
        assertEquals(1, select.match.options.rounds)
        assertEquals(
            DesktopShellScreen.Animation(MissionGate.VICTORY_FLI, DesktopShellScreen.GameModeSelect),
            gate.roundEnded(select.match, winner = 1),
        )
        assertEquals(DesktopShellScreen.GameModeSelect, gate.roundEnded(DualMatch(mission), winner = null))
        assertIs<DesktopShellScreen.TankSelect>(gate.roundEnded(DualMatch(mission), winner = 0))
    }
}
