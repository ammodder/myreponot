package com.areenax.app.ui.screens.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.Type
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.EmptyState
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AreenaxPillField
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.primaryGlow
import com.areenax.app.core.util.FUNDING_BANKS
import com.areenax.app.data.BankAccount
import kotlinx.coroutines.launch

/*
 * SelectBankScreen — 1:1 port of src/components/screens/wallet/SelectBankScreen.tsx
 * (SPEC/01 C10, key `selectBank`, HTML selectbank.html).
 *
 * The legacy full-screen funding picker (the web's BankSelectSheet supersedes
 * it there, but the native Deposit/Withdraw flows navigate here — task spec):
 *  - Dimmed Deposit-screen mockup backdrop (AppBar "Deposit", source card with
 *    swap_horiz, 64px "0", 1-6 numpad tiles) + black/60 overlay.
 *  - Bottom sheet visual: 85% height (web h-[85vh]), rounded-t-[1.75rem],
 *    drag handle, "Choose Funding Bank" title, working search pill.
 *  - 5 method rows (FUNDING_BANKS — Q13 typo fixed: "Alfalah Bank") with the
 *    active row showing a FILLED account_balance icon + green check circle,
 *    then "Your Accounts" from GET /bank (raw accountNumber shown).
 *  - Confirm → SessionManager bank-selection helper (areena_bank_method /
 *    areena_bank_selection equivalents; accountId kept for bound accounts)
 *    → goBack(). Empty search → EmptyState "No banks found".
 */

@Composable
fun SelectBankScreen(env: NavEnv) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var selected by rememberSaveable { mutableStateOf("Jazzcash") }
    var selectedAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var accounts by remember { mutableStateOf<List<BankAccount>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        when (val res = safeCall { env.api.bankAccounts() }) {
            is ApiResult.Success -> accounts = res.data.accounts
            else -> accounts = emptyList()
        }
    }

    val q = query.trim().lowercase()
    val filteredMethods = FUNDING_BANKS.filter { it.lowercase().contains(q) }
    val filteredAccounts = accounts.filter {
        it.bankName.lowercase().contains(q) || it.accountTitle.lowercase().contains(q)
    }

    Box(Modifier.fillMaxSize()) {
        // ---- Background screen (dimmed Deposit mockup) ----
        Column(Modifier.fillMaxSize()) {
            AppBar(title = "Deposit")
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) { // px-margin-page pt-4
                // Source card mockup
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = colors.surfaceContainerLowest,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.3f)),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp), // mb-8
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp) // p-gutter-card
                            .padding(top = 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceContainerLow),
                                contentAlignment = Alignment.Center,
                            ) {
                                AreenaxIcon(
                                    name = "account_balance",
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = colors.primary,
                                )
                            }
                            Text("Jazzcash", style = Type.headlineMd, color = colors.onSurface)
                        }
                        Box(
                            Modifier
                                .size(32.dp) // w-8 h-8
                                .clip(CircleShape)
                                .background(colors.surfaceContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            AreenaxIcon(
                                name = "swap_horiz",
                                contentDescription = "Swap method",
                                modifier = Modifier.size(18.dp), // text-[1.125rem]
                                tint = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                // Amount display mockup
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) { // my-12
                    Text(
                        text = "0",
                        style = Type.heroAmount.copy(fontSize = 64.sp, lineHeight = 64.sp),
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                }
                // Numpad mockup — tiles 1..6
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        listOf("1", "2", "3").forEach { n -> NumpadTile(n, Modifier.weight(1f)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        listOf("4", "5", "6").forEach { n -> NumpadTile(n, Modifier.weight(1f)) }
                    }
                }
            }
        }

        // ---- Dimmed overlay (bg-black/60) ----
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x99000000)),
        )

        // ---- Bottom sheet (h-[85vh] rounded-t-[1.75rem]) ----
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(colors.surfaceContainerLowest),
        ) {
            // Drag handle
            Box(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(width = 48.dp, height = 6.dp) // w-12 h-1.5
                        .clip(RoundedCornerShape(percent = 50))
                        .background(colors.outlineVariant.copy(alpha = 0.5f)),
                )
            }
            // Header + search
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                Text(
                    text = "Choose Funding Bank",
                    style = Type.headlineMd,
                    color = colors.onSurface,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 24.dp), // mb-6
                )
                WalletPillTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search Your Funding Bank",
                    leadingIcon = "search",
                )
            }
            // Scrollable list
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp), // pb-8
            ) {
                if (filteredMethods.isEmpty() && filteredAccounts.isEmpty()) {
                    EmptyState(message = "No banks found", icon = "account_balance")
                } else {
                    filteredMethods.forEachIndexed { index, m ->
                        val active = selected == m
                        SelectBankRow(
                            name = m,
                            subtitle = null,
                            active = active,
                            first = index == 0,
                            onClick = {
                                selected = m
                                selectedAccountId = null
                            },
                        )
                    }

                    if (filteredAccounts.isNotEmpty()) {
                        Text(
                            text = "Your Accounts",
                            style = Type.bodyMd.copy(fontWeight = FontWeight.Medium),
                            color = colors.onSurface,
                            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp), // mt-5 mb-2
                        )
                        filteredAccounts.forEachIndexed { index, a ->
                            val active = selected == a.bankName
                            SelectBankRow(
                                name = a.bankName,
                                subtitle = "${a.accountTitle} • ${a.accountNumber}", // raw number (web parity)
                                active = active,
                                first = index == 0,
                                onClick = {
                                    selected = a.bankName
                                    selectedAccountId = a.id
                                },
                            )
                        }
                    }
                }
            }
            // Confirm footer
            val confirmInteraction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp), // pt-2 pb-8
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp) // h-[3.5rem]
                        .primaryGlow(RoundedCornerShape(50))
                        .pressScale(confirmInteraction, pressedScale = 0.98f)
                        .clip(CircleShape)
                        .background(colors.primary)
                        .clickable(interactionSource = confirmInteraction, indication = null) {
                            scope.launch {
                                env.session.setBankSelection(selected, selectedAccountId)
                                env.goBack()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Confirm",
                        style = Type.labelLg,
                        color = colors.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun NumpadTile(label: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .height(64.dp) // h-16
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerLowest)
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = Type.headlineMd, color = colors.onSurface)
    }
}

