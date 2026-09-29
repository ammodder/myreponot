package com.areenax.app.ui.screens.wallet

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.session.SettingsCache
import com.areenax.app.core.theme.Type
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.primaryGlow
import com.areenax.app.core.util.formatMoney
import com.areenax.app.core.util.maskAccountNumber
import com.areenax.app.data.BankAccount

/*
 * WithdrawScreen — 1:1 port of src/components/screens/wallet/WithdrawScreen.tsx
 * (SPEC/01 C5, key `withdraw`, HTML withdraw.html).
 *
 *  - Centered payment-method pill: bound account "«bankName» «masked»"
 *    (auto-selected from GET /bank — saved selection restored when still
 *    valid, else accounts[0] per the task spec), "Select account" when
 *    accounts exist unselected, "Bind Account" when none → tap navigates
 *    selectBank {purpose:"withdraw"} (or bindAccount {returnTo:"withdraw"}
 *    when there is nothing to select).
 *  - 48px amount + pulsing primary cursor bar (web animate-pulse → infinite
 *    transition), "«formatMoney(balance)» Available balance" pill.
 *  - Quick chips 100 / 500 / 1000 / 5000 (grid-cols-4).
 *  - Withdraw's OWN keypad style (HTML withdraw.html: rounded-xl py-6
 *    headline-lg-mobile bold keys, shadow-sm) — NOT the shared AmountPad.
 *  - Validations → "Enter an amount"/"Amount too low" (SettingsCache
 *    minWithdraw, default 500)/"Insufficient balance" → confirmWithdraw
 *    {amount, method, account}. CTA disabled without a bound account with
 *    the exact helper copy.
 */

private val WITHDRAW_QUICK_CHIPS = listOf("100", "500", "1000", "5000")

private val WithdrawAmountStyle = Type.heroAmount.copy(fontSize = 48.sp, lineHeight = 56.sp)

