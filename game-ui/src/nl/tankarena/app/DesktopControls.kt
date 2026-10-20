package nl.tankarena.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import nl.tankarena.input.ControlMapping
import nl.tankarena.input.PadReading
import nl.tankarena.input.Seat
import nl.tankarena.ui.compose.menu.ControlDevice
import nl.tankarena.input.Heading
import nl.tankarena.input.InputState
import nl.tankarena.protocol.InputFrame
import nl.tankarena.protocol.snapshot.LockMarkerState
import nl.tankarena.protocol.snapshot.ServerFrame
import nl.tankarena.protocol.snapshot.TankState
import nl.tankarena.ui.compose.GameMode
import nl.tankarena.ui.compose.menu.ControlAction
import nl.tankarena.ui.compose.menu.ControlMode
import nl.tankarena.ui.compose.menu.DEFAULT_CONTROLS
import nl.tankarena.ui.compose.menu.PlayerControls

internal class KeyBinding(
    val forward: Key?,
    val reverse: Key?,
    val turnLeft: Key?,
    val turnRight: Key?,
    val aimLeft: Key?,
    val aimRight: Key?,
    val fire: Key?,
    val shield: Key?,
    val weaponLeft: Key?,
    val weaponRight: Key?,
) {
    companion object {
        fun of(controls: PlayerControls) = KeyBinding(
            forward = controls.key(ControlAction.UP),
            reverse = controls.key(ControlAction.DOWN),
            turnLeft = controls.key(ControlAction.LEFT),
            turnRight = controls.key(ControlAction.RIGHT),
            aimLeft = controls.key(ControlAction.AIM_LEFT),
            aimRight = controls.key(ControlAction.AIM_RIGHT),
            fire = controls.key(ControlAction.FIRE),
            shield = controls.key(ControlAction.SHIELD),
            weaponLeft = controls.key(ControlAction.WEAPON_LEFT),
            weaponRight = controls.key(ControlAction.WEAPON_RIGHT),
        )

        /** Extended only: a WASD layout for one player, kept from earlier builds. */
        val WASD = KeyBinding(
            forward = Key.W, reverse = Key.S, turnLeft = Key.A, turnRight = Key.D,
            aimLeft = Key.Q, aimRight = Key.E, fire = Key.CtrlLeft, shield = Key.ShiftLeft,
            weaponLeft = Key.R, weaponRight = Key.F,
        )
    }
}

internal class HeldKeys {
    var forward = false
    var reverse = false
    var turnLeft = false
    var turnRight = false
    var aimLeft = false
    var aimRight = false
    var fire = false
    var shield = false
    var weaponLeft = false
    var weaponRight = false
    /** One-shot F1–F10 request; cleared once sent. */
    var selectTankId = 0

    fun state() = InputState(
        up = forward, down = reverse, left = turnLeft, right = turnRight, aimLeft = aimLeft, aimRight = aimRight,
        fire = fire, shield = shield, weaponLeft = weaponLeft, weaponRight = weaponRight,
    )

    fun apply(binding: KeyBinding, key: Key, pressed: Boolean): Boolean {
        when (key) {
            binding.forward -> forward = pressed
            binding.reverse -> reverse = pressed
            binding.turnLeft -> turnLeft = pressed
            binding.turnRight -> turnRight = pressed
            binding.aimLeft -> aimLeft = pressed
            binding.aimRight -> aimRight = pressed
            binding.fire -> fire = pressed
            binding.shield -> shield = pressed
            binding.weaponLeft -> weaponLeft = pressed
            binding.weaponRight -> weaponRight = pressed
            else -> return false
        }
        return true
    }
}

/** Legacy msg_control(): 29 steps of tinit(10), 12 ticks each on legacy steps. */
private const val MESSAGE_STEPS = 29
private const val MESSAGE_STEP_NANOS = 120_000_000L

private val TANK_SELECT_KEYS: List<Key> = listOf(
    Key.F1, Key.F2, Key.F3, Key.F4, Key.F5, Key.F6, Key.F7, Key.F8, Key.F9, Key.F10,
)

/** The keys held down; written on the UI thread, read by the simulation thread. */
internal class DesktopControls {
    var mode: GameMode = GameMode.SINGLE_PLAYER_VS_COMPUTER
    /** ESC during a game (legacy `game.exit_game`); read by the simulation thread. */
    @Volatile
    var exitRequested = false

    /** Legacy `game_pause` (P): the game stands still until the next key. */
    @Volatile
    var paused = false

    @Volatile
    private var message: Pair<HudMessage, Long>? = null

    /** Legacy create_message() on player one's window, for keys the shell handles (F11). */
    fun showPlayerOneMessage(line1: String, line2: String) {
        message = HudMessage(line1, line2, MESSAGE_STEPS) to System.nanoTime()
    }

    /** The shell's message as it counts down: one step every 12 ticks (legacy msg_control()). */
    fun playerOneMessage(): HudMessage? {
        val (shown, since) = message ?: return null
        val steps = MESSAGE_STEPS - ((System.nanoTime() - since) / MESSAGE_STEP_NANOS).toInt()
        return if (steps > 0) shown.copy(steps = steps) else null
    }
    private val players = List(MAX_PLAYERS) { HeldKeys() }

    /**
     * Who plays with what this session (Extended's join screen, ADR 0026): per player a key set
     * (index into [settings]) and a pad, either may be missing. Null: the settings decide (Classic).
     */
    @Volatile
    var seats: List<Seat>? = null

