package com.areenax.app.ui.screens.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil.compose.AsyncImage
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.theme.bannerDarkStart
import com.areenax.app.core.theme.hairlineGray
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.AppBarCircleButton
import com.areenax.app.core.ui.AppBarMode
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AreenaxSpinner
import com.areenax.app.core.ui.EmptyState
import com.areenax.app.core.ui.GuestGateDialog
import com.areenax.app.core.ui.InsufficientBalanceDialog
import com.areenax.app.core.ui.SupportFab
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.rememberGuestGate
import com.areenax.app.core.ui.rememberImagePicker
import com.areenax.app.core.util.TournamentCardAction
import com.areenax.app.core.util.TournamentCardActionKind
import com.areenax.app.core.util.computeTournamentAction
import com.areenax.app.core.util.formatDateTime
import com.areenax.app.core.util.formatMoney
import com.areenax.app.core.util.isRoomOpen
import com.areenax.app.data.ProofImageRequest
import com.areenax.app.data.Tournament
import com.areenax.app.data.TournamentEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TournamentDetailsScreen — key `tournamentDetails` (SPEC/01 B3). 1:1 port of
 * src/components/screens/main/TournamentDetailsScreen.tsx + tournamentdetails.html —
 * the richest screen: AppBar page mode with share; hero (cover / gradient+stadium)
 * with the 1s-ticking countdown pill; Prize Pool / Entry Fee / Per Kill summary cards
 * (never "Rs 0" cells); Overview / Rules / Prizes underline tabs; stat rows with the
 * ID & Pass View/Hide reveal (copy buttons + "Room ID copied to clipboard" toasts);
 * numbered rules; 50/30/20 prize distribution; participants list; floating bottom bar
 * driven by computeTournamentAction (Room ID & Password rows / "will be announced"
 * / Results button / Submit Result Proof + one-time /proof upload (≤8MB) via the
 * photo picker / Match Processing ping / Joined-Completed chips) and the guest-gated
 * "Join Now - Rs X" CTA (error containing "insufficient" → InsufficientBalanceDialog
 * → Deposit). SupportFab (offset=false). 1s countdown tick (web setInterval).
 */

/** POST /tournaments/:id/proof image cap — 8 MB (SPEC/02 §3). */
private const val PROOF_MAX_BYTES = 8 * 1024 * 1024

