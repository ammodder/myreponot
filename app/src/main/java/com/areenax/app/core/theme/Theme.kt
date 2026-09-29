package com.areenax.app.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Extended AREENAX tokens that have no Material3 slot
 * (SPEC/04 §1/§2 "AREENAX custom colors" + gradients).
 *
 * Access from screens: `val ext = AreenaxTheme.extendedColors` or the
 * shorthand `areenaColors()` composable.
 */
@Immutable
data class ExtendedColors(
    val surfaceLavender: Color,
    val surfaceContainerLavender: Color,
    val surfaceContainerHighLavender: Color,
    val balanceChip: Color,
    /** Wallet money-in icon circle (emerald chip pair). */
    val txInIconBg: Color,
    val txInIconFg: Color,
    /** Fixed primary tone (#DBE1FF light / #182338 dark) — read via
     *  `areenaColors().primaryFixed` (material3 1.3.1 has no *Fixed scheme slots). */
    val primaryFixed: Color,
    val onPrimaryFixed: Color,
    /** Trophy gradient (135°, #ffd700 → #daa520). */
    val trophyGradient: Brush,
    /** Leaderboard crown tint (#F59E0B) / MyStats gold medal (#D4AF37). */
    val leaderboardCrown: Color,
    val goldMedal: Color,
    /** WhatsApp green (deprecated FAB — kept for parity). */
    val whatsappGreen: Color,
    /** Offline screen fixed light gradient. */
    val offlineGradient: Brush,
    /** True when the dark canvas glow should be painted behind the NavHost. */
    val isDark: Boolean,
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = LightInversePrimary,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightSurfaceTint,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    // Fixed accent roles (SPEC/04 §7 "primary-fixed family → custom extension colors"):
    // material3 1.3.1 has no *Fixed slots, so these tones live ONLY in ExtendedColors
    // (read via areenaColors().primaryFixed). Values unchanged.
    surfaceDim = LightSurfaceDim,
    surfaceBright = LightSurfaceBright,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = DarkInversePrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = Tertiary, // web dark does not redefine the tertiary family
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkSurfaceTint,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
)

private fun lightExtended() = ExtendedColors(
    surfaceLavender = LightSurfaceLavender,
    surfaceContainerLavender = LightSurfaceContainerLavender,
    surfaceContainerHighLavender = LightSurfaceContainerHighLavender,
    balanceChip = LightBalanceChip,
    txInIconBg = LightTxInIconBg,
    txInIconFg = LightTxInIconFg,
    primaryFixed = LightPrimaryFixed,
    onPrimaryFixed = LightOnPrimaryFixed,
    trophyGradient = Brush.linearGradient(listOf(TrophyGoldStart, TrophyGoldEnd)),
    leaderboardCrown = LeaderboardCrown,
    goldMedal = GoldMedal,
    whatsappGreen = WhatsAppGreen,
    offlineGradient = Brush.verticalGradient(listOf(OfflineGradientTop, OfflineGradientBottom)),
    isDark = false,
)

private fun darkExtended() = ExtendedColors(
    surfaceLavender = DarkSurfaceLavender,
    surfaceContainerLavender = DarkSurfaceContainerLavender,
    surfaceContainerHighLavender = DarkSurfaceContainerHighLavender,
    balanceChip = DarkBalanceChip,
    txInIconBg = DarkTxInIconBg,
    txInIconFg = DarkTxInIconFg,
    primaryFixed = DarkPrimaryFixed,
    onPrimaryFixed = DarkOnPrimaryFixed,
    trophyGradient = Brush.linearGradient(listOf(TrophyGoldStart, TrophyGoldEnd)),
    leaderboardCrown = LeaderboardCrown,
    goldMedal = GoldMedal,
    whatsappGreen = WhatsAppGreen,
    offlineGradient = Brush.verticalGradient(listOf(OfflineGradientTop, OfflineGradientBottom)),
    isDark = true,
)

val LocalExtendedColors = staticCompositionLocalOf<ExtendedColors> { lightExtended() }

/** Shorthand accessor for the custom AREENAX tokens. */
@Composable
fun areenaColors(): ExtendedColors = LocalExtendedColors.current

/**
 * AREENAX theme root. The web supports dark mode via the areena-theme token
 * swap (Profile toggle, persisted) — the boolean comes from SessionManager's
 * DataStore-backed flow, NOT the system setting (mirrors web behavior where
 * the persisted choice wins; first-run default is light).
 *
 * When [followSystem] is true (used only before the persisted value loads),
 * the system setting is respected.
 */
@Composable
fun AreenaxTheme(
    darkTheme: Boolean,
    followSystem: Boolean = false,
    content: @Composable () -> Unit,
) {
    val useDark = if (followSystem) isSystemInDarkTheme() else darkTheme
    val colorScheme = if (useDark) DarkScheme else LightScheme
    val extended = if (useDark) darkExtended() else lightExtended()
    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AreenaxTypography,
            shapes = AreenaxM3Shapes,
            content = content,
        )
    }
}
