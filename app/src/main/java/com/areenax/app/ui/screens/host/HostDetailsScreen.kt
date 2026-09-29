package com.areenax.app.ui.screens.host

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.areenax.app.core.ui.AreenaxSpinner
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.BellButton
import com.areenax.app.core.ui.EmptyState
import com.areenax.app.core.ui.LiveCountdownText
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.util.formatMoney
import com.areenax.app.core.util.timeAgo
import com.areenax.app.data.Tournament
import com.areenax.app.data.TournamentEntry
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * HostDetailsScreen — key `hostTournamentDetails` (SPEC/01 F5; web
 * HostDetailsScreen.tsx, pixel truth hosttournamentdetails.html).
 *
 * HOST view of their own tournament: `GET /tournaments/:id` (with entries[]).
 * bg-surface-container-low page + inner bg-background card; hero with countdown
 * pill ("Registration ends in …", 1s tick); adaptive summary cells
 * (Prize Pool / Entry Fee / Per Kill); Overview / Rules / Prizes underline tabs;
 * HOST "ID & Pass" copy row + Room Info card (per-field copy, 2s "Copied!"
 * flash); Participants card (initial/avatar rows + timeAgo, "No participants
 * yet." empty); floating "Copy Room Info" bar. Missing room info → toast
 * "Room info not set yet" / "Room ID & password will be available before match
 * start."; missing value → toast "Nothing to copy yet". Not-found state:
 * EmptyState + "My Tournaments" → hostTournament.
 */
@Composable
fun HostDetailsScreen(env: NavEnv) {
    val tournamentId = env.params["tournamentId"] as? String

    var tournament by remember { mutableStateOf<Tournament?>(null) }
    var loading by remember { mutableStateOf(!tournamentId.isNullOrBlank()) }
    var notFound by remember { mutableStateOf(tournamentId.isNullOrBlank()) }
    // A5-03: NetworkError used to claim "Tournament not found." — 404 keeps
    // that copy; transport failures now say "Couldn't load — check your
    // connection."
    var notFoundMessage by remember { mutableStateOf("Tournament not found.") }
    var tab by remember { mutableStateOf(DetailsTab.OVERVIEW) }
    var copiedKey by remember { mutableStateOf<String?>(null) }
    // A7-02: the 1 s screen-scope tick (nowTick) recomposed this whole screen
    // every second; the countdown now lives in the LiveCountdownText leaf.

    val scope = rememberCoroutineScope()

    LaunchedEffect(tournamentId) {
        if (tournamentId.isNullOrBlank()) return@LaunchedEffect
        when (val res = safeCall { env.api.tournamentDetails(tournamentId) }) {
            is ApiResult.Success -> tournament = res.data.tournament
            is ApiResult.NetworkError -> {
                notFound = true
                notFoundMessage = "Couldn't load — check your connection."
            }
            else -> {
                notFound = true
                notFoundMessage = if ((res as? ApiResult.Error)?.code == 404) "Tournament not found." else "Couldn't load this tournament."
            }
        }
        loading = false
    }

    fun flashCopied(key: String) {
        copiedKey = key
        scope.launch {
            delay(2000)
            if (copiedKey == key) copiedKey = null
        }
    }

    fun copyText(text: String): Boolean = try {
        val cm = env.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("AREENAX", text))
        true
    } catch (_: Exception) {
        false
    }

    fun copyRoomInfo(t: Tournament?) {
        if (t == null) return
        if (t.roomId.isNullOrBlank() && t.roomPassword.isNullOrBlank()) {
            env.toast.show(
                title = "Room info not set yet",
                description = "Room ID & password will be available before match start.",
            )
            return
        }
        val text = "Room ID: ${t.roomId ?: "-"}\nRoom Password: ${t.roomPassword ?: "-"}"
        if (copyText(text)) flashCopied("all")
    }

    fun copyField(key: String, value: String?) {
        if (value.isNullOrBlank()) {
            env.toast.show(title = "Nothing to copy yet")
            return
        }
        if (copyText(value)) flashCopied(key)
    }

    when {
        loading -> {
            // bg-surface-container-low page, centered spinner (no AppBar on web)
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AreenaxSpinner(size = 32, strokeWidth = 2)
            }
        }

        notFound || tournament == null -> {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                AppBar(title = "Tournament Details", right = { BellButton() })
                EmptyState(message = notFoundMessage)
                Box(Modifier.fillMaxWidth().padding(bottom = 40.dp), contentAlignment = Alignment.Center) {
                    val interaction = remember { MutableInteractionSource() }
                    Button(
                        onClick = { env.navigate(ScreenKeys.HOST_TOURNAMENT) },
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        interactionSource = interaction,
                        modifier = Modifier.pressScale(interaction, pressedScale = 0.95f),
                    ) {
                        Text("My Tournaments", style = Type.labelLg)
                    }
                }
            }
        }

        else -> {
            val t = tournament ?: return
            val cur = t.currentPlayers
            val max = t.maxPlayers
            val pct = if (max > 0) (cur.toFloat() / max) * 100f else 0f
            val banner = t.bannerImage ?: t.game?.image ?: ""

            val rulesList = t.rules.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val rankPrize: (Int) -> Double = { percent ->
                ((t.prizePool * percent) / 100).roundToLong().toDouble()
            }
            val showSummaryPrize = t.prizePool > 0.0
            val showSummaryPerKill = t.perKill > 0.0
            val summaryCols = 1 + (if (showSummaryPrize) 1 else 0) + (if (showSummaryPerKill) 1 else 0)

            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    AppBar(title = "Tournament Details", right = { BellButton() })

                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                            .padding(bottom = 88.dp), // pb-24 + floating-bar clearance
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        // ---- Hero ------------------------------------------------
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp) // h-[12.5rem]
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                        ) {
                            if (banner.isNotBlank()) {
                                AsyncImage(
                                    model = banner,
                                    contentDescription = t.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(percent = 50),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                ),
                                shadowElevation = 1.dp,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(16.dp),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_schedule),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    // A7-02: leaf-owned 1 s tick — only this text
                                    // re-renders per second (was: whole screen).
                                    Text(
                                        text = "Registration ends in ",
                                        style = Type.labelMd,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    LiveCountdownText(
                                        startTimeIso = t.startTime,
                                        style = Type.labelMd,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        // ---- Summary cards (adaptive grid) ------------------------
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (showSummaryPrize) {
                                SummaryCell(
                                    modifier = Modifier.weight(1f),
                                    iconRes = R.drawable.ic_emoji_events_fill,
                                    label = "Prize Pool",
                                    value = "Rs ${formatMoney(t.prizePool)}",
                                )
                            }
                            SummaryCell(
                                modifier = Modifier.weight(1f),
                                iconRes = R.drawable.ic_payments,
                                label = "Entry Fee",
                                value = "Rs ${formatMoney(t.entryFee)}",
                            )
                            if (showSummaryPerKill) {
                                SummaryCell(
                                    modifier = Modifier.weight(1f),
                                    iconRes = R.drawable.ic_my_location,
                                    label = "Per Kill",
                                    value = "Rs ${formatMoney(t.perKill)}",
                                )
                            }
                        }
                        if (summaryCols == 1) Unit // single cell spans full width via weight above

                        // ---- Tabs (underline style) ---------------------------------
                        Column {
                            Row(Modifier.fillMaxWidth()) {
                                DetailsTab.entries.forEach { entry ->
                                    val active = tab == entry
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) { tab = entry },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            text = entry.label,
                                            style = Type.labelLg,
                                            color = if (active) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                        Spacer(Modifier.height(12.dp)) // pb-3
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(2.dp)
                                                .background(
                                                    if (active) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        Color.Transparent
                                                    },
                                                ),
                                        )
                                    }
                                }
                            }
                            // container hairline (border-b border-outline-variant)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant),
                            )
                        }

                        // ---- Overview ------------------------------------------------
                        if (tab == DetailsTab.OVERVIEW) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                            ) {
                                OverviewRow(icon = R.drawable.ic_person, label = "Mode", value = t.mode, divider = true)
                                if (t.map.isNotBlank()) {
                                    OverviewRow(icon = R.drawable.ic_map, label = "Map", value = t.map, divider = true)
                                }
                                // Players row with mini progress
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_group),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Text(
                                        text = "Players",
                                        style = Type.bodyMd,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Box(
                                        Modifier
                                            .width(96.dp)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(percent = 50))
                                            .background(areenaColors().surfaceContainerHighLavender),
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth(pct / 100f)
                                                .height(8.dp)
                                                .background(MaterialTheme.colorScheme.primary),
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "$cur/$max",
                                        style = Type.labelLg,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant),
                                )
                                if (t.loserPrize > 0.0) {
                                    OverviewRow(
                                        icon = R.drawable.ic_military_tech,
                                        label = "Loser Prize",
                                        value = "Rs ${formatMoney(t.loserPrize)}",
                                        divider = true,
                                    )
                                }
                                if (t.perspective.isNotBlank()) {
                                    OverviewRow(
                                        icon = R.drawable.ic_visibility,
                                        label = "Perspective",
                                        value = t.perspective,
                                        divider = true,
                                    )
                                }
                                // ID & Pass — HOST copy row
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_lock),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Text(
                                        text = "ID & Pass",
                                        style = Type.bodyMd,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(percent = 50))
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) { copyRoomInfo(t) }
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (copiedKey == "all") {
                                            Text(
                                                text = "Copied!",
                                                style = Type.labelLg,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_lock),
                                                contentDescription = "Copy room info",
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ---- Rules ----------------------------------------------------
                        if (tab == DetailsTab.RULES) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Tournament Rules",
                                        style = Type.headlineMd,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Surface(
                                        onClick = { copyField("rules", t.rules) },
                                        shape = RoundedCornerShape(percent = 50),
                                        color = Color.Transparent,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary,
                                        ),
                                    ) {
                                        Row(
                                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                painter = painterResource(
                                                    if (copiedKey == "rules") R.drawable.ic_check else R.drawable.ic_content_copy,
                                                ),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                            if (copiedKey == "rules") {
                                                Text(
                                                    text = "Copied!",
                                                    style = Type.labelLg,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (rulesList.isEmpty()) {
                                        Text(
                                            text = "No rules set for this tournament.",
                                            style = Type.bodyMd,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    rulesList.forEachIndexed { index, rule ->
                                        Column {
                                            Row(
                                                Modifier.padding(bottom = 12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            ) {
                                                Text(
                                                    text = (index + 1).toString().padStart(2, '0'),
                                                    style = Type.labelLg,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                                Text(
                                                    text = rule,
                                                    style = Type.bodyMd,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            if (index < rulesList.size - 1) {
                                                Box(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .height(1.dp)
                                                        .background(MaterialTheme.colorScheme.outlineVariant),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ---- Prizes -----------------------------------------------------
                        if (tab == DetailsTab.PRIZES) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Text(
                                    text = "Prize Distribution",
                                    style = Type.headlineMd,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (t.prizePool > 0.0) {
                                        PrizeRow(
                                            container = areenaColors().surfaceContainerLavender.copy(alpha = 0.5f),
                                            leading = {
                                                Box(
                                                    Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_emoji_events_fill),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp),
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            },
                                            title = "Rank 1",
                                            subtitle = "Winner Trophy",
                                            value = "Rs ${formatMoney(rankPrize(50))}",
                                            valueColor = MaterialTheme.colorScheme.primary,
                                        )
                                        PrizeRow(
                                            container = MaterialTheme.colorScheme.surfaceContainerLowest,
                                            leading = {
                                                NumberBadge("2")
                                            },
                                            title = "Rank 2",
                                            subtitle = null,
                                            value = "Rs ${formatMoney(rankPrize(30))}",
                                            valueColor = MaterialTheme.colorScheme.onSurface,
                                        )
                                        PrizeRow(
                                            container = MaterialTheme.colorScheme.surfaceContainerLowest,
                                            leading = {
                                                NumberBadge("3")
                                            },
                                            title = "Rank 3",
                                            subtitle = null,
                                            value = "Rs ${formatMoney(rankPrize(20))}",
                                            valueColor = MaterialTheme.colorScheme.onSurface,
                                        )
                                    } else {
                                        PrizeRow(
                                            container = areenaColors().surfaceContainerLavender.copy(alpha = 0.5f),
                                            leading = {
                                                Box(
                                                    Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(areenaColors().surfaceContainerHighLavender),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_emoji_events),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            },
                                            title = "Prize pool will be announced",
                                            subtitle = null,
                                            value = "",
                                            valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            mutedTitle = true,
                                        )
                                    }
                                    if (t.perKill > 0.0) {
                                        PrizeRow(
                                            container = MaterialTheme.colorScheme.surfaceContainerLowest,
                                            leading = {
                                                Box(
                                                    Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(areenaColors().surfaceContainerHighLavender),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_my_location),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            },
                                            title = "Per Kill",
                                            subtitle = "Elimination reward",
                                            value = "Rs ${formatMoney(t.perKill)}",
                                            valueColor = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    if (t.loserPrize > 0.0) {
                                        PrizeRow(
                                            container = MaterialTheme.colorScheme.surfaceContainerLowest,
                                            leading = {
                                                Box(
                                                    Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(areenaColors().surfaceContainerHighLavender),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_military_tech),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            },
                                            title = "Loser Prize",
                                            subtitle = "Loser prize",
                                            value = "Rs ${formatMoney(t.loserPrize)}",
                                            valueColor = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }

                        // ---- Room Info ----------------------------------------------------
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_key),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = "Room Info",
                                        style = Type.headlineMd,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Surface(
                                    onClick = { copyRoomInfo(t) },
                                    shape = RoundedCornerShape(percent = 50),
                                    color = Color.Transparent,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary,
                                    ),
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(
                                                if (copiedKey == "all") R.drawable.ic_check else R.drawable.ic_content_copy,
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = if (copiedKey == "all") "Copied!" else "Copy",
                                            style = Type.labelLg,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                            // Room ID
                            RoomFieldRow(
                                label = "ROOM ID",
                                value = t.roomId,
                                copied = copiedKey == "roomId",
                                onCopy = { copyField("roomId", t.roomId) },
                                divider = true,
                            )
                            // Room Password
                            RoomFieldRow(
                                label = "ROOM PASSWORD",
                                value = t.roomPassword,
                                copied = copiedKey == "roomPassword",
                                onCopy = { copyField("roomPassword", t.roomPassword) },
                                divider = false,
                            )
                        }

                        // ---- Participants ---------------------------------------------------
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Participants",
                                    style = Type.headlineMd,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "$cur / $max",
                                    style = Type.labelLg,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (t.entries.isEmpty()) {
                                Text(
                                    text = "No participants yet.",
                                    style = Type.bodyMd,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                )
                            } else {
                                Column(
                                    Modifier
                                        .heightIn(max = 384.dp) // max-h-96
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    t.entries.forEachIndexed { index, entry ->
                                        ParticipantRow(entry = entry, divider = index < t.entries.size - 1)
                                    }
                                }
                            }
                        }
                    }
                }

                // ---- Floating Copy Room Info bar -----------------------------------
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to MaterialTheme.colorScheme.surfaceContainerLowest,
                            ),
                        )
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(
                        onClick = { copyRoomInfo(t) },
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 300.dp) // max-w-[18.75rem]
                            .height(48.dp), // h-[3rem]
                    ) {
                        Icon(
                            painter = painterResource(
                                if (copiedKey == "all") R.drawable.ic_check else R.drawable.ic_content_copy,
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (copiedKey == "all") "Copied!" else "Copy Room Info",
                            style = Type.labelLg,
                        )
                    }
                }
            }
        }
    }
}

private enum class DetailsTab(val label: String) {
    OVERVIEW("Overview"),
    RULES("Rules"),
    PRIZES("Prizes"),
}

@Composable
private fun SummaryCell(modifier: Modifier = Modifier, iconRes: Int, label: String, value: String) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp),
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = Type.labelMd,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = Type.headlineMd.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Overview info row — h-[4rem], 24dp primary icon + label, right value. */
@Composable
private fun OverviewRow(icon: Int, label: String, value: String, divider: Boolean) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                style = Type.bodyMd,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = value,
                style = Type.labelLg,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (divider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun NumberBadge(number: String) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(areenaColors().surfaceContainerHighLavender),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number,
            style = Type.labelLg,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrizeRow(
    container: Color,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    value: String,
    valueColor: Color,
    mutedTitle: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp),
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = Type.labelLg,
                color = if (mutedTitle) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = Type.labelMd,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (value.isNotBlank()) {
            Text(
                text = value,
                style = Type.headlineMd,
                color = valueColor,
            )
        }
    }
}

/** Room Info field row — uppercase label, value (or "Available before start"), copy circle. */
@Composable
private fun RoomFieldRow(label: String, value: String?, copied: Boolean, onCopy: () -> Unit, divider: Boolean) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = Type.labelMd.copy(letterSpacing = 0.8.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value?.takeIf { it.isNotBlank() } ?: "Available before start",
                    style = Type.labelLg,
                    color = if (value.isNullOrBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            Surface(
                onClick = onCopy,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(
                            if (copied) R.drawable.ic_check else R.drawable.ic_content_copy,
                        ),
                        contentDescription = "Copy $label",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (divider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun ParticipantRow(entry: TournamentEntry, divider: Boolean) {
    val u = entry.user
    val name = u?.gameName?.takeIf { it.isNotBlank() }
        ?: entry.teamName?.takeIf { it.isNotBlank() }
        ?: "Player"
    val initial = name.take(1).uppercase()
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!u?.avatar.isNullOrBlank()) {
                AsyncImage(
                    model = u?.avatar,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                )
            } else {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(areenaColors().surfaceContainerLavender),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initial,
                        style = Type.bodyLg.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = Type.labelLg,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "UID: ${u?.uid?.takeIf { it.isNotBlank() } ?: "—"}",
                    style = Type.labelMd,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = timeAgo(entry.createdAt),
                style = Type.labelMd,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (divider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}