    /** The players' keys and modes (legacy TANK.CFG), set by the shell. */
    @Volatile
    var settings: List<PlayerControls> = DEFAULT_CONTROLS

    /** Extended also takes WASD for a single player. */
    @Volatile
    var extended: Boolean = false

    /**
     * Legacy `keys[]`: a single player uses player two's keys (KEYS.HLP: "player two, or player for
     * single player game"); two players have their own.
     */
    private fun controlsFor(playerId: Int): PlayerControls? = seats?.let { seats ->
        val seat = seats.getOrNull(playerId) ?: return null
        settings.getOrNull(seat.keys ?: playerId.coerceAtMost(1))
    } ?: when {
        mode == GameMode.SINGLE_PLAYER_VS_COMPUTER -> if (playerId == 0) settings.getOrNull(1) else null
        else -> settings.getOrNull(playerId)
    }

    private fun bindingsFor(playerId: Int): List<KeyBinding> {
        // A seat without keys plays with its pad only.
        if (seats?.getOrNull(playerId)?.let { it.keys == null } == true) return emptyList()
        val own = controlsFor(playerId)?.let(KeyBinding::of) ?: return emptyList()
        return if (extended && mode == GameMode.SINGLE_PLAYER_VS_COMPUTER) listOf(own, KeyBinding.WASD) else listOf(own)
    }

    @Synchronized
    fun handle(event: KeyEvent): Boolean {
        val pressed = event.type == KeyEventType.KeyDown
        val tankId = TANK_SELECT_KEYS.indexOf(event.key) + 1
        if (tankId > 0) {
            // Legacy read_controls(): F1–F10 pick a tank for `game.id[id_select]`. In two players vs
            // computer `id_select` is 1 (the keypad player) and 0 while Alt is held.
            if (pressed && mode != GameMode.PLAYER_VS_PLAYER) {
                val player = when {
                    mode != GameMode.DUAL_PLAYER_VS_COMPUTER -> 0
                    event.isAltPressed -> 0
                    else -> 1
                }
                players[player].selectTankId = tankId
            }
            return true
        }
        var handled = false
        for (playerId in players.indices) {
            for (binding in bindingsFor(playerId)) {
                if (players[playerId].apply(binding, event.key, pressed)) handled = true
            }
        }
        return handled
    }

    /**
     * The controls of this machine's player [playerId] (its keys, pad, touch screen) as the input of
     * the match's player [seat]: in a network game the two differ (ADR 0024).
     */
    @Synchronized
    fun toInputFrame(playerId: Int, inputSequence: Long, frame: ServerFrame? = null, seat: Int = playerId): InputFrame {
        val p = players[playerId]
        val selectTankId = p.selectTankId
        p.selectTankId = 0
        val controls = controlsFor(playerId)
        val pad = padFor(playerId)?.inputState(controls!!)
        var state = pad?.let { p.state().and(it) } ?: p.state()
        // A touch screen's controller is player one's.
        if (playerId == 0) touch?.state()?.let { state = state.and(it) }
        return ControlMapping.frame(
            state, seat, inputSequence, controls?.mode ?: ControlMode.RELATIVE, heading(seat, frame),
            sensitivity = controls?.sensitivity ?: ControlMapping.DEFAULT_SENSITIVITY,
            selectTankId = selectTankId,
        )
    }

    /**
     * The gamepad of a player who steers with a joystick (legacy `key_or_joy`): the pads go, in the
     * order they are numbered, to the players who chose one.
     */
    private fun padFor(playerId: Int): PadReading? = padIndex(playerId)?.let { pads().getOrNull(it) }

    /** Player one's controller on a touch screen, if it is shown. */
    @Volatile
    var touch: TouchPad? = null

    /** The connected gamepads (the platform's), read when the players' inputs are made. */
    @Volatile
    var pads: () -> List<PadReading> = { emptyList() }

    /** Which pad a player has: their seat's, or with a JOYSTICK in the settings the next one in order. */
    fun padIndex(playerId: Int): Int? {
        seats?.let { return it.getOrNull(playerId)?.pad }
        if (controlsFor(playerId)?.device != ControlDevice.JOYSTICK) return null
        val joystickPlayers = (0..1).filter { controlsFor(it)?.device == ControlDevice.JOYSTICK }
        return joystickPlayers.indexOf(playerId)
    }

    /** The player's vehicle's heading for direct steering; null while the rocket marker is out. */
    private fun heading(playerId: Int, frame: ServerFrame?): Heading? {
        val view = frame?.playerViews?.firstOrNull { it.playerId == playerId } ?: return null
        // Legacy control_tank(): while the rocket marker is out the keys always steer it as usual.
        if (view.lockMarker?.let { it.state != LockMarkerState.LAUNCHED } == true) return null
        val tank = frame.world.actors.firstOrNull { it is TankState && it.actorId == view.controlledActorId } as TankState? ?: return null
        return Heading(tank.bodyDirection, tank.turretDirection)
    }

    @Synchronized
    fun resetAll() {
        players.forEach { p ->
            p.forward = false
            p.reverse = false
            p.turnLeft = false
            p.turnRight = false
            p.aimLeft = false
            p.aimRight = false
            p.fire = false
            p.shield = false
            p.weaponLeft = false
            p.weaponRight = false
            p.selectTankId = 0
        }
    }
}

/** Local players at most (ADR 0027: two teams of two). */
internal const val MAX_PLAYERS = 4
