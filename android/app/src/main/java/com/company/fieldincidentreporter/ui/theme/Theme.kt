package com.company.fieldincidentreporter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Teal,
    secondary = TealDark,
    tertiary = Amber,
    background = Canvas,
    surface = Canvas,
    onBackground = Ink,
    onSurface = Ink,
)

@Composable
fun FieldIncidentReporterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
