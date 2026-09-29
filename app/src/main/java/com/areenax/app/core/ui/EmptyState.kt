package com.areenax.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.areenax.app.core.theme.Type

/*
 * EmptyState — port of src/components/shared/EmptyState.tsx (SPEC/03 §3).
 * Centered column (-mt-20 on web → offset here), min-h 300px: 80dp circle
 * bg-surface-container-low with the outlined Material symbol 40dp text-primary
 * + message (on-surface-variant, font-medium, centered).
 * Every screen passes its EXACT copy — do not reword (01-SCREENS.md).
 */

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: String = "trophy",
    /** Optional per-screen circle override (e.g. bg-balance-chip, primary-fixed tints). */
    circleColor: androidx.compose.ui.graphics.Color? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 300.dp)
            .offset(y = (-80).dp)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(circleColor ?: MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            AreenaxIcon(
                name = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = Type.bodyLg,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
