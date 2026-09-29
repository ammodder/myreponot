package com.areenax.app.ui.screens.wallet

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.session.SettingsCache
import com.areenax.app.core.theme.HankenGrotesk
import com.areenax.app.core.theme.Type
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.GuestGateDialog
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.areenaxFieldColors
import com.areenax.app.core.ui.cardShadow
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.primaryGlow
import com.areenax.app.core.ui.rememberGuestGate
import com.areenax.app.core.util.formatMoney
import com.areenax.app.data.TransferRequest
import kotlinx.coroutines.launch

/*
 * TransferScreen — 1:1 port of src/components/screens/wallet/TransferScreen.tsx
 * (SPEC/01 C8, key `transferMoney`, HTML transfermoney.html).
 *
 *  - AppBar "Transfer Money" (page mode: back circle + centered title + bell,
 *    the unified header the real web app renders; material icons per web).
 *  - SourceAccountCard: primary initial circle, fullName, "**** **** «uid»",
 *    "Rs  «formatMoney(balance)»" right.
 *  - RecipientCard "Send to:": empty → person_search pill input "Enter UID or
 *    email"; set → initial avatar + recipient + "Player UID / email" + Change
 *    (clears).
 *  - InputSection "Enter amount" 36px figure + pulsing cursor; Note textarea
 *    "What is this for?" (label "Note (optional)").
 *  - Keypad per transfermoney.html (white rounded-2xl py-4 text-xl keys) with
 *    the EXACT inline-SVG backspace drawn from the heroicon path data.
 *  - Validations (recipient / amount / balance) → POST /wallet/transfer
 *    {recipient, amount, note?} → setUser(balance) → transferSuccess
 *    {amount, receiver, reference}; errors → "Transfer failed" toast.
 */

/** Exact heroicon "delete" stroke path from transfermoney.html (24×24, stroke 1.5). */
private const val TRANSFER_BACKSPACE_PATH: String =
    "M12 9.75L14.25 12m0 0l2.25 2.25M14.25 12l2.25-2.25M14.25 12L12 14.25" +
        "m-2.58 4.92l-6.375-6.375a1.125 1.125 0 010-1.59L9.42 4.83c.211-.211.498-.33.796-.33" +
        "H19.5a2.25 2.25 0 012.25 2.25v10.5a2.25 2.25 0 01-2.25 2.25h-9.284c-.298 0-.585-.119-.796-.33z"

private val TransferAmountStyle = Type.heroAmount.copy(fontSize = 36.sp, lineHeight = 40.sp)

