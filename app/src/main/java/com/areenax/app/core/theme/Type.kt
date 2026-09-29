package com.areenax.app.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.areenax.app.R

/**
 * AREENAX type system — Hanken Grotesk (bundled variable TTF, weights 100–900).
 *
 * The web type scale (globals.css @theme, SPEC/04 §3):
 *   display-lg   32/40 700 -0.02em
 *   headline-lg  24/32 700
 *   headline-lg-mobile 22/28 700
 *   headline-md  20/28 600
 *   body-lg      16/24 500
 *   body-md      14/20 400
 *   label-lg     14/16 600
 *   label-md     12/14 500 +0.01em
 *   label-sm     12/16 600 +0.05em
 *
 * These exact styles are exposed as [Type.displayLg] etc. so screens can use
 * the same names as the web classes; the Material3 [Typography] is mapped from
 * the same values. `Type` also carries the recurring ad-hoc styles documented
 * in SPEC/04 §3 (page titles, hero amounts, toast, success titles).
 */
@OptIn(ExperimentalTextApi::class)
private fun hankenFont(weight: FontWeight, variation: Int) = Font(
    resId = R.font.hanken_grotesk_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(variation)),
)

val HankenGrotesk = FontFamily(
    hankenFont(FontWeight.W400, 400),
    hankenFont(FontWeight.W500, 500),
    hankenFont(FontWeight.W600, 600),
    hankenFont(FontWeight.W700, 700),
    hankenFont(FontWeight.W800, 800),
    hankenFont(FontWeight.W900, 900),
)

/** Extended, web-named text styles (see file KDoc for the source scale). */
object Type {
    /** `text-display-lg` — 32/40, 700, -0.02em */
    val displayLg = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.02).em,
    )

    /** `text-headline-lg` — 24/32, 700 */
    val headlineLg = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.01).em,
    )

    /** `text-headline-lg-mobile` — 22/28, 700 (the AppBar/page-title size) */
    val headlineLgMobile = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.01).em,
    )

    /** `text-headline-md` — 20/28, 600 */
    val headlineMd = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.01).em,
    )

    /** `text-body-lg` — 16/24, 500 */
    val bodyLg = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 28.sp,
    )

    /** `text-body-md` — 14/20, 400 */
    val bodyMd = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 24.sp,
    )

    /** `text-label-lg` — 14/16, 600 */
    val labelLg = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 16.sp,
    )

    /** `text-label-md` — 12/14, 500, +0.01em */
    val labelMd = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.01.em,
    )

    /** `text-label-sm` — 12/16, 600, +0.05em (chips/badges) */
    val labelSm = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.05.em,
    )

    // ---- Recurring ad-hoc web styles (SPEC/04 §3 "Other recurring styles") ----

    /** Page title variant: 20/26 semibold (`text-[1.25rem] leading-[1.625rem] font-semibold`) */
    val pageTitle = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    )

    /** Hero amount (wallet card / deposit figures): 32 bold tracking-tight */
    val heroAmount = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.02).em,
    )

    /** Toast title: 15px bold; toast description: 13px */
    val toastTitle = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    val toastBody = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Normal, fontSize = 13.sp)

    /** Success-anim title: 26px bold tracking-tight */
    val successTitle = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = (-0.02).em,
    )
}

/** Material3 Typography mapped onto the AREENAX scale. */
val AreenaxTypography = Typography(
    displayLarge = Type.displayLg,
    displayMedium = Type.headlineLg,
    displaySmall = Type.headlineLgMobile,
    headlineLarge = Type.headlineLg,
    headlineMedium = Type.headlineMd,
    headlineSmall = Type.headlineLgMobile.copy(fontSize = 18.sp, lineHeight = 24.sp),
    titleLarge = Type.headlineLgMobile.copy(fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = Type.labelLg.copy(fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = Type.labelLg,
    bodyLarge = Type.bodyLg,
    bodyMedium = Type.bodyMd,
    bodySmall = Type.labelMd.copy(lineHeight = 16.sp),
    labelLarge = Type.labelLg,
    labelMedium = Type.labelMd,
    labelSmall = Type.labelSm,
)
