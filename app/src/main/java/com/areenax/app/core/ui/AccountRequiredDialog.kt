package com.areenax.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.areenax.app.R
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/*
 * AccountRequiredDialog — guest gate (SPEC/03 §8).
 * Centered modal (black/60 backdrop, scale-in), white card rounded-[1.75rem]
 * max-w 320dp p-6: 64dp bg-primary-fixed circle with account_circle 32dp
 * text-primary; title "Account Required" (headline-md bold);
 * "You're exploring AREENAX as a guest." + "Create a free account to «feature»."
 * (body-md muted); primary pill "Create Account" → nav(signup);
 * text button "Continue as Guest" → dismiss.
 *
 * [rememberGuestGate] mirrors the web useRequireAccount hook:
 *   val gate = rememberGuestGate()
 *   Button(onClick = { if (gate.requireAccount(user.isGuest, "make a deposit")) ... }) {}
 *   GuestGateDialog(gate, env) // host once per screen
 */

class GuestGateState internal constructor() {
    var open by mutableStateOf(false)
        private set
    var feature by mutableStateOf<String?>(null)
        private set

    /** Call in front of the gated action: returns true when the user may proceed. */
    fun requireAccount(isGuest: Boolean, featureName: String? = null): Boolean {
        if (!isGuest) return true
        feature = featureName
        open = true
        return false
    }

    fun dismiss() {
        open = false
        feature = null
    }
}

@Composable
fun rememberGuestGate(): GuestGateState = remember { GuestGateState() }

/** GuestGateDialog = AccountRequiredDialog wired to the gate + signup routing. */
@Composable
fun GuestGateDialog(gate: GuestGateState, env: NavEnv) {
    AccountRequiredDialog(
        open = gate.open,
        feature = gate.feature,
        onOpenChange = { gate.dismiss() },
        onCreateAccount = {
            gate.dismiss()
            env.navigate(ScreenKeys.SIGNUP)
        },
    )
}

/**
 * Shared centered-modal scaffold for AccountRequired / InsufficientBalance —
 * black/60 backdrop, tap-outside dismiss, 28dp card, max width 320dp.
 */
@Composable
fun CenteredModalScaffold(
    open: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!open) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .padding(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                onClick = onDismiss, // swallow card taps (don't dismiss on card touch)
                enabled = false,
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun AccountRequiredDialog(
    open: Boolean,
    feature: String?,
    onOpenChange: (Boolean) -> Unit,
    onCreateAccount: () -> Unit,
) {
    CenteredModalScaffold(open = open, onDismiss = { onOpenChange(false) }) {
        Box(
            Modifier
                .size(64.dp)
                .background(areenaColors().primaryFixed, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_account_circle),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "Account Required",
            style = Type.headlineMd,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "You're exploring AREENAX as a guest.",
            style = Type.bodyMd,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            "Create a free account to ${feature ?: "unlock this feature"}.",
            style = Type.bodyMd,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = onCreateAccount,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text("Create Account", style = Type.labelLg)
        }
        TextButton(onClick = { onOpenChange(false) }) {
            Text(
                "Continue as Guest",
                style = Type.labelLg,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
