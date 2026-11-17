package io.github.sebasgl1.queprefieres

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsEnabled
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pruebas de pantalla: juegan rondas completas tocando la app como lo haría una persona. */
@RunWith(AndroidJUnit4::class)
class FlowTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Before fun clean() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("queprefieres", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private val pickTag = SemanticsMatcher("toca un nombre") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("pick-") == true
    }

    private fun addPlayers(vararg names: String) {
        rule.onNodeWithText("Jugadores").performClick()
        names.forEach { n ->
            rule.onNode(hasSetTextAction()).performTextInput(n)
            rule.onNodeWithText("Agregar").performClick()
        }
        rule.onNodeWithText("←").performClick()
    }

    @Test fun votacionAnonimaConPorcentajes() {
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.onNodeWithText("Votación").performClick()
            rule.onNodeWithTag("card-A").performClick()
            rule.onNodeWithTag("card-A").performClick()
            rule.onNodeWithTag("card-B").performClick()
            rule.onNodeWithText("3 votos").assertExists()
            rule.onNodeWithText("Ver resultado").performClick()
            rule.onNodeWithText("67%").assertExists()
            rule.onNodeWithText("33%").assertExists()
            rule.onNodeWithText("Siguiente").performClick()
            rule.onNodeWithText("Anterior").assertIsEnabled()
        }
    }

    @Test fun votacionConNombresOcultaLosVotos() {
        ActivityScenario.launch(MainActivity::class.java).use {
            addPlayers("Ana", "Beto", "Caro")
            rule.onNodeWithText("Votación").performClick()
            rule.onNodeWithTag("card-A").performClick()
            rule.onNodeWithTag("pick-Ana").performClick()
            rule.onNodeWithTag("card-B").performClick()
            rule.onNodeWithTag("pick-Beto").performClick()
            // Antes del resultado solo se ve quién votó, no qué eligió
            rule.onNodeWithText("Ana ✓").assertExists()
            rule.onNodeWithText("Ana · A").assertDoesNotExist()
            rule.onNodeWithText("2 de 3 votaron", substring = true).assertExists()
            rule.onNodeWithText("Ver resultado").performClick()
            rule.onNodeWithText("Ana · A").assertExists()
            rule.onNodeWithText("Beto · B").assertExists()
            rule.onNodeWithText("Fin").performClick()
            rule.onNodeWithText("Resumen").assertExists()
        }
    }

    @Test fun queElegiriaRondaCompleta() {
        ActivityScenario.launch(MainActivity::class.java).use {
            addPlayers("Ana", "Beto", "Caro")
            rule.onNodeWithText("¿Qué elegiría…?").performClick()
            rule.onNode(hasText("Soy ", substring = true)).performClick()
            rule.onNodeWithTag("card-A").performClick()          // elige en secreto
            rule.onNodeWithTag("card-B").performClick()          // alguien adivina B
            rule.onAllNodes(pickTag).onFirst().performClick()
            rule.onNodeWithText("Revelar").performClick()
            rule.onNode(hasText(" eligió A", substring = true)).assertExists()
            rule.onNodeWithText("Siguiente").performClick()
            rule.onNode(hasText("Soy ", substring = true)).assertExists()
        }
    }

    @Test fun atrasPideConfirmacionYFavoritas() {
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            rule.onNodeWithText("Votación").performClick()
            rule.onNodeWithText("☆").performClick()
            rule.onNodeWithText("⭐").assertExists()
            sc.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            rule.onNodeWithText("¿Salir de la partida?").assertExists()
            rule.onNodeWithText("Salir").performClick()
            rule.onNodeWithText("Votación").assertExists()
        }
    }

    @Test fun categoriasYLetraGrande() {
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.onNodeWithText("Categorías").performClick()
            rule.onNodeWithText("Solo favoritas").assertExists()
            rule.onNodeWithTag("cats-list").performScrollToNode(hasTestTag("big-text"))
            rule.onNodeWithTag("big-text").performClick()
            rule.onNodeWithText("←").performClick()
            rule.onNodeWithText("¿Qué prefieres?").assertExists()
        }
    }
}
