package com.areenax.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.areenax.app.core.nav.LocalNavEnv
import com.areenax.app.core.nav.ScreenKeys

/*
 * BottomNav — port of src/components/shared/BottomNav.tsx (SPEC/03 §2).
 * Tab ORDER: myTournament, friends, home, wallet, profile.
 * Icons 24dp FILL'1' variants; active primary, inactive on-surface-variant;
 * NO labels, NO dots; tap → navigate(screen), no-op when already active.
 * Web container: rounded-t-[1.5rem] (24dp), nav-shadow,
 * bg-surface-container-lowest (dark: top hairline outline-variant), px-2 py-3.
 */

private data class NavTab(val screen: String, val icon: String, val contentDescription: String)

private val NAV_TABS = listOf(
    NavTab(ScreenKeys.MY_TOURNAMENT, "emoji_events", "Tournament"),
    NavTab(ScreenKeys.FRIENDS, "group", "Friends"),
    NavTab(ScreenKeys.HOME, "home", "Home"),
    NavTab(ScreenKeys.WALLET, "account_balance_wallet", "Wallet"),
    NavTab(ScreenKeys.PROFILE, "person", "Profile"),
)

@Composable
fun BottomNav(modifier: Modifier = Modifier) {
    val env = LocalNavEnv.current
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navShadow(shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NAV_TABS.forEach { tab ->
            val active = env.navigator.currentScreen == tab.screen
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(56.dp, 48.dp) // A9-04: 40dp → 48dp touch target height
                    .pressScale(interaction, pressedScale = 0.9f)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                    ) {
                        // No-op when already active (web parity)
                        if (env.navigator.currentScreen != tab.screen) {
                            if (tab.screen == ScreenKeys.PROFILE) {
                                env.replace(tab.screen)
                            } else {
                                env.navigate(tab.screen)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconResId(tab.icon, filled = true)),
                    contentDescription = tab.contentDescription,
                    modifier = Modifier.size(24.dp),
                    tint = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