@Composable
fun TournamentDetailsScreen(env: NavEnv) {
    val tournamentId = env.params["tournamentId"] as? String
    val user by env.session.user.collectAsState()
    val scope = rememberCoroutineScope()
    val gate = rememberGuestGate()

    var tournament by remember { mutableStateOf<Tournament?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var failedMessage by remember { mutableStateOf("Tournament not found.") }
    var tab by remember { mutableStateOf("overview") }
    var showInsufficient by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    var showRoom by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var proofUploading by remember { mutableStateOf(false) }

    // ---- Live state refresh (A7-02 + A3-06) ---------------------------------------
    // Was: a 1 s screen-scope tick that recomposed all 1,700 lines every second
    // (participants list included). Room-open/action state moves on minute
    // boundaries → a 15 s cadence is plenty (gated on STARTED so it pauses
    // while backgrounded); the per-second countdown moved INTO the DetailsHero
    // leaf (LiveCountdownText), so only the pill re-renders each second.
    var tick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(15_000)
                tick++
            }
        }
    }
    val nowMillis = remember(tick) { System.currentTimeMillis() }

    // ---- Load (refetch on refreshKey after join / proof — web parity) ------------
    LaunchedEffect(tournamentId, refreshKey) {
        if (tournamentId == null) {
            // Defensive: opened without an id — skip the request
            loading = false
            failed = true
            failedMessage = "Tournament not found."
            return@LaunchedEffect
        }
        when (val res = safeCall { env.api.tournamentDetails(tournamentId) }) {
            is ApiResult.Success -> {
                tournament = res.data.tournament
                failed = false
            }
            is ApiResult.NetworkError -> {
                failed = true
                failedMessage = "Couldn't load — check your connection."
            }
            is ApiResult.Error -> {
                failed = true
                failedMessage = if (res.code == 404) "Tournament not found." else "Couldn't load this tournament."
            }
        }
        loading = false
    }

    // ---- Result Proof — one gallery pick → data URL → POST /tournaments/:id/proof
    val proofPicker = rememberImagePicker(PROOF_MAX_BYTES) { picked ->
        val current = tournament ?: return@rememberImagePicker
        val dataUrl = picked?.dataUrl ?: return@rememberImagePicker
        if (proofUploading) return@rememberImagePicker
        proofUploading = true
        scope.launch {
            val res = safeCall {
                env.api.submitProof(current.id, ProofImageRequest(image = dataUrl))
            }
            proofUploading = false
            when (res) {
                is ApiResult.Success -> {
                    tournament = current.copy(myProofSubmitted = true, myProofImage = dataUrl)
                    env.toast.show("Result Submitted", "Wait for the admin's approval.")
                }
                is ApiResult.Error ->
                    if (res.message.contains("already submitted", ignoreCase = true)) {
                        // Stale UI raced a previous submission — lock the picker
                        tournament = current.copy(myProofSubmitted = true)
                        env.toast.show(
                            "Result Submitted",
                            "You have already submitted your result for this tournament.",
                        )
                    } else {
                        env.toast.show("Upload failed", res.message, ToastVariant.Destructive)
                    }
                is ApiResult.NetworkError ->
                    env.toast.show("Upload failed", res.message, ToastVariant.Destructive)
            }
        }
    }

    val copyText: (String, String) -> Unit = { text, label -> copyAndToast(env, text, label) }

    if (loading) {
        Box(
            Modifier
                .fillMaxSize()
                .background(detailsRootBackground()),
            contentAlignment = Alignment.Center,
        ) {
            AreenaxSpinner(size = 32, strokeWidth = 2)
        }
        return
    }

    if (tournament == null || failed) {
        Column(
            Modifier
                .fillMaxSize()
                .background(detailsRootBackground()),
        ) {
            AppBar(
                mode = AppBarMode.Page,
                title = "Tournament Details",
                right = {
                    AppBarCircleButton(onClick = { shareTournamentDetails(env, null, false, false) }) {
                        AreenaxIcon(
                            name = "share",
                            contentDescription = "Share tournament",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                EmptyState(message = failedMessage, icon = "error")
            }
            Box(
                Modifier
                    .align(Alignment.End)
                    .padding(end = 24.dp, bottom = 24.dp),
            ) {
                SupportFab(offset = false)
            }
        }
        return
    }

    val t = tournament ?: return
    val entries = t.entries
    val myEntry = t.myEntry
    val roomOpen = isRoomOpen(t, nowMillis)
    val myAction = computeTournamentAction(t, myEntry != null, nowMillis = nowMillis)

    // A7-02: the hero countdown no longer reads a screen-scope tick — the leaf
    // composable owns the 1 s update.
    val pct =
        if (t.maxPlayers > 0) {
            (t.currentPlayers.toFloat() / t.maxPlayers) * 100f
        } else {
            0f
        }
    val rulesList = t.rules.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    val rankPrize: (Int) -> String = { percent ->
        formatMoney(Math.round(t.prizePool * percent / 100).toDouble())
    }
    val handleShare = { shareTournamentDetails(env, t, roomOpen, myEntry != null) }

    fun handleJoin() {
        val current = tournament ?: return
        val account = user ?: return
        joining = true
        scope.launch {
            val res = safeCall { env.api.joinTournament(current.id) }
            joining = false
            when (res) {
                is ApiResult.Success -> {
                    env.toast.show(
                        "Joined successfully!",
                        "Room ID & password will appear before the match starts.",
                    )
                    env.session.setUser(account.copy(balance = account.balance - current.entryFee))
                    refreshKey++
                }
                is ApiResult.Error ->
                    if (res.message.contains("insufficient", ignoreCase = true)) {
                        // Real popup instead of a toast — guides the user to Deposit
                        showInsufficient = true
                    } else {
                        env.toast.show("Join failed", res.message, ToastVariant.Destructive)
                    }
                is ApiResult.NetworkError ->
                    env.toast.show("Join failed", res.message, ToastVariant.Destructive)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(detailsRootBackground()),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            AppBar(
                mode = AppBarMode.Page,
                title = "Tournament Details",
                right = {
                    AppBarCircleButton(onClick = { handleShare() }) {
                        AreenaxIcon(
                            name = "share",
                            contentDescription = "Share tournament",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )

            // ---- Main (px-4 py-6 pb-28 gap-6) -----------------------------------
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 24.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // 1. Hero — h-[12.5rem] rounded-[1.5rem] + countdown pill
                DetailsHero(t = t)

                // 2. Summary cards (1–3 cols; never "Rs 0" cells)
                SummaryCards(t = t)

                // 3. Tabs — Overview / Rules / Prizes (underline style)
                Column {
                    DetailsTabs(tab = tab, onSelect = { tab = it })
                    // web tabs container mb-2 on top of the gap-6 rhythm
                    Spacer(Modifier.height(8.dp))
                }
                when (tab) {
                    "rules" -> RulesCard(t = t, rulesList = rulesList, onCopyRules = { copyText(t.rules, "Rules") })
                    "prizes" -> PrizesCard(t = t, rankPrize = rankPrize)
                    else -> OverviewCard(
                        t = t,
                        pct = pct,
                        roomOpen = roomOpen,
                        hasEntry = myEntry != null,
                        showRoom = showRoom,
                        onToggleRoom = { showRoom = !showRoom },
                        onCopy = copyText,
                    )
                }

                // 8. Participants card
                ParticipantsCard(t = t, entries = entries)
            }
        }

        // ---- Floating join bar (fixed bottom, gradient from surface-container-lowest)
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
                .padding(bottom = 16.dp), // pb-8
            contentAlignment = Alignment.Center,
        ) {
            JoinBar(
                t = t,
                myEntry = myEntry,
                myAction = myAction,
                joining = joining,
                proofUploading = proofUploading,
                onJoin = {
                    if (gate.requireAccount(user?.isGuest == true, "join this tournament")) {
                        handleJoin()
                    }
                },
                onPickProof = { proofPicker.launch() },
                onResults = { env.navigate(ScreenKeys.RESULTS, mapOf("tournamentId" to t.id)) },
                onCopy = copyText,
            )
        }

        // Support FAB — no bottom nav on this page (web bottom-6 right-6)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 24.dp),
        ) {
            SupportFab(offset = false)
        }
    }

    // Guest guard dialog
    GuestGateDialog(gate = gate, env = env)

    // Insufficient balance — solid dialog with a working Deposit action
    InsufficientBalanceDialog(
        open = showInsufficient,
        entryFee = t.entryFee,
        balance = user?.balance ?: 0.0,
        onOpenChange = { showInsufficient = it },
        tournamentName = t.name,
        env = env,
    )
}

/** Light root = bg-surface-container-low; dark = transparent (AppShell canvas). */
@Composable
private fun detailsRootBackground(): Color =
    if (areenaColors().isDark) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow

/** Web `#edf2f7` hairline; dark re-inks to outline-variant. */
@Composable
private fun rowHairline(): Color =
    if (areenaColors().isDark) MaterialTheme.colorScheme.outlineVariant else hairlineGray

/** Clipboard helper — "«label» copied to clipboard" (web copyText). */
private fun copyAndToast(env: NavEnv, text: String, label: String) {
    try {
        val cm = env.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("AREENAX", text))
        env.toast("${label} copied to clipboard")
    } catch (_: Exception) {
        env.toast.show("Copy failed", "Could not access the clipboard", ToastVariant.Destructive)
    }
}

/**
 * Share tournament details (room details included once joined) via the native
 * share sheet, falling back to the clipboard (web handleShare).
 */
private fun shareTournamentDetails(env: NavEnv, t: Tournament?, roomOpen: Boolean, hasEntry: Boolean) {
    val lines = mutableListOf<String>()
    lines.add(t?.let { "${it.name} on AREENAX" } ?: "Tournament on AREENAX")
    if (t != null) {
        if (t.prizePool > 0) {
            lines.add("Prize Pool: Rs ${formatMoney(t.prizePool)} · Entry Fee: Rs ${formatMoney(t.entryFee)}")
        } else {
            lines.add("Entry Fee: Rs ${formatMoney(t.entryFee)}")
        }
        if (hasEntry && roomOpen && (!t.roomId.isNullOrBlank() || !t.roomPassword.isNullOrBlank())) {
            t.roomId?.takeIf { it.isNotBlank() }?.let { lines.add("Room ID: $it") }
            t.roomPassword?.takeIf { it.isNotBlank() }?.let { lines.add("Room Password: $it") }
        }
    }
    val text = lines.joinToString("\n")
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        // env.context is the APPLICATION context — the chooser needs the new-task flag
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        env.context.startActivity(Intent.createChooser(intent, "AREENAX Tournament"))
    } catch (_: Exception) {
        try {
            val cm = env.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("AREENAX", text))
            env.toast("Tournament details copied to clipboard")
        } catch (_: Exception) {
            // clipboard unavailable — nothing else to do
        }
    }
}

