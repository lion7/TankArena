package nl.tankarena.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.tankarena.sim.BoxBody
import nl.tankarena.sim.Destroyer
import nl.tankarena.sim.Enforcer
import nl.tankarena.sim.Goal
import nl.tankarena.sim.Lock
import nl.tankarena.sim.Tank
import nl.tankarena.sim.Train
import nl.tankarena.sim.Turret
import nl.tankarena.sim.Warp
import nl.tankarena.sim.map.MapEditor
import nl.tankarena.sim.map.MapObject
import nl.tankarena.sim.map.MapObjects
import nl.tankarena.sim.map.ObjectPoint
import kotlin.math.cos
import kotlin.math.sin

/** The objects tool's state: what is selected, what a click adds, and which point a click sets. */
internal class ObjectToolState {
    var selected: Int? = null
    var template: MapObjects.Template? = null
    var point: ObjectPoint? = null
    var waypointSlot: Int = 0

    /** Forgets a selection the list no longer has (after undo or removing). */
    fun check(editor: MapEditor) {
        if ((selected ?: return) >= editor.objects.size) {
            selected = null
            point = null
        }
    }
}

private fun kindColour(state: MapObject): Color = when {
    state is Tank.State && state.startPadType >= 0 -> Color(0xFF40C0FF)
    state is Tank.State && !state.computer -> Color(0xFF40FF80)
    state is Tank.State && state.team == 0 -> Color(0xFF80FFC0)
    state is Tank.State -> Color(0xFFFF6040)
    state is Turret.State -> Color(0xFFFF9040)
    state is Train.State -> Color(0xFFC0A060)
    else -> Color(0xFFFFD040)
}

/** The map's objects as boxes, and for the selected one the points and ranges that go with it. */
internal fun DrawScope.drawObjects(
    editor: MapEditor,
    tool: ObjectToolState?,
    zoom: Float,
    scrollX: Float,
    scrollY: Float,
    measurer: TextMeasurer,
) {
    fun at(x: Int, y: Int) = Offset(x * zoom - scrollX, y * zoom - scrollY)
    val objects = editor.objects
    for ((index, state) in objects.withIndex()) {
        val (cx, cy) = MapObjects.centre(state)
        val box = MapObjects.body(state) as? BoxBody
        val width = (box?.size?.width ?: 8f) * zoom
        val height = (box?.size?.height ?: 8f) * zoom
        val selected = tool?.selected == index
        drawRect(
            color = if (selected) Color.Yellow else kindColour(state),
            topLeft = at(cx, cy) - Offset(width / 2, height / 2),
            size = Size(width, height),
            style = Stroke(width = if (selected) 3f else 1f),
        )
    }
    val index = tool?.selected ?: return
    val state = objects.getOrNull(index) ?: return
    val (cx, cy) = MapObjects.centre(state)
    val centre = at(cx, cy)
    val link = Color(0xFF60E0FF)
    fun circle(radius: Int, colour: Color = link) = drawCircle(colour, radius * zoom, centre, style = Stroke(width = 1f))
    fun label(text: String, where: Offset) = drawText(measurer, text, where, TextStyle(color = Color.White, fontSize = 11.sp, background = Color(0xB0000000)))
    when (state) {
        is Warp.State -> drawLine(link, centre, at(state.targetX, state.targetY), strokeWidth = 2f)
        is Lock.State -> {
            val key = at(state.lockX, state.lockY)
            drawLine(link, centre, key, strokeWidth = 2f)
            drawRect(link, key - Offset(6f, 6f), Size(12f, 12f), style = Stroke(width = 2f))
        }
        is Goal.State -> {
            circle(state.radius)
            if (state.who == 3 && state.targetRef != 0) {
                objects.firstOrNull { MapObjects.ref(it) == state.targetRef }?.let { target ->
                    val (tx, ty) = MapObjects.centre(target)
                    drawLine(link, centre, at(tx, ty), strokeWidth = 2f)
                }
            }
        }
        is Turret.State -> {
            circle(state.radius)
            for (direction in 0 until 16) {
                if (state.forbidDirections and (1 shl direction) == 0) continue
                val angle = Math.toRadians(direction * 22.5)
                val reach = state.radius * zoom
                drawLine(Color.Red, centre, centre + Offset((sin(angle) * reach).toFloat(), (-cos(angle) * reach).toFloat()), strokeWidth = 3f)
            }
        }
        is Destroyer.State -> circle(state.radius)
        is Enforcer.State -> circle(state.radius)
        is Tank.State -> if (state.computer) {
            if (state.aiActivationRadius > 0) circle(state.aiActivationRadius, Color(0x8060E0FF))
            var previous = centre
            for ((slot, point) in MapObjects.waypoints(state).withIndex()) {
                point ?: continue
                val p = at(point.first, point.second)
                drawLine(link, previous, p, strokeWidth = 2f)
                drawCircle(link, 5f, p)
                label("$slot", p + Offset(6f, -6f))
                previous = p
            }
        }
        else -> Unit
    }
}

