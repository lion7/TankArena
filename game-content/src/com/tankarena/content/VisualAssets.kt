package com.tankarena.content

const val LEGACY_TILE_SIZE = 33

data class SpriteSheetDefinition(
    val assetId: String,
    val columns: Int,
    val rows: Int,
    val frameWidth: Int = LEGACY_TILE_SIZE,
    val frameHeight: Int = LEGACY_TILE_SIZE,
)

object SpriteSheets {
    val Floors = SpriteSheetDefinition(
        assetId = "floors",
        columns = 10,
        rows = 100,
    )
    val Walls = SpriteSheetDefinition(
        assetId = "walls",
        columns = 10,
        rows = 100,
    )
    val Towers = SpriteSheetDefinition(
        assetId = "towers",
        columns = 5,
        rows = 9,
    )
    val Animations = SpriteSheetDefinition(
        assetId = "animations",
        columns = 10,
        rows = 20,
    )
    val Building = SpriteSheetDefinition(
        assetId = "building",
        columns = 10,
        rows = 100,
    )
    val Icons = SpriteSheetDefinition(
        assetId = "icons",
        columns = 10,
        rows = 20,
    )
    val Trees = SpriteSheetDefinition(
        assetId = "trees",
        columns = 10,
        rows = 10,
    )
    val TankVariants: List<SpriteSheetDefinition> = (1..12).map { index ->
        SpriteSheetDefinition(
            assetId = "tank$index",
            columns = 8,
            rows = 7,
        )
    }
}