@Composable
fun WithdrawScreen(env: NavEnv) {
    val colors = MaterialTheme.colorScheme
    val user by env.session.user.collectAsState()

    var amount by rememberSaveable { mutableStateOf("0") }
    var accounts by remember { mutableStateOf<List<BankAccount>>(emptyList()) }
    var selectedAccount by remember { mutableStateOf<BankAccount?>(null) }
    var method by rememberSaveable { mutableStateOf("Jazzcash") }
    var minWithdraw by remember { mutableStateOf(200.0) } // A4-03: server default is 200 (was 500)

    LaunchedEffect(Unit) {
        // Restore the saved selection if its account still exists; else the
        // first bound account (task spec: pill shows GET /bank accounts[0]).
        val saved = runCatching { env.session.bankSelection() }.getOrNull()
        when (val res = safeCall { env.api.bankAccounts() }) {
            is ApiResult.Success -> {
                val list = res.data.accounts
                accounts = list
                val restored = saved?.accountId?.let { id -> list.firstOrNull { it.id == id } }
                    ?: list.firstOrNull()
                selectedAccount = restored
                method = restored?.bankName ?: saved?.method ?: method
            }
            else -> {
                saved?.method?.let { method = it }
            }
        }
        SettingsCache.get { env.api }?.let { s ->
            if (s.minWithdraw > 0) minWithdraw = s.minWithdraw
        }
    }

    val pillLabel = when {
        selectedAccount != null ->
            "${selectedAccount!!.bankName} ${maskAccountNumber(selectedAccount!!.accountNumber)}"
        accounts.isNotEmpty() -> "Select account"
        else -> "Bind Account"
    }
    val pillIcon = if (selectedAccount == null && accounts.isEmpty()) "add_card" else "account_balance"

    val canWithdraw = selectedAccount != null

    val handleContinue: () -> Unit = {
        val value = amount.toDoubleOrNull()
        when {
            value == null || value <= 0 -> env.toast.show(
                "Enter an amount",
                description = "Please enter a valid withdraw amount.",
                variant = ToastVariant.Destructive,
            )
            value < minWithdraw -> env.toast.show(
                "Amount too low",
                description = "Minimum withdraw Rs ${formatMoney(minWithdraw)}",
                variant = ToastVariant.Destructive,
            )
            user != null && value > (user?.balance ?: 0.0) -> env.toast.show(
                "Insufficient balance",
                description = "Available balance is Rs ${formatMoney(user?.balance)}",
                variant = ToastVariant.Destructive,
            )
            else -> {
                val params = mutableMapOf<String, Any?>(
                    "amount" to value,
                    "method" to (selectedAccount?.bankName ?: method),
                )
                params["account"] = selectedAccount
                env.navigate(ScreenKeys.CONFIRM_WITHDRAW, params)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        AppBar(title = "Withdraw")

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 32.dp), // mt-4 pb-8
            verticalArrangement = Arrangement.spacedBy(12.dp), // space-y-3
        ) {
            // ---- Payment Method Pill ----
            Box(Modifier.fillMaxWidth().padding(bottom = 16.dp)) { // mb-4
                val pillInteraction = remember { MutableInteractionSource() }
                Surface(
                    shape = CircleShape,
                    color = colors.surfaceContainerLowest,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant),
                    shadowElevation = 1.dp, // shadow-sm
                    modifier = Modifier
                        .align(Alignment.Center)
                        .pressScale(pillInteraction, pressedScale = 0.95f)
                        .clickable(
                            interactionSource = pillInteraction,
                            indication = null,
                        ) {
                            if (accounts.isEmpty()) {
                                // nothing to pick → go bind one first
                                env.navigate(ScreenKeys.BIND_ACCOUNT, mapOf("returnTo" to "withdraw"))
                            } else {
                                env.navigate(ScreenKeys.SELECT_BANK, mapOf("purpose" to "withdraw"))
                            }
                        },
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp), // px-4 py-2
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp), // space-x-2
                    ) {
                        AreenaxIcon(
                            name = pillIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp), // text-sm
                            tint = colors.onSurfaceVariant,
                        )
                        Text(
                            text = pillLabel,
                            style = Type.bodyMd.copy(fontWeight = FontWeight.Medium),
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        AreenaxIcon(
                            name = "expand_more",
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---- Amount Input Area ----
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp), // py-6
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp), // space-y-4
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = amount,
                        style = WithdrawAmountStyle,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                    // Blinking cursor — w-[2px] h-10 bg-primary animate-pulse
                    val transition = rememberInfiniteTransition(label = "cursorPulse")
                    val cursorAlpha by transition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.4f,
                        animationSpec = infiniteRepeatable(
                            animation = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "cursorPulseAlpha",
                    )
                    Box(
                        Modifier
                            .padding(start = 4.dp) // ml-1
                            .size(width = 2.dp, height = 40.dp)
                            .alpha(cursorAlpha)
                            .clip(CircleShape)
                            .background(colors.primary),
                    )
                }
                // Available balance pill
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(colors.surfaceContainerLow)
                        .padding(horizontal = 12.dp, vertical = 6.dp), // px-3 py-1.5
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp), // space-x-1
                ) {
                    Text(
                        text = "${formatMoney(user?.balance)} Available balance",
                        style = Type.labelSm,
                        color = colors.onSurfaceVariant,
                    )
                    AreenaxIcon(
                        name = "info",
                        contentDescription = null,
                        modifier = Modifier.size(14.dp), // text-[0.875rem]
                        tint = colors.onSurfaceVariant,
                    )
                }
            }

            // ---- Quick Amount Chips (grid-cols-4) ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp), // mt-6
                horizontalArrangement = Arrangement.spacedBy(12.dp), // gap-3
            ) {
                WITHDRAW_QUICK_CHIPS.forEach { chip ->
                    val chipInteraction = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .weight(1f)
                            .pressScale(chipInteraction, pressedScale = 0.95f)
                            .clip(CircleShape)
                            .background(colors.surfaceContainerLowest)
                            .border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), CircleShape)
                            .clickable(interactionSource = chipInteraction, indication = null) {
                                amount = chip
                            }
                            .padding(vertical = 10.dp), // py-2.5
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = chip, style = Type.bodyMd, color = colors.onSurface)
                    }
                }
            }

            // ---- Keypad — withdraw's OWN style (rounded-xl py-6, shadow-sm) ----
            Column(
                Modifier
                    .padding(top = 32.dp, bottom = 32.dp) // mt-8 pb-8
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp), // gap-3
            ) {
                val keypadKeys = listOf(
                    "1", "2", "3",
                    "4", "5", "6",
                    "7", "8", "9",
                    ".", "0", "backspace",
                )
                keypadKeys.chunked(3).forEach { rowKeys ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowKeys.forEach { key ->
                            WithdrawKey(
                                key = key,
                                modifier = Modifier.weight(1f),
                            ) {
                                amount = if (key == "backspace") walletDeleteAmount(amount) else walletAppendAmount(amount, key)
                            }
                        }
                    }
                }
            }

            // ---- Continue to Withdraw ----
            val ctaInteraction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp) // mt-6
                    .height(56.dp) // py-4
                    .alpha(if (canWithdraw) 1f else 0.5f) // disabled:opacity-50
                    .primaryGlow(RoundedCornerShape(50))
                    .pressScale(ctaInteraction, pressedScale = 0.98f)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .clickable(
                        interactionSource = ctaInteraction,
                        indication = null,
                        enabled = canWithdraw,
                        onClick = handleContinue,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Continue to Withdraw",
                    style = Type.bodyLg.copy(fontWeight = FontWeight.Bold),
                    color = colors.onPrimary,
                )
            }

            // ---- Disabled helper text ----
            if (!canWithdraw) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp), // mt-3
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AreenaxIcon(
                        name = if (accounts.isEmpty()) "add_card" else "touch_app",
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (accounts.isEmpty()) {
                            "Bind a withdrawal account first — tap \u201CBind Account\u201D above."
                        } else {
                            "Select a withdrawal account above to continue."
                        },
                        style = Type.labelSm,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Withdraw keypad key — HTML withdraw.html: rounded-xl py-6 bold, shadow-sm. */
@Composable
private fun WithdrawKey(key: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interaction, pressedScale = 0.95f) // active:scale-95
            .clip(RoundedCornerShape(12.dp)) // rounded-xl
            .background(colors.surfaceContainerLowest)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 24.dp), // py-6
        contentAlignment = Alignment.Center,
    ) {
        if (key == "backspace") {
            AreenaxIcon(
                name = "backspace",
                contentDescription = "Backspace",
                modifier = Modifier.size(22.dp), // text-headline-lg-mobile
                tint = colors.onSurface,
            )
        } else {
            Text(
                text = key,
                style = Type.headlineLgMobile, // text-headline-lg-mobile font-bold
                color = colors.onSurface,
            )
        }
    }
}
