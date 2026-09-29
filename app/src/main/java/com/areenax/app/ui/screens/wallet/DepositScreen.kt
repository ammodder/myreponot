package com.areenax.app.ui.screens.wallet

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.session.BankSelection
import com.areenax.app.core.session.SettingsCache
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.ui.AmountPad
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.BankSelectSheet
import com.areenax.app.core.ui.ToastVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.primaryGlow
import com.areenax.app.core.util.formatMoney
import com.areenax.app.core.util.maskAccountNumber
import com.areenax.app.data.BankAccount

/*
 * DepositScreen — 1:1 port of src/components/screens/wallet/DepositScreen.tsx
 * (SPEC/01 C2, key `deposit`, HTML deposit.html).
 *
 *  - Radial top glow background (web inline style: radial-gradient(100% 100%
 *    at 50% 0%, surface-container-low → background)).
 *  - Funding-source card (HTML `.glass-card` translucent white + sync_alt
 *    swap button → navigate(selectBank {purpose:"deposit"}) — the native
 *    picker screen replaces the web's inline BankSelectSheet, per task).
 *  - 48px amount display + core AmountPad (default "0", single ".", max 8
 *    significant digits — React append/delete rules implemented below).
 *  - "Continue to deposit" validates empty/≤0 ("Enter an amount"/"Please
 *    enter a valid deposit amount.") and the SettingsCache minimumDeposit
 *    ("Amount too low" / "Minimum deposit Rs X") → depositConfirm
 *    {amount, method, accountId?}.
 *  - Bank method/accountId restore from the SessionManager
 *    areena_bank_selection/areena_bank_method helpers (web sessionStorage
 *    equivalents) with the "Jazzcash" default.
 */

/** React appendNumber — max 8 significant digits, single ".", "0" base. */
internal fun walletAppendAmount(current: String, num: String): String {
    if (num == ".") {
        if (current.contains(".")) return current
        return "$current."
    }
    if (!num.all { it.isDigit() }) return current
    if (current == "0") return num
    if (current.replace(".", "").length >= 8) return current
    return current + num
}

/** React deleteNumber — floors at "0". */
internal fun walletDeleteAmount(current: String): String =
    if (current.length > 1) current.dropLast(1) else "0"

/** Radial top glow shared by the deposit/confirm/success wallet screens. */
internal fun Modifier.walletRadialBackground(topColor: Color, baseColor: Color): Modifier =
    drawBehind {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(topColor, baseColor),
                center = Offset(size.width / 2f, 0f),
                radius = size.height * 1.41f, // 100% 100% at 50% 0%
            ),
        )
    }

private val DepositAmountStyle = Type.heroAmount.copy(fontSize = 48.sp, lineHeight = 48.sp)

