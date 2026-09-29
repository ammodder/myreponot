package com.areenax.app.ui.screens.social

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.LocationUpdateRequest
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.theme.onlineGreen
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.AppBarMode
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AreenaxPillField
import com.areenax.app.core.ui.AreenaxSpinner
import com.areenax.app.core.ui.BellButton
import com.areenax.app.core.ui.CenteredModalScaffold
import com.areenax.app.core.ui.EmptyState
import com.areenax.app.core.ui.GuestGateDialog
import com.areenax.app.core.ui.QrSheet
import com.areenax.app.core.ui.QrTab
import com.areenax.app.core.ui.QrTarget
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.UserResultCard
import com.areenax.app.core.ui.cardShadow
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.rememberGuestGate
import com.areenax.app.core.util.parseEpochMillis
import com.areenax.app.core.util.timeAgo
import com.areenax.app.data.Friend
import com.areenax.app.data.FriendRequestRow
import com.areenax.app.data.QrRelations
import com.areenax.app.data.QrUserLookup
import com.areenax.app.data.UserSummary
import kotlinx.coroutines.launch

/*
 * FriendsScreen — key `friends` (bottom-nav tab; SPEC/01 D4, build record 3-d):
 * exact freinds.html — "Friends" h1 + person_add / qr_code_2 / bell header
 * buttons, search pill w/ clear, All (n) / • Online (n) / Requests [badge]
 * segmented tabs (active white pill), friend cards (44dp avatar letter +
 * online dot per the 30-min lastMessageAt rule, gameName, UID, lastMessage
 * preview (fallback fullName), timeAgo, unread emerald badge on the
 * chat_bubble button → chat {friendId}); empty EmptyState "No friends yet.";
 * Requests tab (received/sent cards, Accept/Decline/Cancel, Accept All);
 * Add tab "Add by Player UID" card (invite toast) + Q12 REAL suggestions
 * (GET /friends/suggest — demo rows removed): "Players nearby" (opt-in
 * ACCESS_COARSE_LOCATION → POST /me/location coarse cell, prominent
 * disclosure BEFORE the dialog) and "Find from contacts" (PickContact →
 * ONE phone digits → mode=contacts; READ_CONTACTS not required); deep-link
 * "Add Friend" popup (params.qrUser → /qr/lookup → UserResultCard).
 */

private const val ONLINE_WINDOW_MS = 30 * 60 * 1000L
private val OnlineDot = onlineGreen

/** 15-minute freshness window for the last-known fix used by Q12 nearby. */
private const val LOCATION_FRESH_MS = 15 * 60 * 1000L

