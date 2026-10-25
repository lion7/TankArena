package nl.tankarena.app

import nl.tankarena.ui.compose.menu.MOUSE_PLAYER_CONTROLS
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import nl.tankarena.ui.compose.menu.SelectionKey

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
    /** Extended only. */
    val nextTank: Key? = null,
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
            nextTank = controls.key(ControlAction.NEXT_VEHICLE),
        )

        /** Extended: the arrow keys drive player one too (the keyboard is theirs alone). */
        val ARROWS = KeyBinding(
            forward = Key.DirectionUp, reverse = Key.DirectionDown, turnLeft = Key.DirectionLeft, turnRight = Key.DirectionRight,
            aimLeft = null, aimRight = null, fire = null, shield = null, weaponLeft = null, weaponRight = null,
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
    /** One-shot next-vehicle request; cleared once sent. */
    var nextTank = false
    /** The next-vehicle key and pad button held, so that holding them asks only once. */
    var nextTankKeyHeld = false
    var nextTankPadHeld = false

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

/** A mouse this close to its vehicle does not turn the turret. */
private const val MOUSE_DEAD_ZONE_PX = 8.0

/** A wheel notch: weapon switch held this many ticks, out of [WHEEL_CYCLE_TICKS] (past the 15-tick switch cooldown). */
private const val WHEEL_HOLD_TICKS = 6
private const val WHEEL_CYCLE_TICKS = 18

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

    /** Extended: ESC asked whether to leave; a local game stands still until it is answered. */
    var leaveAsked: Boolean by mutableStateOf(false)
        private set

    /** ESC: Classic leaves at once (legacy `game.exit_game`), Extended asks first. */
    fun askToLeave() {
        if (!extended) {
            exitRequested = true
            return
        }
        resetAll()
        leaveAsked = true
    }

    fun answerLeave(leave: Boolean) {
        leaveAsked = false
        if (leave) exitRequested = true
    }

    /** Legacy `game_pause` (P): the game stands still until the next key (Extended shows it). */
    var paused: Boolean by mutableStateOf(false)

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
     * Who plays with what this session (Extended's join screen, ADR 0026): player one this machine's
     * keyboard and mouse, the others a gamepad each. Null: the settings decide (Classic).
     */
    @Volatile
    var seats: List<Seat>? = null

    /** The players' keys and modes (legacy TANK.CFG), set by the shell. */
    @Volatile
    var settings: List<PlayerControls> = DEFAULT_CONTROLS

    @Volatile
    var extended: Boolean = false

    /** Extended's player one's keys and steering ([Seat.Own]). */
    @Volatile
    var mouseControls: PlayerControls = MOUSE_PLAYER_CONTROLS

    /** Extended: gamepads are twin sticks, not the original's joystick. */
    @Volatile
    var twinSticks: Boolean = true

    /** Extended's player one's mouse, fed by their view. */
    val mouse = MousePointer()

    // A wheel notch holds a weapon switch for a few ticks, then waits out the switch's cooldown.
    private var wheelTicks = 0
    private var wheelStep = 0

    /**
     * Legacy `keys[]`: a single player uses player two's keys (KEYS.HLP: "player two, or player for
     * single player game"); two players have their own.
     */
    private fun controlsFor(playerId: Int): PlayerControls? = seats?.let { seats ->
        // A pad in the original's joystick mode has the buttons of player one's or two's settings.
        when (seats.getOrNull(playerId) ?: return null) {
            is Seat.Own -> mouseControls
            is Seat.Pad -> settings.getOrNull(playerId.coerceAtMost(1))
        }
    } ?: when {
        mode == GameMode.SINGLE_PLAYER_VS_COMPUTER -> if (playerId == 0) settings.getOrNull(1) else null
        else -> settings.getOrNull(playerId)
    }

    private fun bindingsFor(playerId: Int): List<KeyBinding> {
        val seats = seats ?: return listOfNotNull(controlsFor(playerId)?.let(KeyBinding::of))
        // Extended: the keyboard is player one's, the arrow keys too; a pad player has no keys.
        return when (seats.getOrNull(playerId)) {
            is Seat.Own -> listOf(KeyBinding.of(mouseControls), KeyBinding.ARROWS)
            else -> emptyList()
        }
    }

    /**
     * The vehicle selection's move for [key] and whose it is (legacy select_tanks(): each player moves
     * their marker with their own drive keys and confirms with fire). Classic has both key sets; with
     * [seats] only player one's keys, so a gamepad player's cursor does not move from the keyboard.
     */
    fun selectionKey(key: Key): Pair<Int, SelectionKey>? {
        val bindings: (Int) -> List<KeyBinding> =
            if (seats == null) { playerId -> listOfNotNull(settings.take(2).getOrNull(playerId)?.let(KeyBinding::of)) } else ::bindingsFor
        for (playerId in players.indices) {
            for (binding in bindings(playerId)) {
                val action = when (key) {
                    binding.forward -> SelectionKey.UP
                    binding.reverse -> SelectionKey.DOWN
                    binding.turnLeft -> SelectionKey.LEFT
                    binding.turnRight -> SelectionKey.RIGHT
                    binding.fire -> SelectionKey.FIRE
                    else -> null
                }
                if (action != null) return playerId to action
            }
        }
        return null
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
        if (extended) {
            for (playerId in players.indices) {
                if (bindingsFor(playerId).none { it.nextTank == event.key }) continue
                val p = players[playerId]
                if (pressed && !p.nextTankKeyHeld) p.nextTank = true
                p.nextTankKeyHeld = pressed
                handled = true
            }
            if (handled) return true
        }
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
        var nextTank = p.nextTank
        p.nextTank = false
        val controls = controlsFor(playerId)
        val devices = seats?.getOrNull(playerId)
        val pad = padFor(playerId)?.let { reading -> if (extended && twinSticks && devices != null) reading.twinStick() else reading.inputState(controls!!) }
        var state = pad?.let { p.state().and(it) } ?: p.state()
        // A touch screen's controller is player one's.
        if (playerId == 0) touch?.let { touchPad ->
            touchPad.state()?.let { state = state.and(it) }
            if (extended && touchPad.takeNext()) nextTank = true
        }
        if (devices is Seat.Own) state = withMouse(state, ownTank(seat, frame))
        // The pad's button asks once when pressed; Classic has only F1–F10.
        val padNext = extended && pad?.nextTank == true
        if (padNext && !p.nextTankPadHeld) nextTank = true
        p.nextTankPadHeld = padNext
        return ControlMapping.frame(
            state, seat, inputSequence, controls?.mode ?: ControlMode.RELATIVE, heading(seat, frame),
            sensitivity = controls?.sensitivity ?: ControlMapping.DEFAULT_SENSITIVITY,
            selectTankId = selectTankId,
            nextTank = nextTank,
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

    /** The vehicle the player in the match's seat [seat] drives, if any. */
    private fun ownTank(seat: Int, frame: ServerFrame?): TankState? {
        val view = frame?.playerViews?.firstOrNull { it.playerId == seat } ?: return null
        return frame.world.actors.firstOrNull { it is TankState && it.actorId == view.controlledActorId } as TankState?
    }

    /**
     * The mouse added to player one's [state]: the turret turns towards where it points, the
     * buttons fire and shield, the wheel switches weapons, and while fire is held the rocket marker
     * goes where it points.
     */
    private fun withMouse(state: InputState, tank: TankState?): InputState {
        var aimX = 0
        var aimY = 0
        if (mouse.pointing && tank != null) {
            val dx = (mouse.worldX - tank.x).toDouble()
            val dy = (mouse.worldY - tank.y).toDouble()
            val distance = kotlin.math.hypot(dx, dy)
            if (distance > MOUSE_DEAD_ZONE_PX) {
                aimX = (dx * 100 / distance).toInt()
                aimY = (dy * 100 / distance).toInt()
            }
        }
        if (wheelTicks > 0) {
            wheelTicks--
        } else {
            wheelStep = mouse.takeWheel()
            if (wheelStep != 0) wheelTicks = WHEEL_CYCLE_TICKS
        }
        val holding = wheelTicks > WHEEL_CYCLE_TICKS - WHEEL_HOLD_TICKS
        val fire = state.fire || mouse.primary
        return state.copy(
            fire = fire,
            shield = state.shield || mouse.secondary,
            aimX = aimX,
            aimY = aimY,
            weaponLeft = state.weaponLeft || (holding && wheelStep < 0),
            weaponRight = state.weaponRight || (holding && wheelStep > 0),
            pointX = if (fire && mouse.pointing) mouse.worldX else -1,
            pointY = if (fire && mouse.pointing) mouse.worldY else -1,
        )
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
            p.nextTank = false
            p.nextTankKeyHeld = false
            p.nextTankPadHeld = false
        }
    }
}

/** Local players at most (ADR 0027: two teams of two). */
internal const val MAX_PLAYERS = 4
