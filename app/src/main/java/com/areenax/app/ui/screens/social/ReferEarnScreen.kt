package com.areenax.app.ui.screens.social

import android.content.Intent
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.theme.LightBackground
import com.areenax.app.core.theme.referGlowStart
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AreenaxSpinner
import com.areenax.app.core.ui.SupportFab
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.cardShadow
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.primaryGlow
import com.areenax.app.core.util.formatMoney
import com.areenax.app.core.util.timeAgo
import com.areenax.app.data.ReferralsResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * ReferEarnScreen — key `referEarn` (build record 3-d):
 * "My Referrals" centered header over a primary-tinted radial top; primary
 * hero card (big referral code, Copy button with "Copied!" 2s state, bonus
 * line), stats grid (Friends Invited / Total Earned), "How It Works" 3 steps,
 * "Copy Referral Code" primary button, Share button (Android share sheet —
 * native navigator.share, fallback clipboard + toast), Referred Users card
 * (count chip; rows = avatar letter, gameName, "Joined «timeAgo»",
 * "+Rs bonusEarned"; exact empty text). SupportFab offset.
 */

@Composable
fun ReferEarnScreen(env: NavEnv) {
    val extended = areenaColors()
    var data by remember { mutableStateOf<ReferralsResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        when (val res = safeCall { env.api.referrals() }) {
            is ApiResult.Success -> data = res.data
            else -> env.toast("Failed to load referrals", ToastVariant.Destructive)
        }
        loading = false
    }

    val code = data?.referralCode.orEmpty()

    fun copyCode() {
        if (code.isBlank()) return
        val ok = try {
            clipboard?.setText(AnnotatedString(code))
            true
        } catch (_: Exception) {
            false
        }
        if (ok) {
            copied = true
            scope.launch {
                delay(2000)
                copied = false
            }
        } else {
            env.toast.show("Could not copy code", description = code, variant = ToastVariant.Destructive)
        }
    }

    fun shareCode() {
        val shareText = "Join me on AREENAX! Use my referral code $code to sign up and earn a bonus."
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            env.context.startActivity(Intent.createChooser(intent, "Share"))
        } catch (_: Exception) {
            // navigator.share fallback: clipboard + toast
            try {
                clipboard?.setText(AnnotatedString(code))
                env.toast("Referral code copied!")
            } catch (_: Exception) {
                env.toast.show(
                    "Could not share code",
                    description = code,
                    variant = ToastVariant.Destructive,
                )
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                if (!extended.isDark) {
                    // radial-gradient(circle at 50% -20%, rgba(219,225,255,0.6) 0%, background 70%)
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(referGlowStart, LightBackground),
                            center = Offset(size.width / 2f, -size.height * 0.2f),
                            radius = size.width * 1.1f,
                        ),
                    )
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            // Header — back circle + centered "My Referrals" + 40dp balance spacer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderBackCircle(onClick = { env.goBack() })
                Text(
                    text = "My Referrals",
                    style = Type.headlineMd,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(40.dp))
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (loading) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 96.dp),
                        contentAlignment = Alignment.Center,
                    ) { AreenaxSpinner() }
                } else {
                    // ===== Primary hero card — big code + Copy + bonus line =====
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .primaryGlow(RoundedCornerShape(24.dp)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = if (code.isBlank()) "—" else code,
                                fontSize = 28.sp,
                                lineHeight = 34.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "Earn Rs ${formatMoney(data?.bonus)} for every friend who joins!",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                            )
                            Surface(
                                onClick = { copyCode() },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.onPrimary,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AreenaxIcon(
                                        name = if (copied) "check" else "content_copy",
                                        contentDescription = null,
                                        filled = copied,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (copied) "Copied!" else "Copy Code",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }

                    // ===== Stats grid — Friends Invited / Total Earned =====
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatCard(
                            label = "Friends Invited",
                            value = (data?.count ?: 0).toString(),
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            label = "Total Earned",
                            value = "Rs ${formatMoney(data?.totalEarned)}",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // ===== How It Works — 3 steps =====
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .cardShadow(RoundedCornerShape(16.dp)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                text = "How It Works",
                                style = Type.headlineMd.copy(fontSize = 16.sp, lineHeight = 22.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            HowItWorksStep(
                                number = 1,
                                text = "Share your referral code with friends.",
                            )
                            HowItWorksStep(
                                number = 2,
                                text = "They sign up and enter your code.",
                            )
                            HowItWorksStep(
                                number = 3,
                                text = "You earn Rs ${formatMoney(data?.bonus)} for every friend who joins.",
                            )
                        }
                    }

                    // ===== Copy Referral Code — primary button w/ "Copied!" 2s state =====
                    val copyInteraction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = { copyCode() },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                        interactionSource = copyInteraction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .primaryGlow(CircleShape)
                            .pressScale(copyInteraction, 0.95f),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AreenaxIcon(
                                name = if (copied) "check" else "content_copy",
                                contentDescription = null,
                                filled = copied,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (copied) "Copied!" else "Copy Referral Code",
                                style = Type.bodyLg,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }

                    // ===== Share — Android share sheet (navigator.share native equivalent) =====
                    val shareInteraction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = { shareCode() },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                        ),
                        interactionSource = shareInteraction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .pressScale(shareInteraction, 0.95f),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AreenaxIcon(
                                name = "share",
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Share",
                                style = Type.bodyLg,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    // ===== Referred Users card =====
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .cardShadow(RoundedCornerShape(16.dp)),
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Referred Users",
                                    style = Type.headlineMd.copy(fontSize = 18.sp, lineHeight = 24.sp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.weight(1f))
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                ) {
                                    Text(
                                        text = "${data?.count ?: 0} users",
                                        style = Type.labelSm,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    )
                                }
                            }
                            val referrals = data?.referrals.orEmpty()
                            if (referrals.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp)
                                        .height(220.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = "No referrals yet. Share your code to invite friends!",
                                        style = Type.bodyLg,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.widthIn(max = 240.dp),
                                    )
                                }
                            } else {
                                referrals.forEachIndexed { i, r ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = (r.gameName.ifBlank { "?" })
                                                    .firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = r.gameName,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "Joined ${timeAgo(r.createdAt)}",
                                                style = Type.labelSm.copy(fontWeight = FontWeight.Normal),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Text(
                                            text = "+Rs ${formatMoney(r.bonusEarned)}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                    }
                                    if (i < referrals.lastIndex) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(start = 16.dp, end = 16.dp)
                                                .height(1.dp)
                                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SupportFab — offset above the (absent) bottom nav on this sub-page
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 96.dp),
        ) {
            SupportFab(offset = true)
        }
    }
}

@Composable
private fun HeaderBackCircle(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.cardShadow(),
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            AreenaxIcon(
                name = "arrow_back",
                contentDescription = "Back",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        modifier = modifier.cardShadow(RoundedCornerShape(16.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label.uppercase(),
                style = Type.labelSm,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = Type.headlineLgMobile,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun HowItWorksStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
