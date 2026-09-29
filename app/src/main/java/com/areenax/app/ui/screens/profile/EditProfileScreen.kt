package com.areenax.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.R
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.AreenaxShapes
import com.areenax.app.core.theme.Type
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AreenaxPillField
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.AppBarMode
import com.areenax.app.core.ui.GuestGateDialog
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.cardShadow
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.rememberGuestGate
import com.areenax.app.data.LogoutRequest
import com.areenax.app.data.MeResponse
import com.areenax.app.data.PatchProfileRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * EditProfileScreen — key `editProfile` (SPEC/01 E2). Exact port of
 * `src/components/screens/profile/EditProfileScreen.tsx`:
 *
 * - **Profile Info card**: Full Name / Email / In-Game Name pill inputs
 *   (validation: "Full name is required" / "In-game name is required" /
 *   "Enter a valid email address") + primary "Save Changes" → PATCH /me
 *   {fullName, gameName, email, password?} → setUser → toast "Profile
 *   updated"/"Your changes have been saved." → back.
 * - **Change Password card**: Current/New/Confirm password fields with
 *   visibility toggles (new ≥ 6 chars, must match confirm) + outlined
 *   "Change Password" (key icon) → PATCH /me {currentPassword, password} →
 *   toast "Password changed"/"Your password has been updated."; errors land
 *   inline on the current-password field + toast "Password change failed".
 * - Divider + outlined "Log out from all devices" (logout icon) with caption
 *   "Ends every active session" → POST /auth/logout {all:true} → toast
 *   "Logged out from all devices." → logout().
 * - Guest gate "edit your profile" on both PATCH actions.
 *
 * NOTE: the web sends `email: null` to CLEAR the email; the shared
 * PatchProfileRequest DTO (explicitNulls=false) omits the key instead, so
 * clearing the email field is a no-op server-side (documented limitation).
 */
