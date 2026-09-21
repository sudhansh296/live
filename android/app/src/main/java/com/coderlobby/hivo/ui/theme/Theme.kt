package com.coderlobby.hivo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HivoDarkColors = darkColorScheme(
    primary = HivoRed,
    onPrimary = Color.White,
    secondary = HivoGold,
    background = HivoBackground,
    onBackground = Color.White,
    surface = HivoCard,
    onSurface = Color.White,
    surfaceVariant = HivoCardAlt,
    onSurfaceVariant = Color(0xFFAAAAAA),
)

@Composable
fun HivoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HivoDarkColors, content = content)
}
