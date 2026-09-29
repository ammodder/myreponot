package com.areenax.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.areenax.app.R
import com.areenax.app.core.theme.Type

/**
 * Screen-builder placeholder — shown by every stub screen in
 * ui/screens/... until its native build replaces the body.
 * Centered: big spinner + the screen key + "Waiting for native build".
 */
@Composable
fun PlaceholderScreen(screenKey: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_hourglass_top),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = screenKey,
            style = Type.headlineMd,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Waiting for native build",
            style = Type.bodyMd,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The web loading spinner: 2rem circle, 2px primary border with a transparent
 * arc. Used by every screen's loading state.
 */
@Composable
fun AreenaxSpinner(modifier: Modifier = Modifier, size: Int = 32, strokeWidth: Int = 2) {
    CircularProgressIndicator(
        modifier = modifier.size(size.dp),
        strokeWidth = strokeWidth.dp,
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainer,
    )
}
