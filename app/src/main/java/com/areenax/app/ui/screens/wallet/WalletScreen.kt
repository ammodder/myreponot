package com.areenax.app.ui.screens.wallet

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.theme.LightInversePrimary
import com.areenax.app.core.theme.roseSoft
import com.areenax.app.core.ui.AreenaxSpinner
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.AppBarMode
import com.areenax.app.core.ui.EmptyState
import com.areenax.app.core.ui.GuestGateDialog
import com.areenax.app.core.ui.SupportFab
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.rememberGuestGate
import com.areenax.app.core.util.formatBalance
import com.areenax.app.core.util.formatMoney
import com.areenax.app.core.util.timeAgo
import com.areenax.app.data.Transaction
import kotlin.math.abs

/*
 * WalletScreen — 1:1 port of src/components/screens/wallet/WalletScreen.tsx
 * (SPEC/01 C1, key `wallet`, HTML wallet.html).
 *
 *  - Ambient glows behind the content (wallet.html `.ambient-glow`: #b4c5ff
 *    top-left 300px, #ffdadb bottom-right 400px, blur(100px), opacity .4 —
 *    painted as soft radial gradients, `position: fixed` → non-scrolling layer).
 *  - AppBar tab "My Wallet" (no balance chip) + notification bell.
 *  - Balance card: bg-primary rounded-2xl h-160 with the white/10 radial
 *    sheen (HTML), "Current Balance" + "Rs «formatBalance»" (32 bold).
 *    Balance starts from the session user and is refreshed via GET /wallet
 *    (which also syncs SessionManager balance, like the web's setUser).
 *  - Quick actions (HTML labels Add / Withdraw / Transfer) behind the guest gate.
 *  - Transactions card with the React TX_META icon/title map, per-type icon
 *    circle colors (money-in = tx-in-icon emerald pair, money-out =
 *    surface-container + primary), subtitle "method • timeAgo" (HTML),
 *    signed amounts (+X emerald / -X neutral, WITHDRAW negative = primary),
 *    status dot+label, Filter dropdown chips All / Money In / Money Out.
 *  - Empty → EmptyState "No transactions yet" (receipt_long); SupportFab.
 */

private enum class FilterMode(val label: String) { All("All"), In("Money In"), Out("Money Out") }

/** Money-in transaction types (React IN_TYPES). */
private val IN_TYPES = setOf(
    "DEPOSIT", "PRIZE", "REFERRAL_BONUS", "TASK_REWARD", "REFUND",
)

/** React TX_META — icon / title / green-circle per transaction type. */
private data class TxMeta(val icon: String, val title: String, val green: Boolean)

private fun txMeta(type: String): TxMeta = when (type) {
    "DEPOSIT" -> TxMeta("payments", "Deposit", true)
    "TRANSFER" -> TxMeta("sync_alt", "Transfer", false)
    "WITHDRAW" -> TxMeta("outbox", "Withdraw", false)
    "PRIZE" -> TxMeta("emoji_events", "Prize", true)
    "ENTRY_FEE" -> TxMeta("sports_esports", "Entry Fee", false)
    "REFERRAL_BONUS" -> TxMeta("card_giftcard", "Referral Bonus", true)
    "TASK_REWARD" -> TxMeta("task_alt", "Task Reward", true)
    "REFUND" -> TxMeta("payments", "Refund", true)
    "ADJUSTMENT" -> TxMeta("tune", "Adjustment", true)
    else -> TxMeta("payments", type, true)
}

/**
 * Methods that describe WHAT the money was (not the channel) become the row's
 * main title — e.g. a guest signup credit shows "Welcome Bonus" (React
 * SUBJECT_METHODS).
 */
private fun txTitle(t: Transaction): String = when ((t.method ?: "").trim().lowercase()) {
    "welcome bonus" -> "Welcome Bonus"
    "signup bonus" -> "Signup Bonus"
    "daily task" -> "Daily Task"
    "task reward" -> "Task Reward"
    "referral" -> "Referral Bonus"
    else -> txMeta(t.type).title.ifEmpty { t.type }
}

