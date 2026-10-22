package nl.tankarena.input

import nl.tankarena.core.LegacyDirections
import nl.tankarena.protocol.InputFrame
import nl.tankarena.ui.compose.menu.ControlMode

/**
 * What one player's devices hold at one moment (ADR 0026): the original's keys (legacy `ks[]`,
 * `bs[]`) and a joystick or stick (legacy `js[]`): [stickX], [stickY] from -100 to 100 (up and left
 * negative), with the buttons that make the stick aim the turret ([aimHeld]) or switch weapons
 * ([weaponHeld]).
 */
data class InputState(
    val up: Boolean = false,
    val down: Boolean = false,
    val left: Boolean = false,
    val right: Boolean = false,
    val aimLeft: Boolean = false,
    val aimRight: Boolean = false,
    val fire: Boolean = false,
    val shield: Boolean = false,
    val weaponLeft: Boolean = false,
    val weaponRight: Boolean = false,
    val stickX: Int = 0,
    val stickY: Int = 0,
    val aimHeld: Boolean = false,
    val weaponHeld: Boolean = false,
    /** A second stick (touch controls, Extended): the way to turn the turret, -100..100. */
    val aimX: Int = 0,
    val aimY: Int = 0,
    /** The stick steers directly whatever the player's setting (a touch screen's stick). */
    val directStick: Boolean = false,
    /** The map position pointed at with a mouse (the rocket marker), world pixels; -1 for none. */
    val pointX: Int = -1,
    val pointY: Int = -1,
) {
    val hasStick: Boolean get() = stickX != 0 || stickY != 0 || aimHeld || weaponHeld

    /** Two devices of one player together: a key or button held on either counts, the stick pushed furthest wins. */
    fun and(other: InputState): InputState = InputState(
        up = up || other.up,
        down = down || other.down,
        left = left || other.left,
        right = right || other.right,
        aimLeft = aimLeft || other.aimLeft,
        aimRight = aimRight || other.aimRight,
        fire = fire || other.fire,
        shield = shield || other.shield,
        weaponLeft = weaponLeft || other.weaponLeft,
        weaponRight = weaponRight || other.weaponRight,
        stickX = if (kotlin.math.abs(other.stickX) > kotlin.math.abs(stickX)) other.stickX else stickX,
        stickY = if (kotlin.math.abs(other.stickY) > kotlin.math.abs(stickY)) other.stickY else stickY,
        aimHeld = aimHeld || other.aimHeld,
        weaponHeld = weaponHeld || other.weaponHeld,
        aimX = if (kotlin.math.abs(other.aimX) > kotlin.math.abs(aimX)) other.aimX else aimX,
        aimY = if (kotlin.math.abs(other.aimY) > kotlin.math.abs(aimY)) other.aimY else aimY,
        directStick = directStick || other.directStick,
        pointX = if (other.pointX >= 0) other.pointX else pointX,
        pointY = if (other.pointX >= 0) other.pointY else pointY,
    )
}

/** The vehicle a player drives, for direct steering: which way its body and turret face. */
data class Heading(val body: Int, val turret: Int)

/**
 * Legacy copy_controls_relative() and copy_controls_direct() (original/src/game/controls.c:339-470): what a
 * player's keys and stick ask of their vehicle. [sensitivity] is the stick's dead zone (legacy
 * `joy[i].sensitivity`, 50 by default). Direct steering needs the vehicle's [heading]; without it
 * (no vehicle, or the rocket marker out) the controls are read as relative.
 */
object ControlMapping {
    const val DEFAULT_SENSITIVITY = 50

    fun frame(
        state: InputState,
        playerId: Int,
        inputSequence: Long,
        mode: ControlMode,
        heading: Heading? = null,
        sensitivity: Int = DEFAULT_SENSITIVITY,
        selectTankId: Int = 0,
    ): InputFrame {
        val base = InputFrame(
            playerId = playerId,
            inputSequence = inputSequence,
            firePrimary = state.fire,
            shield = state.shield,
            cycleWeaponLeft = state.weaponLeft,
            cycleWeaponRight = state.weaponRight,
            aimLeft = state.aimLeft,
            aimRight = state.aimRight,
            selectTankId = selectTankId,
            pointX = state.pointX,
            pointY = state.pointY,
        )
        val steered = if ((mode == ControlMode.DIRECT || state.directStick) && heading != null) {
            direct(state, base, heading, sensitivity)
        } else {
            relative(state, base, sensitivity)
        }
        return aimWithStick(state, steered, heading, sensitivity)
    }

