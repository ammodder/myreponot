package com.areenax.app.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.R
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.theme.DarkInversePrimary
import com.areenax.app.core.theme.mutedSlate
import com.areenax.app.core.theme.primaryDeep
import com.areenax.app.core.ui.AppBarCircleButton
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AreenaxSpinner
import com.areenax.app.core.ui.CenteredModalScaffold
import com.areenax.app.core.ui.EmptyState
import com.areenax.app.core.ui.GuestGateDialog
import com.areenax.app.core.ui.QrSheet
import com.areenax.app.core.ui.QrTab
import com.areenax.app.core.ui.QrTarget
import com.areenax.app.core.ui.TeamResultCard
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.cardShadow
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.primaryGlow
import com.areenax.app.core.ui.rememberGuestGate
import com.areenax.app.core.util.timeAgo
import com.areenax.app.data.QrTeamLookup
import com.areenax.app.data.Team
import com.areenax.app.data.TeamJoinRequestActionBody
import com.areenax.app.data.TeamJoinRequestBody
import com.areenax.app.data.TeamJoinRequestRow
import com.areenax.app.data.TeamStats
import kotlinx.coroutines.launch

/*
 * MyTeamScreen — key `myTeam` (SPEC/01 D1, build record 3-d):
 *  - no-team state EXACT (myteam.html): radial gradient bg (primary-fixed-dim
 *    top-right + emerald bottom-left over the background), glass header w/
 *    bell red dot, centered "No Team Yet" card → guest gate "create a team"
 *    → teamCreation.
 *  - with-team state (teamcreationdone.html): uppercase team hero + tag chip,
 *    Matches/Wins/Kills stat cards (GET /team stats), member rows (48dp
 *    avatar-letter, gameName · fullName · UID, OWNER → "Owner" chip /
 *    MEMBER → "Member" chip), customer-support note, danger "Leave Team"
 *    → DELETE /team → toast + refresh (owner = disband).
 *  - Q5 owner inbox (web MyTeamScreen.tsx): "Join Requests (n)" card above
 *    Team Members — GET /team/join-requests (owner-only), per-row Accept /
 *    Decline → POST /team/join-requests/{id} {action:"accept"|"decline"}
 *    → toast + reload; members-header QR button → QrSheet(team, mine).
 *  - Q5 "Team Connect" deep-link popup (params.qrTeam): GET /qr/lookup
 *    → TeamResultCard → "Request to Join" (POST /team/join-requests {teamId},
 *    guest-gated) / "Requested" / member chip + "Go to My Team".
 */

