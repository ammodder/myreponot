package com.areenax.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.AreenaxShapes
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.AppBarCircleButton
import com.areenax.app.core.ui.AppBarMode
import com.areenax.app.core.ui.AreenaxSpinner
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.cardShadow
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.util.formatMoney
import com.areenax.app.data.Achievement
import com.areenax.app.data.AchievementsResponse

/**
 * AchievementsScreen — key `achievements` (SPEC/01 E5). Exact port of
 * `src/components/screens/profile/AchievementsScreen.tsx`:
 *
 * - GET /achievements → summary card ("«percent»% Completed" +
 *   "«unlocked» of «total» Unlocked" + the 64dp SVG progress ring → Compose
 *   Canvas arc with a filled emoji_events center), horizontally scrolling
 *   category chips (All/Tournaments/Kills/Earnings/Social) and achievement
 *   cards (unlocked = check_circle + emerald glow blob; in-progress = task
 *   icon + progress bar with "progress/target"; locked = lock + "Locked"
 *   chip, 70% opacity).
 * - Tap a card → the detail bottom sheet (rounded-t-[1.75rem],
 *   bg-surface-container): big icon tile, title/description, Progress bar +
 *   "progress/target", Reward "Rs «formatMoney»", status chip, close X.
 * - States: loading spinner; category empty → "No achievements in this
 *   category yet."; footer caption. Error toast "Failed to load achievements".
 */
