package com.areenax.app.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.R
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.AreenaxShapes
import com.areenax.app.core.theme.HankenGrotesk
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AreenaxPillField
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.primaryGlow
import com.areenax.app.data.AuthCheckRequest
import com.areenax.app.data.RegisterRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * SignupStep3Screen — key `signupStep3` (SPEC/01 A5; behavior truth =
 * auth/SignupStep3Screen.tsx, pixel truth = signupstep3.html).
 *
 *  - Progress bar **full** → "Step 3 of 3" + "Final details".
 *  - Email Address (format `^\S+@\S+\.\S+$`) + Referral Code (Optional) pill
 *    inputs; email errors clear on change.
 *  - Custom agreement checkbox (20dp rounded-[6dp] box; checked = bg-primary +
 *    white `check`): "I agree to the Terms & Conditions and Privacy Policy,
 *    and I consent to create an AREENAX account." — the two links navigate
 *    (terms / privacy) without toggling, like the web stopPropagation.
 *  - API: 1) POST /auth/check {email} (best-effort) → taken.email → inline
 *    error + destructive toast "Email already registered", stay.
 *    2) POST /auth/register with ALL params accumulated through the steps
 *    {fullName, gameName, phone, gameUid, password, email, referralCode?} →
 *    toast "Account created" / "Welcome to AREENAX! Your journey starts now."
 *    → env.setAuth (auto-home).
 *    409 → toast "Already registered" + server message; email conflicts also
 *    get the inline emailError + "Go to Login" outlined pill → navigate(login);
 *    other fields (phone/gameName/gameUid) → inline hint so the user can go
 *    back and fix the value. Other errors → toast "Registration failed".
 *  - CTA "Complete Account" disabled until `agree` (spinner + same label while
 *    submitting). Back (arrow + system back) → goBack.
 */
