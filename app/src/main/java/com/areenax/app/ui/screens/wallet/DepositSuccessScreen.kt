package com.areenax.app.ui.screens.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.theme.Type
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.StatusChip
import com.areenax.app.core.ui.SuccessAnim
import com.areenax.app.core.ui.cardShadow
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.primaryGlow
import com.areenax.app.core.util.formatMoney

/*
 * DepositSuccessScreen — 1:1 port of src/components/screens/wallet/
 * DepositSuccessScreen.tsx (SPEC/01 C4, key `depositSuccess` — the success
 * screens have no extracted HTML; design follows React + core SuccessAnim).
 *
 * Params {amount, method, reference}. AppBar back AND "Back to Wallet" →
 * replace(wallet) (no re-trigger); "Go to Home" → replace(home). SuccessAnim
 * "Deposit Request Submitted!" + pending subtitle + PENDING StatusChip,
 * summary rows Amount / Method / Reference / Current Balance (live user).
 */

@Composable
fun DepositSuccessScreen(env: NavEnv) {
    val colors = MaterialTheme.colorScheme
    val user by env.session.user.collectAsState()

    val amount = (env.params["amount"] as? Number)?.toDouble() ?: 0.0
    val method = env.params["method"] as? String ?: "JazzCash"
    val reference = env.params["reference"] as? String ?: "-"

    val rows = listOf(
        SuccessRow("payments", "Amount", "Rs ${formatMoney(amount)}"),
        SuccessRow("credit_card", "Method", method),
        SuccessRow("tag", "Reference", reference),
        SuccessRow("account_balance_wallet", "Current Balance", "Rs ${formatMoney(user?.balance)}"),
    )

    Column(
        Modifier
            .fillMaxSize()
            .walletRadialBackground(
                topColor = colors.surfaceContainer,
                baseColor = colors.background,
            )
            .navigationBarsPadding(),
    ) {
        // Back returns to the wallet without re-triggering the deposit.
        AppBar(title = "Deposit Request", onBack = { env.replace(ScreenKeys.WALLET) })

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 24.dp), // pt-6
        ) {
            SuccessAnim(
                title = "Deposit Request Submitted!",
                subtitle = "Your deposit is now pending admin approval. Your balance will be updated once it is approved.",
            ) {
                // StatusChip status="PENDING" className="!px-3 !py-1 !text-body-md mt-1"
                StatusChip(
                    status = "PENDING",
                    enlarged = true,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // ---- Summary Card ----
            SuccessSummaryCard(rows = rows, modifier = Modifier.padding(top = 32.dp)) // mt-8
        }

        // ---- Actions (mt-auto pt-8 gap-3) ----
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 32.dp), // px-4 pb-8 + pt-8
            verticalArrangement = Arrangement.spacedBy(12.dp), // gap-3
        ) {
            val ctaInteraction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp) // h-14
                    .primaryGlow(RoundedCornerShape(50))
                    .pressScale(ctaInteraction, pressedScale = 0.95f)
                    .clip(RoundedCornerShape(50))
                    .background(colors.primary)
                    .clickable(interactionSource = ctaInteraction, indication = null) {
                        env.replace(ScreenKeys.WALLET)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Back to Wallet",
                    style = Type.bodyLg.copy(fontWeight = FontWeight.Bold),
                    color = colors.onPrimary,
                )
            }
            Text(
                text = "Go to Home",
                style = Type.bodyLg.copy(fontWeight = FontWeight.SemiBold),
                color = colors.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .clickable { env.replace(ScreenKeys.HOME) }
                    .padding(vertical = 12.dp), // py-3
            )
        }
    }
}

/** Shared summary card for the three wallet success screens (React markup). */
@Composable
internal fun SuccessSummaryCard(rows: List<SuccessRow>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp), // rounded-[1.5rem]
        color = colors.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.2f)),
        modifier = modifier
            .fillMaxWidth()
            .cardShadow(RoundedCornerShape(24.dp)),
    ) {
        Column(Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) { // py-2 + mx-4 rows
            rows.forEachIndexed { index, row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp), // px-5 py-4
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AreenaxIcon(
                            name = row.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp), // text-[1.25rem]
                            tint = colors.onSurfaceVariant,
                        )
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

internal data class SuccessRow(val icon: String, val label: String, val value: String)
