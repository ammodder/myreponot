package com.areenax.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.R
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.AppBarMode
import com.areenax.app.core.ui.AreenaxSpinner
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.util.formatGroupedInt
import com.areenax.app.data.LeaderboardResponse
import com.areenax.app.data.LeaderboardRow
import com.areenax.app.data.StatsResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * LeaderboardScreen — key `leaderboard` (SPEC/01 E4). Exact port of
 * `src/components/screens/profile/LeaderboardScreen.tsx`:
 *
 * - Parallel GET /leaderboard + GET /stats (stats failure tolerated — used
 *   only for `myRank` when I'm not listed). Error → toast "Failed to load
 *   leaderboard".
 * - Podium: center #1 (112dp primary circle, border-4 primary, floating gold
 *   crown, "1" badge, points pill bg-primary/10) with #2 left / #3 right
 *   (80dp circles, surface-container-highest badges, secondary points).
 * - Ranked list card (rounded-t-[2rem]): rows for rank > 3 — rank number,
 *   40dp initial circle, gameName, grouped points; the `isMe` row is
 *   highlighted (4dp primary bar, bg-surface-container-low, "You") with no
 *   divider around it. When I'm NOT in the list: bottom "You" row with
 *   `myRank` from stats ("—" when unknown).
 * - States: loading spinner; empty → 80dp primary-fixed/60 circle +
 *   leaderboard icon + "No leaderboard data yet".
 */
@Composable
fun LeaderboardScreen(env: NavEnv) {
    val user by env.session.user.collectAsState()
    var rows by remember { mutableStateOf<List<LeaderboardRow>>(emptyList()) }
    var myRank by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            coroutineScope {
                val lbDef = async { safeCall<LeaderboardResponse> { env.api.leaderboard() } }
                val stDef = async { safeCall<StatsResponse> { env.api.stats() } }
                val lb = lbDef.await()
                val st = stDef.await()
                when (lb) {
                    is ApiResult.Success -> {
                        rows = lb.data.leaderboard
                        myRank = (st as? ApiResult.Success)?.data?.stats?.leaderboardRank
                    }
                    is ApiResult.Error -> env.toast.show(
                        title = "Failed to load leaderboard",
                        description = lb.message,
                        variant = ToastVariant.Destructive,
                    )
                    is ApiResult.NetworkError -> env.toast.show(
                        title = "Failed to load leaderboard",
                        description = lb.message,
                        variant = ToastVariant.Destructive,
                    )
                }
            }
        } finally {
            loading = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent),
    ) {
        // Ambient background blobs (blurred radial primary-container tints)
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = (-50).dp, y = (-50).dp)
                .size(300.dp)
                .blur(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = (-40).dp)
                .size(300.dp)
                .blur(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)),
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 80.dp),
        ) {
            AppBar(mode = AppBarMode.Page, title = "Leaderboard")

            if (loading) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AreenaxSpinner(size = 32)
                }
            } else if (rows.isEmpty()) {
                // Empty — custom web layout (NOT the shared EmptyState)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 140.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val ext = areenaColors()
                    Box(
                        Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                ext.primaryFixed.copy(alpha = 0.6f) // A8-06,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        AreenaxIcon(
                            name = "leaderboard",
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = "No leaderboard data yet",
                        style = Type.bodyLg,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else {
                val first = rows.getOrNull(0)
                val second = rows.getOrNull(1)
                val third = rows.getOrNull(2)
                val rest = rows.filter { it.rank > 3 }
                val meInList = rows.any { it.isMe == true }

                Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {

                    // ---------------------------------------------- podium
                    BoxWithConstraints(
                        Modifier
                            .fillMaxWidth()
                            .height(256.dp),
                    ) {
                        val sideInset = maxWidth * 0.05f
                        second?.let { row ->
                            Column(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .offset(x = sideInset),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                PodiumSide(row = row, rank = 2)
                            }
                        }
                        third?.let { row ->
                            Column(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = -sideInset),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                PodiumSide(row = row, rank = 3)
                            }
                        }
                        first?.let { row ->
                            Column(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // crown + 112dp circle + "1" badge
                                Box {
                                    // floating gold crown SVG (#F59E0B — inline drawable tinted)
                                    androidx.compose.material3.Icon(
                                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_inline_leaderboard_crown),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .offset(y = (-24).dp)
                                            .size(32.dp),
                                        tint = areenaColors().leaderboardCrown,
                                    )
                                    Box(
                                        Modifier
                                            .padding(top = 12.dp)
                                            .size(112.dp)
                                            .clip(CircleShape)
                                            .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = (row.gameName.takeIf { it.isNotEmpty() } ?: "?").first().uppercase(),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = Type.headlineLgMobile,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomCenter)
                                            .offset(y = 12.dp)
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "1",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(20.dp))
                                Text(
                                    text = row.gameName,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                                    Row(
                                        Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AreenaxIcon(
                                            name = "bolt",
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            filled = true,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = formatGroupedInt(row.points),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ---------------------------------------------- ranked list card
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 32.dp, topEnd = 32.dp, bottomStart = 24.dp, bottomEnd = 24.dp,
                        ),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 32.dp),
                    ) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            rest.forEachIndexed { i, row ->
                                ListRow(row)
                                val next = rest.getOrNull(i + 1)
                                if (next != null && next.isMe != true && row.isMe != true) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 64.dp)
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                    )
                                }
                            }

                            // "You" row — shown when I'm not in the top list
                            if (!meInList) {
                                MeRow(
                                    rankText = myRank?.toString() ?: "—",
                                    initial = (user?.gameName?.takeIf { it.isNotEmpty() } ?: "?").first().uppercase(),
                                    pointsText = myRank?.let { "#$it" } ?: "—",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ helpers

@Composable
private fun PodiumSide(row: LeaderboardRow, rank: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(
                Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .border(4.dp, MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (row.gameName.takeIf { it.isNotEmpty() } ?: "?").first().uppercase(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = Type.headlineMd,
                    fontWeight = FontWeight.Bold,
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 12.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = rank.toString(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = row.gameName,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(96.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AreenaxIcon(
                name = "bolt",
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                filled = true,
                tint = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = formatGroupedInt(row.points),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

/** Plain ranked row (rank > 3, not me). */
@Composable
private fun ListRow(row: LeaderboardRow) {
    if (row.isMe == true) {
        MeRow(
            rankText = row.rank.toString(),
            initial = (row.gameName.takeIf { it.isNotEmpty() } ?: "?").first().uppercase(),
            pointsText = formatGroupedInt(row.points),
        )
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.rank.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.width(16.dp))
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (row.gameName.takeIf { it.isNotEmpty() } ?: "?").first().uppercase(),
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = row.gameName,
            style = Type.bodyLg,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatGroupedInt(row.points),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The highlighted "You" row (4dp primary bar, bg-surface-container-low). */
@Composable
private fun MeRow(rankText: String, initial: String, pointsText: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(4.dp)
                .height(72.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                ),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = rankText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.width(16.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = "You",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = pointsText,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
