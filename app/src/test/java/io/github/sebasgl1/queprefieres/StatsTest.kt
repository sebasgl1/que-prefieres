package io.github.sebasgl1.queprefieres

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class StatsTest {
    private val players = listOf("Ana", "Beto", "Caro")
    private val rounds = listOf(
        mapOf("Ana" to 'A', "Beto" to 'A', "Caro" to 'B'),
        mapOf("Ana" to 'B', "Beto" to 'B', "Caro" to 'A'),
        mapOf("Ana" to 'A', "Beto" to 'B', "Caro" to 'A'),
        mapOf("Ana" to 'A', "Beto" to 'A', "Caro" to 'A'),
    )

    @Test fun afinidadPorPareja() {
        val aff = Stats.affinity(players, rounds).associate { (it.p1 to it.p2) to it.pct }
        assertEquals(75, aff["Ana" to "Beto"])
        assertEquals(50, aff["Ana" to "Caro"])
        assertEquals(25, aff["Beto" to "Caro"])
    }

    @Test fun afinidadNecesitaDosPreguntasJuntos() {
        assertTrue(Stats.affinity(players, rounds.take(1)).isEmpty())
    }

    @Test fun quienQuedaEnMinoria() {
        val min = Stats.minority(players, rounds).associate { it.name to it.pct }
        assertEquals(0, min["Ana"])
        assertEquals(25, min["Beto"])
        assertEquals(50, min["Caro"])
    }

    @Test fun empateNoTieneMinoria() {
        val r = listOf(mapOf("Ana" to 'A', "Beto" to 'B'))
        Stats.minority(listOf("Ana", "Beto"), r).forEach { assertEquals(0, it.pct) }
    }

    @Test fun preguntasDisparejas() {
        assertTrue(Stats.lopsided(9, 1))
        assertTrue(Stats.lopsided(0, 8))
        assertFalse(Stats.lopsided(7, 0))
        assertFalse(Stats.lopsided(8, 2))
        assertEquals(0, Stats.gap(5, 5))
        assertEquals(100, Stats.gap(6, 0))
        assertEquals(50, Stats.gap(6, 2))
    }

    @Test fun turnosParejos() {
        val rng = Random(5)
        val turns = mutableMapOf<String, Int>()
        var last = ""
        repeat(30) {
            val c = Stats.nextChooser(players, turns, last, rng)
            assertTrue(c != last)
            turns[c] = (turns[c] ?: 0) + 1
            last = c
        }
        players.forEach { assertEquals(10, turns[it]) }
    }

    @Test fun puntosDeAdivinar() {
        assertEquals(mapOf("Beto" to 1), Stats.guessPoints(mapOf("Beto" to 'A', "Caro" to 'B'), 'A', "Ana"))
        assertEquals(mapOf("Ana" to 2), Stats.guessPoints(mapOf("Beto" to 'B', "Caro" to 'B'), 'A', "Ana"))
        assertTrue(Stats.guessPoints(emptyMap(), 'A', "Ana").isEmpty())
    }

    @Test fun historialNoCuentaDosVeces() {
        // Primera vez en la partida: se suma
        val first = Stats.mergeHistory(10 to 5, null, 3 to 1)
        assertEquals(13 to 6, first)
        // Vuelven a la misma pregunta y votan distinto: reemplaza lo de esta partida
        assertEquals(11 to 8, Stats.mergeHistory(first, 3 to 1, 1 to 3))
        // Sin historial previo
        assertEquals(2 to 2, Stats.mergeHistory(null, null, 2 to 2))
    }

    @Test fun categoriasNuevasSeActivanSolas() {
        val all = listOf("Divertidas", "Comida", "Viajes", "Picante")
        // Primera vez: todo menos Picante
        assertEquals(listOf("Divertidas", "Comida", "Viajes"), Stats.enabledOnLoad(all, null, null))
        // El usuario apagó Comida; llega "Viajes" en una actualización: se activa, Comida sigue apagada
        assertEquals(listOf("Divertidas", "Viajes"), Stats.enabledOnLoad(all, listOf("Divertidas"), listOf("Divertidas", "Comida", "Picante")))
        // Si la apagó después de conocerla, se respeta
        assertEquals(listOf("Divertidas"), Stats.enabledOnLoad(all, listOf("Divertidas"), all))
    }

    @Test fun idsEstablesYMigracion() {
        val id = Stats.questionId("Comida", "Pizza", "Hamburguesa")
        assertEquals(id, Stats.questionId("Comida", "Pizza", "Hamburguesa"))
        assertTrue(id != Stats.questionId("Comida", "Hamburguesa", "Pizza"))
        assertEquals(listOf("kNuevo", "c123"), Stats.migrate(listOf("q0", "c123"), mapOf("q0" to "kNuevo")))
    }
}