@Composable
fun TransferScreen(env: NavEnv) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val user by env.session.user.collectAsState()
    val gate = rememberGuestGate() // A4-06: confirm action was ungated for guests
    var maxTransfer by remember { mutableStateOf(50_000.0) } // A4-03: server default 50000

    var recipient by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("0") }
    var submitting by rememberSaveable { mutableStateOf(false) }

    // A4-03: read the server's max-transfer bound from cached bootstrap settings.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        SettingsCache.get { env.api }?.let { s ->
            if (s.maxTransfer > 0) maxTransfer = s.maxTransfer
        }
    }

    val handleKey: (String) -> Unit = { key ->
        amount = if (key == "backspace") walletDeleteAmount(amount) else walletAppendAmount(amount, key)
    }

    val onTransferClick: () -> Unit = {
        val value = amount.toDoubleOrNull()
        when {
            submitting -> Unit
            recipient.trim().isEmpty() -> env.toast.show(
                "Recipient required",
                description = "Enter the player UID or email to send money.",
                variant = ToastVariant.Destructive,
            )
            value == null || value <= 0 -> env.toast.show(
                "Enter an amount",
                description = "Please enter a valid transfer amount.",
                variant = ToastVariant.Destructive,
            )
            value > maxTransfer -> env.toast.show(
                "Amount too high",
                description = "Maximum transfer Rs ${formatMoney(maxTransfer)}", // A4-03
                variant = ToastVariant.Destructive,
            )
            user != null && value > (user?.balance ?: 0.0) -> env.toast.show(
                "Insufficient balance",
                description = "Available balance is Rs ${formatMoney(user?.balance)}",
                variant = ToastVariant.Destructive,
            )
            else -> {
                submitting = true
                val body = TransferRequest(
                    recipient = recipient.trim(),
                    amount = value,
                    note = note.trim().takeIf { it.isNotEmpty() },
                )
                scope.launch {
                    when (val res = safeCall { env.api.transfer(body) }) {
                        is ApiResult.Success -> {
                            // Web: setUser({...u, balance: res.balance})
                            env.session.user.value?.let { u ->
                                res.data.balance?.let { b -> env.session.setUser(u.copy(balance = b)) }
                            }
                            val receiver = res.data.receiver?.takeIf { it.isNotBlank() } ?: recipient.trim()
                            env.navigate(
                                ScreenKeys.TRANSFER_SUCCESS,
                                mapOf(
                                    "amount" to value,
                                    "receiver" to receiver,
                                    "reference" to res.data.reference,
                                ),
                            )
                        }
                        is ApiResult.Error -> env.toast.show(
                            "Transfer failed",
                            description = res.message,
                            variant = ToastVariant.Destructive,
                        )
                        is ApiResult.NetworkError -> env.toast.show(
                            "Transfer failed",
                            description = res.message,
                            variant = ToastVariant.Destructive,
                        )
                    }
                    submitting = false
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        AppBar(title = "Transfer Money")

        // A4-06: guests must see the AccountRequiredDialog like every other
        // money flow — the confirm action used to hit a raw server 403.
        GuestGateDialog(gate, env)

        // Scrollable Content Area (px-6 pb-32 gap-6)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp) // px-6
                .padding(bottom = 128.dp, top = 8.dp) // pb-32 + mt-2 card offset
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(24.dp), // gap-6
        ) {
            // ---- SourceAccountCard ----
            Surface(
                shape = RoundedCornerShape(24.dp), // rounded-[1.5rem]
                color = colors.surfaceContainerLowest,
                modifier = Modifier
                    .fillMaxWidth()
                    .cardShadowPill(),
            ) {
                        Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp), // p-5
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Initial circle (avatar fallback: first char of name)
                        Box(
                            Modifier
                                .size(48.dp) // w-12 h-12
                                .clip(CircleShape)
                                .background(colors.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = ((user?.fullName ?: user?.gameName ?: "U").trim().ifEmpty { "U" }
                                    .firstOrNull() ?: 'U').uppercaseChar().toString(),
                                style = TextStyle(
                                    fontFamily = HankenGrotesk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp, // text-xl
                                ),
                                color = colors.onPrimary,
                            )
                        }
                        Column {
                            Text(
                                text = user?.fullName?.takeIf { it.isNotEmpty() } ?: "Player",
                                style = Type.bodyLg.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), // text-lg font-bold
                                color = colors.onSurface,
                            )
                            Text(
                                text = "**** **** ${user?.uid ?: "uid"}",
                                style = Type.bodyMd, // text-sm
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp), // mt-0.5
                            )
                        }
                    }
                    Text(
                        text = "Rs\u00A0 ${formatMoney(user?.balance)}", // web "Rs&nbsp; "
                        style = Type.labelMd.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium), // text-xs font-medium
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            // ---- RecipientCard ----
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceContainerLowest,
                modifier = Modifier
                    .fillMaxWidth()
                    .cardShadowPill(),
            ) {
                Column(Modifier.padding(20.dp)) { // p-5
                    Text(
                        text = "Send to:",
                        style = Type.bodyMd.copy(fontWeight = FontWeight.Medium), // text-[0.875rem] font-medium
                        color = colors.onSurface,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp), // ml-2 mb-2
                    )
                    if (recipient.trim().isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(colors.primary),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = recipient.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                        style = TextStyle(
                                            fontFamily = HankenGrotesk,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp, // text-lg
                                        ),
                                        color = colors.onPrimary,
                                    )
                                }
                                Column {
                                    Text(
                                        text = recipient.trim(),
                                        style = Type.bodyLg.copy(fontWeight = FontWeight.SemiBold), // text-base font-semibold
                                        color = colors.onSurface,
                                    )
                                    Text(
                                        text = "Player UID / email",
                                        style = Type.bodyMd,
                                        color = colors.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                            // Change button — clears the recipient
                            val changeInteraction = remember { MutableInteractionSource() }
                            Box(
                                Modifier
                                    .pressScale(changeInteraction, pressedScale = 0.97f)
                                    .clip(CircleShape)
                                    .background(colors.surfaceContainer)
                                    .border(1.dp, colors.outlineVariant.copy(alpha = 0.3f), CircleShape)
                                    .clickable(interactionSource = changeInteraction, indication = null) {
                                        recipient = ""
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp), // px-4 py-2
                            ) {
                                Text(
                                    text = "Change",
                                    style = Type.bodyMd.copy(fontWeight = FontWeight.Medium),
                                    color = colors.onSurface,
                                )
                            }
                        }
                    } else {
                        WalletPillTextField(
                            value = recipient,
                            onValueChange = { recipient = it },
                            placeholder = "Enter UID or email",
                            leadingIcon = "person_search",
                        )
                    }
                }
            }

            // ---- InputSection (Enter amount) ----
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceContainerLowest,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp) // min-h-[10.0rem]
                    .cardShadowPill(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp) // p-8
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Enter amount",
                        style = Type.bodyMd.copy(fontWeight = FontWeight.Medium),
                        color = colors.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp), // mb-2
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = amount,
                            style = TransferAmountStyle,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                        )
                        // Blinking cursor — w-0.5 h-10 bg-primary ml-1 animate-pulse
                        val transition = rememberInfiniteTransition(label = "transferCursor")
                        val cursorAlpha by transition.animateFloat(
                            initialValue = 1f,
                            targetValue = 0.4f,
                            animationSpec = infiniteRepeatable(
                                animation = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "transferCursorAlpha",
                        )
                        Box(
                            Modifier
                                .padding(start = 4.dp) // ml-1
                                .size(width = 2.dp, height = 40.dp) // w-0.5 h-10
                                .alpha(cursorAlpha)
                                .clip(CircleShape)
                                .background(colors.primary),
                        )
                    }
                }
            }

            // ---- Note ----
            var noteFocused by remember { mutableStateOf(false) }
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceContainerLowest,
                modifier = Modifier
                    .fillMaxWidth()
                    .cardShadowPill(),
            ) {
                Column(Modifier.padding(20.dp)) { // p-5
                    Text(
                        text = "Note (optional)",
                        style = Type.bodyMd.copy(fontWeight = FontWeight.Medium),
                        color = colors.onSurface,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp), // ml-2 mb-2
                    )
                    // Owner field concept (multiline variant): animated charcoal
                    // container + 1dp focus ring via areenaxFieldColors; no icon.
                    val noteColors = areenaxFieldColors(noteFocused)
                    BasicTextField(
                        value = note,
                        onValueChange = { note = it },
                        textStyle = Type.bodyLg.copy(color = noteColors.text),
                        cursorBrush = SolidColor(noteColors.cursor),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 110.dp) // min-h-[6.875rem]
                                    .clip(RoundedCornerShape(16.dp)) // rounded-2xl
                                    .background(noteColors.container)
                                    .border(1.dp, noteColors.ring, RoundedCornerShape(16.dp))
                                    .onFocusChanged { noteFocused = it.hasFocus }
                                    .padding(horizontal = 16.dp, vertical = 12.dp), // px-4 py-3
                            ) {
                                Box(Modifier.weight(1f)) {
                                    if (note.isEmpty()) {
                                        Text(
                                            text = "What is this for?",
                                            style = Type.bodyLg,
                                            color = noteColors.placeholder,
                                        )
                                    }
                                    inner()
                                }
                            }
                        },
                    )
                }
            }

            // ---- NumericKeypad (transfermoney.html style + exact backspace) ----
            Column(
                Modifier
                    .padding(top = 24.dp) // pt-6
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp), // gap-3
            ) {
                val keypadKeys = listOf(
                    "1", "2", "3",
                    "4", "5", "6",
                    "7", "8", "9",
                    ".", "0", "backspace",
                )
                keypadKeys.chunked(3).forEach { rowKeys ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowKeys.forEach { key ->
                            TransferKey(key = key, modifier = Modifier.weight(1f), onClick = { handleKey(key) })
                        }
                    }
                }
            }

            // ---- MainCTA ----
            val ctaInteraction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 8.dp) // pt-6 mb-2
                    .height(56.dp) // h-[3.5rem]
                    .primaryGlow(RoundedCornerShape(50))
                    .pressScale(ctaInteraction, pressedScale = 0.98f)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = if (submitting) 0.6f else 1f)) // disabled:opacity-60
                    .clickable(
                        interactionSource = ctaInteraction,
                        indication = null,
                        enabled = !submitting,
                        onClick = {
                            // A4-06: guest gate before the submit branch
                            if (gate.requireAccount(user?.isGuest == true, "send money")) {
                                onTransferClick()
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Transfer Money",
                    style = Type.labelLg,
                    color = colors.onPrimary,
                )
            }
        }
    }
}

