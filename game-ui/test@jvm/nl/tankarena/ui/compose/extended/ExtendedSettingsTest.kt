package nl.tankarena.ui.compose.extended

import nl.tankarena.ui.compose.menu.GameOptions
import kotlin.test.Test
import kotlin.test.assertEquals

class ExtendedSettingsTest {
    @Test
    fun `left and right step a setting and stop at the ends`() {
        val options = GameOptions()
        assertEquals(1, options.stepped(ExtendedSetting.GAMMA, up = true).gamma - options.gamma)
        assertEquals(3, options.copy(gamma = 3).stepped(ExtendedSetting.GAMMA, up = true).gamma)
        assertEquals(-3, options.copy(gamma = -3).stepped(ExtendedSetting.GAMMA, up = false).gamma)
        assertEquals(0, options.copy(windowSize = 1).stepped(ExtendedSetting.WINDOW_SIZE, up = true).windowSize, "right: a bigger window")
        assertEquals(GameOptions.ENDLESS, options.copy(rounds = 9).stepped(ExtendedSetting.ROUNDS, up = true).rounds)
        assertEquals(GameOptions.ENDLESS, options.copy(rounds = GameOptions.ENDLESS).stepped(ExtendedSetting.ROUNDS, up = true).rounds)
        assertEquals(1, options.copy(rounds = 1).stepped(ExtendedSetting.ROUNDS, up = false).rounds)
        assertEquals(255, options.copy(soundVolume = 255).stepped(ExtendedSetting.SOUND, up = true).soundVolume)
        assertEquals(170, options.copy(soundVolume = 255).stepped(ExtendedSetting.SOUND, up = false).soundVolume)
        assertEquals(!options.wideView, options.stepped(ExtendedSetting.WIDE_VIEW, up = false).wideView)
    }
}
