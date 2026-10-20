package com.tankarena.sim

import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.content.PictureCatalog
import com.tankarena.content.TankArenaWorld
import kotlin.random.Random

/**
 * The rules of a player-vs-player round (legacy `game.mode == DUAL`): the start pads of
 * src/objects/player.c, the flags of src/objects/flag.c and the races of control_race().
 *
 * - A pad gives its player four vehicles (`ply.lives`); losing the last one loses the round, and
 *   once the building on the pad is destroyed the vehicle out there is the last one.
 * - With flags on the map it is a flag hunt: one flag per player is picked at random, vehicles
 *   respawn without limit, and bringing the other player's flag to your own pad wins.
 * - Race pads spawn the race tank, which must pass all its checkpoints and cross the start line,
 *   [DualSetup.raceLaps] times; it restarts at the last checkpoint when destroyed.
 */
internal class DualRules(
    private val setup: DualSetup,
    private val world: TankArenaWorld,
    private val widthTiles: Int,
    private val heightTiles: Int,
    private val structures: StructureGrid,
    private val random: Random,
) {
    /** Legacy `game.winner`: -1 while the round is on, else the player (0 or 1) who won it. */
    var winner: Int = -1
        private set

    /** Legacy `game.flag_hunt`. */
    var flagHunt: Boolean = false
        private set

    private class Checkpoint(val x: Int, val y: Int, val direction: Int) {
        var passed = false
    }

    /** Legacy `ply` of a race pad: its checkpoints, the last one passed and the laps to go. */
    private class Race(val tank: Tank, val checkpoints: List<Checkpoint>, var laps: Int) {
        var lastCheckpoint = -1
    }

    private val races = mutableListOf<Race>()
    private val carriers = java.util.IdentityHashMap<Flag, Tank>()

    /**
     * Legacy prepare_player() and prepare_flag() for the round: returns the flags that are not in
     * play (all of them outside player vs player).
     */
    fun prepare(tanks: List<Tank>, flags: List<Flag>): List<Flag> {
        // Legacy load_map(): of each player's flags one is picked at random (`game.flag_no`).
        val byType = flags.groupBy { it.flagType }
        flagHunt = flags.isNotEmpty()
        val chosen = byType.values.map { it[random.nextInt(it.size)] }.toSet()
        for (tank in tanks) {
            if (tank.startPadType < 0) continue
            val player = tank.startPadType and 1
            if (tank.startPadType shr 1 == DualSetup.PAD_RACE) {
                races += Race(tank, prepareTrack(player), setup.raceLaps)
                tank.infiniteLives = true
            } else if (flagHunt) {
                // Legacy control_player(): no lives are lost during a flag hunt.
                tank.infiniteLives = true
            }
            // Legacy dual_game(): "ROUND n" and the score, from this player's side.
            tank.showMessage(
                "ROUND ${setup.round}",
                "${setup.wins.getOrElse(player) { 0 }} : ${setup.wins.getOrElse(player xor 1) { 0 }}",
            )
        }
        return flags.filter { it !in chosen }
    }

    /** Legacy prepare_track(): the player's checkpoint tiles, row by row, at most 25. */
    private fun prepareTrack(player: Int): List<Checkpoint> {
        val track = mutableListOf<Checkpoint>()
        for (y in 0 until heightTiles) for (x in 0 until widthTiles) {
            if (track.size >= 25) return track
            val type = PictureCatalog.legacyRow(world, structures.base[x + y * widthTiles])?.type ?: continue
            val side = if (player == 0) "P1" else "P2"
            val direction = when (type) {
                "CHECK_${side}_UP" -> 0
                "CHECK_${side}_RIGHT" -> 4
                "CHECK_${side}_DOWN" -> 8
                "CHECK_${side}_LEFT" -> 12
                else -> continue
            }
            track += Checkpoint(x * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2, y * LEGACY_TILE_SIZE + LEGACY_TILE_SIZE / 2, direction)
        }
        return track
    }

    /**
     * One tick: pads, flags and races. Returns true on the tick the round is decided, after
     * which [winner] holds the winner.
     */
    fun step(tanks: List<Tank>, flags: List<Flag>, beep: (Tank) -> Unit): Boolean {
        val before = winner
        stepPads(tanks)
        stepFlags(tanks, flags)
        stepRaces(beep)
        return before == -1 && winner != -1
    }

    /** Legacy control_player(). */
    private fun stepPads(tanks: List<Tank>) {
        for (tank in tanks) {
            if (tank.startPadType < 0 || tank.startPadType shr 1 == DualSetup.PAD_RACE) continue
            val player = tank.startPadType and 1
            if (tank.armor <= 0) {
                if (!tank.infiniteLives && tank.lives <= 0 && winner == -1) winner = player xor 1
                continue
            }
            // With the building on the pad destroyed this vehicle is the player's last.
            val tileX = Math.floorDiv(tank.spawnX, LEGACY_TILE_SIZE)
            val tileY = Math.floorDiv(tank.spawnY, LEGACY_TILE_SIZE)
            if (!flagHunt && tileX in 0 until widthTiles && tileY in 0 until heightTiles &&
                structures.solid[tileX + tileY * widthTiles] >= 0 && !structures.isStanding(tileX, tileY)
            ) {
                tank.lives = 1
            }
        }
    }

    /** Legacy `und.in_base`: alive and within 7 px of its own pad. */
    private fun inBase(tank: Tank): Boolean {
        if (tank.startPadType < 0 || tank.armor <= 0) return false
        val dx = tank.positionX - tank.spawnX
        val dy = tank.positionY - tank.spawnY
        return dx * dx + dy * dy < 7 * 7
    }

    /** Legacy check_obj_collide() with FLAG_TYPE, write_flag() and control_flag(). */
    private fun stepFlags(tanks: List<Tank>, flags: List<Flag>) {
        for (flag in flags) {
            var carrier = carriers[flag]
            if (carrier != null && carrier.armor <= 0) {
                // The flag stays where its carrier fell.
                carriers.remove(flag)
                flag.isCarried = false
                carrier = null
            }
            if (carrier == null) {
                val (fx, fy) = centreOf(flag)
                carrier = tanks.firstOrNull { tank ->
                    val reach = tank.spec.radius + FLAG_RADIUS
                    val dx = tank.positionX - fx
                    val dy = tank.positionY - fy
                    tank.armor > 0 && dx * dx + dy * dy < reach * reach
                } ?: continue
                carrier.showMessage("GOT A FLAG!")
                carrier.flagsPicked += 1
                carriers[flag] = carrier
                flag.isCarried = true
            }
            // Carried flags ride on their carrier, P2's two pixels further.
            moveTo(flag, carrier.positionX + flag.flagType * 2, carrier.positionY + flag.flagType * 2)
            if (inBase(carrier) && (carrier.startPadType and 1) != flag.flagType && winner == -1) {
                winner = flag.flagType xor 1
            }
        }
    }

    /** Legacy control_race(). */
    private fun stepRaces(beep: (Tank) -> Unit) {
        for (race in races) {
            val car = race.tank
            if (car.armor <= 0) continue
            race.checkpoints.forEachIndexed { i, checkpoint ->
                val dx = car.positionX - checkpoint.x
                val dy = car.positionY - checkpoint.y
                if (!checkpoint.passed && dx * dx + dy * dy < 70 * 70) {
                    race.lastCheckpoint = i
                    checkpoint.passed = true
                    // A destroyed race tank starts again at the last checkpoint it passed.
                    car.moveSpawn(checkpoint.x, checkpoint.y, checkpoint.direction)
                    beep(car)
                }
            }
            if (groundType(car.positionX, car.positionY) != "START" || race.checkpoints.any { !it.passed }) continue
            race.checkpoints.forEach { it.passed = false }
            race.lastCheckpoint = -1
            car.resetSpawn()
            race.laps -= 1
            val player = car.startPadType and 1
            if (race.laps == 0 && winner == -1) {
                winner = player
            } else if (race.laps > 0) {
                car.showMessage(if (race.laps > 1) "${race.laps} MORE LAPS" else "FINAL LAP!")
            }
        }
    }

    /** Legacy get_underground(): the base picture's type under a point, else the solid one's. */
    private fun groundType(x: Int, y: Int): String? {
        val tileX = Math.floorDiv(x, LEGACY_TILE_SIZE)
        val tileY = Math.floorDiv(y, LEGACY_TILE_SIZE)
        if (tileX !in 0 until widthTiles || tileY !in 0 until heightTiles) return null
        val i = tileX + tileY * widthTiles
        val base = structures.base[i]
        val picture = if (base >= 0) base else structures.solid[i]
        return PictureCatalog.legacyRow(world, picture)?.type
    }

    private fun centreOf(flag: Flag): Pair<Int, Int> {
        val body = flag.body
        return (body.position.x + body.size.width / 2f).toInt() to (body.position.y + body.size.height / 2f).toInt()
    }

    private fun moveTo(flag: Flag, x: Int, y: Int) {
        val body = flag.body
        body.position = ScenePoint((x - body.size.width / 2f), (y - body.size.height / 2f))
    }

    private companion object {
        /** Legacy make_flag(): `create_object(FLAG_TYPE, x, y, 0, 4)`. */
        const val FLAG_RADIUS = 4
    }
}
