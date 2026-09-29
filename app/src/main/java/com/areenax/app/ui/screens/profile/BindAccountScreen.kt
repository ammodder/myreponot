package com.areenax.app.ui.screens.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
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
import com.areenax.app.core.ui.BankSelectSheet
import com.areenax.app.core.ui.GuestGateDialog
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.rememberGuestGate
import com.areenax.app.data.BankAccount
import com.areenax.app.data.BankAccountResponse
import com.areenax.app.data.BankAccountsResponse
import com.areenax.app.data.BankCreateRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BindAccountScreen — key `bindAccount` (SPEC/01 E6). Exact port of
 * `src/components/screens/profile/BindAccountScreen.tsx` — the BANK-account
 * binding flow (the web file binds a withdrawal bank account; there is no
 * binding-code/QR input on this screen):
 *
 * - GET /bank → Saved Accounts list (bank tile icon, bankName + "Primary"
 *   chip when isPrimary, accountTitle, "•••• «last4»").
 * - Form card: Bank Name (opens BankSelectSheet — title "Select Bank",
 *   search "Search Your Bank", banks only, confirm label "Confirm"),
 *   Account Holder Name (`person` icon), Account Number / IBAN
 *   (`credit_card` icon). Errors: "Select your bank", "Account holder name
 *   is required", "Account number is required", "Enter a valid account
 *   number" (<8 chars).
 * - Footnote (`info`): "Ensure your account details match your official
 *   legal documents for successful verification." CTA "Bind Account Now"
 *   → POST /bank → toast "Account bound"/"«bankName» linked successfully."
 *   → navigate(bindAccountSuccess, {bankName, accountTitle, accountNumber,
 *   returnTo?}). Error toast "Failed to bind account".
 * - Guest gate "bind your bank account".
 */
