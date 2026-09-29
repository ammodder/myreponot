package com.areenax.app.ui.screens.host

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.areenax.app.R
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.theme.bannerDarkStart
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.AreenaxSpinner
import com.areenax.app.core.ui.BellButton
import com.areenax.app.core.ui.GuestGateDialog
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.rememberGuestGate
import com.areenax.app.core.util.formatDateTime
import com.areenax.app.core.util.formatMoney
import com.areenax.app.data.Tournament

/**
 * HostTournamentScreen — key `hostTournament` (SPEC/01 F1; web HostTournamentScreen.tsx,
 * pixel truth hosttournament.html).
 *
 * Host hub for one game: AppBar (title = gameName ?? "Host Tournament"), pill tab row
 * Upcoming / Ongoing / Completed / Created, `GET /my/hosted` scoped by gameId, the exact
 * hosttournamentcard.html HostCard replica per row (tap → hostTournamentDetails),
 * per-tab empty states (Created tab adds a "Create Tournament" CTA) and a 48dp add FAB.
 * FAB / empty CTA pass the guest gate "host your own tournament".
 */
@Composable
fun HostTournamentScreen(env: NavEnv) {
    val gameId = env.params["gameId"] as? String
    val gameName = env.params["gameName"] as? String

    var tab by remember { mutableStateOf(HostTab.UPCOMING) }
    var tournaments by remember { mutableStateOf<List<Tournament>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    val gate = rememberGuestGate()
    val user by env.session.user.collectAsState()

    LaunchedEffect(Unit) {
        when (val res = safeCall { env.api.myHosted() }) {
            is ApiResult.Success -> tournaments = res.data.tournaments
            else -> tournaments = emptyList()
        }
        loading = false
    }

    // Per-game scope: when opened for a specific game, only that game's
    // tournaments are shown across every tab.
    val scoped = if (gameId != null) tournaments.filter { it.gameId == gameId } else tournaments
    val filtered = when (tab) {
        HostTab.CREATED -> scoped
        HostTab.UPCOMING -> scoped.filter { it.status == "UPCOMING" }
        HostTab.ONGOING -> scoped.filter { it.status == "ONGOING" }
        HostTab.COMPLETED -> scoped.filter { it.status == "COMPLETED" }
    }

    val createParams: Map<String, Any?> = if (gameId != null) {
        buildMap {
            put("gameId", gameId)
            gameName?.let { put("gameName", it) }
        }
    } else {
        emptyMap()
    }
    val onCreate = {
        if (gate.requireAccount(user?.isGuest == true, "host your own tournament")) {
            env.navigate(ScreenKeys.HOST_TOURNAMENT_CREATION, createParams)
        }
        Unit
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent),
    ) {
        AppBarHost(title = gameName ?: "Host Tournament")

        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize()) {
                HostTabRow(
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(32.dp)) // mb-8

                when {
                    loading -> {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .offset(y = (-80).dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AreenaxSpinner(size = 32, strokeWidth = 2)
                        }
                    }

                    filtered.isEmpty() -> {
                        HostEmptyState(
                            tab = tab,
                            onCreateClick = onCreate,
                            modifier = Modifier
                                .fillMaxSize()
                                .offset(y = (-80).dp),
                        )
                    }

                    else -> {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            filtered.forEach { tournament ->
                                HostTournamentCard(
                                    tournament = tournament,
                                    onClick = {
                                        env.navigate(
                                            ScreenKeys.HOST_TOURNAMENT_DETAILS,
                                            mapOf("tournamentId" to tournament.id),
                                        )
                                    },
                                )
                            }
                            Spacer(Modifier.height(96.dp)) // pb-24 breathing room
                        }
                    }
                }
            }

            // FAB — bottom-6 right-6, 48dp primary circle + add
            val fabInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = onCreate,
                interactionSource = fabInteraction,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 24.dp)
                    .size(48.dp)
                    .pressScale(fabInteraction, pressedScale = 0.95f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = "Create Tournament",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }

    GuestGateDialog(gate, env)
}

private enum class HostTab(val label: String) {
    UPCOMING("Upcoming"),
    ONGOING("Ongoing"),
    COMPLETED("Completed"),
    CREATED("Created"),
}

/** Status chip label (web statusLabel): Open / Live / Completed / Cancelled. */
internal fun hostStatusLabel(t: Tournament): String = when (t.status) {
    "UPCOMING" -> "Open"
    "ONGOING" -> "Live"
    "COMPLETED" -> "Completed"
    else -> "Cancelled"
}

/** Web AppBar(title = …) — page mode with the default notification bell. */
@Composable
internal fun AppBarHost(title: String) {
    AppBar(title = title, right = { BellButton() })
}

/**
 * Pill tab row — `bg-surface-container-lavender p-1.5 rounded-full h-12`; the
 * active tab is a white (dark: white) glass pill with primary text + soft shadow.
 */
