package com.tankarena.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LegacySpriteResourcesTest {

    @Test
    fun resource_keys_match_safe_naming_for_known_tiles() {
        assertEquals("pic_wegh", LegacySpriteResources.resourceKey("WEGH"))
        assertEquals("pic_wallh", LegacySpriteResources.resourceKey("WALLH"))
        assertEquals("pic_brickh", LegacySpriteResources.resourceKey("BRICKH"))
        assertEquals("pic_palm1", LegacySpriteResources.resourceKey("PALM1"))
        assertEquals("pic_base1a", LegacySpriteResources.resourceKey("BASE1A"))
    }

    @Test
    fun unknown_legacy_names_return_null_resource_key() {
        assertNull(LegacySpriteResources.resourceKey("SOMETHING_THAT_DOES_NOT_EXIST"))
    }

    @Test
    fun safe_keys_disambiguate_prefix_variants() {
        assertEquals("pic_base1a", LegacyResourceNaming.safeKey("BASE1A"))
        assertEquals("pic__aase1a", LegacyResourceNaming.safeKey("@ASE1A"))
        assertEquals("pic__uase1a", LegacyResourceNaming.safeKey("_ASE1A"))
        assertEquals("pic__tase1a", LegacyResourceNaming.safeKey("~ASE1A"))
        assertEquals("pic__case1a", LegacyResourceNaming.safeKey("^ASE1A"))
        assertEquals("pic_wegt_hb", LegacyResourceNaming.safeKey("WEGT-B"))
        assertEquals("pic_loods_ddh", LegacyResourceNaming.safeKey("LOODS.DH"))
    }

    @Test
    fun tank_body_naming_matches_init_c_pattern() {
        assertEquals("TAN01-1", LegacySpriteResources.nameForTankBody(tankIndex = 0, frameIndex = 0))
        assertEquals("TAN01-5", LegacySpriteResources.nameForTankBody(tankIndex = 0, frameIndex = 4))
        assertEquals("TAN05-12", LegacySpriteResources.nameForTankBody(tankIndex = 4, frameIndex = 11))
        assertEquals("TAN23-54", LegacySpriteResources.nameForTankBody(tankIndex = 22, frameIndex = 53))
    }

    @Test
    fun turret_naming_matches_init_c_pattern() {
        assertEquals("TUR1-1", LegacySpriteResources.nameForTurret(turretIndex = 0, frameIndex = 0))
        assertEquals("TUR2-3", LegacySpriteResources.nameForTurret(turretIndex = 1, frameIndex = 2))
        assertEquals("TUR9-5", LegacySpriteResources.nameForTurret(turretIndex = 8, frameIndex = 4))
    }

    @Test
    fun direction_mapping_covers_eight_compass_facings() {
        assertEquals(0, LegacySpriteResources.direction16FromFacing(0, -1))
        assertEquals(2, LegacySpriteResources.direction16FromFacing(1, -1))
        assertEquals(4, LegacySpriteResources.direction16FromFacing(1, 0))
        assertEquals(6, LegacySpriteResources.direction16FromFacing(1, 1))
        assertEquals(8, LegacySpriteResources.direction16FromFacing(0, 1))
        assertEquals(10, LegacySpriteResources.direction16FromFacing(-1, 1))
        assertEquals(12, LegacySpriteResources.direction16FromFacing(-1, 0))
        assertEquals(14, LegacySpriteResources.direction16FromFacing(-1, -1))
    }

    @Test
    fun turret_frame_collapses_legacy_16_directions_into_5_sprites() {
        // North-facing collapses frames 15,0,1.
        assertEquals(0, LegacySpriteResources.turretFrameForDirection(0))
        assertEquals(0, LegacySpriteResources.turretFrameForDirection(15))
        // East-facing block (2..5).
        assertEquals(1, LegacySpriteResources.turretFrameForDirection(4))
        // South-facing block (6..9).
        assertEquals(2, LegacySpriteResources.turretFrameForDirection(8))
        // West-facing block (10..13).
        assertEquals(3, LegacySpriteResources.turretFrameForDirection(12))
        // Final NW frame.
        assertEquals(4, LegacySpriteResources.turretFrameForDirection(14))
    }

    @Test
    fun explosion_frames_cycle_exp1_to_exp5() {
        assertEquals("EXP1", LegacySpriteResources.nameForExplosion(0))
        assertEquals("EXP3", LegacySpriteResources.nameForExplosion(2))
        assertEquals("EXP5", LegacySpriteResources.nameForExplosion(4))
        assertEquals("EXP5", LegacySpriteResources.nameForExplosion(99))
    }

    @Test
    fun every_referenced_tile_name_has_a_resource_key_and_drawable() {
        val drawableDir = locateDrawableResources()
        val worldRecords = listOf(
            GeneratedLegacyPictureCatalog.desertRecords,
            GeneratedLegacyPictureCatalog.temperateRecords,
            GeneratedLegacyPictureCatalog.cityRecords,
            GeneratedLegacyPictureCatalog.nightRecords,
        )
        worldRecords.forEach { records ->
            records.forEach { record ->
                record.nameVariants.forEach { name ->
                    val key = LegacySpriteResources.resourceKey(name)
                    assertNotNull(key, "Missing resource key for legacy picture '$name'")
                    val pngFile = java.io.File(drawableDir, "$key.png")
                    assertTrue(
                        pngFile.isFile,
                        "Missing PNG asset for $name at ${pngFile.absolutePath}",
                    )
                }
            }
        }
    }

    @Test
    fun every_combat_actor_sprite_has_a_resource_key() {
        // Tank bodies for every supported variant in their 16 directional frames.
        for (tankIndex in 0 until 23) {
            for (frame in 0 until 16) {
                val name = LegacySpriteResources.nameForTankBody(tankIndex, frame)
                assertNotNull(
                    LegacySpriteResources.resourceKey(name),
                    "No resource key for $name",
                )
            }
        }
        // All 9 turret variants � 5 sprite frames.
        for (turretIndex in 0 until 9) {
            for (frame in 0 until 5) {
                val name = LegacySpriteResources.nameForTurret(turretIndex, frame)
                assertNotNull(
                    LegacySpriteResources.resourceKey(name),
                    "No resource key for $name",
                )
            }
        }
        // Explosion stages.
        for (stage in 0 until 5) {
            val name = LegacySpriteResources.nameForExplosion(stage)
            assertNotNull(
                LegacySpriteResources.resourceKey(name),
                "No resource key for $name",
            )
        }
    }

    private fun locateDrawableResources(): java.io.File {
        val candidates = sequenceOf(
            "../game-client/composeResources/drawable",
            "game-client/composeResources/drawable",
        )
        for (candidate in candidates) {
            val file = java.io.File(candidate).absoluteFile
            if (file.isDirectory) return file
        }
        error(
            "Could not locate composeResources/drawable; tried " +
                candidates.toList().joinToString(),
        )
    }
}
