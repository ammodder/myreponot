package com.areenax.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
import com.areenax.app.core.ui.areenaxFieldColors
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.util.formatDateTime
import com.areenax.app.core.util.formatDisplayDate
import com.areenax.app.core.util.formatMoney
import com.areenax.app.core.util.parseEpochMillis
import com.areenax.app.core.util.timeAgo
import com.areenax.app.data.AdminActionBody
import com.areenax.app.data.AdminDisableBody
import com.areenax.app.data.AdminProof
import com.areenax.app.data.AdminResultEntry
import com.areenax.app.data.AdminResultsBody
import com.areenax.app.data.AdminReconcileRow
import com.areenax.app.data.AdminRoomBody
import com.areenax.app.data.AdminSecurityItem
import com.areenax.app.data.AdminSettingsBody
import com.areenax.app.data.AdminStatusBody
import com.areenax.app.data.PaymentRequest
import com.areenax.app.data.Roles
import com.areenax.app.data.Tournament
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * AdminPanelScreen — key `adminPanel` (SPEC/01 E8). Exact port of
 * `profile/AdminPanelScreen.tsx` — the mobile admin dashboard:
 *
 * - Gate: role ∈ ADMIN_PANEL_ROLES (client gate; server enforces RBAC) else
 *   EmptyState "Admin access required." (lock). GET /admin/settings 403 →
 *   denied state. Load error → EmptyState(message, error).
 * - Role-gated sections (server-side RBAC matrix): GamesAdminSection
 *   (SUPER_ADMIN — file-local), Payment Requests (FINANCE_ADMIN+), Tournament
 *   Management (MODERATOR+), ResultProofsAdminSection (MODERATOR+ — file-local),
 *   Settings sections (fields for every panel role, headers SUPER_ADMIN-only),
 *   Security & Audit (SUPER_ADMIN), bottom Save Settings CTA (all roles).
 * - Save: POST /admin/settings {settings: form} → toast "Settings saved" /
 *   "Save failed". Four-eyes knob (fourEyesEnabled) only relabels Approve →
 *   "Final Approve".
 */
