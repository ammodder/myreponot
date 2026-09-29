package com.areenax.app.core.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.areenax.app.core.util.countdownText
import kotlinx.coroutines.delay

/**
 * A7-02: per-second "now" that lives INSIDE the leaf composable that displays
 * it. The two tournament-detail screens used to own a screen-scope 1 s tick,
 * which recomposed the entire (1,200-1,700 line) screen every second —
 * participants list included. Time-dependent SCREEN-level state now refreshes
 * on a slower cadence; only the countdown leaf re-renders per second.
 */
@Composable
fun rememberLiveNow(intervalMs: Long = 1_000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMs) {
        while (true) {
            delay(intervalMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/**
 * A7-02 leaf: countdown text that re-renders alone every second.
 * [startTimeIso] is the tournament's ISO start timestamp (the web's
 * `startTime` string — countdownText parses it, exactly like the screens did).
 */
@Composable
fun LiveCountdownText(
    startTimeIso: String?,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    intervalMs: Long = 1_000L,
) {
    val now = rememberLiveNow(intervalMs)
    val text = remember(startTimeIso, now) { countdownText(startTimeIso) }
    Text(text = text, style = style, color = color, modifier = modifier, fontWeight = fontWeight)
}
