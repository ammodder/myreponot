package com.areenax.app.core.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/*
 * Shadow / press-feedback utilities replicating the web shadow utilities
 * (SPEC/04 §4). Compose shadow maps elevation dp → blur; the soft web values
 * map to small elevations with tinted ambient/spot colors:
 *   .card-shadow (0 4px 20px rgba(11,28,48,0.05)) → 3dp, navy-tinted
 *   .nav-shadow  (0 -4px 20px …)                  → 3dp on the nav container
 *   shadow-fab / primary CTA (blue glow)          → 8dp, primary-tinted
 */

/**
 * Universal card / appbar-button shadow (web `.card-shadow`).
 * Use a non-circle [shape] to match the element being lifted.
 */
fun Modifier.cardShadow(shape: Shape = CircleShape): Modifier = shadow(
    elevation = 3.dp,
    shape = shape,
    clip = false,
    ambientColor = Color(0x1A0B1C30), // 10% alpha
    spotColor = Color(0x260B1C30),    // 15% alpha
)

/** Blue-tinted glow for SupportFab / primary CTAs (rgba(0,74,198,0.25–0.3)). */
fun Modifier.primaryGlow(shape: Shape = CircleShape): Modifier = shadow(
    elevation = 8.dp,
    shape = shape,
    clip = false,
    ambientColor = Color(0x40004AC6),
    spotColor = Color(0x59004AC6),
)

/** BottomNav shadow (web `.nav-shadow` — a top lift). */
fun Modifier.navShadow(shape: Shape): Modifier = shadow(
    elevation = 3.dp,
    shape = shape,
    clip = false,
    ambientColor = Color(0x0D0B1C30),
    spotColor = Color(0x0D0B1C30),
)

/**
 * Web press feedback (`active:scale-95..0.98` on nearly every button).
 * Pass the SAME interactionSource given to the clickable.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    this.scale(if (pressed) pressedScale else 1f)
}
