package io.github.sebasgl1.queprefieres

import android.app.Activity
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private lateinit var game: Game

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        game = Game(applicationContext)
        setContent { QueTheme { App(game) } }
    }

    // Guarda la partida por si Android cierra la app en segundo plano
    override fun onStop() {
        super.onStop()
        game.saveState()
    }
}

/** Durante la partida: oculta los botones de Android y mantiene la pantalla encendida. */
@Composable
fun GameMode(active: Boolean) {
    val view = LocalView.current
    LaunchedEffect(active) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val bars = WindowCompat.getInsetsController(window, view)
        if (active) {
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            bars.show(WindowInsetsCompat.Type.systemBars())
        }
        view.keepScreenOn = active
    }
}

@Composable
fun App(game: Game) {
    GameMode(active = game.screen == Screen.Play || game.screen == Screen.Summary)
    BackHandler(enabled = game.screen != Screen.Home) {
        when (game.screen) {
            Screen.Play -> game.askQuit = true
            Screen.Summary -> game.screen = Screen.Play
            Screen.Insights -> game.screen = Screen.Categories
            else -> game.screen = Screen.Home
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        AnimatedContent(targetState = game.screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "s") { s ->
            when (s) {
                Screen.Home -> HomeScreen(game)
                Screen.Categories -> CategoriesScreen(game)
                Screen.Custom -> CustomScreen(game)
                Screen.Insights -> InsightsScreen(game)
                Screen.Play -> if (game.mode == Mode.ADIVINA) GuessScreen(game) else PlayScreen(game)
                Screen.Players -> PlayersScreen(game)
                Screen.Summary -> SummaryScreen(game)
            }
        }
    }
    if (game.askQuit) {
        AlertDialog(
            onDismissRequest = { game.askQuit = false },
            title = { Text("¿Salir de la partida?") },
            text = { Text("Se pierden los puntajes y estadísticas de esta partida. Con \"Fin\" puedes ver el resumen sin salir.") },
            confirmButton = { TextButton(onClick = { game.quit() }) { Text("Salir") } },
            dismissButton = { TextButton(onClick = { game.askQuit = false }) { Text("Seguir jugando") } },
        )
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
                Box(Modifier.size(60.dp).background(ColorA, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Text("A", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Ink) }
                Box(Modifier.size(60.dp).background(ColorB, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Text("B", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Ink) }
            }
            Spacer(Modifier.height(16.dp))
            Text("¿Qué prefieres?", fontSize = 38.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Muted("${game.poolSize()} ${if (game.onlyFavs) "favoritas" else "preguntas"} · ${if (game.players.isEmpty()) "sin jugadores" else "${game.players.size} jugadores"}", align = TextAlign.Center)
        }
        ModeCard("🗳️", "Votación", "Todos votan y se ven los porcentajes" + if (game.named) ". Con nombres y estadísticas al final." else ".", ColorA, game.canStart(Mode.VOTACION)) {
            game.startPlaying(Mode.VOTACION)
        }
        Spacer(Modifier.height(10.dp))
        ModeCard("🔮", "¿Qué elegiría…?", if (game.players.size >= 3) "Uno elige en secreto y los demás adivinan. Gana quien más acierte." else "Agrega mínimo 3 jugadores para este modo.", ColorB, game.canStart(Mode.ADIVINA)) {
            game.startPlaying(Mode.ADIVINA)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Jugadores", Modifier.weight(1f)) { game.screen = Screen.Players }
            GhostButton("Categorías", Modifier.weight(1f)) { game.screen = Screen.Categories }
            GhostButton("Mías", Modifier.weight(0.8f)) { game.screen = Screen.Custom }
        }
    }
}

@Composable
fun ModeCard(emoji: String, title: String, desc: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).background(if (enabled) color else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 24.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant)
            Muted(desc, size = 13)
        }
        Text("▶", color = if (enabled) color else MaterialTheme.colorScheme.outline, fontSize = 18.sp)
    }
}

// ---------- Jugadores ----------

