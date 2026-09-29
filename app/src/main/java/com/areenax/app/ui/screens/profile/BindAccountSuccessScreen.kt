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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.R
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.AppBarMode
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.util.formatShortDate
import java.time.Instant

/**
 * BindAccountSuccessScreen — key `bindAccountSuccess` (SPEC/01 E7). Exact
 * port of `src/components/screens/profile/BindAccountSuccessScreen.tsx`:
 *
 * - Params: {bankName, accountTitle, accountNumber, returnTo?}. doneTarget =
 *   returnTo==="withdraw" ? withdraw : wallet. Back + primary CTA →
 *   replace(doneTarget); "Back to Home" → replace(home).
 * - Success hero: 80dp tinted circle + 48dp primary circle with a white
 *   FILLED check, "Account Linked!" (26px bold), subtitle "Your «bankName»
 *   account is now active for instant withdrawals."
 * - Summary card: bank icon tile + bankName + "Verified" chip (bg-secondary/15)
 *   + "Primary Withdrawal Method"; rows: Account Holder, Account Number
 *   ("•••• «last4»"), Linked Date (today, "Oct 25, 2025"), Instant Payouts
 *   ("Enabled" + green dot).
 * - Buttons: primary "Continue to Withdraw"/"Go to Wallet" (arrow_forward),
 *   secondary "Back to Home".
 *
 * NOTE: the web renders its own hero (not the shared SuccessAnim component).
 */
@Composable
fun BindAccountSuccessScreen(env: NavEnv) {
    val params = env.params
    val user by env.session.user.collectAsState()

    val bankName = (params["bankName"] as? String)?.takeIf { it.isNotEmpty() } ?: "Bank"
    val accountTitle = (params["accountTitle"] as? String)?.takeIf { it.isNotEmpty() }
        ?: user?.fullName?.takeIf { it.isNotEmpty() } ?: "—"
    val accountNumber = (params["accountNumber"] as? String) ?: ""
    // Coming from the Withdraw flow → continue straight back to the withdrawal
    val returnToWithdraw = params["returnTo"] == "withdraw"
    val doneTarget = if (returnToWithdraw) ScreenKeys.WITHDRAW else ScreenKeys.WALLET
    val last4 = if (accountNumber.isNotEmpty()) accountNumber.takeLast(4) else "----"
    val linkedDate = formatShortDate(Instant.now().toString())

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState()),
    ) {
        // Top App Bar — back leads to the same target as the primary CTA
        AppBar(mode = AppBarMode.Page, title = "Bank Account", onBack = { env.replace(doneTarget) })

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // ------------------------------------------------ success hero
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val ext = areenaColors()
                Box(
                    Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            ext.primaryFixed.copy(alpha = 0.6f) // A8-06,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Icon(
                            painter = painterResource(R.drawable.ic_check_fill),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Account Linked!",
                    style = Type.successTitle, // 26px bold tracking-tight
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Your $bankName account is now active for instant withdrawals.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            Spacer(Modifier.height(24.dp))

            // ------------------------------------------------ summary card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Bank profile header row
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow),
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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = bankName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        text = "Verified",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(
                                text = "Primary Withdrawal Method",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }

                    // Key-value details list (divide-y)
                    DetailRow("Account Holder", accountTitle)
                    DetailRow("Account Number", "•••• $last4")
                    DetailRow("Linked Date", linkedDate)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Instant Payouts",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary),
                            )
                            Text(
                                text = "Enabled",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }
        }

        // ------------------------------------------------ bottom actions
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val primaryInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = { env.replace(doneTarget) },
                interactionSource = primaryInteraction,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .pressScale(primaryInteraction, 0.95f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (returnToWithdraw) "Continue to Withdraw" else "Go to Wallet",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    AreenaxIcon(
                        name = "arrow_forward",
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            val homeInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = { env.replace(ScreenKeys.HOME) },
                interactionSource = homeInteraction,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .pressScale(homeInteraction, 0.95f),
            ) {
                Text(
                    text = "Back to Home",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, showDivider: Boolean = true) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            )
        }
    }
}
