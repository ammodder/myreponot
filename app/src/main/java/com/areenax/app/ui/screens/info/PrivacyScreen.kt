package com.areenax.app.ui.screens.info

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.theme.Type
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.BellButton

/**
 * PrivacyScreen — key `privacy` (SPEC/01 G3; web PrivacyScreen.tsx, pixel truth
 * privacy.html). PUBLIC pre-auth legal page on a #f3f4f6-ish token background:
 * sticky white-circle header (native AppBar), white rounded-3xl card and ALL
 * 8 sections VERBATIM — section 8 with the bold "support@gamingapp.com"
 * contact line. AppBar bell renders ONLY when signed in (web right=
 * {user ? <BellButton/> : null}). Historical Argon2id/JWT copy kept verbatim.
 */

// ---- VERBATIM web copy (PrivacyScreen.tsx) -----------------------------------
private data class PrivacySection(val title: String, val body: String)

private val PRIVACY_SECTIONS: List<PrivacySection> = listOf(
    PrivacySection(
        "1. Information We Collect",
        "We collect information you provide directly to us, including name, email address, phone number, and payment details when you register, make transactions, or interact with our services.",
    ),
    PrivacySection(
        "2. How We Use Your Information",
        "We use the information we collect to: provide, maintain, and improve our services; process transactions and send transaction notifications; send technical notices and support messages; and communicate with you about tournaments, results, and prizes.",
    ),
    PrivacySection(
        "3. Data Security",
        "We implement industry-standard security measures including Argon2id password hashing, JWT authentication, encrypted data transmission (HTTPS), and regular security audits to protect your personal information.",
    ),
    PrivacySection(
        "4. Payment Information",
        "Payment information is processed securely. We do not store complete payment details. Transaction records are maintained for accounting and dispute resolution purposes only.",
    ),
    PrivacySection(
        "5. Location & Contacts (friend suggestions)",
        "With your explicit action, we match a phone number from a contact you pick to find friends on AREENAX — we do not upload or store your address book. When you open Players Nearby, we collect your approximate location (a coarse area of roughly 1-2 km) at that moment only, to suggest players near you; it is stored with a timestamp and used solely for suggestions. Neither is used in the background or shared with other users.",
    ),
    PrivacySection(
        "5b. Receipts & Financial Data",
        "Deposits require a photo of your payment receipt and a transaction reference. Receipt images are used only to verify your payment, are visible to our limited verification staff, and are never shown to other players. Withdrawals require bank-account details you bind, which are stored to process your payouts.",
    ),
    PrivacySection(
        "5c. Account Deletion",
        "You can permanently delete your account from Profile → Delete Account in the app, or from the web at /delete-account while logged in. Deletion removes your profile, balance history, tournament entries, teams, friendships and messages and cannot be undone.",
    ),
    PrivacySection(
        "6. Information Sharing",
        "We do not sell, trade, or rent your personal information to third parties. We may share aggregate, anonymized data for analytical purposes.",
    ),
    PrivacySection(
        "7. Cookies",
        "We use local storage and session tokens to keep you logged in and improve your experience. You can clear these at any time through your browser settings.",
    ),
    PrivacySection(
        "8. Your Rights",
        "You have the right to access, correct, or delete your personal information. Contact our support team to exercise these rights.",
    ),
    PrivacySection(
        "9. Contact Us",
        "If you have questions about this Privacy Policy, please contact us at:\nsupport@gamingapp.com",
    ),
)

@Composable
fun PrivacyScreen(env: NavEnv) {
    val user by env.session.user.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState()),
    ) {
        AppBar(
            title = "Privacy Policy",
            right = if (user != null) ({ BellButton() }) else null, // no bell pre-auth
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 32.dp),
        ) {
            // ---- Privacy Policy Card (rounded-3xl p-6) --------------------------
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(24.dp),
            ) {
                Text(
                    text = "Privacy Policy",
                    style = Type.headlineLg, // text-2xl font-bold (24sp)
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
                PRIVACY_SECTIONS.forEachIndexed { index, section ->
                    Column(Modifier.padding(bottom = if (index < PRIVACY_SECTIONS.size - 1) 24.dp else 0.dp)) {
                        Text(
                            text = section.title,
                            style = Type.bodyLg.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 20.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        if (index == PRIVACY_SECTIONS.size - 1) {
                            // Section 8 — body + newline + bold contact line
                            Text(
                                text = buildAnnotatedString {
                                    append("If you have questions about this Privacy Policy, please contact us at:\n")
                                    withStyle(
                                        SpanStyle(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        ),
                                    ) {
                                        append("support@gamingapp.com")
                                    }
                                },
                                style = Type.bodyLg.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 24.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = section.body,
                                style = Type.bodyLg.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 24.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
