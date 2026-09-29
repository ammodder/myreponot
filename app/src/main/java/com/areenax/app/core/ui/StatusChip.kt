package com.areenax.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.R
import com.areenax.app.core.theme.Type

/*
 * StatusChip — port of src/components/shared/StatusChip.tsx (SPEC/03 §4).
 *   PENDING          → bg-surface-container-high / on-surface-variant, "Pending",  schedule
 *   REJECTED/FAILED  → bg-error/10 / error,                         "Rejected", close
 *   else             → bg-secondary/15 / secondary,                 "Completed", check
 * Used by DepositSuccess / WithdrawSuccess (PENDING, optionally enlarged).
 */

@Composable
fun StatusChip(status: String, modifier: Modifier = Modifier, enlarged: Boolean = false) {
    val pending = status.equals("PENDING", ignoreCase = true)
    val failed = status.equals("REJECTED", true) || status.equals("FAILED", true)
    val (bg, fg, icon, label) = when {
        pending -> Tetrad(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
            R.drawable.ic_schedule,
            "Pending",
        )
        failed -> Tetrad(
            MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
            MaterialTheme.colorScheme.error,
            R.drawable.ic_close,
            "Rejected",
        )
        else -> Tetrad(
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.secondary,
            R.drawable.ic_check,
            "Completed",
        )
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(bg)
            .padding(horizontal = if (enlarged) 14.dp else 10.dp, vertical = if (enlarged) 6.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(if (enlarged) 20.dp else 14.dp),
            tint = fg,
        )
        Text(
            text = label,
            style = Type.labelSm.copy(
                fontSize = if (enlarged) 14.sp else 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = fg,
        )
    }
}

private data class Tetrad(val bg: androidx.compose.ui.graphics.Color, val fg: androidx.compose.ui.graphics.Color, val icon: Int, val label: String)
