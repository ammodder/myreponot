package com.areenax.app.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.R
import com.areenax.app.core.nav.LocalNavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.util.formatMoney

/*
 * AppBar + BellButton + BalanceChip + AppBarIconButton — port of
 * src/components/shared/AppBar.tsx (SPEC/03 §1).
 * Header height 64dp, px-16, status-bar padded, transparent bg.
 */

/**
 * @param mode    "page" = back circle + centered title + right slot;
 *                "tab"  = left slot + right cluster (root tabs).
 * @param title   Page title (both modes; tab mode can also take [left]).
 * @param balance When true renders the BalanceChip before [right].
 * @param right   Right slot; `null` renders an empty 40dp spacer (legal pages pre-auth).
 * @param onBack  Page-mode back action; defaults to the navigator stack goBack.
 */
@Composable
fun AppBar(
    modifier: Modifier = Modifier,
    mode: AppBarMode = AppBarMode.Page,
    title: String? = null,
    balance: Boolean = false,
    right: (@Composable RowScope.() -> Unit)? = null,
    left: (@Composable RowScope.() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    val env = LocalNavEnv.current
    Surface(modifier = modifier.fillMaxWidth(), color = Color.Transparent) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (mode) {
                AppBarMode.Page -> {
                    AppBarBackButton(onClick = onBack ?: { env.goBack() })
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = title ?: "",
                        style = Type.headlineLgMobile,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (balance) BalanceChip()
                    if (right != null) right() else Spacer(Modifier.size(40.dp))
                }
                AppBarMode.Tab -> {
                    if (left != null) {
                        left()
                    } else if (title != null) {
                        // A9-05: page titles are headings for TalkBack navigation
                        Text(
                            text = title,
                            style = Type.headlineLgMobile,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (balance) BalanceChip()
                    right?.invoke(this)
                }
            }
        }
    }
}

enum class AppBarMode { Page, Tab }

/** 40dp white circle + arrow_back (page-mode back). */
@Composable
fun AppBarBackButton(onClick: () -> Unit, iconRes: Int = R.drawable.ic_arrow_back) {
    AppBarCircleButton(onClick = onClick) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = "Back",
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 40dp white circle hosting any symbol (AppBarIconButton).
 *  A9-04: the clickable Surface is wrapped in a 48dp minimum touch target —
 *  the visual circle stays 40dp, the tappable area meets Material guidance. */
@Composable
fun AppBarCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(48.dp), // A9-04: 48dp touch target (was the 40dp circle itself)
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Surface(
            onClick = onClick,
            modifier = Modifier
                .size(40.dp)
                .cardShadow(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
            ),
        ) {
            Box(contentAlignment = Alignment.Center) { content() }
        }
    }
}

/** BellButton — self-refreshing unread badge → notifications screen. */
@Composable
fun BellButton(size: BellSize = BellSize.Md) {
    val env = LocalNavEnv.current
    val unread by env.unread.unread.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        env.unread.refresh() // 30s-throttled countOnly poll (SPEC/00 §2e)
    }
    val bellSize = if (size == BellSize.Md) 40.dp else 36.dp
    Box(contentAlignment = Alignment.Center) {
        AppBarCircleButton(onClick = { env.navigate(ScreenKeys.NOTIFICATIONS) }, modifier = Modifier.size(bellSize)) {
            Icon(
                painter = painterResource(R.drawable.ic_notifications),
                contentDescription = "Notifications",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (unread > 0) {
            // Red badge: min 18dp pill, 10sp bold, "9+" cap, 2dp white ring,
            // -top/-right. A4: widthIn(min) + horizontal padding — 2-digit
            // counts expand the pill instead of clipping ("10" fits, "9+" cap).
            Surface(
                onClick = { env.navigate(ScreenKeys.NOTIFICATIONS) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-3).dp)
                    .height(18.dp)
                    .widthIn(min = 18.dp)
                    .padding(horizontal = 3.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (unread > 9) "9+" else unread.toString(),
                        color = MaterialTheme.colorScheme.onError,
                        style = Type.labelSm.copy(fontSize = 10.sp), // A4: labelSm keeps 600 weight + tracking
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

enum class BellSize { Md, Sm }

/** BalanceChip — pill with "Rs «money»" → wallet. */
@Composable
fun BalanceChip(balance: Double? = null) {
    val env = LocalNavEnv.current
    val user by env.session.user.collectAsState()
    val value = balance ?: user?.balance ?: 0.0
    val extended = areenaColors()
    Surface(
        onClick = { env.navigate(ScreenKeys.WALLET) },
        shape = CircleShape,
        color = extended.balanceChip,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
        shadowElevation = 2.dp,
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Text(
            text = "Rs ${formatMoney(value)}",
            style = Type.labelLg.copy(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
