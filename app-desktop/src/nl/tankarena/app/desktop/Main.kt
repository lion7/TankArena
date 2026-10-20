package nl.tankarena.app.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import nl.tankarena.app.GameKeyHook
import nl.tankarena.app.GamePlatform
import nl.tankarena.app.Music
import nl.tankarena.app.TankArenaApp
import nl.tankarena.audio.AudioBackend
import nl.tankarena.audio.desktopAudioBackend
import nl.tankarena.audio.MusicPlayer
import nl.tankarena.input.Gamepads
import nl.tankarena.input.PadReading
import nl.tankarena.sim.Cheats
import nl.tankarena.ui.resources.Res
import nl.tankarena.ui.resources.pic_tankico
import org.jetbrains.compose.resources.painterResource
import java.io.File

/** `-Dtankarena.debugOverlay=true` draws viewport/camera geometry over the playfield. */
fun main(args: Array<String>) = application {
    val platform = remember { DesktopPlatform(::exitApplication) }
    val keyHook = remember { GameKeyHook() }
    androidx.compose.ui.window.Window(
        onCloseRequest = ::exitApplication,
        title = "Tank Arena Rewrite",
        // The original's TANKICO picture.
        icon = painterResource(Res.drawable.pic_tankico),
        state = rememberWindowState(placement = WindowPlacement.Maximized),
        onPreviewKeyEvent = keyHook::handle,
    ) {
        platform.window = window
        TankArenaApp(platform, keyHook, args.toList(), Cheats.fromArgs(args))
    }
}

/** The desktop (ADR 0025): `javax.sound` mixer and MIDI, Jamepad's pads, AWT for keys and screenshots. */
private class DesktopPlatform(private val exit: () -> Unit) : GamePlatform {
    var window: java.awt.Window? = null

    override fun audioBackend(): AudioBackend = desktopAudioBackend()

    override val music: Music = MusicPlayer()

    override fun gamepads(): List<PadReading> = Gamepads.read()

    override val postKey: ((Key) -> Unit) = { key -> window?.let { pressKey(it, key.nativeKeyCode) } }

    override fun quit() = exit()

    override fun screenshot(file: File): Boolean {
        val content = (window as? javax.swing.JFrame)?.contentPane ?: return false
        return try {
            val image = java.awt.Robot().createScreenCapture(java.awt.Rectangle(content.locationOnScreen, content.size))
            javax.imageio.ImageIO.write(image, "png", file)
        } catch (e: Exception) {
            System.err.println("Could not save a screenshot: ${e.message}")
            false
        }
    }
}

/** Posts a key press and release to the window's focused component, as if typed. */
private fun pressKey(window: java.awt.Window, code: Int) {
    val target = window.focusOwner ?: window
    val queue = java.awt.Toolkit.getDefaultToolkit().systemEventQueue
    val time = System.currentTimeMillis()
    val char = if (code == java.awt.event.KeyEvent.VK_ENTER) '\n' else java.awt.event.KeyEvent.CHAR_UNDEFINED
    queue.postEvent(java.awt.event.KeyEvent(target, java.awt.event.KeyEvent.KEY_PRESSED, time, 0, code, char))
    queue.postEvent(java.awt.event.KeyEvent(target, java.awt.event.KeyEvent.KEY_RELEASED, time, 0, code, char))
}

