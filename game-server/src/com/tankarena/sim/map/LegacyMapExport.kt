package com.tankarena.sim.map

import com.tankarena.content.LEGACY_TILE_SIZE
import com.tankarena.sim.B52
import com.tankarena.sim.Destroyer
import com.tankarena.sim.Enforcer
import com.tankarena.sim.Flag
import com.tankarena.sim.Goal
import com.tankarena.sim.LegacyVehicles
import com.tankarena.sim.Lock
import com.tankarena.sim.Mine
import com.tankarena.sim.Product
import com.tankarena.sim.Tank
import com.tankarena.sim.Train
import com.tankarena.sim.Turret
import com.tankarena.sim.VehicleKind
import com.tankarena.sim.Warp
import com.tankarena.content.MapSceneSidecar
import java.io.ByteArrayOutputStream

/**
 * Writes a map as an original `.MAP` (version 6, not locked) that the original game plays and its
 * editor opens (ADR 0022, option C). Each object record is what the original editor's `make_*()`
 * would have saved, with the map's own values on top; the bytes the rewrite does not model come from
 * the map's [LegacyPatch] when it was imported, so an imported map exports to its original file.
 */
object LegacyMapExport {
    const val MAP_VERSION = 6
    const val HEADER_SIZE = 84
    const val OBJECT_SIZE = 696
    private const val OBJ_MAGIC = 0x4A424F54
    private const val MAX_LONG = 0x7FFFFFFF

    /** Where objects without a saved address of their own are put (legacy pointers are only matched). */
    private const val FIRST_ADDRESS = 0x00400000

    private const val U = 84

    fun export(document: MapDocument): ByteArray {
        val unpatched = exportUnpatched(document)
        val patch = document.legacy ?: return unpatched.bytes()
        return unpatched.bytes(patch)
    }

