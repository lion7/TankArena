package nl.tankarena.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import nl.tankarena.input.InputState
import nl.tankarena.protocol.snapshot.HudState
import nl.tankarena.render.LegacySpriteCatalog
import nl.tankarena.ui.compose.extended.RetroColors
import nl.tankarena.ui.compose.extended.RetroFont
import nl.tankarena.ui.compose.extended.retroTextImage
import nl.tankarena.ui.compose.menu.TouchSettings
import org.jetbrains.compose.resources.imageResource
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Player one's controls on a touch screen, written by [TouchControls] and read with the other
 * devices for every game tick: a move stick (steering directly, as a stick does), an aim stick for
 * the turret, fire, shield, the weapon before and after, and (Extended) the next vehicle.
 */
class TouchPad {
    @Volatile private var current: InputState? = null
    private val next = AtomicBoolean(false)

    /** What the fingers hold now, or null when none is down. */
    fun state(): InputState? = current

    internal fun set(state: InputState?) {
        current = state
    }

    /** Whether NEXT was tapped since the last call: one tap asks for one vehicle, however long it is held. */
    fun takeNext(): Boolean = next.getAndSet(false)

    internal fun pressNext() {
        next.set(true)
    }
}

internal enum class TouchRole { MOVE, AIM, POINTER, FIRE, SHIELD, WEAPON_LEFT, WEAPON_RIGHT, NEXT, PAUSE }

/** A round button: its centre and radius in pixels. */
internal data class Spot(val role: TouchRole, val centre: Offset, val radius: Float)

/** A stick's radius, a button's size and the room kept from the edges, at 100% (dp). */
private const val STICK_DP = 60f
private const val PAUSE_DP = 26f
private const val MARGIN_DP = 12f

/** A stick never takes more than this part of the screen's height. */
private const val STICK_MAX_HEIGHT = 0.17f

/** Inside this part of its radius a stick does nothing: a thumb's wobble does not drive. */
internal const val DEAD_ZONE = 0.15f

/** The aim stick pushed this far fires too (when [TouchSettings.aimFires]). */
internal const val AIM_FIRES_AT = 0.7f

/** The buttons around the aim stick, in its radius: where (from its centre) and how big. */
private val CLUSTER = listOf(
    Triple(TouchRole.FIRE, Offset(0f, -1.95f), 0.62f),
    Triple(TouchRole.SHIELD, Offset(-1.25f, -1.45f), 0.48f),
    Triple(TouchRole.WEAPON_LEFT, Offset(-0.75f, -2.9f), 0.42f),
    Triple(TouchRole.WEAPON_RIGHT, Offset(0.6f, -2.95f), 0.42f),
    Triple(TouchRole.NEXT, Offset(-1.65f, -2.55f), 0.4f),
)

/** How far the cluster reaches left and right of the aim stick's centre, in its radius. */
private const val CLUSTER_LEFT = 2.05f
private const val CLUSTER_RIGHT = 1.05f

/** Where the touch controls go on a screen of [width] × [height] pixels. */
internal data class TouchLayout(
    /** A stick's radius in pixels. */
    val stick: Float,
    val moveHome: Offset,
    val aimHome: Offset,
    val spots: List<Spot>,
    /** The move stick's half of the screen: the left one, unless left-handed. */
    val moveOnLeft: Boolean,
    val width: Float,
) {
    fun onMoveSide(at: Offset): Boolean = if (moveOnLeft) at.x < width / 2 else at.x >= width / 2

    /** The aim stick takes a thumb that lands near its home; it floats from there. */
    fun nearAim(at: Offset): Boolean = (at - aimHome).getDistance() <= stick * 1.6f

    /** The button under [at]: the drawn circle and a little more. */
    fun spotAt(at: Offset): Spot? = spots.firstOrNull { (it.centre - at).getDistance() <= it.radius * 1.15f }
}

/**
 * The touch controls on a [width] × [height] screen (pixels, [density] per dp): sizes in dp at
 * [TouchSettings.size], capped by the height. With [picture] (the map window) leaving room beside
 * it, the move stick sits in one side's strip and the aim stick with its buttons in the other;
 * otherwise in the corners over the map. Left-handed mirrors it all. NEXT only with [nextVehicle].
 */