@Composable
fun PlayersScreen(game: Game) {
    var input by remember { mutableStateOf("") }
    val add = { if (game.addPlayer(input)) input = "" }
    Column(Modifier.fillMaxSize()) {
        TopBar("Jugadores") { game.screen = Screen.Home }
        Column(Modifier.padding(horizontal = 22.dp)) {
            Muted("Con 2 o más, la votación es con nombres y al final ves quién piensa parecido. Con 3 o más se activa \"¿Qué elegiría…?\".", size = 13)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    input, { input = it.take(20) }, placeholder = { Text("Nombre") }, singleLine = true,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                )
                Spacer(Modifier.width(10.dp))
                Button(onClick = add, enabled = input.isNotBlank(), modifier = Modifier.height(56.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(contentColor = Ink)) { Text("Agregar") }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)) {
            if (game.players.isEmpty()) item { Muted("Sin jugadores: la votación es anónima (solo se cuentan votos).", Modifier.padding(top = 8.dp)) }
            items(game.players, key = { it }) { p ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(p, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { game.removePlayer(p) }) { Text("✕", color = ColorA) }
                }
            }
        }
    }
}

// ---------- Categorías ----------

@Composable
fun CategoriesScreen(game: Game) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Categorías") { game.screen = Screen.Home }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⭐", fontSize = 24.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Solo favoritas", fontSize = 17.sp)
                        Muted(if (game.favs.isEmpty()) "Marca preguntas con ☆ mientras juegas" else "${game.favs.size} guardadas · ignora las categorías", size = 13)
                    }
                    Switch(checked = game.onlyFavs, enabled = game.favs.isNotEmpty(), onCheckedChange = { game.changeOnlyFavs(it) })
                }
            }
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
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Saltar preguntas disparejas", fontSize = 17.sp)
                        Muted("Las que casi todos responden igual (90 % con 8 o más votos) · ${game.lopsidedCount()} detectadas", size = 13)
                    }
                    Switch(checked = game.avoidLopsided, onCheckedChange = { game.changeAvoidLopsided(it) })
                }
                Spacer(Modifier.height(10.dp))
                GhostButton("🔥 Ver las preguntas más reñidas") { game.screen = Screen.Insights }
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

// ---------- Piezas del juego ----------