    /**
     * The second stick turns the turret the short way towards where it points (with the vehicle's
     * heading), or left and right without it.
     */
    private fun aimWithStick(s: InputState, frame: InputFrame, heading: Heading?, sensitivity: Int): InputFrame {
        if (s.aimX * s.aimX + s.aimY * s.aimY <= sensitivity * sensitivity) return frame
        if (heading == null) return frame.copy(aimLeft = s.aimX < -sensitivity, aimRight = s.aimX > sensitivity)
        val wanted = LegacyDirections.fromAngle(s.aimX, s.aimY)
        if (wanted == heading.turret) return frame.copy(aimLeft = false, aimRight = false)
        val left = ((heading.turret - wanted) and 15) < 8
        return frame.copy(aimLeft = left, aimRight = !left)
    }

    /** Legacy copy_controls_relative(): keys as they are; the stick past the dead zone like the keys. */
    private fun relative(s: InputState, base: InputFrame, sensitivity: Int): InputFrame {
        var frame = base.copy(forward = s.up, reverse = s.down, turnLeft = s.left, turnRight = s.right)
        if (!s.hasStick) return frame
        if (s.stickY < -sensitivity) frame = frame.copy(forward = true)
        if (s.stickY > sensitivity) frame = frame.copy(reverse = true)
        val stickLeft = s.stickX < -sensitivity
        val stickRight = s.stickX > sensitivity
        return when {
            s.weaponHeld -> frame.copy(cycleWeaponLeft = frame.cycleWeaponLeft || stickLeft, cycleWeaponRight = frame.cycleWeaponRight || stickRight)
            s.aimHeld -> frame.copy(aimLeft = frame.aimLeft || stickLeft, aimRight = frame.aimRight || stickRight)
            else -> frame.copy(turnLeft = frame.turnLeft || stickLeft, turnRight = frame.turnRight || stickRight)
        }
    }

    /**
     * Legacy copy_controls_direct(): the keys or the stick point the way to go; the vehicle turns the
     * short way towards it and drives forward (never in reverse), and stops inside the dead zone.
     * With an aim key or the stick's aim button they point the turret instead.
     */
    private fun direct(s: InputState, base: InputFrame, heading: Heading, sensitivity: Int): InputFrame {
        val stick = s.hasStick && !s.up && !s.down && !s.left && !s.right
        val x = if (stick) s.stickX else if (s.right) 100 else if (s.left) -100 else 0
        val y = if (stick) s.stickY else if (s.up) -100 else if (s.down) 100 else 0
        val frame = base.copy(aimLeft = false, aimRight = false)
        if (stick && s.weaponHeld) {
            // Legacy: the weapon button turns the stick into weapon left/right, and nothing else.
            return frame.copy(cycleWeaponLeft = frame.cycleWeaponLeft || x < -sensitivity, cycleWeaponRight = frame.cycleWeaponRight || x > sensitivity)
        }
        val outside = x * x + y * y > sensitivity * sensitivity
        if (!outside) return frame
        val wanted = LegacyDirections.fromAngle(x, y)
        // Legacy go_left_or_right(): left when the other way round is shorter.
        fun turnLeft(actual: Int) = ((actual - wanted) and 15) < 8
        return if (s.aimLeft || s.aimRight || s.aimHeld) {
            val turn = wanted != heading.turret
            frame.copy(aimLeft = turn && turnLeft(heading.turret), aimRight = turn && !turnLeft(heading.turret))
        } else {
            val turn = wanted != heading.body
            frame.copy(forward = true, turnLeft = turn && turnLeft(heading.body), turnRight = turn && !turnLeft(heading.body))
        }
    }
}

/**
 * One player's devices for a session (ADR 0026): a key set (index into the controls settings) and a
 * pad; Extended's player one has the [mouse] and its own keys instead of a key set.
 */
data class Seat(val keys: Int?, val pad: Int?, val mouse: Boolean = false)
