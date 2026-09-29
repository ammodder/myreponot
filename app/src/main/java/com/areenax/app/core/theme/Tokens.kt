package com.areenax.app.core.theme

import androidx.compose.ui.graphics.Color

/**
 * AREENAX palette constants (SPEC/04-DESIGN-TOKENS.md §1 light + §2 dark).
 *
 * The web source of truth is `src/app/globals.css` (`@theme` + `:root` +
 * `:root.dark`). These top-level vals are consumed by [Theme.kt]'s
 * `LightScheme` / `DarkScheme` / `lightExtended()` / `darkExtended()`.
 *
 * Naming: `Light<WebToken>` / `Dark<WebToken>` (PascalCase web token).
 * Shared-accent tokens (tertiary family, trophy/crown/gold, WhatsApp green,
 * offline gradient) are theme-independent per the web CSS, so they have no
 * Light/Dark prefix.
 */

// ===========================================================================
// §1 — LIGHT THEME (`:root`)
// ===========================================================================

// --- Surfaces & ink --------------------------------------------------------
val LightBackground = Color(0xFFF8F9FF)
val LightSurface = Color(0xFFF8F9FF)
val LightSurfaceBright = Color(0xFFF8F9FF)
val LightSurfaceDim = Color(0xFFCBDBF5)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFEFF4FF)
val LightSurfaceContainer = Color(0xFFE5EEFF)
val LightSurfaceContainerHigh = Color(0xFFDCE9FF)
val LightSurfaceContainerHighest = Color(0xFFD3E4FE)
val LightSurfaceVariant = Color(0xFFD3E4FE)
val LightOnSurface = Color(0xFF0B1C30)
val LightOnSurfaceVariant = Color(0xFF434655)

// --- Primary (blue) --------------------------------------------------------
val LightPrimary = Color(0xFF004AC6)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFF2563EB)
val LightOnPrimaryContainer = Color(0xFFEEEFFF)
/** Fixed tone — identical in light & dark per web (`#dbe1ff` light, `#182338` dark). */
val LightPrimaryFixed = Color(0xFFDBE1FF)
val LightOnPrimaryFixed = Color(0xFF00174B)
val LightInversePrimary = Color(0xFFB4C5FF)
val LightSurfaceTint = Color(0xFF0053DB)

// --- Secondary (green / money-in) -----------------------------------------
val LightSecondary = Color(0xFF006C49)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFF6CF8BB)
val LightOnSecondaryContainer = Color(0xFF00714D)

// --- Tertiary (red/rose accents) — NOT redefined in dark -------------------
val Tertiary = Color(0xFFAD0033)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFD22348)
val OnTertiaryContainer = Color(0xFFFFECEC)

// --- Error, outline, inverse -----------------------------------------------
val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF93000A)
val LightOutline = Color(0xFF737686)
val LightOutlineVariant = Color(0xFFC3C6D7)
val LightInverseSurface = Color(0xFF213145)
val LightInverseOnSurface = Color(0xFFEAF1FF)

// --- AREENAX custom colors (light) -----------------------------------------
val LightSurfaceLavender = Color(0xFFFAF8FF)
val LightSurfaceContainerLavender = Color(0xFFEDEDF8)
val LightSurfaceContainerHighLavender = Color(0xFFE7E7F2)
val LightBalanceChip = Color(0xFFF0F4FF)
/** Wallet money-in icon circle (emerald chip pair, light). */
val LightTxInIconBg = Color(0xFFECFDF5)
val LightTxInIconFg = Color(0xFF059669)

// ===========================================================================
// §2 — DARK THEME (`:root.dark`, "Midnight Pills")
// ===========================================================================

// --- Surfaces & ink (dark) -------------------------------------------------
val DarkBackground = Color(0xFF0A0A0C)
val DarkSurface = Color(0xFF0A0A0C)
val DarkSurfaceBright = Color(0xFF232327)
val DarkSurfaceDim = Color(0xFF060608)
val DarkSurfaceContainerLowest = Color(0xFF131316)
val DarkSurfaceContainerLow = Color(0xFF17171B)
val DarkSurfaceContainer = Color(0xFF1B1B1F)
val DarkSurfaceContainerHigh = Color(0xFF232327)
val DarkSurfaceContainerHighest = Color(0xFF2A2A2F)
val DarkSurfaceVariant = Color(0xFF232327)
val DarkOnSurface = Color(0xFFF5F5F7)
val DarkOnSurfaceVariant = Color(0xFF9D9DA6)