private fun txStatusLabel(status: String): Pair<String, Int> = when (status) {
    "PENDING" -> "Pending" to STATUS_MUTED
    "REJECTED", "FAILED" -> "Rejected" to STATUS_ERROR
    "COMPLETED" -> "Completed" to STATUS_COMPLETED
    else -> status to STATUS_MUTED
}

// Status color selectors (resolved against the theme inside the composable).
private const val STATUS_MUTED = 0
private const val STATUS_ERROR = 1
private const val STATUS_COMPLETED = 2

/** A9: 30s TTL cache so quick revisits to Wallet render instantly without spinner. */
private const val WALLET_TTL_MS = 30_000L
private var walletCacheBalance: Double? = null
private var walletCacheTxs: List<Transaction>? = null
private var walletCacheAt: Long = 0L

@Composable
fun WalletScreen(env: NavEnv) {
    val ext = areenaColors()
    val colors = MaterialTheme.colorScheme
    val user by env.session.user.collectAsState()

    var balance by rememberSaveable { mutableStateOf(walletCacheBalance ?: user?.balance ?: 0.0) }
    var txs by remember { mutableStateOf<List<Transaction>?>(walletCacheTxs) }
    var filter by rememberSaveable { mutableStateOf(FilterMode.All.name) }
    val filterMode = remember(filter) { FilterMode.valueOf(filter) }
    var filterOpen by remember { mutableStateOf(false) }

    val gate = rememberGuestGate()

    // GET /wallet — refreshes balance & txs; uses cached data for immediate display
    // on quick revisits (<30s).
    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        val isFresh = walletCacheTxs != null && (now - walletCacheAt < WALLET_TTL_MS)
        if (isFresh) {
            balance = walletCacheBalance ?: balance
            txs = walletCacheTxs
        }
        when (val res = safeCall { env.api.wallet() }) {
            is ApiResult.Success -> {
                balance = res.data.balance
                txs = res.data.transactions
                walletCacheBalance = res.data.balance
                walletCacheTxs = res.data.transactions
                walletCacheAt = System.currentTimeMillis()
                env.session.updateBalance(res.data.balance)
            }
            is ApiResult.Error -> {
                if (!isFresh) txs = emptyList()
                env.toast.show(
                    "Couldn't load wallet",
                    description = res.message,
                    variant = ToastVariant.Destructive,
                )
            }
            is ApiResult.NetworkError -> {
                if (!isFresh) txs = emptyList()
                env.toast.show(
                    "Couldn't load wallet",
                    description = res.message,
                    variant = ToastVariant.Destructive,
                )
            }
        }
    }

    val filtered = remember(txs, filterMode) {
        txs?.filter { t ->
            when (filterMode) {
                FilterMode.All -> true
                FilterMode.In -> t.type in IN_TYPES
                FilterMode.Out -> t.type !in IN_TYPES
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // ---- Ambient glows (wallet.html .glow-1/.glow-2, position:fixed) ----
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    fun glow(center: Offset, radius: Float, color: Color) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(color.copy(alpha = 0.4f), Color.Transparent),
                                center = center,
                                radius = radius,
                            ),
                        )
                    }
                    // glow-1: top:-10% left:-10% w-300 h-300 #b4c5ff
                    glow(
                        center = Offset(size.width * 0.05f, size.height * 0.05f),
                        radius = 150.dp.toPx() * 2.2f, // blur(100px) spread
                        color = LightInversePrimary,
                    )
                    // glow-2: bottom:10% right:-20% w-400 h-400 #ffdadb
                    glow(
                        center = Offset(size.width * 1.1f, size.height * 0.78f),
                        radius = 200.dp.toPx() * 2.2f,
                        color = roseSoft,
                    )
                },
        )

        // ---- Scrollable content --------------------------------------------
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 112.dp), // web pb-28 (nav + SupportFab clearance)
        ) {
            // Unified AppBar (bottom-nav root tab) — no balance chip here.
            AppBar(mode = AppBarMode.Tab, title = "My Wallet")

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // ---- Main Wallet Card (bg-primary h-[10rem] rounded-2xl) ----
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp) // h-[10rem]
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.primary)
                        .drawBehind {
                            // HTML decorative sheen: radial ellipse white/10 at
                            // top:-50% right:-20%, 150%x150%, opacity 30%.
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.10f),
                                        Color.Transparent,
                                    ),
                                    center = Offset(size.width * 0.45f, size.height * 0.25f),
                                    radius = size.width * 0.75f,
                                ),
                                alpha = 0.3f,
                            )
                        }
                        .padding(20.dp), // p-gutter-card
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Column {
                        Text(
                            text = "Current Balance",
                            style = Type.labelSm,
                            color = Color.White.copy(alpha = 0.8f), // text-on-primary/80
                        )
                        Spacer(Modifier.height(4.dp)) // mb-1
                        Text(
                            text = "Rs ${formatBalance(balance)}",
                            style = Type.heroAmount,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }

                // ---- Quick Actions Row ----
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceContainerLowest,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp) // py-4 px-margin-page
                            .padding(top = 0.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        QuickAction(
                            label = "Add", // HTML quick-action label (React: "Deposit")
                            icon = "add",
                            container = colors.primaryContainer,
                            content = colors.onPrimaryContainer,
                            bordered = false,
                            elevated = true,
                        ) {
                            if (gate.requireAccount(user?.isGuest == true, "make a deposit")) {
                                env.navigate(ScreenKeys.DEPOSIT)
                            }
                        }
                        QuickAction(
                            label = "Withdraw",
                            icon = "arrow_upward",
                            container = colors.surfaceContainer,
                            content = colors.onSurfaceVariant,
                            bordered = true,
                            elevated = false,
                        ) {
                            if (gate.requireAccount(user?.isGuest == true, "withdraw your winnings")) {
                                env.navigate(ScreenKeys.WITHDRAW)
                            }
                        }
                        QuickAction(
                            label = "Transfer",
                            icon = "sync_alt",
                            container = colors.surfaceContainer,
                            content = colors.onSurfaceVariant,
                            bordered = true,
                            elevated = false,
                        ) {
                            if (gate.requireAccount(user?.isGuest == true, "send money")) {
                                env.navigate(ScreenKeys.TRANSFER_MONEY)
                            }
                        }
                    }
                }

                // ---- Transactions Section ----
                Surface(
                    shape = RoundedCornerShape(24.dp), // rounded-[1.5rem]
                    color = colors.surfaceContainerLowest,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.2f)),
                    shadowElevation = 2.dp, // .premium-shadow
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) { // p-gutter-card
                        // Header + Filter pill + dropdown
                        Box(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Transactions",
                                    style = Type.headlineMd,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurface,
                                    modifier = Modifier.semantics { heading() }, // R5
                                )
                                val filterInteraction = remember { MutableInteractionSource() }
                                Surface(
                                    onClick = { filterOpen = !filterOpen },
                                    shape = CircleShape,
                                    color = colors.surfaceContainer,
                                    modifier = Modifier.pressScale(filterInteraction, pressedScale = 0.95f),
                                ) {
                                    Row(
                                        Modifier
                                            .clickable(
                                                interactionSource = filterInteraction,
                                                indication = null,
                                            ) { filterOpen = !filterOpen }
                                            .padding(horizontal = 12.dp, vertical = 6.dp), // px-3 py-1.5
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text("Filter", style = Type.labelSm, color = colors.primary)
                                        AreenaxIcon(
                                            name = "tune",
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = colors.primary,
                                        )
                                    }
                                }
                            }

                            if (filterOpen) {
                                // Full-screen dismiss layer (web fixed inset-0 z-20)
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .clickable(onClick = { filterOpen = false }, indication = null, interactionSource = remember { MutableInteractionSource() }),
                                )
                                // Dropdown chips — All / Money In / Money Out
                                val density = LocalDensity.current
                                Popup(
                                    alignment = Alignment.TopEnd,
                                    offset = androidx.compose.ui.unit.IntOffset(0, with(density) { 44.dp.roundToPx() }),
                                    onDismissRequest = { filterOpen = false },
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = colors.surfaceContainerLowest,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.2f)),
                                        shadowElevation = 6.dp,
                                    ) {
                                        Row(
                                            Modifier.padding(8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            FilterMode.entries.forEach { f ->
                                                val selected = filterMode == f
                                                val chipBg = if (selected) {
                                                    if (ext.isDark) Color.White else colors.primary
                                                } else {
                                                    colors.surfaceContainer
                                                }
                                                val chipFg = if (selected) {
                                                    if (ext.isDark) Color.Black else colors.onPrimary
                                                } else {
                                                    colors.onSurfaceVariant
                                                }
                                                Text(
                                                    text = f.label,
                                                    style = Type.labelSm,
                                                    color = chipFg,
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(chipBg)
                                                        .clickable {
                                                            filter = f.name
                                                            filterOpen = false
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp)) // mb-4

                        when {
                            filtered == null -> {
                                // Loading spinner (web border-spinner, py-10)
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AreenaxSpinner(size = 24, strokeWidth = 2)
                                }
                            }
                            filtered.isEmpty() -> EmptyState(message = "No transactions yet", icon = "receipt_long")
                            else -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                filtered.forEachIndexed { index, t ->
                                    TransactionRow(
                                        tx = t,
                                        isLast = index == filtered.lastIndex,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Customer Support FAB (above the bottom nav — right-4 bottom-24 on web)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 20.dp),
        ) {
            SupportFab(offset = true)
        }

        GuestGateDialog(gate, env)
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: String,
    container: Color,
    content: Color,
    bordered: Boolean,
    elevated: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(56.dp) // w-14 h-14
                .pressScale(interaction, pressedScale = 0.95f) // group-active:scale-95
                .then(if (elevated) Modifier.shadow(4.dp, CircleShape) else Modifier)
                .clip(CircleShape)
                .background(container)
                .then(
                    if (bordered) {
                        Modifier.border(1.dp, colors.outlineVariant.copy(alpha = 0.2f), CircleShape)
                    } else {
                        Modifier
                    }
                )
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            AreenaxIcon(name = icon, contentDescription = label, modifier = Modifier.size(24.dp), tint = content)
        }
        Text(text = label, style = Type.labelSm, color = colors.onSurface)
    }
}

@Composable
private fun TransactionRow(tx: Transaction, isLast: Boolean) {
    val colors = MaterialTheme.colorScheme
    val ext = areenaColors()
    val meta = txMeta(tx.type)
    val positive = tx.amount > 0

    val amountColor = when {
        positive -> ext.txInIconFg // emerald-600 light / emerald-400 dark
        tx.type == "WITHDRAW" -> colors.primary
        else -> colors.onSurface
    }

    val (statusLabel, statusKind) = txStatusLabel(tx.status)
    val statusColor = when (statusKind) {
        STATUS_ERROR -> colors.error
        STATUS_COMPLETED -> ext.txInIconFg
        else -> colors.onSurfaceVariant
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = if (isLast) 4.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        // 48dp type icon circle
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (meta.green) ext.txInIconBg else colors.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            AreenaxIcon(
                name = meta.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (meta.green) ext.txInIconFg else colors.primary,
            )
        }
        Spacer(Modifier.width(12.dp))
        // Left — main subject, time below
        Column(Modifier.weight(1f)) {
            Text(
                text = txTitle(tx),
                style = Type.bodyLg,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // HTML subtitle: "method • timeAgo"
                text = listOfNotNull(
                    tx.method?.trim()?.takeIf { it.isNotEmpty() },
                    timeAgo(tx.createdAt).takeIf { it.isNotEmpty() },
                ).joinToString(" • "),
                style = Type.labelSm.copy(fontWeight = FontWeight.Normal),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        // Right — signed amount + status below
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = (if (positive) "+" else "-") + formatMoney(abs(tx.amount)),
                style = Type.bodyLg.copy(fontWeight = FontWeight.SemiBold),
                color = amountColor,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .size(6.dp) // w-1.5 h-1.5
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Text(
                    text = statusLabel,
                    style = Type.labelSm.copy(fontWeight = FontWeight.Medium),
                    color = statusColor,
                )
            }
        }
        }
        // Non-last rows: bottom hairline (border-b border-outline-variant/20)
        if (!isLast) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.outlineVariant.copy(alpha = 0.2f)),
            )
        }
    }
}
