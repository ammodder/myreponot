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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
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
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.primaryGlow
import com.areenax.app.core.util.QrKind
import com.areenax.app.core.util.parseQrPayload
import com.areenax.app.data.AuthCheckRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * SignupScreen — key `signup` (SPEC/01 A2; behavior truth = auth/SignupScreen.tsx,
 * pixel truth = upload/pages_extracted/pages/signup.html).
 *
 *  - Lavender root; the main area is `bg-surface-container-lowest` in light
 *    (transparent in dark, exactly like the web `dark:bg-transparent`) with the
 *    footer reading as one surface.
 *  - Header: brand logo (5rem) → PendingQrBanner → "Create your account"
 *    (display-lg) + "Start your journey today" (body-lg) → Full Name + Game Name
 *    pill inputs (gameName error clears on change) → red hint → rounded-full
 *    primary CTA "Continue" (spinner + "Checking..." while checking, primary
 *    glow shadow) → footer "Already have an account? Log in" → navigate(login).
 *  - API: POST /auth/check {gameName}. `taken.gameName` → inline server message
 *    (default "This in-game name is already taken."), stays. Check failure is
 *    best-effort → navigates anyway (register 409 is the hard stop).
 *  - Out: navigate(signupStep1, {fullName, gameName}) — trimmed values.
 */
@Composable
fun SignupScreen(env: NavEnv) {
    val extended = areenaColors()
    val colors = MaterialTheme.colorScheme

    var fullName by rememberSaveable { mutableStateOf("") }
    var gameName by rememberSaveable { mutableStateOf("") }
    var hint by rememberSaveable { mutableStateOf("") }
    var gameNameError by rememberSaveable { mutableStateOf("") }
    var checking by rememberSaveable { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val gameNameFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Header (Logo)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent) // header sits on the lavender root
                .padding(vertical = 16.dp), // header py-[1rem]
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp)) // img mt-6
            Image(
                painter = painterResource(R.drawable.areenax_logo),
                contentDescription = "Brand Logo",
                contentScale = ContentScale.FillHeight,
                modifier = Modifier.height(80.dp), // h-[5rem]
            )
        }
        Spacer(Modifier.height(16.dp)) // header mb-[1rem]

        // Scrollable Content Area — bg-surface-container-lowest dark:bg-transparent
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(
                    if (extended.isDark) Modifier.background(Color.Transparent)
                    else Modifier.background(colors.surfaceContainerLowest),
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp) // px-[1.5rem]
                .padding(bottom = 32.dp), // pb-[2rem]
        ) {
            // Pending QR banner — the visitor scanned a QR code and must authenticate.
            Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) { // mb-[1rem]
                PendingQrBanner(env)
            }

            // Headings — text-center mb-[2rem]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Create your account",
                    style = Type.displayLg,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp), // mb-[0.5rem]
                )
                Text(
                    text = "Start your journey today",
                    style = Type.bodyLg.copy(fontWeight = FontWeight.Normal), // font-normal
                    color = colors.onSurfaceVariant,
                )
            }

            // Form — flex flex-col gap-[1rem]
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Full Name Field
                Column(Modifier.fillMaxWidth()) {
                    FieldLabel("Full Name")
                    PillTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        placeholder = "Enter your full name",
                        leadingIcon = "person",
                        imeAction = ImeAction.Next,
                        onAction = { gameNameFocus.requestFocus() },
                    )
                }
                // Game Name Field
                Column(Modifier.fillMaxWidth()) {
                    FieldLabel("Game Name")
                    PillTextField(
                        value = gameName,
                        onValueChange = {
                            gameName = it
                            if (gameNameError.isNotEmpty()) gameNameError = ""
                        },
                        placeholder = "Enter your game name",
                        leadingIcon = "sports_esports",
                        imeAction = ImeAction.Done,
                        onAction = {
                            if (!checking) {
                                submitSignup(env, scope, fullName, gameName, { hint = it }, { gameNameError = it }, { checking = it })
                            }
                        },
                        focusRequester = gameNameFocus,
                    )
                    AnimatedVisibility(
                        visible = gameNameError.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        HintText(gameNameError)
                    }
                }
                AnimatedVisibility(
                    visible = hint.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    HintText(hint)
                }
                // Primary Action CTA — mt-[0.75rem] on top of the 16dp gap
                Box(Modifier.padding(top = 12.dp)) {
                    AuthPrimaryButton(
                        label = "Continue",
                        busyLabel = "Checking...",
                        busy = checking,
                        enabled = !checking,
                        withShadow = true, // shadow-[0_8px_20px_rgba(0,74,198,0.25)]
                        onClick = {
                            if (!checking) {
                                submitSignup(env, scope, fullName, gameName, { hint = it }, { gameNameError = it }, { checking = it })
                            }
                        },
                    )
                }
            }
        }

        // Footer — same background as the content above so the page reads as one surface
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (extended.isDark) Modifier.background(Color.Transparent)
                    else Modifier.background(colors.surfaceContainerLowest),
                )
                .padding(vertical = 24.dp), // py-[1.5rem]
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Already have an account? ",
                style = Type.bodyMd,
                color = colors.onSurfaceVariant,
            )
            Text(
                text = "Log in",
                style = Type.labelLg.copy(fontWeight = FontWeight.Bold), // font-label-lg font-bold
                color = colors.primary,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { env.navigate(ScreenKeys.LOGIN) }
                    .padding(start = 4.dp), // ml-[0.25rem]
            )
        }
    }
}

