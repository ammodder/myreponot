package com.areenax.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.Type
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.AppBarMode
import com.areenax.app.core.ui.AreenaxSpinner
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.cardShadow
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.util.formatMoney
import com.areenax.app.data.StatsResponse

/**
 * MyStatsScreen — key `myStats` (SPEC/01 E3). Exact port of
 * `src/components/screens/profile/MyStatsScreen.tsx`:
 *
 * - GET /stats → hero Win Rate card ("«wins» Wins out of «totalMatches»
 *   Total Tournaments", h-3 progress fill + "«winRate»%" label), 2×2 stats
 *   grid (Total Matches / Total Wins / Total Kills / Total Earnings) and the
 *   whole-card-tappable Leaderboard card (gold military_tech medal,
 *   "Leaderboard #«rank»", "Times ranked Top 1 globally", "«top3» Times" chip
 *   + chevron → leaderboard).
 * - Error → toast "Failed to load stats" + the request message.
 */
@Composable
fun MyStatsScreen(env: NavEnv) {
    var stats by remember { mutableStateOf<com.areenax.app.data.MyStats?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        when (val res = safeCall<StatsResponse> { env.api.stats() }) {
            is ApiResult.Success -> stats = res.data.stats
            is ApiResult.Error -> env.toast.show(
                title = "Failed to load stats",
                description = res.message,
                variant = ToastVariant.Destructive,
            )
            is ApiResult.NetworkError -> env.toast.show(
                title = "Failed to load stats",
                description = res.message,
                variant = ToastVariant.Destructive,
            )
        }
        loading = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp),
    ) {
        AppBar(mode = AppBarMode.Page, title = "My Stats")

        if (loading) {
            // min-h-[60vh] centered spinner
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                contentAlignment = Alignment.Center,
            ) {
                AreenaxSpinner(size = 32)
            }
        } else {
            val s = stats
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ------------------------------------------------ hero win rate card
                StatsCard {
                    Column {
                        Text(
                            text = "Win Rate",
                            style = Type.headlineLgMobile,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${s?.wins ?: 0} Wins out of ${s?.totalMatches ?: 0} Total Tournaments",
                            style = Type.bodyMd,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        val fraction = ((s?.winRate ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
                        Box(
                            Modifier
                                .weight(1f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                        Text(
                            text = "${formatWinRate(s?.winRate)}%",
                            style = Type.labelSm,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ------------------------------------------------ 2×2 stats grid
                // (web renders the grid ONLY when stats resolved — tiles = stats ? […] : [])
                if (s != null) {
                    val tiles = listOf(
                        StatTile("sports_esports", "Total Matches", "${s.totalMatches}"),
                        StatTile("emoji_events", "Total Wins", "${s.wins}"),
                        StatTile("my_location", "Total Kills", "${s.kills}"),
                        StatTile("payments", "Total Earnings", "Rs ${formatMoney(s.earnings)}"),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        tiles.chunked(2).forEach { rowTiles ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                rowTiles.forEach { tile ->
                                    StatsCard(modifier = Modifier.weight(1f)) {
                                        StatTileContent(tile)
                                    }
                                }
                                if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                // ------------------------------------------------ leaderboard card
                val interaction = remember { MutableInteractionSource() }
                Surface(
                    onClick = { env.navigate(ScreenKeys.LEADERBOARD) },
                    interactionSource = interaction,
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .cardShadow(RoundedCornerShape(24.dp))
                        .pressScale(interaction, 0.98f),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                AreenaxIcon(
                                    name = "military_tech",
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = com.areenax.app.core.theme.areenaColors().goldMedal,
                                )
                            }
                            Column {
                                Text(
                                    text = "Leaderboard #${s?.leaderboardRank ?: "-"}",
                                    style = Type.bodyLg,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Times ranked Top 1 globally",
                                    style = Type.labelSm,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, // A9-01: was alpha 0.8 → ≈3.4:1
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = "${s?.top3 ?: 0} Times",
                                    style = Type.labelSm,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                            AreenaxIcon(
                                name = "chevron_right",
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** winRate renders like the web's `{n}%` (number as-is). */
private fun formatWinRate(v: Double?): String {
    val n = v ?: 0.0
    return if (n == Math.floor(n)) n.toInt().toString() else n.toString()
}

private data class StatTile(val icon: String, val label: String, val value: String)

/** rounded-2xl p-4 stat tile body (icon circle + label + headline value). */
@Composable
private fun StatTileContent(tile: StatTile) {
    val (circleBg, iconTint) = when (tile.icon) {
        "sports_esports" -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.primary
        "emoji_events" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f) to MaterialTheme.colorScheme.secondary
        "my_location" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f) to MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) to MaterialTheme.colorScheme.primary
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(circleBg),
            contentAlignment = Alignment.Center,
        ) {
            AreenaxIcon(name = tile.icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = iconTint)
        }
        Column {
            Text(
                text = tile.label,
                style = Type.bodyMd,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = tile.value,
                style = Type.headlineMd,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** rounded-3xl p-5 white card with hairline border + card shadow. */
@Composable
private fun StatsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .cardShadow(RoundedCornerShape(24.dp)),
    ) {
        Column(Modifier.padding(20.dp)) { content() }
    }
}
