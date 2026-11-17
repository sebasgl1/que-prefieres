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

/** Una pregunta con sus votos acumulados (en el orden original a/b). */
data class QuestionStat(val q: Question, val a: Int, val b: Int)

enum class Mode { VOTACION, ADIVINA }

/** Fases del modo "¿Qué elegiría…?". */
enum class Phase { PASS, SECRET, GUESS, REVEAL }

sealed class Screen {
    data object Home : Screen()
    data object Players : Screen()
    data object Categories : Screen()
    data object Custom : Screen()
    data object Insights : Screen()
    data object Play : Screen()
    data object Summary : Screen()
}

class Game(context: Context) {
    private val prefs = context.getSharedPreferences("queprefieres", Context.MODE_PRIVATE)
    private val rng = SecureRandom()
    private val builtIn: List<Question> = loadBuiltIn(context)

    val categories: List<String> = builtIn.map { it.categoria }.distinct() + MIAS
    val custom = mutableStateListOf<Question>().apply { addAll(loadCustom()) }
    val enabled = mutableStateListOf<String>().apply {
        val saved = prefs.getString("enabled", null)?.split("|")?.filter { it.isNotBlank() }
        val known = prefs.getString("knownCats", null)?.split("|")?.filter { it.isNotBlank() }
        addAll(Stats.enabledOnLoad(categories, saved, known))
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
    var avoidLopsided by mutableStateOf(prefs.getBoolean("avoidLopsided", true))
    var bigText by mutableStateOf(prefs.getBoolean("bigText", false))
    private val hist: MutableMap<String, Pair<Int, Int>> = loadHist()
    /** Lo que esta partida ya sumó al historial por pregunta, para no contarla dos veces. */
    private val sessionHist = mutableMapOf<String, Pair<Int, Int>>()
    /** Votos de partidas anteriores para la pregunta actual (en el orden A/B de la pantalla). */
    var prevHist by mutableStateOf<Pair<Int, Int>?>(null)

    var screen by mutableStateOf<Screen>(Screen.Home)
    var mode by mutableStateOf(Mode.VOTACION)
    var askQuit by mutableStateOf(false)

    // Pregunta actual
    var current by mutableStateOf<Question?>(null)
    var swapped by mutableStateOf(false)
    var revealed by mutableStateOf(false)
    var played by mutableIntStateOf(0)
    private val backStack = mutableListOf<Pair<Question, Boolean>>()

    // Votos: anónimos (sin jugadores) o con nombre (ocultos hasta revelar)
    var anonA by mutableIntStateOf(0)
    var anonB by mutableIntStateOf(0)
    val votes = mutableStateMapOf<String, Char>()
    private val rounds = LinkedHashMap<String, Map<String, Char>>()   // votos con nombre, por pregunta

    // Modo "¿Qué elegiría…?"
    var chooser by mutableStateOf("")
    var secret by mutableStateOf<Char?>(null)
    var phase by mutableStateOf(Phase.PASS)
    val scores = mutableStateMapOf<String, Int>()
    private val chooserTurns = mutableMapOf<String, Int>()
    private val guessLog = mutableListOf<Triple<String, String, Boolean>>() // adivino, elector, acertó

    init {
        migrateIds()
        restoreState()
    }

    /**
     * Antes los ids eran por posición en la librería ("q0", "q1"…): agregar o quitar preguntas
     * corría favoritas e historial a otras preguntas. Ahora son por texto; esto pasa los datos viejos.
     */
    private fun migrateIds() {
        if (prefs.getInt("idv", 1) >= 2) return
        val map = builtIn.mapIndexed { i, q -> "q$i" to q.id }.toMap()
        for (i in favs.indices) favs[i] = map[favs[i]] ?: favs[i]
        val newHist = hist.mapKeys { (k, _) -> map[k] ?: k }
        hist.clear(); hist.putAll(newHist)
        val e = prefs.edit()
        e.putString("favs", favs.joinToString("|"))
        val o = JSONObject()
        hist.forEach { (k, v) -> o.put(k, "${v.first},${v.second}") }
        e.putString("hist", o.toString())
        e.putStringSet("seen", Stats.migrate(prefs.getStringSet("seen", emptySet()) ?: emptySet(), map).toSet())
        e.remove("state")   // una partida a medias con ids viejos no se retoma
        e.putInt("idv", 2).apply()
    }

    val named get() = players.size >= 2
    private val namedVotes get() = mode == Mode.ADIVINA || named

    fun all(): List<Question> = builtIn + custom
    fun countIn(cat: String) = all().count { it.categoria == cat }

    private fun isLopsided(id: String) = hist[id]?.let { Stats.lopsided(it.first, it.second) } ?: false

    private fun pool(): List<Question> {
        val base = if (onlyFavs) all().filter { it.id in favs } else all().filter { it.categoria in enabled }
        if (!avoidLopsided || onlyFavs) return base
        return base.filter { !isLopsided(it.id) }.ifEmpty { base }
    }

    fun poolSize() = pool().size
    fun lopsidedCount() = all().count { isLopsided(it.id) }

    // ---------- Configuración ----------

    fun toggle(cat: String) {
        if (cat in enabled) enabled.remove(cat) else enabled.add(cat)
        save()
    }

    fun setVotingMode(v: Boolean) { voting = v; save() }

    fun changeAvoidLopsided(v: Boolean) { avoidLopsided = v; save() }

    fun changeBigText(v: Boolean) { bigText = v; save() }

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
            .putString("knownCats", categories.joinToString("|"))
            .putBoolean("voting", voting)
            .putBoolean("avoidLopsided", avoidLopsided)
            .putBoolean("bigText", bigText)
            .putString("players", players.joinToString("\n"))
            .apply()
    }