// --- Primary (dark — ONE vivid accent) -------------------------------------
val DarkPrimary = Color(0xFF2F6BFF)
val DarkOnPrimary = Color(0xFFFFFFFF)
val DarkPrimaryContainer = Color(0xFF004AC6)
val DarkOnPrimaryContainer = Color(0xFFDBE1FF)
val DarkPrimaryFixed = Color(0xFF182338)
val DarkOnPrimaryFixed = Color(0xFFDBE1FF)
val DarkInversePrimary = Color(0xFF9DB9FF)
val DarkSurfaceTint = Color(0xFF2F6BFF)

// --- Secondary (dark) ------------------------------------------------------
val DarkSecondary = Color(0xFF4EDEA3)
val DarkOnSecondary = Color(0xFF002113)
val DarkSecondaryContainer = Color(0xFF0E3A2A)
val DarkOnSecondaryContainer = Color(0xFF6FFBBE)

// --- Error (dark) ----------------------------------------------------------
val DarkError = Color(0xFFFF5449)
val DarkOnError = Color(0xFFFFFFFF)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

// --- Outline (dark) --------------------------------------------------------
val DarkOutline = Color(0xFF3A3A41)
val DarkOutlineVariant = Color(0xFF26262B)
val DarkInverseSurface = Color(0xFFECECF0)
val DarkInverseOnSurface = Color(0xFF141416)

// --- AREENAX custom colors (dark) ------------------------------------------
val DarkSurfaceLavender = Color(0xFF0A0A0C)
val DarkSurfaceContainerLavender = Color(0xFF1B1B1F)
val DarkSurfaceContainerHighLavender = Color(0xFF232327)
val DarkBalanceChip = Color(0xFF16181D)
/** Dark wallet money-in icon: bg `#064e3b`, fg `#6ee7b7`. */
val DarkTxInIconBg = Color(0xFF064E3B)
val DarkTxInIconFg = Color(0xFF6EE7B7)

// ===========================================================================
// Theme-independent accents (SPEC/04 §1 custom colors + §5 gradients)
// ===========================================================================

/** `.trophy-icon` gradient stops (135°, #ffd700 → #daa520). */
val TrophyGoldStart = Color(0xFFFFD700)
val TrophyGoldEnd = Color(0xFFDAA520)

/** LeaderboardScreen crown SVG tint (#F59E0B). */
val LeaderboardCrown = Color(0xFFF59E0B)

/** MyStats gold medal (#D4AF37 = rgb(212,175,55)). */
val GoldMedal = Color(0xFFD4AF37)

/** WhatsAppFab brand green (deprecated FAB — kept for parity). */
val WhatsAppGreen = Color(0xFF25D366)

/** Offline page fixed LIGHT gradient (SPEC/04 §5: #f0f4f8 0% → #dbe4ec 100%). */
val OfflineGradientTop = Color(0xFFF0F4F8)
val OfflineGradientBottom = Color(0xFFDBE4EC)

// ===========================================================================
// R3 sweep: literals extracted from screens — values unchanged (Task 19-a).
// Hex values that already had a token above (e.g. LightPrimary, DarkPrimary,
// DarkInversePrimary, LightInversePrimary, LightPrimaryContainer,
// DarkSurfaceDim, DarkTxInIconBg, LightTxInIconBg, LightBackground) are
// referenced there and NOT re-declared here. Shadow() tints live in
// core/ui/Shadows.kt (none needed this sweep).
// ===========================================================================

// --- Brand / social marks (AboutScreen + SocialMarks) ----------------------
val brandYouTube = Color(0xFFFF0000)
val brandInstagram = Color(0xFFEC4899)
val brandDiscord = Color(0xFF5865F2)
val brandFacebook = Color(0xFF1877F2)

// --- Pastel social-chip backgrounds (AboutScreen, light mode) --------------
val chipGreenSoft = Color(0xFFF0FDF4)
val chipRedSoft = Color(0xFFFEF2F2)
val chipPinkSoft = Color(0xFFFDF2F8)
val chipBlueSoft = Color(0xFFEFF6FF)

// --- Light-mode accents / hairlines / status pairs -------------------------
val successGreen = Color(0xFF22C55E)
val chevronGray = Color(0xFF9CA3AF)
val hairlineLavender = Color(0xFFE2E2EC)
val hairlineGray = Color(0xFFEDF2F7)
val primaryDeep = Color(0xFF003594)
val mutedSlate = Color(0xFF94A3B8)
val onlineGreen = Color(0xFF10B981)
val ghostInk = Color(0xFF1E293B)
val roseSoft = Color(0xFFFFDADB)
val roseInk = Color(0xFF92002A)

// --- Banner / page decorations ----------------------------------------------
val bannerDarkStart = Color(0xFF111827)
val bannerDotDark = Color(0xFF333333)
val tasksGradientEnd = Color(0xFFF4F5F9)
val referGlowStart = Color(0x99DBE1FF)
