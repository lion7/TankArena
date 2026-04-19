package com.tankarena.content

/**
 * Pure data-side resolver that maps simulation state (tile ids, tank
 * variants, turret types, facing vectors, …) to legacy picture names.
 *
 * The mappings mirror the lookup logic in `src/game/init.c` and the data
 * arrays in `src/data/pictures.c`. Pairing them with
 * [LegacyResourceNaming.safeKey] yields the Compose Multiplatform
 * resource key for each sprite.
 */
object LegacySpriteResources {

    /** All legacy picture names that have a PNG shipped under `composeResources/drawable/`. */
    val availableLegacyNames: Set<String> by lazy {
        GeneratedLegacyPictureCatalog.resourceKeys.keys.toSet()
    }

    /** Returns the Compose resource key (`pic_*`) for a legacy picture, or `null` if missing. */
    fun resourceKey(legacyName: String): String? {
        return GeneratedLegacyPictureCatalog.resourceKeys[legacyName]
    }

    /** Resolves the picture-record entry for a tile, falling back to wrapping the index. */
    fun tileRecord(world: TankArenaWorld, tileId: Int): LegacyPictureRecord? {
        if (tileId < 0) return null
        val records = recordsForWorld(world)
        if (records.isEmpty()) return null
        val safeIndex = tileId % records.size
        return records[safeIndex]
    }

    /** Returns the legacy picture name for the given tile + variant. */
    fun nameForTile(world: TankArenaWorld, tileId: Int, variant: LegacyPictureVariant): String? {
        return tileRecord(world, tileId)?.nameForVariant(variant)
    }

    /**
     * Tank body sprite. Mirrors `sprintf("TAN%02ld-%ld", i+1, j+1)` from
     * `init.c`: `tankIndex` is zero-based (0 -> TAN01) and `frameIndex`
     * picks from the 54 body frames (0 -> -1 suffix). Frames 0..15 are
     * the 16 cardinal/diagonal facings used by the legacy renderer.
     */
    fun nameForTankBody(tankIndex: Int, frameIndex: Int): String {
        val variant = (tankIndex.coerceAtLeast(0) % MAX_TANK_VARIANTS) + 1
        val frame = (frameIndex.coerceAtLeast(0) % MAX_TANK_FRAMES) + 1
        return "TAN${variant.toString().padStart(2, '0')}-$frame"
    }

    /**
     * Turret sprite. Mirrors `sprintf("TUR%ld-%ld", i+1, j+1)`: 9 turret
     * variants, each with 5 directional frames covering the legacy 16-step
     * turret rotation (north, NE, east, SE, south).
     */
    fun nameForTurret(turretIndex: Int, frameIndex: Int): String {
        val variant = (turretIndex.coerceAtLeast(0) % MAX_TURRET_VARIANTS) + 1
        val frame = (frameIndex.coerceAtLeast(0) % MAX_TURRET_FRAMES) + 1
        return "TUR$variant-$frame"
    }

    /**
     * Picks the body frame for a 16-direction facing. The legacy engine
     * uses `tnk.motion.dir` directly (0=up, 4=right, 8=down, 12=left).
     * Animation cycles are encoded by frames 16..53 — for now we just
     * hold the directional frame.
     */
    fun tankBodyFrameForFacing(direction16: Int): Int {
        return ((direction16 % 16) + 16) % 16
    }

    /**
     * Maps the legacy 16-direction angle to one of the 5 turret frames
     * (north, NE, east, SE, south). The legacy renderer mirrors the same
     * 5 frames horizontally to cover the western half of the rotation.
     */
    fun turretFrameForDirection(direction16: Int): Int {
        val normalized = ((direction16 % 16) + 16) % 16
        return when (normalized) {
            in 0..1, 15 -> 0       // north
            in 2..5 -> 1           // north-east / east
            in 6..9 -> 2           // south-east / south
            in 10..13 -> 3         // south-west / west
            else -> 4              // north-west
        }
    }

    /**
     * Translates a unit facing vector (each axis in -1..1) to the 16-step
     * legacy direction used by the renderer. North is 0 and rotation is
     * clockwise so east is 4.
     */
    fun direction16FromFacing(facingX: Int, facingY: Int): Int {
        val sx = facingX.coerceIn(-1, 1)
        val sy = facingY.coerceIn(-1, 1)
        return when {
            sx == 0 && sy < 0 -> 0
            sx > 0 && sy < 0 -> 2
            sx > 0 && sy == 0 -> 4
            sx > 0 && sy > 0 -> 6
            sx == 0 && sy > 0 -> 8
            sx < 0 && sy > 0 -> 10
            sx < 0 && sy == 0 -> 12
            sx < 0 && sy < 0 -> 14
            else -> 0
        }
    }

    /** Sequential explosion frames `EXP1`..`EXP5`. */
    fun nameForExplosion(stage: Int): String {
        val clamped = stage.coerceIn(0, EXPLOSION_FRAMES - 1)
        return "EXP${clamped + 1}"
    }

    /**
     * Picks an internal hint sprite for projectiles. The legacy engine
     * draws plain bullet pixels for `HT_MAIN` and `HT_CHAIN` — only the
     * heavier projectile types use sprites. We expose a single placeholder
     * (`PIJL` = the on-screen aim arrow) here as an opt-in helper for
     * renderers that want a textured bullet.
     */
    fun nameForProjectile(): String? {
        return INTERNAL_PROJECTILE_NAME.takeIf { it in availableLegacyNames }
    }

    private fun recordsForWorld(world: TankArenaWorld): List<LegacyPictureRecord> = when (world) {
        TankArenaWorld.DESERT -> GeneratedLegacyPictureCatalog.desertRecords
        TankArenaWorld.TEMPERATE -> GeneratedLegacyPictureCatalog.temperateRecords
        TankArenaWorld.CITY -> GeneratedLegacyPictureCatalog.cityRecords
        TankArenaWorld.NIGHT -> GeneratedLegacyPictureCatalog.nightRecords
    }

    private const val MAX_TANK_VARIANTS = 23
    private const val MAX_TANK_FRAMES = 54
    private const val MAX_TURRET_VARIANTS = 9
    private const val MAX_TURRET_FRAMES = 5
    private const val EXPLOSION_FRAMES = 5
    private const val INTERNAL_PROJECTILE_NAME = "PIJL"
}
