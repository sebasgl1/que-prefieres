package io.github.sebasgl1.queprefieres

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

data class Question(val id: String, val categoria: String, val a: String, val b: String)

enum class Mode { VOTACION, ADIVINA }

/** Fases del modo "¿Qué elegiría…?". */
enum class Phase { PASS, SECRET, GUESS, REVEAL }

sealed class Screen {
    data object Home : Screen()
    data object Players : Screen()
    data object Categories : Screen()
    data object Custom : Screen()
    data object Play : Screen()
    data object Summary : Screen()
}

const val MIAS = "Mis preguntas"

val CATEGORY_EMOJI = mapOf(
    "Divertidas" to "😂", "Difíciles" to "🤯", "Asquerosas" to "🤢", "Superpoderes" to "🦸",
    "Plata y trabajo" to "💸", "Comida" to "🍕", "Tecnología" to "📱", "Colombianas" to "🇨🇴",
    "Picante" to "🌶️", MIAS to "✍️",
)

data class PairStat(val p1: String, val p2: String, val pct: Int, val n: Int)
data class PlayerStat(val name: String, val pct: Int, val n: Int)

class Game(context: Context) {
    private val prefs = context.getSharedPreferences("queprefieres", Context.MODE_PRIVATE)
    private val rng = SecureRandom()
    private val builtIn: List<Question> = loadBuiltIn(context)

    val categories: List<String> = builtIn.map { it.categoria }.distinct() + MIAS
    val custom = mutableStateListOf<Question>().apply { addAll(loadCustom()) }
    val enabled = mutableStateListOf<String>().apply {
        val saved = prefs.getString("enabled", null)
        if (saved == null) addAll(categories.filter { it != "Picante" })
        else addAll(saved.split("|").filter { it in categories })
    }
    var voting by mutableStateOf(prefs.getBoolean("voting", true))
    val players = mutableStateListOf<String>().apply {
        addAll((prefs.getString("players", "") ?: "").split("\n").filter { it.isNotBlank() })
    }

    // Favoritas e historial de votos (se guardan en el celular)
    val favs = mutableStateListOf<String>().apply {
        addAll((prefs.getString("favs", "") ?: "").split("|").filter { it.isNotBlank() })
    }
    var onlyFavs by mutableStateOf(prefs.getBoolean("onlyFavs", false))
    private val hist: MutableMap<String, Pair<Int, Int>> = loadHist()
    /** Votos de partidas anteriores para la pregunta actual (en el orden A/B de la pantalla). */
    var prevHist by mutableStateOf<Pair<Int, Int>?>(null)

    var screen by mutableStateOf<Screen>(Screen.Home)
    var mode by mutableStateOf(Mode.VOTACION)

    // Pregunta actual
    var current by mutableStateOf<Question?>(null)
    var swapped by mutableStateOf(false)
    var revealed by mutableStateOf(false)
    var played by mutableIntStateOf(0)
    private val history = mutableListOf<Pair<Question, Boolean>>()

    // Votos: anónimos (sin jugadores) o con nombre
    var anonA by mutableIntStateOf(0)
    var anonB by mutableIntStateOf(0)
    val votes = mutableStateMapOf<String, Char>()
    private val rounds = mutableListOf<Map<String, Char>>()   // votos con nombre de la sesión

    // Modo "¿Qué elegiría…?"
    var chooser by mutableStateOf("")
    var secret by mutableStateOf<Char?>(null)
    var phase by mutableStateOf(Phase.PASS)
    val scores = mutableStateMapOf<String, Int>()
    private val chooserTurns = mutableMapOf<String, Int>()
    private val guessLog = mutableListOf<Triple<String, String, Boolean>>() // adivino, elector, acertó

    val named get() = players.size >= 2

    fun all(): List<Question> = builtIn + custom
    fun countIn(cat: String) = all().count { it.categoria == cat }
    private fun pool() = if (onlyFavs) all().filter { it.id in favs } else all().filter { it.categoria in enabled }
    fun poolSize() = pool().size

    // ---------- Configuración ----------

    fun toggle(cat: String) {
        if (cat in enabled) enabled.remove(cat) else enabled.add(cat)
        save()
    }

    fun setVotingMode(v: Boolean) { voting = v; save() }

    fun addPlayer(raw: String): Boolean {
        val n = raw.trim().replace("\n", " ")
        if (n.isEmpty() || players.any { it.equals(n, ignoreCase = true) }) return false
        players.add(n); save(); return true
    }

