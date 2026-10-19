package com.tankarena.sim

import com.tankarena.sim.map.MapDocument
import com.tankarena.input.PlayerIntentFrame
import com.tankarena.protocol.snapshot.GameEvent
import com.tankarena.protocol.snapshot.PROTOCOL_VERSION
import com.tankarena.protocol.snapshot.ServerFrame
import com.tankarena.protocol.snapshot.WorldSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Version of the gameplay rules. Bump it whenever a change makes the same inputs play out
 * differently (movement, damage, weapon geometry, AI ...): recordings made under older rules no
 * longer replay to their outcome and have to be played again.
 *
 * 1: first recordings (centre-fired guns, sliding along walls).
 * 2: guns fire from their legacy barrels, chain guns per barrel, tanks bounce off walls.
 * 3: destructible structures with pixel collisions, turrets fall with their building, vehicles
 *    collide by radius and shove each other by mass.
 * 4: helicopters and planes fly, the remaining weapons, bonuses and soldiers, the mortar's recoil,
 *    hits push vehicles, the start shield lasts 275 ticks.
 * 5: trains run on the rails, locks, destroyers and enforcers work, objects sit at their legacy
 *    centres, blasts set off mines.
 * 6: B52 bombers and the zeppelin fly like the original, shots bounce off force fields, ramps
 *    throw vehicles into the air.
 * 7: night maps add the light switch as a 13th weapon slot.
 * 8: shots hit vehicles by their pictures along their path, after the walls (line_obj_hit_point()).
 * 9: computer tanks drive like the original (fuzzy logic, waypoints); player-vs-player rounds
 *    with pads, lives, flags and races.
 * 10: building goals are signed (the player's own buildings count against them); start pads hand
 *     out their own weapons.
 * 11: turrets fire what their kind fires (rocket turrets), aim in 16 directions from their gun
 *     barrels, and only at the side they were set to shoot at.
 * 12: the game moves in legacy steps of 3 ticks: timers run out on the first step at or after their
 *     length (turning, refire, lava, shields ...), per-step rules run once per step, the start
 *     shield is 15 blinks after 200 ticks.
 */
const val GAME_RULES_VERSION: Int = 12

/**
 * A played mission as the server saw it: what every player pressed on every tick, plus how the
 * mission ended. The server is deterministic, so replaying the inputs against the same scene must
 * end the same way; recorded playthroughs therefore double as regression tests.
 *
 * Inputs are stored as changes only: an entry means "from this tick on, this player presses this".
 */
@Serializable
data class MatchRecording(
    val protocolVersion: Int = PROTOCOL_VERSION,
    /** The ruleset's rules version the match was played under; recordings without one predate versioning. */
    val rulesVersion: Int = 1,
    /** The ruleset the match was played with; recordings without one are Classic. */
    val ruleset: Ruleset = Ruleset.CLASSIC,
    /** Shipped scene base name, e.g. `begin1`. */
    val mission: String,
    val mode: MatchMode,
    /** Player vs player: the round's vehicles, laps and score. */
    val dual: DualSetup? = null,
    val inputs: List<RecordedInput> = emptyList(),
    val result: RecordedResult? = null,
    /** Ticks played, including the time under the end-of-mission banner. */
    val ticks: Long = 0,
) {
    fun toJson(): String = json.encodeToString(this)

    companion object {
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

        fun fromJson(text: String): MatchRecording = json.decodeFromString(text)
    }
}

@Serializable
data class RecordedInput(val tick: Long, val playerId: Int, val intent: PlayerIntentFrame)

/** How a mission ended (the legacy goal counters, kills and the tick of the result). */
@Serializable
data class RecordedResult(
    val won: Boolean,
    val reason: String,
    val kills: Int,
    val goalGood: Int,
    val goalBad: Int,
    val tick: Long,
)

/** Collects a [MatchRecording] while a [LocalMatchClient] plays. */
class MatchRecorder(
    private val mission: String,
    private val mode: MatchMode,
    private val dual: DualSetup? = null,
    private val ruleset: Ruleset = Ruleset.CLASSIC,
) {
    private val inputs = mutableListOf<RecordedInput>()
    private val lastIntent = mutableMapOf<Int, PlayerIntentFrame>()
    private var result: RecordedResult? = null
    private var ticks: Long = 0

    internal fun record(tick: Long, intents: Map<Int, PlayerIntentFrame>) {
        for ((playerId, intent) in intents.toSortedMap()) {
            if (lastIntent[playerId] == intent) continue
            lastIntent[playerId] = intent
            inputs += RecordedInput(tick, playerId, intent)
        }
        ticks = tick + 1
    }

    internal fun recordEvents(match: Match, tick: Long, events: List<GameEvent>) {
        if (result != null) return
        val end = events.filterIsInstance<GameEvent.MissionResult>().firstOrNull() ?: return
        result = RecordedResult(
            won = end.won,
            reason = end.reason,
            kills = end.kills,
            goalGood = match.goalProgressGood,
            goalBad = match.goalProgressBad,
            tick = tick,
        )
    }

    fun recording(): MatchRecording = MatchRecording(
        rulesVersion = ruleset.rulesVersion,
        ruleset = ruleset,
        mission = mission,
        mode = mode,
        dual = dual,
        inputs = inputs.toList(),
        result = result,
        ticks = ticks,
    )
}

/** Plays a [MatchRecording] back on the headless server. */
object MatchReplay {
    /**
     * Replays [recording] against the scene it was recorded on and returns how the mission ended
     * (null if it did not end within the recorded ticks).
     */
    fun play(recording: MatchRecording, map: MapDocument): RecordedResult? {
        val recorder = MatchRecorder(recording.mission, recording.mode, recording.dual, recording.ruleset)
        drive(recording, map) { match, tick, world ->
            recorder.recordEvents(match, tick, world.events)
            true
        }
        return recorder.recording().result
    }

    /**
     * Replays [recording] tick by tick and hands each tick's [ServerFrame] to [onFrame], as a client
     * would have received it, until the recording ends or [onFrame] returns false.
     */
    fun replayFrames(
        recording: MatchRecording,
        map: MapDocument,
        onFrame: (ServerFrame) -> Boolean,
    ) = drive(recording, map) { match, _, world ->
        onFrame(ServerFrame(tick = world.tick, world = world, playerViews = match.buildPlayerViews()))
    }

    private inline fun drive(
        recording: MatchRecording,
        map: MapDocument,
        step: (Match, Long, WorldSnapshot) -> Boolean,
    ) {
        val match = Match.fromMap(map, recording.mode, recording.dual ?: DualSetup(), recording.ruleset)
        match.initialize()
        try {
            val byTick = recording.inputs.groupBy { it.tick }
            val current = mutableMapOf<Int, PlayerIntentFrame>()
            for (tick in 0 until recording.ticks) {
                byTick[tick]?.forEach { current[it.playerId] = it.intent }
                if (!step(match, tick, match.tick(current.toMap()))) return
            }
        } finally {
            match.dispose()
        }
    }
}
