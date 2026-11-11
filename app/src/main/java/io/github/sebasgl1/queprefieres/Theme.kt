package io.github.sebasgl1.queprefieres

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF121019)
val ColorA = Color(0xFFFF5F7E)
val ColorB = Color(0xFF4FB8FF)

private val Scheme = darkColorScheme(
    primary = Color(0xFFF4F1FA), onPrimary = Ink,
    background = Ink, onBackground = Color(0xFFECE9F3),
    surface = Color(0xFF1D1A27), onSurface = Color(0xFFECE9F3),
    surfaceVariant = Color(0xFF2C2839), onSurfaceVariant = Color(0xFF9690A8),
    outline = Color(0xFF3D3850),
)

@Composable
fun QueTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
    }
}
