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
)

data class MapDefinition(
    val width: Int,
    val height: Int,
    val cells: List<TileCell>,
) {
    init {
        require(width > 0 && height > 0) { "Map dimensions must be positive" }
        require(cells.size == width * height) { "Map cell count must be width * height" }
    }

    companion object {
        fun empty(width: Int, height: Int): MapDefinition = MapDefinition(
            width = width,
            height = height,
            cells = List(width * height) { TileCell() },
        )
    }
}
