package nl.tankarena.sim

import nl.tankarena.sim.map.MapDocument
import nl.tankarena.input.PlayerIntentFrame
import nl.tankarena.sim.legacy.CanonicalMapDefinition
import nl.tankarena.protocol.InputFrame
import nl.tankarena.protocol.snapshot.ServerFrame
import nl.tankarena.protocol.snapshot.WorldSnapshot

interface MatchClient {
    fun submitInput(input: InputFrame)
    fun tick(): ServerFrame
    fun currentServerFrame(): ServerFrame
    fun dispose()
}

class LocalMatchClient private constructor(
    private val prototype: Match,
    /** Records the inputs and result of the match, so it can be replayed as a test. */
    val recorder: MatchRecorder? = null,
) : MatchClient {

    private val latestInputs: MutableMap<Int, InputFrame> = mutableMapOf()
    private var tickIndex: Long = 0

    override fun submitInput(input: InputFrame) {
        latestInputs[input.playerId] = input
    }

    override fun tick(): ServerFrame {
        val intents = if (latestInputs.isEmpty()) {
            emptyMap()
        } else {
            latestInputs.mapValues { (_, frame) -> frame.toIntent() }
        }
        recorder?.record(tickIndex, intents)
        val world = prototype.tick(intents)
        recorder?.recordEvents(prototype, tickIndex, world.events)
        tickIndex++
        return toServerFrame(world)
    }

    override fun currentServerFrame(): ServerFrame = toServerFrame(prototype.snapshot())

    override fun dispose() {
        prototype.dispose()
    }

    private fun toServerFrame(world: WorldSnapshot): ServerFrame = ServerFrame(
        tick = world.tick,
        world = world,
        playerViews = prototype.buildPlayerViews(),
    )

    companion object {
        /** [recordAs] names the shipped map to record the match under; null plays without recording. */
        fun fromMap(
            map: MapDocument,
            mode: MatchMode = MatchMode.SINGLE,
            recordAs: String? = null,
            dual: DualSetup = DualSetup(),
            ruleset: Ruleset = Ruleset.CLASSIC,
            cheats: Cheats = Cheats(),
            players: Int = mode.defaultPlayers,
        ): LocalMatchClient {
            val prototype = Match.fromMap(map, mode, dual, ruleset, cheats, players)
            prototype.initialize()
            return LocalMatchClient(prototype, recordAs?.let { MatchRecorder(it, mode, dual.takeIf { mode == MatchMode.DUAL }, ruleset, cheats, players) })
        }

        internal fun fromCanonicalMap(map: CanonicalMapDefinition, mode: MatchMode = MatchMode.SINGLE): LocalMatchClient {
            val prototype = Match.fromCanonicalMap(map, mode)
            prototype.initialize()
            return LocalMatchClient(prototype)
        }
    }
}

private fun InputFrame.toIntent(): PlayerIntentFrame = PlayerIntentFrame(
    forward = forward,
    reverse = reverse,
    turnLeft = turnLeft,
    turnRight = turnRight,
    aimLeft = aimLeft,
    aimRight = aimRight,
    firePrimary = firePrimary,
    fireSecondary = fireSecondary,
    shield = shield,
    cycleWeaponLeft = cycleWeaponLeft,
    cycleWeaponRight = cycleWeaponRight,
    selectTankId = selectTankId,
    pointX = pointX,
    pointY = pointY,
)

/**
 * Plays a [MatchRecording] back as a match the client can show: every [tick] applies the inputs
 * recorded for it; [submitInput] is ignored. The match runs as long as the recording, then [finished].
 */
class ReplayMatchClient(private val recording: MatchRecording, map: MapDocument) : MatchClient {
    private val match = Match.fromMap(map, recording.mode, recording.dual ?: DualSetup(), recording.ruleset, recording.cheats, recording.playerCount)
    private val byTick = recording.inputs.groupBy { it.tick }
    private val current = mutableMapOf<Int, PlayerIntentFrame>()
    private var tickIndex: Long = 0

    init {
        match.initialize()
    }

    /** Ticks played so far. */
    val ticksPlayed: Long get() = tickIndex

    /** The recording has no more ticks. */
    val finished: Boolean get() = tickIndex >= recording.ticks

    override fun submitInput(input: InputFrame) = Unit

    override fun tick(): ServerFrame {
        byTick[tickIndex]?.forEach { current[it.playerId] = it.intent }
        val world = match.tick(current.toMap())
        tickIndex++
        return ServerFrame(tick = world.tick, world = world, playerViews = match.buildPlayerViews())
    }

    override fun currentServerFrame(): ServerFrame {
        val world = match.snapshot()
        return ServerFrame(tick = world.tick, world = world, playerViews = match.buildPlayerViews())
    }

    override fun dispose() = match.dispose()
}

