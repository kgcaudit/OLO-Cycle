package com.kgcaudit.olocycle.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * OLO Cycle brand palette — our own identity, deliberately distinct from other period apps:
 * a plum primary with a warm coral accent, and calm phase colors on a soft lilac-cream ground.
 */
object OloColors {
    val Primary = Color(0xFF6D4AA3)      // OLO Plum
    val PrimaryDark = Color(0xFF553A85)
    val OnPrimary = Color(0xFFFFFFFF)
    val Accent = Color(0xFFF2795B)       // OLO Coral

    val Background = Color(0xFFFBF7FA)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceSoft = Color(0xFFF5EEF5)
    val Ink = Color(0xFF2C2230)
    val Muted = Color(0xFF8A7E90)
    val Line = Color(0xFFEBE1EC)

    // Cycle phases
    val Period = Color(0xFFE15C74)
    val PeriodLight = Color(0xFFF7DBE3)
    val Fertile = Color(0xFF2FA58C)
    val FertileLight = Color(0xFFD6F0EA)
    val Ovulation = Color(0xFF3E8FC7)
    val OvulationLight = Color(0xFFD8EAF6)
    val Pms = Color(0xFFB98BD9)
    val PmsLight = Color(0xFFEEE1F7)

    /** Accent colors offered when creating a member profile. */
    val ProfilePalette = listOf(
        Color(0xFF6D4AA3), // plum
        Color(0xFFF2795B), // coral
        Color(0xFF2FA58C), // teal
        Color(0xFFE8A13C), // amber
        Color(0xFFE15C74), // rose
        Color(0xFF4F79C4), // blue
    )
}