@Composable
fun DepositScreen(env: NavEnv) {
    val colors = MaterialTheme.colorScheme
    val ext = areenaColors()

    var amount by rememberSaveable { mutableStateOf("0") }
    var selection by remember { mutableStateOf(BankSelection(method = "Jazzcash", accountId = null)) }
    var accounts by remember { mutableStateOf<List<BankAccount>>(emptyList()) }
    var depositBanks by remember { mutableStateOf<List<String>>(emptyList()) }
    var showBankSheet by remember { mutableStateOf(false) }
    var minDeposit by remember { mutableStateOf(100.0) }
    var maxDeposit by remember { mutableStateOf(100_000.0) } // A4-03: server default 100000

    // Restore the saved funding selection (sessionStorage equivalents) and
    // load bound accounts + the min-deposit setting (failures ignored).
    LaunchedEffect(Unit) {
        selection = env.session.bankSelection() ?: BankSelection(method = "Jazzcash", accountId = null)
        when (val res = safeCall { env.api.bankAccounts() }) {
            is ApiResult.Success -> accounts = res.data.accounts
            else -> Unit // bound accounts are optional for depositing
        }
        SettingsCache.get { env.api }?.let { s ->
            if (s.minDeposit > 0) minDeposit = s.minDeposit
            if (s.maxDeposit > 0) maxDeposit = s.maxDeposit // A4-03
            if (s.depositAccounts.isNotEmpty()) {
                depositBanks = s.depositAccounts.keys.map { it.replaceFirstChar { c -> c.uppercase() } }.distinct()
            }
        }
    }

    val selectedAccount = selection.accountId?.let { id -> accounts.firstOrNull { it.id == id } }

    val handleKey: (String) -> Unit = { key ->
        amount = if (key == "backspace") walletDeleteAmount(amount) else walletAppendAmount(amount, key)
    }

    val handleContinue = {
        val value = amount.toDoubleOrNull()
        if (value == null || value <= 0) {
            env.toast.show(
                "Enter an amount",
                description = "Please enter a valid deposit amount.",
                variant = ToastVariant.Destructive,
            )
        } else if (value < minDeposit) {
            env.toast.show(
                "Amount too low",
                description = "Minimum deposit Rs ${formatMoney(minDeposit)}",
                variant = ToastVariant.Destructive,
            )
        } else if (value > maxDeposit) {
            // A4-03: client pre-gate for the server's max bound
            env.toast.show(
                "Amount too high",
                description = "Maximum deposit Rs ${formatMoney(maxDeposit)}",
                variant = ToastVariant.Destructive,
            )
        } else {
            val params = mutableMapOf<String, Any?>(
                "amount" to value,
                "method" to selection.method,
            )
            if (selection.accountId != null) params["accountId"] = selection.accountId
            env.navigate(ScreenKeys.DEPOSIT_CONFIRM, params)
        }
        Unit
    }

    Column(
        Modifier
            .fillMaxSize()
            .walletRadialBackground(
                topColor = colors.surfaceContainerLow,
                baseColor = colors.background,
            )
            .navigationBarsPadding(),
    ) {
        AppBar(title = "Deposit")

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 24.dp) // pt-4 pb-6
                .weight(1f),
        ) {
            // ---- Deposit Source Card (HTML glass-card) ----
            Surface(
                shape = RoundedCornerShape(24.dp), // rounded-3xl
                color = if (ext.isDark) colors.surfaceContainerLowest else Color.White.copy(alpha = 0.7f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (ext.isDark) {
                        colors.outlineVariant.copy(alpha = 0.2f)
                    } else {
                        Color(0x80DCE9FF) // rgba(220,233,255,0.5)
                    },
                ),
                shadowElevation = 2.dp, // card-shadow
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp), // mb-8
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp), // py-3 px-4
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // 48dp rounded-2xl icon tile
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surfaceContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            AreenaxIcon(
                                name = "account_balance",
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = colors.primary,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = selection.method,
                                style = Type.headlineMd,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (selectedAccount != null) {
                                Text(
                                    text = "${selectedAccount.accountTitle} • ${maskAccountNumber(selectedAccount.accountNumber)}",
                                    style = Type.labelSm,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    // Swap button → opens "Choose Funding Bank" bottom sheet modal
                    val swapInteraction = remember { MutableInteractionSource() }
                    Surface(
                        shape = CircleShape,
                        color = colors.surfaceContainerLowest,
                        modifier = Modifier
                            .size(40.dp) // w-10 h-10
                            .pressScale(swapInteraction, pressedScale = 0.95f)
                            .clickable(
                                interactionSource = swapInteraction,
                                indication = null,
                            ) {
                                showBankSheet = true
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AreenaxIcon(
                                name = "sync_alt",
                                contentDescription = "Change deposit source",
                                modifier = Modifier.size(16.dp), // text-sm
                                tint = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ---- Amount Display (48px, flex-grow centered) ----
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 32.dp), // mb-8
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = amount,
                    style = DepositAmountStyle,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
            }

            // ---- Keypad (always visible) ----
            AmountPad(onKey = handleKey)

            Spacer(Modifier.height(24.dp))

            // ---- Continue Button ----
            // A8-04: disabled affordance matching the sibling money CTAs
            // (withdraw/transfer dim at 0.5 while invalid). The validation
            // toast stays as a backstop for races.
            val ctaInteraction = remember { MutableInteractionSource() }
            val depositValue = amount.toDoubleOrNull()
            val depositValid =
                depositValue != null && depositValue > 0 &&
                    depositValue >= minDeposit && depositValue <= maxDeposit
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp) // py-4
                    .primaryGlow(RoundedCornerShape(50))
                    .pressScale(ctaInteraction, pressedScale = 0.95f)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = if (depositValid) 1f else 0.5f))
                    .clickable(interactionSource = ctaInteraction, indication = null, enabled = depositValid, onClick = handleContinue),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Continue to deposit",
                    style = Type.bodyLg.copy(fontWeight = FontWeight.Bold),
                    color = colors.onPrimary,
                )
            }
        }

        BankSelectSheet(
            open = showBankSheet,
            onClose = { showBankSheet = false },
            title = "Choose Funding Bank",
            selectedId = selection.accountId?.let { "acc:$it" } ?: "bank:${selection.method}",
            banks = if (depositBanks.isNotEmpty()) depositBanks else listOf("Jazzcash", "Easypaisa", "SadaPay", "NayaPay", "Bank Transfer", "Raast"),
            accounts = accounts,
            showBanks = true,
            showAccounts = accounts.isNotEmpty(),
            onSelect = { _, bankName, account ->
                selection = BankSelection(method = bankName, accountId = account?.id)
                val ioScope = CoroutineScope(Dispatchers.IO)
                ioScope.launch {
                    env.session.setBankSelection(bankName, account?.id)
                }
            },
            onConfirm = { showBankSheet = false },
        )
    }
}
