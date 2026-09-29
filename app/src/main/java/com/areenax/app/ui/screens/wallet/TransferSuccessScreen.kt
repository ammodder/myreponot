package com.areenax.app.ui.screens.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.SuccessAnim
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.primaryGlow
import com.areenax.app.core.util.formatMoney

/*
 * TransferSuccessScreen — 1:1 port of src/components/screens/wallet/
 * TransferSuccessScreen.tsx (SPEC/01 C9, key `transferSuccess`).
 *
 * Params {amount, receiver, reference}. Back + "Back to Wallet" →
 * replace(wallet); "Go to Home" → replace(home). SuccessAnim
 * "Transfer Successful!" + "You sent Rs «amount» to «receiver»";
 * rows Amount / Recipient (break-all) / Reference — no StatusChip.
 */

@Composable
fun TransferSuccessScreen(env: NavEnv) {
    val colors = MaterialTheme.colorScheme

    val amount = (env.params["amount"] as? Number)?.toDouble() ?: 0.0
    val receiver = env.params["receiver"] as? String ?: "player"
    val reference = env.params["reference"] as? String ?: "-"

    val rows = listOf(
        SuccessRow("payments", "Amount", "Rs ${formatMoney(amount)}"),
        SuccessRow("person", "Recipient", receiver),
        SuccessRow("tag", "Reference", reference),
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
        // Back returns to the wallet without re-triggering the transfer.
        AppBar(title = "Transfer Successful", onBack = { env.replace(ScreenKeys.WALLET) })

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 24.dp), // pt-6
        ) {
            SuccessAnim(
                title = "Transfer Successful!",
                subtitle = "You sent Rs ${formatMoney(amount)} to $receiver",
            )

            // ---- Summary Card ----
            SuccessSummaryCard(rows = rows, modifier = Modifier.padding(top = 32.dp)) // mt-8
        }

        // ---- Actions (mt-auto pt-8 gap-3) ----
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
