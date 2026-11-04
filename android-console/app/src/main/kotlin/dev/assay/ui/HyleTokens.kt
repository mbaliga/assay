package dev.assay.ui

import androidx.compose.ui.graphics.Color

/**
 * Hyle design token values, copied (not imported: the Hyle submodule is deliberately not a
 * dependency, BUILD-SPEC rule 12). Cyan #08FED5 is retired and must not be used.
 */
object HyleTokens {
    /** Ground. Never pure black except for pixels-off states. */
    val ground = Color(0xFF07070A)

    /** Brand / primary. */
    val violet = Color(0xFF8E7BFF)

    /** On-device / local provenance glow. */
    val radium = Color(0xFFCCEC75)

    /** Cloud provenance glow. Reserved for WP5's watched-object badge. */
    val alien = Color(0xFF3ED4FF)

    // Dark theme (primary).
    val darkSurface = Color(0xFF111118)
    val darkSurfaceHigh = Color(0xFF1B1B26)
    val darkOnSurface = Color(0xFFEDEBF5)
    val darkMuted = Color(0xFFA9A6BA)
    val darkOutline = Color(0xFF45435C)

    // Light theme: must still pass contrast, so the violet is darkened for text/controls.
    val lightGround = Color(0xFFF8F7FC)
    val lightSurface = Color(0xFFFFFFFF)
    val lightSurfaceHigh = Color(0xFFEEEBF8)
    val lightOnSurface = Color(0xFF15141C)
    val lightMuted = Color(0xFF5C5970)
    val lightOutline = Color(0xFF8C88A4)
    val lightPrimary = Color(0xFF5A45E0)

    /** Luminance steps for SEV1..SEV4 glyphs, as a fraction of on-surface. */
    val severityLuminance = floatArrayOf(1.0f, 0.8f, 0.6f, 0.4f)
}
