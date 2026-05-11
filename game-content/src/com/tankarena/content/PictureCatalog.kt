package com.tankarena.content

import com.tankarena.protocol.snapshot.TerrainMaterial
import com.tankarena.protocol.snapshot.TerrainTile

/**
 * Maps (world, picture-index) → terrain properties (material + speed multiplier).
 *
 * Extracted from src/data/pictures.c (pc0–pc3 arrays) and src/include/define.h.
 *
 * The legacy code selects one of four background-specific picture arrays (pc0–pc3)
 * at map load, then indexes into it with the value stored in the tile layer.
 *
 * Defaults: speed = 1.0, material = NORMAL (i.e., no entry in the map).
 */
object PictureCatalog {

    /**
     * Resolve terrain info for a picture index in the given world.
     * Returns default NORMAL / 1.0 for unknown indices.
     */
    fun resolve(world: TankArenaWorld, pictureIndex: Int): TerrainTile {
        if (pictureIndex < 0) return TerrainTile()
        val table = TABLES[world] ?: return TerrainTile()
        val entry = table[pictureIndex] ?: return TerrainTile()
        return TerrainTile(material = entry.material, speedMultiplier = entry.speed)
    }

    private data class Entry(val material: TerrainMaterial, val speed: Float)

    private val TABLES: Map<TankArenaWorld, Map<Int, Entry>> = buildTables()