/** Hero — cover image (bannerImage ?? game.image) else gradient + stadium icon. */
@Composable
private fun DetailsHero(t: Tournament) {
    val heroUrl = t.bannerImage?.takeIf { it.isNotBlank() }
        ?: t.game?.image?.takeIf { it.isNotBlank() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(200.dp) // h-[12.5rem]
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(24.dp), clip = false)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        if (heroUrl != null) {
            AsyncImage(
                model = heroUrl,
                contentDescription = t.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(bannerDarkStart, Color.Black),
                                start = Offset(0f, size.height),
                                end = Offset(size.width, 0f),
                            ),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                AreenaxIcon(
                    name = "stadium",
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).alpha(0.7f),
                    tint = Color.White,
                )
            }
        }

        // Countdown pill — top-left
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            ),
            shadowElevation = 2.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AreenaxIcon(
                    name = "schedule",
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                val labelTextStyle = Type.labelMd
                when (t.status) {
                    "UPCOMING" -> {
                        // A7-02: the countdown is its own leaf Text — it alone
                        // re-renders per second.
                        Text(
                            text = "Registration ends in ",
                            style = labelTextStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        com.areenax.app.core.ui.LiveCountdownText(
                            startTimeIso = t.startTime,
                            style = labelTextStyle,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    "ONGOING" -> Text(
                        text = "Match in progress",
                        style = labelTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    else -> Text(
                        text = "Match completed",
                        style = labelTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** Summary cards — Prize Pool (only >0), Entry Fee (always), Per Kill (only >0). */
@Composable
private fun SummaryCards(t: Tournament) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (t.prizePool > 0) {
            SummaryCell(
                icon = "emoji_events",
                filled = true,
                label = "Prize Pool",
                value = "Rs ${formatMoney(t.prizePool)}",
                modifier = Modifier.weight(1f),
            )
        }
        SummaryCell(
            icon = "payments",
            filled = false,
            label = "Entry Fee",
            value = "Rs ${formatMoney(t.entryFee)}",
            modifier = Modifier.weight(1f),
        )
        if (t.perKill > 0) {
            SummaryCell(
                icon = "my_location",
                filled = false,
                label = "Per Kill",
                value = "Rs ${formatMoney(t.perKill)}",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryCell(
    icon: String,
    filled: Boolean,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        ),
        shadowElevation = 1.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
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
                AreenaxIcon(
                    name = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    filled = filled,
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
            )
        }
    }
}

/** Underline tabs — active border-b-2 primary + text-primary (web Overview/Rules/Prizes). */
@Composable
private fun DetailsTabs(tab: String, onSelect: (String) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            listOf("overview" to "Overview", "rules" to "Rules", "prizes" to "Prizes").forEach { (key, label) ->
                val active = tab == key
                val interaction = remember { MutableInteractionSource() }
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(key) },
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            style = Type.labelLg,
                            color = if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                            ),
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(rowHairline()),
        )
    }
}

/** Overview tab — stat rows; ID & Pass reveals Room ID + Room Password with copy. */
@Composable
private fun OverviewCard(
    t: Tournament,
    pct: Float,
    roomOpen: Boolean,
    hasEntry: Boolean,
    showRoom: Boolean,
    onToggleRoom: () -> Unit,
    onCopy: (String, String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            StatRow(icon = "person", label = "Mode") {
                Text(t.mode, style = Type.labelLg, color = MaterialTheme.colorScheme.onSurface)
            }
            if (t.map.isNotBlank()) {
                StatRow(icon = "map", label = "Map") {
                    Text(t.map, style = Type.labelLg, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            StatRow(icon = "group", label = "Players") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .size(width = 96.dp, height = 8.dp)
                            .clip(CircleShape)
                            .background(areenaColors().surfaceContainerHighLavender),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(pct / 100f)
                                .height(8.dp)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                    Text(
                        text = "${t.currentPlayers}/${t.maxPlayers}",
                        style = Type.labelLg,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (t.loserPrize > 0) {
                StatRow(icon = "military_tech", label = "Loser Prize") {
                    Text(
                        "Rs ${formatMoney(t.loserPrize)}",
                        style = Type.labelLg,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (t.perspective.isNotBlank()) {
                StatRow(icon = "visibility", label = "Perspective") {
                    Text(
                        t.perspective,
                        style = Type.labelLg,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            StatRow(icon = "lock", label = "ID & Pass", noBorder = true) {
                if (roomOpen && hasEntry) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleRoom,
                        ),
                    ) {
                        Text(
                            text = if (showRoom) "Hide" else "View",
                            style = Type.labelLg,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        AreenaxIcon(
                            name = if (showRoom) "lock_open" else "lock",
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    AreenaxIcon(
                        name = "lock",
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            // Room details reveal
            if (showRoom && roomOpen && hasEntry) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(rowHairline()),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(areenaColors().surfaceContainerLavender.copy(alpha = 0.5f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RevealRow(
                        label = "Room ID",
                        value = t.roomId ?: "—",
                        copyContainer = MaterialTheme.colorScheme.surfaceContainerLowest,
                        copyValue = t.roomId,
                        copyLabel = "Room ID",
                        onCopy = onCopy,
                    )
                    RevealRow(
                        label = "Room Password",
                        value = t.roomPassword ?: "—",
                        copyContainer = MaterialTheme.colorScheme.surfaceContainerLowest,
                        copyValue = t.roomPassword,
                        copyLabel = "Room password",
                        onCopy = onCopy,
                    )
                }
            }
        }
    }
}

/** h-[4rem] stat row — icon + label left, value right, hairline divider unless last. */
@Composable
private fun StatRow(
    icon: String,
    label: String,
    noBorder: Boolean = false,
    value: @Composable () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f),
            ) {
                AreenaxIcon(
                    name = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(label, style = Type.bodyMd, color = MaterialTheme.colorScheme.onSurface)
            }
            value()
        }
        if (!noBorder) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(rowHairline()),
            )
        }
    }
}

@Composable
private fun RevealRow(
    label: String,
    value: String,
    copyContainer: Color,
    copyValue: String?,
    copyLabel: String,
    onCopy: (String, String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.labelMd, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = Type.labelLg.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (!copyValue.isNullOrBlank()) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(copyContainer)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        CircleShape,
                    )
                    .clickable { onCopy(copyValue!!, copyLabel) },
                contentAlignment = Alignment.Center,
            ) {
                AreenaxIcon(
                    name = "content_copy",
                    contentDescription = "Copy $label",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Rules tab — header + copy pill; numbered rules ("01", "02"…) or the empty copy. */
@Composable
private fun RulesCard(t: Tournament, rulesList: List<String>, onCopyRules: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Tournament Rules",
                    style = Type.headlineMd,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(percent = 50))
                        .clickable(onClick = onCopyRules)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AreenaxIcon(
                        name = "content_copy",
                        contentDescription = "Copy rules",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (rulesList.isEmpty()) {
                Text(
                    "No rules provided for this tournament.",
                    style = Type.bodyMd,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rulesList.forEachIndexed { index, rule ->
                        Column {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = (index + 1).toString().padStart(2, '0'),
                                    style = Type.labelLg,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(28.dp),
                                )
                                Text(
                                    text = rule,
                                    style = Type.bodyMd,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (index < rulesList.lastIndex) {
                                Spacer(Modifier.height(12.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(rowHairline()),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Prizes tab — Rank 1/2/3 (50/30/20), Per Kill + Loser Prize rows when configured. */
@Composable
private fun PrizesCard(t: Tournament, rankPrize: (Int) -> String) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Prize Distribution",
                style = Type.headlineMd,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (t.prizePool > 0) {
                    PrizeRow(
                        highlight = true,
                        leading = {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                AreenaxIcon(
                                    name = "emoji_events",
                                    contentDescription = null,
                                    filled = true,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        title = "Rank 1",
                        subtitle = "Winner Trophy",
                        value = "Rs ${rankPrize(50)}",
                        valueColor = MaterialTheme.colorScheme.primary,
                    )
                    PrizeRow(
                        highlight = false,
                        leading = { RankNumberCircle("2") },
                        title = "Rank 2",
                        subtitle = null,
                        value = "Rs ${rankPrize(30)}",
                        valueColor = MaterialTheme.colorScheme.onSurface,
                    )
                    PrizeRow(
                        highlight = false,
                        leading = { RankNumberCircle("3") },
                        title = "Rank 3",
                        subtitle = null,
                        value = "Rs ${rankPrize(20)}",
                        valueColor = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    // Pool not configured — muted note instead of "Rs 0" rows
                    PrizeRow(
                        highlight = true,
                        leading = {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(areenaColors().surfaceContainerHighLavender),
                                contentAlignment = Alignment.Center,
                            ) {
                                AreenaxIcon(
                                    name = "emoji_events",
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        title = "Prize pool will be announced",
                        subtitle = null,
                        value = null,
                        valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (t.perKill > 0) {
                    PrizeRow(
                        highlight = false,
                        leading = {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(areenaColors().surfaceContainerHighLavender),
                                contentAlignment = Alignment.Center,
                            ) {
                                AreenaxIcon(
                                    name = "my_location",
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        title = "Per Kill",
                        subtitle = null,
                        value = "Rs ${formatMoney(t.perKill)}",
                        valueColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (t.loserPrize > 0) {
                    PrizeRow(
                        highlight = false,
                        leading = {
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(areenaColors().surfaceContainerHighLavender),
                                contentAlignment = Alignment.Center,
                            ) {
                                AreenaxIcon(
                                    name = "military_tech",
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        title = "Loser Prize",
                        subtitle = null,
                        value = "Rs ${formatMoney(t.loserPrize)}",
                        valueColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun RankNumberCircle(number: String) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(areenaColors().surfaceContainerHighLavender),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number,
            style = Type.labelLg,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrizeRow(
    highlight: Boolean,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    value: String?,
    valueColor: Color,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (highlight) {
                    areenaColors().surfaceContainerLavender.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                },
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp),
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            leading()
            Column {
                Text(title, style = Type.labelLg, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = Type.labelMd,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (value != null) {
            Text(
                value,
                style = Type.headlineMd,
                color = valueColor,
            )
        }
    }
}

/** Participants card — header with n / max, initial-circle rows. */
@Composable
private fun ParticipantsCard(t: Tournament, entries: List<TournamentEntry>) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Participants",
                    style = Type.headlineMd,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${entries.size} / ${t.maxPlayers}",
                    style = Type.labelMd,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(rowHairline()),
            )
            if (entries.isEmpty()) {
                Text(
                    "No participants yet. Be the first to join!",
                    style = Type.bodyMd,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                entries.forEachIndexed { index, entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = (entry.user?.gameName ?: "?").take(1).uppercase(),
                                style = Type.bodyLg.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = entry.user?.gameName ?: "Player",
                                style = Type.labelLg,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "UID: ${entry.user?.uid ?: "—"}",
                                style = Type.labelMd,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (index < entries.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(rowHairline()),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Floating bottom bar content — the per-state machine widget (web join bar):
 * joined users get the white card (Room rows / announce line / Results /
 * proof upload-or-submitted / Match Processing / Joined / Completed chips);
 * not joined get Join Now / Slots Full / locked label chips.
 */
@Composable
private fun JoinBar(
    t: Tournament,
    myEntry: TournamentEntry?,
    myAction: TournamentCardAction,
    joining: Boolean,
    proofUploading: Boolean,
    onJoin: () -> Unit,
    onPickProof: () -> Unit,
    onResults: () -> Unit,
    onCopy: (String, String) -> Unit,
) {
    if (myEntry != null) {
        // Joined — state machine: Room → Live → Results, JOINED in Balance-chip style
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            ),
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (myAction.kind == TournamentCardActionKind.ROOM) {
                    BarRoomRow(
                        label = "Room ID",
                        value = t.roomId,
                        copyValue = t.roomId,
                        copyLabel = "Room ID",
                        onCopy = onCopy,
                    )
                    BarRoomRow(
                        label = "Room Password",
                        value = t.roomPassword,
                        copyValue = t.roomPassword,
                        copyLabel = "Room password",
                        onCopy = onCopy,
                    )
                    t.roomExpiresAt?.let { expiry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AreenaxIcon(
                                name = "timer_off",
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Visible until ${formatDateTime(expiry)}",
                                style = Type.labelMd,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                when (myAction.kind) {
                    TournamentCardActionKind.ROOM -> Unit // rows above
                    TournamentCardActionKind.JOINED -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        AreenaxIcon(
                            name = "schedule",
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Room ID & Password will be announced before the match starts.",
                            style = Type.labelMd,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    TournamentCardActionKind.RESULTS -> Button(
                        onClick = onResults,
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        AreenaxIcon(
                            name = "leaderboard",
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "RESULTS",
                            style = Type.bodyLg.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.025.em,
                            ),
                        )
                    }

                    TournamentCardActionKind.PROOF ->
                        if (t.myProofSubmitted == true) {
                            // ONE-TIME submission done — neutral status chip
                            StatePill(
                                container = areenaColors().balanceChip,
                                contentColor = MaterialTheme.colorScheme.primary,
                                label = "RESULT SUBMITTED",
                                withShadow = true,
                            ) {
                                AreenaxIcon(
                                    name = "check_circle",
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    filled = true,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AreenaxIcon(
                                    name = "hourglass_top",
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Wait for approval — the admin will review your result",
                                    style = Type.labelMd,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Button(
                                onClick = onPickProof,
                                enabled = !proofUploading,
                                shape = RoundedCornerShape(percent = 50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                            ) {
                                if (proofUploading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        trackColor = Color.Transparent,
                                    )
                                } else {
                                    AreenaxIcon(
                                        name = "upload",
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "SUBMIT RESULT PROOF",
                                    style = Type.bodyLg.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.025.em,
                                    ),
                                )
                            }
                        }

                    TournamentCardActionKind.LIVE -> StatePill(
                        container = areenaColors().balanceChip,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        label = "MATCH PROCESSING",
                        withShadow = true,
                        leading = { LivePingDot() },
                    )

                    TournamentCardActionKind.COMPLETED -> StatePill(
                        container = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        label = myAction.label,
                    ) {
                        AreenaxIcon(
                            name = "hourglass_top",
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> StatePill(
                        container = areenaColors().balanceChip,
                        contentColor = MaterialTheme.colorScheme.primary,
                        label = "JOINED",
                        withShadow = true,
                    ) {
                        AreenaxIcon(
                            name = "check_circle",
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            filled = true,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    } else {
        when (myAction.kind) {
            TournamentCardActionKind.JOIN -> {
                // Not joined + open before start — normal Join CTA
                Button(
                    onClick = onJoin,
                    enabled = !joining,
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 300.dp) // max-w-[18.75rem]
                        .heightIn(min = 48.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(percent = 50),
                            clip = false,
                            ambientColor = Color(0x14000000),
                            spotColor = Color(0x14000000),
                        ),
                ) {
                    if (joining) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            trackColor = Color.Transparent,
                        )
                    } else {
                        Text(
                            text = if (t.entryFee > 0) {
                                "Join Now - Rs ${formatMoney(t.entryFee)}"
                            } else {
                                "Join Now - Free"
                            },
                            style = Type.labelLg,
                        )
                    }
                }
            }

            TournamentCardActionKind.SLOTS_FULL -> StatePill(
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                label = "SLOTS FULL",
            ) {
                AreenaxIcon(
                    name = "groups",
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> StatePill(
                container = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                label = myAction.label,
            ) {
                AreenaxIcon(
                    name = "hourglass_top",
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Bottom-bar Room ID / Password row with the lavender copy button. */
@Composable
private fun BarRoomRow(
    label: String,
    value: String?,
    copyValue: String?,
    copyLabel: String,
    onCopy: (String, String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.labelMd, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value ?: "Will be announced",
                style = Type.labelLg.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (!copyValue.isNullOrBlank()) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(areenaColors().surfaceContainerLavender)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        CircleShape,
                    )
                    .clickable { onCopy(copyValue!!, copyLabel) },
                contentAlignment = Alignment.Center,
            ) {
                AreenaxIcon(
                    name = "content_copy",
                    contentDescription = "Copy $label",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * h-12 uppercase pill (Result Submitted / Match Processing / Joined / Completed /
 * Slots Full). Optional card-shadow for the balance-chip variants (web card-shadow).
 */
@Composable
private fun StatePill(
    container: Color,
    contentColor: Color,
    label: String,
    withShadow: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (withShadow) Modifier.shadow(4.dp, shape, clip = false) else Modifier)
            .clip(shape)
            .background(container)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), shape),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            style = Type.bodyLg.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.025.em),
            color = contentColor,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** Red animate-ping halo + solid dot (web .animate-ping on the Match Processing chip). */
@Composable
private fun LivePingDot() {
    val transition = rememberInfiniteTransition(label = "livePing")
    val pingAlpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "livePingAlpha",
    )
    val pingScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "livePingScale",
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(10.dp)
                .scale(pingScale)
                .alpha(pingAlpha)
                .background(MaterialTheme.colorScheme.error, CircleShape),
        )
        Box(
            Modifier
                .size(10.dp)
                .background(MaterialTheme.colorScheme.error, CircleShape),
        )
    }
}
