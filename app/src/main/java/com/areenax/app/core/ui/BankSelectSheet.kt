package com.areenax.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.areenax.app.R
import com.areenax.app.core.theme.Type
import com.areenax.app.core.util.maskAccountNumber

/*
 * BankSelectSheet — port of src/components/shared/BankSelectSheet.tsx
 * (SPEC/03 §13). Used by:
 *   DepositScreen  → "Choose Funding Bank" (banks + bound accounts)
 *   WithdrawScreen → "Choose Withdrawal Account" (accounts only + Bind Account emptyAction)
 *   BindAccountScreen → "Select Bank" (banks only)
 *
 * Selection id: "bank:<name>" | "acc:<id>". Rows = 48dp rounded-xl icon tile
 * (account_balance / credit_card, FILLED when active) + name + subtitle
 * + green check circle (24dp, active only). Sticky Confirm footer.
 * Empty-with-action → dashed card; filter-miss → EmptyState "No banks found".
 */

data class BankEmptyAction(
    val icon: String,
    val title: String,
    val description: String,
    val buttonLabel: String,
    val onClick: () -> Unit,
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BankSelectSheet(
    open: Boolean,
    onClose: () -> Unit,
    title: String,
    selectedId: String?,
    onSelect: (id: String, bankName: String, account: com.areenax.app.data.BankAccount?) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    searchPlaceholder: String = "Search bank",
    banks: List<String> = emptyList(),
    accounts: List<com.areenax.app.data.BankAccount> = emptyList(),
    accountsLabel: String = "Your Accounts",
    showBanks: Boolean = true,
    showAccounts: Boolean = true,
    confirmLabel: String = "Confirm",
    emptyAction: BankEmptyAction? = null,
) {
    if (!open) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember(open) { mutableStateOf("") }
    var selection by remember(open) { mutableStateOf(selectedId) }

    val filteredBanks = banks.filter { it.contains(query, ignoreCase = true) }
    val filteredAccounts = accounts.filter {
        it.bankName.contains(query, true) ||
            it.accountTitle.contains(query, true) ||
            it.accountNumber.contains(query, true)
    }
    val nothingAtAll = (showBanks && banks.isEmpty() && !showAccounts) ||
        (!showBanks && showAccounts && accounts.isEmpty())
    val filterMiss = showBanks && banks.isNotEmpty() && filteredBanks.isEmpty() && filteredAccounts.isEmpty() && query.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        dragHandle = {
            // w-12 h-1.5 rounded-full bg-outline-variant/50
            Box(
                Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .size(width = 48.dp, height = 6.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                title,
                style = Type.headlineMd,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(12.dp))
            // Search pill
            AreenaxPillField(
                value = query,
                onValueChange = { query = it },
                placeholder = searchPlaceholder,
                leadingIcon = "search",
                textStyle = Type.bodyMd,
                height = 48.dp,
                imeAction = ImeAction.Default, // pre-migration OutlinedTextField default
            )
            Spacer(Modifier.height(12.dp))

            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    nothingAtAll && emptyAction != null -> EmptyActionCard(emptyAction)
                    filterMiss -> EmptyState("No banks found", icon = "account_balance")
                    else -> {
                        if (showBanks && filteredBanks.isNotEmpty()) {
                            filteredBanks.forEach { bank ->
                                val id = "bank:$bank"
                                BankRow(
                                    icon = "account_balance",
                                    name = bank,
                                    subtitle = null,
                                    active = selection == id,
                                    onClick = {
                                        selection = id
                                        onSelect(id, bank, null)
                                    },
                                )
                            }
                        }
                        if (showAccounts && filteredAccounts.isNotEmpty()) {
                            if (showBanks && filteredBanks.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    accountsLabel,
                                    style = Type.labelLg,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                            filteredAccounts.forEach { account ->
                                val id = "acc:${account.id}"
                                BankRow(
                                    icon = "credit_card",
                                    name = account.bankName,
                                    subtitle = "${account.accountTitle} • ${maskAccountNumber(account.accountNumber)}",
                                    active = selection == id,
                                    onClick = {
                                        selection = id
                                        onSelect(id, account.bankName, account)
                                    },
                                )
                            }
                        }
                        if (filteredBanks.isEmpty() && filteredAccounts.isEmpty() && query.isBlank()) {
                            EmptyState("No banks found", icon = "account_balance")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Sticky Confirm footer
            Button(
                onClick = onConfirm,
                enabled = selection != null,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .height(56.dp),
            ) {
                Text(confirmLabel, style = Type.labelLg)
            }
            Spacer(
                Modifier
                    .navigationBarsPadding()
                    .height(4.dp),
            )
        }
    }
}

@Composable
private fun BankRow(
    icon: String,
    name: String,
    subtitle: String?,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AreenaxIcon(
                name = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                filled = active,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = Type.labelLg,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = Type.labelMd,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (active) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_check), null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSecondary,
                )
            }
        }
    }
}

/** Dashed empty-with-action card (Bind Account on withdraw). */
@Composable
private fun EmptyActionCard(action: BankEmptyAction) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AreenaxIcon(name = action.icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Text(action.title, style = Type.labelLg, color = MaterialTheme.colorScheme.onSurface)
            Text(
                action.description,
                style = Type.bodyMd,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Button(
                onClick = action.onClick,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(action.buttonLabel, style = Type.labelLg)
            }
        }
    }
}
