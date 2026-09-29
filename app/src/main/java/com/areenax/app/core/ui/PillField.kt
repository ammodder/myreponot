package com.areenax.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.alpha
import com.areenax.app.core.theme.AreenaxShapes
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors

/**
 * THE AREENAX input field — owner field concept (dark mode), applied to every
 * text input in the app.
 *
 * States (dark theme):
 *  - **Idle**   : matte dark-charcoal pill, NO cursor, clean pure-white outline
 *                 leading icon, no ring.
 *  - **Focused**: the icon smoothly animates white → the app's vibrant blue
 *                 (`colorScheme.primary` = #2F6BFF), a bright-white blinking
 *                 cursor appears right beside the icon, text renders in the
 *                 soft light-white on-surface ink, and a crisp subtle focus
 *                 ring (primary @ 55% alpha, 1dp) wraps the charcoal pill.
 *  - **Blurred**: everything animates back (300ms FastOutSlowIn — the app-wide
 *                 animation standard from Checkpoint-1 decision Q10).
 *
 * Light theme keeps the existing web-parity look (lavender container,
 * outline-variant hairline → primary border) — the owner scoped the new
 * concept to dark mode only.
 *
 * Performance: plain [BasicTextField] (no Material3 field chrome), two
 * `animateColorAsState` tweens that run only during the 300ms transition,
 * zero per-frame allocations. Behavior (keyboard type, IME action, input
 * filtering, transformations, focus requesters) is unchanged — this is a
 * pure visual component.
 */
@Immutable
class AreenaxFieldColors(
    val container: Color,
    val text: Color,
    val placeholder: Color,
    /** Leading-icon tint — already animated (white → vibrant blue). */
    val icon: Color,
    /** Cursor brush color — bright white in dark mode. Blinking is the platform default. */
    val cursor: Color,
    /** 1dp border color — already animated (transparent → subtle blue ring). */
    val ring: Color,
)

/**
 * Focus-aware visual state for every AREENAX input (pills AND multiline
 * areas). Pass the field's current focus; the returned colors are animated.
 */
@Composable
fun areenaxFieldColors(focused: Boolean, isError: Boolean = false): AreenaxFieldColors {
    val extended = areenaColors()
    val colors = MaterialTheme.colorScheme
    val dark = extended.isDark
    val accent = colors.primary
    val spec = tween<Color>(durationMillis = 300, easing = FastOutSlowInEasing)

    val icon by animateColorAsState(
        targetValue = when {
            isError -> colors.error
            focused -> accent
            dark -> Color.White
            else -> colors.onSurfaceVariant
        },
        animationSpec = spec,
        label = "aaxFieldIcon",
    )
    val ring by animateColorAsState(
        targetValue = when {
            isError -> colors.error
            focused -> if (dark) accent.copy(alpha = 0.55f) else accent
            dark -> Color.Transparent
            else -> colors.outlineVariant
        },
        animationSpec = spec,
        label = "aaxFieldRing",
    )
    return AreenaxFieldColors(
        container = if (dark) colors.surfaceContainerHighest else extended.surfaceContainerLavender,
        text = colors.onSurface,
        placeholder = colors.onSurfaceVariant,
        icon = icon,
        cursor = if (dark) Color.White else accent,
        ring = ring,
    )
}

/**
 * The universal AREENAX pill text input. Single source of truth for every
 * single-line text field in the app (auth, wallet, admin, social, sheets).
 *
 * @param leadingIcon Material-Symbol name from the bundled icon set
 *   (see [AreenaxIcon]); renders pure-white idle, animates to vibrant blue
 *   on focus. Use `null` only for fields that genuinely have no icon slot.
 * @param onAction invoked on IME Next/Done unless [keyboardActions] overrides.
 */
@Composable
fun AreenaxPillField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: String? = null,
    iconSize: Dp = 20.dp,
    textStyle: TextStyle = Type.bodyLg,
    height: Dp = 48.dp,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onAction: (() -> Unit)? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Unspecified,
    keyboardActions: KeyboardActions? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    focusRequester: FocusRequester? = null,
    containerOverride: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val fieldColors = areenaxFieldColors(focused = focused, isError = isError)
    val pill = AreenaxShapes.Pill

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(pill)
            .background(containerOverride ?: fieldColors.container, pill)
            .border(1.dp, fieldColors.ring, pill)
            .onFocusChanged { focused = it.hasFocus }
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                },
            )
            .alpha(if (enabled) 1f else 0.6f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Spacer(Modifier.width(16.dp))
            AreenaxIcon(
                name = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = fieldColors.icon,
            )
            Spacer(Modifier.width(12.dp))
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty() && !placeholder.isNullOrEmpty()) {
                Text(
                    text = placeholder,
                    style = textStyle,
                    color = fieldColors.placeholder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
                textStyle = textStyle.copy(color = fieldColors.text),
                cursorBrush = SolidColor(fieldColors.cursor),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = imeAction,
                    capitalization = capitalization,
                ),
                keyboardActions = keyboardActions
                    ?: KeyboardActions(
                        onNext = { onAction?.invoke() },
                        onDone = { onAction?.invoke() },
                    ),
                visualTransformation = visualTransformation,
            )
        }
        if (trailing != null) {
            Row(
                modifier = Modifier.padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                trailing()
            }
        } else {
            Spacer(Modifier.width(16.dp))
        }
    }
}
