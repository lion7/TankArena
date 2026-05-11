package com.tankarena.sim.kubriko.server

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.MapMetadata
import com.tankarena.content.TankArenaWorld
import com.tankarena.content.TerrainGridBuilder
import com.tankarena.content.TileLayers
import com.tankarena.protocol.snapshot.TerrainGrid
import com.tankarena.protocol.snapshot.TerrainMaterial
import com.tankarena.sim.kubriko.server.legacy.LegacyMapImporter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerrainMaterialTest {

    @Test
    fun `terrain grid defaults to NORMAL when no layers`() {
        val grid = TerrainGrid.empty(10, 10)
        assertEquals(TerrainMaterial.NORMAL, grid.materialAt(0, 0))
        assertEquals(1.0f, grid.speedAt(0, 0))
        assertEquals(TerrainMaterial.NORMAL, grid.materialAt(9, 9))
    }

    @Test
    fun `terrain grid out-of-bounds returns NORMAL`() {
        val grid = TerrainGrid.empty(5, 5)
        assertEquals(TerrainMaterial.NORMAL, grid.materialAt(-1, 0))
        assertEquals(TerrainMaterial.NORMAL, grid.materialAt(5, 0))
        assertEquals(TerrainMaterial.NORMAL, grid.materialAt(0, -1))
        assertEquals(TerrainMaterial.NORMAL, grid.materialAt(0, 5))
    }

    @Test
    fun `terrain grid pixel lookup maps to correct tile`() {
        val grid = TerrainGrid.empty(10, 10)
        // Pixel (33, 66) should map to tile (1, 2) with tile size 33
        assertEquals(TerrainMaterial.NORMAL, grid.atPixel(33, 66, LEGACY_TILE_SIZE).material)
    }

    @Test
    fun `picture catalog resolves water in desert`() {
        // pc0 index 260 = WAT1, speed 0.2, WATER
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 260)
        assertEquals(TerrainMaterial.WATER, tile.material)
        assertEquals(0.2f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves oil in city`() {
        // pc2 index 254 = OLIEH, speed 0.8, OIL
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.CITY, 254)
        assertEquals(TerrainMaterial.OIL, tile.material)
        assertEquals(0.8f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves lava in desert`() {
        // pc0 index 452 = LAVAPUT, speed 0.9, LAVA
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 452)
        assertEquals(TerrainMaterial.LAVA, tile.material)
        assertEquals(0.9f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves runway in desert`() {
        // pc0 index 248 = AIRH1, speed 1.3, RUNWAY
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 248)
        assertEquals(TerrainMaterial.RUNWAY, tile.material)
        assertEquals(1.3f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves road in desert`() {
        // pc0 index 231 = WEGH, speed 1.3, ROAD
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 231)
        assertEquals(TerrainMaterial.ROAD, tile.material)
        assertEquals(1.3f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves sand road in desert`() {
        // pc0 index 332 = ZANH, speed 1.1, ROAD
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 332)
        assertEquals(TerrainMaterial.ROAD, tile.material)
        assertEquals(1.1f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves bridge in desert`() {
        // pc0 index 281 = @RUG1.H, speed 1.0, BRIDGE
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 281)
        assertEquals(TerrainMaterial.BRIDGE, tile.material)
        assertEquals(1.0f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves big pit in desert`() {
        // pc0 index 350 = PIT_A (TL), speed 1.0, PIT_BIG
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 350)
        assertEquals(TerrainMaterial.PIT_BIG, tile.material)
        assertEquals(1.0f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves small pit in desert`() {
        // pc0 index 328 = PITDA (S_PIT_TL), speed 1.0, PIT_SMALL
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 328)
        assertEquals(TerrainMaterial.PIT_SMALL, tile.material)
        assertEquals(1.0f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves bushes in desert`() {
        // pc0 index 211 = @UN1, speed 1.0, BUSHES
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 211)
        assertEquals(TerrainMaterial.BUSHES, tile.material)
        assertEquals(1.0f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves rails in desert`() {
        // pc0 index 299 = SPOORCR, speed 1.0, RAILS
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 299)
        assertEquals(TerrainMaterial.RAILS, tile.material)
        assertEquals(1.0f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog returns default for unknown index`() {
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 9999)
        assertEquals(TerrainMaterial.NORMAL, tile.material)
        assertEquals(1.0f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog returns default for negative index`() {
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, -1)
        assertEquals(TerrainMaterial.NORMAL, tile.material)
        assertEquals(1.0f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves shelter in desert`() {
        // pc0 index 20 = @HELTHA, speed 0.8, SHELTER
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 20)
        assertEquals(TerrainMaterial.SHELTER, tile.material)
        assertEquals(0.8f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves helisite in desert`() {
        // pc0 index 259 = LSITE, speed 0.9, HELISITE
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 259)
        assertEquals(TerrainMaterial.HELISITE, tile.material)
        assertEquals(0.9f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves field in city`() {
        // pc2 index 141 = FORCE1, speed 1.0, FIELD
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.CITY, 141)
        assertEquals(TerrainMaterial.FIELD, tile.material)
        assertEquals(1.0f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves start check in city`() {
        // pc2 index 280 = STARTH2, speed 1.5, START_CHECK
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.CITY, 280)
        assertEquals(TerrainMaterial.START_CHECK, tile.material)
        assertEquals(1.5f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves race track in city`() {
        // pc2 index 267 = RACE2, speed 1.5, ROAD
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.CITY, 267)
        assertEquals(TerrainMaterial.ROAD, tile.material)
        assertEquals(1.5f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves structure speed in desert`() {
        // pc0 index 0 = @ASE1A, speed 0.8, NORMAL (structure)
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 0)
        assertEquals(TerrainMaterial.NORMAL, tile.material)
        assertEquals(0.8f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves crater in desert`() {
        // pc0 index 117 = KRATER, speed 0.9, CRATER
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 117)
        assertEquals(TerrainMaterial.CRATER, tile.material)
        assertEquals(0.9f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves fuel dump in desert`() {
        // pc0 index 105 = FUELD, speed 1.0, FUEL_DUMP
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 105)
        assertEquals(TerrainMaterial.FUEL_DUMP, tile.material)
        assertEquals(1.0f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves warp in desert`() {
        // pc0 index 111 = WARP1, speed 1.0, WARP_IN
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 111)
        assertEquals(TerrainMaterial.WARP_IN, tile.material)
        assertEquals(1.0f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves aabox in desert`() {
        // pc0 index 499 = ABOX, speed 1.0, ABOX
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 499)
        assertEquals(TerrainMaterial.ABOX, tile.material)
        assertEquals(1.0f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves barrier in desert`() {
        // pc0 index 203 = WALLH, speed 1.0, BARRIER
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 203)
        assertEquals(TerrainMaterial.BARRIER, tile.material)
        assertEquals(1.0f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves small ramp in desert`() {
        // pc0 index 283 = @RUG2.H, speed 1.0, RAMP_SMALL
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 283)
        assertEquals(TerrainMaterial.RAMP_SMALL, tile.material)
        assertEquals(1.0f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves big ramp in desert`() {
        // pc0 index 426 = @CHANS1, speed 1.0, RAMP_BIG
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 426)
        assertEquals(TerrainMaterial.RAMP_BIG, tile.material)
        assertEquals(1.0f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves station in desert`() {
        // pc0 index 16 = @TATIONA, speed 1.0, STATION
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 16)
        assertEquals(TerrainMaterial.STATION, tile.material)
        assertEquals(1.0f, tile.speedMultiplier)
    }

    @Test
    fun `picture catalog resolves light in desert`() {
        // pc0 index 384 = LAMP, speed 1.0, LIGHT
        val tile = com.tankarena.content.PictureCatalog.resolve(TankArenaWorld.DESERT, 384)
        assertEquals(TerrainMaterial.LIGHT, tile.material)
        assertEquals(1.0f, tile.speedMultiplier)
    }
}
