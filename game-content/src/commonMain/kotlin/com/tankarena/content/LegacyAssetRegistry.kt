package com.tankarena.content

data class SpriteFrameRef(
    val sheet: SpriteSheetDefinition,
    val column: Int,
    val row: Int,
)

enum class TileLayerKind {
    BASE,
    SOLID,
    TOP,
}

object LegacyAssetRegistry {
    fun resolveTileFrame(
        layer: TileLayerKind,
        tileId: Int,
        world: TankArenaWorld,
    ): SpriteFrameRef {
        val catalog = LegacyPictureCatalog.forWorld(world)
        val legacyIndex = tileId.coerceAtLeast(0)
        val boundedIndex = when {
            catalog.pictureCount <= 0 -> legacyIndex
            legacyIndex < catalog.pictureCount -> legacyIndex
            else -> legacyIndex % catalog.pictureCount
        }
        val entry = catalog.entryAt(boundedIndex)
        if (entry != null) {
            val familyKey = legacyPictureFamilyKey(entry.primaryName)
            val sheet = sheetForPictureName(
                name = entry.primaryName,
                layer = layer,
            )
            val familyEntries = catalog.entriesForFamily(familyKey)
            val familyIndex = familyEntries.indexOfFirst { it.index == entry.index }.coerceAtLeast(0)
            return sheet.frameAt(normalizeFrameIndex(familyIndex, sheet))
        }

        val fallbackSheet = when (layer) {
            TileLayerKind.BASE -> SpriteSheets.Floors
            TileLayerKind.SOLID, TileLayerKind.TOP -> SpriteSheets.Walls
        }
        return fallbackSheet.frameAt(normalizeFrameIndex(boundedIndex, fallbackSheet))
    }

    fun resolveTankSheet(variant: Int): SpriteSheetDefinition {
        return SpriteSheets.TankVariants[variant.mod(SpriteSheets.TankVariants.size)]
    }

    fun resolveTankFrame(
        variant: Int,
        facingX: Int,
        facingY: Int,
        animationFrame: Int,
    ): SpriteFrameRef {
        val sheet = resolveTankSheet(variant)
        val row = when {
            facingY < 0 -> 0
            facingX > 0 -> 2
            facingY > 0 -> 4
            facingX < 0 -> 6
            else -> 0
        }.coerceIn(0, sheet.rows - 1)
        return SpriteFrameRef(
            sheet = sheet,
            column = animationFrame.mod(sheet.columns),
            row = row,
        )
    }

    fun resolveTurretFrame(turretType: Int, direction: Int): SpriteFrameRef {
        val sheet = SpriteSheets.Towers
        return SpriteFrameRef(
            sheet = sheet,
            column = (direction / 4).coerceIn(0, sheet.columns - 1),
            row = turretType.coerceIn(0, sheet.rows - 1),
        )
    }

    fun resolveProjectileFrame(): SpriteFrameRef {
        return SpriteFrameRef(
            sheet = SpriteSheets.Animations,
            column = 0,
            row = 1,
        )
    }

    private fun SpriteSheetDefinition.frameAt(index: Int): SpriteFrameRef {
        val column = index % columns
        val row = index / columns
        return SpriteFrameRef(this, column, row)
    }

    private fun normalizeFrameIndex(rawIndex: Int, sheet: SpriteSheetDefinition): Int {
        val frameCount = sheet.columns * sheet.rows
        return rawIndex.mod(frameCount)
    }

    private fun sheetForPictureName(
        name: String,
        layer: TileLayerKind,
    ): SpriteSheetDefinition {
        val familyKey = legacyPictureFamilyKey(name)
        return when {
            familyKey.startsWith("WEG") -> SpriteSheets.Floors
            familyKey.startsWith("AIR") -> SpriteSheets.Floors
            familyKey.startsWith("RUNW") -> SpriteSheets.Floors
            familyKey.startsWith("WAT") -> SpriteSheets.Floors
            familyKey.startsWith("KRATER") -> SpriteSheets.Floors
            familyKey.startsWith("KUIL") -> SpriteSheets.Floors

            familyKey.startsWith("WALL") -> SpriteSheets.Walls
            familyKey.startsWith("BRICK") -> SpriteSheets.Walls
            familyKey.startsWith("HEG") -> SpriteSheets.Walls
            familyKey.startsWith("WOOD") -> SpriteSheets.Walls
            familyKey.startsWith("SMET") -> SpriteSheets.Walls
            familyKey.startsWith("RUG") -> SpriteSheets.Walls
            familyKey.startsWith("TUN") -> SpriteSheets.Walls
            familyKey.startsWith("BRUG") -> SpriteSheets.Walls
            familyKey.startsWith("WALHO") -> SpriteSheets.Walls

            familyKey.startsWith("PALM") -> SpriteSheets.Trees
            familyKey.startsWith("BERG") -> SpriteSheets.Trees
            familyKey.startsWith("BOOM") -> SpriteSheets.Trees
            familyKey.startsWith("BUSH") -> SpriteSheets.Trees

            familyKey.startsWith("BASE") -> SpriteSheets.Building
            familyKey.startsWith("STATION") -> SpriteSheets.Building
            familyKey.startsWith("SHELT") -> SpriteSheets.Building
            familyKey.startsWith("LOODS") -> SpriteSheets.Building
            familyKey.startsWith("BARAK") -> SpriteSheets.Building
            familyKey.startsWith("POMP") -> SpriteSheets.Building
            familyKey.startsWith("KERK") -> SpriteSheets.Building
            familyKey.startsWith("HUIS") -> SpriteSheets.Building
            familyKey.startsWith("PIRA") -> SpriteSheets.Building
            familyKey.startsWith("TENT") -> SpriteSheets.Building
            familyKey.startsWith("KOEPEL") -> SpriteSheets.Building
            familyKey.startsWith("FUELD") -> SpriteSheets.Building
            familyKey.startsWith("CAR") -> SpriteSheets.Building
            familyKey.startsWith("WARP") -> SpriteSheets.Building
            familyKey.startsWith("RBLOCK") -> SpriteSheets.Building
            familyKey.startsWith("LAMP") -> SpriteSheets.Building
            familyKey.startsWith("SCHOTEL") -> SpriteSheets.Building
            familyKey.startsWith("SHIP") -> SpriteSheets.Building

            else -> when (layer) {
                TileLayerKind.BASE -> SpriteSheets.Floors
                TileLayerKind.SOLID, TileLayerKind.TOP -> SpriteSheets.Walls
            }
        }
    }
}
