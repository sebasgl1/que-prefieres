package io.github.sebasgl1.queprefieres

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Revisa la librería de preguntas: sin vacíos ni opciones repetidas. */
class QuestionsTest {
    private val qs = JSONArray(File("src/main/assets/preguntas.json").readText(Charsets.UTF_8))

    @Test fun hayPreguntasEnTodasLasCategorias() {
        val cats = (0 until qs.length()).map { qs.getJSONObject(it).getString("categoria") }.groupingBy { it }.eachCount()
        assertTrue(cats.size >= 9)
        cats.forEach { (c, n) -> assertTrue("$c tiene $n", n >= 20) }
        assertTrue(CATEGORY_EMOJI.keys.containsAll(cats.keys))
    }

    @Test fun ningunaOpcionSeRepite() {
        val all = mutableListOf<String>()
        for (i in 0 until qs.length()) {
            val o = qs.getJSONObject(i)
            val a = o.getString("a").trim(); val b = o.getString("b").trim()
            assertTrue("pregunta $i vacía", a.isNotEmpty() && b.isNotEmpty())
            assertTrue("pregunta $i con opciones iguales", !a.equals(b, ignoreCase = true))
            all.add(a.lowercase()); all.add(b.lowercase())
        }
        val ids = (0 until qs.length()).map { qs.getJSONObject(it).let { o -> Stats.questionId(o.getString("categoria"), o.getString("a"), o.getString("b")) } }
        assertEquals("ids repetidos", ids.size, ids.toSet().size)
        val dup = all.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        assertEquals("opciones repetidas: $dup", 0, dup.size)
    }
}