    fun removePlayer(n: String) { players.remove(n); save() }

    fun isFav() = current?.id in favs

    fun toggleFav() {
        val id = current?.id ?: return
        if (id in favs) favs.remove(id) else favs.add(id)
        prefs.edit().putString("favs", favs.joinToString("|")).apply()
        if (onlyFavs && favs.isEmpty()) changeOnlyFavs(false)
    }

    fun changeOnlyFavs(v: Boolean) { onlyFavs = v; prefs.edit().putBoolean("onlyFavs", v).apply() }

    private fun save() {
        prefs.edit()
            .putString("enabled", enabled.joinToString("|"))
            .putBoolean("voting", voting)
            .putString("players", players.joinToString("\n"))
            .apply()
    }

    // ---------- Partida ----------

    fun canStart(m: Mode) = poolSize() > 0 && (m == Mode.VOTACION || players.size >= 3)

    fun startPlaying(m: Mode): Boolean {
        if (!canStart(m)) return false
        mode = m
        history.clear(); rounds.clear(); guessLog.clear(); chooserTurns.clear()
        scores.clear(); players.forEach { scores[it] = 0 }
        played = 0; current = null
        next()
        screen = Screen.Play
        return true
    }

    /** Siguiente pregunta: no repite hasta agotar las de las categorías elegidas. */
    fun next() {
        current?.let { history.add(it to swapped) }
        val pool = pool()
        if (pool.isEmpty()) return
        val seen = (prefs.getStringSet("seen", emptySet()) ?: emptySet()).toMutableSet()
        var fresh = pool.filter { it.id !in seen }
        if (fresh.isEmpty()) {
            seen.removeAll(pool.map { it.id }.toSet())
            fresh = pool.filter { it.id != current?.id }.ifEmpty { pool }
        }
        val q = fresh[rng.nextInt(fresh.size)]
        seen.add(q.id)
        prefs.edit().putStringSet("seen", seen).apply()
        current = q
        swapped = rng.nextBoolean()
        resetRound()
        played++
        if (mode == Mode.ADIVINA) {
            // Turnos parejos: elige quien menos veces ha elegido
            val min = players.minOf { chooserTurns[it] ?: 0 }
            val cands = players.filter { (chooserTurns[it] ?: 0) == min && it != chooser }.ifEmpty { players.filter { (chooserTurns[it] ?: 0) == min } }
            chooser = cands[rng.nextInt(cands.size)]
            chooserTurns[chooser] = (chooserTurns[chooser] ?: 0) + 1
            phase = Phase.PASS
        }
    }

    fun canGoBack() = mode == Mode.VOTACION && history.isNotEmpty()

    fun previous() {
        val (q, s) = history.removeLastOrNull() ?: return
        current = q; swapped = s; resetRound()
        played = maxOf(1, played - 1)
    }

    private fun resetRound() { anonA = 0; anonB = 0; votes.clear(); revealed = false; secret = null; prevHist = null }

    fun optionA() = current?.let { if (swapped) it.b else it.a } ?: ""
    fun optionB() = current?.let { if (swapped) it.a else it.b } ?: ""

    fun countA() = if (named && mode == Mode.VOTACION) votes.values.count { it == 'A' } else if (mode == Mode.ADIVINA) votes.values.count { it == 'A' } else anonA
    fun countB() = if (named && mode == Mode.VOTACION) votes.values.count { it == 'B' } else if (mode == Mode.ADIVINA) votes.values.count { it == 'B' } else anonB

    /** Toca el nombre: sin voto → A → B → sin voto. */
    fun cycleVote(p: String) {
        when (votes[p]) {
            null -> votes[p] = 'A'
            'A' -> votes[p] = 'B'
            else -> votes.remove(p)
        }
    }

    fun reveal() {
        if (revealed) return
        revealed = true
        if (named && votes.isNotEmpty()) rounds.add(votes.toMap())
        recordHistory(countA(), countB())
    }

    /** Guarda los votos con la orientación original de la pregunta (a/b), no la de pantalla. */
    private fun recordHistory(a: Int, b: Int) {
        val q = current ?: return
        val old = hist[q.id]
        prevHist = old?.let { if (swapped) it.second to it.first else it }
        val (oa, ob) = if (swapped) b to a else a to b
        hist[q.id] = ((old?.first ?: 0) + oa) to ((old?.second ?: 0) + ob)
        val o = JSONObject(); hist.forEach { (k, v) -> o.put(k, "${v.first},${v.second}") }
        prefs.edit().putString("hist", o.toString()).apply()
    }

