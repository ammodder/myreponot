package com.areenax.app.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.AppBarMode
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AreenaxSpinner
import com.areenax.app.core.ui.BellButton
import com.areenax.app.core.ui.EmptyState
import com.areenax.app.core.ui.RoomInfoSheet
import com.areenax.app.core.ui.TournamentCard
import com.areenax.app.core.util.TournamentCardActionKind
import com.areenax.app.core.util.deriveTournamentStatus
import com.areenax.app.data.Tournament
import com.areenax.app.data.TournamentEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * MyTournamentScreen — key `myTournament` (bottom-nav "Tournament" tab, SPEC/01 B4).
 * 1:1 port of src/components/screens/main/MyTournamentScreen.tsx + mytournament.html.
 *
 * ONLY the tournaments the current user has joined. AppBar tab mode
 * ("My Tournaments" + BalanceChip + bell). Status filter chips (bg-balance-chip pill)
 * — client-side filter by deriveTournamentStatus. Cards = TournamentCard with
 * joined=true, normalized to the live status, joinLabel "VIEW RESULTS" (completed)
 * else "VIEW DETAILS":
 *   entry tap → COMPLETED ? results {tournamentId} : tournamentDetails {tournamentId}
 *   room      → RoomInfoSheet;  results → results {tournamentId};  proof → tournamentDetails.
 * API: GET /my/tournaments — POLLS EVERY 30000 ms; errors only surface when nothing
 * was ever loaded. Guests/logged-out skip the fetch entirely (no 401 risk).
 * Empty: 80dp circle (border primary-fixed) + emoji_events + "You have not joined any
 * tournaments yet." (or the per-filter copy) + "Browse Games" CTA (hidden on Completed).
 */

/** Filter descriptors — keys + labels + EXACT per-filter empty copy (web FILTERS array). */
private val MY_FILTERS = listOf(
    Triple("UPCOMING", "Upcoming", "No upcoming tournaments."),
    Triple("ONGOING", "Ongoing", "No ongoing tournaments."),
    Triple("COMPLETED", "Completed", "No completed tournaments."),
)

/** Web primary-fixed tone for the empty-circle border (dark re-ink = #182338). */
@Composable
private fun primaryFixedTone(): Color =
    areenaColors().primaryFixed // A8-06

