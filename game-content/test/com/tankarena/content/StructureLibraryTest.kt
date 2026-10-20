package com.tankarena.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StructureLibraryTest {
    @Test
    fun `every world has its library with pictures of its own table`() {
        for (world in TankArenaWorld.entries) {
            val structures = StructureLibrary.forWorld(world)
            assertTrue(structures.size > 100, "${world.name}: ${structures.size} structures")
            val count = PictureCatalog.pictureCount(world)
            for (structure in structures) for (cell in structure.cells) for (picture in cell.pictures) {
                assertTrue(picture in -1 until count, "${world.name} structure at ${structure.libraryX},${structure.libraryY}: picture $picture")
            }
        }
    }

    @Test
    fun `linked cells form one structure from its top-left cell`() {
        val bytes = ByteArray(50 * 50 * 8) { -1 }
        fun put(x: Int, y: Int, floor: Int, links: Int) {
            val at = (x * 50 + y) * 8
            for ((i, v) in listOf(floor, -1, -1, links).withIndex()) {
                bytes[at + i * 2] = v.toByte()
                bytes[at + i * 2 + 1] = (v shr 8).toByte()
            }
        }
        for (x in 0 until 50) for (y in 0 until 50) put(x, y, -1, 0)
        put(3, 4, 7, 8) // RIGHT
        put(4, 4, 8, 4 or 2) // LEFT, DOWN
        put(4, 5, 9, 1) // UP
        put(10, 10, 1, 0)
        val structures = StructureLibrary.parse(bytes)
        assertEquals(2, structures.size)
        val big = structures.first()
        assertEquals(3 to 4, big.libraryX to big.libraryY)
        assertEquals(2 to 2, big.width to big.height)
        assertEquals(listOf(Triple(0, 0, 7), Triple(1, 0, 8), Triple(1, 1, 9)), big.cells.map { Triple(it.dx, it.dy, it.pictures[0]) }.sortedBy { it.first * 10 + it.second })
    }
}
