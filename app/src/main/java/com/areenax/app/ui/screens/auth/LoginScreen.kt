package com.areenax.app.ui.screens.auth

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.areenax.app.R
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.session.SettingsCache
import com.areenax.app.core.theme.AreenaxShapes
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AreenaxPillField
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.primaryGlow
import com.areenax.app.core.util.QrKind
import com.areenax.app.core.util.parseQrPayload
import com.areenax.app.data.LoginRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * LoginScreen — key `login` (SPEC/01 A1; behavior truth = auth/LoginScreen.tsx,
 * pixel truth = upload/pages_extracted/pages/index.html).
 *
 *  - Centered lavender column (max 420dp): brand logo (5rem) → PendingQrBanner
 *    (when a QR was scanned pre-auth) → "Welcome back" (display-lg) →
 *    "Please enter required details." → single "Email or Phone" identifier field
 *    + password field (visibility toggle) → Login CTA (spinner + "Logging In") →
 *    "or" divider → outlined "Continue as Guest" → "Don't have an account?" +
 *    "Create Account" → empty mt-[3rem] slot (the old demo-hint spot; the demo
 *    helper text is NOT present in the React source, so nothing is rendered).
 *  - The index.html `hidden lg:block` hero panel is desktop-only chrome the web
 *    app itself omits inside the AppShell frame — omitted here as well.
 *  - API: POST /auth/login. The identifier is a phone when digits-only (after
 *    stripping whitespace/-/./() plus an optional "+") matches ^\+?[0-9]{7,15}$;
 *    phones try {phone: digits} first and retry {phone: as-typed} on failure,
 *    everything else posts {email: identifier}. Success → env.setAuth (auto-home).
 *    Errors → destructive toast "Login failed" + server message.
 *  - Guest → POST /auth/guest with its own spinner.
 *  - "Create Account" → navigate(signup).
 */
@Composable
fun LoginScreen(env: NavEnv) {
    val extended = areenaColors()
    val colors = MaterialTheme.colorScheme

    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPw by rememberSaveable { mutableStateOf(false) }
    var loggingIn by rememberSaveable { mutableStateOf(false) }
    var guestIn by rememberSaveable { mutableStateOf(false) }
    var hint by rememberSaveable { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val passwordFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(16.dp), // web p-[1rem]
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp)) // logo mt-6
            Image(
                painter = painterResource(R.drawable.areenax_logo),
                contentDescription = "Brand Logo",
                contentScale = ContentScale.FillHeight,
                modifier = Modifier.height(80.dp), // h-[5rem]
            )
            Spacer(Modifier.height(20.dp)) // wrapper mb-[1.25rem]

            // Pending QR banner — the visitor scanned a QR code and must authenticate.
            Column(Modifier.fillMaxWidth()) {
                PendingQrBanner(env)
            }
            Spacer(Modifier.height(24.dp)) // mb-[1.5rem]

            Text(
                text = "Welcome back",
                style = Type.displayLg,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp)) // mb-[0.375rem]
            Text(
                text = "Please enter required details.",
                style = Type.bodyMd,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp)) // mb-[1.25rem]

            // form — space-y-[1.0rem], mt-[0.75rem]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    FieldLabel("Email or Phone")
                    PillTextField(
                        value = identifier,
                        onValueChange = { identifier = it },
                        placeholder = "Enter your email or phone number",
                        leadingIcon = "person",
                        textStyle = Type.bodyMd,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        onAction = { passwordFocus.requestFocus() },
                    )
                }
                Column(Modifier.fillMaxWidth()) {
                    FieldLabel("Password")
                    PillTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "Enter your password",
                        leadingIcon = "lock",
                        textStyle = Type.bodyMd,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        onAction = {
                            if (!loggingIn && !guestIn) {
                                submitLogin(env, scope, identifier, password, { hint = it }, { loggingIn = it })
                            }
                        },
                        visualTransformation = if (showPw) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        focusRequester = passwordFocus,
                        trailing = {
                            // w-9 h-9 rounded-full toggle; 'FILL' 1 when the pw is visible
                            val pwInteraction = remember { MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .pressScale(pwInteraction, pressedScale = 0.9f) // active:scale-90
                                    .clip(CircleShape)
                                    .clickable(interactionSource = pwInteraction, indication = null) {
                                        showPw = !showPw
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                AreenaxIcon(
                                    name = if (showPw) "visibility" else "visibility_off",
                                    contentDescription = if (showPw) "Hide password" else "Show password",
                                    modifier = Modifier.size(20.dp), // text-[1.25rem]
                                    filled = showPw,
                                    tint = colors.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
                AnimatedVisibility(
                    visible = hint.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    HintText(hint)
                }
                // Primary CTA — h-14, no shadow on the login variant
                AuthPrimaryButton(
                    label = "Login",
                    busyLabel = "Logging In",
                    busy = loggingIn,
                    enabled = !loggingIn && !guestIn,
                    onClick = {
                        if (!loggingIn) {
                            submitLogin(env, scope, identifier, password, { hint = it }, { loggingIn = it })
                        }
                    },
                )
            }

            // or-divider — my-[2rem]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(colors.outlineVariant.copy(alpha = 0.5f)), // border-outline-variant/50
                )
                Text(
                    text = "or",
                    style = Type.labelLg,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp), // px-[1rem]
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(colors.outlineVariant.copy(alpha = 0.5f)),
                )
            }

            // Continue as Guest — transparent outlined pill, spinner while pending
            val guestInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AreenaxShapes.Pill)
                    .background(Color.Transparent)
                    .border(1.dp, colors.outline, AreenaxShapes.Pill)
                    .clickable(interactionSource = guestInteraction, indication = null, enabled = !guestIn && !loggingIn) {
                        if (!guestIn) submitGuest(env, scope) { guestIn = it }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp), // py-[0.75rem] px-[1rem]
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp), // gap-[0.75rem]
                ) {
                    if (guestIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp), // w-4 h-4
                            strokeWidth = 2.dp,
                            color = colors.onSurface, // border-t-on-surface
                            trackColor = colors.outline, // border-outline
                        )
                    }
                    Text(
                        text = "Continue as Guest",
                        style = Type.labelLg,
                        color = colors.onSurface,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp), // mt-[2rem]
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Don't have an account?",
                    style = Type.bodyLg.copy(fontWeight = FontWeight.Normal), // font-normal
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp)) // mb-[1rem]
                Text(
                    text = "Create Account",
                    style = Type.labelLg,
                    color = colors.primary,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { env.navigate(ScreenKeys.SIGNUP) }
                        .padding(vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(48.dp)) // mt-[3rem] empty slot (old demo-hint spot)
        }
    }
}

