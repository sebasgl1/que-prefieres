package io.github.sebasgl1.queprefieres

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { QueTheme { App() } }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val game = remember { Game(ctx.applicationContext) }
    BackHandler(enabled = game.screen != Screen.Home) { game.screen = Screen.Home }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        AnimatedContent(targetState = game.screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "s") { s ->
            when (s) {
                Screen.Home -> HomeScreen(game)
                Screen.Categories -> CategoriesScreen(game)
                Screen.Custom -> CustomScreen(game)
                Screen.Play -> PlayScreen(game)
            }
        }
    }
}

// ---------- Comunes ----------

@Composable
fun BigButton(text: String, enabled: Boolean = true, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.height(58.dp), shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Ink),
    ) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier.fillMaxWidth(), enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.height(52.dp), shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) { Text(text, fontSize = 16.sp, color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline) }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start, size: Int = 15) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = size.sp, textAlign = align, modifier = modifier)
}

@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("←", fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground) }
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------- Inicio ----------

@Composable
fun HomeScreen(game: Game) {
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(64.dp).background(ColorA, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Text("A", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Ink) }
                Box(Modifier.size(64.dp).background(ColorB, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Text("B", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Ink) }
            }
            Spacer(Modifier.height(18.dp))
            Text("¿Qué prefieres?", fontSize = 40.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Muted("${game.poolSize()} preguntas en ${game.enabled.size} categorías", align = TextAlign.Center)
        }
        BigButton("Jugar", enabled = game.poolSize() > 0) { game.startPlaying() }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Categorías", Modifier.weight(1f)) { game.screen = Screen.Categories }
            GhostButton("Mis preguntas", Modifier.weight(1f)) { game.screen = Screen.Custom }
        }
    }
}

// ---------- Categorías ----------

@Composable
fun CategoriesScreen(game: Game) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Categorías") { game.screen = Screen.Home }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp)) {
            items(game.categories) { c ->
                val on = c in game.enabled
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface).clickable { game.toggle(c) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(CATEGORY_EMOJI[c] ?: "❓", fontSize = 24.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c, fontSize = 17.sp)
                        Muted("${game.countIn(c)} preguntas" + if (c == "Picante") " · subidas de tono" else "", size = 13)
                    }
                    Switch(checked = on, onCheckedChange = { game.toggle(c) })
                }
            }
            item {
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Votación del grupo", fontSize = 17.sp)
                        Muted("Contar votos y ver porcentajes", size = 13)
                    }
                    Switch(checked = game.voting, onCheckedChange = { game.setVotingMode(it) })
                }
            }
        }
    }
}

// ---------- Mis preguntas ----------

