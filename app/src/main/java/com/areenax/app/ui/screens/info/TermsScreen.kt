package com.areenax.app.ui.screens.info

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.theme.Type
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.BellButton
import com.areenax.app.core.ui.cardShadow

/**
 * TermsScreen — key `terms` (SPEC/01 G2; web TermsScreen.tsx, pixel truth
 * terms.html). PUBLIC pre-auth legal page: "Legal Document / User Agreement"
 * header, floating white card with the decorative blur circle, "Last updated:
 * March 2026" and ALL 10 sections VERBATIM (section 7 = bulleted list).
 * AppBar bell renders ONLY when signed in (web right={user ? <BellButton/> :
 * null} — avoids a 401 bounce for visitors).
 */

// ---- VERBATIM web copy (TermsScreen.tsx) -------------------------------------
private val TERMS_SECTIONS: List<Triple<String, String?, List<String>>> = listOf(
    Triple(
        "1. Acceptance of Terms",
        "By accessing or using our platform, you agree to be bound by these Terms and Conditions. If you do not agree to all the terms and conditions, then you may not access the platform or use any services.",
        emptyList(),
    ),
    Triple(
        "2. Eligibility",
        "You must be at least 18 years of age to create an account and participate in any tournaments or services offered on this platform. By using the platform, you represent and warrant that you meet this age requirement.",
        emptyList(),
    ),
    Triple(
        "3. Account Rules",
        "You are responsible for maintaining the confidentiality of your account credentials and for all activities that occur under your account. You agree to notify us immediately of any unauthorized use of your account.",
        emptyList(),
    ),
    Triple(
        "4. Tournament Participation",
        "Participation in tournaments is subject to specific rules outlined for each event. We reserve the right to disqualify any participant found violating these rules, engaging in unfair practices, or using unauthorized third-party software.",
        emptyList(),
    ),
    Triple(
        "5. Wallet & Payments",
        "All transactions within the platform, including deposits and withdrawals, are processed securely. We are not responsible for delays caused by third-party payment processors. Users are responsible for any applicable taxes on their winnings.",
        emptyList(),
    ),
    Triple(
        "6. Referral Program",
        "Our referral program allows users to earn rewards by inviting others to join. Rewards are subject to change, and we reserve the right to suspend or terminate the referral program or any user's participation at any time for any reason.",
        emptyList(),
    ),
    Triple("7. Prohibited Conduct", null, listOf(
        "Engaging in any fraudulent or illegal activity.",
        "Attempting to interfere with the proper functioning of the platform.",
        "Harassing, threatening, or intimidating other users.",
        "Using automated scripts or bots to interact with the platform.",
    )),
    Triple(
        "8. Termination",
        "We may terminate or suspend your access immediately, without prior notice or liability, for any reason whatsoever, including without limitation if you breach the Terms.",
        emptyList(),
    ),
    Triple(
        "9. Limitation of Liability",
        "In no event shall the platform, nor its directors, employees, partners, agents, suppliers, or affiliates, be liable for any indirect, incidental, special, consequential or punitive damages, including without limitation, loss of profits, data, use, goodwill, or other intangible losses.",
        emptyList(),
    ),
    Triple(
        "10. Changes to Terms",
        "We reserve the right, at our sole discretion, to modify or replace these Terms at any time. We will provide reasonable notice of any material changes.",
        emptyList(),
    ),
)

@Composable
fun TermsScreen(env: NavEnv) {
    val user by env.session.user.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            // web inline bg: primary-container 5% top-right + primary 3% bottom-left radials
            .drawBehind {
                drawCircle(
                    color = Color(0x0D2563EB),
                    radius = size.width * 1.1f,
                    center = Offset(size.width, 0f),
                )
                drawCircle(
                    color = Color(0x08004AC6),
                    radius = size.width * 0.95f,
                    center = Offset(0f, size.height),
                )
            }
            .verticalScroll(rememberScrollState()),
    ) {
        AppBar(
            title = "Terms & Conditions",
            right = if (user != null) ({ BellButton() }) else null, // explicit null → no bell pre-auth
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 24.dp, bottom = 32.dp),
        ) {
            // ---- Document header context ------------------------------------
            Column(
                Modifier
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = "LEGAL DOCUMENT",
                    style = Type.labelSm,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    text = "User Agreement",
                    style = Type.headlineLgMobile,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = "Please read these terms carefully before using our services.",
                    style = Type.bodyMd,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- The Floating White Card --------------------------------------
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(24.dp),
                    )
                    .cardShadow(RoundedCornerShape(24.dp)),
            ) {
                // Subtle internal decorative element (top-right bg-primary/5 blur-2xl)
                Box(
                    Modifier
                        .align(androidx.compose.ui.Alignment.TopEnd)
                        .offset(x = 64.dp, y = (-64).dp)
                        .size(128.dp)
                        .blur(24.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                )

                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = "Last updated: March 2026",
                        style = Type.labelSm,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 24.dp),
                    )
                    TERMS_SECTIONS.forEach { (title, body, bullets) ->
                        Column(Modifier.padding(bottom = 24.dp)) {
                            Text(
                                text = title,
                                style = Type.headlineMd,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            if (body != null) {
                                Text(
                                    text = body,
                                    style = Type.bodyMd.copy(lineHeight = 22.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            bullets.forEachIndexed { index, item ->
                                Row(Modifier.padding(bottom = if (index < bullets.size - 1) 4.dp else 0.dp)) {
                                    Text(
                                        text = "•  ",
                                        style = Type.bodyMd.copy(lineHeight = 22.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = item,
                                        style = Type.bodyMd.copy(lineHeight = 22.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
