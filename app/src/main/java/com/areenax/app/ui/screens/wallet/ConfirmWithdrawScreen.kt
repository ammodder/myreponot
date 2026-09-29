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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.Type
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.GuestGateDialog
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.cardShadow
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.primaryGlow
import com.areenax.app.core.ui.rememberGuestGate
import com.areenax.app.core.util.formatMoney
import com.areenax.app.core.util.maskAccountNumber
import com.areenax.app.data.BankAccount
import com.areenax.app.data.WithdrawRequest
import kotlinx.coroutines.launch

/*
 * ConfirmWithdrawScreen — 1:1 port of src/components/screens/wallet/
 * ConfirmWithdrawScreen.tsx (SPEC/01 C6, key `confirmWithdraw`,
 * HTML confirmwithdraw.html).
 *
 *  - Invalid amount param → replace(withdraw) (web effect).
 *  - Hero: 48dp bordered circle + account_balance 28dp primary + 32px amount
 *    + "Reviewed & approved by admin" (React copy).
 *  - Rounded-24 summary card rows (React): Amount / Withdraw To
 *    ("«bankName» «masked»" or method) / Bank (method) / Our Fee "0" /
 *    You Get with payment_arrow_down rotated 270°.
 *  - Fixed footer: "risks & terms" disclaimer + "Confirm Withdraw"
 *    (guest gate "withdraw your winnings"; disabled while submitting) →
 *    POST /wallet/withdraw {amount, method, accountNumber, accountTitle} →
 *    setUser(balance) → withdrawSuccess {amount, method, reference};
 *    errors → "Withdraw failed" destructive toast.
 */

@Composable
fun ConfirmWithdrawScreen(env: NavEnv) {
    val colors = MaterialTheme.colorScheme

    // ---- Params (read once) ------------------------------------------------
    val amount = (env.params["amount"] as? Number)?.toDouble() ?: Double.NaN
    val method = env.params["method"] as? String ?: "Jazzcash"
    val account = env.params["account"] as? BankAccount?

    val user by env.session.user.collectAsState()
    val gate = rememberGuestGate()
    val scope = rememberCoroutineScope()

    var submitting by rememberSaveable { mutableStateOf(false) }

    // Invalid amount → back to the withdraw screen (web useEffect).
    LaunchedEffect(amount) {
        if (amount.isNaN()) env.replace(ScreenKeys.WITHDRAW)
    }

    val accountLabel = account
        ?.let { "${it.bankName} ${maskAccountNumber(it.accountNumber)}" }
        ?: method

    val onConfirmClick: () -> Unit = {
        if (!submitting) {
            submitting = true
            val body = WithdrawRequest(
                amount = amount,
                method = method,
                accountNumber = account?.accountNumber ?: "",
                accountTitle = account?.accountTitle,
            )
            scope.launch {
                when (val res = safeCall { env.api.withdraw(body) }) {
                    is ApiResult.Success -> {
                        // Web: setUser({...u, balance: res.balance})
                        env.session.user.value?.let { u ->
                            res.data.balance?.let { b -> env.session.setUser(u.copy(balance = b)) }
                        }
                        env.navigate(
                            ScreenKeys.WITHDRAW_SUCCESS,
                            mapOf(
                                "amount" to amount,
                                "method" to method,
                                "reference" to res.data.reference,
                            ),
                        )
                    }
                    is ApiResult.Error -> env.toast.show(
                        "Withdraw failed",
                        description = res.message,
                        variant = ToastVariant.Destructive,
                    )
                    is ApiResult.NetworkError -> env.toast.show(
                        "Withdraw failed",
                        description = res.message,
                        variant = ToastVariant.Destructive,
                    )
                }
                submitting = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .walletRadialBackground(
                topColor = colors.surfaceContainer,
                baseColor = colors.background,
            ),
    ) {
        AppBar(title = "Confirm Withdraw")

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 32.dp, bottom = 210.dp), // pt-8 pb-24 + footer
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---- Hero ----
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp), // mb-8
            ) {
                Box(
                    Modifier
                        .size(48.dp) // w-12 h-12
                        .cardShadow(CircleShape)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerLowest)
                        .border(1.dp, colors.outlineVariant.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    AreenaxIcon(
                        name = "account_balance",
                        contentDescription = null,
                        modifier = Modifier.size(28.dp), // font-size 28px
                        tint = colors.primary,
                    )
                }
                Spacer(Modifier.height(16.dp)) // mb-4
                Text(
                    text = formatMoney(amount.takeIf { !it.isNaN() }),
                    style = Type.heroAmount, // text-[2rem] bold tracking-tight
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(4.dp)) // mb-1
                Text(
                    text = "Reviewed & approved by admin",
                    style = Type.bodyMd,
                    color = colors.onSurfaceVariant,
                )
            }

            // ---- Summary Card (rounded-[1.5rem], mx-4 rows with dividers) ----
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceContainerLowest,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.2f)),
                shadowElevation = 2.dp, // card-shadow
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 16.dp)) { // mx-4
                    val rows = listOf(
                        WithdrawRow("payments", "Amount", formatMoney(amount.takeIf { !it.isNaN() }), rotated = false),
                        WithdrawRow("wallet", "Withdraw To", accountLabel, rotated = false),
                        WithdrawRow("account_balance", "Bank", method, rotated = false),
                        WithdrawRow("remove_circle_outline", "Our Fee", "0", rotated = false),
                        WithdrawRow("payment_arrow_down", "You Get", formatMoney(amount.takeIf { !it.isNaN() }), rotated = true),
                    )
                    rows.forEachIndexed { index, row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp), // py-padding-item
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (row.rotated) {
                                    AreenaxIcon(
                                        name = row.icon,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .rotate(270f), // transform rotate(270deg)
                                        tint = colors.onSurfaceVariant,
                                    )
                                } else {
                                    AreenaxIcon(
                                        name = row.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = colors.onSurfaceVariant,
                                    )
                                }
                                Text(text = row.label, style = Type.bodyMd, color = colors.onSurfaceVariant)
                            }
                            Text(
                                text = row.value,
                                style = Type.bodyMd.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.onSurface,
                            )
                        }
                        if (index < rows.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(colors.outlineVariant.copy(alpha = 0.2f)),
                            )
                        }
                    }
                }
            }
        }

        // ---- Footer ----
        Surface(
            color = colors.surface, // bg-surface
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 24.dp), // px-4 py-6
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "By confirming, you agree to the risks & terms.",
                    style = Type.labelSm.copy(fontWeight = FontWeight.Normal),
                    color = colors.onSurfaceVariant, // A9-01: was alpha 0.7 → 2.67:1
                )
                Spacer(Modifier.height(12.dp)) // gap-3
                val confirmInteraction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp) // py-4
                        .primaryGlow(RoundedCornerShape(50))
                        .pressScale(confirmInteraction, pressedScale = 0.95f)
                        .clip(CircleShape)
                        .background(colors.primary.copy(alpha = if (submitting) 0.6f else 1f)) // disabled:opacity-60
                        .clickable(
                            interactionSource = confirmInteraction,
                            indication = null,
                            enabled = !submitting,
                        ) {
                            if (gate.requireAccount(user?.isGuest == true, "withdraw your winnings")) {
                                onConfirmClick()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Confirm Withdraw",
                        style = Type.bodyLg.copy(fontWeight = FontWeight.Bold),
                        color = colors.onPrimary,
                    )
                }
            }
        }

        GuestGateDialog(gate, env)
    }
}

private data class WithdrawRow(val icon: String, val label: String, val value: String, val rotated: Boolean)