@Composable
fun CustomScreen(game: Game) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        TopBar("Mis preguntas") { game.screen = Screen.Home }
        Column(Modifier.padding(horizontal = 22.dp)) {
            Muted("¿Qué prefieres…")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                a, { a = it.take(120) }, placeholder = { Text("Opción A") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Text("o", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), textAlign = TextAlign.Center)
            OutlinedTextField(
                b, { b = it.take(120) }, placeholder = { Text("Opción B") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Spacer(Modifier.height(10.dp))
            BigButton("Agregar", enabled = a.isNotBlank() && b.isNotBlank()) { if (game.addCustom(a, b)) { a = ""; b = "" } }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp)) {
            if (game.custom.isEmpty()) item { Muted("Todavía no has agregado preguntas.", Modifier.padding(top = 10.dp)) }
            items(game.custom.reversed(), key = { it.id }) { q ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                        .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(q.a, color = ColorA, fontSize = 15.sp)
                        Text(q.b, color = ColorB, fontSize = 15.sp)
                    }
                    TextButton(onClick = { game.removeCustom(q) }) { Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

// ---------- Juego ----------

@Composable
fun PlayScreen(game: Game) {
    val q = game.current ?: return
    val total = game.votesA + game.votesB
    val showResult = game.voting && game.revealed
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { game.screen = Screen.Home }) { Text("✕", fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground) }
            Spacer(Modifier.weight(1f))
            Box(Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(99.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("${CATEGORY_EMOJI[q.categoria] ?: ""} ${q.categoria}", fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            Muted("#${game.played}", Modifier.padding(end = 12.dp))
        }
        Text("¿Qué prefieres?", fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center)

        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxSize()) {
                OptionCard(game.optionA(), "A", ColorA, game.votesA, total, game.voting, showResult, Modifier.weight(1f),
                    onVote = { game.votesA++ }, onUnvote = { if (game.votesA > 0) game.votesA-- })
                Spacer(Modifier.height(10.dp))
                OptionCard(game.optionB(), "B", ColorB, game.votesB, total, game.voting, showResult, Modifier.weight(1f),
                    onVote = { game.votesB++ }, onUnvote = { if (game.votesB > 0) game.votesB-- })
            }
            Box(
                Modifier.size(50.dp).background(MaterialTheme.colorScheme.background, CircleShape).padding(5.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("o", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }

        Spacer(Modifier.height(10.dp))
        if (showResult) {
            val msg = when {
                game.votesA == game.votesB -> "¡Empate! Que alguien de cada lado defienda su opción 🗣️"
                game.votesA == 0 || game.votesB == 0 -> "¡Todos de acuerdo! 🤝"
                else -> "La minoría (${if (game.votesA < game.votesB) "A" else "B"}) tiene que defender su opción 🗣️"
            }
            Muted(msg, Modifier.fillMaxWidth().padding(bottom = 10.dp), align = TextAlign.Center)
        } else if (game.voting) {
            Muted(if (total == 0) "Toquen la opción que cada uno prefiere" else "$total ${if (total == 1) "voto" else "votos"}", Modifier.fillMaxWidth().padding(bottom = 10.dp), align = TextAlign.Center)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (game.voting && !game.revealed) {
                GhostButton("Saltar", Modifier.weight(1f)) { game.next() }
                BigButton("Ver resultado", enabled = total > 0, modifier = Modifier.weight(1.4f)) { game.revealed = true }
            } else {
                GhostButton("Anterior", Modifier.weight(1f), enabled = game.canGoBack()) { game.previous() }
                BigButton("Siguiente", modifier = Modifier.weight(1.4f)) { game.next() }
            }
        }
    }
}

@Composable
fun OptionCard(
    text: String, letter: String, color: Color, votes: Int, total: Int,
    voting: Boolean, showResult: Boolean, modifier: Modifier,
    onVote: () -> Unit, onUnvote: () -> Unit,
) {
    val pct = if (total > 0) votes.toFloat() / total else 0f
    val fill by animateFloatAsState(if (showResult) pct else 0f, tween(700), label = "fill")
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(if (showResult) MaterialTheme.colorScheme.surface else color)
            .clickable(enabled = voting && !showResult, onClick = onVote),
    ) {
        if (showResult) Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(color.copy(alpha = 0.85f)))
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text, fontSize = if (text.length > 60) 22.sp else 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                color = if (showResult) MaterialTheme.colorScheme.onBackground else Ink, lineHeight = 30.sp,
            )
            if (showResult) {
                Spacer(Modifier.height(8.dp))
                Text("${Math.round(pct * 100)}%", fontSize = 34.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
                Muted("$votes ${if (votes == 1) "voto" else "votos"}")
            }
        }
        Text(letter, fontSize = 15.sp, fontWeight = FontWeight.Black, color = if (showResult) color else Ink.copy(alpha = 0.6f), modifier = Modifier.padding(14.dp))
        if (voting && !showResult && votes > 0) {
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onUnvote) { Text("−1", color = Ink.copy(alpha = 0.7f), fontWeight = FontWeight.Bold) }
                Box(Modifier.size(38.dp).background(Ink, CircleShape), contentAlignment = Alignment.Center) {
                    Text("$votes", color = color, fontWeight = FontWeight.Black, fontSize = 17.sp)
                }
            }
        }
    }
}
