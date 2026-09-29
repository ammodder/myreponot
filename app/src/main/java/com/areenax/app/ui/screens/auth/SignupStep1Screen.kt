package com.areenax.app.ui.screens.auth

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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.R
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.AreenaxShapes
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AreenaxPillField
import com.areenax.app.core.ui.areenaxFieldColors
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.primaryGlow
import com.areenax.app.data.AuthCheckRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * SignupStep1Screen — key `signupStep1` (SPEC/01 A3; behavior truth =
 * auth/SignupStep1Screen.tsx, pixel truth = signupstep1.html).
 *
 *  - Header: small logo (h-8) → back arrow (goBack) + progress bar (h-1 track
 *    `bg-surface-container-high-lavender`, primary fill **1/3**) →
 *    "Step 1 of 3" + "Let's set up your access" (20/26 semibold).
 *  - Phone Number: pill row with the STATIC country chip (🇵🇰 +92 +
 *    arrow_drop_down, right hairline — no country list exists, non-clickable)
 *    + tel input ("300 1234567").
 *  - Account UID: numeric-only pill input ("Enter your game UID", digits
 *    stripped on change exactly like the web `replace(/[^0-9]/g, "")`).
 *  - API: POST /auth/check {phone, gameUid} → per-field taken/messages
 *    (defaults "This phone number is already registered." / "This game UID is
 *    already registered."). Check failure is best-effort → navigates anyway.
 *  - Out: navigate(signupStep2, {fullName, gameName, phone, gameUid});
 *    header back → env.goBack(). Hint when either field is empty.
 */
@Composable
fun SignupStep1Screen(env: NavEnv) {
    // Read params ONCE (web: params as {fullName?, gameName?}).
    val fullName = remember { env.params["fullName"] as? String }
    val gameName = remember { env.params["gameName"] as? String }

    val extended = areenaColors()
    val colors = MaterialTheme.colorScheme

    var phone by rememberSaveable { mutableStateOf("") }
    var gameUid by rememberSaveable { mutableStateOf("") }
    var hint by rememberSaveable { mutableStateOf("") }
    var phoneError by rememberSaveable { mutableStateOf("") }
    var gameUidError by rememberSaveable { mutableStateOf("") }
    var checking by rememberSaveable { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val uidFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(16.dp), // root p-[1rem]
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
        ) {
            // Header Section — pt-[1rem] px-[1rem] pb-[0.5rem]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.areenax_logo),
                    contentDescription = "Luminous Core Logo",
                    contentScale = ContentScale.FillHeight,
                    modifier = Modifier
                        .padding(bottom = 16.dp) // mb-[1rem]
                        .height(32.dp) // h-8
                        .align(Alignment.CenterHorizontally),
                )
                // back arrow + progress bar — gap-[0.75rem] mb-[0.75rem]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val backInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .offset(x = (-8).dp) // -ml-2
                            .size(40.dp) // p-2 + 24dp glyph
                            .pressScale(backInteraction, pressedScale = 0.95f) // active:scale-95
                            .clip(CircleShape)
                            .clickable(interactionSource = backInteraction, indication = null) {
                                env.goBack()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AreenaxIcon(
                            name = "arrow_back",
                            contentDescription = "Go back",
                            modifier = Modifier.size(24.dp),
                            tint = colors.onSurfaceVariant,
                        )
                    }
                    // h-1 w-full bg-surface-container-high-lavender rounded-full
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(AreenaxShapes.Pill)
                            .background(extended.surfaceContainerHighLavender),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(1f / 3f) // w-1/3 (transition-all duration-300)
                                .height(4.dp)
                                .clip(AreenaxShapes.Pill)
                                .background(colors.primary),
                        )
                    }
                }
            }

            // Content Section — px-[1.5rem] pt-[0.75rem] pb-[2rem] gap-[1.5rem]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // Title
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { // gap-[0.5rem]
                    Text(
                        text = "Step 1 of 3",
                        style = Type.bodyMd,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        // font-headline-lg-mobile text-[1.25rem] leading-[1.625rem] font-semibold
                        text = "Let's set up your access",
                        style = Type.pageTitle,
                        color = colors.onSurface,
                    )
                }

                // Form — flex flex-col gap-[1.5rem]
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    // Phone Input Field
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { // gap-[0.5rem]
                        FieldLabel("Phone Number")
                        PhoneField(
                            value = phone,
                            onValueChange = {
                                phone = it
                                if (phoneError.isNotEmpty()) phoneError = ""
                            },
                            leadingIcon = "call",
                            imeAction = ImeAction.Next,
                            onAction = { uidFocus.requestFocus() },
                        )
                        AnimatedVisibility(
                            visible = phoneError.isNotEmpty(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            HintText(phoneError)
                        }
                    }
                    // UID Input Field
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldLabel("Account UID")
                        PillTextField(
                            value = gameUid,
                            onValueChange = { raw ->
                                // Game UID is digits-only — strip letters, spaces and
                                // special characters (web replace(/[^0-9]/g, "")).
                                gameUid = raw.filter { it in '0'..'9' }
                                if (gameUidError.isNotEmpty()) gameUidError = ""
                            },
                            placeholder = "Enter your game UID",
                            leadingIcon = "sports_esports",
                            keyboardType = KeyboardType.Number, // inputMode="numeric"
                            imeAction = ImeAction.Done,
                            onAction = {
                                if (!checking) {
                                    submitStep1(env, scope, fullName, gameName, phone, gameUid, { hint = it }, { pe, ue -> phoneError = pe; gameUidError = ue }, { checking = it })
                                }
                            },
                            focusRequester = uidFocus,
                        )
                        AnimatedVisibility(
                            visible = gameUidError.isNotEmpty(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            HintText(gameUidError)
                        }
                    }
                    AnimatedVisibility(
                        visible = hint.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        HintText(hint)
                    }
                    // Action Button — mt-[0.75rem]
                    Box(Modifier.padding(top = 12.dp)) {
                        AuthPrimaryButton(
                            label = "Continue",
                            busyLabel = "Checking...",
                            busy = checking,
                            enabled = !checking,
                            withShadow = true,
                            onClick = {
                                if (!checking) {
                                    submitStep1(env, scope, fullName, gameName, phone, gameUid, { hint = it }, { pe, ue -> phoneError = pe; gameUidError = ue }, { checking = it })
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Web handleContinue: both trimmed values required → POST /auth/check
 * {phone, gameUid}; per-field taken/messages with the web's default strings;
 * check failure is best-effort → navigate(signupStep2) with all params.
 */
private fun submitStep1(
    env: NavEnv,
    scope: CoroutineScope,
    fullName: String?,
    gameName: String?,
    phoneRaw: String,
    gameUidRaw: String,
    setHint: (String) -> Unit,
    setFieldErrors: (phoneError: String, gameUidError: String) -> Unit,
    setChecking: (Boolean) -> Unit,
) {
    val phone = phoneRaw.trim()
    val gameUid = gameUidRaw.trim()
    if (phone.isEmpty() || gameUid.isEmpty()) {
        setHint("Please enter your phone number and game UID.")
        return
    }
    setHint("")
    setChecking(true)
    scope.launch {
        val res = safeCall {
            env.api.authCheck(AuthCheckRequest(phone = phone, gameUid = gameUid))
        }
        if (res is ApiResult.Success) {
            val pErr = if (res.data.taken["phone"] == true) {
                res.data.messages["phone"] ?: "This phone number is already registered."
            } else {
                ""
            }
            val uErr = if (res.data.taken["gameUid"] == true) {
                res.data.messages["gameUid"] ?: "This game UID is already registered."
            } else {
                ""
            }
            setFieldErrors(pErr, uErr)
            if (pErr.isNotEmpty() || uErr.isNotEmpty()) {
                setChecking(false)
                return@launch
            }
        }
        // Availability check is best-effort — registration 409 is the hard stop
        env.navigate(
            ScreenKeys.SIGNUP_STEP2,
            mapOf(
                "fullName" to fullName,
                "gameName" to gameName,
                "phone" to phone,
                "gameUid" to gameUid,
            ),
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
 * px-4 text-body-lg ... border-2 border-transparent dark:border-outline-variant
 * focus:border-primary focus:ring-2 focus:ring-primary/20` (shared styling).
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
 * Step-1 phone row — `flex items-center h-12 bg-surface-container-lavender
 * rounded-full px-4 focus-within:border-primary` with the STATIC country chip
 * (🇵🇰 +92 + arrow_drop_down, right hairline) and a tel input. The chip is a
 * plain display (no country list exists) — exactly like the web.
 *
 * Custom layout (the country chip owns the leading slot, so it cannot be an
 * [AreenaxPillField] icon) — colors come from the shared [areenaxFieldColors]
 * so the pill follows the owner field concept: matte charcoal container in
 * dark mode, subtle animated 1dp ring, bright-white cursor, animated icon.
 */
@Composable
private fun PhoneField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    onAction: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val fieldColors = areenaxFieldColors(focused = focused)
    val pill = AreenaxShapes.Pill
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp) // h-12
            .clip(pill)
            .background(fieldColors.container, pill)
            .border(1.dp, fieldColors.ring, pill)
            .onFocusChanged { focused = it.hasFocus } // focus-within
            .padding(horizontal = 16.dp), // px-4
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Country Selector — static display (no country list exists yet)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp), // gap-[0.5rem]
        ) {
            Text(
                text = "🇵🇰",
                style = Type.bodyLg.copy(fontSize = 18.sp), // text-[1.125rem]
                color = colors.onSurface,
            )
            // font-label-lg text-body-lg font-normal
            Text(
                text = "+92",
                style = Type.bodyLg.copy(fontWeight = FontWeight.Normal),
                color = colors.onSurface,
            )
            AreenaxIcon(
                name = "arrow_drop_down",
                contentDescription = null,
                modifier = Modifier.size(18.dp), // text-[1.125rem]
                tint = colors.onSurfaceVariant,
            )
        }
        Box(Modifier.width(12.dp)) // pr-[0.75rem]
        // border-r border-outline-variant — vertical hairline
        Box(
            Modifier
                .width(1.dp)
                .height(24.dp)
                .background(colors.outlineVariant),
        )
        Box(Modifier.width(12.dp))
        if (leadingIcon != null) {
            AreenaxIcon(
                name = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = fieldColors.icon,
            )
            Box(Modifier.width(12.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            textStyle = Type.bodyLg.copy(color = fieldColors.text),
            cursorBrush = SolidColor(fieldColors.cursor),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = imeAction), // type="tel"
            keyboardActions = KeyboardActions(onNext = { onAction?.invoke() }, onDone = { onAction?.invoke() }),
            decorationBox = { innerField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = "300 1234567",
                            style = Type.bodyLg.copy(color = fieldColors.placeholder),
                            maxLines = 1,
                        )
                    }
                    innerField()
                }
            },
        )
    }
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
