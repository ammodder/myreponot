package com.areenax.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.areenax.app.R
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.theme.Type
import com.areenax.app.core.util.rsBalance
import com.areenax.app.core.util.rsMoney

/*
 * InsufficientBalanceDialog — port of src/components/shared/InsufficientBalanceDialog.tsx
 * (SPEC/03 §9). Same modal language as the guest dialog; 64dp bg-error-container
 * circle with account_balance_wallet; title "Insufficient Balance"; lines:
 *   "You need Rs «entryFee» to join «tournamentName|this tournament»."
 *   "Your balance: Rs «balance»" (bold)
 *   shortfall>0 → "Deposit at least Rs «shortfall» to continue." (shortfall text-primary)
 * Primary "Deposit Now" → close + nav(deposit); text "Not Now" → dismiss.
 *
 * NOTE: the web JoinTeamSheet references an older prop shape — dead code; the
 * live contract below matches TournamentDetailsScreen's usage.
 */

@Composable
fun InsufficientBalanceDialog(
    open: Boolean,
    entryFee: Double,
    balance: Double,
    onOpenChange: (Boolean) -> Unit,
    tournamentName: String? = null,
    env: NavEnv? = null,
) {
    val shortfall = (entryFee - balance).coerceAtLeast(0.0)
    CenteredModalScaffold(open = open, onDismiss = { onOpenChange(false) }) {
        Box(
            Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_account_balance_wallet),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        Text(
            "Insufficient Balance",
            style = Type.headlineMd,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "You need ${rsMoney(entryFee)} to join ${tournamentName ?: "this tournament"}.",
            style = Type.bodyMd,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            "Your balance: ${rsBalance(balance)}",
            style = Type.bodyMd,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (shortfall > 0) {
            Text(
                "Deposit at least ${rsMoney(shortfall)} to continue.",
                style = Type.bodyMd,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = {
                onOpenChange(false)
                env?.navigate(ScreenKeys.DEPOSIT)
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text("Deposit Now", style = Type.labelLg)
        }
        TextButton(onClick = { onOpenChange(false) }) {
            Text(
                "Not Now",
                style = Type.labelLg,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
