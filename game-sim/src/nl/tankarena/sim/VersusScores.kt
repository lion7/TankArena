package nl.tankarena.sim

import kotlinx.serialization.Serializable

/**
 * One map's record in a series against each other: the matches played on it, and the matches and
 * rounds each side (0: players 1 and 3, 1: players 2 and 4) won there.
 */
@Serializable
data class MapScore(
    val played: Int = 0,
    val matchWins: List<Int> = listOf(0, 0),
    val roundWins: List<Int> = listOf(0, 0),
) {
    /** The record as the screens show it, e.g. "played 2× · matches 1–1 · rounds 3–2". */
    fun text(): String =
        "played $played× · matches ${matchWins[0]}–${matchWins[1]} · rounds ${roundWins[0]}–${roundWins[1]}"
}

/**
 * Extended: the record of a series against each other, from when the players joined until they join
 * anew, per map (by base name) and over every map. Kept by the local game and by a network host.
 */
class VersusScores {
    private val maps = LinkedHashMap<String, MapScore>()

    /** The rounds each side has won over every map: the losers tank goes by these. */
    val rounds: List<Int>
        get() = maps.values.fold(listOf(0, 0)) { total, score -> total.zip(score.roundWins) { a, b -> a + b } }

    /** The record of [map], or null when it has not been played in this series. */
    fun of(map: String): MapScore? = maps[map.uppercase()]

    /** Side [winner] won a round on [map]. */
    fun round(map: String, winner: Int) = update(map) { it.copy(roundWins = it.roundWins.plusOne(winner)) }

    /** A match on [map] is over: decided for side [winner], or left before that (null). */
    fun matchEnded(map: String, winner: Int?) = update(map) { score ->
        score.copy(played = score.played + 1, matchWins = if (winner != null) score.matchWins.plusOne(winner) else score.matchWins)
    }

    /** A new series: the players joined anew. */
    fun clear() = maps.clear()

    private fun update(map: String, change: (MapScore) -> MapScore) {
        val key = map.uppercase()
        maps[key] = change(maps[key] ?: MapScore())
    }

    private fun List<Int>.plusOne(side: Int): List<Int> = mapIndexed { i, n -> if (i == side) n + 1 else n }
}
