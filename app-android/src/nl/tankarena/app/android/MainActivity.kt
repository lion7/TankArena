package nl.tankarena.app.android

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import nl.tankarena.app.AppDirs
import nl.tankarena.app.GameKeyHook
import nl.tankarena.app.TankArenaApp
import java.io.File

/**
 * Tank Arena on Android (ADR 0025): the whole game full screen, landscape. Keys and gamepad
 * buttons go to the game first ([GameKeyHook]); gamepads also work the menus (A and START are
 * ENTER, B, BACK and the device's back button are ESC).
 */
class MainActivity : ComponentActivity() {
    private lateinit var platform: AndroidPlatform
    private val keyHook = GameKeyHook()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The player's maps, recordings and exports where a computer can reach them over USB.
        val storage = getExternalFilesDir(null) ?: filesDir
        AppDirs.config = File(filesDir, "config")
        AppDirs.data = storage
        platform = AndroidPlatform(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { TankArenaApp(platform, keyHook, emptyList()) }
        // Android 16 delivers the back button and gesture here, not as a key: it is ESC to the game.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val now = SystemClock.uptimeMillis()
                dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE, 0))
                dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE, 0))
            }
        })
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        platform.pads.onKey(event)
        val mapped = menuKey(event) ?: event
        if (keyHook.handle(androidx.compose.ui.input.key.KeyEvent(mapped))) return true
        return super.dispatchKeyEvent(mapped)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) && platform.pads.onMotion(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    /** A gamepad's buttons as the keys the menus know; null to keep the event as it is. */
    private fun menuKey(event: KeyEvent): KeyEvent? {
        val code = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_DPAD_CENTER -> KeyEvent.KEYCODE_ENTER
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BACK -> KeyEvent.KEYCODE_ESCAPE
            KeyEvent.KEYCODE_BUTTON_L1 -> KeyEvent.KEYCODE_PAGE_UP
            KeyEvent.KEYCODE_BUTTON_R1 -> KeyEvent.KEYCODE_PAGE_DOWN
            else -> return null
        }
        return KeyEvent(event.downTime, event.eventTime, event.action, code, event.repeatCount, event.metaState, event.deviceId, event.scanCode, event.flags, event.source)
    }

    override fun onPause() {
        super.onPause()
        platform.music.pause()
    }

    override fun onResume() {
        super.onResume()
        platform.music.resume()
    }

    override fun onDestroy() {
        platform.music.dispose()
        super.onDestroy()
    }
}