@Composable
fun GameHeader(game: Game, onEnd: () -> Unit) {
    val q = game.current
    val ctx = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { game.askQuit = true }) { Text("✕", fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground) }
        Spacer(Modifier.weight(1f))
        if (q != null) Box(Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(99.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text("${CATEGORY_EMOJI[q.categoria] ?: ""} ${q.categoria}", fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { game.toggleFav() }) { Text(if (game.isFav()) "⭐" else "☆", fontSize = 20.sp, color = if (game.isFav()) Color(0xFFFFC94D) else MaterialTheme.colorScheme.onSurfaceVariant) }
        TextButton(onClick = { shareQuestion(ctx, game) }) { Text("📤", fontSize = 18.sp) }
        TextButton(onClick = onEnd) { Text("Fin", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Dos tarjetas A / B con el círculo "o" en medio. */
@Composable
fun Cards(
    game: Game, modifier: Modifier,
    onA: (() -> Unit)?, onB: (() -> Unit)?,
    showPct: Boolean = false, chosen: Char? = null,
    badgeA: Int = 0, badgeB: Int = 0, onUnA: () -> Unit = {}, onUnB: () -> Unit = {},
) {
    val total = game.countA() + game.countB()
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxSize()) {
            OptionCard(game.optionA(), "A", ColorA, Modifier.weight(1f), onA, if (showPct) game.countA() else null, total,
                if (chosen == null) null else chosen == 'A', badgeA, onUnA)
            Spacer(Modifier.height(10.dp))
            OptionCard(game.optionB(), "B", ColorB, Modifier.weight(1f), onB, if (showPct) game.countB() else null, total,
                if (chosen == null) null else chosen == 'B', badgeB, onUnB)
        }
        Box(
            Modifier.size(50.dp).background(MaterialTheme.colorScheme.background, CircleShape).padding(5.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("o", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun OptionCard(
    text: String, letter: String, color: Color, modifier: Modifier,
    onClick: (() -> Unit)?, votes: Int?, total: Int, highlight: Boolean?,
    badge: Int, onUnvote: () -> Unit,
) {
    val showResult = votes != null
    val pct = if (showResult && total > 0) votes!!.toFloat() / total else 0f
    val fill by animateFloatAsState(if (showResult) pct else 0f, tween(700), label = "fill")
    val dim = highlight == false
    val solid = !showResult && !dim
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(if (solid) color else MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (showResult) Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(color.copy(alpha = 0.85f)))
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text, fontSize = if (text.length > 60) 21.sp else 25.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                color = when { solid -> Ink; dim -> MaterialTheme.colorScheme.outline; else -> MaterialTheme.colorScheme.onBackground }, lineHeight = 29.sp,
            )
            if (showResult) {
                Spacer(Modifier.height(6.dp))
                Text("${Math.round(pct * 100)}%", fontSize = 32.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
                Muted("$votes ${if (votes == 1) "voto" else "votos"}")
            }
        }
        Text(letter, fontSize = 15.sp, fontWeight = FontWeight.Black, color = if (solid) Ink.copy(alpha = 0.6f) else color, modifier = Modifier.padding(14.dp))
        if (badge > 0) {
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onUnvote) { Text("−1", color = Ink.copy(alpha = 0.7f), fontWeight = FontWeight.Bold) }
                Box(Modifier.size(38.dp).background(Ink, CircleShape), contentAlignment = Alignment.Center) {
                    Text("$badge", color = color, fontWeight = FontWeight.Black, fontSize = 17.sp)
                }
            }
        }
    }
}

/** Fichas con los nombres. Con hidden = true solo se ve quién ya votó, no qué eligió. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoteChips(names: List<String>, votes: Map<String, Char>, hidden: Boolean, correct: Char? = null, onTap: (String) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        names.forEach { n ->
            val v = votes[n]
            val bg = if (v == null) MaterialTheme.colorScheme.surfaceVariant else if (hidden) MaterialTheme.colorScheme.primary else if (v == 'A') ColorA else ColorB
            val mark = if (v == null) "" else if (hidden) " ✓" else if (correct != null) (if (v == correct) " ✓" else " ✗") else " · $v"
            Box(
                Modifier.clip(RoundedCornerShape(99.dp)).background(bg)
                    .clickable(enabled = hidden && v != null) { onTap(n) }.padding(horizontal = 14.dp, vertical = 9.dp),
            ) { Text(n + mark, color = if (v != null) Ink else MaterialTheme.colorScheme.onBackground, fontWeight = if (v != null) FontWeight.Bold else FontWeight.Normal) }
        }
    }
}

/** Después de tocar una opción: quién la eligió. Así el voto no queda a la vista. */
@Composable
fun NamePicker(side: Char, names: List<String>, votes: Map<String, Char>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Quién elige $side?", color = if (side == 'A') ColorA else ColorB, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                names.forEach { n ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant).clickable { onPick(n) }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(n, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        if (n in votes) Muted("ya votó · cambiar", size = 12)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// ---------- Modo Votación ----------

@Composable
fun PlayScreen(game: Game) {
    val total = game.countA() + game.countB()
    val showResult = game.voting && game.revealed
    val namedVoting = game.voting && game.named
    val canVote = game.voting && !game.revealed
    var picking by remember { mutableStateOf<Char?>(null) }
    val onA: (() -> Unit)? = if (!canVote) null else if (namedVoting) ({ picking = 'A' }) else ({ game.anonA += 1 })
    val onB: (() -> Unit)? = if (!canVote) null else if (namedVoting) ({ picking = 'B' }) else ({ game.anonB += 1 })
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
        GameHeader(game) { game.screen = if (game.named && game.roundsCount() > 0) Screen.Summary else Screen.Home }
        Text("¿Qué prefieres?", fontSize = 25.sp, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), textAlign = TextAlign.Center)
        Cards(
            game, Modifier.weight(1f), onA = onA, onB = onB, showPct = showResult,
            badgeA = if (canVote && !namedVoting) game.anonA else 0, badgeB = if (canVote && !namedVoting) game.anonB else 0,
            onUnA = { if (game.anonA > 0) game.anonA -= 1 }, onUnB = { if (game.anonB > 0) game.anonB -= 1 },
        )
        Spacer(Modifier.height(10.dp))
        if (namedVoting) {
            VoteChips(game.players, game.votes, hidden = !game.revealed) { game.unvote(it) }
            Spacer(Modifier.height(8.dp))
        }
        if (showResult) {
            val a = game.countA(); val b = game.countB()
            val minSide = if (a < b) 'A' else 'B'
            val minNames = game.votes.filter { it.value == minSide }.keys
            val msg = if (a == b) "¡Empate! Que alguien de cada lado defienda su opción 🗣️"
            else if (a == 0 || b == 0) "¡Todos de acuerdo! 🤝"
            else if (minNames.isNotEmpty()) "${minNames.joinToString(", ")}: defiendan su opción 🗣️"
            else "La minoría ($minSide) tiene que defender su opción 🗣️"
            Muted(msg, Modifier.fillMaxWidth().padding(bottom = 4.dp), align = TextAlign.Center)
            val ph = game.prevHist
            if (ph != null && ph.first + ph.second > 0) {
                val ht = ph.first + ph.second
                val win = if (ph.first >= ph.second) "A" else "B"
                val pct = Math.round(maxOf(ph.first, ph.second) * 100f / ht)
                Muted("📊 En partidas anteriores: $pct% eligió $win ($ht votos)", Modifier.fillMaxWidth().padding(bottom = 10.dp), align = TextAlign.Center, size = 13)
            } else {
                Spacer(Modifier.height(6.dp))
            }
        } else if (game.voting) {
            val hint = if (namedVoting) {
                if (total == 0) "Toca tu opción y luego tu nombre. Los votos se ven al final."
                else "$total de ${game.players.size} votaron · toca un nombre para quitar su voto"
            } else {
                if (total == 0) "Toquen la opción que cada uno prefiere" else "$total ${if (total == 1) "voto" else "votos"}"
            }
            Muted(hint, Modifier.fillMaxWidth().padding(bottom = 10.dp), align = TextAlign.Center)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (game.voting && !game.revealed) {
                GhostButton("Saltar", Modifier.weight(1f)) { game.next() }
                BigButton("Ver resultado", enabled = total > 0, modifier = Modifier.weight(1.4f)) { game.reveal() }
            } else {
                GhostButton("Anterior", Modifier.weight(1f), enabled = game.canGoBack()) { game.previous() }
                BigButton("Siguiente", modifier = Modifier.weight(1.4f)) { game.next() }
            }
        }
    }
    val side = picking
    if (side != null) {
        NamePicker(side, game.players, game.votes, onPick = { game.vote(it, side); picking = null }, onDismiss = { picking = null })
    }
}

// ---------- Modo "¿Qué elegiría…?" ----------

@Composable
fun GuessScreen(game: Game) {
    var picking by remember { mutableStateOf<Char?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
        GameHeader(game) { game.screen = Screen.Summary }
        when (game.phase) {
            Phase.PASS -> {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🤫", fontSize = 64.sp)
                    Spacer(Modifier.height(12.dp))
                    Muted("Pásale el celular a", align = TextAlign.Center)
                    Text(game.chooser, fontSize = 40.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Muted("Va a elegir en secreto. Los demás, no miren.", align = TextAlign.Center)
                }
                BigButton("Soy ${game.chooser}, ver pregunta") { game.phase = Phase.SECRET }
            }
            Phase.SECRET -> {
                Text("${game.chooser}, ¿qué prefieres?", fontSize = 23.sp, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), textAlign = TextAlign.Center)
                Muted("Toca tu opción. Nadie más debe ver.", Modifier.fillMaxWidth().padding(bottom = 8.dp), align = TextAlign.Center)
                Cards(game, Modifier.weight(1f), onA = { game.chooseSecret('A') }, onB = { game.chooseSecret('B') })
            }
            Phase.GUESS -> {
                Text("¿Qué eligió ${game.chooser}?", fontSize = 23.sp, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), textAlign = TextAlign.Center)
                Cards(game, Modifier.weight(1f), onA = { picking = 'A' }, onB = { picking = 'B' })
                Spacer(Modifier.height(10.dp))
                VoteChips(game.guessers(), game.votes, hidden = true) { game.unvote(it) }
                Muted(
                    "Toca lo que crees que eligió y luego tu nombre · ${game.votes.size} de ${game.guessers().size}",
                    Modifier.fillMaxWidth().padding(vertical = 8.dp), align = TextAlign.Center,
                )
                BigButton("Revelar", enabled = game.votes.isNotEmpty()) { game.revealGuess() }
            }
            Phase.REVEAL -> {
                val s = game.secret ?: 'A'
                Text("${game.chooser} eligió $s", fontSize = 23.sp, fontWeight = FontWeight.Black, color = if (s == 'A') ColorA else ColorB,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), textAlign = TextAlign.Center)
                Cards(game, Modifier.weight(1f), onA = null, onB = null, chosen = s)
                Spacer(Modifier.height(10.dp))
                VoteChips(game.guessers(), game.votes, hidden = false, correct = s) {}
                val right = game.votes.count { it.value == s }
                Muted(
                    if (game.nobodyGuessed()) "¡${game.chooser} los engañó a todos! +2 🎭" else "$right ${if (right == 1) "acertó" else "acertaron"} · +1 cada uno",
                    Modifier.fillMaxWidth().padding(vertical = 8.dp), align = TextAlign.Center,
                )
                ScoreLine(game)
                Spacer(Modifier.height(8.dp))
                BigButton("Siguiente") { game.next() }
            }
        }
    }
    val side = picking
    if (side != null && game.phase == Phase.GUESS) {
        NamePicker(side, game.guessers(), game.votes, onPick = { game.vote(it, side); picking = null }, onDismiss = { picking = null })
    }
}

