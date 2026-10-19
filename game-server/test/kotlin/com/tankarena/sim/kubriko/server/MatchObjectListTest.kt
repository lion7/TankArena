package com.tankarena.sim.kubriko.server

import kotlin.test.Test
import kotlin.test.assertEquals

class MatchObjectListTest {

    private class Probe(private val name: String, private val log: MutableList<String>) : SceneObject<Probe>, Steppable, Resolvable {
        override fun update(deltaTimeInMilliseconds: Int) {
            log += "update $name"
        }

        override fun applyPendingResolutions(worldWidthPixels: Int, worldHeightPixels: Int) {
            log += "resolve $name ${worldWidthPixels}x$worldHeightPixels"
        }

        override fun save(): SceneObject.State<Probe> = error("not saved")
    }

    @Test
    fun `a step updates every object in the order they were added, then applies their resolutions`() {
        val log = mutableListOf<String>()
        val list = MatchObjectList(listOf(Probe("a", log), Probe("b", log)))
        list.add(listOf(Probe("c", log)))

        list.step(640, 480)

        assertEquals(
            listOf("update a", "update b", "update c", "resolve a 640x480", "resolve b 640x480", "resolve c 640x480"),
            log,
        )
    }

    @Test
    fun `the list seen while walking it does not change when objects are added or removed`() {
        val log = mutableListOf<String>()
        val a = Probe("a", log)
        val b = Probe("b", log)
        val list = MatchObjectList(listOf(a, b))
        val seen = list.all

        list.remove(listOf(a))
        list.add(listOf(a, b))

        assertEquals(listOf(a, b), seen)
        assertEquals(listOf(b, a), list.all)
    }
}