@Composable
fun FriendsScreen(env: NavEnv) {
    val scope = rememberCoroutineScope()
    val gate = rememberGuestGate()

    var friends by remember { mutableStateOf<List<Friend>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf("friends") }
    var query by remember { mutableStateOf("") }

    var addUid by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val sentTo = remember { mutableStateMapOf<String, Boolean>() }

    // Real friend requests (GET /friends/requests)
    var received by remember { mutableStateOf<List<FriendRequestRow>>(emptyList()) }
    var sentRows by remember { mutableStateOf<List<FriendRequestRow>>(emptyList()) }
    var receivedPending by remember { mutableStateOf(0) }
    var reqLoading by remember { mutableStateOf(true) }
    var reqBusy by remember { mutableStateOf(false) }
    var acceptAllBusy by remember { mutableStateOf(false) }

    var qrOpen by remember { mutableStateOf(false) }

    // Q12 real-friend suggestions (GET /friends/suggest) — demo rows removed
    var nearbyUsers by remember { mutableStateOf<List<UserSummary>?>(null) }
    var nearbyLoading by remember { mutableStateOf(false) }
    var contactUsers by remember { mutableStateOf<List<UserSummary>?>(null) }
    var contactBusy by remember { mutableStateOf(false) }
    var suggestBusyId by remember { mutableStateOf<String?>(null) }

    // Q12 nearby: permission state + prominent-disclosure step
    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(env.context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }
    var locGranted by remember { mutableStateOf(hasLocationPermission) }
    var locDisclosed by remember { mutableStateOf(false) }
    var locDeniedHint by remember { mutableStateOf(false) }

    // Q5: deep-linked "Add Friend" popup (params.qrUser)
    var qrUserLookup by remember { mutableStateOf<QrUserLookup?>(null) }
    var qrUserClosedUid by remember { mutableStateOf<String?>(null) }
    var popupBusy by remember { mutableStateOf(false) }

    suspend fun loadFriends() {
        when (val res = safeCall { env.api.friends() }) {
            is ApiResult.Success -> {
                friends = res.data.friends
                loadError = false
            }
            else -> loadError = true
        }
        loading = false
    }

    suspend fun loadRequests() {
        when (val res = safeCall { env.api.friendRequests() }) {
            is ApiResult.Success -> {
                received = res.data.received
                sentRows = res.data.sent
                receivedPending = res.data.receivedPending
            }
            else -> {
                // requests are secondary — keep whatever we already have
            }
        }
        reqLoading = false
    }

    /** Players nearby — reads the caller's own coarse cell (web loadNearby). */
    suspend fun loadNearby() {
        if (env.user?.isGuest == true) return
        nearbyLoading = true
        when (val res = safeCall { env.api.suggestFriends(mode = "nearby") }) {
            is ApiResult.Success -> nearbyUsers = res.data.users
            else -> nearbyUsers = emptyList() // silently empty — suggestions are never critical (web)
        }
        nearbyLoading = false
    }

    /** Fire the last-known coarse fix to POST /me/location (best effort). */
    @SuppressLint("MissingPermission")
    suspend fun sendLocation() {
        if (ContextCompat.checkSelfPermission(env.context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(env.context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            val lm = env.context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val last = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .mapNotNull { p -> runCatching { lm?.getLastKnownLocation(p) }.getOrNull() }
                .filter { System.currentTimeMillis() - it.time < LOCATION_FRESH_MS }
                .maxByOrNull { it.time }
            if (last != null) {
                safeCall { env.api.updateLocation(LocationUpdateRequest(lat = last.latitude, lon = last.longitude)) }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadFriends()
    }
    LaunchedEffect(Unit) {
        loadRequests()
    }
    // Web reloads suggestions whenever the Add tab opens
    LaunchedEffect(tab, locGranted) {
        if (tab == "add" && locGranted) loadNearby()
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        locDisclosed = false
        if (granted) {
            locGranted = true
            locDeniedHint = false
            scope.launch {
                sendLocation()
                loadNearby()
            }
        } else {
            locDeniedHint = true
        }
    }

    val contactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact(),
    ) { uri ->
        if (uri == null || contactBusy) return@rememberLauncherForActivityResult
        contactBusy = true
        scope.launch {
            // ONE phone number (digits) from the contact the user explicitly picked
            val phone = queryContactPhone(env.context, uri)
            if (!phone.isNullOrEmpty()) {
                when (val res = safeCall { env.api.suggestFriends(mode = "contacts", phone = phone) }) {
                    is ApiResult.Success -> contactUsers = res.data.users
                    is ApiResult.Error, is ApiResult.NetworkError -> env.toast.show(
                        "Could not match contact",
                        description = (res as? ApiResult.Error)?.message ?: (res as? ApiResult.NetworkError)?.message,
                        variant = ToastVariant.Destructive,
                    )
                }
            }
            contactBusy = false
        }
    }

    val lowered = query.lowercase().trim()
    val filtered = if (lowered.isEmpty()) {
        friends
    } else {
        friends.filter {
            it.gameName.lowercase().contains(lowered) ||
                it.fullName.lowercase().contains(lowered) ||
                it.uid.contains(lowered)
        }
    }
    val onlineCount = friends.count { it.isActive() }

    fun sendInvite() {
        if (sending) return
        if (!gate.requireAccount(env.user?.isGuest == true, "add friends")) return
        val uid = addUid.trim()
        if (uid.isEmpty()) {
            env.toast("Enter a player UID first", ToastVariant.Destructive)
            return
        }
        sending = true
        scope.launch {
            when (val res = safeCall {
                env.api.sendFriendRequest(
                    com.areenax.app.data.FriendRequestCreateBody(uid = uid, source = "UID"),
                )
            }) {
                is ApiResult.Success -> {
                    if (res.data.autoAccepted == true) {
                        env.toast("You are now friends!")
                        loadFriends()
                    } else {
                        env.toast("Request sent to UID $uid")
                    }
                    addUid = ""
                }
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    val status = (res as? ApiResult.Error)?.code ?: 0
                    env.toast.show(
                        when (status) {
                            404 -> "Player not found"
                            409 -> "Already connected"
                            else -> "Could not send request"
                        },
                        description = (res as? ApiResult.Error)?.message ?: (res as? ApiResult.NetworkError)?.message,
                        variant = if (status == 409) ToastVariant.Default else ToastVariant.Destructive,
                    )
                }
            }
            sending = false
            loadRequests()
        }
    }

    fun actOnRequest(row: FriendRequestRow, action: String) {
        if (reqBusy) return
        reqBusy = true
        scope.launch {
            when (val res = safeCall {
                env.api.respondFriendRequest(
                    row.id,
                    com.areenax.app.data.FriendRequestActionBody(action = action),
                )
            }) {
                is ApiResult.Success -> {
                    env.toast(
                        when (action) {
                            "accept" -> "Friend added!"
                            "decline" -> "Request declined"
                            else -> "Request canceled"
                        },
                    )
                }
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    env.toast.show(
                        "Action failed",
                        description = (res as? ApiResult.Error)?.message ?: (res as? ApiResult.NetworkError)?.message,
                        variant = ToastVariant.Destructive,
                    )
                }
            }
            reqBusy = false
            loadFriends()
            loadRequests()
        }
    }

    /** Send a friend request to a suggested (real) player — same backend as UID add. */
    fun addSuggested(s: UserSummary) {
        if (suggestBusyId != null) return
        if (!gate.requireAccount(env.user?.isGuest == true, "add friends")) return
        suggestBusyId = s.id
        scope.launch {
            when (val res = safeCall {
                env.api.sendFriendRequest(
                    com.areenax.app.data.FriendRequestCreateBody(uid = s.uid, source = "UID"),
                )
            }) {
                is ApiResult.Success -> {
                    if (res.data.autoAccepted == true) {
                        env.toast("You are now friends!")
                        loadFriends()
                    } else {
                        env.toast("Request sent to UID ${s.uid}")
                    }
                    sentTo[s.id] = true
                }
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    env.toast.show(
                        "Could not send request",
                        description = (res as? ApiResult.Error)?.message ?: (res as? ApiResult.NetworkError)?.message,
                        variant = ToastVariant.Destructive,
                    )
                }
            }
            suggestBusyId = null
            loadRequests()
        }
    }

    fun acceptAllRequests() {
        val pending = received.filter { it.status == "PENDING" }
        if (pending.isEmpty() || acceptAllBusy) return
        acceptAllBusy = true
        scope.launch {
            var accepted = 0
            for (row in pending) {
                val res = safeCall {
                    env.api.respondFriendRequest(
                        row.id,
                        com.areenax.app.data.FriendRequestActionBody(action = "accept"),
                    )
                }
                if (res is ApiResult.Success) accepted += 1
            }
            env.toast(
                if (accepted > 0) {
                    "Added $accepted friend" + if (accepted == 1) "!" else "s!"
                } else {
                    "No requests accepted"
                },
            )
            acceptAllBusy = false
            loadFriends()
            loadRequests()
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Top bar — root tab: "Friends" h1 + person_add / qr_code_2 / bell
        AppBar(
            mode = AppBarMode.Tab,
            title = "Friends",
            right = {
                HeaderPlainIconButton(icon = "person_add", label = "Add friend") { tab = "add" }
                HeaderPlainIconButton(icon = "qr_code_2", label = "Show QR code") { qrOpen = true }
                BellButton()
            },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ===== Search pill (w/ clear) =====
            Box(Modifier.fillMaxWidth()) {
                SearchPill(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search by username or UID...",
                )
                if (query.isNotEmpty()) {
                    Surface(
                        onClick = { query = "" },
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp),
                    ) {
                        AreenaxIcon(
                            name = "close",
                            contentDescription = "Clear search",
                            modifier = Modifier
                                .padding(6.dp)
                                .size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ===== Segmented tabs — All / Online / Requests =====
            Surface(
                shape = RoundedCornerShape(50),
                color = areenaColors().surfaceContainerHighLavender.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(4.dp)) {
                    SegmentedTab(
                        label = "All (${friends.size})",
                        active = tab == "friends",
                        modifier = Modifier.weight(1f),
                        onClick = { tab = "friends" },
                    )
                    SegmentedTab(
                        label = "Online ($onlineCount)",
                        active = tab == "online",
                        leadingDot = true,
                        modifier = Modifier.weight(1f),
                        onClick = { tab = "online" },
                    )
                    SegmentedTab(
                        label = "Requests",
                        active = tab == "requests",
                        badge = if (receivedPending > 0) receivedPending.toString() else null,
                        modifier = Modifier.weight(1f),
                        onClick = { tab = "requests" },
                    )
                }
            }

            when {
                loading -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) { AreenaxSpinner() }

                loadError && friends.isEmpty() -> {
                    EmptyState(message = "Could not load friends. Please try again.", icon = "group")
                }

                tab == "friends" || tab == "online" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (friends.isNotEmpty()) {
                            // Meta row — "«n» Online • «total» Total" + "Recent Activity"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondary),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "$onlineCount Online",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "•",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${friends.size} Total",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "Recent Activity",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        when {
                            friends.isEmpty() -> EmptyState(message = "No friends yet.", icon = "group")
                            tab == "friends" && filtered.isEmpty() -> {
                                EmptyState(message = "No friends match \"$query\".", icon = "search")
                            }
                            tab == "friends" -> filtered.forEach { FriendCard(env, it, forceOnline = false) }
                            filtered.none { it.isActive() } -> {
                                EmptyState(message = "No friends online right now.", icon = "schedule")
                            }
                            else -> filtered
                                .filter { it.isActive() }
                                .forEach { FriendCard(env, it, forceOnline = true) }
                        }
                    }
                }

                tab == "requests" -> {
                    if (reqLoading) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) { AreenaxSpinner(size = 24) }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Requests Received
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Requests Received ($receivedPending pending)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.weight(1f))
                                val acceptAllEnabled = receivedPending > 0 && !acceptAllBusy
                                Text(
                                    text = if (acceptAllBusy) "Accepting…" else "Accept All",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (acceptAllEnabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    },
                                    modifier = Modifier.clickable(enabled = acceptAllEnabled) {
                                        acceptAllRequests()
                                    },
                                )
                            }

                            val pendingReceived = received.filter { it.status == "PENDING" }
                            if (received.isEmpty()) {
                                if (sentRows.isNotEmpty()) {
                                    Text(
                                        text = "Nothing here yet.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                    )
                                }
                            } else {
                                received.forEach { row ->
                                    ReceivedRequestCard(row, reqBusy) { action ->
                                        actOnRequest(row, action)
                                    }
                                }
                            }

                            // Requests Sent
                            Text(
                                text = "Requests Sent (${sentRows.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                            )
                            if (sentRows.isEmpty()) {
                                if (pendingReceived.isNotEmpty()) {
                                    Text(
                                        text = "Nothing here yet.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                    )
                                }
                            } else {
                                sentRows.forEach { row ->
                                    SentRequestCard(row, reqBusy) { actOnRequest(row, "cancel") }
                                }
                            }

                            if (received.isEmpty() && sentRows.isEmpty()) {
                                EmptyState(message = "No requests yet.", icon = "person_add")
                            }
                        }
                    }
                }

                else -> {
                    // ===== Add tab — "Add by Player UID" card + suggested players =====
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .cardShadow(RoundedCornerShape(16.dp)),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column {
                                    Text(
                                        text = "Add by Player UID",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "Enter a player tag to send an instant squad invite",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.weight(1f)) {
                                        SearchPill(
                                            value = addUid,
                                            onValueChange = { addUid = it.filter { c -> c.isDigit() } },
                                            placeholder = "e.g. 782104",
                                            numeric = true,
                                        )
                                    }
                                    val sendInteraction = remember { MutableInteractionSource() }
                                    Surface(
                                        onClick = { sendInvite() },
                                        enabled = !sending,
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        interactionSource = sendInteraction,
                                        modifier = Modifier.pressScale(sendInteraction, 0.95f),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            AreenaxIcon(
                                                name = "send",
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = if (sending) "Sending…" else "Send",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ===== Q12 (a): Players nearby — strictly opt-in =====
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Players nearby",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .semantics { heading() },
                            )
                            if (env.user?.isGuest == true) {
                                Text(
                                    text = "Create a free account to find players near you.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            } else if (!locGranted) {
                                if (locDeniedHint) {
                                    Text(
                                        text = "Location permission is needed to find players near you.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )
                                }
                                // Prominent disclosure — visible BEFORE the system dialog (Q12/A9-11)
                                if (locDisclosed) {
                                    Text(
                                        text = "AREENAX uses your approximate location only to suggest " +
                                            "nearby players. It is never used in the background.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )
                                }
                                SuggestActionButton(
                                    icon = "my_location",
                                    label = if (locDisclosed) "Continue" else "Enable location",
                                    enabled = true,
                                    onClick = {
                                        if (locDisclosed) {
                                            locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                        } else {
                                            locDisclosed = true
                                        }
                                    },
                                )
                            } else if (nearbyLoading || nearbyUsers == null) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) { AreenaxSpinner(size = 24) }
                            } else {
                                val users = nearbyUsers.orEmpty()
                                if (users.isEmpty()) {
                                    EmptyState(message = "No players found yet", icon = "group")
                                } else {
                                    users.forEach { s ->
                                        SuggestRow(
                                            s = s,
                                            sent = sentTo[s.id] == true,
                                            busy = suggestBusyId == s.id,
                                            onAdd = { addSuggested(s) },
                                        )
                                    }
                                }
                            }
                        }

                        // ===== Q12 (b): Find from contacts — ONE picked contact =====
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Find from contacts",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .semantics { heading() },
                            )
                            SuggestActionButton(
                                icon = "person_search",
                                label = if (contactBusy) "Matching…" else "Pick a contact",
                                enabled = !contactBusy,
                                onClick = {
                                    if (gate.requireAccount(env.user?.isGuest == true, "find friends from contacts")) {
                                        contactLauncher.launch(null)
                                    }
                                },
                            )
                            if (contactBusy) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) { AreenaxSpinner(size = 24) }
                            } else {
                                val users = contactUsers
                                if (users != null) {
                                    if (users.isEmpty()) {
                                        EmptyState(message = "No players found yet", icon = "person_add")
                                    } else {
                                        users.forEach { s ->
                                            SuggestRow(
                                                s = s,
                                                sent = sentTo[s.id] == true,
                                                busy = suggestBusyId == s.id,
                                                onAdd = { addSuggested(s) },
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

    QrSheet(
        open = qrOpen,
        onClose = { qrOpen = false },
        env = env,
        target = QrTarget.USER,
        initialTab = QrTab.SCAN,
    )

    /* ---- Q5 QR deep-link: params.qrUser opens the Add Friend popup ---- */
    val qrUserParam = (env.params["qrUser"] as? String)?.takeIf { it.isNotBlank() }
    val showQrUserPopup = qrUserParam != null && qrUserClosedUid != qrUserParam

    fun closeQrUserPopup() {
        qrUserLookup = null
        qrUserClosedUid = qrUserParam
    }

    LaunchedEffect(qrUserParam, qrUserClosedUid) {
        if (qrUserParam == null || qrUserClosedUid == qrUserParam) return@LaunchedEffect
        when (val res = safeCall { env.api.qrLookup(uid = qrUserParam) }) {
            is ApiResult.Success -> {
                val user = res.data.asUser
                if (user != null) {
                    qrUserLookup = user
                } else {
                    env.toast.show(
                        "Could not open player",
                        description = "No player found with this QR code.",
                        variant = ToastVariant.Destructive,
                    )
                    qrUserClosedUid = qrUserParam
                }
            }
            is ApiResult.Error, is ApiResult.NetworkError -> {
                val status = (res as? ApiResult.Error)?.code ?: 0
                val message = (res as? ApiResult.Error)?.message ?: (res as? ApiResult.NetworkError)?.message
                env.toast.show(
                    "Could not open player",
                    description = if (status == 404) {
                        "No player found with this QR code."
                    } else {
                        message ?: "Please try again."
                    },
                    variant = ToastVariant.Destructive,
                )
                qrUserClosedUid = qrUserParam
            }
        }
    }

    fun handlePopupAdd(data: QrUserLookup) {
        if (popupBusy) return
        if (!gate.requireAccount(env.user?.isGuest == true, "add friends")) return
        popupBusy = true
        scope.launch {
            when (val res = safeCall {
                env.api.sendFriendRequest(
                    com.areenax.app.data.FriendRequestCreateBody(uid = data.user.uid, source = "QR"),
                )
            }) {
                is ApiResult.Success -> {
                    if (res.data.autoAccepted == true) {
                        env.toast("You are now friends!")
                        qrUserLookup = data.copy(relation = QrRelations.FRIEND)
                    } else {
                        env.toast("Request sent to UID ${data.user.uid}")
                        qrUserLookup = data.copy(relation = QrRelations.REQUEST_SENT)
                    }
                    loadFriends()
                    loadRequests()
                }
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    env.toast.show(
                        "Could not send request",
                        description = (res as? ApiResult.Error)?.message ?: (res as? ApiResult.NetworkError)?.message,
                        variant = ToastVariant.Destructive,
                    )
                }
            }
            popupBusy = false
        }
    }

    CenteredModalScaffold(open = showQrUserPopup, onDismiss = { closeQrUserPopup() }) {
        val lookup = qrUserLookup
        if (lookup != null && lookup.user.uid == qrUserParam) {
            UserResultCard(
                data = lookup,
                busy = popupBusy,
                env = env,
                onAddFriend = { handlePopupAdd(lookup) },
                onMessage = {
                    closeQrUserPopup()
                    env.navigate(ScreenKeys.CHAT, mapOf("friendId" to lookup.user.id))
                },
            )
            Text(
                text = qrRelationLine(lookup),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        } else {
            // Resolving — spinner + "Looking up player…" (web :844-849)
            AreenaxSpinner()
            Text(
                text = "Looking up player…",
                style = Type.bodyMd,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    GuestGateDialog(gate, env)
}

/** One-line relation summary shown under the deep-linked player card (web :48-60). */
private fun qrRelationLine(lookup: QrUserLookup): String = when (lookup.relation) {
    QrRelations.SELF -> "This is your own QR code."
    QrRelations.FRIEND -> "You and ${lookup.user.gameName} are friends."
    QrRelations.REQUEST_SENT -> "Your friend request is waiting for a response."
    QrRelations.REQUEST_RECEIVED -> "${lookup.user.gameName} sent you a friend request."
    else -> "Send a friend request to connect."
}

/**
 * Read ONE phone number (digits only) from the contact the user explicitly
 * picked. PickContact grants temporary access to exactly the returned URI,
 * so no READ_CONTACTS is needed; the phone-table fallback silently degrades
 * when that read is not permitted. Null = nothing readable (the web's
 * no-tel path stays silent).
 */
private fun queryContactPhone(context: Context, contactUri: Uri): String? {
    val raw: String? = try {
        val resolver: ContentResolver = context.contentResolver
        // 1) ask the picked URI directly for its number (covered by the grant)
        resolver.query(contactUri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            // 2) fallback: _ID → phone table (only if READ_CONTACTS happens to be granted)
            ?: resolver.query(contactUri, arrayOf(ContactsContract.Contacts._ID), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
                ?.let { id ->
                    resolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(id.toString()),
                        null,
                    )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                }
    } catch (_: Exception) {
        null
    }
    return raw?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }
}

/** Section action pill — h-11 primary/10 (web :771-779), ≥48dp touch target. */
@Composable
private fun SuggestActionButton(
    icon: String,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .pressScale(interaction, 0.95f),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 10.dp),
        ) {
            AreenaxIcon(
                name = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** One GET /friends/suggest row — real player + Add (web renderSuggestRow). */
@Composable
private fun SuggestRow(
    s: UserSummary,
    sent: Boolean,
    busy: Boolean,
    onAdd: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .cardShadow(RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (s.gameName.ifBlank { "?" }).firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = s.gameName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "UID: ${s.uid}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val interaction = remember { MutableInteractionSource() }
            Surface(
                onClick = onAdd,
                enabled = !sent && !busy,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                interactionSource = interaction,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .pressScale(interaction, 0.95f),
            ) {
                Text(
                    text = if (sent) "Sent" else if (busy) "…" else "Add",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (sent) 0.6f else 1f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** 30-min lastMessageAt activity rule (web isActive()). */
private fun Friend.isActive(): Boolean {
    val at = lastMessageAt?.parseEpochMillis() ?: return false
    return System.currentTimeMillis() - at < ONLINE_WINDOW_MS
}

/** Flat header icon button (freinds.html: no circle bg, 22dp symbol). */
@Composable
private fun HeaderPlainIconButton(icon: String, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.Transparent) {
        AreenaxIcon(
            name = icon,
            contentDescription = label,
            modifier = Modifier
                .padding(8.dp)
                .size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Search / UID input pill (h-12 rounded-full) — shared [AreenaxPillField]. */
@Composable
private fun SearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    numeric: Boolean = false,
) {
    AreenaxPillField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        leadingIcon = if (numeric) "person_add" else "search",
        textStyle = Type.bodyMd,
        keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
        imeAction = ImeAction.Done,
        keyboardActions = KeyboardActions(onDone = {}),
    )
}

/** One segmented tab — active = white pill w/ shadow; inactive = flat muted. */
@Composable
private fun SegmentedTab(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    leadingDot: Boolean = false,
    badge: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (active) {
            MaterialTheme.colorScheme.surfaceContainerLowest
        } else {
            Color.Transparent
        },
        shadowElevation = if (active) 2.dp else 0.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingDot) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary),
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
            )
            if (badge != null) {
                Spacer(Modifier.width(5.dp))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Text(
                        text = badge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/** Friend card — avatar letter + online dot, gameName / UID / preview, chat button. */
@Composable
private fun FriendCard(env: NavEnv, friend: Friend, forceOnline: Boolean) {
    val online = forceOnline || friend.isActive()
    Surface(
        onClick = { env.navigate(ScreenKeys.CHAT, mapOf("friendId" to friend.id)) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .cardShadow(RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (friend.gameName.ifBlank { "?" }).firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (online) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(OnlineDot)
                            .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = friend.gameName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "UID: ${friend.uid}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val preview = friend.lastMessage ?: friend.fullName.ifBlank { null }
                if (!preview.isNullOrBlank()) {
                    Text(
                        text = preview,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (!friend.lastMessageAt.isNullOrBlank()) {
                    Text(
                        text = timeAgo(friend.lastMessageAt),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Box {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        AreenaxIcon(
                            name = "chat_bubble",
                            contentDescription = "Chat with ${friend.gameName}",
                            modifier = Modifier
                                .padding(7.dp)
                                .size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (friend.unread > 0) {
                        // Emerald unread badge on the chat button
                        Surface(
                            shape = CircleShape,
                            color = OnlineDot,
                            border = androidx.compose.foundation.BorderStroke(
                                2.dp,
                                MaterialTheme.colorScheme.surfaceContainerLowest,
                            ),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp),
                        ) {
                            Text(
                                text = if (friend.unread > 9) "9+" else friend.unread.toString(),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Received request card — identity + via/UID/timeAgo + Accept / Decline. */
@Composable
private fun ReceivedRequestCard(
    row: FriendRequestRow,
    busy: Boolean,
    onAction: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .cardShadow(RoundedCornerShape(16.dp)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RequestIdentity(row)
            when (row.status) {
                "ACCEPTED" -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AreenaxIcon(
                        name = "check_circle",
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Accepted — added to friends",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                "DECLINED" -> Text(
                    text = "Request declined",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                )
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val acceptEnabled = !busy
                    Surface(
                        onClick = { if (acceptEnabled) onAction("accept") },
                        enabled = acceptEnabled,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = if (acceptEnabled) 1f else 0.6f),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = if (busy) "…" else "Accept",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                    val declineEnabled = !busy
                    Surface(
                        onClick = { if (declineEnabled) onAction("decline") },
                        enabled = declineEnabled,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "Decline",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Sent request card — identity + Pending (Cancel) / Accepted / Declined chip. */
@Composable
private fun SentRequestCard(
    row: FriendRequestRow,
    busy: Boolean,
    onCancel: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .cardShadow(RoundedCornerShape(16.dp)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RequestIdentity(row)
            when (row.status) {
                "PENDING" -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AreenaxIcon(
                                name = "schedule",
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Pending",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Cancel",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (busy) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.clickable(enabled = !busy) { onCancel() },
                    )
                }
                "ACCEPTED" -> StatusChipRow(label = "Accepted", leadingCheck = true)
                else -> StatusChipRow(label = "Declined", leadingCheck = false)
            }
        }
    }
}

@Composable
private fun StatusChipRow(label: String, leadingCheck: Boolean) {
    Row {
        Surface(
            shape = CircleShape,
            color = if (leadingCheck) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingCheck) {
                    AreenaxIcon(
                        name = "check_circle",
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (leadingCheck) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun RequestIdentity(row: FriendRequestRow) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = row.user.gameName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
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
                text = "via " + if (row.source == "QR") "QR code" else "UID",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "UID: ${row.user.uid}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = timeAgo(row.createdAt),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