@Composable
private fun SelectBankRow(
    name: String,
    subtitle: String?,
    active: Boolean,
    first: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (first) Modifier else Modifier.padding(top = 8.dp)) // mt-2
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .pressScale(interaction, pressedScale = 0.98f) // active:scale-[0.98]
            .padding(16.dp), // p-padding-item
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // 48dp rounded-xl icon tile — FILLED glyph when active (web FILL 1)
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceContainerLowest)
                    .border(1.dp, colors.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AreenaxIcon(
                    name = "account_balance",
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    filled = active,
                    tint = colors.primary,
                )
            }
            Column {
                Text(
                    text = name,
                    style = Type.bodyLg.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = Type.labelSm.copy(fontWeight = FontWeight.Normal),
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp), // mt-0.5
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (active) {
            // Green check circle (bg-secondary)
            Box(
                Modifier
                    .size(24.dp) // w-6 h-6
                    .clip(CircleShape)
                    .background(colors.secondary),
                contentAlignment = Alignment.Center,
            ) {
                AreenaxIcon(
                    name = "check",
                    contentDescription = "Selected",
                    modifier = Modifier.size(16.dp),
                    tint = colors.onSecondary,
                )
            }
        }
    }
}

/**
 * The web's standard pill text input — now the shared [AreenaxPillField]
 * (owner field concept): matte charcoal pill in dark mode, white→blue
 * leading icon, bright-white cursor, subtle 1dp focus ring. Kept as an
 * `internal` wrapper so the wallet call sites (DepositConfirmScreen,
 * TransferScreen) compile unchanged.
 */
@Composable
internal fun WalletPillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    AreenaxPillField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        textStyle = Type.bodyLg,
        keyboardType = keyboardType,
        imeAction = ImeAction.Done,
    )
}
