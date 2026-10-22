package nl.tankarena.sim

import nl.tankarena.content.LEGACY_TILE_SIZE
import nl.tankarena.content.PictureCatalog
import nl.tankarena.content.PictureMasks
import nl.tankarena.content.TankArenaWorld
import nl.tankarena.content.TileLayers

/** Legacy `hit[type].kind` (original/src/check/hitwall.c): which of a structure's three strengths a hit wears down. */
enum class HitKind { NORMAL, CRASH, FLAME }

/** Legacy structure layers: the solid layer (`mapm.m[1]`, `strength[0]`) and the top layer (`mapm.m[2]`, `strength[1]`). */
enum class StructureLayer { SOLID, TOP }

/** A structure tile that was just destroyed, with what the original does about it. */
data class DestroyedStructure(
    val tileX: Int,
    val tileY: Int,
    val layer: StructureLayer,
    /** Legacy `exptype` of the picture: the effect played on destruction. */
    val explosion: String,
    /** Legacy picture type of the tile (e.g. `BRIDGE_H`, `FUEL_DUMP`, `ABOX`). */
    val legacyType: String,
    /** Legacy `mapm.g`: the goal-counter percentage for destroying this (solid-layer) tile. */
    val goalContribution: Int,
)

/**
 * The buildings of a map as the original keeps them (`strength[2][3][]` in original/src/game/mapfiles.c):
 * per tile and layer three strengths (against normal hits, crashes and flames; 1000 means
 * indestructible), linked multi-tile structures that are damaged as one, and pixel masks for
 * collisions. A solid-layer tile blocks ground vehicles only while its normal strength is above
 * zero, and then only with the opaque pixels of its picture (`overlap()` in original/src/check/overlap.c).
 */