@Composable
fun EditProfileScreen(env: NavEnv) {
    val user by env.session.user.collectAsState()
    val gate = rememberGuestGate()

    var fullName by remember { mutableStateOf(user?.fullName ?: "") }
    var email by remember { mutableStateOf(user?.email ?: "") }
    var gameName by remember { mutableStateOf(user?.gameName ?: "") }
    var currentPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    var fullNameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var gameNameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var currentPasswordError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var loggingOutAll by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp),
    ) {
        AppBar(mode = AppBarMode.Page, title = "Edit Profile")

        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ------------------------------------------------------ Profile Info card
            AdminCard {
                Text(
                    text = "Profile Info",
                    style = Type.headlineMd,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PillField(
                        label = "Full Name",
                        leadingIcon = "person",
                        value = fullName,
                        onValueChange = { fullName = it },
                        error = fullNameError,
                    )
                    PillField(
                        label = "Email",
                        leadingIcon = "mail",
                        value = email,
                        onValueChange = { email = it },
                        error = emailError,
                        keyboardType = KeyboardType.Email,
                    )
                    PillField(
                        label = "In-Game Name",
                        leadingIcon = "sports_esports",
                        value = gameName,
                        onValueChange = { gameName = it },
                        error = gameNameError,
                        placeholder = "Enter in-game name",
                    )

                    PrimaryPillButton(
                        label = "Save Changes",
                        busy = saving,
                        enabled = !saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        if (saving) return@PrimaryPillButton
                        if (!gate.requireAccount(user?.isGuest == true, "edit your profile")) return@PrimaryPillButton
                        fullNameError = if (fullName.isBlank()) "Full name is required" else null
                        gameNameError = if (gameName.isBlank()) "In-game name is required" else null
                        emailError =
                            if (email.isNotBlank() && !EMAIL_REGEX.matches(email.trim())) "Enter a valid email address"
                            else null
                        if (fullNameError != null || gameNameError != null || emailError != null) return@PrimaryPillButton
                        saving = true
                        CoroutineScope(Dispatchers.Main).launch {
                            // A4-02: Save Changes must NEVER carry a credential —
                            // the newPw field belongs to the Change-Password card,
                            // whose own submit sends {currentPassword, password}.
                            val body = PatchProfileRequest(
                                fullName = fullName.trim(),
                                gameName = gameName.trim(),
                                email = email.trim().ifBlank { null },
                            )
                            when (val res = safeCall<MeResponse> { env.api.patchMe(body) }) {
                                is ApiResult.Success -> {
                                    env.session.setUser(res.data.user)
                                    env.toast.show(
                                        title = "Profile updated",
                                        description = "Your changes have been saved.",
                                    )
                                    env.goBack()
                                }
                                is ApiResult.Error -> env.toast.show(
                                    title = "Update failed",
                                    description = res.message,
                                    variant = ToastVariant.Destructive,
                                )
                                is ApiResult.NetworkError -> env.toast.show(
                                    title = "Update failed",
                                    description = res.message,
                                    variant = ToastVariant.Destructive,
                                )
                            }
                            saving = false
                        }
                    }
                }
            }

            // ------------------------------------------------------ Change Password card
            AdminCard {
                Text(
                    text = "Change Password",
                    style = Type.headlineMd,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PasswordField(
                        label = "Current Password",
                        value = currentPw,
                        onValueChange = { currentPw = it },
                        error = currentPasswordError,
                    )
                    PasswordField(
                        label = "New Password",
                        value = newPw,
                        onValueChange = { newPw = it },
                        error = passwordError,
                    )
                    PasswordField(
                        label = "Confirm New Password",
                        value = confirmPw,
                        onValueChange = { confirmPw = it },
                    )

                    // Outlined "Change Password" (border-2 primary)
                    val interaction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = {
                            if (saving) return@Surface
                            if (!gate.requireAccount(user?.isGuest == true, "edit your profile")) return@Surface
                            passwordError = when {
                                newPw.isEmpty() -> "Enter a new password"
                                newPw.length < 6 -> "New password must be at least 6 characters"
                                newPw != confirmPw -> "Passwords do not match"
                                else -> null
                            }
                            if (passwordError != null) return@Surface
                            saving = true
                            CoroutineScope(Dispatchers.Main).launch {
                                val body = PatchProfileRequest(currentPassword = currentPw, password = newPw)
                                when (val res = safeCall<MeResponse> { env.api.patchMe(body) }) {
                                    is ApiResult.Success -> {
                                        env.session.setUser(res.data.user)
                                        currentPw = ""
                                        newPw = ""
                                        confirmPw = ""
                                        currentPasswordError = null
                                        env.toast.show(
                                            title = "Password changed",
                                            description = "Your password has been updated.",
                                        )
                                    }
                                    is ApiResult.Error -> {
                                        currentPasswordError = res.message
                                        env.toast.show(
                                            title = "Password change failed",
                                            description = res.message,
                                            variant = ToastVariant.Destructive,
                                        )
                                    }
                                    is ApiResult.NetworkError -> {
                                        currentPasswordError = res.message
                                        env.toast.show(
                                            title = "Password change failed",
                                            description = res.message,
                                            variant = ToastVariant.Destructive,
                                        )
                                    }
                                }
                                saving = false
                            }
                        },
                        interactionSource = interaction,
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        enabled = !saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(48.dp)
                            .pressScale(interaction, 0.95f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AreenaxIcon(
                                name = "key",
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                filled = true,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Change Password",
                                style = Type.bodyLg,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // Subtle security action — revoke every active session
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                        )
                        Spacer(Modifier.height(16.dp))
                        val quietInteraction = remember { MutableInteractionSource() }
                        Surface(
                            onClick = {
                                if (loggingOutAll) return@Surface
                                loggingOutAll = true
                                CoroutineScope(Dispatchers.Main).launch {
                                    safeCall { env.api.logout(LogoutRequest(all = true)) }
                                    env.toast.show(title = "Logged out from all devices.")
                                    env.logout()
                                }
                            },
                            interactionSource = quietInteraction,
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            enabled = !loggingOutAll,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .pressScale(quietInteraction, 0.95f),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AreenaxIcon(
                                    name = "logout",
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "Log out from all devices",
                                    style = Type.bodyMd,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            text = "Ends every active session",
                            style = Type.labelSm,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }

    GuestGateDialog(gate, env)
}

private val EMAIL_REGEX = Regex("^\\S+@\\S+\\.\\S+$")

// ------------------------------------------------------------------ helpers

/** bg-surface-container-lowest rounded-[1.5rem] p-5 card section. */
@Composable
private fun AdminCard(content: @Composable () -> Unit) {
    Surface(
        shape = AreenaxShapes.Card,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .cardShadow(AreenaxShapes.Card),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) { content() }
    }
}

/** Primary CTA pill (h-14, label-lg, primary glow shadow, busy spinner). */
@Composable
private fun PrimaryPillButton(
    label: String,
    busy: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        enabled = enabled,
        shadowElevation = 4.dp,
        modifier = modifier
            .height(56.dp)
            .pressScale(interaction, 0.98f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    trackColor = Color.Transparent,
                )
            }
            Text(text = label, style = Type.labelLg, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/**
 * Profile pill input — delegates to the shared [AreenaxPillField] (owner field
 * concept: matte dark-charcoal pill, white→blue animated leading icon,
 * bright-white cursor, animated focus ring). Keeps the 14sp-medium label and
 * optional error text.
 */
@Composable
private fun PillField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        AreenaxPillField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder.ifEmpty { null },
            leadingIcon = leadingIcon,
            keyboardType = keyboardType,
            imeAction = ImeAction.Default, // pre-migration KeyboardOptions(keyboardType)
            visualTransformation = visualTransformation,
            isError = error != null,
            trailing = trailing,
        )
        if (error != null) {
            Text(
                text = error,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** PasswordField — pill input + visibility toggle (visibility/visibility_off). */
@Composable
private fun PasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String? = null,
) {
    var show by remember { mutableStateOf(false) }
    PillField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        error = error,
        leadingIcon = "lock",
        keyboardType = KeyboardType.Password,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        placeholder = "••••••••",
        trailing = {
            // A9-04: 24dp icon → 48dp touch target (visual stays 24dp)
            androidx.compose.material3.Icon(
                painter = painterResource(if (show) R.drawable.ic_visibility else R.drawable.ic_visibility_off),
                contentDescription = if (show) "Hide password" else "Show password",
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { show = !show }
                    .padding(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
