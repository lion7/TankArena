package com.tankarena.ui.compose.menu

import androidx.compose.ui.input.key.Key
import com.tankarena.content.GameModeCompatibility
import com.tankarena.content.MapMetadata
import com.tankarena.content.MapSceneSidecar
import com.tankarena.content.MissionText
import com.tankarena.sim.Ruleset
import com.tankarena.ui.compose.DesktopShellScreen
import com.tankarena.ui.compose.GameMode
import com.tankarena.ui.compose.MissionOutcome
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal fun mission(
    baseName: String,
    code: String,
    next: String? = null,
    public: Boolean = false,
    compatibility: GameModeCompatibility = GameModeCompatibility.SINGLE,
    briefing: String = "Briefing of $baseName",
): MissionEntry = MissionEntry(
    code = code,
    baseName = baseName,
    briefingPreview = "",
    sidecar = MapSceneSidecar(
        metadata = MapMetadata(
            name = baseName,
            widthTiles = 10,
            heightTiles = 10,
            missionCode = code,
            nextMissionCode = next,
            publicPassword = public,
            modeCompatibility = compatibility,
        ),
        missionText = MissionText(briefing = briefing),
    ),
    document = testMap(code),
)

class MissionCodesTest {
    private val begin1 = mission("begin1", "BEGIN1", next = "BEGIN2")
    private val begin2 = mission("begin2", "BEGIN2", next = "BEGIN3")
    private val assault = mission("assualt1", "ASSUALT1", public = true)
    private val air = mission("air", "AIR STRIKE", next = "SCRAMBLE", compatibility = GameModeCompatibility.SINGLE_OR_DUAL)
    private val football = mission("denball", "FOOTBALL", compatibility = GameModeCompatibility.DUAL_VS_COMPUTER)
    private val flagIsle = mission("flagisle", "DUAL", public = true, compatibility = GameModeCompatibility.DUAL)
    private val chill = mission("chill2", "CHILL2", compatibility = GameModeCompatibility.DONT_CARE)
    private val lavaDual = mission("lava_2p", "LAVA 2P", compatibility = GameModeCompatibility.DUAL)
    private val lavaAny = mission("mission1", "LAVA 2P", compatibility = GameModeCompatibility.DONT_CARE)
    private val all = listOf(begin1, begin2, assault, air, football, flagIsle, chill, lavaDual, lavaAny)

    @Test
    fun `an exact mission code starts its mission`() {
        val result = MissionCodes.resolve(all, "BEGIN2", GameMode.SINGLE_PLAYER_VS_COMPUTER)
        assertEquals(CodeResolution.Accepted(begin2), result)
    }

    @Test
    fun `an unknown code gets the legacy hint for the mode`() {
        assertEquals(
            CodeResolution.Rejected("INVALID CODE, TRY \"BEGIN1\" FOR FIRST LEVEL."),
            MissionCodes.resolve(all, "BEGIN9", GameMode.SINGLE_PLAYER_VS_COMPUTER),
        )
        assertEquals(
            CodeResolution.Rejected("INVALID CODE, TRY \"AIR STRIKE\"."),
            MissionCodes.resolve(all, "", GameMode.DUAL_PLAYER_VS_COMPUTER),
        )
    }

    @Test
    fun `codes compare exactly like strcmp`() {
        assertIs<CodeResolution.Rejected>(MissionCodes.resolve(all, "begin1", GameMode.SINGLE_PLAYER_VS_COMPUTER))
    }

    @Test
    fun `a map of another mode is not suitable`() {
        assertEquals(
            CodeResolution.Rejected(MissionCodes.NOT_SUITABLE),
            MissionCodes.resolve(all, "FOOTBALL", GameMode.SINGLE_PLAYER_VS_COMPUTER),
        )
        assertEquals(CodeResolution.Accepted(football), MissionCodes.resolve(all, "FOOTBALL", GameMode.DUAL_PLAYER_VS_COMPUTER))
    }

    @Test
    fun `a shared code prefers the map that suits the mode`() {
        assertEquals(CodeResolution.Accepted(lavaAny), MissionCodes.resolve(all, "LAVA 2P", GameMode.SINGLE_PLAYER_VS_COMPUTER))
    }

    @Test
    fun `player vs player opens maps by file name`() {
        assertEquals(CodeResolution.Accepted(flagIsle), MissionCodes.resolve(all, "FLAGISLE", GameMode.PLAYER_VS_PLAYER))
        assertEquals(
            CodeResolution.Rejected("FILE NOT FOUND"),
            MissionCodes.resolve(all, "NOPE", GameMode.PLAYER_VS_PLAYER),
        )
        assertEquals(
            CodeResolution.Rejected(MissionCodes.NOT_SUITABLE),
            MissionCodes.resolve(all, "BEGIN1", GameMode.PLAYER_VS_PLAYER),
        )
    }

