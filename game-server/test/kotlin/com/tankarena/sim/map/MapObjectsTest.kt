package com.tankarena.sim.map

import com.tankarena.content.TankArenaWorld
import com.tankarena.sim.Goal
import com.tankarena.sim.Tank
import com.tankarena.sim.Train
import com.tankarena.sim.Turret
import com.tankarena.sim.Warp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapObjectsTest {
    private fun editor() = MapEditor(MapEditor.newDocument("objects", TankArenaWorld.DESERT, 40, 40))
    private fun template(label: String) = MapObjects.TEMPLATES.first { it.label == label }

    /** Adds an object and returns it (read after adding: the list is replaced on every change). */
    private fun MapEditor.added(template: MapObjects.Template, x: Int, y: Int): Tank.State {
        val index = addObject(template, x, y)!!
        return objects[index] as Tank.State
    }

    @Test
    fun `every shipped map keeps its objects exactly when opened and saved`() {
        for (name in ShippedMaps.baseNames()) {
            val document = ShippedMaps.load(name)!!.document
            assertEquals(document.objects, MapEditor(document).toDocument().objects, name)
        }
    }

    @Test
    fun `trains are placed as the original editor placed the shipped ones`() {
        var checked = 0
        var same = 0
        for (name in ShippedMaps.baseNames()) {
            val editor = MapEditor(ShippedMaps.load(name)!!.document)
            for (car in editor.objects.filterIsInstance<Train.State>()) {
                if (car.direction !in listOf(0, 4, 8, 12)) continue
                val (tx, ty) = MapObjects.trainTile(car)
                val placed = MapObjects.train(tx, ty, car.picture, car.direction, null, emptyList())
                checked++
                if (listOf(placed.x, placed.y, placed.railsX, placed.railsY, placed.railsDir, placed.railsType, placed.endX, placed.endY) ==
                    listOf(car.x, car.y, car.railsX, car.railsY, car.railsDir, car.railsType, car.endX, car.endY) &&
                    placed.railsStart == car.railsStart
                ) same++
            }
        }
        assertTrue(checked > 20, "$checked train cars to compare")
        assertEquals(checked, same, "train cars placed differently")
    }

    @Test
    fun `every template builds an object`() {
        val editor = editor()
        for (template in MapObjects.TEMPLATES) assertNotNull(editor.addObject(template, 300, 300), template.label)
    }

    @Test
    fun `new vehicles get the original editor's settings`() {
        val editor = editor()
        val player = editor.added(template("Standard"), 100, 100)
        assertEquals(false, player.computer)
        assertEquals(1, player.controllerId)
        val second = editor.added(template("Devastator"), 200, 100)
        assertEquals(2, second.controllerId, "the next free player key")
        val computer = editor.added(MapObjects.TEMPLATES.first { it.group == "Computer vehicle" }, 300, 100)
        assertEquals(true, computer.computer)
        assertEquals(1, computer.team)
        assertEquals(250, computer.aiActivationRadius)
        assertEquals(100 to 100, MapObjects.centre(player))
    }

    @Test
    fun `objects move, turn, clone and go, and every step can be undone`() {
        val editor = editor()
        val index = editor.addObject(template("Standard"), 100, 100)!!
        editor.moveObject(index, 164, 200)
        val moved = editor.objects[index] as Tank.State
        assertEquals(164 to 200, MapObjects.centre(moved))
        editor.turnObject(index)
        assertEquals(1, (editor.objects[index] as Tank.State).bodyDirection)
        val copy = editor.cloneObject(index, 300, 300)
        assertEquals(2, (editor.objects[copy] as Tank.State).controllerId)
        editor.removeObject(index)
        assertEquals(1, editor.objects.size)
        repeat(4) { editor.undo() }
        assertEquals(100 to 100, MapObjects.centre(editor.objects.single()))
    }

    @Test
    fun `a start pad of each kind is placed once and moves when placed again`() {
        val editor = editor()
        val first = editor.addObject(template("Tank P1"), 100, 100)
        val again = editor.addObject(template("Tank P1"), 300, 300)
        assertEquals(first, again)
        assertEquals(1, editor.objects.size)
        assertEquals(300 to 300, MapObjects.centre(editor.objects.single()))
    }

    @Test
    fun `wagons couple behind the train and a turret rides on the turret wagon`() {
        val editor = editor()
        val engine = editor.addObject(template("Locomotive"), 10 * 33 + 5, 10 * 33 + 5)!!
        editor.turnObject(engine) // facing east
        val wagon = editor.addWagon(engine, 8)!!
        val front = editor.objects[engine] as Train.State
        val car = editor.objects[wagon] as Train.State
        assertEquals(car.ref, front.rearRef)
        assertEquals(front.ref, car.frontRef)
        assertEquals(9 to 10, MapObjects.trainTile(car), "the tile behind the locomotive")
        val turret = editor.objects.filterIsInstance<Turret.State>().single()
        assertEquals(car.ref, turret.ownerRef)
        editor.removeObject(wagon)
        assertEquals(0, (editor.objects.single() as Train.State).rearRef)
    }

    @Test
    fun `warp exits, waypoints and the object a goal waits for are set by clicking`() {
        val editor = editor()
        val warp = editor.addObject(template("Warp"), 100, 100)!!
        editor.setPoint(warp, ObjectPoint.WARP_EXIT, 500, 600)
        assertEquals(484 to 584, (editor.objects[warp] as Warp.State).let { it.targetX to it.targetY })

        val tank = editor.addObject(MapObjects.TEMPLATES.first { it.group == "Computer vehicle" }, 300, 300)!!
        editor.setPoint(tank, ObjectPoint.WAYPOINT, 400, 410, slot = 2)
        val points = MapObjects.waypoints(editor.objects[tank] as Tank.State)
        assertEquals(400 to 410, points[2])
        assertNull(points[0])

        val goal = editor.addObject(template("Goal"), 200, 200)!!
        assertTrue(editor.setPoint(goal, ObjectPoint.GOAL_TARGET, 302, 298))
        val ref = (editor.objects[tank] as Tank.State).ref
        assertTrue(ref != 0)
        assertEquals(3 to ref, (editor.objects[goal] as Goal.State).let { it.who to it.targetRef })
    }

    @Test
    fun `properties change the object within the original's ranges`() {
        val editor = editor()
        val index = editor.addObject(template("Big round gray"), 100, 100)!!
        val power = MapObjects.properties(editor.objects[index]).first { it.label == "Fire power" }
        assertEquals(5, power.value)
        editor.replaceObject(index, power.with(power.stepped(up = true)))
        assertEquals(6, (editor.objects[index] as Turret.State).power)
        val shootAt = MapObjects.properties(editor.objects[index]).first { it.label == "Shoots at" }
        editor.replaceObject(index, shootAt.with(2))
        assertEquals(0, (editor.objects[index] as Turret.State).team, "shooting only evil vehicles: on the good side")
    }
}
