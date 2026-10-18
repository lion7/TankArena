package com.tankarena.sim.kubriko.server

/** What a tank needs from the match to move like the original (src/tanks/control.c new_position()). */
internal interface TankSurroundings {
    val structures: StructureGrid
    val worldWidth: Int
    val worldHeight: Int

    /** The match tick, for the computer driver's timers. */
    val tick: Long get() = 0L

    /** Every vehicle in the match, for the legacy vehicle-against-vehicle check. */
    fun tanks(): List<ServerTankActor>

    /** Legacy check_obj_collide(): is a train car within [radius] + 12 of ([x], [y]) at about [height]? */
    fun trainAt(x: Int, y: Int, radius: Int, height: Int): Boolean = false

    /** Legacy get_type(x, y, 0): the type of the base picture at a tile (ramps), or null. */
    fun baseType(tileX: Int, tileY: Int): String? = null

    /** Wear down a structure [by] crashed into (legacy HT_CRASH). */
    fun damageStructure(tileX: Int, tileY: Int, layer: StructureLayer, kind: HitKind, power: Int, by: ServerTankActor? = null)
}