@Composable
fun ScoreLine(game: Game) {
    val top = game.scores.entries.sortedByDescending { it.value }
    Text(
        top.joinToString("   ") { "${it.key} ${it.value}" }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 2,
    )
}

// ---------- Preguntas más reñidas ----------

@Composable
fun InsightsScreen(game: Game) {
    val close = remember { game.closest() }
    val lop = remember { game.mostLopsided() }
    Column(Modifier.fillMaxSize()) {
        TopBar("Preguntas del parche") { game.screen = Screen.Categories }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 8.dp)) {
            if (close.isEmpty()) {
                Muted("Aún no hay suficientes votos. Jueguen en modo Votación y aquí verán cuáles preguntas dividen más al parche.", Modifier.padding(top = 12.dp))
            } else {
                Section("🔥 Las más reñidas")
                close.forEach { QuestionStatRow(it) }
            }
            if (lop.isNotEmpty()) {
                Section("😴 Casi todos eligen lo mismo")
                lop.forEach { QuestionStatRow(it) }
            }
        }
    }
}

@Composable
fun QuestionStatRow(st: QuestionStat) {
    val t = st.a + st.b
    val pa = if (t > 0) Math.round(st.a * 100f / t) else 0
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row {
            Text(st.q.a, color = ColorA, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("$pa%", color = ColorA, fontWeight = FontWeight.Bold)
        }
        Row {
            Text(st.q.b, color = ColorB, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("${100 - pa}%", color = ColorB, fontWeight = FontWeight.Bold)
        }
        Muted("$t votos", size = 12)
    }
}

// ---------- Resumen ----------

@Composable
fun SummaryScreen(game: Game) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Resumen") { game.screen = Screen.Play }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 8.dp)) {
            if (game.mode == Mode.ADIVINA) {
                Section("🏆 Puntajes")
                game.scores.entries.sortedByDescending { it.value }.forEachIndexed { i, (n, v) ->
                    StatRow(if (i == 0 && v > 0) "👑 $n" else n, "$v pts")
                }
                val readers = game.bestReader()
                if (readers.isNotEmpty()) {
                    Section("🔮 Quién conoce mejor a quién")
                    readers.take(5).forEach { StatRow("${it.p1} → ${it.p2}", "${it.pct}% (${it.n})") }
                }
            } else {
                Muted("${game.roundsCount()} preguntas con votos")
                val aff = game.affinity()
                val min = game.minority()
                if (aff.isEmpty() && min.isEmpty()) {
                    Muted("Jueguen más preguntas votando con nombres para ver estadísticas.", Modifier.padding(top = 16.dp))
                }
                if (aff.isNotEmpty()) {
                    Section("💞 Almas gemelas")
                    aff.sortedByDescending { it.pct }.take(3).forEach { StatRow("${it.p1} y ${it.p2}", "${it.pct}% igual") }
                    Section("⚡ Polos opuestos")
                    aff.sortedBy { it.pct }.take(3).forEach { StatRow("${it.p1} y ${it.p2}", "${it.pct}% igual") }
                }
                if (min.isNotEmpty()) {
                    val rare = min.maxBy { it.pct }
                    val sheep = min.minBy { it.pct }
                    Section("🦄 El más raro")
                    StatRow(rare.name, "${rare.pct}% en la minoría")
                    if (sheep.name != rare.name) {
                        Section("🐑 Siempre con la mayoría")
                        StatRow(sheep.name, "${sheep.pct}% en la minoría")
                    }
                }
            }
        }
        Row(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Inicio", Modifier.weight(1f)) { game.screen = Screen.Home }
            BigButton("Seguir jugando", modifier = Modifier.weight(1.4f)) { game.screen = Screen.Play }
        }
    }
}

@Composable
fun Section(t: String) {
    Text(t, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
}

@Composable
fun StatRow(left: String, right: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(left, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(right, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
