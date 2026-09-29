package com.areenax.app.core.ui

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.areenax.app.R
import com.areenax.app.core.nav.LocalNavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.session.SettingsCache

/*
 * SupportFab — port of src/components/shared/SupportFab.tsx (SPEC/03 §6).
 * Fixed right 56dp bg-primary circle, white support_agent 36dp, shadow-fab,
 * scale press. Reads cached settings → opens https://wa.me/<digits>;
 * no WhatsApp number → nav(about).
 * offset=true → above the BottomNav (bottom-24); false → bottom-6.
 * Used by: Home, Tournaments, Wallet, Profile (offset) and
 * TournamentDetails (offset=false). NOT on auth/chat/admin.
 */

@Composable
fun SupportFab(offset: Boolean = true) {
    val env = LocalNavEnv.current
    val isOnline by env.connectivity.isOnline.collectAsState()
    if (!isOnline) return // web hides FAB effectively behind the offline swap

    // A8-05: web SupportFab = shadow-fab (primary glow) + scale press, NO
    // ripple. M3 Surface(onClick) always applies LocalIndication, so the press
    // is a plain clickable with indication = null over the Surface.
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 8.dp,
        modifier = Modifier
            .size(56.dp)
            .primaryGlow(CircleShape) // web shadow-fab
            .pressScale(interaction, pressedScale = 0.95f) // web active:scale-95
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    val settings = SettingsCache.getCached()
                    val number = settings?.whatsapp?.filter { it.isDigit() }.orEmpty()
                    if (number.isNotBlank()) {
                        try {
                            env.context.startActivity(
                                Intent(Intent.ACTION_VIEW, "https://wa.me/$number".toUri())
                                    // env.context is the APPLICATION context — a NEW_TASK
                                    // flag is mandatory or startActivity throws
                                    // AndroidRuntimeException (Task 3-b integration note).
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } catch (_: Exception) {
                            env.navigate(ScreenKeys.ABOUT)
                        }
                    } else {
                        env.navigate(ScreenKeys.ABOUT)
                    }
                },
            ),
    ) {
        Box24(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_support_agent),
                contentDescription = "Support",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun Box24(contentAlignment: Alignment, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier = Modifier.size(56.dp), contentAlignment = contentAlignment) {
        content()
    }
}