@Composable
fun AchievementsScreen(env: NavEnv) {
    var achievements by remember { mutableStateOf<List<Achievement>>(emptyList()) }
    var unlockedCount by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf("All") }
    var selected by remember { mutableStateOf<Achievement?>(null) }

    LaunchedEffect(Unit) {
        when (val res = safeCall<AchievementsResponse> { env.api.achievements() }) {
            is ApiResult.Success -> {
                achievements = res.data.achievements
                unlockedCount = res.data.unlockedCount
                total = res.data.total
            }
            is ApiResult.Error -> env.toast.show(
                title = "Failed to load achievements",
                description = res.message,
                variant = ToastVariant.Destructive,
            )
            is ApiResult.NetworkError -> env.toast.show(
                title = "Failed to load achievements",
                description = res.message,
                variant = ToastVariant.Destructive,
            )
        }
        loading = false
    }

    val percent = if (total > 0) ((unlockedCount.toFloat() / total) * 100).toInt() else 0
    val filtered = if (filter == "All") achievements else achievements.filter { it.category == filter }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp),
    ) {
        AppBar(mode = AppBarMode.Page, title = "Achievements")

        if (loading) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                contentAlignment = Alignment.Center,
            ) {
                AreenaxSpinner(size = 32)
            }
        } else {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // --------------------------------------------- summary card
                Box(Modifier.fillMaxWidth()) {
                    // decorative -right-10 -top-10 w-32 h-32 primary-fixed/40 blur-2xl blob
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 40.dp, y = (-40).dp)
                            .size(128.dp)
                            .clip(CircleShape)
                            .blur(20.dp)
                            .background(areenaColors().primaryFixed.copy(alpha = 0.4f)) // A8-06,
                    )
                    Surface(
                        shape = AreenaxShapes.Card,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .cardShadow(AreenaxShapes.Card),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "$percent% Completed",
                                    style = Type.headlineLgMobile,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "$unlockedCount of $total Unlocked",
                                    style = Type.bodyMd,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            // 64dp circular progress ring (SVG r=16/stroke 4 → Canvas arc)
                            Box(contentAlignment = Alignment.Center) {
                                val ringColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                val progressColor = MaterialTheme.colorScheme.primary
                                Canvas(modifier = Modifier.size(64.dp)) {
                                    val stroke = 7f
                                    val inset = stroke / 2
                                    val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                                    drawArc(
                                        color = ringColor,
                                        startAngle = 0f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        topLeft = Offset(inset, inset),
                                        size = arcSize,
                                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                                    )
                                    drawArc(
                                        color = progressColor,
                                        startAngle = -90f,
                                        sweepAngle = 360f * (percent / 100f),
                                        useCenter = false,
                                        topLeft = Offset(inset, inset),
                                        size = arcSize,
                                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                                    )
                                }
                                AreenaxIcon(
                                    name = "emoji_events",
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    filled = true,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                // --------------------------------------------- category chips
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "All" to "All",
                        "Tournaments" to "TOURNAMENTS",
                        "Kills" to "KILLS",
                        "Earnings" to "EARNINGS",
                        "Social" to "SOCIAL",
                    ).forEach { (label, value) ->
                        val active = filter == value
                        val ext = areenaColors()
                        val bg = when {
                            active && ext.isDark -> Color.White
                            active -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                        val fg = when {
                            active && ext.isDark -> Color.Black
                            active -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(
                            onClick = { filter = value },
                            shape = CircleShape,
                            color = bg,
                            shadowElevation = 1.dp,
                            modifier = Modifier.pressScale(remember { MutableInteractionSource() }, 0.97f),
                        ) {
                            Text(
                                text = label,
                                style = Type.labelSm,
                                color = fg,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            )
                        }
                    }
                }

                // --------------------------------------------- achievement list
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (filtered.isEmpty()) {
                        Text(
                            text = "No achievements in this category yet.",
                            style = Type.bodyMd,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    } else {
                        filtered.forEach { a ->
                            AchievementCard(a) { selected = a }
                        }
                    }
                }

                // --------------------------------------------- footer caption
                Text(
                    text = "Tap on any achievement card to view full details and reward eligibility.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }

    // Achievement detail bottom sheet (AnimatePresence-equivalent via ModalBottomSheet)
    selected?.let { current ->
        AchievementDetailSheet(a = current, onClose = { selected = null })
    }
}

// ------------------------------------------------------------------ cards

private fun achievementPct(a: Achievement): Int =
    if (a.target > 0) ((a.progress.toFloat() / a.target) * 100).toInt().coerceAtMost(100) else 0

@Composable
private fun AchievementCard(a: Achievement, onSelect: (Achievement) -> Unit) {
    val pct = achievementPct(a)
    val inProgress = !a.unlocked && a.progress > 0
    val interaction = remember { MutableInteractionSource() }

    Box(Modifier.fillMaxWidth()) {
        if (a.unlocked) {
            // subtle success glow: -right-8 -bottom-8 w-24 h-24 secondary-container/20 blur-xl
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 32.dp, y = 32.dp)
                    .size(96.dp)
                    .clip(CircleShape)
                    .blur(20.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)),
            )
        }
        Surface(
            onClick = { onSelect(a) },
            interactionSource = interaction,
            shape = AreenaxShapes.Card,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (!a.unlocked && !inProgress) 0.7f else 1f)
                .cardShadow(AreenaxShapes.Card)
                .pressScale(interaction, 0.98f),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // icon tile — same tinting rules as the web
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            when {
                                a.unlocked -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                inProgress -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        a.unlocked -> AreenaxIcon(
                            name = "check_circle", contentDescription = null,
                            modifier = Modifier.size(24.dp), filled = true,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        inProgress -> AreenaxIcon(
                            name = a.icon, contentDescription = null,
                            modifier = Modifier.size(24.dp), filled = true,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        else -> AreenaxIcon(
                            name = "lock", contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = a.title,
                            style = Type.bodyLg,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        AreenaxIcon(
                            name = "chevron_right",
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = a.description,
                        style = Type.bodyMd,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = if (inProgress) 12.dp else 8.dp),
                    )
                    if (inProgress) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(pct / 100f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(percent = 50))
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                            Text(
                                text = "${a.progress}/${a.target}",
                                style = Type.labelSm,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        Row {
                            Surface(shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    text = if (a.unlocked) "Unlocked" else "Locked",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (a.unlocked) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .background(
                                            if (a.unlocked) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        )
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** rounded-t-[1.75rem] bg-surface-container detail sheet (Escape-close = back/scrim). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AchievementDetailSheet(a: Achievement, onClose: () -> Unit) {
    val pct = achievementPct(a)
    val inProgress = !a.unlocked && a.progress > 0
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // big icon tile — same tinting logic as the cards
                Box(
                    Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            when {
                                a.unlocked -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                inProgress -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    AreenaxIcon(
                        name = if (a.unlocked || inProgress) a.icon else "lock",
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        filled = a.unlocked || inProgress,
                        tint = when {
                            a.unlocked -> MaterialTheme.colorScheme.onSecondaryContainer
                            inProgress -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Column(Modifier.padding(end = 48.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = a.title,
                        style = Type.headlineMd,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = a.description,
                        style = Type.bodyMd,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Progress
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Progress",
                            style = Type.labelSm,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${a.progress}/${a.target}",
                            style = Type.labelSm,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(pct / 100f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                // Reward + status chip
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Reward",
                            style = Type.labelSm,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Rs ${formatMoney(a.reward)}",
                            style = Type.headlineMd,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val (chipBg, chipFg, chipText) = when {
                        a.unlocked -> Triple(
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.secondary,
                            "Unlocked",
                        )
                        inProgress -> Triple(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.primary,
                            "In Progress",
                        )
                        else -> Triple(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            "Locked",
                        )
                    }
                    Surface(shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = chipText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = chipFg,
                            modifier = Modifier
                                .background(chipBg)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            // Close (top-right) — AppBarIconButton equivalent
            Box(Modifier.align(Alignment.TopEnd)) {
                AppBarCircleButton(onClick = onClose) {
                    AreenaxIcon(
                        name = "close",
                        contentDescription = "Close",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
