package com.areenax.app.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import com.areenax.app.core.session.CrashReporter
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.ui.SocialMark
import com.areenax.app.core.session.SettingsCache
import com.areenax.app.core.theme.AreenaxShapes
import com.areenax.app.core.theme.Type
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.AppBarMode
import com.areenax.app.core.ui.GuestGateDialog
import com.areenax.app.core.ui.QrSheet
import com.areenax.app.core.ui.QrTab
import com.areenax.app.core.ui.QrTarget
import com.areenax.app.core.ui.SupportFab
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.cardShadow
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.rememberGuestGate
import com.areenax.app.data.Roles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ProfileScreen — key `profile` (SPEC/01 E1, bottom-nav tab). Exact port of
 * `src/components/screens/profile/ProfileScreen.tsx`:
 *
 * 1. Header card — 96dp avatar in a primary-container ring (Coil photo when
 *    `user.avatar` is set; initial circle = the web-visible render), gameName
 *    + superscript "PK" chip, email line (guests: "Guest user"), "UID: «uid»",
 *    Share + QR Code pill buttons.
 * 2. Menu card 1 — Edit Profile, Refer & Earn, My Team, Tasks, Theme (custom
 *    switch → SessionManager theme controller, persisted "areena-theme"),
 *    About, Terms & Conditions, Privacy Policy.
 * 3. Menu card 2 — Admin Console (role ∈ ADMIN_PANEL_ROLES → adminPanel —
 *    the mobile admin UI per SPEC/01 E8), My Stats, My Tournaments,
 *    Achievements, Leaderboard, Bind Account (guest gate "bind your bank
 *    account").
 * 4. Social icons row (only configured handles; same remote icons as web).
 * 5. Logout primary pill — best-effort POST /auth/logout then local logout
 *    (the web ProfileScreen has NO confirm dialog — behavior truth).
 */
@Composable
fun ProfileScreen(env: NavEnv) {
    val user by env.session.user.collectAsState()
    val themeIsDark by env.session.themeIsDark.collectAsState()
    val gate = rememberGuestGate()
    var qrOpen by remember { mutableStateOf(false) }
    var social by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showDeleteDialog by remember { mutableStateOf(false) } // Q15/A11-03

    // Social links come from app settings — icons without a configured handle are hidden
    LaunchedEffect(Unit) {
        SettingsCache.get { env.api }?.let { s ->
            social = mapOf(
                "instagram" to s.instagram,
                "telegram" to s.telegram,
                "discord" to s.discord,
                "youtube" to s.youtube,
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 128.dp), // web pb-32 (BottomNav + FAB clearance)
        ) {
            AppBar(mode = AppBarMode.Tab, title = "Profile")

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // ------------------------------------------------------ header card
                Surface(
                    shape = AreenaxShapes.Card,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = outlineBorder(0.3f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // 96dp avatar ring (border-2 primary-container p-1): photo
                        // when available, else the bg-primary initial circle.
                        Box(
                            Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            val avatar = user?.avatar
                            if (!avatar.isNullOrBlank()) {
                                AsyncImage(
                                    model = avatar,
                                    contentDescription = "Profile photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(CircleShape),
                                )
                            } else {
                                Box(
                                    Modifier
                                        .size(84.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = (user?.gameName?.takeIf { it.isNotBlank() } ?: "U")
                                            .first().uppercase(),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = Type.headlineLg,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        // gameName + "PK" superscript chip (10sp, raised)
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = user?.gameName ?: "Guest",
                                style = Type.headlineLgMobile,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "PK",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                modifier = Modifier.offset(y = (-4).dp),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (user?.isGuest == true) "Guest user" else (user?.email ?: "Guest user"),
                            style = Type.bodyMd,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "UID: ${user?.uid ?: "—"}",
                            style = Type.bodyMd,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            HeaderPillButton(label = "Share", leading = null) {
                                shareProfile(env, user?.uid, user?.referralCode)
                            }
                            HeaderPillButton(label = "QR Code", leading = "qr_code_2") { qrOpen = true }
                        }
                    }
                }

                // ------------------------------------------------------ menu card 1
                MenuCard {
                    MenuRow(icon = "person", label = "Edit Profile") { env.navigate(ScreenKeys.EDIT_PROFILE) }
                    MenuDivider()
                    MenuRow(icon = "group_add", label = "Refer & Earn") { env.navigate(ScreenKeys.REFER_EARN) }
                    MenuDivider()
                    MenuRow(icon = "diversity_3", label = "My Team") { env.navigate(ScreenKeys.MY_TEAM) }
                    MenuDivider()
                    MenuRow(icon = "checklist", label = "Tasks") { env.navigate(ScreenKeys.TASKS) }
                    MenuDivider()
                    // A9-02: the Theme row is ONE toggleable target with
                    // Role.Switch + stateDescription (was: row clickable + an
                    // inner unlabeled switch clickable = two dead-end targets).
                    val themeInteraction = remember { MutableInteractionSource() }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = themeIsDark,
                                role = Role.Switch,
                                interactionSource = themeInteraction,
                                indication = null,
                                onValueChange = { env.session.setTheme(!themeIsDark) },
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            AreenaxIcon(
                                name = "dark_mode",
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(text = "Theme", style = Type.bodyLg, color = MaterialTheme.colorScheme.onSurface)
                        }
                        ThemeSwitch(dark = themeIsDark, onToggle = {})
                    }
                    MenuDivider()
                    MenuRow(icon = "help", label = "About") { env.navigate(ScreenKeys.ABOUT) }
                    MenuDivider()
                    MenuRow(icon = "gavel", label = "Terms & Conditions") { env.navigate(ScreenKeys.TERMS) }
                    MenuDivider()
                    MenuRow(icon = "shield", label = "Privacy Policy") { env.navigate(ScreenKeys.PRIVACY) }
                    if (user?.isGuest != true) {
                        MenuDivider()
                        // Q15/A11-03/A6-03: Play User-Data policy requires a
                        // readily discoverable in-app account-deletion option.
                        MenuRow(icon = "delete_forever", label = "Delete Account") {
                            showDeleteDialog = true
                        }
                    }
                    // A13-02: local crash capture — share the stored report
                    // (user consents by sending; nothing leaves the device
                    // uninvited; excluded from backups per A6-02 rules).
                    if (CrashReporter.hasCrashLog(env.context)) {
                        MenuDivider()
                        MenuRow(icon = "bug_report", label = "Share crash log") {
                            val text = runCatching {
                                java.io.File(env.context.filesDir, "crash/last_crash.txt").readText()
                            }.getOrDefault("")
                            if (text.isNotBlank()) {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "AREENAX crash report")
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                runCatching {
                                    env.context.startActivity(
                                        Intent.createChooser(share, "Share crash log")
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }
                        }
                    }
                }

                // ------------------------------------------------------ menu card 2
                MenuCard {
                    // "Admin Console" — client visibility gate only; the server
                    // enforces the real RBAC matrix. Native: opens adminPanel.
                    if (Roles.isAdminPanelRole(user?.role)) {
                        MenuRow(icon = "admin_panel_settings", label = "Admin Console") {
                            env.navigate(ScreenKeys.ADMIN_PANEL)
                        }
                        MenuDivider()
                    }
                    MenuRow(icon = "bar_chart", label = "My Stats") { env.navigate(ScreenKeys.MY_STATS) }
                    MenuDivider()
                    MenuRow(icon = "sports_esports", label = "My Tournaments") { env.navigate(ScreenKeys.MY_TOURNAMENT) }
                    MenuDivider()
                    MenuRow(icon = "emoji_events", label = "Achievements") { env.navigate(ScreenKeys.ACHIEVEMENTS) }
                    MenuDivider()
                    MenuRow(icon = "leaderboard", label = "Leaderboard") { env.navigate(ScreenKeys.LEADERBOARD) }
                    MenuDivider()
                    MenuRow(icon = "account_balance", label = "Bind Account") {
                        if (gate.requireAccount(user?.isGuest == true, "bind your bank account")) {
                            env.navigate(ScreenKeys.BIND_ACCOUNT)
                        }
                    }
                }

                // ------------------------------------------------------ social row
                val links = socialLinks(social)
                if (links.isNotEmpty()) {
                    val context = LocalContext.current
                    Surface(
                        shape = AreenaxShapes.Card,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = outlineBorder(0.3f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            links.forEach { link ->
                                // ICON-01: local SocialMark (AboutScreen style) —
                                // was a remote googleusercontent AsyncImage that
                                // rendered blank offline.
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, link.href.toUri()),
                                                )
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    SocialMark(alt = link.alt, size = 22.dp)
                                }
                            }
                        }
                    }
                }

                // ------------------------------------------------------ logout
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val interaction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = { logout(env) },
                        interactionSource = interaction,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 2.dp,
                        modifier = Modifier.pressScale(interaction, 0.97f),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AreenaxIcon(
                                name = "logout",
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text(
                                text = "Logout",
                                style = Type.bodyLg,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }

        // Customer Support FAB (screen-owned placement — web fixed bottom-24 right-4)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 96.dp),
        ) {
            SupportFab(offset = true)
        }
    }

    // QR sheet — real scannable deep-link QR + live scanner (shared component)
    QrSheet(open = qrOpen, onClose = { qrOpen = false }, env = env, target = QrTarget.USER, initialTab = QrTab.SCAN)
    GuestGateDialog(gate, env)

    // Q15/A11-03: delete-account confirmation — typed "DELETE" guard (the
    // server also refuses while balance > 0 / hosted tournaments are open / a
    // team still has members). Success wipes the local session (the server
    // already destroyed every session row) and lands on login.
    if (showDeleteDialog) {
        var confirmText by remember { mutableStateOf("") }
        var deleting by remember { mutableStateOf(false) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteDialog = false },
            title = { Text("Delete account?", style = Type.headlineMd) },
            text = {
                Column {
                    Text(
                        text = "This permanently deletes your account, balance history, teams, friends and messages. This cannot be undone. Type DELETE to confirm.",
                        style = Type.bodyMd,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = confirmText,
                        onValueChange = { confirmText = it.uppercase() },
                        singleLine = true,
                        enabled = !deleting,
                        label = { Text("Type DELETE") },
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = !deleting && confirmText == "DELETE",
                    onClick = {
                        deleting = true
                        CoroutineScope(Dispatchers.Main).launch {
                            when (val res = safeCall { env.api.deleteAccount() }) {
                                is ApiResult.Success -> {
                                    env.toast.show(
                                        "Account deleted",
                                        description = "Your account and all its data were removed.",
                                    )
                                    env.logout()
                                }
                                is ApiResult.Error -> {
                                    deleting = false
                                    env.toast.show("Can't delete account", res.message, ToastVariant.Destructive)
                                }
                                is ApiResult.NetworkError -> {
                                    deleting = false
                                    env.toast.show("Can't delete account", res.message, ToastVariant.Destructive)
                                }
                            }
                        }
                    },
                ) { Text("Delete forever", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(enabled = !deleting, onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

// ------------------------------------------------------------------ helpers

/** 1dp outline-variant/N border (web `border-outline-variant/30`). */
@Composable
private fun outlineBorder(alpha: Float) = androidx.compose.foundation.BorderStroke(
    1.dp,
    MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha),
)

/** Share text exactly as the web ProfileScreen builds it; chooser → clipboard fallback. */
private fun shareProfile(env: NavEnv, uid: String?, referralCode: String?) {
    val text = "Join me on AREENAX! My UID is $uid — use my referral code $referralCode to get a welcome bonus!"
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TITLE, "AREENAX")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        env.context.startActivity(Intent.createChooser(shareIntent, "AREENAX"))
    } catch (_: Exception) {
        // navigator.share unavailable → clipboard fallback (web behavior)
        val cm = env.context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("AREENAX", text))
        env.toast.show(
            title = "Copied!",
            description = "Your profile invite was copied to the clipboard.",
            variant = ToastVariant.Default,
        )
    }
}

/** POST /auth/logout best-effort (a failed revoke still logs the user out), then local wipe. */
private fun logout(env: NavEnv) {
    CoroutineScope(Dispatchers.Main).launch {
        safeCall { env.api.logout() } // result ignored — web swallows errors too
        env.logout() // session wipe + navigator → login (clears pending-qr too)
    }
}

/** Social href resolution — socialHref() from the web (missing/"#" → hidden).
 *  ICON-01: the remote aida-public icon URLs are gone — SocialMark draws them. */
private data class SocialLink(val alt: String, val href: String)

private fun socialLinks(settings: Map<String, String>): List<SocialLink> {
    fun href(value: String?, base: String): String? {
        if (value.isNullOrEmpty() || value == "#") return null
        return if (value.startsWith("http")) value else "$base$value"
    }
    val defs = listOf(
        "Instagram" to "https://instagram.com/",
        "Telegram" to "https://t.me/",
        "Discord" to "https://discord.gg/",
        "YouTube" to "https://youtube.com/",
    )
    return defs.mapNotNull { (alt, base) ->
        val target = href(settings[alt.lowercase()], base) ?: return@mapNotNull null
        SocialLink(alt, target)
    }
}

/** px-6 py-2 min-h-[2.75rem] rounded-full bg-surface-container-low pill. */
@Composable
private fun HeaderPillButton(label: String, leading: String?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .pressScale(interaction, 0.97f),
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (leading != null) {
                AreenaxIcon(
                    name = leading,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(text = label, style = Type.labelSm, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** rounded-[1.5rem] px-5 py-2 card-shadow menu container. */
@Composable
private fun MenuCard(content: @Composable () -> Unit) {
    Surface(
        shape = AreenaxShapes.Card,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = outlineBorder(0.3f),
        modifier = Modifier
            .fillMaxWidth()
            .cardShadow(AreenaxShapes.Card),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) { content() }
    }
}

@Composable
private fun MenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
    )
}

/** flex items-center justify-between py-4 menu row with a 24dp icon + chevron. */
@Composable
private fun MenuRow(
    icon: String,
    label: String,
    onClick: (() -> Unit)? = null,
    right: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) { onClick?.invoke() }
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AreenaxIcon(
                name = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = label, style = Type.bodyLg, color = MaterialTheme.colorScheme.onSurface)
        }
        if (right != null) {
            right()
        } else {
            AreenaxIcon(
                name = "chevron_right",
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The web's hand-rolled 40x24 switch (track + 16dp knob, animated).
 *  A9-02 (WCAG 4.1.2): the ROW is the single toggleable target with
 *  Role.Switch + stateDescription — the visual switch no longer nests its own
 *  clickable (TalkBack announced an unlabeled, stateless focus target). */
@Composable
private fun ThemeSwitch(dark: Boolean, onToggle: () -> Unit) {
    val knobX by animateDpAsState(targetValue = if (dark) 20.dp else 4.dp, label = "themeKnob")
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 24.dp)
            .clip(CircleShape)
            .background(if (dark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            Modifier
                .offset { IntOffset(knobX.roundToPx(), 4.dp.roundToPx()) }
                .size(16.dp)
                .clip(CircleShape)
                .background(if (dark) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}
