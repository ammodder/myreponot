package com.areenax.app.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.areenax.app.R
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.theme.DarkTxInIconBg
import com.areenax.app.core.theme.LightTxInIconBg
import kotlinx.coroutines.delay

/**
 * AREENAX toast system (web use-toast.ts + ui/toast.tsx, task-62 M3 restyle):
 *  - ONE toast at a time (TOAST_LIMIT 1 — newest replaces older);
 *  - auto-dismiss 5s with a shrinking 3dp timer bar;
 *  - slides in from the TOP edge (mobile), 0.45s spring-out curve; exit 0.3s;
 *  - `default`      = surface-container-lowest card, outline-variant border,
 *                     emerald check chip;
 *  - `destructive`  = M3 error-container / on-error-container + red error chip.
 * Do NOT use the platform Snackbar — copy must match.
 */

enum class ToastVariant { Default, Destructive }

/** One visible toast. `title` is the bold line; `description` optional. */
data class ToastData(
    val id: Long,
    val title: String,
    val description: String?,
    val variant: ToastVariant,
)

class ToastController {

    var current by mutableStateOf<ToastData?>(null)
        private set

    private var idCounter = 0L

    /** Show a toast for [durationMs] (web default 5s). Replaces any visible toast. */
    fun show(
        title: String,
        description: String? = null,
        variant: ToastVariant = ToastVariant.Default,
        durationMs: Long = 5000,
    ) {
        idCounter += 1
        current = ToastData(idCounter, title, description, variant)
        lastDurationMs = durationMs
    }

    fun dismiss() {
        current = null
    }

    /** Duration of the currently shown toast (kept alongside the data). */
    var lastDurationMs: Long = 5000L
        private set
}

/** Host rendering the single toast (top-anchored, above all screens). */
@Composable
fun ToastHost(controller: ToastController, modifier: Modifier = Modifier) {
    val toast = controller.current
    val durationMs = controller.lastDurationMs
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = toast != null,
            enter = slideInVertically(
                animationSpec = tween(450, easing = CubicBezierEasing(0.21f, 1.02f, 0.73f, 1f)),
                initialOffsetY = { -it - 64 },
            ) + fadeIn(animationSpec = tween(180)),
            exit = slideOutVertically(
                animationSpec = tween(300, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)),
                targetOffsetY = { -it - 64 },
            ) + fadeOut(animationSpec = tween(300)),
        ) {
            toast?.let { data ->
                ToastCard(data = data, durationMs = durationMs, onDismiss = { controller.dismiss() })
            }
        }
    }
}

@Composable
private fun ToastCard(data: ToastData, durationMs: Long, onDismiss: () -> Unit) {
    val destructive = data.variant == ToastVariant.Destructive
    val colorScheme = MaterialTheme.colorScheme
    val extended = areenaColors()

    // Timer bar (scaleX 1→0 over the toast duration). Restart key = toast id.
    var started by remember(data.id) { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (started) 0f else 1f,
        animationSpec = tween(durationMs.toInt(), easing = LinearEasing),
        label = "toastTimer",
    )
    LaunchedEffect(data.id) {
        started = true
        delay(durationMs)
        onDismiss()
    }

    Surface(
        modifier = Modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            // A9-03 (WCAG 4.1.3 Status Messages): transient toasts are announced
            // politely by accessibility services (runtime behavior NOT VERIFIED —
            // no device). The timer bar below stays decorative.
            .semantics {
                liveRegion = LiveRegionMode.Polite
                paneTitle = data.title
            },
        shape = RoundedCornerShape(16.dp),
        color = if (destructive) colorScheme.errorContainer else colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (destructive) {
                colorScheme.onErrorContainer.copy(alpha = 0.2f)
            } else {
                colorScheme.outlineVariant.copy(alpha = 0.6f)
            },
        ),
        shadowElevation = 12.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = if (destructive) {
                                colorScheme.error.copy(alpha = 0.14f)
                            } else if (extended.isDark) {
                                DarkTxInIconBg
                            } else {
                                LightTxInIconBg
                            },
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            if (destructive) R.drawable.ic_error else R.drawable.ic_check_circle_fill,
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (destructive) colorScheme.error else extended.txInIconFg,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = data.title,
                        style = Type.toastTitle,
                        color = if (destructive) colorScheme.onErrorContainer else colorScheme.onSurface,
                    )
                    if (!data.description.isNullOrBlank()) {
                        Text(
                            modifier = Modifier.alpha(0.9f),
                            text = data.description,
                            style = Type.toastBody,
                            color = if (destructive) colorScheme.onErrorContainer else colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(18.dp),
                        tint = if (destructive) colorScheme.onErrorContainer else colorScheme.onSurfaceVariant,
                    )
                }
            }
            // 3dp timer bar — bg-secondary/60 (default) or on-error-container/40.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.Transparent)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(
                            if (destructive) colorScheme.onErrorContainer.copy(alpha = 0.4f)
                            else colorScheme.secondary.copy(alpha = 0.6f)
                        )
                )
            }
        }
    }
}