@Composable
fun AdminPanelScreen(env: NavEnv) {
    val user by env.session.user.collectAsState()
    val role = user?.role ?: Roles.USER

    val form = remember { mutableStateMapOf<String, String>() }
    var loading by remember { mutableStateOf(true) }
    var denied by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var fourEyes by remember { mutableStateOf(false) }
    var formReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        when (val res = safeCall { env.api.adminSettings() }) {
            is ApiResult.Success -> {
                val s = res.data.settings
                val keys = listOf(
                    "appDownloadUrl", "minDeposit", "minWithdraw", "referralBonus",
                    "welcomeBonus", "welcomeBonusUserEnabled", "welcomeBonusGuestEnabled",
                    "welcomeBonusGuest", "commission", "version", "whatsapp", "instagram",
                    "telegram", "youtube", "discord", "aboutMission", "depositAccounts",
                )
                keys.forEach { key ->
                    form[key] = when (key) {
                        "welcomeBonusUserEnabled" -> s[key] ?: "true"
                        "welcomeBonusGuestEnabled" -> s[key] ?: "false"
                        else -> s[key] ?: ""
                    }
                }
                fourEyes = s["fourEyesEnabled"] == "true"
                formReady = true
            }
            is ApiResult.Error -> {
                if (res.code == 403) {
                    denied = true
                } else {
                    loadError = res.message
                }
            }
            is ApiResult.NetworkError -> loadError = res.message
        }
        loading = false
    }

    fun setField(key: String, value: String) {
        form[key] = value
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp),
    ) {
        AppBar(mode = AppBarMode.Page, title = "Admin Panel")

        when {
            denied || !Roles.isAdminPanelRole(role) -> {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
                    EmptyState(message = "Admin access required.", icon = "lock")
                }
            }
            loading || !formReady -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AreenaxSpinner(size = 24)
                }
            }
            loadError.isNotEmpty() -> {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
                    EmptyState(message = loadError, icon = "error")
                }
            }
            else -> {
                Column(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Games Management — SUPER_ADMIN
                    if (Roles.hasRole(role, Roles.SUPER_ADMIN)) {
                        GamesAdminSection(env)
                    }

                    // Payment Requests — FINANCE_ADMIN+
                    if (Roles.hasRole(role, Roles.FINANCE_ADMIN)) {
                        PaymentRequestsSection(env, fourEyes = fourEyes)
                    }

                    // Tournament Management — MODERATOR+
                    if (Roles.hasRole(role, Roles.MODERATOR)) {
                        TournamentsAdminSection(env)
                    }

                    // Result Proofs — MODERATOR+
                    if (Roles.hasRole(role, Roles.MODERATOR)) {
                        ResultProofsAdminSection(env)
                    }

                    // ------------------------------------------------ App Distribution
                    SectionCard {
                        if (Roles.hasRole(role, Roles.SUPER_ADMIN)) {
                            Text(
                                text = "App Distribution",
                                style = Type.headlineMd,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.semantics { heading() }, // R5
                            )
                        }
                        FormField(
                            label = "App Download URL",
                            leadingIcon = "tag",
                            value = form["appDownloadUrl"] ?: "",
                            onChange = { setField("appDownloadUrl", it) },
                            placeholder = "https://play.google.com/store/apps/details?id=...",
                            helper = "Used by QR codes: visitors without the app are sent here.",
                        )
                    }

                    // ------------------------------------------------ Wallet Limits & Bonuses
                    SectionCard {
                        if (Roles.hasRole(role, Roles.SUPER_ADMIN)) {
                            Text(
                                text = "Wallet Limits & Bonuses",
                                style = Type.headlineMd,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.semantics { heading() }, // R5
                            )
                        }
                        FormField(
                            label = "Min Deposit",
                            leadingIcon = "payments",
                            value = form["minDeposit"] ?: "",
                            onChange = { setField("minDeposit", it) },
                            placeholder = "100",
                            inputMode = KeyboardType.Decimal,
                        )
                        FormField(
                            label = "Min Withdraw",
                            leadingIcon = "payments",
                            value = form["minWithdraw"] ?: "",
                            onChange = { setField("minWithdraw", it) },
                            placeholder = "500",
                            inputMode = KeyboardType.Decimal,
                        )
                        FormField(
                            label = "Referral Bonus",
                            leadingIcon = "payments",
                            value = form["referralBonus"] ?: "",
                            onChange = { setField("referralBonus", it) },
                            placeholder = "50",
                            inputMode = KeyboardType.Decimal,
                        )
                        SettingToggle(
                            label = "User Welcome Bonus",
                            on = (form["welcomeBonusUserEnabled"] ?: "true") == "true",
                            onToggle = {
                                setField(
                                    "welcomeBonusUserEnabled",
                                    if ((form["welcomeBonusUserEnabled"] ?: "true") == "true") "false" else "true",
                                )
                            },
                        )
                        FormField(
                            label = "User Welcome Bonus Amount",
                            leadingIcon = "payments",
                            value = form["welcomeBonus"] ?: "",
                            onChange = { setField("welcomeBonus", it) },
                            placeholder = "100",
                            inputMode = KeyboardType.Decimal,
                            helper = "Paid to new users at signup while the toggle above is On.",
                        )
                        SettingToggle(
                            label = "Guest Welcome Bonus",
                            on = (form["welcomeBonusGuestEnabled"] ?: "false") == "true",
                            onToggle = {
                                setField(
                                    "welcomeBonusGuestEnabled",
                                    if ((form["welcomeBonusGuestEnabled"] ?: "false") == "true") "false" else "true",
                                )
                            },
                        )
                        FormField(
                            label = "Guest Welcome Bonus Amount",
                            leadingIcon = "payments",
                            value = form["welcomeBonusGuest"] ?: "",
                            onChange = { setField("welcomeBonusGuest", it) },
                            placeholder = "50",
                            inputMode = KeyboardType.Decimal,
                            helper = "Paid to Continue-as-Guest sessions while the toggle above is On. Guests get nothing while Off.",
                        )
                        FormField(
                            label = "Commission %",
                            leadingIcon = "calculate",
                            value = form["commission"] ?: "",
                            onChange = { setField("commission", it) },
                            placeholder = "10",
                            inputMode = KeyboardType.Decimal,
                        )
                        FormTextArea(
                            label = "Deposit Accounts",
                            value = form["depositAccounts"] ?: "",
                            onChange = { setField("depositAccounts", it) },
                            placeholder = "JazzCash: 0300-1234567 | EasyPaisa: 0345-7654321",
                            helper = "Name: number | Name: number",
                        )
                    }

                    // ------------------------------------------------ App
                    SectionCard {
                        if (Roles.hasRole(role, Roles.SUPER_ADMIN)) {
                            Text(
                                text = "App",
                                style = Type.headlineMd,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.semantics { heading() }, // R5
                            )
                        }
                        FormField(
                            label = "Version",
                            leadingIcon = "edit",
                            value = form["version"] ?: "",
                            onChange = { setField("version", it) },
                            placeholder = "1.0.0",
                        )
                    }

                    // ------------------------------------------------ Support & Social
                    SectionCard {
                        if (Roles.hasRole(role, Roles.SUPER_ADMIN)) {
                            Text(
                                text = "Support & Social",
                                style = Type.headlineMd,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.semantics { heading() }, // R5
                            )
                        }
                        FormField(
                            label = "WhatsApp",
                            leadingIcon = "call",
                            value = form["whatsapp"] ?: "",
                            onChange = { setField("whatsapp", it) },
                            placeholder = "+92 300 1234567",
                        )
                        FormField(
                            label = "Instagram",
                            leadingIcon = "edit",
                            value = form["instagram"] ?: "",
                            onChange = { setField("instagram", it) },
                            placeholder = "@areena",
                        )
                        FormField(
                            label = "Telegram",
                            leadingIcon = "edit",
                            value = form["telegram"] ?: "",
                            onChange = { setField("telegram", it) },
                            placeholder = "@areena",
                        )
                        FormField(
                            label = "YouTube",
                            leadingIcon = "edit",
                            value = form["youtube"] ?: "",
                            onChange = { setField("youtube", it) },
                            placeholder = "@areena",
                        )
                        FormField(
                            label = "Discord",
                            leadingIcon = "edit",
                            value = form["discord"] ?: "",
                            onChange = { setField("discord", it) },
                            placeholder = "discord.gg/areena",
                        )
                        FormTextArea(
                            label = "About / Mission",
                            value = form["aboutMission"] ?: "",
                            onChange = { setField("aboutMission", it) },
                            placeholder = "Write the mission statement shown on the About screen...",
                        )
                    }

                    // Security & Audit — SUPER_ADMIN
                    if (Roles.hasRole(role, Roles.SUPER_ADMIN)) {
                        SecurityAuditSection(env)
                    }

                    // ------------------------------------------------ Save CTA
                    val saveInteraction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = {
                            if (saving) return@Surface
                            saving = true
                            CoroutineScope(Dispatchers.Main).launch {
                                when (val res = safeCall {
                                    env.api.adminSaveSettings(AdminSettingsBody(settings = form.toMap()))
                                }) {
                                    is ApiResult.Success -> env.toast.show(title = "Settings saved")
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
                        },
                        interactionSource = saveInteraction,
                        enabled = !saving,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .pressScale(saveInteraction, 0.95f),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (saving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    trackColor = Color.Transparent,
                                )
                            }
                            Text(
                                text = if (saving) "Saving..." else "Save Settings",
                                style = Type.labelLg,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===========================================================================
// Payment Requests (FINANCE_ADMIN+)
// ===========================================================================

@Composable
private fun PaymentRequestsSection(env: NavEnv, fourEyes: Boolean) {
    var requests by remember { mutableStateOf<List<PaymentRequest>?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var viewingReceipt by remember { mutableStateOf<PaymentRequest?>(null) }
    var receiptImage by remember { mutableStateOf<String?>(null) }
    var receiptLoading by remember { mutableStateOf(false) }
    var receiptFailed by remember { mutableStateOf(false) }
    // A7-05: bounded receipt cache (was an unbounded in-composition map of
    // base64 payloads that grew with every opened receipt in the session).
    val receiptCache = remember { object : LinkedHashMap<String, String>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 8
    } }

    fun load() {
        CoroutineScope(Dispatchers.Main).launch {
            when (val res = safeCall { env.api.adminRequests() }) {
                is ApiResult.Success -> requests = res.data.requests
                is ApiResult.Error -> {
                    requests = emptyList()
                    env.toast.show(
                        title = "Couldn't load requests",
                        description = res.message,
                        variant = ToastVariant.Destructive,
                    )
                }
                is ApiResult.NetworkError -> {
                    requests = emptyList()
                    env.toast.show(
                        title = "Couldn't load requests",
                        description = res.message,
                        variant = ToastVariant.Destructive,
                    )
                }
            }
        }
    }

    fun openReceipt(r: PaymentRequest) {
        viewingReceipt = r
        receiptCache[r.id]?.let { cached ->
            receiptImage = cached
            receiptLoading = false
            receiptFailed = false
            return
        }
        receiptImage = null
        receiptLoading = true
        receiptFailed = false
        CoroutineScope(Dispatchers.Main).launch {
            when (val res = safeCall { env.api.adminRequestImage(r.id) }) {
                is ApiResult.Success -> {
                    val image = res.data.image
                    if (image != null) {
                        receiptCache[r.id] = image
                        receiptImage = image
                    } else {
                        receiptFailed = true
                    }
                }
                else -> {
                    receiptFailed = true
                    env.toast.show(
                        title = "Couldn't load receipt",
                        description = "Something went wrong",
                        variant = ToastVariant.Destructive,
                    )
                }
            }
            receiptLoading = false
        }
    }

    fun act(id: String, action: String) {
        if (busyId != null) return
        busyId = id
        CoroutineScope(Dispatchers.Main).launch {
            when (val res = safeCall {
                env.api.adminRequestAction(id, AdminActionBody(action = action))
            }) {
                is ApiResult.Success -> {
                    env.toast.show(
                        title = if (action == "approve") "Request approved" else "Request rejected",
                        description = if (action == "approve")
                            "The user has been notified and their balance updated."
                        else
                            "The user has been notified.",
                    )
                    load()
                }
                is ApiResult.Error -> env.toast.show(
                    title = if (action == "approve") "Approve failed" else "Reject failed",
                    description = res.message,
                    variant = ToastVariant.Destructive,
                )
                is ApiResult.NetworkError -> env.toast.show(
                    title = if (action == "approve") "Approve failed" else "Reject failed",
                    description = res.message,
                    variant = ToastVariant.Destructive,
                )
            }
            busyId = null
        }
    }

    LaunchedEffect(Unit) { load() }

    SectionCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Payment Requests",
                style = Type.headlineMd,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }, // R5
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val list = requests
                if (!list.isNullOrEmpty()) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                        Text(
                            text = list.size.toString(),
                            style = Type.labelSm,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                }
                RefreshButton(onClick = { load() })
            }
        }

        val list = requests
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                AreenaxSpinner(size = 24)
            }
            list.isEmpty() -> Text(
                text = "No pending payment requests.",
                style = Type.bodyMd,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                textAlign = TextAlign.Center,
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                list.forEach { r ->
                    val isDeposit = r.type == "DEPOSIT"
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = areenaColors().surfaceContainerLavender,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Row(
                                    Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isDeposit) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AreenaxIcon(
                                            name = if (isDeposit) "arrow_downward" else "arrow_upward",
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (isDeposit) MaterialTheme.colorScheme.secondary
                                            else MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = if (isDeposit) "Deposit Request" else "Withdrawal Request",
                                            style = Type.bodyLg,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "${r.user.gameName} • UID ${r.user.uid}",
                                            style = Type.labelSm,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${r.method ?: "—"} • ${timeAgo(r.createdAt)}",
                                            style = Type.labelSm,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Text(
                                    text = (if (isDeposit) "+" else "-") + formatMoney(kotlin.math.abs(r.amount)),
                                    style = Type.bodyLg,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDeposit) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.primary,
                                )
                            }

                            if (!r.note.isNullOrBlank()) {
                                Text(
                                    text = r.note,
                                    style = Type.labelSm,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }

                            Text(
                                text = "Ref: ${r.reference} • Wallet: Rs ${formatMoney(r.user.balance)}",
                                style = Type.labelSm,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            if (isDeposit) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    SmallQuietChip(
                                        icon = "receipt_long",
                                        label = "View Receipt",
                                        contentColor = MaterialTheme.colorScheme.primary,
                                    ) { openReceipt(r) }
                                }
                            }

                            if (r.reviewedById != null && r.approvedById == null) {
                                Row {
                                    SmallQuietChip(
                                        icon = "hourglass_top",
                                        label = "Reviewed — awaiting second approval",
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ) { }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val approveInteraction = remember { MutableInteractionSource() }
                                Surface(
                                    onClick = { act(r.id, "approve") },
                                    interactionSource = approveInteraction,
                                    enabled = busyId != r.id,
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .pressScale(approveInteraction, 0.95f),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        AreenaxIcon(
                                            name = "check",
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            filled = true,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                        )
                                        Text(
                                            text = if (fourEyes) "Final Approve" else "Approve",
                                            style = Type.labelMd,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                }
                                val rejectInteraction = remember { MutableInteractionSource() }
                                Surface(
                                    onClick = { act(r.id, "reject") },
                                    interactionSource = rejectInteraction,
                                    enabled = busyId != r.id,
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .pressScale(rejectInteraction, 0.95f),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        AreenaxIcon(
                                            name = "close",
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                        Text(
                                            text = "Reject",
                                            style = Type.labelMd,
                                            fontWeight = FontWeight.Bold,
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
    }

    // Full payment-receipt screenshot viewer
    viewingReceipt?.let { current ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { viewingReceipt = null }
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                onClick = { /* stopPropagation equivalent */ },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Deposit receipt • ${current.user.gameName}",
                                style = Type.bodyMd,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${current.method ?: "—"} • ${formatMoney(kotlin.math.abs(current.amount))}",
                                style = Type.labelSm,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        com.areenax.app.core.ui.AppBarCircleButton(onClick = { viewingReceipt = null }) {
                            AreenaxIcon(
                                name = "close",
                                contentDescription = "Close receipt",
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    when {
                        receiptLoading -> Box(
                            Modifier.fillMaxWidth().height(192.dp),
                            contentAlignment = Alignment.Center,
                        ) { AreenaxSpinner(size = 24) }
                        receiptImage != null -> AsyncImage(
                            model = receiptImage,
                            contentDescription = "Payment receipt from ${current.user.gameName}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.05f)),
                        )
                        else -> AdminViewerPlaceholder(
                            icon = "broken_image",
                            message = "Could not load image",
                        )
                    }
                }
            }
        }
    }
}

// ===========================================================================
// Tournament Management (MODERATOR+)
// ===========================================================================

private val STATUS_OPTIONS = listOf(
    "UPCOMING" to "Upcoming",
    "ONGOING" to "Live",
    // COMPLETED is publish-only (the status route 400s it) — use Publish Results
    "CANCELLED" to "Cancelled",
)

@Composable
private fun TournamentsAdminSection(env: NavEnv) {
    var tournaments by remember { mutableStateOf<List<Tournament>?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // uploaded result-proof screenshots, keyed "tournamentId:userId" → proof
    val proofMap = remember { mutableStateMapOf<String, AdminProof>() }
    var viewingProof by remember { mutableStateOf<AdminProof?>(null) }
    val proofImageCache = remember { mutableMapOf<String, String>() }
    var viewingImage by remember { mutableStateOf<String?>(null) }
    var viewerLoading by remember { mutableStateOf(false) }
    var viewerFailed by remember { mutableStateOf(false) }

    // per-tournament room form state
    var roomFormId by remember { mutableStateOf<String?>(null) }
    var roomId by remember { mutableStateOf("") }
    var roomPassword by remember { mutableStateOf("") }
    var roomExpiresAt by remember { mutableStateOf("") } // datetime-local value
    var expiryPickerOpen by remember { mutableStateOf(false) }
    // per-tournament results draft: entryId → { rank, kills, prize }
    val resultsDraft = remember { mutableStateMapOf<String, MutableMap<String, String>>() }

    fun load() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                coroutineScope {
                    val tDef = async { safeCall { env.api.adminTournaments() } }
                    val pDef = async { safeCall { env.api.adminProofs() } }
                    val tRes = tDef.await()
                    val pRes = pDef.await()
                    when (tRes) {
                        is ApiResult.Success -> tournaments = tRes.data.tournaments
                        is ApiResult.Error -> {
                            tournaments = emptyList()
                            env.toast.show(
                                title = "Couldn't load tournaments",
                                description = tRes.message,
                                variant = ToastVariant.Destructive,
                            )
                        }
                        is ApiResult.NetworkError -> {
                            tournaments = emptyList()
                            env.toast.show(
                                title = "Couldn't load tournaments",
                                description = tRes.message,
                                variant = ToastVariant.Destructive,
                            )
                        }
                    }
                    proofMap.clear()
                    if (pRes is ApiResult.Success) {
                        pRes.data.proofs.forEach { p ->
                            proofMap["${p.tournament.id}:${p.user.id}"] = p
                        }
                    }
                }
            } finally {
            }
        }
    }

    fun expand(t: Tournament) {
        val next = if (expandedId == t.id) null else t.id
        expandedId = next
        if (next != null) {
            roomFormId = t.id
            roomId = t.roomId ?: ""
            roomPassword = t.roomPassword ?: ""
            roomExpiresAt = t.roomExpiresAt?.let { iso ->
                iso.parseEpochMillis()?.let { millis ->
                    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime().toString().take(16)
                }
            } ?: ""
            resultsDraft.clear()
            t.entries.forEach { e ->
                resultsDraft[e.id] = mutableMapOf(
                    "rank" to (e.rank?.toString() ?: ""),
                    "kills" to (if (e.kills != 0) e.kills.toString() else ""),
                    "prize" to (if (e.prize != 0.0) formatPlain(e.prize) else ""),
                )
            }
        }
    }

    /** Position / kills changed → auto-fill the prize (admin can still edit it). */
    fun setDraftAuto(t: Tournament, entryId: String, field: String, value: String) {
        val row = resultsDraft[entryId]?.toMutableMap() ?: mutableMapOf("rank" to "", "kills" to "", "prize" to "")
        row[field] = value
        val rank = row["rank"]?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        val kills = row["kills"]?.takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: 0.0
        row["prize"] = autoPrizeFor(t, rank, kills).toString()
        resultsDraft[entryId] = row
    }

    fun setStatus(id: String, status: String) {
        if (busy) return
        busy = true
        CoroutineScope(Dispatchers.Main).launch {
            when (val res = safeCall {
                env.api.adminTournamentStatus(id, AdminStatusBody(status = status))
            }) {
                is ApiResult.Success -> {
                    env.toast.show(title = "Status set to $status")
                    load()
                }
                is ApiResult.Error -> env.toast.show(
                    title = "Status update failed",
                    description = res.message,
                    variant = ToastVariant.Destructive,
                )
                is ApiResult.NetworkError -> env.toast.show(
                    title = "Status update failed",
                    description = res.message,
                    variant = ToastVariant.Destructive,
                )
            }
            busy = false
        }
    }

    fun saveRoom(id: String) {
        if (busy) return
        if (roomId.isBlank() || roomPassword.isBlank()) {
            env.toast.show(title = "Room ID and Password are required", variant = ToastVariant.Destructive)
            return
        }
        busy = true
        CoroutineScope(Dispatchers.Main).launch {
            val expiresIso = roomExpiresAt.takeIf { it.isNotBlank() }?.let { local ->
                runCatching {
                    LocalDateTime.parse(local).atZone(ZoneId.systemDefault()).toInstant().toString()
                }.getOrNull()
            }
            when (val res = safeCall {
                env.api.adminTournamentRoom(
                    id,
                    AdminRoomBody(
                        roomId = roomId,
                        roomPassword = roomPassword,
                        roomExpiresAt = expiresIso,
                    ),
                )
            }) {
                is ApiResult.Success -> {
                    env.toast.show(
                        title = "Room details saved",
                        description = "Joined players can now see them on the card.",
                    )
                    load()
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
            busy = false
        }
    }

    fun toggleDisabled(t: Tournament) {
        if (busy) return
        busy = true
        CoroutineScope(Dispatchers.Main).launch {
            when (val res = safeCall {
                env.api.adminTournamentDisable(t.id, AdminDisableBody(disabled = !(t.disabled ?: false)))
            }) {
                is ApiResult.Success -> {
                    val disabled = t.disabled ?: false
                    env.toast.show(
                        title = if (disabled) "Tournament enabled" else "Tournament disabled",
                        description = if (disabled)
                            "Players are back on the normal match flow."
                        else
                            "Joined players now see Submit Result Proof instead of Match in Progress.",
                    )
                    load()
                }
                is ApiResult.Error -> env.toast.show(
                    title = "Update failed",
                    description = res.message,
                    variant = ToastVariant.Destructive,
                )
                is ApiResult.NetworkError -> env.toast.show(
                    title = "Update failed",
                    description = res.message,
                    variant = ToastVariant.Destructive,
                )
            }
            busy = false
        }
    }

    fun publishResults(t: Tournament) {
        if (busy) return
        val rows = t.entries.map { e ->
            val draft = resultsDraft[e.id]
            AdminResultEntry(
                entryId = e.id,
                rank = draft?.get("rank")?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0,
                kills = draft?.get("kills")?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0,
                prize = draft?.get("prize")?.takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: 0.0,
            )
        }
        busy = true
        CoroutineScope(Dispatchers.Main).launch {
            when (val res = safeCall {
                env.api.adminTournamentResults(t.id, AdminResultsBody(entries = rows))
            }) {
                is ApiResult.Success -> {
                    env.toast.show(
                        title = "Results published",
                        description = "Prizes credited and players notified.",
                    )
                    load()
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
            busy = false
        }
    }

    fun openProof(proof: AdminProof) {
        viewingProof = proof
        proofImageCache[proof.id]?.let { cached ->
            viewingImage = cached
            viewerLoading = false
            viewerFailed = false
            return
        }
        viewingImage = null
        viewerLoading = true
        viewerFailed = false
        CoroutineScope(Dispatchers.Main).launch {
            when (val res = safeCall { env.api.adminProofImage(proof.id) }) {
                is ApiResult.Success -> {
                    val image = res.data.image
                    if (image != null) {
                        proofImageCache[proof.id] = image
                        viewingImage = image
                    } else {
                        viewerFailed = true
                    }
                }
                else -> {
                    viewerFailed = true
                    env.toast.show(
                        title = "Couldn't load screenshot",
                        description = "Something went wrong",
                        variant = ToastVariant.Destructive,
                    )
                }
            }
            viewerLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    SectionCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Tournament Management",
                style = Type.headlineMd,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }, // R5
            )
            RefreshButton(onClick = { load() })
        }

        val list = tournaments
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                AreenaxSpinner(size = 24)
            }
            list.isEmpty() -> Text(
                text = "No tournaments yet.",
                style = Type.bodyMd,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                textAlign = TextAlign.Center,
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                list.forEach { t ->
                    val open = expandedId == t.id
                    val statusLabel = STATUS_OPTIONS.firstOrNull { it.first == t.status }?.second ?: t.status
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = areenaColors().surfaceContainerLavender,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // header (expands)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { expand(t) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = (t.game?.name?.takeIf { it.isNotEmpty() } ?: "T").first().toString(),
                                        style = Type.labelLg,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = t.name,
                                        style = Type.bodyLg,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = buildString {
                                            append("$statusLabel • ${t.currentPlayers}/${t.maxPlayers} players")
                                            if (t.disabled == true) append(" • Disabled")
                                            if (t.resultsPublishedAt != null) append(" • Results out")
                                            else if (t.roomId != null) append(" • Room set")
                                        },
                                        style = Type.labelSm,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                AreenaxIcon(
                                    name = if (open) "expand_less" else "expand_more",
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (open) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    // Match Status chips
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Match Status",
                                            style = Type.labelMd,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            STATUS_OPTIONS.forEach { (key, label) ->
                                                val selected = t.status == key
                                                val chipInteraction = remember { MutableInteractionSource() }
                                                Surface(
                                                    onClick = { if (!busy && !selected) setStatus(t.id, key) },
                                                    interactionSource = chipInteraction,
                                                    enabled = !busy && !selected,
                                                    shape = CircleShape,
                                                    color = if (selected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceContainerLowest,
                                                    border = if (selected) null
                                                    else androidx.compose.foundation.BorderStroke(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.outlineVariant,
                                                    ),
                                                    modifier = Modifier
                                                        .height(36.dp)
                                                        .pressScale(chipInteraction, 0.95f),
                                                ) {
                                                    Text(
                                                        text = label,
                                                        style = Type.labelMd,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 16.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Result Proof Mode toggle
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Result Proof Mode",
                                            style = Type.labelMd,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        val disabled = t.disabled ?: false
                                        val toggleInteraction = remember { MutableInteractionSource() }
                                        Surface(
                                            onClick = { if (!busy) toggleDisabled(t) },
                                            interactionSource = toggleInteraction,
                                            enabled = !busy,
                                            shape = CircleShape,
                                            color = if (disabled) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.surfaceContainerLowest,
                                            border = if (disabled) null
                                            else androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant,
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp)
                                                .pressScale(toggleInteraction, 0.95f),
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                AreenaxIcon(
                                                    name = if (disabled) "toggle_on" else "toggle_off",
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .semantics {
                                                            role = Role.Switch // A9-02
                                                            stateDescription =
                                                                if (disabled) "Disabled" else "Enabled"
                                                        },
                                                    tint = if (disabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Text(
                                                    text = if (disabled) "Disabled — players submit result proofs"
                                                    else "Disable Tournament",
                                                    style = Type.labelMd,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (disabled) Color.White
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        Text(
                                            text = "When disabled, joined players see a \"Submit Result Proof\" button instead of Match in Progress — they upload one screenshot from their gallery which you can review in the Result Proofs section.",
                                            style = Type.labelSm,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    // Room ID & Password + expiry
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Room ID & Password",
                                            style = Type.labelMd,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.semantics { heading() }, // R5
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            SmallInput(
                                                value = roomId,
                                                onValueChange = { roomId = it },
                                                placeholder = "Room ID",
                                                leadingIcon = "edit",
                                                modifier = Modifier.weight(1f),
                                            )
                                            SmallInput(
                                                value = roomPassword,
                                                onValueChange = { roomPassword = it },
                                                placeholder = "Password",
                                                leadingIcon = "edit",
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        // datetime-local expiry → readonly field + picker modal
                                        val expiryInteraction = remember { MutableInteractionSource() }
                                        Surface(
                                            onClick = { expiryPickerOpen = true },
                                            interactionSource = expiryInteraction,
                                            shape = AreenaxShapes.Pill,
                                            color = areenaColors().surfaceContainerLavender,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant,
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(40.dp)
                                                .pressScale(expiryInteraction, 0.98f),
                                        ) {
                                            Row(
                                                Modifier.padding(horizontal = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = roomExpiresAt.takeIf { it.isNotBlank() }
                                                        ?.let { formatDisplayDate("$it:00") } ?: "",
                                                    style = Type.labelMd,
                                                    color = if (roomExpiresAt.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                                                    else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                )
                                                if (roomExpiresAt.isNotBlank()) {
                                                    Box(
                                                        Modifier
                                                            .padding(end = 4.dp)
                                                            .clickable(
                                                                interactionSource = remember { MutableInteractionSource() },
                                                                indication = null,
                                                            ) { roomExpiresAt = "" },
                                                    ) {
                                                        AreenaxIcon(
                                                            name = "close",
                                                            contentDescription = "Clear room details expiry",
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Text(
                                            text = "Leave expiry empty to keep the details visible until you change them. After expiry the card automatically shows the match/tournament status instead.",
                                            style = Type.labelSm,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        val saveRoomInteraction = remember { MutableInteractionSource() }
                                        Surface(
                                            onClick = { if (!busy) saveRoom(t.id) },
                                            interactionSource = saveRoomInteraction,
                                            enabled = !busy,
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp)
                                                .pressScale(saveRoomInteraction, 0.95f),
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                AreenaxIcon(
                                                    name = "key",
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    filled = true,
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                )
                                                Text(
                                                    text = "Save Room Details",
                                                    style = Type.labelMd,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                )
                                            }
                                        }
                                    }

                                    // Results editor
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = "Results",
                                                style = Type.labelMd,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.semantics { heading() }, // R5
                                            )
                                            if (t.resultsPublishedAt != null) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                                ) {
                                                    Row(
                                                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) {
                                                        AreenaxIcon(
                                                            name = "check_circle",
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp),
                                                            filled = true,
                                                            tint = MaterialTheme.colorScheme.secondary,
                                                        )
                                                        Text(
                                                            text = "Results published",
                                                            style = Type.labelSm,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.secondary,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (t.entries.isEmpty()) {
                                            Text(
                                                text = "No participants joined yet.",
                                                style = Type.labelSm,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        } else {
                                            Column(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(256.dp)
                                                    .verticalScroll(rememberScrollState()),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                t.entries.forEach { e ->
                                                    val proof = proofMap["${t.id}:${e.userId}"]
                                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = buildString {
                                                                    append("${e.user?.gameName ?: ""} • UID ${e.user?.uid ?: ""}")
                                                                    if (!e.teamName.isNullOrBlank()) append(" • ${e.teamName}")
                                                                },
                                                                style = Type.labelSm,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f),
                                                            )
                                                            if (proof != null) {
                                                                SmallQuietChip(
                                                                    icon = "image",
                                                                    label = "View Proof",
                                                                    contentColor = MaterialTheme.colorScheme.primary,
                                                                ) { openProof(proof) }
                                                            }
                                                        }
                                                        val readOnly = t.resultsPublishedAt != null
                                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            SmallInput(
                                                                value = resultsDraft[e.id]?.get("rank") ?: "",
                                                                onValueChange = { if (!readOnly) setDraftAuto(t, e.id, "rank", it.filter { ch -> ch.isDigit() }) },
                                                                placeholder = "Rank",
                                                                leadingIcon = "edit",
                                                                enabled = !readOnly,
                                                                modifier = Modifier.weight(1f),
                                                            )
                                                            SmallInput(
                                                                value = resultsDraft[e.id]?.get("kills") ?: "",
                                                                onValueChange = { if (!readOnly) setDraftAuto(t, e.id, "kills", it.filter { ch -> ch.isDigit() }) },
                                                                placeholder = "Kills",
                                                                leadingIcon = "edit",
                                                                enabled = !readOnly,
                                                                modifier = Modifier.weight(1f),
                                                            )
                                                            SmallInput(
                                                                value = resultsDraft[e.id]?.get("prize") ?: "",
                                                                onValueChange = { if (!readOnly) resultsDraft[e.id]?.put("prize", it.filter { ch -> ch.isDigit() || ch == '.' }) },
                                                                placeholder = "Prize Rs",
                                                                leadingIcon = "edit",
                                                                enabled = !readOnly,
                                                                modifier = Modifier.weight(1f),
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        Text(
                                            text = "Entering a position auto-fills the prize — Rank 1 = 50%, Rank 2 = 30%, Rank 3 = 20% of the prize pool, other positions get the per-loser refund — plus per-kill money. You can edit any prize before publishing.",
                                            style = Type.labelSm,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (t.resultsPublishedAt == null) {
                                            val publishInteraction = remember { MutableInteractionSource() }
                                            Surface(
                                                onClick = { if (!busy && t.entries.isNotEmpty()) publishResults(t) },
                                                interactionSource = publishInteraction,
                                                enabled = !busy && t.entries.isNotEmpty(),
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(44.dp)
                                                    .pressScale(publishInteraction, 0.95f),
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    AreenaxIcon(
                                                        name = "emoji_events",
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                        filled = true,
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                    )
                                                    Text(
                                                        text = "Publish Results",
                                                        style = Type.labelMd,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "Publishing marks the tournament Completed, credits prize money to wallets and notifies players.",
                                            style = Type.labelSm,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )

                                        // Published Final Standings
                                        if (t.resultsPublishedAt != null && t.entries.isNotEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                                ),
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        AreenaxIcon(
                                                            name = "leaderboard",
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp),
                                                            tint = MaterialTheme.colorScheme.primary,
                                                        )
                                                        Text(
                                                            text = "Final Standings",
                                                            style = Type.labelMd,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            modifier = Modifier.semantics { heading() }, // R5
                                                        )
                                                    }
                                                    t.entries
                                                        .sortedBy { it.rank ?: 9999 }
                                                        .forEach { e ->
                                                            Row(
                                                                Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                            ) {
                                                                Text(
                                                                    text = buildString {
                                                                        append(if (e.rank != null) "#${e.rank}" else "—")
                                                                        append(" • ${e.user?.gameName ?: ""}")
                                                                        if (!e.teamName.isNullOrBlank()) append(" (${e.teamName})")
                                                                    },
                                                                    style = Type.labelSm,
                                                                    color = MaterialTheme.colorScheme.onSurface,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    modifier = Modifier.weight(1f),
                                                                )
                                                                Text(
                                                                    text = if (e.prize > 0) "Rs ${formatMoney(e.prize)}" else "No prize",
                                                                    style = Type.labelSm,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = if (e.prize > 0) MaterialTheme.colorScheme.secondary
                                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
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

    DateTimePickerModal(
        open = expiryPickerOpen,
        value = roomExpiresAt,
        onClose = { expiryPickerOpen = false },
        onChange = { roomExpiresAt = it.take(16) },
    )

    // Full result-proof screenshot viewer
    viewingProof?.let { current ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { viewingProof = null }
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                onClick = { /* stopPropagation equivalent */ },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "${current.user.gameName} • UID ${current.user.uid}",
                                style = Type.bodyMd,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${current.tournament.name} • ${current.tournament.mode}",
                                style = Type.labelSm,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        com.areenax.app.core.ui.AppBarCircleButton(onClick = { viewingProof = null }) {
                            AreenaxIcon(
                                name = "close",
                                contentDescription = "Close screenshot",
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    when {
                        viewerLoading -> Box(
                            Modifier.fillMaxWidth().height(192.dp),
                            contentAlignment = Alignment.Center,
                        ) { AreenaxSpinner(size = 24) }
                        viewingImage != null -> AsyncImage(
                            model = viewingImage,
                            contentDescription = "Result proof from ${current.user.gameName}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.05f)),
                        )
                        else -> AdminViewerPlaceholder(
                            icon = "broken_image",
                            message = "Could not load image",
                        )
                    }
                }
            }
        }
    }
}

/** The web's confirmed auto-prize structure: r1 50% / r2 30% / r3 20%, else loserPrize, + kills×perKill. */
private fun autoPrizeFor(t: Tournament, rank: Double?, kills: Double): Double {
    val r = rank?.let { if (it > 0) kotlin.math.floor(it) else null }
    val base = when (r) {
        1.0 -> kotlin.math.round((t.prizePool * 50) / 100)
        2.0 -> kotlin.math.round((t.prizePool * 30) / 100)
        3.0 -> kotlin.math.round((t.prizePool * 20) / 100)
        else -> kotlin.math.round(t.loserPrize)
    }
    val perKill = if (t.perKill > 0) t.perKill else 0.0
    val k = if (kills > 0) kills else 0.0
    return (base + k * perKill).coerceAtLeast(0.0)
}

private fun formatPlain(v: Double): String =
    if (v == kotlin.math.floor(v)) v.toLong().toString() else v.toString()

// ===========================================================================
// Security & Audit (SUPER_ADMIN)
// ===========================================================================

@Composable
private fun SecurityAuditSection(env: NavEnv) {
    var tab by remember { mutableStateOf("events") }
    var events by remember { mutableStateOf<List<AdminSecurityItem>?>(null) }
    var audit by remember { mutableStateOf<List<AdminSecurityItem>?>(null) }
    var reconciling by remember { mutableStateOf(false) }
    var reconcileChecked by remember { mutableIntStateOf(0) }
    var reconcileDrifts by remember { mutableStateOf<List<AdminReconcileRow>?>(null) }

    fun load() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                coroutineScope {
                    val evDef = async { safeCall { env.api.adminSecurity(type = "events", take = 50) } }
                    val auDef = async { safeCall { env.api.adminSecurity(type = "audit", take = 50) } }
                    val ev = evDef.await()
                    val au = auDef.await()
                    events = (ev as? ApiResult.Success)?.data?.items ?: emptyList()
                    audit = (au as? ApiResult.Success)?.data?.items ?: emptyList()
                    if (ev !is ApiResult.Success || au !is ApiResult.Success) {
                        env.toast.show(
                            title = "Couldn't load security data",
                            description = "Something went wrong",
                            variant = ToastVariant.Destructive,
                        )
                    }
                }
            } finally {
            }
        }
    }

    fun runReconcile() {
        if (reconciling) return
        reconciling = true
        CoroutineScope(Dispatchers.Main).launch {
            when (val res = safeCall { env.api.adminReconcile() }) {
                is ApiResult.Success -> {
                    reconcileChecked = res.data.checkedCount
                    reconcileDrifts = res.data.items
                }
                is ApiResult.Error -> env.toast.show(
                    title = "Reconciliation failed",
                    description = res.message,
                    variant = ToastVariant.Destructive,
                )
                is ApiResult.NetworkError -> env.toast.show(
                    title = "Reconciliation failed",
                    description = res.message,
                    variant = ToastVariant.Destructive,
                )
            }
            reconciling = false
        }
    }

    LaunchedEffect(Unit) { load() }

    SectionCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Security & Audit",
                style = Type.headlineMd,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }, // R5
            )
            RefreshButton(onClick = { load() })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabChip(label = "Events", active = tab == "events") { tab = "events" }
            TabChip(label = "Audit", active = tab == "audit") { tab = "audit" }
            val reconcileInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = { runReconcile() },
                interactionSource = reconcileInteraction,
                enabled = !reconciling,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.pressScale(reconcileInteraction, 0.95f),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AreenaxIcon(
                        name = "balance",
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Run reconciliation",
                        style = Type.labelMd,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (tab == "events") {
            val list = events
            when {
                list == null -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    AreenaxSpinner(size = 24)
                }
                list.isEmpty() -> Text(
                    text = "No security events yet.",
                    style = Type.bodyMd,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    textAlign = TextAlign.Center,
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { ev ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = areenaColors().surfaceContainerLavender,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = ev.action,
                                        style = Type.labelMd,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    when (ev.decision) {
                                        "DENY" -> StatusPill(
                                            text = "DENY",
                                            container = MaterialTheme.colorScheme.error.copy(alpha = 0.05f),
                                            content = MaterialTheme.colorScheme.error,
                                            border = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                        )
                                        "ALLOW" -> StatusPill(
                                            text = "ALLOW",
                                            container = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                            content = MaterialTheme.colorScheme.secondary,
                                            border = null,
                                        )
                                        else -> StatusPill(
                                            text = ev.decision ?: "",
                                            container = MaterialTheme.colorScheme.surfaceContainerLowest,
                                            content = MaterialTheme.colorScheme.onSurfaceVariant,
                                            border = MaterialTheme.colorScheme.outlineVariant,
                                        )
                                    }
                                    if ((ev.riskScore ?: 0) > 0) {
                                        Text(
                                            text = "risk ${ev.riskScore}",
                                            style = Type.labelSm,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                                Text(
                                    text = buildString {
                                        append(formatDateTime(ev.createdAt))
                                        if (!ev.endpoint.isNullOrBlank()) append(" • ${ev.endpoint}")
                                        if (!ev.reason.isNullOrBlank()) append(" • ${ev.reason}")
                                    },
                                    style = Type.labelSm,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val list = audit
            when {
                list == null -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    AreenaxSpinner(size = 24)
                }
                list.isEmpty() -> Text(
                    text = "No audit entries yet.",
                    style = Type.bodyMd,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    textAlign = TextAlign.Center,
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEach { a ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = areenaColors().surfaceContainerLavender,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = a.action,
                                        style = Type.labelMd,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    StatusPill(
                                        text = a.actorRole ?: "",
                                        container = MaterialTheme.colorScheme.surfaceContainerLowest,
                                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                                        border = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                                Text(
                                    text = "${formatDateTime(a.createdAt)} • ${a.targetType} • ${a.targetId}",
                                    style = Type.labelSm,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        reconcileDrifts?.let { result ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (result.isEmpty()) {
                        Text(
                            text = "All $reconcileChecked balances match the ledger.",
                            style = Type.labelMd,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        result.forEach { d ->
                            Text(
                                text = "${d.gameName}: balance ${formatMoney(d.balance)} vs ledger ${formatMoney(d.ledgerSum)}",
                                style = Type.labelSm,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===========================================================================
// shared admin pieces (file-private)
// ===========================================================================

@Composable
private fun TabChip(label: String, active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = if (active) null
        else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .heightIn(min = 36.dp)
            .pressScale(interaction, 0.95f),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = label,
                style = Type.labelMd,
                fontWeight = FontWeight.Bold,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, container: Color, content: Color, border: Color?) {
    Surface(
        shape = CircleShape,
        color = container,
        border = border?.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
    ) {
        Text(
            text = text,
            style = Type.labelSm,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Small h-7 chip with icon + label (View Receipt / View Proof / four-eyes). */
@Composable
private fun SmallQuietChip(icon: String, label: String, contentColor: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.heightIn(min = 28.dp).pressScale(interaction, 0.95f),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AreenaxIcon(
                name = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor,
            )
            Text(
                text = label,
                style = Type.labelSm,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
        }
    }
}

/** Web SMALL_INPUT: h-10 pill input (Areenax field concept). */
@Composable
private fun SmallInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: String? = null,
) {
    AreenaxPillField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        textStyle = Type.labelMd,
        height = 40.dp,
        enabled = enabled,
        imeAction = ImeAction.Default, // pre-migration KeyboardOptions(Text)
    )
}

/** Web Field: label + INPUT_CLS pill + optional helper (Areenax field concept). */
@Composable
private fun FormField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    helper: String? = null,
    inputMode: KeyboardType = KeyboardType.Text,
    leadingIcon: String? = null,
) {
    Column {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        AreenaxPillField(
            value = value,
            onValueChange = onChange,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            keyboardType = inputMode,
            imeAction = ImeAction.Default, // pre-migration KeyboardOptions(inputMode)
        )
        if (helper != null) {
            Text(
                text = helper,
                style = Type.bodyMd,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Web textarea: rounded-2xl px-4 py-3 min-h-7.5rem (Areenax field concept, no icon). */
@Composable
private fun FormTextArea(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    helper: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val c = areenaxFieldColors(focused)
    Column {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = Type.bodyLg.copy(color = c.text),
            cursorBrush = SolidColor(c.cursor),
            decorationBox = { inner ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .border(1.dp, c.ring, RoundedCornerShape(16.dp))
                        .background(c.container, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .onFocusChanged { focused = it.hasFocus },
                ) {
                    Box {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                style = Type.bodyLg,
                                color = c.placeholder,
                            )
                        }
                        inner()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (helper != null) {
            Text(
                text = helper,
                style = Type.bodyMd,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** h-10 px-3 On/Off pill (toggle_on / toggle_off). */
@Composable
private fun SettingToggle(label: String, on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val interaction = remember { MutableInteractionSource() }
        Surface(
            onClick = onToggle,
            interactionSource = interaction,
            shape = CircleShape,
            color = if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surfaceContainerLowest,
            border = if (on) null
            else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .heightIn(min = 40.dp)
                .pressScale(interaction, 0.95f),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AreenaxIcon(
                    name = if (on) "toggle_on" else "toggle_off",
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .semantics {
                            role = Role.Switch // A9-02
                            stateDescription = if (on) "On" else "Off"
                        },
                    tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (on) "On" else "Off",
                    style = Type.labelMd,
                    fontWeight = FontWeight.Bold,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "Could not load image" tile used by the receipt/proof viewers. */
@Composable
internal fun AdminViewerPlaceholder(icon: String, message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(192.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AreenaxIcon(
                name = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = message,
                style = Type.bodyMd,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