@Composable
fun SignupStep3Screen(env: NavEnv) {
    // Read params ONCE (web: params as {fullName?, gameName?, phone?, gameUid?, password?}).
    val fullName = remember { env.params["fullName"] as? String }
    val gameName = remember { env.params["gameName"] as? String }
    val phone = remember { env.params["phone"] as? String }
    val gameUid = remember { env.params["gameUid"] as? String }
    val password = remember { env.params["password"] as? String }

    val extended = areenaColors()
    val colors = MaterialTheme.colorScheme

    var email by rememberSaveable { mutableStateOf("") }
    var referralCode by rememberSaveable { mutableStateOf("") }
    var agree by rememberSaveable { mutableStateOf(false) }
    var hint by rememberSaveable { mutableStateOf("") }
    var emailError by rememberSaveable { mutableStateOf("") }
    var showGoLogin by rememberSaveable { mutableStateOf(false) }
    var submitting by rememberSaveable { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val referralFocus = remember { FocusRequester() }

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
            // Header Section
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
                // back arrow + progress bar (full)
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
                            .size(40.dp)
                            .pressScale(backInteraction, pressedScale = 0.95f)
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
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp) // h-1
                            .clip(AreenaxShapes.Pill)
                            .background(extended.surfaceContainerHighLavender),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(1f) // w-full
                                .height(4.dp)
                                .clip(AreenaxShapes.Pill)
                                .background(colors.primary),
                        )
                    }
                }
            }

            // Content Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // Title
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Step 3 of 3",
                        style = Type.bodyMd,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        text = "Final details",
                        style = Type.pageTitle,
                        color = colors.onSurface,
                    )
                }

                // Form
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    // Email Input Field
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldLabel("Email Address")
                        PillTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                clearEmailError(
                                    { emailError = it },
                                    { showGoLogin = it },
                                )
                            },
                            placeholder = "name@domain.com",
                            leadingIcon = "mail",
                            keyboardType = KeyboardType.Email, // type="email"
                            imeAction = ImeAction.Next,
                            onAction = { referralFocus.requestFocus() },
                        )
                        AnimatedVisibility(
                            visible = emailError.isNotEmpty(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Column {
                                HintText(emailError)
                                if (showGoLogin) {
                                    val goLoginInteraction = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 4.dp) // mt-[0.25rem]
                                            .height(40.dp) // h-10
                                            .clip(AreenaxShapes.Pill)
                                            .border(1.dp, colors.outlineVariant, AreenaxShapes.Pill)
                                            .clickable(
                                                interactionSource = goLoginInteraction,
                                                indication = null,
                                            ) { env.navigate(ScreenKeys.LOGIN) }
                                            .padding(horizontal = 16.dp), // px-4
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "Go to Login",
                                            style = Type.labelLg.copy(fontWeight = FontWeight.SemiBold),
                                            color = colors.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Referral Code Input Field
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldLabel("Referral Code (Optional)")
                        PillTextField(
                            value = referralCode,
                            onValueChange = { referralCode = it },
                            placeholder = "Enter code if you have one",
                            leadingIcon = "tag",
                            imeAction = ImeAction.Done,
                            onAction = {
                                if (canSubmit(agree, submitting)) {
                                    submitStep3(
                                        env, scope, fullName, gameName, phone, gameUid, password,
                                        email, referralCode, agree,
                                        { hint = it }, { emailError = it }, { showGoLogin = it }, { submitting = it },
                                    )
                                }
                            },
                            focusRequester = referralFocus,
                        )
                    }
                    AnimatedVisibility(
                        visible = hint.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        HintText(hint)
                    }
                    // Agreement Checkbox — required to enable sign-up
                    AgreementRow(
                        agree = agree,
                        onToggle = {
                            agree = !agree
                            if (hint.isNotEmpty()) hint = "" // web clears the hint on toggle
                        },
                        onTerms = { env.navigate(ScreenKeys.TERMS) },
                        onPrivacy = { env.navigate(ScreenKeys.PRIVACY) },
                    )
                    // Action Button — disabled until the agreement checkbox is checked
                    Box(Modifier.padding(top = 12.dp)) {
                        AuthPrimaryButton(
                            label = "Complete Account",
                            busyLabel = "Complete Account",
                            busy = submitting,
                            enabled = agree, // disabled = submitting || !agree
                            withShadow = true,
                            onClick = {
                                if (canSubmit(agree, submitting)) {
                                    submitStep3(
                                        env, scope, fullName, gameName, phone, gameUid, password,
                                        email, referralCode, agree,
                                        { hint = it }, { emailError = it }, { showGoLogin = it }, { submitting = it },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun canSubmit(agree: Boolean, submitting: Boolean): Boolean = agree && !submitting

/** Web clearEmailError — inline error + Go-to-Login pill clear on email edits. */
private fun clearEmailError(setEmailError: (String) -> Unit, setShowGoLogin: (Boolean) -> Unit) {
    setEmailError("")
    setShowGoLogin(false)
}

/**
 * Web handleComplete: agreement + email format gates → POST /auth/check {email}
 * (best-effort) → POST /auth/register → success toast + env.setAuth. 409 →
 * "Already registered" toast; email conflicts also show the inline error +
 * "Go to Login" pill; other fields → inline hint. Other errors → "Registration
 * failed" toast.
 */
private fun submitStep3(
    env: NavEnv,
    scope: CoroutineScope,
    fullName: String?,
    gameName: String?,
    phone: String?,
    gameUid: String?,
    password: String?,
    emailRaw: String,
    referralRaw: String,
    agree: Boolean,
    setHint: (String) -> Unit,
    setEmailError: (String) -> Unit,
    setShowGoLogin: (Boolean) -> Unit,
    setSubmitting: (Boolean) -> Unit,
) {
    if (!agree) {
        setHint("Please agree to the Terms & Conditions and Privacy Policy to continue.")
        return
    }
    val trimmedEmail = emailRaw.trim()
    if (trimmedEmail.isEmpty() || !EMAIL_REGEX.matches(trimmedEmail)) {
        setHint("Please enter a valid email address.")
        return
    }
    setHint("")
    setEmailError("")
    setShowGoLogin(false)
    setSubmitting(true)
    scope.launch {
        // 1. Pre-submit email availability (best-effort — registration 409 is the hard stop)
        val check = safeCall { env.api.authCheck(AuthCheckRequest(email = trimmedEmail)) }
        if (check is ApiResult.Success && check.data.taken["email"] == true) {
            val msg = check.data.messages["email"] ?: "This email address is already registered."
            setEmailError(msg)
            env.toast.show("Email already registered", msg, ToastVariant.Destructive)
            setSubmitting(false)
            return@launch
        }
        // 2. Register with ALL params accumulated through the steps.
        val res = safeCall {
            env.api.register(
                RegisterRequest(
                    fullName = fullName ?: "",
                    gameName = gameName ?: "",
                    phone = phone ?: "",
                    gameUid = gameUid ?: "",
                    password = password ?: "",
                    email = trimmedEmail,
                    // web: referralCode: referralCode.trim() || undefined
                    referralCode = referralRaw.trim().ifEmpty { null },
                ),
            )
        }
        when (res) {
            is ApiResult.Success -> {
                env.toast.show(
                    title = "Account created",
                    description = "Welcome to AREENAX! Your journey starts now.",
                )
                env.setAuth(res.data.token, res.data.user)
            }
            is ApiResult.Error -> {
                if (res.code == 409) {
                    env.toast.show("Already registered", res.message, ToastVariant.Destructive)
                    // The 409 body carries `field` (email|phone|gameName|gameUid);
                    // safeCall exposes only message+code, so email conflicts are
                    // detected via the message — the server's email 409 string is
                    // the only one containing "email" (route: auth/register/route.ts).
                    if (res.message.contains("email", ignoreCase = true)) {
                        setEmailError(res.message)
                        setShowGoLogin(true)
                    } else {
                        // phone / gameName / gameUid — stay on the step so the user
                        // can go back and fix the conflicting value (web behavior).
                        setHint(res.message)
                    }
                } else {
                    env.toast.show("Registration failed", res.message, ToastVariant.Destructive)
                }
            }
            is ApiResult.NetworkError -> {
                env.toast.show("Registration failed", res.message, ToastVariant.Destructive)
            }
        }
        setSubmitting(false) // web finally
    }
}

private val EMAIL_REGEX = Regex("^\\S+@\\S+\\.\\S+$")

/** Tag for the annotated agreement links (Terms & Conditions / Privacy Policy). */
private const val AGREEMENT_LINK_TAG = "agreement_link"

/**
 * The agreement checkbox row — 20dp rounded-[6dp] custom box (checked =
 * bg-primary + white check) + the 13/19 consent text with two tappable links.
 * Tapping a link navigates WITHOUT toggling (web stopPropagation); tapping any
 * other part of the row/text toggles the checkbox, exactly like the web's
 * role="checkbox" wrapper.
 */
@Composable
private fun AgreementRow(
    agree: Boolean,
    onToggle: () -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val extended = areenaColors()
    val rowInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .offset(y = (-2).dp) // -mt-[0.25rem]
            .fillMaxWidth()
            .clickable(interactionSource = rowInteraction, indication = null) { onToggle() },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp), // gap-[0.75rem]
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp) // mt-[0.125rem]
                .size(20.dp) // w-[1.25rem] h-[1.25rem]
                .clip(RoundedCornerShape(6.dp)) // rounded-[0.375rem]
                .background(
                    if (agree) {
                        colors.primary // agree: bg-primary border-primary
                    } else if (extended.isDark) {
                        colors.surfaceContainerHigh // dark:bg-surface-container-high
                    } else {
                        Color.White // bg-white (web literal)
                    },
                )
                .border(
                    1.dp,
                    if (agree) colors.primary else colors.outlineVariant, // border-outline-variant
                    RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (agree) {
                AreenaxIcon(
                    name = "check",
                    contentDescription = null,
                    modifier = Modifier.size(16.dp), // text-[1rem]
                    tint = colors.onPrimary, // text-on-primary
                )
            }
        }
        AgreementText(
            onTerms = onTerms,
            onPrivacy = onPrivacy,
            onOtherTap = onToggle,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AgreementText(
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    onOtherTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val linkStyle = SpanStyle(
        color = colors.primary,
        fontWeight = FontWeight.SemiBold, // font-semibold
        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline, // underline
    )
    val annotated = buildAnnotatedString {
        append("I agree to the ")
        pushStringAnnotation(AGREEMENT_LINK_TAG, "terms")
        withStyle(linkStyle) { append("Terms & Conditions") }
        pop()
        append(" and ")
        pushStringAnnotation(AGREEMENT_LINK_TAG, "privacy")
        withStyle(linkStyle) { append("Privacy Policy") }
        pop()
        append(", and I consent to create an AREENAX account.")
    }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated,
        // text-[0.8125rem] leading-[1.1875rem] text-on-surface-variant
        style = TextStyle(
            fontFamily = HankenGrotesk,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        ),
        color = colors.onSurfaceVariant,
        onTextLayout = { layoutResult = it },
        modifier = modifier.pointerInput(onTerms, onPrivacy) {
            detectTapGestures { position ->
                layoutResult?.let { layout ->
                    val offset = layout.getOffsetForPosition(position)
                    val link = annotated
                        .getStringAnnotations(AGREEMENT_LINK_TAG, offset, offset)
                        .firstOrNull()
                    when (link?.item) {
                        "terms" -> onTerms()
                        "privacy" -> onPrivacy()
                        else -> onOtherTap()
                    }
                }
            }
        },
    )
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
 * The AREENAX auth text field — shared styling (see Step1 for the full class
 * list). Delegates to the shared [AreenaxPillField] (owner field concept):
 * matte charcoal pill in dark mode, leading icon white → vibrant blue on
 * focus, subtle animated 1dp focus ring, bright-white blinking cursor.
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
 * = 16dp white spinner + the SAME label ("Complete Account" while submitting).
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
