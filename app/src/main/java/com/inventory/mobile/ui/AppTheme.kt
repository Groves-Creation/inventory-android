package com.inventory.mobile.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

val pilotLightColors = lightColorScheme(
    primary = Color(0xFF146C5A),
    onPrimary = Color.White,
    secondary = Color(0xFF52616B),
    error = Color(0xFFB3261E),
    background = Color(0xFFF4F6F8),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE8EDF0),
    onSurfaceVariant = Color(0xFF434C53),
    outline = Color(0xFF8A9399),
)

val pilotDarkColors = darkColorScheme(
    primary = Color(0xFF4ECDB4),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFF7AF8E0),
    secondary = Color(0xFFB0BEC5),
    onSecondary = Color(0xFF1A282E),
    background = Color(0xFF0A0E10),
    onBackground = Color(0xFFE3E8EA),
    surface = Color(0xFF12171A),
    onSurface = Color(0xFFE3E8EA),
    surfaceVariant = Color(0xFF1E262B),
    onSurfaceVariant = Color(0xFFB8C4CA),
    surfaceContainer = Color(0xFF181E22),
    surfaceContainerHigh = Color(0xFF222A30),
    surfaceContainerHighest = Color(0xFF2C353C),
    outline = Color(0xFF5A6B75),
    outlineVariant = Color(0xFF3A474F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        content = content,
    )
}
