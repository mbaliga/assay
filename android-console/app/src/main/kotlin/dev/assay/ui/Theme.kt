package dev.assay.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = HyleTokens.violet,
    onPrimary = HyleTokens.ground,
    primaryContainer = HyleTokens.darkSurfaceHigh,
    onPrimaryContainer = HyleTokens.darkOnSurface,
    background = HyleTokens.ground,
    onBackground = HyleTokens.darkOnSurface,
    surface = HyleTokens.ground,
    onSurface = HyleTokens.darkOnSurface,
    surfaceVariant = HyleTokens.darkSurface,
    onSurfaceVariant = HyleTokens.darkMuted,
    surfaceContainer = HyleTokens.darkSurface,
    surfaceContainerHigh = HyleTokens.darkSurfaceHigh,
    outline = HyleTokens.darkOutline,
    outlineVariant = HyleTokens.darkOutline,
    error = HyleTokens.darkOnSurface,
    onError = HyleTokens.ground,
)

private val LightColors = lightColorScheme(
    primary = HyleTokens.lightPrimary,
    onPrimary = HyleTokens.lightSurface,
    primaryContainer = HyleTokens.lightSurfaceHigh,
    onPrimaryContainer = HyleTokens.lightOnSurface,
    background = HyleTokens.lightGround,
    onBackground = HyleTokens.lightOnSurface,
    surface = HyleTokens.lightGround,
    onSurface = HyleTokens.lightOnSurface,
    surfaceVariant = HyleTokens.lightSurfaceHigh,
    onSurfaceVariant = HyleTokens.lightMuted,
    surfaceContainer = HyleTokens.lightSurface,
    surfaceContainerHigh = HyleTokens.lightSurfaceHigh,
    outline = HyleTokens.lightOutline,
    outlineVariant = HyleTokens.lightOutline,
    error = HyleTokens.lightOnSurface,
    onError = HyleTokens.lightSurface,
)

/**
 * Dark is primary. `error` is deliberately the ordinary text colour: errors are marked by a ✕
 * glyph and the word "Error", never by red, because colour never carries state alone.
 */
@Composable
fun AssayTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
