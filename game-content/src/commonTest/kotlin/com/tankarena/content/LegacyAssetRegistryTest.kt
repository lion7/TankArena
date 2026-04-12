package com.tankarena.content

import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyAssetRegistryTest {
    @Test
    fun `resolves tank and turret frames deterministically`() {
        val tankFrame = LegacyAssetRegistry.resolveTankFrame(
            variant = 3,
            facingX = 1,
            facingY = 0,
            animationFrame = 5,
        )
        val turretFrame = LegacyAssetRegistry.resolveTurretFrame(
            turretType = 2,
            direction = 12,
        )

        assertEquals("tank4", tankFrame.sheet.assetId)
        assertEquals(5, tankFrame.column)
        assertEquals(2, tankFrame.row)
        assertEquals("towers", turretFrame.sheet.assetId)
        assertEquals(3, turretFrame.column)
        assertEquals(2, turretFrame.row)
    }

    @Test
    fun `resolves world aware tile frames`() {
        val desert = LegacyAssetRegistry.resolveTileFrame(
            layer = TileLayerKind.BASE,
            tileId = LegacyPictureCatalog.forWorld(TankArenaWorld.DESERT).findByName("WEGH")!!.index,
            world = TankArenaWorld.DESERT,
        )
        val temperate = LegacyAssetRegistry.resolveTileFrame(
            layer = TileLayerKind.BASE,
            tileId = LegacyPictureCatalog.forWorld(TankArenaWorld.TEMPERATE).findByName("WEGH")!!.index,
            world = TankArenaWorld.TEMPERATE,
        )
        val cityWall = LegacyAssetRegistry.resolveTileFrame(
            layer = TileLayerKind.SOLID,
            tileId = LegacyPictureCatalog.forWorld(TankArenaWorld.CITY).findByName("WALLH")!!.index,
            world = TankArenaWorld.CITY,
        )

        assertEquals("floors", desert.sheet.assetId)
        assertEquals(0, desert.column)
        assertEquals(0, desert.row)
        assertEquals("floors", temperate.sheet.assetId)
        assertEquals(0, temperate.column)
        assertEquals(0, temperate.row)
        assertEquals("walls", cityWall.sheet.assetId)
        assertEquals(0, cityWall.column)
        assertEquals(0, cityWall.row)
    }

    @Test
    fun `selects extracted sheets by legacy picture family`() {
        val desert = LegacyPictureCatalog.forWorld(TankArenaWorld.DESERT)

        val road = LegacyAssetRegistry.resolveTileFrame(
            layer = TileLayerKind.BASE,
            tileId = desert.findByName("WEGH")!!.index,
            world = TankArenaWorld.DESERT,
        )
        val wall = LegacyAssetRegistry.resolveTileFrame(
            layer = TileLayerKind.SOLID,
            tileId = desert.findByName("BRICKH")!!.index,
            world = TankArenaWorld.DESERT,
        )
        val tree = LegacyAssetRegistry.resolveTileFrame(
            layer = TileLayerKind.TOP,
            tileId = desert.findByName("PALM1")!!.index,
            world = TankArenaWorld.DESERT,
        )
        val building = LegacyAssetRegistry.resolveTileFrame(
            layer = TileLayerKind.BASE,
            tileId = desert.findByName("BASE1A")!!.index,
            world = TankArenaWorld.DESERT,
        )

        assertEquals("floors", road.sheet.assetId)
        assertEquals("walls", wall.sheet.assetId)
        assertEquals("trees", tree.sheet.assetId)
        assertEquals("building", building.sheet.assetId)
    }
}