    /** The parts of a `.MAP`: header, layers and texts, and one record per object. */
    class Parts(val head: ByteArray, val records: List<ByteArray>) {
        fun bytes(patch: LegacyPatch? = null): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(patch?.head?.let { applyRuns(head, it) } ?: head)
            for ((index, record) in records.withIndex()) {
                out.write(patch?.objects?.get(index.toString())?.let { applyRuns(record, it) } ?: record)
            }
            return out.toByteArray()
        }
    }

    fun exportUnpatched(document: MapDocument): Parts {
        val states = MapConversion.objectsOf(document).map { it.save() }
        return Parts(head(MapConversion.sidecarOf(document), document.legacy?.texts), records(states))
    }

    /** The header, layers and texts; [rawTexts] are an imported map's texts as they were in its file. */
    fun head(sidecar: MapSceneSidecar, rawTexts: List<String>?): ByteArray {
        val metadata = sidecar.metadata
        val layers = requireNotNull(sidecar.tileLayers)
        val cells = metadata.widthTiles * metadata.heightTiles
        val texts = listOf(sidecar.missionText.briefing, sidecar.missionText.success, sidecar.missionText.failure)
            .mapIndexed { i, text ->
                // The importer trims the texts; the file's own text is kept while it reads the same.
                val raw = rawTexts?.getOrNull(i)?.takeIf { it.trim() == text }
                (raw ?: if (text.isEmpty()) "" else text + "\n").toByteArray(Charsets.ISO_8859_1)
            }

        val head = ByteArray(HEADER_SIZE + cells * 10 + texts.sumOf { it.size })
        val w = Writer(head)
        w.string(0, metadata.missionCode, 22)
        w.string(22, metadata.nextMissionCode.orEmpty(), 22)
        w.int(44, metadata.widthTiles)
        w.int(48, metadata.heightTiles)
        var offset = HEADER_SIZE + cells * 10
        for (i in 0..2) {
            w.int(52 + i * 4, offset)
            offset += texts[i].size
        }
        w.int(64, offset)
        // Legacy clear_map(): random_bonus = TRUE (-1).
        w.short(68, if (metadata.randomBonus) -1 else 0)
        w.byte(70, metadata.world.ordinal)
        w.byte(71, if (metadata.publicPassword) 1 else 0)
        w.short(72, if (metadata.night) 1 else 0)
        w.byte(74, metadata.modeCompatibility.ordinal)
        w.byte(75, metadata.legacyMapVersion.takeIf { it > 0 } ?: MAP_VERSION)
        w.short(76, metadata.mapContents)
        w.short(78, OBJECT_SIZE)
        // shareware 0 (a registered map), lock 0.

        var at = HEADER_SIZE
        for (layer in listOf(layers.base, layers.solid, layers.top)) for (cell in layer) { w.short(at, cell); at += 2 }
        for (layer in listOf(layers.goalLayer, layers.bonusLayer, layers.manTypeLayer, layers.manAmountLayer)) for (cell in layer) { w.byte(at, cell); at += 1 }
        for (text in texts) { text.copyInto(head, at); at += text.size }
        return head
    }

    /** An object's record with no addresses filled in, to see which of its bytes an edit changes. */
    fun bareRecord(state: MapObject): ByteArray? = record(state, 0) { 0 }

    /** One record per object, pointers made of saved addresses (`ref`) or new ones. */
    internal fun records(states: List<MapObject>): List<ByteArray> {
        val addresses = states.mapIndexed { i, state -> MapObjects.ref(state).takeIf { it != 0 } ?: (FIRST_ADDRESS + i * OBJECT_SIZE) }
        fun turretOf(train: Train.State): Int =
            states.indexOfFirst { it is Turret.State && it.ownerRef != 0 && it.ownerRef == train.ref }.let { if (it < 0) 0 else addresses[it] }
        return states.mapIndexedNotNull { i, state -> record(state, addresses[i], ::turretOf) }
    }

    private fun record(state: MapObject, address: Int, turretOf: (Train.State) -> Int): ByteArray? {
        val r = ByteArray(OBJECT_SIZE)
        val w = Writer(r)
        val (cx, cy) = MapObjects.centre(state)
        // legacy create_object(type, x, y, h, rad): the record's corner is the centre - 16.
        fun objectHeader(type: Int, x: Int, y: Int, h: Int, rad: Int, gc: Int = 0) {
            w.int(0, OBJ_MAGIC)
            w.int(4, address)
            w.int(48, type)
            w.int(52, x)
            w.int(56, y)
            w.int(60, h)
            w.int(64, rad)
            w.int(68, gc)
            w.float(72, x.toFloat())
            w.float(76, y.toFloat())
            w.float(80, h.toFloat())
        }
        when (state) {
            is Tank.State -> if (state.startPadType >= 0) {
                // Legacy make_player().
                val type = state.startPadType
                objectHeader(10, cx - 16, cy - 16, if (type == 2 || type == 3) 7 else 1, 0, state.goalContribution)
                w.int(U, type)
                w.int(U + 4, state.bodyDirection / 4)
                w.int(U + 8, state.lives)
                w.int(U + 16, -1)
                val ammo = state.padAmmo
                if (ammo != null) {
                    // Legacy plweap_fun(): weap.active = TRUE (-1).
                    w.int(U + 224, -1)
                    for (k in 0 until 12) w.int(U + 228 + k * 4, ammo.getOrElse(k) { 0 })
                }
            } else {
                tank(w, state, cx, cy, ::objectHeader)
            }
            is Turret.State -> {
                objectHeader(3, cx - 16, cy - 16, if (state.ownerRef != 0) 2 else 4, 0, state.goalContribution)
                w.int(12, state.ownerRef)
                w.int(U, state.turretType)
                w.int(U + 4, state.turretDirection)
                w.int(U + 12, state.delay)
                w.int(U + 16, state.power)
                w.int(U + 20, state.radius)
                w.int(U + 24, state.shootAt)
                w.int(U + 28, if (state.fixed) 1 else 0)
                w.short(U + 40, state.forbidDirections)
            }
            is B52.State -> {
                objectHeader(1, state.x - 16, state.y - 16, 1, 20, state.goalContribution)
                w.int(U + 8, state.heading)
                w.int(U + 12, state.armor)
                w.int(U + 16, 4)
            }
            is Mine.State -> {
                objectHeader(5, cx - 16, cy - 16, 0, 3)
                w.int(U, if (state.heavy) 1 else 0)
                w.int(U + 8, state.damage)
            }
            is Train.State -> {
                objectHeader(if (state.isEngine) 8 else 9, state.x - 16, state.y - 16, 1, 12, state.goalContribution)
                // Legacy prepare_train(): only cars saved with a control function drive.
                if (state.driving) w.int(24, 1)
                w.int(U, state.speed)
                w.int(U + 8, state.direction)
                w.int(U + 12, state.armor)
                w.int(U + 16, if (state.smokes) 0 else MAX_LONG)
                w.int(U + 24, state.frontRef)
                w.int(U + 28, state.rearRef)
                w.int(U + 32, turretOf(state))
                val (tx, ty) = MapObjects.trainTile(state)
                w.int(U + 36, tx)
                w.int(U + 40, ty)
                w.int(U + 44, state.picture)
                w.float(U + 48, state.railsStart)
                w.int(U + 52, state.railsType)
                w.int(U + 56, state.railsDir)
                w.int(U + 60, state.railsX)
                w.int(U + 64, state.railsY)
                w.int(U + 68, state.endType)
                w.int(U + 72, state.endDir)
                w.int(U + 76, state.endX)
                w.int(U + 80, state.endY)
            }
            is Warp.State -> {
                objectHeader(11, cx - 16, cy - 16, 24, 0)
                w.int(U, state.targetX - 16)
                w.int(U + 4, state.targetY - 16)
            }
            is Flag.State -> {
                objectHeader(13, cx - 16, cy - 16, 0, 4)
                w.int(U, state.flagType)
                w.int(U + 4, state.number)
            }
            is Lock.State -> {
                objectHeader(14, cx - 16, cy - 16, 24, 0, state.goalContribution)
                w.int(U, state.lockX - 16)
                w.int(U + 4, state.lockY - 16)
                w.int(U + 8, state.activation)
                w.int(U + 12, state.target)
            }
            is Goal.State -> {
                objectHeader(15, cx - 16, cy - 16, 24, 0, state.contribution)
                w.int(8, state.targetRef)
                w.int(U, state.radius)
                w.int(U + 4, state.who)
            }
            is Destroyer.State -> {
                objectHeader(16, cx - 16, cy - 16, 24, 0, state.goalContribution)
                w.int(U, state.radius)
                w.int(U + 4, if (state.immediate) -1 else 0)
                w.int(U + 8, state.what)
            }
            is Enforcer.State -> {
                objectHeader(17, cx - 16, cy - 16, 24, 0)
                w.int(U, state.radius)
                // Legacy make_enforcer() sets these TRUE (-1) or FALSE.
                w.int(U + 4, if (state.good) -1 else 0)
                w.int(U + 8, if (state.bad) -1 else 0)
                w.int(U + 12, state.weapon)
                w.int(U + 16, if (state.fire) -1 else 0)
                w.int(U + 20, if (state.change) -1 else 0)
                w.int(U + 24, state.delay)
            }
            is Product.State -> {
                objectHeader(19, cx - 16, cy - 16, 0, 4)
                w.int(U, state.productType)
                w.int(U + 4, state.price)
            }
            // The zeppelin is made by the shareware game itself, never saved in a map.
            else -> return null
        }
        return r
    }

    /** Legacy make_tank() with the map's values: the `tankstruct` at the union. */
    private fun tank(w: Writer, state: Tank.State, cx: Int, cy: Int, objectHeader: (Int, Int, Int, Int, Int, Int) -> Unit) {
        val spec = LegacyVehicles.spec(state.tankType)
        val car = spec.kind == VehicleKind.CAR
        objectHeader(0, cx - 16, cy - 16, if (spec.kind == VehicleKind.PLANE) 1 else spec.height, spec.radius, state.goalContribution)
        w.int(U + 4, state.tankType)
        w.float(U + 20, spec.maxFuel.toFloat())
        w.int(U + 32, state.bodyDirection)
        w.int(U + 36, state.turretDirection)
        w.int(U + 40, LEGACY_TILE_SIZE * LEGACY_TILE_SIZE)
        w.int(U + 88, MAX_LONG)
        w.int(U + 108, state.aiAttackAt.takeIf { it >= 0 } ?: if (car) 0 else 150)
        w.int(U + 112, state.aiAttackTanks)
        w.int(U + 116, state.aiAttackGoals)
        w.int(U + 120, state.aiFireDelay.takeIf { it >= 0 } ?: spec.refireTicks.getOrElse(spec.initialWeapon) { 0 })
        w.int(U + 124, state.aiWaveDelay.takeIf { it >= 0 } ?: spec.waveDelay)
        w.int(U + 132, state.aiActivator)
        w.int(U + 136, if (state.aiActive) 1 else 0)
        w.int(U + 140, state.aiActivationRadius.takeIf { it >= 0 } ?: 250)
        w.int(U + 144, if (state.aiStayActive) 1 else 0)
        w.int(U + 156, -1)
        w.int(U + 160, -1)
        w.int(U + 180, state.wayType)
        w.int(U + 184, state.wayCurrent)
        for (i in 0 until Tank.WAYPOINTS) {
            w.int(U + 188 + i * 8, state.wayPoints.getOrElse(i * 2) { -1 })
            w.int(U + 192 + i * 8, state.wayPoints.getOrElse(i * 2 + 1) { 0 })
        }
        w.int(U + 268, state.controllerId)
        w.int(U + 272, if (state.computer) -1 else 1)
        w.int(U + 276, if (state.computer) 1 else 0)
        w.int(U + 280, if (state.team == 0) 1 else 0)
        w.int(U + 308, MAX_LONG)
        w.int(U + 320, 1)
        w.int(U + 328, state.armor)
        w.int(U + 332, 3000)
        w.int(U + 340, if (state.computer) 0 else 15)
        w.int(U + 352, spec.initialWeapon)
        for (k in 0 until 12) {
            w.int(U + 368 + k * 4, state.ammo.getOrElse(k) { 0 })
            w.int(U + 432 + k * 4, state.power.getOrElse(k) { 0 })
        }
        w.int(U + 504, -1)
        w.int(U + 508, -1)
        // last_min.light_on = TRUE (-1).
        w.int(U + 604, -1)
    }

    /** The runs of [runs] over bytes that are the same in [before] and [after]: an edit drops the others. */
    fun keepRuns(runs: List<PatchRun>, before: ByteArray, after: ByteArray): List<PatchRun> = runs.filter { run ->
        val end = run.offset + run.data.length / 2
        end <= before.size && end <= after.size && (run.offset until end).all { before[it] == after[it] }
    }

    /**
     * An original `.MAP` as export writes it: the header unprotected (legacy protect() XORs it in
     * locked maps) and not locked.
     */
    fun unlocked(bytes: ByteArray): ByteArray {
        val out = bytes.copyOf()
        fun int(at: Int) = (0..3).fold(0) { v, k -> v or ((out[at + k].toInt() and 0xFF) shl (8 * k)) }
        val width = int(44)
        if (width !in 1..300) {
            for (i in 0 until HEADER_SIZE step 4) {
                val v = int(i) xor 0x34312E33
                for (k in 0..3) out[i + k] = (v shr (8 * k)).toByte()
            }
        }
        out[82] = 0
        out[83] = 0
        return out
    }

    /** Patch runs: `[offset, "hex bytes"]`. */
    fun applyRuns(bytes: ByteArray, runs: List<PatchRun>): ByteArray {
        val out = bytes.copyOf()
        for (run in runs) {
            val data = run.data.chunked(2).map { it.toInt(16).toByte() }
            for ((k, b) in data.withIndex()) if (run.offset + k < out.size) out[run.offset + k] = b
        }
        return out
    }

    /** The runs of bytes where [actual] differs from [expected] (equal length), joining gaps under 4 bytes. */
    fun diffRuns(expected: ByteArray, actual: ByteArray): List<PatchRun> {
        val runs = mutableListOf<PatchRun>()
        var i = 0
        while (i < actual.size) {
            if (i < expected.size && expected[i] == actual[i]) { i++; continue }
            var end = i + 1
            while (end < actual.size) {
                val gap = (end until minOf(end + 4, actual.size)).all { it < expected.size && expected[it] == actual[it] }
                if (gap) break
                end++
            }
            runs += PatchRun(i, actual.copyOfRange(i, end).joinToString("") { "%02x".format(it) })
            i = end
        }
        return runs
    }

    private class Writer(val bytes: ByteArray) {
        fun byte(at: Int, value: Int) { bytes[at] = value.toByte() }
        fun short(at: Int, value: Int) { bytes[at] = value.toByte(); bytes[at + 1] = (value shr 8).toByte() }
        fun int(at: Int, value: Int) { for (k in 0..3) bytes[at + k] = (value shr (8 * k)).toByte() }
        fun float(at: Int, value: Float) = int(at, java.lang.Float.floatToRawIntBits(value))
        fun string(at: Int, value: String, length: Int) {
            val data = value.toByteArray(Charsets.ISO_8859_1)
            data.copyInto(bytes, at, 0, minOf(data.size, length - 1))
        }
    }

}
