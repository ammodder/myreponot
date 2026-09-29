package com.areenax.app.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
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
import com.areenax.app.core.ui.RoomInfoSheet
import com.areenax.app.core.ui.SupportFab
import com.areenax.app.core.ui.TournamentCard
import com.areenax.app.core.util.TournamentCardActionKind
import com.areenax.app.data.Tournament

/**
 * TournamentsScreen — key `tournaments` (SPEC/01 B2). 1:1 port of
 * src/components/screens/main/TournamentsScreen.tsx + tournament.html.
 *
 * Params in: {gameId?}. AppBar tab mode ("Tournaments" + BalanceChip + bell).
 * Segmented status tabs in a bg-balance-chip pill — Upcoming / Ongoing / Completed —
 * each switch REFETCHES GET /tournaments?status=<tab>[&gameId=<id>]. The loaded
 * snapshot is kept per tab (a fetched tab renders instantly; an error only marks
 * THAT tab, exactly like the web `result`/`errorTab` pair). TournamentCard rows
 * (space-y-4):
 *   card tap  → tournamentDetails {tournamentId}
 *   room      → RoomInfoSheet (fresh credentials per open)
 *   results   → results {tournamentId}
 *   proof     → tournamentDetails {tournamentId}
 * Empty per-tab copy: "No upcoming tournaments." / "No ongoing tournaments." /
 * "No completed tournaments." — 80dp bg-balance-chip circle + outlined trophy.
 * Error → EmptyState "Failed to load tournaments." (icon error). SupportFab above the nav.
 */

/** Tab descriptors — keys + labels + EXACT per-tab empty copy (web TABS array). */
private val TOURNAMENT_TABS = listOf(
    Triple("UPCOMING", "Upcoming", "No upcoming tournaments."),
    Triple("ONGOING", "Ongoing", "No ongoing tournaments."),
    Triple("COMPLETED", "Completed", "No completed tournaments."),
)

/** A9: 30s TTL per-tab memory cache so quick revisits render instantly. */
private const val TOURNAMENTS_TTL_MS = 30_000L
private val tournamentsCacheMap = mutableMapOf<String, Pair<Long, List<Tournament>>>()

@Composable
fun TournamentsScreen(env: NavEnv) {
    val gameId = env.params["gameId"] as? String

    var tab by remember { mutableStateOf("UPCOMING") }
    val cacheKey = "${gameId ?: "all"}_$tab"
    val cached = tournamentsCacheMap[cacheKey]
    val isFresh = cached != null && (System.currentTimeMillis() - cached.first < TOURNAMENTS_TTL_MS)

    // Loaded snapshot + the tab it belongs to.
    var resultTab by remember { mutableStateOf<String?>(if (isFresh) tab else null) }
    var resultTournaments by remember { mutableStateOf<List<Tournament>?>(cached?.second) }
    var errorTab by remember { mutableStateOf<String?>(null) }
    var roomTournament by remember { mutableStateOf<Tournament?>(null) }

    LaunchedEffect(tab, gameId) {
        val key = "${gameId ?: "all"}_$tab"
        val entry = tournamentsCacheMap[key]
        if (entry != null && System.currentTimeMillis() - entry.first < TOURNAMENTS_TTL_MS) {
            resultTab = tab
            resultTournaments = entry.second
        }
        when (val res = safeCall { env.api.tournaments(status = tab, gameId = gameId) }) {
            is ApiResult.Success -> {
                resultTab = tab
                resultTournaments = res.data.tournaments
                tournamentsCacheMap[key] = System.currentTimeMillis() to res.data.tournaments
            }
            else -> {
                if (tournamentsCacheMap[key] == null) {
                    errorTab = tab
                }
            }
        }
    }

    val activeTab = TOURNAMENT_TABS.firstOrNull { it.first == tab }
    val error = errorTab == tab
    val tournaments = if (resultTab == tab) resultTournaments else null
    val loading = tournaments == null && !error

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            AppBar(mode = AppBarMode.Tab, title = "Tournaments", balance = true, right = { BellButton() })

            // ---- Main (p-4 pb-24) ------------------------------------------------
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 96.dp),
            ) {
                Spacer(Modifier.height(16.dp))

                // ---- Status tabs (bg-balance-chip pill) --------------------------
                StatusTabs(
                    selected = tab,
                    onSelect = { tab = it },
                )
                Spacer(Modifier.height(32.dp)) // mb-8

                when {
                    loading -> Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AreenaxSpinner(size = 32, strokeWidth = 2)
                    }

                    error -> EmptyState(message = "Failed to load tournaments.", icon = "error")

                    tournaments.isNullOrEmpty() ->
                        // Empty State — exact tournament.html markup
                        TournamentsEmptyState(
                            message = activeTab?.third ?: "No tournaments found.",
                        )

                    else -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        tournaments.forEach { t ->
                            TournamentCard(
                                tournament = t,
                                joined = t.joined ?: false,
                                onClick = {
                                    env.navigate(
                                        ScreenKeys.TOURNAMENT_DETAILS,
                                        mapOf("tournamentId" to t.id),
                                    )
                                },
                                onAction = { action ->
                                    when (action.kind) {
                                        TournamentCardActionKind.ROOM -> roomTournament = t
                                        TournamentCardActionKind.RESULTS ->
                                            env.navigate(ScreenKeys.RESULTS, mapOf("tournamentId" to t.id))
                                        TournamentCardActionKind.PROOF ->
                                            env.navigate(
                                                ScreenKeys.TOURNAMENT_DETAILS,
                                                mapOf("tournamentId" to t.id),
                                            )
                                        else -> Unit
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        // Support FAB — sits above the bottom nav (web bottom-24 right-6)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 20.dp),
        ) {
            SupportFab()
        }
    }

    // Room ID & Password popup — joined users only (credentials fetched per open)
    RoomInfoSheet(
        open = roomTournament != null,
        onClose = { roomTournament = null },
        tournamentId = roomTournament?.id,
        env = env,
        tournamentName = roomTournament?.name,
    )
}

/** Segmented pill tabs — active = white glass-tab pill + primary text (dark: white/black). */
@Composable
private fun StatusTabs(selected: String, onSelect: (String) -> Unit) {
    val isDark = areenaColors().isDark
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(percent = 50))
            .background(areenaColors().balanceChip)
            .padding(4.dp),
    ) {
        TOURNAMENT_TABS.forEach { (key, label, _) ->
            val active = key == selected
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(percent = 50))
                    .then(
                        if (active) {
                            Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                .shadow(
                                    elevation = 2.dp,
                                    shape = RoundedCornerShape(percent = 50),
                                    clip = false,
                                    ambientColor = Color(0x14004AC6), // .glass-tab
                                    spotColor = Color(0x14004AC6),
                                )
                        } else {
                            Modifier
                        },
                    )
                    .clickable(interactionSource = interaction, indication = null) { onSelect(key) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = when {
                        active && isDark -> Color.Black
                        active -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/** Exact tournament.html empty state — balance-chip circle + outlined trophy + copy. */
@Composable
private fun TournamentsEmptyState(message: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 300.dp)
            .offset(y = (-80).dp) // web -mt-20
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(areenaColors().balanceChip),
            contentAlignment = Alignment.Center,
        ) {
            AreenaxIcon(
                name = "trophy",
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