@Composable
fun MyTournamentScreen(env: NavEnv) {
    val user by env.session.user.collectAsState()
    val hasAccount = user != null && user?.isGuest != true

    var entries by remember { mutableStateOf<List<TournamentEntry>?>(null) }
    var error by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("UPCOMING") }
    var roomEntry by remember { mutableStateOf<TournamentEntry?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(hasAccount) {
        // Guests (and logged-out users) never joined anything — skip the fetch so the
        // 401 auto-logout path is never triggered from this tab.
        if (!hasAccount) return@LaunchedEffect
        // A3-06: poll only while the screen is at least STARTED — the 30 s loop
        // used to keep firing while the app was backgrounded.
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var loaded = false
            while (isActive) {
                when (val res = safeCall { env.api.myTournaments() }) {
                    is ApiResult.Success -> {
                        loaded = true
                        error = false
                        entries = res.data.entries
                    }
                    // Only surface the error when we never loaded — later poll failures
                    // must not wipe an already-rendered list
                    else -> if (!loaded) error = true
                }
                delay(30_000)
            }
        }
    }

    val list = if (hasAccount) entries.orEmpty() else emptyList()
    // Every joined tournament is bucketed by its live, time-aware status
    val filtered = list.filter { entry ->
        entry.tournament != null && deriveTournamentStatus(entry.tournament!!) == filter
    }
    val loading = hasAccount && entries == null && !error
    val activeFilter = MY_FILTERS.firstOrNull { it.first == filter }
    val emptyMsg =
        if (list.isEmpty()) "You have not joined any tournaments yet."
        else activeFilter?.third ?: "No tournaments found."

    Box(Modifier.fillMaxSize()) {
        // A7-03: the entry list used to compose EVERY card inside a
        // Column+verticalScroll; a LazyColumn windows the composition (stable
        // keys = tournament id). Visual order and paddings preserved.
        LazyColumn(
            Modifier.fillMaxSize(),
        ) {
            // Root tab header — BalanceChip + standard bell (no back button on a nav tab)
            item(key = "appbar") {
                AppBar(mode = AppBarMode.Tab, title = "My Tournaments", balance = true, right = { BellButton() })
            }

            // ---- Main (px-4 py-2 pb-24) ----------------------------------------
            item(key = "filters") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    // Status Filter Chips
                    Spacer(Modifier.height(8.dp)) // container mt-2
                    MyFilterTabs(selected = filter, onSelect = { filter = it })
                    Spacer(Modifier.height(24.dp)) // mb-6
                }
            }

            when {
                loading -> item(key = "state") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AreenaxSpinner(size = 32, strokeWidth = 2)
                    }
                }

                error -> item(key = "state") {
                    EmptyState(message = "Failed to load tournaments.", icon = "error")
                }

                filtered.isEmpty() -> item(key = "state") {
                    // Empty State Card — exact mytournament.html markup
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Trophy Icon
                        Box(
                            Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .border(1.dp, primaryFixedTone(), CircleShape)
                                .shadow(
                                    elevation = 2.dp,
                                    shape = CircleShape,
                                    clip = false,
                                    ambientColor = Color(0x0D0B1C30),
                                    spotColor = Color(0x0D0B1C30),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            AreenaxIcon(
                                name = "emoji_events",
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(24.dp)) // mb-6
                        // Empty State Text
                        Text(
                            text = emptyMsg,
                            fontSize = 15.sp, // text-[0.9375rem] leading-relaxed
                            fontWeight = FontWeight.Medium,
                            lineHeight = 24.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(32.dp)) // mb-8
                        // Primary Action Button — hidden on the Completed tab only
                        if (filter != "COMPLETED") {
                            Button(
                                onClick = { env.navigate(ScreenKeys.HOME) },
                                shape = RoundedCornerShape(percent = 50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                modifier = Modifier
                                    .widthIn(max = 240.dp) // max-w-[15rem]
                                    .height(52.dp)
                                    .shadow(
                                        elevation = 6.dp,
                                        shape = RoundedCornerShape(percent = 50),
                                        clip = false,
                                        ambientColor = Color(0x4D004AC6),
                                        spotColor = Color(0x4D004AC6),
                                    ),
                            ) {
                                Text(
                                    "Browse Games",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                else -> itemsIndexed(
                    filtered,
                    key = { _, entry -> entry.id },
                ) { _, entry ->
                    val t = entry.tournament ?: return@itemsIndexed
                    // Normalize the card to its live status so the chip/labels
                    // always match the tab it sits in, even between refreshes
                    val live: Tournament = t.copy(status = deriveTournamentStatus(t))
                    Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        TournamentCard(
                            tournament = live,
                            joined = true,
                            joinLabel = if (live.status == "COMPLETED") "VIEW RESULTS" else "VIEW DETAILS",
                            onClick = {
                                if (live.status == "COMPLETED") {
                                    env.navigate(ScreenKeys.RESULTS, mapOf("tournamentId" to live.id))
                                } else {
                                    env.navigate(
                                        ScreenKeys.TOURNAMENT_DETAILS,
                                        mapOf("tournamentId" to live.id),
                                    )
                                }
                            },
                            onAction = { action ->
                                when (action.kind) {
                                    TournamentCardActionKind.ROOM -> roomEntry = entry
                                    TournamentCardActionKind.RESULTS ->
                                        env.navigate(ScreenKeys.RESULTS, mapOf("tournamentId" to live.id))
                                    TournamentCardActionKind.PROOF ->
                                        env.navigate(
                                            ScreenKeys.TOURNAMENT_DETAILS,
                                            mapOf("tournamentId" to live.id),
                                        )
                                    else -> Unit
                                }
                            },
                        )
                    }
                }
            }

            item(key = "footer") { Spacer(Modifier.height(88.dp)) } // pb-24
        }
    }

    // Room ID & Password popup — credentials fetched fresh per open
    RoomInfoSheet(
        open = roomEntry != null,
        onClose = { roomEntry = null },
        tournamentId = roomEntry?.tournament?.id,
        env = env,
        tournamentName = roomEntry?.tournament?.name,
    )
}

/** Segmented filter pills — same language as TournamentsScreen (active = glass-tab). */
@Composable
private fun MyFilterTabs(selected: String, onSelect: (String) -> Unit) {
    val isDark = areenaColors().isDark
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(percent = 50))
            .background(areenaColors().balanceChip)
            .padding(4.dp),
    ) {
        MY_FILTERS.forEach { (key, label, _) ->
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
                    .padding(horizontal = 8.dp, vertical = 8.dp),
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
