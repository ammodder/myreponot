package com.areenax.app.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * AREENAX corner radii (SPEC/04 §4).
 *
 * Web usage → Compose mapping:
 *   rounded-full inputs/buttons/chips → CircleShape / [Pill]
 *   rounded-[1.5rem] cards (tournament/stat/list cards) → [Card] (24dp) / M3 `large`
 *   rounded-t-[2rem] bottom sheets → [Sheet] (top 32dp) / M3 `extraLarge`
 *   rounded-2xl (16dp) small cards, rounded-xl (12dp) icon tiles → M3 `medium`
 *   rounded-[1.75rem] dialogs (28dp) → [Dialog]
 */
object AreenaxShapes {
    /** All-text-field + button pill shape. */
    val Pill = RoundedCornerShape(percent = 50)

    /** Big cards: `rounded-[1.5rem]` / `rounded-3xl` = 24dp. */
    val Card = RoundedCornerShape(24.dp)

    /** Bottom sheets: `rounded-t-[2rem]` = 32dp top corners. */
    val Sheet = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

    /** Dialog cards: `rounded-[1.75rem]` = 28dp. */
    val Dialog = RoundedCornerShape(28.dp)

    /** Small cards/inputs: `rounded-2xl` = 16dp. */
    val MediumCard = RoundedCornerShape(16.dp)

    /** Icon tiles: `rounded-xl` = 12dp. */
    val Tile = RoundedCornerShape(12.dp)

    /** Small chips/inputs: `rounded-lg..xl` = 12dp; progress bars 4–6dp. */
    val Small = RoundedCornerShape(8.dp)
}

/** Material3 [Shapes] fed into [androidx.compose.material3.MaterialTheme]. */
val AreenaxM3Shapes = Shapes(
    extraSmall = AreenaxShapes.Small,           // 8dp
    small = AreenaxShapes.Tile,                 // 12dp
    medium = AreenaxShapes.MediumCard,          // 16dp
    large = AreenaxShapes.Card,                 // 24dp
    extraLarge = AreenaxShapes.Dialog,          // 28dp
)