@Composable
private fun HostTabRow(selected: HostTab, onSelect: (HostTab) -> Unit, modifier: Modifier = Modifier) {
    val extended = areenaColors()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(extended.surfaceContainerLavender)
            .padding(6.dp),
    ) {
        Row(Modifier.fillMaxSize()) {
            HostTab.entries.forEach { tab ->
                val active = tab == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(percent = 50))
                        .background(
                            when {
                                active && extended.isDark -> Color.White
                                active -> MaterialTheme.colorScheme.surfaceContainerLowest
                                else -> Color.Transparent
                            },
                        )
                        .then(
                            if (active) {
                                Modifier.shadow(
                                    elevation = 2.dp,
                                    shape = RoundedCornerShape(percent = 50),
                                    clip = false,
                                    ambientColor = Color(0x14004AC6),
                                    spotColor = Color(0x14004AC6),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(tab) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tab.label,
                        style = Type.bodyMd.copy(
                            fontSize = 14.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = when {
                            active && extended.isDark -> Color.Black
                            active -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}


/**
 * Empty state — 80dp balance-chip circle + trophy/add_circle (Created tab),
 * exact per-tab copy, and the Created-tab "Create Tournament" CTA.
 */
@Composable
private fun HostEmptyState(tab: HostTab, onCreateClick: () -> Unit, modifier: Modifier = Modifier) {
    val extended = areenaColors()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(extended.balanceChip),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (tab == HostTab.CREATED) R.drawable.ic_add_circle else R.drawable.ic_trophy),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = when (tab) {
                HostTab.UPCOMING -> "No upcoming tournaments."
                HostTab.ONGOING -> "No ongoing tournaments."
                HostTab.COMPLETED -> "No completed tournaments."
                HostTab.CREATED -> "You haven't hosted any tournaments yet."
            },
            style = Type.bodyLg,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (tab == HostTab.CREATED) {
            Spacer(Modifier.height(24.dp)) // mb-6
            val interaction = remember { MutableInteractionSource() }
            Button(
                onClick = onCreateClick,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .pressScale(interaction, pressedScale = 0.95f),
                interactionSource = interaction,
            ) {
                Text(
                    text = "Create Tournament",
                    style = Type.bodyLg.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

/**
 * HostCard — EXACT replica of hosttournamentcard.html (web HostCard in both
 * HostTournamentScreen + HostCardsScreen). Shared internally by both screens.
 */
@Composable
internal fun HostTournamentCard(tournament: Tournament, onClick: () -> Unit) {
    val cur = tournament.currentPlayers
    val max = tournament.maxPlayers
    val pct = if (max > 0) (cur.toFloat() / max) * 100f else 0f
    val initial = ((tournament.host?.gameName ?: tournament.name).ifBlank { "A" })
        .take(1)
        .uppercase()
    val showPrize = tournament.prizePool > 0.0
    val shape = RoundedCornerShape(24.dp) // rounded-[1.5rem]
    val interaction = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction, pressedScale = 0.99f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
        ),
        shadowElevation = 1.dp,
    ) {
        Column {
            // ---- Banner (h-48) -------------------------------------------------
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(192.dp)
                    .background(
                        Brush.verticalGradient(listOf(bannerDarkStart, Color.Black)),
                    ),
            ) {
                val imageUrl = tournament.bannerImage ?: tournament.game?.image ?: ""
                if (imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // bg-gradient-to-t from-black/70
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.7f),
                            ),
                        ),
                )
                // Mode chip — top-right primary pill
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp),
                ) {
                    Text(
                        text = tournament.mode,
                        style = Type.labelMd.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                // Start time — bottom-left
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_schedule),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White,
                    )
                    Text(
                        text = formatDateTime(tournament.startTime),
                        style = Type.bodyMd.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                }
            }

            // ---- Body (p-4) ----------------------------------------------------
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = initial,
                                style = Type.headlineMd,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column {
                            Text(
                                text = tournament.name,
                                style = Type.bodyLg.copy(fontWeight = FontWeight.Bold, lineHeight = 20.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Tournament Lead",
                                style = Type.labelMd.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.6.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Tournament Status",
                            style = Type.labelMd.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = hostStatusLabel(tournament),
                            style = Type.bodyLg.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Stats band (border-t/b outline-variant/10) — Total Prize only when configured
                Column(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)),
                    )
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(vertical = 12.dp),
                    ) {
                        if (showPrize) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "TOTAL PRIZE",
                                    style = Type.labelMd.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Rs ${formatMoney(tournament.prizePool)}",
                                    style = Type.bodyMd.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Column(
                            Modifier.weight(if (showPrize) 1f else 2f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "ENTRY FEE",
                                style = Type.labelMd.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Rs ${formatMoney(tournament.entryFee)}",
                                style = Type.bodyMd.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)),
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Filled Slots + progress
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Filled Slots",
                            style = Type.bodyMd.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "$cur / $max Players",
                            style = Type.bodyMd.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(pct / 100f)
                                .height(8.dp)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // JOIN NOW — display-only pill (whole card navigates)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.primary),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "JOIN NOW",
                        style = Type.bodyLg.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}
