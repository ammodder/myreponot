package com.areenax.app.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.areenax.app.R
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.theme.AreenaxShapes
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AreenaxPillField
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.primaryGlow

/**
 * SignupStep2Screen — key `signupStep2` (SPEC/01 A4; behavior truth =
 * auth/SignupStep2Screen.tsx, pixel truth = signupstep2.html).
 *
 *  - Progress bar **2/3** → "Step 2 of 3" + "Create a password".
 *  - Password pill input ("Enter a secure password") with the eye visibility
 *    toggle (`visibility` FILL'1 when visible / `visibility_off`).
 *  - Live checklist ("Your password must contain at least"):
 *      "1 letter"                                        /[A-Za-z]/
 *      "1 number or special character (example: # ? ! &)" /\d|[^A-Za-z0-9\s]/
 *      "10 characters"                                   length >= 10
 *    satisfied = `check_circle` + text-secondary; unsatisfied =
 *    `radio_button_unchecked` + on-surface-variant — exact web colors.
 *  - Continue validates all → navigate(signupStep3, {..., password}); otherwise
 *    hint "Your password does not meet all the requirements yet."
 *  - Back (arrow + system back) → goBack. No API calls (client validation only).
 */
@Composable
fun SignupStep2Screen(env: NavEnv) {
    // Read params ONCE (web: params as {fullName?, gameName?, phone?, gameUid?}).
    val fullName = remember { env.params["fullName"] as? String }
    val gameName = remember { env.params["gameName"] as? String }
    val phone = remember { env.params["phone"] as? String }
    val gameUid = remember { env.params["gameUid"] as? String }

    val extended = areenaColors()
    val colors = MaterialTheme.colorScheme

    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var hint by rememberSaveable { mutableStateOf("") }

    // Live requirement checks (web regexes verbatim).
    val hasLetter = Regex("[A-Za-z]").containsMatchIn(password)
    val hasNumberOrSpecial = Regex("\\d|[^A-Za-z0-9\\s]").containsMatchIn(password)
    val has10Chars = password.length >= 10
    // A4-09: the server policy also caps at 128 chars (password-policy.ts) —
    // surface it so client-passes/server-fails can't happen on this rule.
    val hasMax128 = password.length <= 128
    val allValid = hasLetter && hasNumberOrSpecial && has10Chars && hasMax128

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
                // back arrow + progress bar (2/3)
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
                                .fillMaxWidth(2f / 3f) // w-2/3
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
                        text = "Step 2 of 3",
                        style = Type.bodyMd,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        text = "Create a password",
                        style = Type.pageTitle,
                        color = colors.onSurface,
                    )
                }

                // Form — animateContentSize mirrors the web's transition-all easing
                // when the hint expands/collapses under the checklist.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    // Password Input
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldLabel("Password")
                        PillTextField(
                            value = password,
                            onValueChange = { password = it },
                            placeholder = "Enter a secure password",
                            leadingIcon = "lock",
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                            onAction = { submitStep2(env, fullName, gameName, phone, gameUid, password, allValid, { hint = it }) },
                            visualTransformation = if (showPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailing = {
                                // p-2 rounded-full toggle — 'Toggle password visibility'
                                val pwInteraction = remember { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp) // p-2 + 20dp glyph
                                        .clip(CircleShape)
                                        .clickable(interactionSource = pwInteraction, indication = null) {
                                            showPassword = !showPassword
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AreenaxIcon(
                                        name = if (showPassword) "visibility" else "visibility_off",
                                        contentDescription = "Toggle password visibility",
                                        modifier = Modifier.size(20.dp), // text-[1.25rem]
                                        // Step-2 web toggle has NO 'FILL' 1 style (unlike
                                        // the login eye) — always the outlined glyphs.
                                        filled = false,
                                        tint = colors.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                    // Validation Checklist — gap-[0.75rem]
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Your password must contain at least",
                            style = Type.labelLg,
                            color = colors.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp), // mb-[0.5rem]
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { // ul gap-[0.75rem]
                            ChecklistItem(
                                label = "1 letter",
                                ok = hasLetter,
                            )
                            ChecklistItem(
                                label = "1 number or special character (example: # ? ! &)",
                                ok = hasNumberOrSpecial,
                            )
                            ChecklistItem(
                                label = "10 characters",
                                ok = has10Chars,
                            )
                            ChecklistItem(
                                label = "At most 128 characters", // A4-09
                                ok = hasMax128,
                            )
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
                            busyLabel = "Continue",
                            busy = false,
                            enabled = true,
                            withShadow = true,
                            onClick = { submitStep2(env, fullName, gameName, phone, gameUid, password, allValid, { hint = it }) },
                        )
                    }
                }
            }
        }
    }
}

/** Web handleContinue — all requirements met → signupStep3 with the password. */
private fun submitStep2(
    env: NavEnv,
    fullName: String?,
    gameName: String?,
    phone: String?,
    gameUid: String?,
    password: String,
    allValid: Boolean,
    setHint: (String) -> Unit,
) {
    if (!allValid) {
        setHint("Your password does not meet all the requirements yet.")
        return
    }
    setHint("")
    env.navigate(
        ScreenKeys.SIGNUP_STEP3,
        mapOf(
            "fullName" to fullName,
            "gameName" to gameName,
            "phone" to phone,
            "gameUid" to gameUid,
            "password" to password,
        ),
    )
}

/** One live checklist row — check_circle/secondary when satisfied, else radio/muted. */
@Composable
private fun ChecklistItem(label: String, ok: Boolean) {
    val colors = MaterialTheme.colorScheme
    val itemColor = if (ok) colors.secondary else colors.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp), // gap-[0.75rem]
    ) {
        AreenaxIcon(
            name = if (ok) "check_circle" else "radio_button_unchecked",
            contentDescription = null,
            modifier = Modifier.size(18.dp), // text-[1.125rem]
            tint = itemColor,
        )
        Text(
            text = label,
            style = Type.bodyMd,
            color = itemColor,
        )
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
        trailing = trailing,
    )
}

/**
 * Primary auth CTA — `rounded-full bg-primary text-on-primary font-label-lg
 * active:scale-[0.98]` + `shadow-[0_8px_20px_rgba(0,74,198,0.25)]`. Busy state
 * = 16dp white spinner + the busy label (unused on this screen — no API call).
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = colors.onPrimary,
                    trackColor = colors.onPrimary.copy(alpha = 0.4f),
                )
                Text(text = busyLabel, style = Type.labelLg, color = colors.onPrimary)
            }
        } else {
            Text(text = label, style = Type.labelLg, color = colors.onPrimary)
        }
    }
}