    private fun loadHist(): MutableMap<String, Pair<Int, Int>> = runCatching {
        val o = JSONObject(prefs.getString("hist", "{}") ?: "{}")
        o.keys().asSequence().associateWith { k ->
            val (a, b) = o.getString(k).split(","); a.toInt() to b.toInt()
        }.toMutableMap()
    }.getOrElse { mutableMapOf() }

    // ---------- "¿Qué elegiría…?" ----------

    fun chooseSecret(c: Char) { secret = c; phase = Phase.GUESS }

    fun guessers() = players.filter { it != chooser }

    /** Revela la elección. Acierto = +1. Si nadie acierta, el que eligió gana +2. */
    fun revealGuess() {
        val s = secret ?: return
        val right = votes.filter { it.value == s }.keys
        right.forEach { scores[it] = (scores[it] ?: 0) + 1 }
        if (right.isEmpty() && votes.isNotEmpty()) scores[chooser] = (scores[chooser] ?: 0) + 2
        votes.forEach { (p, v) -> guessLog.add(Triple(p, chooser, v == s)) }
        phase = Phase.REVEAL
    }

    fun nobodyGuessed() = secret != null && votes.isNotEmpty() && votes.none { it.value == secret }

    // ---------- Estadísticas ----------

    fun roundsCount() = rounds.size

    /** % de veces que cada pareja votó lo mismo (mínimo 2 preguntas juntos). */
    fun affinity(): List<PairStat> {
        val ps = players.toList(); val out = mutableListOf<PairStat>()
        for (i in ps.indices) for (j in i + 1 until ps.size) {
            val both = rounds.filter { ps[i] in it && ps[j] in it }
            if (both.size < 2) continue
            val same = both.count { it[ps[i]] == it[ps[j]] }
            out.add(PairStat(ps[i], ps[j], Math.round(same * 100f / both.size), both.size))
        }
        return out
    }

    /** % de veces que cada jugador quedó en la minoría. */
    fun minority(): List<PlayerStat> = players.mapNotNull { p ->
        val mine = rounds.filter { p in it }
        if (mine.isEmpty()) return@mapNotNull null
        val inMin = mine.count { r ->
            val a = r.values.count { it == 'A' }; val b = r.values.count { it == 'B' }
            a != b && a > 0 && b > 0 && r[p] == (if (a < b) 'A' else 'B')
        }
        PlayerStat(p, Math.round(inMin * 100f / mine.size), mine.size)
    }

    /** Quién adivina mejor a quién (modo "¿Qué elegiría…?"). */
    fun bestReader(): List<PairStat> = guessLog.groupBy { it.first to it.second }
        .filter { it.value.size >= 2 }
        .map { (k, v) -> PairStat(k.first, k.second, Math.round(v.count { it.third } * 100f / v.size), v.size) }
        .sortedByDescending { it.pct }

    // ---------- Mis preguntas ----------

    fun addCustom(a: String, b: String): Boolean {
        val ta = a.trim(); val tb = b.trim()
        if (ta.isEmpty() || tb.isEmpty()) return false
        custom.add(Question("c" + System.currentTimeMillis(), MIAS, ta, tb))
        if (MIAS !in enabled) enabled.add(MIAS)
        saveCustom(); save()
        return true
    }

    fun removeCustom(q: Question) { custom.remove(q); saveCustom() }

    private fun saveCustom() {
        val arr = JSONArray()
        custom.forEach { arr.put(JSONObject().put("id", it.id).put("a", it.a).put("b", it.b)) }
        prefs.edit().putString("custom", arr.toString()).apply()
    }

    private fun loadCustom(): List<Question> = runCatching {
        val arr = JSONArray(prefs.getString("custom", "[]") ?: "[]")
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Question(o.getString("id"), MIAS, o.getString("a"), o.getString("b"))
        }
    }.getOrElse { emptyList() }

    companion object {
        private fun loadBuiltIn(context: Context): List<Question> {
            val text = context.assets.open("preguntas.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONArray(text)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Question("q$i", o.getString("categoria"), o.getString("a"), o.getString("b"))
            }
        }
    }
}