/**
 * Web handleLogin: identifier auto-detection (phone vs email) + the phone
 * digits-first / then-as-typed retry. Errors → destructive "Login failed" toast
 * with the server message (web errText). Success → env.setAuth (auto-nav home).
 */
private fun submitLogin(
    env: NavEnv,
    scope: CoroutineScope,
    identifierRaw: String,
    password: String,
    setHint: (String) -> Unit,
    setLoggingIn: (Boolean) -> Unit,
) {
    val id = identifierRaw.trim()
    if (id.isEmpty()) {
        setHint("Please enter your email or phone number.")
        return
    }
    if (password.isEmpty()) {
        setHint("Please enter your password.")
        return
    }
    setHint("")
    setLoggingIn(true)
    scope.launch {
        val res = if (isPhoneNumber(id)) {
            // Accounts store the phone exactly as typed at signup — try the bare
            // digits-only form first, then the value as typed (web login()).
            val digits = id.replace(PHONE_STRIP_REGEX, "").removePrefix("+")
            val first = safeCall { env.api.login(LoginRequest(phone = digits, password = password)) }
            if (first !is ApiResult.Success && digits != id) {
                safeCall { env.api.login(LoginRequest(phone = id, password = password)) }
            } else {
                first
            }
        } else {
            safeCall { env.api.login(LoginRequest(email = id, password = password)) }
        }
        when (res) {
            is ApiResult.Success -> env.setAuth(res.data.token, res.data.user)
            is ApiResult.Error ->
                env.toast.show("Login failed", res.message, ToastVariant.Destructive)
            is ApiResult.NetworkError ->
                env.toast.show("Login failed", res.message, ToastVariant.Destructive)
        }
        setLoggingIn(false) // web finally
    }
}

/** Web handleGuest — POST /auth/guest → setAuth; errors → "Login failed" toast. */
private fun submitGuest(
    env: NavEnv,
    scope: CoroutineScope,
    setGuestIn: (Boolean) -> Unit,
) {
    setGuestIn(true)
    scope.launch {
        when (val res = safeCall { env.api.guestLogin() }) {
            is ApiResult.Success -> env.setAuth(res.data.token, res.data.user)
            is ApiResult.Error ->
                env.toast.show("Login failed", res.message, ToastVariant.Destructive)
            is ApiResult.NetworkError ->
                env.toast.show("Login failed", res.message, ToastVariant.Destructive)
        }
        setGuestIn(false) // web finally
    }
}

private val PHONE_STRIP_REGEX = Regex("[\\s\\-().]")
private val PHONE_DETECT_REGEX = Regex("^\\+?[0-9]{7,15}$")

/** Web isPhoneNumber: digits-only (separators allowed, optional leading +). */
private fun isPhoneNumber(value: String): Boolean =
    PHONE_DETECT_REGEX.matches(value.trim().replace(PHONE_STRIP_REGEX, ""))

// ===========================================================================
//  Private auth primitives (web-auth parity, file-local per SPEC/05 §6)
// ===========================================================================

/** `font-label-lg text-label-lg text-on-surface-variant mb-[0.5rem] ml-[0.5rem]` */
@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Type.labelLg,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 8.dp, bottom = 8.dp),
    )
}

/** `font-label-lg text-label-lg text-error ml-[0.5rem]` — inline hint line. */
@Composable
private fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Type.labelLg,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.padding(start = 8.dp),
    )
}

