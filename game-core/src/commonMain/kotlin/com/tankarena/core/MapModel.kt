package com.tankarena.core

enum class TileLayer {
    Ground,
    Structure,
    Overlay,
}

data class TileCell(
    val groundId: Int = -1,
    val structureId: Int = -1,
    val overlayId: Int = -1,
) {
    val blocksMovement: Boolean get() = structureId >= 0
}

data class MapDefinition(
    val width: Int,
    val height: Int,
    val cells: List<TileCell>,
    val tileSize: Float = 16f,
) {
    init {
        require(width > 0 && height > 0) { "Map dimensions must be positive" }
        require(cells.size == width * height) { "Map cell count must be width * height" }
    }

    fun cellAt(tileX: Int, tileY: Int): TileCell? {
        if (tileX !in 0 until width || tileY !in 0 until height) return null
        return cells[tileX + tileY * width]
    }

    fun blocksMovementAtWorld(worldX: Float, worldY: Float): Boolean {
        val tx = (worldX / tileSize).toInt()
        val ty = (worldY / tileSize).toInt()
        return cellAt(tx, ty)?.blocksMovement == true
    }

    companion object {
        fun empty(width: Int, height: Int, tileSize: Float = 16f): MapDefinition = MapDefinition(
            width = width,
            height = height,
            tileSize = tileSize,
            cells = List(width * height) { TileCell() },
        )
    }
}
