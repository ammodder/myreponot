package com.areenax.app.ui.screens.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.AppBarMode
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AreenaxSpinner
import com.areenax.app.core.ui.BellButton
import com.areenax.app.core.ui.EmptyState
import com.areenax.app.core.util.formatCompact
import com.areenax.app.core.util.formatMoney
import com.areenax.app.data.TournamentEntry
import com.areenax.app.data.TournamentResultsResponse
import androidx.compose.foundation.layout.RowScope

/**
 * ResultsScreen — key `results` (SPEC/01 B5). 1:1 port of
 * src/components/screens/main/ResultsScreen.tsx + results.html (no podium — omitted
 * by design on the web too). AppBar page mode ("Tournament Results" + default bell).
 *
 * Params in: {tournamentId}. API: GET /tournaments/:id/results → {tournament, stats}.
 * 403 for non-participants → treated as failure. Body (px-4 py-6 pb-32 space-y-6):
 *   1. Match Statistics card (bar_chart): grid 3 cols when totalPrize>0 else 2, with
 *      vertical hairlines — "Prize Pool" Rs. «compact» (Intl compact, 1 decimal),
 *      "Players", "Top Kills" (primary).
 *   2. Winners list — sorted by rank (null→999) then kills desc; 64dp rows: rank
 *      (primary, w-10), 40dp avatar (image else initial circle), gameName +
 *      "«kills» Kills", right "Rs. «prize»" only when >0. The caller's row (isMe)
 *      tinted bg-primary-fixed/40.
 * Empty/failed/no-data → EmptyState "Results not published yet." (icon trophy).
 * Sticky bottom bar: primary "Share Results" (share icon) → native share sheet of
 * "«name» results on AREENAX" (clipboard fallback toast "Results copied to clipboard").
 */

/** Web primary-fixed tone for the isMe row tint bg-primary-fixed/40 (dark: #182338). */
@Composable
private fun primaryFixedTone(): Color =
    areenaColors().primaryFixed // A8-06

@Composable
fun ResultsScreen(env: NavEnv) {
    val tournamentId = env.params["tournamentId"] as? String
    val user by env.session.user.collectAsState()

    var data by remember { mutableStateOf<TournamentResultsResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(tournamentId) {
        when (val res = safeCall { env.api.tournamentResults(tournamentId ?: "") }) {
            is ApiResult.Success -> data = res.data
            else -> failed = true
        }
        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AreenaxSpinner(size = 32, strokeWidth = 2)
        }
        return
    }

    val tournament = data?.tournament
    val stats = data?.stats
    val failedState = failed || tournament == null || stats == null

    // Winners — sorted by rank (null→999) then kills desc
    val played: List<TournamentEntry> = remember(data) {
        (tournament?.entries ?: emptyList()).sortedWith(
            compareBy<TournamentEntry> { it.rank ?: 999 }.thenByDescending { it.kills },
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Unified page header — back + centered title + standard bell
            AppBar(mode = AppBarMode.Page, title = "Tournament Results", right = { BellButton() })

            if (failedState) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    EmptyState(message = "Results not published yet.", icon = "trophy")
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                        .padding(bottom = 104.dp), // pb-32
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    // ---- Match Statistics Card --------------------------------
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        ),
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AreenaxIcon(
                                    name = "bar_chart",
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    "Match Statistics",
                                    style = Type.labelLg,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            val s = stats!!
                            val showPrize = s.totalPrize > 0
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                if (showPrize) {
                                    StatCell(label = "Prize Pool", value = "Rs. ${formatCompact(s.totalPrize)}")
                                    VerticalDivider()
                                }
                                StatCell(label = "Players", value = "${s.players}")
                                VerticalDivider()
                                StatCell(
                                    label = "Top Kills",
                                    value = "${s.topKills}",
                                    valueColor = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    // ---- Winners list ------------------------------------------
                    Column {
                        Text(
                            "Winners",
                            style = Type.headlineMd,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(16.dp))
                        if (played.isEmpty()) {
                            EmptyState(message = "Results not published yet.", icon = "trophy")
                        } else {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                ),
                                shadowElevation = 4.dp,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column {
                                    played.forEachIndexed { index, entry ->
                                        val isMe = entry.user?.id == user?.id
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(64.dp)
                                                .background(
                                                    if (isMe) primaryFixedTone().copy(alpha = 0.4f)
                                                    else Color.Transparent,
                                                )
                                                .padding(horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = (entry.rank ?: (index + 1)).toString(),
                                                style = Type.labelLg,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.width(40.dp),
                                                textAlign = TextAlign.Center,
                                            )
                                            Box(
                                                Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        areenaColors().surfaceContainerHighLavender,
                                                    ),
                                            ) {
                                                if (!entry.user?.avatar.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = entry.user?.avatar,
                                                        contentDescription = entry.user?.gameName,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize(),
                                                    )
                                                } else {
                                                    Box(
                                                        Modifier
                                                            .fillMaxSize()
                                                            .background(MaterialTheme.colorScheme.primary),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Text(
                                                            text = (entry.user?.gameName ?: "?")
                                                                .take(1).uppercase(),
                                                            style = Type.labelLg.copy(
                                                                fontWeight = FontWeight.Bold,
                                                            ),
                                                            color = MaterialTheme.colorScheme.onPrimary,
                                                        )
                                                    }
                                                }
                                            }
                                            Column(
                                                Modifier
                                                    .weight(1f)
                                                    .padding(start = 4.dp),
                                            ) {
                                                Text(
                                                    text = entry.user?.gameName ?: "Player",
                                                    style = Type.labelLg,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = "${entry.kills} Kills",
                                                    style = Type.labelMd,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            if (entry.prize > 0) {
                                                Text(
                                                    text = "Rs. ${formatMoney(entry.prize)}",
                                                    style = Type.labelLg.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                        }
                                        if (index < played.lastIndex) {
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(1.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.outlineVariant
                                                            .copy(alpha = 0.2f),
                                                    ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sticky bottom action — only when the results page has data (web parity)
        if (!failedState) {
            val handleShare = {
                val text = "${tournament?.name ?: "Tournament"} results on AREENAX"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    // env.context is the APPLICATION context — needs the new-task flag
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    env.context.startActivity(Intent.createChooser(intent, "AREENAX Results"))
                } catch (_: Exception) {
                    try {
                        val cm =
                            env.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("AREENAX", text))
                        env.toast("Results copied to clipboard")
                    } catch (_: Exception) {
                        // clipboard unavailable
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.4f to areenaColors().surfaceLavender,
                            1f to areenaColors().surfaceLavender,
                        ),
                    )
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .padding(bottom = 16.dp), // pb-8
                contentAlignment = Alignment.Center,
            ) {
                Button(
                    onClick = { handleShare() },
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    AreenaxIcon(name = "share", contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share Results", style = Type.labelLg)
                }
            }
        }
    }
}

/** Centered statistics cell (label-md muted over a 20/26 semibold value). */
@Composable
private fun RowScope.StatCell(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        Modifier
            .weight(1f)
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = Type.labelMd,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = Type.pageTitle,
            color = valueColor,
        )
    }
}

/** Web divide-x hairline between the statistics cells. */
@Composable
private fun RowScope.VerticalDivider() {
    Box(
        Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
    )
}
