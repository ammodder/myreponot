package com.areenax.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.areenax.app.R
import com.areenax.app.core.theme.Type

/*
 * AmountPad — port of src/components/shared/AmountPad.tsx (SPEC/03 §10).
 * 3-col grid, gap-3, max-w 320dp; keys h-16 (64dp) rounded-2xl
 * bg-surface-container-lowest + outline-variant/30 border + subtle shadow;
 * digits headline-lg; backspace = ic_backspace muted; order 1-9, ".", 0, backspace.
 *
 * The APPEND RULES (max 8 significant digits, single ".") are exposed in
 * [appendAmountKey] so screens apply them on every key — same as the web.
 */

/** Keys in visual order. */
val AMOUNT_PAD_KEYS = listOf(
    "1", "2", "3",
    "4", "5", "6",
    "7", "8", "9",
    ".", "0", "backspace",
)

/**
 * Append rules shared by deposit/withdraw/transfer screens:
 * - digits only + a single "." separator;
 * - max 8 significant digits before the decimal;
 * - max 2 decimals after the ".";
 * - "." on empty inserts "0.".
 */
fun appendAmountKey(current: String, key: String): String {
    return when (key) {
        "backspace" -> if (current.isNotEmpty()) current.dropLast(1) else current
        "." -> when {
            current.contains('.') -> current
            current.isEmpty() -> "0."
            else -> "$current."
        }
        else -> {
            if (!key.all { it.isDigit() }) return current
            val hasDot = current.contains('.')
            if (hasDot) {
                val decimals = current.substringAfter('.').length
                if (decimals >= 2) current else current + key
            } else {
                val whole = current.replace(".", "")
                if (whole.length >= 8) current else if (current == "0") key else current + key
            }
        }
    }
}

@Composable
fun AmountPad(onKey: (key: String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in AMOUNT_PAD_KEYS.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (key in row) {
                    AmountKey(key = key, onClick = { onKey(key) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AmountKey(key: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(64.dp)
            .pressScale(interaction, pressedScale = 0.95f)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (key == "backspace") {
            Icon(
                painter = painterResource(R.drawable.ic_backspace),
                contentDescription = "Backspace",
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = key,
                style = Type.headlineLg,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}