    @Test
    fun `Extended lists reached codes first, then public and dual-vs-computer codes, of the mode only`() {
        val reached = listOf("BEGIN2", "BOGUS", "ASSUALT1")
        assertEquals(
            listOf("BEGIN2", "ASSUALT1"),
            MissionCodes.knownPasswords(all, reached, GameMode.SINGLE_PLAYER_VS_COMPUTER, onlyForMode = true),
        )
        assertEquals(
            listOf("FOOTBALL"),
            MissionCodes.knownPasswords(all, reached, GameMode.DUAL_PLAYER_VS_COMPUTER, onlyForMode = true),
        )
    }

    @Test
    fun `Classic lists every known code in every mode, like the original`() {
        val reached = listOf("BEGIN2", "BOGUS", "ASSUALT1")
        val single = MissionCodes.knownPasswords(all, reached, GameMode.SINGLE_PLAYER_VS_COMPUTER)
        assertEquals(single, MissionCodes.knownPasswords(all, reached, GameMode.DUAL_PLAYER_VS_COMPUTER))
        assertEquals(listOf("BEGIN2", "BOGUS", "ASSUALT1"), single.take(3))
        assertTrue("FOOTBALL" in single)
    }

    @Test
    fun `the player vs player list shows dual and dont-care maps sorted by name`() {
        assertEquals(listOf("CHILL2", "FLAGISLE", "LAVA_2P", "MISSION1"), MissionCodes.selectableMaps(all))
    }

    @Test
    fun `prompt typing keeps legacy characters, upper case and the length limit`() {
        assertEquals("AB", "A".typeInPrompt('b', 20))
        assertEquals("A ", "A".typeInPrompt(' ', 20))
        assertEquals("A", "A".typeInPrompt('-', 20))
        assertEquals("ABCDEFGH", "ABCDEFGH".typeInPrompt('I', MissionCodes.MAX_MAP_NAME_LENGTH))
        assertEquals("BEGIN", "BEGIN1".backspaceInPrompt())
        assertEquals("", "".backspaceInPrompt())
        assertEquals('Q', promptCharFor(Key.Q))
        assertEquals('7', promptCharFor(Key.Seven))
        assertEquals(null, promptCharFor(Key.Minus))
    }

    @Test
    fun `the gate remembers typed codes and moves on to the next code after a win`() {
        val dir = Files.createTempDirectory("tankarena-gate")
        val store = PasswordStore(dir.resolve("passwd.json"))
        val gate = MissionGate(store, GameSettings().apply { update(options.copy(ruleset = Ruleset.EXTENDED)) })
        val mode = GameMode.SINGLE_PLAYER_VS_COMPUTER
        assertEquals("BEGIN1", gate.currentCode(mode))

        val wrong = gate.submit(all, "WRONG", mode)
        assertIs<DesktopShellScreen.Message>(wrong)
        assertEquals(DesktopShellScreen.GameModeSelect, wrong.next)
        assertEquals("WRONG", gate.currentCode(mode))
        assertEquals(emptyList(), store.load())

        assertEquals(DesktopShellScreen.Briefing(begin1, mode), gate.submit(all, "BEGIN1", mode))
        assertEquals(listOf("BEGIN1"), store.load())

        gate.missionEnded(begin1, mode, MissionOutcome.LOST)
        assertEquals("BEGIN1", gate.currentCode(mode))
        gate.missionEnded(begin1, mode, MissionOutcome.WON)
        assertEquals("BEGIN2", gate.currentCode(mode))
        assertEquals(listOf("BEGIN1", "BEGIN2"), store.load())
        assertEquals(listOf("BEGIN1", "BEGIN2", "ASSUALT1"), gate.listEntries(all, mode))
        assertEquals("AIR STRIKE", gate.currentCode(GameMode.DUAL_PLAYER_VS_COMPUTER))
    }

    @Test
    fun `the last code of each mode survives a restart`() {
        val dir = Files.createTempDirectory("tankarena-gate")
        val passwords = PasswordStore(dir.resolve("passwd.json"))
        val settingsFile = dir.resolve("settings.json")
        val mode = GameMode.SINGLE_PLAYER_VS_COMPUTER
        val gate = MissionGate(passwords, GameSettings(GameOptionsStore(settingsFile)))
        gate.submit(all, "BEGIN1", mode)
        gate.missionEnded(begin1, mode, MissionOutcome.WON)
        gate.submit(all, "CHILL2", GameMode.PLAYER_VS_PLAYER)

        val restarted = MissionGate(passwords, GameSettings(GameOptionsStore(settingsFile)))
        assertEquals("BEGIN2", restarted.currentCode(mode))
        assertEquals("CHILL2", restarted.currentCode(GameMode.PLAYER_VS_PLAYER))
        assertEquals("AIR STRIKE", restarted.currentCode(GameMode.DUAL_PLAYER_VS_COMPUTER))
    }

    @Test
    fun `a mission without briefing starts right away`() {
        val silent = mission("silent", "SILENT", briefing = "")
        val gate = MissionGate(PasswordStore(Files.createTempDirectory("tankarena-gate").resolve("passwd.json")))
        assertEquals(
            DesktopShellScreen.Playing(silent, GameMode.SINGLE_PLAYER_VS_COMPUTER),
            gate.submit(listOf(silent), "SILENT", GameMode.SINGLE_PLAYER_VS_COMPUTER),
        )
    }
}
