package com.areenax.app.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.R
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.theme.DarkInversePrimary
import com.areenax.app.core.theme.LightPrimary
import com.areenax.app.core.theme.primaryDeep
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AreenaxPillField
import com.areenax.app.core.ui.AreenaxSpinner
import com.areenax.app.core.ui.BellButton
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.GuestGateDialog
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.cardShadow
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.primaryGlow
import com.areenax.app.core.ui.rememberGuestGate
import com.areenax.app.data.TeamCreateRequest
import kotlinx.coroutines.launch

/*
 * TeamCreationScreen — key `teamCreation` (SPEC/01 D2, build record 3-d):
 * shield "Team Name" input (maxLength 24, error state), optional UPPERCASE
 * max-4 Team Tag, crown LEADER (YOU) card w/ gameName + UID + Leader chip,
 * "Squad Members" card with numbered Player UID inputs — add/remove 1–3 via
 * the dashed "Add member by UID" row and per-field X — country/in-app-invite
 * note, CTA "Create Team" → POST /team → navigate(teamCreationDone,
 * {teamName}). "already in team" server error → destructive toast.
 */

@Composable
fun TeamCreationScreen(env: NavEnv) {
    val user by env.session.user.collectAsState()
    val extended = areenaColors()

    var name by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    val uids = remember { mutableStateListOf("") }
    var nameError by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    val gate = rememberGuestGate()
    val scope = rememberCoroutineScope()

    fun submit() {
        if (name.trim().isEmpty()) {
            nameError = true
            env.toast("Team name is required", ToastVariant.Destructive)
            return
        }
        if (submitting) return
        if (!gate.requireAccount(user?.isGuest == true, "create a team")) return
        submitting = true
        scope.launch {
            when (val res = safeCall {
                env.api.createTeam(
                    TeamCreateRequest(
                        name = name.trim(),
                        tag = tag.trim().uppercase().takeIf { it.isNotEmpty() },
                        memberUids = uids.map { it.trim() }.filter { it.isNotEmpty() },
                    ),
                )
            }) {
                is ApiResult.Success -> {
                    env.navigate(ScreenKeys.TEAM_CREATION_DONE, mapOf("teamName" to name.trim()))
                }
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    // includes "You already belong to a team. Leave it first."
                    env.toast.show(
                        "Failed to create team",
                        description = (res as? ApiResult.Error)?.message ?: (res as? ApiResult.NetworkError)?.message,
                        variant = ToastVariant.Destructive,
                    )
                }
            }
            submitting = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppBar(
            title = "My Team",
            right = { BellButton() },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ===== Team Name =====
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_inline_teamcreation_shield_check),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Team Name",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                PillTextField(
                    value = name,
                    onValueChange = {
                        if (it.length <= 24) {
                            name = it
                            if (it.trim().isNotEmpty()) nameError = false
                        }
                    },
                    placeholder = "e.g. Phoenix Squad",
                    leadingIcon = "groups",
                    isError = nameError,
                )
                if (nameError) {
                    Text(
                        text = "Please enter a team name to continue.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            // ===== Team Tag (optional, UPPERCASE, max 4) =====
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    AreenaxIcon(
                        name = "tag",
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Team Tag (optional)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                PillTextField(
                    value = tag,
                    onValueChange = { next ->
                        tag = next.filter { c -> c.isLetterOrDigit() }.uppercase().take(4)
                    },
                    placeholder = "e.g. PXQ",
                    leadingIcon = "tag",
                    keyboardType = KeyboardType.Ascii,
                )
            }

            // ===== LEADER (YOU) =====
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    AreenaxIcon(
                        name = "crown",
                        contentDescription = null,
                        filled = true,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "LEADER (YOU)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            // border-l-4 border-primary
                            drawRect(
                                color = LightPrimary,
                                topLeft = Offset(0f, 0f),
                                size = Size(4.dp.toPx(), size.height),
                            )
                        }
                        .cardShadow(RoundedCornerShape(16.dp)),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val leader = user
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .border(
                                    2.dp,
                                    extended.primaryFixed, // A8-06
                                    CircleShape,
                                )
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(LightPrimary, primaryDeep),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = (leader?.gameName ?: "U").firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = leader?.gameName?.ifBlank { "username" } ?: "username",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.width(6.dp))
                                // presence dot (bg-secondary)
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondary),
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "UID: ${leader?.uid?.ifBlank { "—" } ?: "—"}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = extended.primaryFixed, // A8-06
                        ) {
                            Text(
                                text = "Leader",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = if (extended.isDark) DarkInversePrimary else primaryDeep,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            // ===== Squad Members =====
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "SQUAD MEMBERS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = CircleShape,
                        color = extended.primaryFixed.copy(alpha = 0.6f), // A8-06
                    ) {
                        Text(
                            text = "3 Players Required",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .cardShadow(RoundedCornerShape(24.dp)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        uids.forEachIndexed { i, uidValue ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = (i + 1).toString(),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Player ${i + 1} UID",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    if (uids.size > 1) {
                                        // X remove (fields 2..3)
                                        Surface(
                                            onClick = { uids.removeAt(i) },
                                            shape = CircleShape,
                                            color = Color.Transparent,
                                        ) {
                                            AreenaxIcon(
                                                name = "close",
                                                contentDescription = "Remove player ${i + 1}",
                                                modifier = Modifier
                                                    .padding(4.dp)
                                                    .size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                PillTextField(
                                    value = uidValue,
                                    onValueChange = { next -> uids[i] = next.filter { c -> c.isDigit() } },
                                    placeholder = "Enter player UID",
                                    leadingIcon = "person_search",
                                    keyboardType = KeyboardType.Number,
                                )
                            }
                        }

                        if (uids.size < 3) {
                            // dashed "Add member by UID"
                            val addInteraction = remember { MutableInteractionSource() }
                            val dashColor = MaterialTheme.colorScheme.outlineVariant
                            Surface(
                                onClick = { uids.add("") },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Transparent,
                                interactionSource = addInteraction,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .drawBehind {
                                        drawRoundRect(
                                            color = dashColor,
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                                            style = Stroke(
                                                width = 1.5.dp.toPx(),
                                                pathEffect = PathEffect.dashPathEffect(
                                                    floatArrayOf(10f, 8f),
                                                ),
                                            ),
                                        )
                                    }
                                    .pressScale(addInteraction, 0.98f),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AreenaxIcon(
                                        name = "add",
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Add member by UID",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ===== Action area =====
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "All members must be from the same country and will receive an in-app invite.",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                val submitInteraction = remember { MutableInteractionSource() }
                Button(
                    onClick = { submit() },
                    enabled = !submitting,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    interactionSource = submitInteraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .primaryGlow(CircleShape)
                        .pressScale(submitInteraction, 0.98f),
                ) {
                    if (submitting) {
                        AreenaxSpinner(size = 16, strokeWidth = 2)
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_inline_teamcreation_send),
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer {
                                    rotationZ = -45f
                                    translationY = -2.dp.toPx()
                                },
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (submitting) "Creating Team…" else "Create Team",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp,
                    )
                }
            }
        }
    }

    GuestGateDialog(gate, env)
}

/**
 * Rounded-full input — web `INPUT_CLS`: h-12, bg-surface-container-lavender,
 * border outline-variant (error → error), body-lg text, pill shape.
 * Delegates to the shared [AreenaxPillField] (owner field concept): matte
 * charcoal pill in dark mode, leading icon white → vibrant blue on focus,
 * error ring/icon, and the Ascii → all-caps capitalization passed through.
 */
@Composable
private fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: String? = null,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    AreenaxPillField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        isError = isError,
        keyboardType = keyboardType,
        capitalization = if (keyboardType == KeyboardType.Ascii) {
            KeyboardCapitalization.Characters
        } else {
            KeyboardCapitalization.Unspecified
        },
        imeAction = ImeAction.Done,
    )
}