@Composable
fun MyTeamScreen(env: NavEnv) {
    val user by env.session.user.collectAsState()
    val extended = areenaColors()

    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var team by remember { mutableStateOf<Team?>(null) }
    var stats by remember { mutableStateOf(TeamStats()) }
    var leaving by remember { mutableStateOf(false) }
    val gate = rememberGuestGate()
    val scope = rememberCoroutineScope()

    // Q5: owner-only incoming join requests (GET /team/join-requests)
    var joinRequests by remember { mutableStateOf<List<TeamJoinRequestRow>>(emptyList()) }
    var joinBusyId by remember { mutableStateOf<String?>(null) }

    // Q5: Team QR sheet + deep-linked "Team Connect" popup (params.qrTeam)
    var qrOpen by remember { mutableStateOf(false) }
    var teamLookup by remember { mutableStateOf<QrTeamLookup?>(null) }
    var teamClosedUid by remember { mutableStateOf<String?>(null) }
    var teamActionBusy by remember { mutableStateOf(false) }

    suspend fun loadTeam() {
        when (val res = safeCall { env.api.team() }) {
            is ApiResult.Success -> {
                team = res.data.team
                stats = res.data.stats
                loadError = false
            }
            else -> loadError = true
        }
        loading = false
    }

    LaunchedEffect(Unit) { loadTeam() }

    val isOwner = team?.ownerId == user?.id
    val unread by env.unread.unread.collectAsState()

    // Owners pull their pending join requests whenever the team (re)loads
    suspend fun loadJoinRequests() {
        // secondary data — keep whatever we already have on failure (web parity)
        when (val res = safeCall { env.api.teamJoinRequests() }) {
            is ApiResult.Success -> joinRequests = res.data.requests
            else -> {}
        }
    }

    LaunchedEffect(team, isOwner) {
        if (team != null && isOwner) loadJoinRequests()
    }

    /** Owner accepts / declines a pending join request (web respondJoin). */
    fun respondJoin(row: TeamJoinRequestRow, action: String) {
        if (joinBusyId != null) return
        joinBusyId = row.id
        scope.launch {
            when (val res = safeCall {
                env.api.respondTeamJoinRequest(row.id, TeamJoinRequestActionBody(action = action))
            }) {
                is ApiResult.Success -> {
                    env.toast(if (action == "accept") "Member added!" else "Request declined")
                }
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    env.toast.show(
                        "Could not update request",
                        description = (res as? ApiResult.Error)?.message ?: (res as? ApiResult.NetworkError)?.message,
                        variant = ToastVariant.Destructive,
                    )
                }
            }
            joinBusyId = null
            loadTeam()
            loadJoinRequests()
        }
    }

    /* ---- Q5 QR deep-link: params.qrTeam opens the Team Connect popup ---- */
    val qrTeamParam = (env.params["qrTeam"] as? String)?.takeIf { it.isNotBlank() }
    val showTeamPopup = qrTeamParam != null && teamClosedUid != qrTeamParam

    fun closeTeamPopup() {
        teamLookup = null
        teamClosedUid = qrTeamParam
    }

    LaunchedEffect(qrTeamParam, teamClosedUid) {
        if (qrTeamParam == null || teamClosedUid == qrTeamParam) return@LaunchedEffect
        when (val res = safeCall { env.api.qrLookup(teamId = qrTeamParam) }) {
            is ApiResult.Success -> {
                val lookup = res.data.asTeam
                if (lookup != null) {
                    teamLookup = lookup
                } else {
                    env.toast.show(
                        "Could not open team",
                        description = "No team found with this QR code.",
                        variant = ToastVariant.Destructive,
                    )
                    teamClosedUid = qrTeamParam
                }
            }
            is ApiResult.Error, is ApiResult.NetworkError -> {
                val status = (res as? ApiResult.Error)?.code ?: 0
                val message = (res as? ApiResult.Error)?.message ?: (res as? ApiResult.NetworkError)?.message
                env.toast.show(
                    "Could not open team",
                    description = if (status == 404) {
                        "No team found with this QR code."
                    } else {
                        message ?: "Please try again."
                    },
                    variant = ToastVariant.Destructive,
                )
                teamClosedUid = qrTeamParam
            }
        }
    }

    /** Deep-link popup: send a join request to the scanned team (web handlePopupJoin). */
    fun handlePopupJoin(data: QrTeamLookup) {
        if (teamActionBusy) return
        if (!gate.requireAccount(user?.isGuest == true, "join a team")) return
        teamActionBusy = true
        scope.launch {
            when (val res = safeCall { env.api.requestTeamJoin(TeamJoinRequestBody(teamId = data.team.id)) }) {
                is ApiResult.Success -> {
                    env.toast("Join request sent to the team owner!")
                    teamLookup = data.copy(joinRequested = true)
                }
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    env.toast.show(
                        "Could not send request",
                        description = (res as? ApiResult.Error)?.message ?: (res as? ApiResult.NetworkError)?.message,
                        variant = ToastVariant.Destructive,
                    )
                }
            }
            teamActionBusy = false
        }
    }

    fun goToMyTeam() {
        closeTeamPopup()
        scope.launch { loadTeam() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                if (!extended.isDark) {
                    if (team == null) {
                        // myteam.html .radial-gradient-bg
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0x66B4C5FF), Color.Transparent),
                                center = Offset(size.width, 0f),
                                radius = size.width * 0.9f,
                            ),
                        )
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0x336CF8BB), Color.Transparent),
                                center = Offset(0f, size.height),
                                radius = size.width * 0.9f,
                            ),
                        )
                    } else {
                        // teamcreationdone.html .ambient-bg (dual radial)
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0x80DBE1FF), Color.Transparent),
                                center = Offset(size.width / 2f, 0f),
                                radius = size.width * 0.85f,
                            ),
                        )
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0x66D3E4FE), Color.Transparent),
                                center = Offset(size.width, size.height),
                                radius = size.width * 0.75f,
                            ),
                        )
                    }
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            MyTeamGlassHeader(env = env, unread = unread)

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AreenaxSpinner()
                }

                loadError -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    EmptyState(message = "Could not load your team. Pull to retry.", icon = "groups")
                    Surface(
                        onClick = { scope.launch { loadTeam() } },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 40.dp)
                            .cardShadow(),
                    ) {
                        Text(
                            text = "Try Again",
                            style = Type.labelLg,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        )
                    }
                }

                team == null -> {
                    // ===== No Team Yet — exact myteam.html =====
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 16.dp, end = 16.dp, top = 96.dp, bottom = 128.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "No Team Yet",
                                style = Type.headlineMd,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Create your squad team to join Squad tournaments!",
                                style = Type.bodyLg,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(max = 280.dp),
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        val createInteraction = remember { MutableInteractionSource() }
                        Surface(
                            onClick = {
                                if (gate.requireAccount(user?.isGuest == true, "create a team")) {
                                    env.navigate(ScreenKeys.TEAM_CREATION)
                                }
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .widthIn(max = 240.dp)
                                .primaryGlow(CircleShape)
                                .pressScale(createInteraction, 0.95f),
                            interactionSource = createInteraction,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AreenaxIcon(
                                    name = "add",
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Create Team",
                                    style = Type.bodyLg,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }

                else -> {
                    // ===== With team — exact teamcreationdone.html + stat cards =====
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        // Team Identity Hero (+ optional tag chip)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp, bottom = 32.dp)
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = team?.name?.uppercase().orEmpty(),
                                style = Type.heroAmount.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    lineHeight = 40.sp,
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center,
                            )
                            val tag = team?.tag
                            if (!tag.isNullOrBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = extended.primaryFixed, // A8-06
                                ) {
                                    Text(
                                        text = tag.uppercase(),
                                        style = Type.labelSm.copy(fontSize = 11.sp),
                                        color = if (extended.isDark) DarkInversePrimary else primaryDeep,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }

                        // Matches / Wins / Kills stat cards
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TeamStatCard("Matches", stats.matches, Modifier.weight(1f))
                            TeamStatCard("Wins", stats.wins, Modifier.weight(1f))
                            TeamStatCard("Kills", stats.kills, Modifier.weight(1f))
                        }

                        Spacer(Modifier.height(24.dp))

                        // Q5: owner inbox — pending join requests for my team
                        if (isOwner && joinRequests.isNotEmpty()) {
                            Column(
                                Modifier.padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = "Join Requests (${joinRequests.size})",
                                    style = Type.labelSm,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp).semantics { heading() }, // R5
                                )
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .cardShadow(RoundedCornerShape(24.dp)),
                                ) {
                                    Column(
                                        Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        joinRequests.forEach { r ->
                                            JoinRequestRow(
                                                row = r,
                                                busy = joinBusyId == r.id,
                                                onAccept = { respondJoin(r, "accept") },
                                                onDecline = { respondJoin(r, "decline") },
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }

                        // Team Members (+ members-header QR button)
                        Column(
                            Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Team Members",
                                    style = Type.labelSm,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp).semantics { heading() }, // R5
                                )
                                Spacer(Modifier.weight(1f))
                                val qrInteraction = remember { MutableInteractionSource() }
                                Surface(
                                    onClick = { qrOpen = true },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    interactionSource = qrInteraction,
                                    modifier = Modifier
                                        .pressScale(qrInteraction, 0.95f)
                                        .cardShadow(),
                                ) {
                                    AreenaxIcon(
                                        name = "qr_code_2",
                                        contentDescription = "Team QR code — scan or share",
                                        modifier = Modifier
                                            .padding(9.dp)
                                            .size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .cardShadow(RoundedCornerShape(24.dp)),
                            ) {
                                Column {
                                    val members = team?.members.orEmpty()
                                    members.forEachIndexed { i, m ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = m.user.gameName.firstOrNull()?.toString() ?: "?",
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    style = Type.headlineLg.copy(fontSize = 18.sp),
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                            Spacer(Modifier.width(16.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    text = m.user.gameName,
                                                    style = Type.bodyLg,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = if (m.user.fullName.isNotBlank()) {
                                                        "${m.user.fullName} · UID: ${m.user.uid}"
                                                    } else {
                                                        "UID: ${m.user.uid}"
                                                    },
                                                    style = Type.labelSm.copy(fontWeight = FontWeight.Normal),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            if (m.role == "OWNER") {
                                                TeamRoleChip(text = "Owner", owner = true)
                                            } else {
                                                TeamRoleChip(text = "Member", owner = false)
                                            }
                                        }
                                        if (i < members.lastIndex) {
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(1.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                                    ),
                                            )
                                        }
                                    }
                                }
                            }

                            // Customer-support note
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp)
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.Top,
                            ) {
                                AreenaxIcon(
                                    name = "info",
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = mutedSlate,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Note: To add or remove team members, or to delete your team, " +
                                        "please contact Customer Support.",
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, // A9-01: was #94A3B8 → 2.56:1
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        // Danger action — Leave / Disband (DELETE /team)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp, bottom = 96.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            val leaveInteraction = remember { MutableInteractionSource() }
                            Surface(
                                onClick = {
                                    if (leaving) return@Surface
                                    leaving = true
                                    scope.launch {
                                        when (val res = safeCall { env.api.leaveTeam() }) {
                                            is ApiResult.Success -> {
                                                env.toast(
                                                    if (isOwner) "Team disbanded" else "You left the team",
                                                )
                                                loadTeam()
                                            }
                                            is ApiResult.Error, is ApiResult.NetworkError -> {
                                                env.toast.show(
                                                    "Failed to leave team",
                                                    description = (res as? ApiResult.Error)?.message ?: (res as? ApiResult.NetworkError)?.message,
                                                    variant = ToastVariant.Destructive,
                                                )
                                            }
                                        }
                                        leaving = false
                                    }
                                },
                                shape = CircleShape,
                                color = Color.Transparent,
                                interactionSource = leaveInteraction,
                                modifier = Modifier.pressScale(leaveInteraction, 0.95f),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                ) {
                                    AreenaxIcon(
                                        name = "logout",
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = when {
                                            leaving -> "Leaving…"
                                            isOwner -> "Leave Team (Disband)"
                                            else -> "Leave Team"
                                        },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Q5: Team QR sheet — share my team QR or scan someone else's (web :349-359)
    if (team != null) {
        QrSheet(
            open = qrOpen,
            onClose = { qrOpen = false },
            env = env,
            target = QrTarget.TEAM,
            teamId = team?.id,
            teamName = team?.name,
            initialTab = QrTab.MINE,
        )
    }

    // Q5: Team Connect popup — deep-linked team (params.qrTeam; web :361-399)
    CenteredModalScaffold(open = showTeamPopup, onDismiss = { closeTeamPopup() }) {
        val lookup = teamLookup
        if (lookup != null && lookup.team.id == qrTeamParam) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AreenaxIcon(
                    name = "groups",
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Team Connect".uppercase(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }, // R5
                )
            }
            TeamResultCard(
                data = lookup,
                busy = teamActionBusy,
                env = env,
                onJoin = { handlePopupJoin(lookup) },
            )
            if (lookup.isMember || lookup.joinRequested) {
                Surface(
                    onClick = { goToMyTeam() },
                    shape = CircleShape,
                    color = Color.Transparent,
                ) {
                    Text(
                        text = "Go to My Team",
                        style = Type.labelLg,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        } else {
            // Resolving — spinner + "Looking up team…"
            AreenaxSpinner()
            Text(
                text = "Looking up team…",
                style = Type.bodyMd,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    GuestGateDialog(gate, env)
}

/** One owner-inbox row — 40dp avatar letter, player info, Accept / Decline. */
@Composable
private fun JoinRequestRow(
    row: TeamJoinRequestRow,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (row.user.gameName.ifBlank { "?" }).firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = row.user.gameName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "UID: ${row.user.uid}",
                style = Type.labelSm.copy(fontWeight = FontWeight.Normal),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = timeAgo(row.createdAt),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val acceptInteraction = remember { MutableInteractionSource() }
        Surface(
            onClick = { if (!busy) onAccept() },
            enabled = !busy,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            interactionSource = acceptInteraction,
            modifier = Modifier.pressScale(acceptInteraction, 0.95f),
        ) {
            Text(
                text = if (busy) "…" else "Accept",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
        val declineInteraction = remember { MutableInteractionSource() }
        Surface(
            onClick = { if (!busy) onDecline() },
            enabled = !busy,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            interactionSource = declineInteraction,
            modifier = Modifier.pressScale(declineInteraction, 0.95f),
        ) {
            Text(
                text = "Decline",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

/** Glass top bar (myteam.html): translucent surface, back circle, primary title, bell + red dot. */
@Composable
private fun MyTeamGlassHeader(env: NavEnv, unread: Int) {
    val extended = areenaColors()
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppBarCircleButton(onClick = { env.goBack() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "My Team",
                    style = Type.headlineLgMobile,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                Box {
                    AppBarCircleButton(onClick = { env.navigate(ScreenKeys.NOTIFICATIONS) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_notifications),
                            contentDescription = "Notifications",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (unread > 0) {
                        // Red dot — w-2 h-2 bg-error rounded-full ring-2 ring-surface (myteam.html)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-6).dp, y = 6.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        )
                    }
                }
            }
            if (!extended.isDark) {
                // border-b border-outline-variant/30
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                )
            }
        }
    }
}

/** Matches/Wins/Kills stat card (with-team state). */
@Composable
private fun TeamStatCard(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        modifier = modifier.cardShadow(RoundedCornerShape(16.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label.uppercase(),
                style = Type.labelSm,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value.toString(),
                style = Type.headlineLgMobile,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** OWNER → "Owner" outlined-primary chip / MEMBER → muted chip. */
@Composable
private fun TeamRoleChip(text: String, owner: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (owner) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = if (owner) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Text(
            text = text.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = if (owner) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}
