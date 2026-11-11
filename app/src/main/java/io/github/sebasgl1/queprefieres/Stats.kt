package io.github.sebasgl1.queprefieres

import java.util.Random
import kotlin.math.abs

const val MIAS = "Mis preguntas"

val CATEGORY_EMOJI = mapOf(
    "Divertidas" to "😂", "Difíciles" to "🤯", "Asquerosas" to "🤢", "Superpoderes" to "🦸",
    "Plata y trabajo" to "💸", "Comida" to "🍕", "Tecnología" to "📱", "Colombianas" to "🇨🇴",
    "Picante" to "🌶️", MIAS to "✍️",
)

data class PairStat(val p1: String, val p2: String, val pct: Int, val n: Int)
data class PlayerStat(val name: String, val pct: Int, val n: Int)

/** Cálculos del juego sin nada de Android, para poder probarlos en cada compilación. */
object Stats {
    /** % de veces que cada pareja votó lo mismo (mínimo 2 preguntas juntos). */
    fun affinity(players: List<String>, rounds: Collection<Map<String, Char>>): List<PairStat> {
        val out = mutableListOf<PairStat>()
        for (i in players.indices) for (j in i + 1 until players.size) {
            val both = rounds.filter { players[i] in it && players[j] in it }
            if (both.size < 2) continue
            val same = both.count { it[players[i]] == it[players[j]] }
            out.add(PairStat(players[i], players[j], Math.round(same * 100f / both.size), both.size))
        }
        return out
    }

    /** % de veces que cada jugador quedó en la minoría. */
    fun minority(players: List<String>, rounds: Collection<Map<String, Char>>): List<PlayerStat> =
        players.mapNotNull { p ->
            val mine = rounds.filter { p in it }
            if (mine.isEmpty()) return@mapNotNull null
            val inMin = mine.count { r ->
                val a = r.values.count { it == 'A' }
                val b = r.values.count { it == 'B' }
                a != b && a > 0 && b > 0 && r[p] == (if (a < b) 'A' else 'B')
            }
            PlayerStat(p, Math.round(inMin * 100f / mine.size), mine.size)
        }

    /** Pregunta dispareja: con 8 votos o más, el 90 % o más eligió lo mismo. */
    fun lopsided(a: Int, b: Int): Boolean = a + b >= 8 && maxOf(a, b) * 10 >= (a + b) * 9

    /** Qué tan dividida está: 0 = empate, 100 = todos del mismo lado. */
    fun gap(a: Int, b: Int): Int = if (a + b == 0) 100 else Math.round(abs(a - b) * 100f / (a + b))

    /** Turnos parejos: elige quien menos veces ha elegido, sin repetir al anterior si se puede. */
    fun nextChooser(players: List<String>, turns: Map<String, Int>, last: String, rng: Random): String {
        val min = players.minOf { turns[it] ?: 0 }
        val least = players.filter { (turns[it] ?: 0) == min }
        val cands = least.filter { it != last }.ifEmpty { least }
        return cands[rng.nextInt(cands.size)]
    }

    /** "¿Qué elegiría…?": +1 a quien acierta. Si nadie acierta, +2 a quien eligió. */
    fun guessPoints(votes: Map<String, Char>, secret: Char, chooser: String): Map<String, Int> {
        val right = votes.filter { it.value == secret }.keys
        return if (right.isEmpty() && votes.isNotEmpty()) mapOf(chooser to 2) else right.associateWith { 1 }
    }

    /**
     * Suma los votos de una pregunta al historial. Si en esta misma partida ya se había
     * contado esa pregunta (previous), se reemplaza en vez de sumarse dos veces.
     */
    fun mergeHistory(total: Pair<Int, Int>?, previous: Pair<Int, Int>?, now: Pair<Int, Int>): Pair<Int, Int> {
        val baseA = (total?.first ?: 0) - (previous?.first ?: 0)
        val baseB = (total?.second ?: 0) - (previous?.second ?: 0)
        return (maxOf(0, baseA) + now.first) to (maxOf(0, baseB) + now.second)
    }
}
