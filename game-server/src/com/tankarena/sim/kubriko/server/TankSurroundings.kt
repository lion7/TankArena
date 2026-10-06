package com.tankarena.sim.kubriko.server

/** What a tank needs from the match to move like the original (src/tanks/control.c new_position()). */
internal interface TankSurroundings {
    val structures: StructureGrid
    val worldWidth: Int
    val worldHeight: Int

    /** Every vehicle in the match, for the legacy vehicle-against-vehicle check. */
    fun tanks(): List<ServerTankActor>

    /** Wear down a structure the tank crashed into (legacy HT_CRASH). */
    fun damageStructure(tileX: Int, tileY: Int, layer: StructureLayer, kind: HitKind, power: Int)
}