internal fun touchLayout(width: Float, height: Float, density: Float, picture: Rect?, settings: TouchSettings, nextVehicle: Boolean): TouchLayout {
    val scale = settings.size / 100f
    val stick = min(STICK_DP * density * scale, height * STICK_MAX_HEIGHT)
    val margin = MARGIN_DP * density
    val leftStrip = picture?.left?.coerceAtLeast(0f) ?: 0f
    val rightStrip = picture?.let { width - it.right }?.coerceAtLeast(0f) ?: 0f
    // Laid out right-handed (move left, aim right), then mirrored for the left hand.
    val moveStrip = if (settings.leftHanded) rightStrip else leftStrip
    val aimStrip = if (settings.leftHanded) leftStrip else rightStrip
    val y = height - margin - stick
    val moveX = if (moveStrip >= 2 * stick + 2 * margin) moveStrip / 2 else margin + stick
    val clusterWidth = (CLUSTER_LEFT + CLUSTER_RIGHT) * stick
    val aimX = if (aimStrip >= clusterWidth + 2 * margin) {
        width - aimStrip / 2 + (CLUSTER_LEFT - CLUSTER_RIGHT) * stick / 2
    } else {
        width - margin - CLUSTER_RIGHT * stick
    }
    val aimHome = Offset(aimX, y)
    val pause = max(PAUSE_DP * density * scale, 24f * density)
    val pauseX = if (aimStrip >= 2 * pause + 2 * margin) width - aimStrip / 2 else width - margin - pause
    val spots = CLUSTER.filter { (role) -> role != TouchRole.NEXT || nextVehicle }
        .map { (role, at, radius) -> Spot(role, aimHome + at * stick, radius * stick) } +
        Spot(TouchRole.PAUSE, Offset(pauseX, margin + pause), pause)
    fun Offset.mirrored() = if (settings.leftHanded) Offset(width - x, this.y) else this
    return TouchLayout(
        stick = stick,
        moveHome = Offset(moveX, y).mirrored(),
        aimHome = aimHome.mirrored(),
        spots = spots.map { it.copy(centre = it.centre.mirrored()) },
        moveOnLeft = !settings.leftHanded,
        width = width,
    )
}

/** A stick pushed in direction ([x], [y]) (-100..100, full strength) and [reach] of its radius (0..1). */
internal data class StickPush(val x: Int, val y: Int, val reach: Float)

/**
 * A stick of [radius] pushed from [from] to [to]: nothing inside the [DEAD_ZONE], else its direction
 * at full strength (the game's vehicles drive or do not; there is no half speed) and how far.
 */
internal fun stickPush(from: Offset, to: Offset, radius: Float): StickPush? {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val distance = hypot(dx, dy)
    if (distance < radius * DEAD_ZONE) return null
    return StickPush((dx / distance * 100).roundToInt(), (dy / distance * 100).roundToInt(), min(1f, distance / radius))
}

/** The weapon one [step] (-1 or 1) from the current one, as the game switches (Tank.stepWeaponCycle); null for none. */
internal fun neighbourWeapon(hud: HudState, night: Boolean, step: Int): Int? {
    val slots = if (night) WEAPON_SLOTS + 1 else WEAPON_SLOTS
    fun available(weapon: Int) =
        hud.ammo.getOrElse(weapon) { 0 } != 0 || (weapon == FLAME_WEAPON && hud.hasFlamer) || (weapon == LIGHT_WEAPON && night)
    var weapon = hud.currentWeapon
    repeat(slots) {
        weapon = (weapon + step).mod(slots)
        if (available(weapon)) return weapon.takeIf { it != hud.currentWeapon }
    }
    return null
}

/** Legacy `num_weapon`, the flamethrower's number and the light switch (ICON15) on night maps. */
private const val WEAPON_SLOTS = 12
private const val FLAME_WEAPON = 2
private const val LIGHT_WEAPON = 12

/**
 * The virtual controller over the game (ADR 0025, ADR 0029), in the original's colours and font:
 * - the move stick wherever the thumb lands on its half (left, unless left-handed);
 * - on the other half the aim stick, floating from where the thumb lands near its home (pushed far it
 *   fires, with [TouchSettings.aimFires]), FIRE, SHIELD, the weapon before and after (showing them),
 *   and NEXT when there is another vehicle;
 * - a finger elsewhere on the map aims and fires there, as the mouse does ([mouse]);
 * - PAUSE at the top.
 * Every finger keeps the role of what it first touched. Idle the controls show at
 * [TouchSettings.visibility]; a held one lights up.
 */