/** Keypad key per transfermoney.html: white rounded-2xl py-4 text-xl font-medium. */
@Composable
private fun TransferKey(key: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interaction, pressedScale = 0.95f)
            .clip(RoundedCornerShape(16.dp)) // rounded-2xl
            .background(colors.surfaceContainerLowest)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 16.dp) // py-4
            .heightIn(min = 56.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (key == "backspace") {
            HeroiconDelete(
                modifier = Modifier.size(24.dp), // w-6 h-6
                tint = colors.onSurfaceVariant, // text-app-textSecondary
            )
        } else {
            Text(
                text = key,
                style = TextStyle(
                    fontFamily = HankenGrotesk,
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp, // text-xl
                ),
                color = colors.onSurface,
            )
        }
    }
}

/**
 * The transfer keypad's EXACT inline-SVG backspace (heroicon outline "delete",
 * stroke 1.5, round caps/joins — path data copied from transfermoney.html).
 */
@Composable
private fun HeroiconDelete(modifier: Modifier = Modifier, tint: Color) {
    val path: Path = remember {
        PathParser().parsePathString(TRANSFER_BACKSPACE_PATH).toPath()
    }
    Box(
        modifier.drawBehind {
            val scale = size.minDimension / 24f
            withTransform2(scale) {
                drawPath(
                    path = path,
                    color = tint,
                    style = Stroke(
                        width = 1.5f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        },
    )
}

/** scale + translate helper for the 24×24 viewBox path. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.withTransform2(
    scale: Float,
    block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    drawContext.canvas.apply {
        save()
        scale(scale, scale)
        block()
        restore()
    }
}

/** `.card-shadow` on a rounded-24 card. */
private fun Modifier.cardShadowPill(): Modifier = cardShadow(RoundedCornerShape(24.dp))