    // ---------- Partida ----------

    fun canStart(m: Mode) = poolSize() > 0 && (m == Mode.VOTACION || players.size >= 3)

    fun startPlaying(m: Mode): Boolean {
        if (!canStart(m)) return false
        mode = m
        backStack.clear(); rounds.clear(); guessLog.clear(); chooserTurns.clear(); sessionHist.clear()
        scores.clear(); players.forEach { scores[it] = 0 }
        played = 0; current = null; chooser = ""
        next()
        screen = Screen.Play
        return true
    }

    /** Sale de la partida y descarta sus puntajes y estadísticas. */
    fun quit() {
        askQuit = false
        screen = Screen.Home
    }

    /** Siguiente pregunta: no repite hasta agotar las de las categorías elegidas. */
    fun next() {
        current?.let { backStack.add(it to swapped) }
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
            chooser = Stats.nextChooser(players.toList(), chooserTurns, chooser, rng)
            chooserTurns[chooser] = (chooserTurns[chooser] ?: 0) + 1
            phase = Phase.PASS
        }
    }

    fun canGoBack() = mode == Mode.VOTACION && backStack.isNotEmpty()

    fun previous() {
        val (q, s) = backStack.removeLastOrNull() ?: return
        current = q; swapped = s; resetRound()
        played = maxOf(1, played - 1)
    }

    private fun resetRound() { anonA = 0; anonB = 0; votes.clear(); revealed = false; secret = null; prevHist = null }

    fun optionA() = current?.let { if (swapped) it.b else it.a } ?: ""
    fun optionB() = current?.let { if (swapped) it.a else it.b } ?: ""

    fun countA() = if (namedVotes) votes.values.count { it == 'A' } else anonA
    fun countB() = if (namedVotes) votes.values.count { it == 'B' } else anonB

    fun vote(p: String, side: Char) { votes[p] = side }
    fun unvote(p: String) { votes.remove(p) }

    fun reveal() {
        if (revealed) return
        val q = current ?: return
        revealed = true
        if (named && votes.isNotEmpty()) rounds[q.id] = votes.toMap()   // por pregunta: volver a votarla reemplaza
        recordHistory(q, countA(), countB())
    }

    /** Guarda los votos con la orientación original de la pregunta (a/b), no la de pantalla. */
    private fun recordHistory(q: Question, a: Int, b: Int) {
        val now = if (swapped) b to a else a to b
        val total = hist[q.id]
        val before = sessionHist[q.id]
        val base = Stats.mergeHistory(total, before, 0 to 0)
        prevHist = if (base.first + base.second > 0) (if (swapped) base.second to base.first else base) else null
        hist[q.id] = Stats.mergeHistory(total, before, now)
        sessionHist[q.id] = now
        val o = JSONObject()
        hist.forEach { (k, v) -> o.put(k, "${v.first},${v.second}") }
        prefs.edit().putString("hist", o.toString()).apply()
    }

    private fun loadHist(): MutableMap<String, Pair<Int, Int>> = try {
        val o = JSONObject(prefs.getString("hist", "{}") ?: "{}")
        val m = mutableMapOf<String, Pair<Int, Int>>()
        for (k in o.keys()) {
            val parts = o.getString(k).split(",")
            m[k] = parts[0].toInt() to parts[1].toInt()
        }
        m
    } catch (e: Exception) {
        mutableMapOf()
    }

    // ---------- "¿Qué elegiría…?" ----------

    fun chooseSecret(c: Char) { secret = c; phase = Phase.GUESS }

    fun guessers() = players.filter { it != chooser }

    /** Revela la elección. Acierto = +1. Si nadie acierta, el que eligió gana +2. */
    fun revealGuess() {
        val s = secret ?: return
        if (phase != Phase.GUESS) return
        Stats.guessPoints(votes.toMap(), s, chooser).forEach { (p, pts) -> scores[p] = (scores[p] ?: 0) + pts }
        votes.forEach { (p, v) -> guessLog.add(Triple(p, chooser, v == s)) }
        phase = Phase.REVEAL
    }

    fun nobodyGuessed() = secret != null && votes.isNotEmpty() && votes.none { it.value == secret }

    // ---------- Estadísticas ----------

    fun roundsCount() = rounds.size

    fun affinity(): List<PairStat> = Stats.affinity(players.toList(), rounds.values)

    fun minority(): List<PlayerStat> = Stats.minority(players.toList(), rounds.values)

    /** Quién adivina mejor a quién (modo "¿Qué elegiría…?"). */
    fun bestReader(): List<PairStat> = guessLog.groupBy { it.first to it.second }
        .filter { it.value.size >= 2 }
        .map { (k, v) -> PairStat(k.first, k.second, Math.round(v.count { it.third } * 100f / v.size), v.size) }
        .sortedByDescending { it.pct }

    private fun withVotes(min: Int): List<QuestionStat> = all().mapNotNull { q ->
        hist[q.id]?.let { QuestionStat(q, it.first, it.second) }
    }.filter { it.a + it.b >= min }

    /** Las preguntas que más dividen al parche (mínimo 4 votos). */
    fun closest(): List<QuestionStat> = withVotes(4).sortedBy { Stats.gap(it.a, it.b) }.take(15)

    /** Las preguntas donde casi todos eligen lo mismo. */
    fun mostLopsided(): List<QuestionStat> =
        withVotes(8).filter { Stats.lopsided(it.a, it.b) }.sortedByDescending { it.a + it.b }.take(15)

    // ---------- Mis preguntas ----------

    fun addCustom(a: String, b: String): Boolean {
        val ta = a.trim(); val tb = b.trim()
        if (ta.isEmpty() || tb.isEmpty()) return false
        custom.add(Question("c" + System.currentTimeMillis(), MIAS, ta, tb))
        if (MIAS !in enabled) enabled.add(MIAS)
        saveCustom(); save()
        return true
    }

    fun removeCustom(q: Question) {
        custom.remove(q)
        saveCustom()
        if (favs.remove(q.id)) {
            prefs.edit().putString("favs", favs.joinToString("|")).apply()
            if (onlyFavs && favs.isEmpty()) changeOnlyFavs(false)
        }
    }

    private fun saveCustom() {
        val arr = JSONArray()
        custom.forEach { arr.put(JSONObject().put("id", it.id).put("a", it.a).put("b", it.b)) }
        prefs.edit().putString("custom", arr.toString()).apply()
    }

    private fun loadCustom(): List<Question> = try {
        val arr = JSONArray(prefs.getString("custom", "[]") ?: "[]")
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Question(o.getString("id"), MIAS, o.getString("a"), o.getString("b"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    // ---------- Guardado de la partida ----------

    /** Guarda la partida en curso para retomarla si Android cierra la app. */
    fun saveState() {
        val q = current
        if ((screen != Screen.Play && screen != Screen.Summary) || q == null) {
            prefs.edit().remove("state").apply()
            return
        }
        val o = JSONObject()
        o.put("t", System.currentTimeMillis())
        o.put("mode", mode.name).put("qid", q.id).put("swapped", swapped).put("revealed", revealed).put("played", played)
        o.put("anonA", anonA).put("anonB", anonB)
        o.put("votes", votesJson(votes))
        val rs = JSONObject()
        rounds.forEach { (k, v) -> rs.put(k, votesJson(v)) }
        o.put("rounds", rs)
        o.put("chooser", chooser).put("secret", secret?.toString() ?: "").put("phase", phase.name)
        val sc = JSONObject()
        scores.forEach { (k, v) -> sc.put(k, v) }
        o.put("scores", sc)
        val tu = JSONObject()
        chooserTurns.forEach { (k, v) -> tu.put(k, v) }
        o.put("turns", tu)
        val gl = JSONArray()
        guessLog.forEach { (g, c, ok) -> gl.put(JSONArray().put(g).put(c).put(ok)) }
        o.put("guess", gl)
        prefs.edit().putString("state", o.toString()).apply()
    }

    private fun votesJson(m: Map<String, Char>): JSONObject {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v.toString()) }
        return o
    }

    private fun votesFrom(o: JSONObject): Map<String, Char> {
        val m = LinkedHashMap<String, Char>()
        for (k in o.keys()) {
            val v = o.getString(k)
            if (v == "A" || v == "B") m[k] = v[0]
        }
        return m
    }

    private fun restoreState() {
        val raw = prefs.getString("state", null) ?: return
        try {
            val o = JSONObject(raw)
            // Una partida abandonada hace más de 6 horas no se retoma
            if (System.currentTimeMillis() - o.optLong("t", 0L) > RESUME_MS) {
                prefs.edit().remove("state").apply()
                return
            }
            val m = Mode.valueOf(o.getString("mode"))
            val q = all().firstOrNull { it.id == o.getString("qid") }
            if (q == null || (m == Mode.ADIVINA && players.size < 3)) {
                prefs.edit().remove("state").apply()
                return
            }
            mode = m
            current = q
            swapped = o.getBoolean("swapped")
            revealed = o.getBoolean("revealed")
            played = o.getInt("played")
            anonA = o.getInt("anonA"); anonB = o.getInt("anonB")
            votes.putAll(votesFrom(o.getJSONObject("votes")))
            val rs = o.getJSONObject("rounds")
            for (k in rs.keys()) rounds[k] = votesFrom(rs.getJSONObject(k))
            val sc = o.getJSONObject("scores")
            for (k in sc.keys()) scores[k] = sc.getInt(k)
            players.forEach { if (it !in scores) scores[it] = 0 }
            val tu = o.getJSONObject("turns")
            for (k in tu.keys()) chooserTurns[k] = tu.getInt(k)
            val gl = o.getJSONArray("guess")
            for (i in 0 until gl.length()) {
                val g = gl.getJSONArray(i)
                guessLog.add(Triple(g.getString(0), g.getString(1), g.getBoolean(2)))
            }
            if (m == Mode.ADIVINA) {
                val ch = o.getString("chooser")
                chooser = if (ch in players) ch else players.first()
                val sec = o.getString("secret")
                secret = if (sec == "A" || sec == "B") sec[0] else null
                val ph = Phase.valueOf(o.getString("phase"))
                // Si estaba eligiendo en secreto (o falta el dato), se vuelve a pasar el celular
                phase = if (ph == Phase.SECRET || secret == null) Phase.PASS else ph
                if (phase == Phase.PASS) { secret = null; votes.clear() }
            }
            screen = Screen.Play
        } catch (e: Exception) {
            prefs.edit().remove("state").apply()
        }
    }

    companion object {
        const val RESUME_MS = 6 * 60 * 60 * 1000L

        private fun loadBuiltIn(context: Context): List<Question> {
            val text = context.assets.open("preguntas.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONArray(text)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Question(Stats.questionId(o.getString("categoria"), o.getString("a"), o.getString("b")), o.getString("categoria"), o.getString("a"), o.getString("b"))
            }
        }
    }
}