    private fun buildTables(): Map<TankArenaWorld, Map<Int, Entry>> {
        // Build from compact ranges: (start..end) to Entry
        // Only entries differing from default (NORMAL, 1.0) are listed.

        val pc0 = mutableMapOf<Int, Entry>()
        val pc1 = mutableMapOf<Int, Entry>()
        val pc2 = mutableMapOf<Int, Entry>()
        val pc3 = mutableMapOf<Int, Entry>()

        // ── PC0 (DESERT) ──
        // Water (speed 0.2) — indices 260-279
        for (i in 260..279) pc0[i] = Entry(TerrainMaterial.WATER, 0.2f)
        // Shelter (speed 0.8) — indices 20-27
        for (i in 20..27) pc0[i] = Entry(TerrainMaterial.SHELTER, 0.8f)
        // Helisite (speed 0.9)
        pc0[259] = Entry(TerrainMaterial.HELISITE, 0.9f)
        // Runway (speed 1.3) — AIR* indices 248-258
        for (i in 248..258) pc0[i] = Entry(TerrainMaterial.RUNWAY, 1.3f)
        // Runway (speed 1.3) — RUNW* indices 462-470
        for (i in 462..470) pc0[i] = Entry(TerrainMaterial.RUNWAY, 1.3f)
        // Road WEG (speed 1.3) — indices 231-247
        for (i in 231..247) pc0[i] = Entry(TerrainMaterial.ROAD, 1.3f)
        // Sand road ZAN (speed 1.1) — indices 332-346
        for (i in 332..346) pc0[i] = Entry(TerrainMaterial.ROAD, 1.1f)
        // Bridge (speed 1.0)
        pc0[281] = Entry(TerrainMaterial.BRIDGE, 1.0f)
        pc0[282] = Entry(TerrainMaterial.BRIDGE, 1.0f)
        pc0[287] = Entry(TerrainMaterial.BRIDGE, 1.0f)
        pc0[288] = Entry(TerrainMaterial.BRIDGE, 1.0f)
        pc0[293] = Entry(TerrainMaterial.BRIDGE, 1.0f)
        pc0[294] = Entry(TerrainMaterial.BRIDGE, 1.0f)
        pc0[295] = Entry(TerrainMaterial.BRIDGE, 1.0f)
        pc0[296] = Entry(TerrainMaterial.BRIDGE, 1.0f)
        pc0[297] = Entry(TerrainMaterial.BRIDGE, 1.0f)
        pc0[298] = Entry(TerrainMaterial.BRIDGE, 1.0f)
        // Rails (speed 1.0) — indices 299-318
        for (i in 299..318) pc0[i] = Entry(TerrainMaterial.RAILS, 1.0f)
        // Bushes (speed 1.0) — indices 211-230
        for (i in 211..230) pc0[i] = Entry(TerrainMaterial.BUSHES, 1.0f)
        // Big pits (speed 1.0) — indices 350-358
        for (i in 350..358) pc0[i] = Entry(TerrainMaterial.PIT_BIG, 1.0f)
        // Small pits (speed 1.0) — indices 328-331
        for (i in 328..331) pc0[i] = Entry(TerrainMaterial.PIT_SMALL, 1.0f)
        // Lava (speed 0.9) — indices 452-461
        for (i in 452..461) pc0[i] = Entry(TerrainMaterial.LAVA, 0.9f)
        // Fuel dump
        pc0[105] = Entry(TerrainMaterial.FUEL_DUMP, 1.0f)
        // ABOX
        pc0[499] = Entry(TerrainMaterial.ABOX, 1.0f)
        // Warp
        pc0[111] = Entry(TerrainMaterial.WARP_IN, 1.0f)
        pc0[112] = Entry(TerrainMaterial.WARP_OUT, 1.0f)
        // Station
        for (i in 16..19) pc0[i] = Entry(TerrainMaterial.STATION, 1.0f)
        // Light source
        pc0[384] = Entry(TerrainMaterial.LIGHT, 1.0f)
        pc0[385] = Entry(TerrainMaterial.LIGHT, 1.0f)
        pc0[386] = Entry(TerrainMaterial.LIGHT, 1.0f)
        pc0[400] = Entry(TerrainMaterial.LIGHT, 1.0f)
        pc0[401] = Entry(TerrainMaterial.LIGHT, 1.0f)
        pc0[402] = Entry(TerrainMaterial.LIGHT, 1.0f)
        pc0[404] = Entry(TerrainMaterial.LIGHT, 1.0f)
        // Crater (speed 0.9)
        pc0[117] = Entry(TerrainMaterial.CRATER, 0.9f)
        pc0[118] = Entry(TerrainMaterial.CRATER, 0.9f)
        // Oil (speed 1.0 in pc0 — SHIT2)
        pc0[398] = Entry(TerrainMaterial.OIL, 1.0f)
        // Small ramp
        pc0[283] = Entry(TerrainMaterial.RAMP_SMALL, 1.0f)
        pc0[285] = Entry(TerrainMaterial.RAMP_SMALL, 1.0f)
        pc0[289] = Entry(TerrainMaterial.RAMP_SMALL, 1.0f)
        pc0[291] = Entry(TerrainMaterial.RAMP_SMALL, 1.0f)
        for (i in 420..423) pc0[i] = Entry(TerrainMaterial.RAMP_SMALL, 1.0f)
        // Big ramp
        for (i in 426..429) pc0[i] = Entry(TerrainMaterial.RAMP_BIG, 1.0f)
        // Barrier
        for (i in 203..210) pc0[i] = Entry(TerrainMaterial.BARRIER, 1.0f)
        // Structures with non-1.0 speed (material stays NORMAL)
        pc0[0] = Entry(TerrainMaterial.NORMAL, 0.8f)
        pc0[2] = Entry(TerrainMaterial.NORMAL, 0.8f)
        pc0[4] = Entry(TerrainMaterial.NORMAL, 0.8f)
        pc0[6] = Entry(TerrainMaterial.NORMAL, 0.8f)
        for (i in 68..74 step 2) pc0[i] = Entry(TerrainMaterial.NORMAL, 0.5f)
        for (i in 102..104) pc0[i] = Entry(TerrainMaterial.NORMAL, 0.5f)
        pc0[116] = Entry(TerrainMaterial.NORMAL, 0.5f)
        for (i in 141..153) pc0[i] = Entry(TerrainMaterial.NORMAL, 0.8f)
        for (i in 154..166) pc0[i] = Entry(TerrainMaterial.NORMAL, 0.9f)
        for (i in 180..194) pc0[i] = Entry(TerrainMaterial.NORMAL, 0.8f)
        pc0[477] = Entry(TerrainMaterial.NORMAL, 0.5f)

        // ── PC1 (TEMPERATE) ──
        // Water (speed 0.2) — indices 204-218
        for (i in 204..218) pc1[i] = Entry(TerrainMaterial.WATER, 0.2f)
        // Shelter (speed 0.8) — indices 20-27
        for (i in 20..27) pc1[i] = Entry(TerrainMaterial.SHELTER, 0.8f)
        // Helisite (speed 0.9)
        pc1[203] = Entry(TerrainMaterial.HELISITE, 0.9f)
        // Runway (speed 1.3) — AIR* indices 192-202
        for (i in 192..202) pc1[i] = Entry(TerrainMaterial.RUNWAY, 1.3f)
        // Runway (speed 1.3) — AIR* indices 400-410
        for (i in 400..410) pc1[i] = Entry(TerrainMaterial.RUNWAY, 1.3f)
        // Runway (speed 1.3) — RUNW* indices 413-427
        for (i in 413..427) pc1[i] = Entry(TerrainMaterial.RUNWAY, 1.3f)
        // Road WEG (speed 1.3) — indices 172-189
        for (i in 172..189) pc1[i] = Entry(TerrainMaterial.ROAD, 1.3f)
        // Bridge (speed 1.0)
        pc1[238] = Entry(TerrainMaterial.BRIDGE, 1.0f)
        pc1[239] = Entry(TerrainMaterial.BRIDGE, 1.0f)
        pc1[244] = Entry(TerrainMaterial.BRIDGE, 1.0f)
        pc1[245] = Entry(TerrainMaterial.BRIDGE, 1.0f)
        for (i in 250..255) pc1[i] = Entry(TerrainMaterial.BRIDGE, 1.0f)
        // Rails (speed 1.0) — indices 256-288
        for (i in 256..288) pc1[i] = Entry(TerrainMaterial.RAILS, 1.0f)
        // Bushes (speed 1.0) — indices 204-223 (overlaps water indices — bushes override for those specific indices)
        // Note: indices 204-218 are water in base layer, bushes are in top layer
        // We handle this by having both — the TerrainGridBuilder merges layers
        for (i in 204..223) pc1[i] = Entry(TerrainMaterial.BUSHES, 1.0f)
        // Big pits (speed 1.0) — indices 293-301
        for (i in 293..301) pc1[i] = Entry(TerrainMaterial.PIT_BIG, 1.0f)
        // Small pits (speed 1.0) — indices 289-292
        for (i in 289..292) pc1[i] = Entry(TerrainMaterial.PIT_SMALL, 1.0f)
        // Lava (speed 0.9) — indices 434-443
        for (i in 434..443) pc1[i] = Entry(TerrainMaterial.LAVA, 0.9f)
        // Fuel dump
        pc1[93] = Entry(TerrainMaterial.FUEL_DUMP, 1.0f)
        // ABOX
        pc1[383] = Entry(TerrainMaterial.ABOX, 1.0f)
        // Warp
        pc1[99] = Entry(TerrainMaterial.WARP_IN, 1.0f)
        pc1[100] = Entry(TerrainMaterial.WARP_OUT, 1.0f)
        // Station
        for (i in 16..19) pc1[i] = Entry(TerrainMaterial.STATION, 1.0f)
        // Light source
        pc1[327] = Entry(TerrainMaterial.LIGHT, 1.0f)
        pc1[328] = Entry(TerrainMaterial.LIGHT, 1.0f)
        pc1[329] = Entry(TerrainMaterial.LIGHT, 1.0f)
        pc1[334] = Entry(TerrainMaterial.LIGHT, 1.0f)
        pc1[335] = Entry(TerrainMaterial.LIGHT, 1.0f)
        pc1[336] = Entry(TerrainMaterial.LIGHT, 1.0f)
        pc1[338] = Entry(TerrainMaterial.LIGHT, 1.0f)
        // Crater (speed 0.9)
        pc1[103] = Entry(TerrainMaterial.CRATER, 0.9f)
        pc1[104] = Entry(TerrainMaterial.CRATER, 0.9f)
        // Oil (speed 1.0 in pc1)
        pc1[381] = Entry(TerrainMaterial.OIL, 1.0f)
        // Small ramp
        pc1[240] = Entry(TerrainMaterial.RAMP_SMALL, 1.0f)
        pc1[242] = Entry(TerrainMaterial.RAMP_SMALL, 1.0f)
        pc1[246] = Entry(TerrainMaterial.RAMP_SMALL, 1.0f)
        pc1[248] = Entry(TerrainMaterial.RAMP_SMALL, 1.0f)
        for (i in 349..352) pc1[i] = Entry(TerrainMaterial.RAMP_SMALL, 1.0f)
        // Big ramp
        for (i in 355..358) pc1[i] = Entry(TerrainMaterial.RAMP_BIG, 1.0f)
        // Barrier
        for (i in 196..203) pc1[i] = Entry(TerrainMaterial.BARRIER, 1.0f)
        // Structures with non-1.0 speed
        pc1[0] = Entry(TerrainMaterial.NORMAL, 0.8f)
        pc1[2] = Entry(TerrainMaterial.NORMAL, 0.8f)
        pc1[4] = Entry(TerrainMaterial.NORMAL, 0.8f)
        pc1[6] = Entry(TerrainMaterial.NORMAL, 0.8f)
        for (i in 68..74 step 2) pc1[i] = Entry(TerrainMaterial.NORMAL, 0.5f)
        for (i in 90..92) pc1[i] = Entry(TerrainMaterial.NORMAL, 0.5f)
        for (i in 113..126) pc1[i] = Entry(TerrainMaterial.NORMAL, 0.8f)
        for (i in 127..140) pc1[i] = Entry(TerrainMaterial.NORMAL, 0.9f)
        for (i in 171..189) pc1[i] = Entry(TerrainMaterial.NORMAL, 0.8f)

        // ── PC2 (CITY) ──
        // Shelter (speed 0.8) — indices 12-19
        for (i in 12..19) pc2[i] = Entry(TerrainMaterial.SHELTER, 0.8f)
        // Helisite (speed 0.9)
        pc2[175] = Entry(TerrainMaterial.HELISITE, 0.9f)
        // Oil (speed 0.8) — OLIE* indices 254-279
        for (i in 254..279) pc2[i] = Entry(TerrainMaterial.OIL, 0.8f)
        // Race track (speed 1.5) — indices 267-280
        for (i in 267..280) pc2[i] = Entry(TerrainMaterial.ROAD, 1.5f)
        // Start/Check (speed 1.5) — indices 280-295
        for (i in 280..295) pc2[i] = Entry(TerrainMaterial.START_CHECK, 1.5f)
        // Field (speed 1.0) — indices 141-146
        for (i in 141..146) pc2[i] = Entry(TerrainMaterial.FIELD, 1.0f)
        // Fuel dump
        pc2[113] = Entry(TerrainMaterial.FUEL_DUMP, 1.0f)
        // ABOX
        pc2[403] = Entry(TerrainMaterial.ABOX, 1.0f)
        // Warp
        pc2[118] = Entry(TerrainMaterial.WARP_IN, 1.0f)
        pc2[119] = Entry(TerrainMaterial.WARP_OUT, 1.0f)
        // Station
        for (i in 98..101) pc2[i] = Entry(TerrainMaterial.STATION, 1.0f)
        // Light source
        pc2[307] = Entry(TerrainMaterial.LIGHT, 1.0f)
        pc2[308] = Entry(TerrainMaterial.LIGHT, 1.0f)
        pc2[309] = Entry(TerrainMaterial.LIGHT, 1.0f)
        pc2[362] = Entry(TerrainMaterial.LIGHT, 1.0f)
        pc2[363] = Entry(TerrainMaterial.LIGHT, 1.0f)
        pc2[364] = Entry(TerrainMaterial.LIGHT, 1.0f)
        pc2[366] = Entry(TerrainMaterial.LIGHT, 1.0f)
        // Crater (speed 0.9)
        pc2[138] = Entry(TerrainMaterial.CRATER, 0.9f)
        pc2[139] = Entry(TerrainMaterial.CRATER, 0.9f)
        // Oil fallback (speed 1.0)
        pc2[399] = Entry(TerrainMaterial.OIL, 1.0f)
        // Big pits (speed 1.0) — indices 413-427
        for (i in 413..427) pc2[i] = Entry(TerrainMaterial.PIT_BIG, 1.0f)
        // Small pits (speed 1.0) — indices 349-352
        for (i in 349..352) pc2[i] = Entry(TerrainMaterial.PIT_SMALL, 1.0f)
        // Barrier
        for (i in 231..238) pc2[i] = Entry(TerrainMaterial.BARRIER, 1.0f)
        // Rails
        pc2[368] = Entry(TerrainMaterial.RAILS, 1.0f)
        pc2[369] = Entry(TerrainMaterial.RAILS, 1.0f)
        // Big ramp (speed 1.5)
        pc2[344] = Entry(TerrainMaterial.RAMP_BIG, 1.5f)
        pc2[345] = Entry(TerrainMaterial.RAMP_BIG, 1.5f)
        // Structures with non-1.0 speed
        pc2[0] = Entry(TerrainMaterial.NORMAL, 0.8f)
        pc2[2] = Entry(TerrainMaterial.NORMAL, 0.8f)
        pc2[4] = Entry(TerrainMaterial.NORMAL, 0.8f)
        pc2[6] = Entry(TerrainMaterial.NORMAL, 0.8f)
        for (i in 86..89) pc2[i] = Entry(TerrainMaterial.NORMAL, 0.5f)
        for (i in 110..112) pc2[i] = Entry(TerrainMaterial.NORMAL, 0.5f)
        for (i in 217..230) pc2[i] = Entry(TerrainMaterial.NORMAL, 0.8f)

        // ── PC3 (NIGHT) ──
        // Shelter (speed 0.8) — indices 12-19
        for (i in 12..19) pc3[i] = Entry(TerrainMaterial.SHELTER, 0.8f)
        // Runway (speed 1.3) — AIR* indices 400-410
        for (i in 400..410) pc3[i] = Entry(TerrainMaterial.RUNWAY, 1.3f)
        // Runway (speed 1.3) — RUNW* indices 425-439
        for (i in 425..439) pc3[i] = Entry(TerrainMaterial.RUNWAY, 1.3f)
        // Road WEG (speed 1.3) — indices 172-189
        for (i in 172..189) pc3[i] = Entry(TerrainMaterial.ROAD, 1.3f)
        // Big ramp (speed 1.5) — indices 388-395
        for (i in 388..395) pc3[i] = Entry(TerrainMaterial.RAMP_BIG, 1.5f)
        // Station
        for (i in 40..43) pc3[i] = Entry(TerrainMaterial.STATION, 1.0f)
        // Light source
        for (i in 392..395) pc3[i] = Entry(TerrainMaterial.LIGHT, 1.0f)
        // ABOX
        pc3[497] = Entry(TerrainMaterial.ABOX, 1.0f)
        // Big ramp (speed 1.0)
        for (i in 442..445) pc3[i] = Entry(TerrainMaterial.RAMP_BIG, 1.0f)
        // Structures with non-1.0 speed
        for (i in 0..3) pc3[i] = Entry(TerrainMaterial.NORMAL, 0.8f)

        return mapOf(
            TankArenaWorld.DESERT to pc0,
            TankArenaWorld.TEMPERATE to pc1,
            TankArenaWorld.CITY to pc2,
            TankArenaWorld.NIGHT to pc3,
        )
    }
}