/**
 * Web handleContinue: both trimmed values required → POST /auth/check {gameName};
 * taken.gameName → inline server message (default fallback), stays; check
 * failure is best-effort → navigate(signupStep1) with trimmed {fullName, gameName}.
 */
private fun submitSignup(
    env: NavEnv,
    scope: CoroutineScope,
    fullNameRaw: String,
    gameNameRaw: String,
    setHint: (String) -> Unit,
    setGameNameError: (String) -> Unit,
    setChecking: (Boolean) -> Unit,
) {
    val fullName = fullNameRaw.trim()
    val gameName = gameNameRaw.trim()
    if (fullName.isEmpty() || gameName.isEmpty()) {
        setHint("Please enter your full name and game name.")
        return
    }
    setHint("")
    setChecking(true)
    scope.launch {
        val res = safeCall { env.api.authCheck(AuthCheckRequest(gameName = gameName)) }
        if (res is ApiResult.Success && res.data.taken["gameName"] == true) {
            setGameNameError(res.data.messages["gameName"] ?: "This in-game name is already taken.")
            setChecking(false)
            return@launch
        }
        // Availability check is best-effort — registration 409 is the hard stop
        env.navigate(
            ScreenKeys.SIGNUP_STEP1,
            mapOf("fullName" to fullName, "gameName" to gameName),
        )
        setChecking(false) // web finally
    }
}

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
 * px-4 text-body-lg text-on-surface placeholder:text-outline dark:placeholder:
 * text-on-surface-variant outline-none border-2 border-transparent dark:border-
 * outline-variant focus:border-primary focus:ring-2 focus:ring-primary/20`.
 * Delegates to the shared [AreenaxPillField] (owner field concept): matte
 * charcoal pill in dark mode, leading icon white → vibrant blue on focus,
 * subtle animated 1dp focus ring, bright-white blinking cursor.
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
 * active:scale-[0.98]` + `shadow-[0_8px_20px_rgba(0,74,198,0.25)]`. Busy state
 * = 16dp white spinner (`border-white/40 border-t-white`) + the busy label.
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
            .height(56.dp) // h-[3.5rem]
            .pressScale(interaction, pressedScale = 0.98f) // active:scale-[0.98]
            .then(if (withShadow) Modifier.primaryGlow(AreenaxShapes.Pill) else Modifier)
            .clip(AreenaxShapes.Pill)
            .background(
                when {
                    busy -> colors.primary.copy(alpha = 0.6f) // disabled:opacity-60
                    !enabled -> colors.primary.copy(alpha = 0.6f)
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
 * Pending-Qr banner (web LoginScreen/SignupScreen local component): shown when
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
            .clip(AreenaxShapes.MediumCard) // rounded-2xl
            .background(colors.surfaceContainerLowest)
            .border(
                1.dp,
                colors.outlineVariant.copy(alpha = 0.3f), // border-outline-variant/30
                AreenaxShapes.MediumCard,
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
