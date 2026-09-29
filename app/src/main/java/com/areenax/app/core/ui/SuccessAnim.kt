package com.areenax.app.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.areenax.app.R
import com.areenax.app.core.theme.Type

/*
 * SuccessAnim — port of src/components/shared/SuccessAnim.tsx (SPEC/03 §5).
 * Glowing check badge: radial primary glow (112px blur), tint ring
 * (bg-primary/15 border-primary/20, p-16, rounded-full) around a 48dp
 * bg-primary circle with a white filled check; title 26sp bold; subtitle
 * 14sp muted max-w 290dp. Spring-scale entrance mirrors the CSS keyframe.
 */

@Composable
fun SuccessAnim(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    children: @Composable () -> Unit = {},
) {
    var entered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.6f,
        animationSpec = tween(420),
        label = "successScale",
    )
    LaunchedEffect(Unit) { entered = true }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Radial primary glow (web bg-primary/25 blur-2xl circle)
            Box(
                Modifier
                    .size(112.dp)
                    .scale(scale)
                    .blur(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            )
            // Tint ring + primary circle + white check
            Box(
                Modifier
                    .size(84.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check_fill),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = Color.White,
                    )
                }
            }
        }
        Text(
            text = title,
            style = Type.successTitle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = Type.bodyMd,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .alpha(0.9f)
                    .fillMaxWidth()
                    .padding(horizontal = 45.dp),
            )
        }
        children()
    }
}