class StructureGrid(
    val width: Int,
    val height: Int,
    private val world: TankArenaWorld,
    layers: TileLayers?,
) {
    private val size = width * height
    /** Legacy `mapm.m[0..2]`; mutable because a destroyed bridge turns its base into water. */
    val base: IntArray = IntArray(size) { layers?.base?.getOrNull(it) ?: -1 }
    val solid: IntArray = IntArray(size) { layers?.solid?.getOrNull(it) ?: -1 }
    val top: IntArray = IntArray(size) { layers?.top?.getOrNull(it) ?: -1 }
    private val goal: IntArray = IntArray(size) { layers?.goalLayer?.getOrNull(it) ?: 0 }
    private val strength: Array<Array<IntArray>> = Array(2) { Array(3) { IntArray(size) } }

    /** Tiles whose pictures changed since the match started (destroyed structures, bridges turned to water). */
    val changedTiles: Set<Int> get() = changed
    private val changed = linkedSetOf<Int>()

    init {
        // Legacy load_map(): each layer takes its picture's `str[]`, and a tile that is
        // indestructible (1000) in either layer becomes [1000, 0, 0] in both.
        for (index in 0 until size) {
            for ((layer, pictures) in listOf(solid, top).withIndex()) {
                val row = PictureCatalog.legacyRow(world, pictures[index]) ?: continue
                for (kind in 0 until 3) strength[layer][kind][index] = row.strength.getOrElse(kind) { 0 }
            }
            if (strength[0][0][index] == INDESTRUCTIBLE || strength[1][0][index] == INDESTRUCTIBLE) {
                for (layer in 0 until 2) for (kind in 0 until 3) {
                    strength[layer][kind][index] = if (kind == 0) INDESTRUCTIBLE else 0
                }
            }
        }
    }

    private fun index(tileX: Int, tileY: Int): Int = tileX + tileY * width

    private fun inside(tileX: Int, tileY: Int) = tileX in 0 until width && tileY in 0 until height

    fun strength(tileX: Int, tileY: Int, layer: StructureLayer, kind: HitKind = HitKind.NORMAL): Int =
        if (inside(tileX, tileY)) strength[layer.ordinal][kind.ordinal][index(tileX, tileY)] else 0

    /** True while the solid-layer structure on this tile stands (legacy `strength[0][0] > 0`). */
    fun isStanding(tileX: Int, tileY: Int): Boolean = strength(tileX, tileY, StructureLayer.SOLID) > 0

    /** Legacy `mapm.g` of a standing solid structure, the computer tanks' goal buildings; else 0. */
    fun standingGoal(tileX: Int, tileY: Int): Int =
        if (inside(tileX, tileY) && isStanding(tileX, tileY)) goal[index(tileX, tileY)] else 0

    /** True when the top-layer picture of this tile is intact. */
    fun isTopIntact(tileX: Int, tileY: Int): Boolean = strength(tileX, tileY, StructureLayer.TOP) > 0

    /** Navigation/line-of-sight view: a standing solid structure, or -1. */
    fun blockingLayer(): List<Int> = object : AbstractList<Int>() {
        override val size: Int get() = this@StructureGrid.size
        override fun get(index: Int): Int = if (strength[0][0][index] > 0) solid[index] else -1
    }

    private fun mask(pictureIndex: Int, variant: Int): PictureMasks.Mask? {
        val names = PictureCatalog.pictureNames(world, pictureIndex) ?: return null
        return PictureMasks.mask(names.getOrElse(variant) { names.last() })
    }

    /**
     * Legacy overlap() for a ground vehicle whose picture [picture] has its top-left corner at
     * ([left], [top]): the number of pixels where both it and a standing solid structure are opaque.
     * [firstHit] receives the tile of the first overlapping pixel.
     */
    fun overlap(picture: PictureMasks.Mask, left: Int, top: Int, firstHit: IntArray? = null): Int =
        overlapWith(picture, left, top, firstHit) { tileX, tileY ->
            if (isStanding(tileX, tileY)) mask(solid[index(tileX, tileY)], 0) else null
        }

    /**
     * Legacy overlap() for a vehicle flying at height 2..7: only the top-layer pictures of standing
     * buildings stand in its way (their ruined variant once the top layer itself is destroyed).
     */
    fun overlapTop(picture: PictureMasks.Mask, left: Int, top: Int, firstHit: IntArray? = null): Int =
        overlapWith(picture, left, top, firstHit) { tileX, tileY ->
            val i = index(tileX, tileY)
            if (isStanding(tileX, tileY) && this.top[i] >= 0) mask(this.top[i], if (strength[1][0][i] == 0) 1 else 0) else null
        }

    /** Legacy check_wall_hit_point(..., exclude = 1): is a standing solid structure opaque at this world pixel? */
    fun solidOpaqueAt(x: Int, y: Int): Boolean {
        val tileX = Math.floorDiv(x, LEGACY_TILE_SIZE)
        val tileY = Math.floorDiv(y, LEGACY_TILE_SIZE)
        if (!inside(tileX, tileY) || !isStanding(tileX, tileY)) return false
        val structure = mask(solid[index(tileX, tileY)], 0) ?: return false
        return structure.isOpaque(x - tileX * LEGACY_TILE_SIZE, y - tileY * LEGACY_TILE_SIZE)
    }

    private inline fun overlapWith(
        picture: PictureMasks.Mask,
        left: Int,
        top: Int,
        firstHit: IntArray?,
        structureMask: (Int, Int) -> PictureMasks.Mask?,
    ): Int {
        var count = 0
        val tileX0 = Math.floorDiv(left, LEGACY_TILE_SIZE)
        val tileY0 = Math.floorDiv(top, LEGACY_TILE_SIZE)
        for (tileY in tileY0..tileY0 + 1) {
            for (tileX in tileX0..tileX0 + 1) {
                if (!inside(tileX, tileY)) continue
                val structure = structureMask(tileX, tileY) ?: continue
                val ox = tileX * LEGACY_TILE_SIZE - left
                val oy = tileY * LEGACY_TILE_SIZE - top
                val x0 = maxOf(0, ox)
                val y0 = maxOf(0, oy)
                val x1 = minOf(LEGACY_TILE_SIZE, ox + LEGACY_TILE_SIZE)
                val y1 = minOf(LEGACY_TILE_SIZE, oy + LEGACY_TILE_SIZE)
                for (y in y0 until y1) for (x in x0 until x1) {
                    if (picture.isOpaque(x, y) && structure.isOpaque(x - ox, y - oy)) {
                        if (count == 0 && firstHit != null) {
                            firstHit[0] = tileX
                            firstHit[1] = tileY
                        }
                        count++
                    }
                }
            }
        }
        return count
    }

    /**
     * Legacy determine_hit() with nothing excluded: the layer a bullet at world pixel ([x], [y])
     * hits, or null if both structures are transparent there. An intact top-layer picture is hit
     * first; once it is destroyed its ruined picture still belongs to the solid layer below.
     */
    fun hitLayerAt(x: Int, y: Int): StructureLayer? {
        val tileX = Math.floorDiv(x, LEGACY_TILE_SIZE)
        val tileY = Math.floorDiv(y, LEGACY_TILE_SIZE)
        if (!inside(tileX, tileY)) return null
        val i = index(tileX, tileY)
        val px = x - tileX * LEGACY_TILE_SIZE
        val py = y - tileY * LEGACY_TILE_SIZE
        val solidStanding = strength[0][0][i] != 0
        val topPicture = top[i]
        val (topMask, topLayer) = when {
            topPicture >= 0 && strength[1][0][i] != 0 -> mask(topPicture, 0) to StructureLayer.TOP
            topPicture >= 0 && solidStanding -> mask(topPicture, 1) to StructureLayer.SOLID
            else -> null to StructureLayer.SOLID
        }
        val solidMask = if (solidStanding && solid[i] >= 0) mask(solid[i], 0) else null
        if (topMask?.isOpaque(px, py) == true) return topLayer
        if (solidMask?.isOpaque(px, py) == true) return StructureLayer.SOLID
        return null
    }

    /**
     * Legacy check_wall_hit(): the layer an area blast at this tile wears down (the top layer
     * first while it stands), or null if nothing destructible stands there.
     */
    fun blastLayerAt(tileX: Int, tileY: Int): StructureLayer? = when {
        !inside(tileX, tileY) -> null
        strength(tileX, tileY, StructureLayer.TOP) != 0 -> StructureLayer.TOP
        strength(tileX, tileY, StructureLayer.SOLID) != 0 -> StructureLayer.SOLID
        else -> null
    }

    /**
     * Legacy hit_wall_for()/hit_wall(): wear [power] off the [kind] strength of [layer] at this
     * tile and of every structure linked to it. Strength 1000 is indestructible and a structure
     * whose [kind] strength is 0 cannot be hurt that way. Returns the structures destroyed.
     */
    fun hit(
        tileX: Int,
        tileY: Int,
        layer: StructureLayer,
        kind: HitKind,
        power: Int,
        /** Legacy HT_REMOVE (a lock's blow_lock()): takes indestructible structures down too. */
        remove: Boolean = false,
    ): List<DestroyedStructure> {
        if (!inside(tileX, tileY)) return emptyList()
        val newStrength = (strength(tileX, tileY, layer, kind) - power).coerceAtLeast(0)
        val destroyed = mutableListOf<DestroyedStructure>()
        hitLinked(tileX, tileY, layer, kind, newStrength, from = null, destroyed, remove)
        return destroyed
    }

    /**
     * Legacy fly_b52(): a bomb that hits open ground may leave a crater — a NORM_KRAT base on an
     * empty tile, or a ROAD_KRAT on a road. Returns true when the tile changed.
     */
    fun placeCrater(tileX: Int, tileY: Int): Boolean {
        if (!inside(tileX, tileY)) return false
        val i = index(tileX, tileY)
        if (base[i] == -1 && solid[i] == -1) {
            base[i] = firstPicture("NORM_KRAT")
        } else if (solid[i] == -1 && PictureCatalog.legacyRow(world, base[i])?.type == "ROAD") {
            solid[i] = firstPicture("ROAD_KRAT")
        } else {
            return false
        }
        changed += i
        return true
    }

    private fun firstPicture(type: String): Int =
        (0 until 1000).firstOrNull { PictureCatalog.legacyRow(world, it)?.type == type } ?: -1

    /** Legacy prepare_lock() with `lock_target == 0`: the locked structure only falls with its lock. */
    fun makeIndestructible(tileX: Int, tileY: Int) {
        if (!inside(tileX, tileY)) return
        val i = index(tileX, tileY)
        if (solid[i] >= 0) strength[0][0][i] = INDESTRUCTIBLE
        if (top[i] >= 0) strength[1][0][i] = INDESTRUCTIBLE
    }

    private fun hitLinked(
        tileX: Int,
        tileY: Int,
        layer: StructureLayer,
        kind: HitKind,
        newStrength: Int,
        from: String?,
        destroyed: MutableList<DestroyedStructure>,
        remove: Boolean = false,
    ) {
        if (!inside(tileX, tileY)) return
        val i = index(tileX, tileY)
        val picture = if (layer == StructureLayer.SOLID) solid[i] else top[i]
        if (picture < 0) return
        val row = PictureCatalog.legacyRow(world, picture) ?: return
        val links = row.links.split('+').toSet()
        val current = strength[layer.ordinal][kind.ordinal][i]
        val tileType = PictureCatalog.legacyRow(world, solid[i])?.type
        if ((from != null && "STOP" in links) ||
            (tileType == "BUSHES" && from != null) ||
            (!remove && current == INDESTRUCTIBLE) || current == newStrength || current == 0
        ) {
            return
        }
        strength[layer.ordinal][kind.ordinal][i] = newStrength
        if ("LEFT" in links) hitLinked(tileX - 1, tileY, layer, kind, newStrength, "RIGHT", destroyed, remove)
        if ("RIGHT" in links) hitLinked(tileX + 1, tileY, layer, kind, newStrength, "LEFT", destroyed, remove)
        if ("UP" in links) hitLinked(tileX, tileY - 1, layer, kind, newStrength, "DOWN", destroyed, remove)
        if ("DOWN" in links) hitLinked(tileX, tileY + 1, layer, kind, newStrength, "UP", destroyed, remove)
        if (newStrength != 0) return

        strength[layer.ordinal][0][i] = 0
        changed += i
        if (layer == StructureLayer.SOLID) {
            // The top layer goes with the building it stands on.
            strength[1][0][i] = 0
            val type = row.type
            if (type.startsWith("BRIDGE_")) {
                // Legacy hit_wall(): a destroyed bridge leaves water.
                base[i] = waterPicture()
                solid[i] = -1
            }
        }
        destroyed += DestroyedStructure(
            tileX = tileX,
            tileY = tileY,
            layer = layer,
            explosion = row.explosion,
            legacyType = row.type,
            goalContribution = if (layer == StructureLayer.SOLID) goal[i] else 0,
        )
    }

    /** Legacy search_type(WATER): the first water picture of this world. */
    private fun waterPicture(): Int =
        (0 until 1000).firstOrNull { PictureCatalog.legacyRow(world, it)?.type == "WATER" } ?: -1

    companion object {
        const val INDESTRUCTIBLE: Int = 1000
    }
}