/**
 * The objects tool's side panel: with an object selected its settings (− / +), the points a click
 * sets and its buttons; otherwise the kinds of object a click adds (legacy objects_mnu, addobj_mnu).
 */
@Composable
internal fun ColumnScope.ObjectPanel(
    editor: MapEditor,
    tool: ObjectToolState,
    revision: Int,
    onChange: () -> Unit,
) {
    revision.let { tool.check(editor) }
    val index = tool.selected
    val state = index?.let { editor.objects.getOrNull(it) }
    if (index == null || state == null) {
        Text(
            "Left click: select an object (drag moves it) or add the kind chosen below; Shift adds always. " +
                "Right click turns the selected object. Delete removes, C clones at the cursor.",
            color = Color.White,
            fontSize = 12.sp,
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            var group = ""
            for (template in MapObjects.TEMPLATES) {
                if (template.group != group) {
                    group = template.group
                    item { Text(group, color = Color(0xFFE0B04A), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                }
                item {
                    val chosen = tool.template === template
                    Text(
                        template.label,
                        color = if (chosen) Color.Yellow else Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (chosen) Color(0xFF3A3220) else Color.Transparent)
                            .clickable { tool.template = template; onChange() }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        return
    }
    Text(MapObjects.describe(state), color = Color(0xFFE0B04A), fontSize = 15.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = { editor.turnObject(index); onChange() }, enabled = MapObjects.turned(state) != null) { Text("Turn") }
        OutlinedButton(onClick = { editor.removeObject(index); tool.selected = null; tool.point = null; onChange() }) { Text("Remove") }
        OutlinedButton(onClick = { tool.selected = null; tool.point = null; onChange() }) { Text("Done") }
    }
    for (point in MapObjects.points(state)) {
        FilterChip(
            selected = tool.point == point,
            onClick = { tool.point = if (tool.point == point) null else point; onChange() },
            label = { Text("Click sets the ${point.label}") },
        )
    }
    if (tool.point == ObjectPoint.WAYPOINT) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (slot in 0 until Tank.WAYPOINTS) {
                Text(
                    "$slot",
                    color = if (tool.waypointSlot == slot) Color.Yellow else Color.White,
                    modifier = Modifier.clickable { tool.waypointSlot = slot; onChange() }.padding(4.dp),
                )
            }
        }
        Text("Left sets the waypoint, right clears it.", color = Color.LightGray, fontSize = 11.sp)
    }
    if (state is Train.State) {
        Text("Couple a wagon behind:", color = Color.White, fontSize = 12.sp)
        Column {
            for ((i, name) in MapObjects.WAGON_NAMES.withIndex()) {
                Text(
                    name,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { editor.addWagon(index, i + 1)?.let { tool.selected = it }; onChange() }.padding(2.dp),
                )
            }
        }
    }
    LazyColumn(modifier = Modifier.weight(1f)) {
        items(MapObjects.properties(state)) { property ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(property.label, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(
                    "−",
                    color = Color.White,
                    modifier = Modifier.clickable { editor.replaceObject(index, property.with(property.stepped(up = false))); onChange() }.padding(horizontal = 6.dp),
                )
                Text(property.text, color = Color.Yellow, fontSize = 12.sp, modifier = Modifier.width(110.dp))
                Text(
                    "+",
                    color = Color.White,
                    modifier = Modifier.clickable { editor.replaceObject(index, property.with(property.stepped(up = true))); onChange() }.padding(horizontal = 6.dp),
                )
            }
        }
    }
}