@Composable
internal fun TouchControls(
    pad: TouchPad,
    onPause: () -> Unit,
    modifier: Modifier = Modifier,
    settings: TouchSettings = TouchSettings(),
    /** Player one's status: the weapons on the weapon buttons, and whether NEXT shows. */
    hud: HudState? = null,
    night: Boolean = false,
    /** Extended's NEXT. */
    nextVehicle: Boolean = false,
    /** Player one's pointer: a finger on the map aims and fires like the mouse. */
    mouse: MousePointer? = null,
) {
    val density = LocalDensity.current.density
    val sprites = LegacySpriteCatalog.shared
    @Composable
    fun picture(name: String): ImageBitmap? = sprites.findResource(name)?.let { imageResource(it) }
    val icons = (1..15).map { picture("ICON$it") }
    // The pictures hold the icon in a corner of a larger square: only the drawn part counts.
    val weaponIcons = remember(icons) { icons.map { it?.let { image -> image to image.contentBounds() } } }
    val labelColor = RetroColors.high
    val labels = remember {
        mapOf(
            TouchRole.FIRE to retroTextImage("FIRE", RetroFont.SMALL, labelColor),
            TouchRole.SHIELD to retroTextImage("SHIELD", RetroFont.SMALL, labelColor),
            TouchRole.NEXT to retroTextImage("NEXT", RetroFont.SMALL, labelColor),
            TouchRole.PAUSE to retroTextImage("II", RetroFont.BIG, labelColor),
            TouchRole.WEAPON_LEFT to retroTextImage("<", RetroFont.SMALL, labelColor),
            TouchRole.WEAPON_RIGHT to retroTextImage(">", RetroFont.SMALL, labelColor),
        )
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        var origin by remember { mutableStateOf(Offset.Zero) }
        val picture = mouse?.view?.translate(-origin)
        val showNext = nextVehicle && hud?.otherVehicle == true
        val layout = remember(w, h, density, picture, settings, showNext) { touchLayout(w, h, density, picture, settings, showNext) }
        // Per finger: its role and, for a stick, where the stick's centre is.
        val fingers = remember { HashMap<PointerId, Pair<TouchRole, Offset>>() }
        var moveKnob by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
        var aimKnob by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
        var held by remember { mutableStateOf(emptySet<TouchRole>()) }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.positionInRoot() }
                .pointerInput(layout, settings.aimFires, mouse) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            var move: StickPush? = null
                            var aim: StickPush? = null
                            var moveAt: Pair<Offset, Offset>? = null
                            var aimAt: Pair<Offset, Offset>? = null
                            var pointer: Offset? = null
                            for (change in event.changes) {
                                // A mouse is player one's own (read by the game screen); only fingers here.
                                if (change.type == PointerType.Mouse) continue
                                val id = change.id
                                if (change.pressed && id !in fingers) {
                                    val at = change.position
                                    val spot = layout.spotAt(at)
                                    val role = when {
                                        spot != null -> spot.role
                                        layout.onMoveSide(at) -> TouchRole.MOVE
                                        layout.nearAim(at) -> TouchRole.AIM
                                        picture != null && picture.contains(at) && fingers.values.none { it.first == TouchRole.POINTER } -> TouchRole.POINTER
                                        else -> null
                                    }
                                    if (role != null) {
                                        // A stick floats: its centre is where the thumb landed.
                                        fingers[id] = role to at
                                        when (role) {
                                            TouchRole.PAUSE -> onPause()
                                            TouchRole.NEXT -> pad.pressNext()
                                            else -> Unit
                                        }
                                    }
                                }
                                if (!change.pressed) {
                                    fingers.remove(id)
                                    continue
                                }
                                val (role, from) = fingers[id] ?: continue
                                change.consume()
                                when (role) {
                                    TouchRole.MOVE -> {
                                        move = stickPush(from, change.position, layout.stick)
                                        moveAt = from to change.position
                                    }
                                    TouchRole.AIM -> {
                                        aim = stickPush(from, change.position, layout.stick)
                                        aimAt = from to change.position
                                    }
                                    TouchRole.POINTER -> pointer = change.position
                                    else -> Unit
                                }
                            }
                            val roles = fingers.values.map { it.first }.toSet()
                            held = roles
                            moveKnob = moveAt
                            aimKnob = aimAt
                            if (mouse != null) {
                                // The finger on the map is the pointer, held down: the turret turns there and fires.
                                mouse.cursor = pointer?.let { origin + it }
                                mouse.primary = pointer != null
                            }
                            val aimFiring = settings.aimFires && (aim?.reach ?: 0f) >= AIM_FIRES_AT
                            pad.set(
                                if (roles.isEmpty()) {
                                    null
                                } else {
                                    InputState(
                                        stickX = move?.x ?: 0,
                                        stickY = move?.y ?: 0,
                                        directStick = true,
                                        aimX = aim?.x ?: 0,
                                        aimY = aim?.y ?: 0,
                                        fire = TouchRole.FIRE in roles || aimFiring,
                                        shield = TouchRole.SHIELD in roles,
                                        weaponLeft = TouchRole.WEAPON_LEFT in roles,
                                        weaponRight = TouchRole.WEAPON_RIGHT in roles,
                                    )
                                },
                            )
                        }
                    }
                },
        ) {
            val idle = settings.visibility / 100f
            val lit = max(idle, 0.9f)
            val ring = RetroColors.accent
            val stroke = max(2f, layout.stick * 0.05f)
            // The labels' legacy pixel: SHIELD fits its button.
            val pixel = max(1, (layout.stick * 0.48f * 1.6f / (6 * 6)).toInt())
            fun circle(centre: Offset, radius: Float, active: Boolean) {
                val alpha = if (active) lit else idle
                drawCircle(Color.Black.copy(alpha = alpha * 0.5f), radius, centre)
                if (active) drawCircle(ring.copy(alpha = alpha * 0.35f), radius, centre)
                drawCircle(ring.copy(alpha = alpha), radius, centre, style = Stroke(width = stroke))
            }
            fun image(image: ImageBitmap?, centre: Offset, scale: Int, alpha: Float, part: IntRect? = null) {
                image ?: return
                val source = part ?: IntRect(0, 0, image.width, image.height)
                val size = IntSize(source.width * scale, source.height * scale)
                drawImage(
                    image = image,
                    srcOffset = source.topLeft,
                    srcSize = source.size,
                    dstOffset = IntOffset((centre.x - size.width / 2f).roundToInt(), (centre.y - size.height / 2f).roundToInt()),
                    dstSize = size,
                    alpha = alpha,
                    filterQuality = FilterQuality.None,
                )
            }
            // The sticks sit where the thumbs went down, or at home.
            stick(layout.moveHome, moveKnob, layout.stick, ::circle)
            stick(layout.aimHome, aimKnob, layout.stick, ::circle)
            for (spot in layout.spots) {
                val active = spot.role in held
                val alpha = if (active) lit else max(idle, 0.5f)
                circle(spot.centre, spot.radius, active)
                when (spot.role) {
                    TouchRole.WEAPON_LEFT, TouchRole.WEAPON_RIGHT -> {
                        // The weapon this button switches to, over its arrow.
                        val weapon = hud?.let { neighbourWeapon(it, night, if (spot.role == TouchRole.WEAPON_LEFT) -1 else 1) }
                        val icon = weapon?.let { weaponIcons.getOrNull(if (it == LIGHT_WEAPON) 14 else it) }
                        val arrow = labels[spot.role]
                        if (icon != null) {
                            image(icon.first, spot.centre - Offset(0f, spot.radius * 0.15f), pixel * 2, alpha, icon.second)
                            image(arrow, spot.centre + Offset(0f, spot.radius * 0.55f), pixel, alpha)
                        } else {
                            image(arrow, spot.centre, pixel * 2, alpha)
                        }
                    }
                    TouchRole.PAUSE -> image(labels[spot.role], spot.centre, max(1, pixel / 2 + 1), alpha)
                    TouchRole.FIRE -> image(labels[spot.role], spot.centre, pixel + 1, alpha)
                    else -> image(labels[spot.role], spot.centre, pixel, alpha)
                }
            }
        }
    }
}

/** The part of the picture that is not transparent. */
private fun ImageBitmap.contentBounds(): IntRect {
    val pixels = IntArray(width * height)
    readPixels(pixels)
    var left = width
    var top = height
    var right = 0
    var bottom = 0
    for (y in 0 until height) for (x in 0 until width) {
        if (pixels[y * width + x] ushr 24 == 0) continue
        left = min(left, x)
        top = min(top, y)
        right = max(right, x + 1)
        bottom = max(bottom, y + 1)
    }
    return if (right > left) IntRect(left, top, right, bottom) else IntRect(0, 0, width, height)
}

/** A stick: its ring where the thumb went down (or at [home]) and the knob under the thumb, kept inside the ring. */
private fun DrawScope.stick(home: Offset, knob: Pair<Offset, Offset>?, radius: Float, circle: (Offset, Float, Boolean) -> Unit) {
    val (centre, thumb) = knob ?: (home to home)
    circle(centre, radius, knob != null)
    val offset = thumb - centre
    val clamped = if (offset.getDistance() > radius) offset * (radius / offset.getDistance()) else offset
    circle(centre + clamped, radius * 0.4f, knob != null)
}
