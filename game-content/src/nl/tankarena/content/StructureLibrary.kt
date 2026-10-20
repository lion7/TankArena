package nl.tankarena.content

/**
 * One cell of a [Structure]: where it sits from the structure's top-left cell, and its pictures on the
 * floor, solid and top layers (`-1` leaves that layer of the map as it is).
 */
data class StructureCell(val dx: Int, val dy: Int, val pictures: List<Int>)

/**
 * A ready-made piece of map from the original editor's structure library: one cell (a building on its
 * floor, say) or a few linked cells. [libraryX]/[libraryY] is where it starts in the library's grid.
 */
data class Structure(val libraryX: Int, val libraryY: Int, val cells: List<StructureCell>) {
    val width: Int get() = cells.maxOf { it.dx } + 1
    val height: Int get() = cells.maxOf { it.dy } + 1
}

/**
 * The original editor's structure libraries, `DATA/MAP_0..3.DAT` (one per world, shipped as
 * `structures/map_N.dat`): a 50×50 grid, column by column, of `short pic[3], links` (legacy
 * `structtype`). Linked cells (UP 1, DOWN 2, LEFT 4, RIGHT 8) form one structure; it is placed from its
 * top-left cell along the DOWN and RIGHT links (legacy `recursive_add_struct()`).
 */
object StructureLibrary {
    private const val SIDE = 50
    private const val DOWN = 2
    private const val UP = 1
    private const val LEFT = 4
    private const val RIGHT = 8
    private val cache = mutableMapOf<TankArenaWorld, List<Structure>>()

    /** The world's structures, row by row as the library shows them. */
    fun forWorld(world: TankArenaWorld): List<Structure> = synchronized(cache) {
        cache.getOrPut(world) {
            val bytes = StructureLibrary::class.java.classLoader
                .getResourceAsStream("structures/map_${world.ordinal}.dat")
                ?.use { it.readBytes() }
                ?: return@getOrPut emptyList()
            parse(bytes)
        }
    }

    internal fun parse(bytes: ByteArray): List<Structure> {
        require(bytes.size == SIDE * SIDE * 8) { "a structure library has ${SIDE * SIDE * 8} bytes, not ${bytes.size}" }
        fun short(offset: Int) = ((bytes[offset].toInt() and 0xFF) or (bytes[offset + 1].toInt() shl 8)).toShort().toInt()
        fun pictures(x: Int, y: Int) = List(3) { short((x * SIDE + y) * 8 + it * 2) }
        fun links(x: Int, y: Int) = short((x * SIDE + y) * 8 + 6)
        fun empty(x: Int, y: Int) = pictures(x, y).all { it < 0 }

        val structures = mutableListOf<Structure>()
        for (y in 0 until SIDE) for (x in 0 until SIDE) {
            val links = links(x, y)
            if (empty(x, y) || links and UP != 0 || links and LEFT != 0) continue
            val cells = linkedMapOf<Pair<Int, Int>, StructureCell>()
            fun add(cx: Int, cy: Int) {
                if (cx !in 0 until SIDE || cy !in 0 until SIDE || (cx to cy) in cells) return
                cells[cx to cy] = StructureCell(cx - x, cy - y, pictures(cx, cy))
                val l = links(cx, cy)
                if (l and DOWN != 0) add(cx, cy + 1)
                if (l and RIGHT != 0) add(cx + 1, cy)
            }
            add(x, y)
            structures += Structure(x, y, cells.values.toList())
        }
        return structures
    }
}
