package nl.tankarena.ui.compose.menu

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ControlSettingsTest {
    private fun PlayerControls.classicOnly() = copy(keys = keys.take(ControlAction.CLASSIC.size))

    @Test
    fun `settings saved before the next-vehicle key get its defaults`() {
        val old = GameOptions(controls = DEFAULT_CONTROLS.map { it.classicOnly() }, mouseControls = MOUSE_PLAYER_CONTROLS.classicOnly())
        val loaded = old.withNewKeys()
        assertEquals(Key.Tab, loaded.mouseControls.key(ControlAction.NEXT_VEHICLE))
        assertEquals(Key.Enter, loaded.controls[1].key(ControlAction.NEXT_VEHICLE))
        assertNull(loaded.controls[0].key(ControlAction.NEXT_VEHICLE))
    }

    @Test
    fun `a default key someone already uses is left out`() {
        val mouse = MOUSE_PLAYER_CONTROLS.classicOnly().let { it.copy(keys = it.keys.mapIndexed { i, k -> if (i == ControlAction.FIRE.ordinal) Key.Tab.keyCode else k }) }
        val loaded = GameOptions(controls = DEFAULT_CONTROLS.map { it.classicOnly() }, mouseControls = mouse).withNewKeys()
        assertNull(loaded.mouseControls.key(ControlAction.NEXT_VEHICLE))
        assertEquals(Key.Tab, loaded.mouseControls.key(ControlAction.FIRE))
    }
}
