package io.github.sebasgl1.queprefieres

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

data class Question(val id: String, val categoria: String, val a: String, val b: String)

sealed class Screen {
    data object Home : Screen()
    data object Categories : Screen()
    data object Custom : Screen()
    data object Play : Screen()
}

const val MIAS = "Mis preguntas"

val CATEGORY_EMOJI = mapOf(
    "Divertidas" to "😂", "Difíciles" to "🤯", "Asquerosas" to "🤢", "Superpoderes" to "🦸",
    "Plata y trabajo" to "💸", "Comida" to "🍕", "Tecnología" to "📱", "Colombianas" to "🇨🇴",
    "Picante" to "🌶️", MIAS to "✍️",
)

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

    var screen by mutableStateOf<Screen>(Screen.Home)

    // Ronda actual
    var current by mutableStateOf<Question?>(null)
    var swapped by mutableStateOf(false)
    var votesA by mutableIntStateOf(0)
    var votesB by mutableIntStateOf(0)
    var revealed by mutableStateOf(false)
    var played by mutableIntStateOf(0)
    private val history = mutableListOf<Pair<Question, Boolean>>()

    fun all(): List<Question> = builtIn + custom
    fun countIn(cat: String) = all().count { it.categoria == cat }
    private fun pool() = all().filter { it.categoria in enabled }
    fun poolSize() = pool().size

    fun toggle(cat: String) {
        if (cat in enabled) enabled.remove(cat) else enabled.add(cat)
        save()
    }

    fun setVotingMode(v: Boolean) { voting = v; save() }

    private fun save() {
        prefs.edit().putString("enabled", enabled.joinToString("|")).putBoolean("voting", voting).apply()
    }

    fun startPlaying(): Boolean {
        if (poolSize() == 0) return false
        history.clear(); played = 0
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
        resetVotes()
        played++
    }

    fun canGoBack() = history.isNotEmpty()

    fun previous() {
        val (q, s) = history.removeLastOrNull() ?: return
        current = q; swapped = s; resetVotes()
        played = maxOf(1, played - 1)
    }

    private fun resetVotes() { votesA = 0; votesB = 0; revealed = false }

    fun optionA() = current?.let { if (swapped) it.b else it.a } ?: ""
    fun optionB() = current?.let { if (swapped) it.a else it.b } ?: ""

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
