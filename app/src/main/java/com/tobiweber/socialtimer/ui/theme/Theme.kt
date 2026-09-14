package com.tobiweber.socialtimer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PurplePrimary = Color(0xFF6650a4)
private val TealSecondary = Color(0xFF03DAC5)

private val LightColors = lightColorScheme(
    primary = PurplePrimary,
    secondary = TealSecondary
)

private val DarkColors = darkColorScheme(
    primary = PurplePrimary,
    secondary = TealSecondary
)

@Composable
fun SocialTimerTheme(darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