@Composable
fun BindAccountScreen(env: NavEnv) {
    val returnTo = env.params["returnTo"] as? String
    val gate = rememberGuestGate()
    val user by env.session.user.collectAsState()

    var accounts by remember { mutableStateOf<List<BankAccount>?>(null) }
    var bankName by remember { mutableStateOf("") }
    var accountTitle by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var bankError by remember { mutableStateOf<String?>(null) }
    var titleError by remember { mutableStateOf<String?>(null) }
    var numberError by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    // Bank-select sheet state (draft selection is committed on Confirm)
    var sheetOpen by remember { mutableStateOf(false) }
    var draftBank by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        when (val res = safeCall<BankAccountsResponse> { env.api.bankAccounts() }) {
            is ApiResult.Success -> accounts = res.data.accounts
            else -> accounts = emptyList() // non-fatal — the form still works
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent),
    ) {
        AppBar(mode = AppBarMode.Page, title = "Bind Account")

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            // ------------------------------------------------ Saved Accounts
            val loaded = accounts
            when {
                loaded == null -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        AreenaxSpinner(size = 24)
                    }
                }
                loaded.isNotEmpty() -> {
                    Column(Modifier.padding(bottom = 24.dp)) {
                        Text(
                            text = "Saved Accounts",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            loaded.forEach { acc ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    ),
                                    shadowElevation = 1.dp,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Box(
                                            Modifier
                                                .size(40.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceContainer,
                                                    RoundedCornerShape(12.dp),
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            AreenaxIcon(
                                                name = "account_balance",
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Text(
                                                    text = acc.bankName,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (acc.isPrimary) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                                    ) {
                                                        Text(
                                                            text = "Primary",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.secondary,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = acc.accountTitle,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "•••• ${acc.accountNumber.takeLast(4)}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {}
            }

            // ------------------------------------------------ form card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    // Bank Name — opens the BankSelectSheet
                    Column {
                        Text(
                            text = "Bank Name",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        val interaction = remember { MutableInteractionSource() }
                        Box {
                            Surface(
                                onClick = {
                                    draftBank = bankName.takeIf { it.isNotEmpty() }
                                    sheetOpen = true
                                },
                                interactionSource = interaction,
                                shape = AreenaxShapes.Pill,
                                color = areenaColors().surfaceContainerLavender,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .pressScale(interaction, 0.98f),
                            ) {
                                Row(
                                    Modifier.padding(start = 48.dp, end = 40.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = bankName.ifBlank { "Select your bank" },
                                        style = Type.bodyLg,
                                        color = if (bankName.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            AreenaxIcon(
                                name = "account_balance",
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 16.dp)
                                    .size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            AreenaxIcon(
                                name = "expand_more",
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 12.dp)
                                    .size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (bankError != null) {
                            Text(
                                text = bankError!!,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }

                    // Account Holder Name
                    Column {
                        Text(
                            text = "Account Holder Name",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        IconPillInput(
                            icon = "person",
                            value = accountTitle,
                            onValueChange = {
                                accountTitle = it
                                titleError = null
                            },
                            placeholder = "Enter account holder name",
                        )
                        if (titleError != null) {
                            Text(
                                text = titleError!!,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }

                    // Account Number / IBAN
                    Column {
                        Text(
                            text = "Account Number / IBAN",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        IconPillInput(
                            icon = "credit_card",
                            value = accountNumber,
                            onValueChange = {
                                accountNumber = it
                                numberError = null
                            },
                            placeholder = "Enter account or IBAN number",
                        )
                        if (numberError != null) {
                            Text(
                                text = numberError!!,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Footnote
            Row(
                Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AreenaxIcon(
                    name = "info",
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Ensure your account details match your official legal documents for successful verification.",
                    style = Type.bodyLg,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ------------------------------------------------ footer CTA
        val ctaInteraction = remember { MutableInteractionSource() }
        Surface(
            onClick = {
                if (submitting) return@Surface
                if (!gate.requireAccount(user?.isGuest == true, "bind your bank account")) return@Surface
                bankError = if (bankName.isEmpty()) "Select your bank" else null
                titleError = if (accountTitle.isBlank()) "Account holder name is required" else null
                numberError = when {
                    accountNumber.isBlank() -> "Account number is required"
                    accountNumber.trim().length < 8 -> "Enter a valid account number"
                    else -> null
                }
                if (bankError != null || titleError != null || numberError != null) return@Surface
                submitting = true
                CoroutineScope(Dispatchers.Main).launch {
                    when (val res = safeCall<BankAccountResponse> {
                        env.api.createBankAccount(
                            BankCreateRequest(
                                bankName = bankName,
                                accountTitle = accountTitle.trim(),
                                accountNumber = accountNumber.trim(),
                            ),
                        )
                    }) {
                        is ApiResult.Success -> {
                            val account = res.data.account
                            env.toast.show(
                                title = "Account bound",
                                description = "${account.bankName} linked successfully.",
                            )
                            val params = mutableMapOf<String, Any?>(
                                "bankName" to account.bankName,
                                "accountTitle" to account.accountTitle,
                                "accountNumber" to account.accountNumber,
                            )
                            if (returnTo != null) params["returnTo"] = returnTo
                            env.navigate(ScreenKeys.BIND_ACCOUNT_SUCCESS, params)
                        }
                        is ApiResult.Error -> env.toast.show(
                            title = "Failed to bind account",
                            description = res.message,
                            variant = ToastVariant.Destructive,
                        )
                        is ApiResult.NetworkError -> env.toast.show(
                            title = "Failed to bind account",
                            description = res.message,
                            variant = ToastVariant.Destructive,
                        )
                    }
                    submitting = false
                }
            },
            interactionSource = ctaInteraction,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            enabled = !submitting,
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .height(56.dp)
                .pressScale(ctaInteraction, 0.98f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        trackColor = Color.Transparent,
                    )
                }
                Text(text = "Bind Account Now", style = Type.labelLg, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }

    GuestGateDialog(gate, env)

    // Bank picker — same bottom sheet as Deposit / Withdraw
    BankSelectSheet(
        open = sheetOpen,
        onClose = { sheetOpen = false },
        title = "Select Bank",
        searchPlaceholder = "Search Your Bank",
        banks = BANK_OPTIONS,
        showBanks = true,
        showAccounts = false,
        selectedId = draftBank?.let { "bank:$it" },
        onSelect = { _, meta, _ -> draftBank = meta },
        onConfirm = {
            if (draftBank != null) {
                bankName = draftBank!!
                bankError = null
            }
            sheetOpen = false
        },
        confirmLabel = "Confirm",
    )
}

/** FUNDING_BANKS — Q13: "Alphla" corrected to "Alfalah"; admin Deposit-Accounts
 *  settings must use the same key ("Alfalah Bank") to match. */
private val BANK_OPTIONS = listOf("Jazzcash", "Easypaisa", "Sadapay", "Alfalah Bank", "Mezan Bank")

/** Pill input with a leading Material icon — shared [AreenaxPillField] (owner field concept). */
@Composable
private fun IconPillInput(
    icon: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    AreenaxPillField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        leadingIcon = icon,
        iconSize = 24.dp,
        textStyle = Type.bodyLg,
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Default, // pre-migration KeyboardOptions(Text)
    )
}
