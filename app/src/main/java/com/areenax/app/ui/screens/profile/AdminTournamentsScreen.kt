package com.areenax.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.AreenaxShapes
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AreenaxPillField
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.AppBarMode
import com.areenax.app.core.ui.AreenaxSpinner
import com.areenax.app.core.ui.DateTimePickerModal
import com.areenax.app.core.ui.EmptyState
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.util.formatDateTime
import com.areenax.app.core.util.parseEpochMillis
import com.areenax.app.data.AdminResultEntry
import com.areenax.app.data.AdminResultsBody
import com.areenax.app.data.AdminRoomBody
import com.areenax.app.data.AdminTournamentsResponse
import com.areenax.app.data.OkResponse
import com.areenax.app.data.Roles
import com.areenax.app.data.Tournament
import com.areenax.app.data.TournamentStatuses
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * AdminTournamentsScreen — key `adminDashboard` (SPEC/01 E9 context). Port of
 * `profile/AdminTournamentsScreen.tsx` ("Tournament Manager" — the dedicated
 * admin tournament-management screen).
 *
 * The React file targets the OLDER room contract (`roomVisibleMinutes` +
 * `publish` flag + `roomReleasedAt`), which the live server no longer accepts;
 * the current server + shared Api (AdminRoomBody) expect
 * `{roomId, roomPassword, roomExpiresAt}` and the results route expects
 * `{entries:[…]}`. This port therefore keeps the Tournament Manager UI
 * (legend card, expandable cards with mode/status/Room-live/Results-out
 * chips, Room Details / Results tabs, per-entry rank/kills/prize inputs) and
 * wires it to the LIVE contract:
 *   - Room: Room ID + Room Password + expiry datetime (DateTimePickerModal,
 *     clearable = "visible forever") → POST /admin/tournaments/:id/room.
 *   - Results: rank/kills/prize per player → POST /admin/tournaments/:id/results
 *     {entries} (blank fields fall back to the entry's current values).
 * Deny state (error mentioning "admin", or non-panel role) → EmptyState
 * "Admin access required." Empty list → EmptyState "No tournaments yet."
 * (emoji_events).
 */
@Composable
fun AdminTournamentsScreen(env: NavEnv) {
    val user by env.session.user.collectAsState()
    var tournaments by remember { mutableStateOf<List<Tournament>?>(null) }
    var denied by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf("room") }
    var saving by remember { mutableStateOf(false) }

    // Room forms per tournament (live contract: expiry datetime instead of minutes)
    val roomForm = remember { mutableStateMapOf<String, MutableMap<String, String>>() }
    // Results forms per entry: entryId → { rank, kills, prize }
    val resultForm = remember { mutableStateMapOf<String, MutableMap<String, String>>() }
    var expiryPickerFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        when (val res = safeCall<AdminTournamentsResponse> { env.api.adminTournaments() }) {
            is ApiResult.Success -> {
                val list = res.data.tournaments
                tournaments = list
                roomForm.clear()
                list.forEach { t ->
                    roomForm[t.id] = mutableMapOf(
                        "roomId" to (t.roomId ?: ""),
                        "roomPassword" to (t.roomPassword ?: ""),
                        "roomExpiresAt" to (t.roomExpiresAt?.parseEpochMillis()?.let { millis ->
                            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime().toString().take(16)
                        } ?: ""),
                    )
                }
            }
            is ApiResult.Error -> {
                if (res.message.lowercase().contains("admin")) denied = true
                tournaments = emptyList()
            }
            is ApiResult.NetworkError -> tournaments = emptyList()
        }
    }

    fun setRoomField(id: String, key: String, value: String) {
        roomForm.getOrPut(id) { mutableMapOf("roomId" to "", "roomPassword" to "", "roomExpiresAt" to "") }[key] = value
    }

    fun setResultField(entryId: String, key: String, value: String) {
        resultForm.getOrPut(entryId) { mutableMapOf("rank" to "", "kills" to "", "prize" to "") }[key] = value
    }

    fun saveRoom(t: Tournament) {
        if (saving) return
        val form = roomForm[t.id]
        val id = form?.get("roomId")?.trim().orEmpty()
        val pass = form?.get("roomPassword")?.trim().orEmpty()
        // A4-05: the server requires BOTH (admin/tournaments/[id]/room:35-37);
        // the old "and/or" gate forwarded half-filled forms to a guaranteed 400.
        if (id.isEmpty() || pass.isEmpty()) {
            env.toast.show(title = "Enter a Room ID and Password", variant = ToastVariant.Destructive)
            return
        }
        saving = true
        CoroutineScope(Dispatchers.Main).launch {
            val expiresIso = form?.get("roomExpiresAt").takeUnless { it.isNullOrBlank() }?.let { local ->
                runCatching {
                    java.time.LocalDateTime.parse(local).atZone(ZoneId.systemDefault()).toInstant().toString()
                }.getOrNull()
            }
            when (val res = safeCall<OkResponse> {
                env.api.adminTournamentRoom(
                    t.id,
                    AdminRoomBody(roomId = id, roomPassword = pass, roomExpiresAt = expiresIso),
                )
            }) {
                is ApiResult.Success -> {
                    env.toast.show(
                        title = "Room details saved",
                        description = "Saved — publish is part of the live room contract; joined players see the details as soon as they are saved.",
                    )
                }
                is ApiResult.Error -> env.toast.show(
                    title = "Save failed",
                    description = res.message,
                    variant = ToastVariant.Destructive,
                )
                is ApiResult.NetworkError -> env.toast.show(
                    title = "Save failed",
                    description = res.message,
                    variant = ToastVariant.Destructive,
                )
            }
            saving = false
        }
    }

    fun publishResults(t: Tournament) {
        if (saving) return
        val rows = t.entries.map { e ->
            val f = resultForm[e.id]
            AdminResultEntry(
                entryId = e.id,
                rank = f?.get("rank")?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0,
                kills = f?.get("kills")?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: e.kills,
                prize = f?.get("prize")?.takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: e.prize,
            )
        }
        saving = true
        CoroutineScope(Dispatchers.Main).launch {
            when (val res = safeCall<OkResponse> {
                env.api.adminTournamentResults(t.id, AdminResultsBody(entries = rows))
            }) {
                is ApiResult.Success -> {
                    env.toast.show(
                        title = "Results published!",
                        description = "Players were notified and prizes credited.",
                    )
                }
                is ApiResult.Error -> env.toast.show(
                    title = "Publish failed",
                    description = res.message,
                    variant = ToastVariant.Destructive,
                )
                is ApiResult.NetworkError -> env.toast.show(
                    title = "Publish failed",
                    description = res.message,
                    variant = ToastVariant.Destructive,
                )
            }
            saving = false
        }
    }

    val sorted = (tournaments ?: emptyList()).sortedBy { t ->
        when (t.status) {
            TournamentStatuses.UPCOMING -> 0
            TournamentStatuses.ONGOING -> 1
            TournamentStatuses.COMPLETED -> 2
            else -> 3
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 112.dp),
    ) {
        AppBar(mode = AppBarMode.Page, title = "Tournament Manager")

        when {
            denied || !Roles.isAdminPanelRole(user?.role) -> {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
                    EmptyState(message = "Admin access required.", icon = "lock")
                }
            }
            tournaments == null -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AreenaxSpinner(size = 32)
                }
            }
            else -> {
                Column(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Legend
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AreenaxIcon(
                                    name = "info",
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "Join-flow control",
                                    style = Type.labelLg,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Text(
                                text = "Publish room details to give joined players a timed \u201CRoom ID & Password\u201D button — it automatically becomes \u201CResults\u201D when the window ends. Publish results to credit prizes and open the results screen.",
                                style = Type.bodyMd,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (sorted.isEmpty()) {
                        EmptyState(message = "No tournaments yet.", icon = "emoji_events")
                    } else {
                        sorted.forEach { t ->
                            val expanded = expandedId == t.id
                            val form = roomForm[t.id]
                            val playedCount = t.entries.count { it.status == "PLAYED" }
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column {
                                    // Card header
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) {
                                                expandedId = if (expanded) null else t.id
                                            }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Box(
                                            Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            AreenaxIcon(
                                                name = "emoji_events",
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = t.name,
                                                style = Type.labelLg,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Row(
                                                Modifier.padding(top = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                ManagerChip(
                                                    text = t.mode,
                                                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                ManagerChip(
                                                    text = t.status,
                                                    container = when (t.status) {
                                                        TournamentStatuses.UPCOMING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                                        TournamentStatuses.ONGOING -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                                        TournamentStatuses.COMPLETED -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                                        else -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                                    },
                                                    content = when (t.status) {
                                                        TournamentStatuses.UPCOMING -> MaterialTheme.colorScheme.primary
                                                        TournamentStatuses.ONGOING -> MaterialTheme.colorScheme.secondary
                                                        TournamentStatuses.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
                                                        else -> MaterialTheme.colorScheme.error
                                                    },
                                                )
                                                Text(
                                                    text = "${t.currentPlayers}/${t.maxPlayers} players",
                                                    style = Type.labelMd,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                if (t.roomExpiresAt != null && (t.roomId != null || t.roomPassword != null)) {
                                                    val expiryMillis = t.roomExpiresAt?.parseEpochMillis()
                                                    val windowOpen = expiryMillis != null && expiryMillis > System.currentTimeMillis()
                                                    ManagerChip(
                                                        text = if (windowOpen) "Room live" else "Room ended",
                                                        container = if (windowOpen) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                                        content = if (windowOpen) MaterialTheme.colorScheme.secondary
                                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                if (playedCount > 0) {
                                                    ManagerChip(
                                                        text = "Results out",
                                                        container = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                        content = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            }
                                        }
                                        AreenaxIcon(
                                            name = "expand_more",
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .rotate(if (expanded) 180f else 0f),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    if (expanded) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                                        )
                                        // Tabs
                                        Row {
                                            listOf("room" to "Room Details", "results" to "Results (${t.entries.size})").forEach { (key, label) ->
                                                Column(
                                                    Modifier
                                                        .weight(1f)
                                                        .clickable(
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            indication = null,
                                                        ) { tab = key }
                                                        .padding(vertical = 12.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                ) {
                                                    Text(
                                                        text = label,
                                                        style = Type.labelLg,
                                                        color = if (tab == key) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                    Box(
                                                        Modifier
                                                            .padding(top = 4.dp)
                                                            .fillMaxWidth()
                                                            .height(2.dp)
                                                            .background(
                                                                if (tab == key) MaterialTheme.colorScheme.primary
                                                                else Color.Transparent,
                                                            ),
                                                    )
                                                }
                                            }
                                        }

                                        if (tab == "room") {
                                            Column(
                                                Modifier.padding(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                RoomField(
                                                    label = "Room ID",
                                                    value = form?.get("roomId") ?: "",
                                                    onChange = { setRoomField(t.id, "roomId", it) },
                                                    placeholder = "e.g. 16823780",
                                                )
                                                RoomField(
                                                    label = "Room Password",
                                                    value = form?.get("roomPassword") ?: "",
                                                    onChange = { setRoomField(t.id, "roomPassword", it) },
                                                    placeholder = "e.g. areena2024",
                                                )
                                                // Expiry (live contract) — readonly field + picker
                                                Text(
                                                    text = "Room details expiry",
                                                    style = Type.labelMd,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(start = 4.dp),
                                                )
                                                val expiryInteraction = remember { MutableInteractionSource() }
                                                Surface(
                                                    onClick = { expiryPickerFor = t.id },
                                                    interactionSource = expiryInteraction,
                                                    shape = AreenaxShapes.Pill,
                                                    color = areenaColors().surfaceContainerLavender,
                                                    border = androidx.compose.foundation.BorderStroke(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.outlineVariant,
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(48.dp)
                                                        .pressScale(expiryInteraction, 0.98f),
                                                ) {
                                                    Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = form?.get("roomExpiresAt").takeUnless { it.isNullOrBlank() }
                                                                ?.let { formatDateTime("$it:00") }
                                                                ?: "Leave empty — visible until you change them",
                                                            style = Type.bodyMd,
                                                            color = if (form?.get("roomExpiresAt").isNullOrBlank())
                                                                MaterialTheme.colorScheme.onSurfaceVariant
                                                            else MaterialTheme.colorScheme.onSurface,
                                                            modifier = Modifier.weight(1f),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                        if (!form?.get("roomExpiresAt").isNullOrBlank()) {
                                                            Box(
                                                                Modifier
                                                                    .padding(start = 8.dp)
                                                                    .clickable(
                                                                        interactionSource = remember { MutableInteractionSource() },
                                                                        indication = null,
                                                                    ) { setRoomField(t.id, "roomExpiresAt", "") },
                                                            ) {
                                                                AreenaxIcon(
                                                                    name = "close",
                                                                    contentDescription = "Clear expiry",
                                                                    modifier = Modifier.size(16.dp),
                                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                if (t.roomExpiresAt != null) {
                                                    val expiryMillis = t.roomExpiresAt?.parseEpochMillis()
                                                    val windowOpen = expiryMillis != null && expiryMillis > System.currentTimeMillis()
                                                    Surface(
                                                        shape = RoundedCornerShape(12.dp),
                                                        color = areenaColors().surfaceContainerLavender.copy(alpha = 0.6f),
                                                        modifier = Modifier.fillMaxWidth(),
                                                    ) {
                                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            Text(
                                                                text = "Expiry ${formatDateTime(t.roomExpiresAt)}",
                                                                style = Type.labelMd,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                            Text(
                                                                text = if (windowOpen) "Visible to joined players right now"
                                                                else "Window closed — Results state is live",
                                                                style = Type.labelMd,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = if (windowOpen) MaterialTheme.colorScheme.secondary
                                                                else MaterialTheme.colorScheme.error,
                                                            )
                                                        }
                                                    }
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    val saveInteraction = remember { MutableInteractionSource() }
                                                    Surface(
                                                        onClick = { if (!saving) saveRoom(t) },
                                                        interactionSource = saveInteraction,
                                                        enabled = !saving,
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(44.dp)
                                                            .pressScale(saveInteraction, 0.95f),
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            AreenaxIcon(
                                                                name = "save",
                                                                contentDescription = null,
                                                                modifier = Modifier.size(18.dp),
                                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                            )
                                                            Text(
                                                                text = "Save",
                                                                style = Type.labelMd,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = MaterialTheme.colorScheme.onPrimary,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            // Results tab
                                            Column(
                                                Modifier.padding(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                if (t.entries.isEmpty()) {
                                                    Text(
                                                        text = "No players joined yet.",
                                                        style = Type.bodyMd,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                } else {
                                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        t.entries.forEach { e ->
                                                            val f = resultForm[e.id]
                                                            Surface(
                                                                shape = RoundedCornerShape(16.dp),
                                                                color = Color.Transparent,
                                                                border = androidx.compose.foundation.BorderStroke(
                                                                    1.dp,
                                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                                                ),
                                                                modifier = Modifier.fillMaxWidth(),
                                                            ) {
                                                                Column(
                                                                    Modifier.padding(12.dp),
                                                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                                                ) {
                                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                        Box(
                                                                            Modifier
                                                                                .size(32.dp)
                                                                                .clip(CircleShape)
                                                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                                                            contentAlignment = Alignment.Center,
                                                                        ) {
                                                                            Text(
                                                                                text = (e.user?.gameName?.takeIf { it.isNotEmpty() } ?: "?").uppercase().first().toString(),
                                                                                style = Type.labelMd,
                                                                                fontWeight = FontWeight.Bold,
                                                                                color = MaterialTheme.colorScheme.primary,
                                                                            )
                                                                        }
                                                                        Column(Modifier.weight(1f)) {
                                                                            Text(
                                                                                text = e.user?.gameName ?: "",
                                                                                style = Type.labelMd,
                                                                                fontWeight = FontWeight.SemiBold,
                                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                                maxLines = 1,
                                                                                overflow = TextOverflow.Ellipsis,
                                                                            )
                                                                            Text(
                                                                                text = "UID: ${e.user?.uid ?: ""}",
                                                                                style = Type.labelMd,
                                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                            )
                                                                        }
                                                                        ManagerChip(
                                                                            text = if (e.status == "PLAYED") "Played" else "Joined",
                                                                            container = if (e.status == "PLAYED") MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                                                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                                                                            content = if (e.status == "PLAYED") MaterialTheme.colorScheme.secondary
                                                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                        )
                                                                    }
                                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                        RoomFieldCompact(
                                                                            value = f?.get("rank") ?: "",
                                                                            onChange = { setResultField(e.id, "rank", it.filter { ch -> ch.isDigit() }) },
                                                                            placeholder = "Rank",
                                                                            modifier = Modifier.weight(1f),
                                                                        )
                                                                        RoomFieldCompact(
                                                                            value = f?.get("kills") ?: "",
                                                                            onChange = { setResultField(e.id, "kills", it.filter { ch -> ch.isDigit() }) },
                                                                            placeholder = "Kills",
                                                                            modifier = Modifier.weight(1f),
                                                                        )
                                                                        RoomFieldCompact(
                                                                            value = f?.get("prize") ?: "",
                                                                            onChange = { setResultField(e.id, "prize", it.filter { ch -> ch.isDigit() || ch == '.' }) },
                                                                            placeholder = "Prize",
                                                                            modifier = Modifier.weight(1f),
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                if (t.entries.isNotEmpty()) {
                                                    val publishInteraction = remember { MutableInteractionSource() }
                                                    Surface(
                                                        onClick = { if (!saving) publishResults(t) },
                                                        interactionSource = publishInteraction,
                                                        enabled = !saving,
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(44.dp)
                                                            .pressScale(publishInteraction, 0.95f),
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            AreenaxIcon(
                                                                name = "publish",
                                                                contentDescription = null,
                                                                modifier = Modifier.size(18.dp),
                                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                            )
                                                            Text(
                                                                text = "Publish Results",
                                                                style = Type.labelMd,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = MaterialTheme.colorScheme.onPrimary,
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
                    }
                }
            }
        }
    }

    expiryPickerFor?.let { tid ->
        DateTimePickerModal(
            open = true,
            value = roomForm[tid]?.get("roomExpiresAt") ?: "",
            onClose = { expiryPickerFor = null },
            onChange = { iso -> setRoomField(tid, "roomExpiresAt", iso.take(16)) },
        )
    }
}

// ------------------------------------------------------------------ helpers

@Composable
private fun ManagerChip(text: String, container: Color, content: Color) {
    Surface(shape = CircleShape, color = container) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Web INPUT_CLS: h-12 rounded-[14px] field with a 14sp-medium label. */
@Composable
private fun RoomField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
) {
    Column {
        Text(
            text = label,
            style = Type.labelMd,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        RoomFieldCompact(
            value = value,
            onChange = onChange,
            placeholder = placeholder,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RoomFieldCompact(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    AreenaxPillField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = "key",
        textStyle = Type.bodyMd,
        height = 42.dp,
        imeAction = ImeAction.Default, // pre-migration KeyboardOptions(Text)
    )
}