/**
 * The AREENAX auth text field — `h-12 bg-surface-container-lavender rounded-full
 * px-4 border-2 border-transparent dark:border-outline-variant focus:border-primary
 * focus:ring-2 focus:ring-primary/15..20`. Delegates to the shared
 * [AreenaxPillField] (owner field concept): matte charcoal pill in dark mode,
 * leading icon white → vibrant blue on focus, subtle animated 1dp focus ring,
 * bright-white blinking cursor.
 */
@Composable
private fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: String? = null,
    textStyle: TextStyle = Type.bodyLg,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onAction: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    focusRequester: FocusRequester? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    AreenaxPillField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        textStyle = textStyle,
        keyboardType = keyboardType,
        imeAction = imeAction,
        onAction = onAction,
        visualTransformation = visualTransformation,
        focusRequester = focusRequester,
        trailing = trailing,
    )
}

/**
 * Primary auth CTA — `rounded-full bg-primary text-on-primary font-label-lg
 * active:scale-[0.98]`; login = h-14 shadowless, signup/steps = h-[3.5rem] +
 * `shadow-[0_8px_20px_rgba(0,74,198,0.25)]`. Busy state = 16dp white spinner
 * (`border-white/40 border-t-white`) + the busy label.
 */
@Composable
private fun AuthPrimaryButton(
    label: String,
    busyLabel: String,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    withShadow: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp) // h-14 == h-[3.5rem]
            .pressScale(interaction, pressedScale = 0.98f) // active:scale-[0.98]
            .then(if (withShadow) Modifier.primaryGlow(AreenaxShapes.Pill) else Modifier)
            .clip(AreenaxShapes.Pill)
            .background(
                when {
                    busy -> colors.primary.copy(alpha = 0.7f) // disabled:opacity-70 (login)
                    !enabled -> colors.primary.copy(alpha = 0.6f) // disabled:opacity-60
                    else -> colors.primary
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !busy,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp), // gap-[0.75rem]
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp), // w-4 h-4
                    strokeWidth = 2.dp,
                    color = colors.onPrimary, // border-t-white
                    trackColor = colors.onPrimary.copy(alpha = 0.4f), // border-white/40
                )
                Text(text = busyLabel, style = Type.labelLg, color = colors.onPrimary)
            }
        } else {
            Text(text = label, style = Type.labelLg, color = colors.onPrimary)
        }
    }
}

/**
 * Pending-QR banner (web LoginScreen/SignupScreen local component): shown when
 * a QR was scanned pre-auth (`session.pendingQr`) — offers the app download.
 * `bg-surface-container-lowest border border-outline-variant/30 rounded-2xl p-4
 * flex items-start gap-3`.
 */
@Composable
private fun PendingQrBanner(env: NavEnv, modifier: Modifier = Modifier) {
    val raw by env.session.pendingQr.collectAsState(initial = null)
    val payload = remember(raw) { parseQrPayload(raw) }
    var downloadUrl by remember { mutableStateOf("") }

    // Web: getSettings() → appDownloadUrl once the banner is visible.
    LaunchedEffect(payload) {
        if (payload != null) {
            val settings = SettingsCache.get { env.api }
            downloadUrl = settings?.appDownloadUrl ?: ""
        }
    }

    val visiblePayload = payload ?: return
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)) // rounded-2xl
            .background(colors.surfaceContainerLowest)
            .border(
                1.dp,
                colors.outlineVariant.copy(alpha = 0.3f), // border-outline-variant/30
                RoundedCornerShape(16.dp),
            )
            .padding(16.dp), // p-4
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp), // gap-3
    ) {
        Box(
            modifier = Modifier
                .size(40.dp) // w-10 h-10
                .background(colors.primary.copy(alpha = 0.1f), CircleShape) // bg-primary/10
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AreenaxIcon(
                name = "qr_code_2",
                contentDescription = null,
                modifier = Modifier.size(22.dp), // text-[1.375rem]
                tint = colors.primary,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = "You scanned an AREENAX QR code",
                style = Type.bodyLg.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
            )
            Spacer(Modifier.height(4.dp)) // mt-1
            Text(
                text = if (visiblePayload.kind == QrKind.USER) {
                    "Log in or create an account to add this player as a friend."
                } else {
                    "Log in or create an account to join this team."
                },
                style = Type.bodyMd,
                color = colors.onSurfaceVariant,
            )
            if (downloadUrl.isNotBlank()) {
                val linkInteraction = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .padding(top = 12.dp) // mt-3
                        .height(40.dp) // h-10
                        .clip(AreenaxShapes.Pill)
                        .border(1.dp, colors.outlineVariant, AreenaxShapes.Pill)
                        .clickable(interactionSource = linkInteraction, indication = null) {
                            runCatching {
                                env.context.startActivity(
                                    Intent(Intent.ACTION_VIEW, downloadUrl.toUri())
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                        .padding(horizontal = 16.dp), // px-4
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AreenaxIcon(
                        name = "download",
                        contentDescription = null,
                        modifier = Modifier.size(18.dp), // text-[1.125rem]
                        tint = colors.primary,
                    )
                    Text(
                        text = "Download the App",
                        style = Type.labelLg.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.primary,
                    )
                }
            }
        }
    }
}
