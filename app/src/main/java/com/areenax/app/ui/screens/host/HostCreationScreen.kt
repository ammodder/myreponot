package com.areenax.app.ui.screens.host

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.areenax.app.R
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AreenaxPillField
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.BellButton
import com.areenax.app.core.ui.DateTimePickerModal
import com.areenax.app.core.ui.GuestGateDialog
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.UploadCaps
import com.areenax.app.core.ui.areenaxFieldColors
import com.areenax.app.core.ui.primaryGlow
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.rememberGuestGate
import com.areenax.app.core.ui.rememberImagePicker
import com.areenax.app.core.util.formatDisplayDate
import com.areenax.app.core.util.formatMoney
import com.areenax.app.data.CreateTournamentRequest
import com.areenax.app.data.Game
import com.areenax.app.data.GameMode
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * HostCreationScreen — key `hostTournamentCreation` (SPEC/01 F2; web
 * HostCreationScreen.tsx, pixel truth hosttournamentcreation.html).
 *
 * FULL creation form: Tournament Name, Date & Time (core DateTimePickerModal),
 * Match Type (Admin-managed modes via GET /games/config — selection fixes Slots),
 * Entry Fee (Rs.), Slots (disabled, auto-filled), Rules (centered modal),
 * optional Tournament Image (≤5 MB base64 via core rememberImagePicker),
 * live CALCULATIONS PREVIEW (collection = fee × slots; pool = 50%; ranks
 * 50/30/20; refund per loser), and the "Host Tournament" CTA (guest gate →
 * POST /tournaments → toast → hostTournamentSuccess {tournamentId, name}).
 */
import com.areenax.app.core.util.TournamentPrizeCalculationService

private fun calculateTop9Breakdown(slots: Int, fee: Double): TournamentPrizeCalculationService.Breakdown {
    return TournamentPrizeCalculationService.breakdown(slots, fee)
}

@Composable
fun HostCreationScreen(env: NavEnv) {
    val paramGameId = env.params["gameId"] as? String
    val paramGameName = env.params["gameName"] as? String

    var games by remember { mutableStateOf<List<Game>>(emptyList()) }
    var gameId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("") }
    var map by remember { mutableStateOf("") }
    var perspective by remember { mutableStateOf("") }
    var entryFee by remember { mutableStateOf("50") }
    var maxPlayers by remember { mutableStateOf("") }
    var rules by remember { mutableStateOf("") }
    var rulesDraft by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf<String?>(null) }
    var imageName by remember { mutableStateOf<String?>(null) }

    var matchTypeOpen by remember { mutableStateOf(false) }
    var dateOpen by remember { mutableStateOf(false) }
    var rulesOpen by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf(mapOf<String, String>()) }
    var submitting by remember { mutableStateOf(false) }

    val gate = rememberGuestGate()
    val scope = rememberCoroutineScope()
    val extended = areenaColors()
    val inputContainer =
        if (extended.isDark) MaterialTheme.colorScheme.surfaceContainer else extended.surfaceContainerLavender

    val picker = rememberImagePicker(UploadCaps.HOST_IMAGE) { picked ->
        if (picked != null) {
            imageUrl = picked.dataUrl
            imageName = picked.fileName
        }
    }

    LaunchedEffect(Unit) {
        // GET /games/config — failures are ignored (game list optional, like the web)
        when (val res = safeCall { env.api.gamesConfig() }) {
            is ApiResult.Success -> games = res.data.games
            else -> Unit
        }
    }

    val selectedGame = games.find { it.id == gameId }
    val gameModes = selectedGame?.modes ?: emptyList()

    // Auto-select the tapped game (or the first Admin game) and silently apply
    // its Admin defaults — first match type (with its fixed slots), first map,
    // first perspective. No game/map/perspective pickers on this page.
    LaunchedEffect(games, paramGameId) {
        if (games.isEmpty()) return@LaunchedEffect
        val target = games.find { it.id == paramGameId } ?: games.first()
        if (gameId != target.id) {
            mode = target.modes.firstOrNull()?.name ?: ""
            maxPlayers = target.modes.firstOrNull()?.slots?.toString() ?: ""
            map = target.maps.firstOrNull()?.name ?: ""
            perspective = target.perspectives.firstOrNull()?.name ?: ""
            gameId = target.id
        }
    }

    // ---- Automatic tournament economics (the app's fixed rule) ----------------
    val slots = maxPlayers.toIntOrNull() ?: 0
    val feeNum = entryFee.toDoubleOrNull() ?: 0.0
    val breakdown = calculateTop9Breakdown(slots, feeNum)
    val collection = breakdown.collectedEntryPool
    val prizePool = breakdown.top9PrizePool
    val loserPrize = breakdown.consolationPerUser

    fun clearError(key: String) {
        errors = errors - key
    }

    val selectMatchType: (String, Int) -> Unit = { modeName, modeSlots ->
        mode = modeName
        maxPlayers = modeSlots.toString()
        errors = errors - "mode" - "maxPlayers"
        scope.launch {
            delay(200)
            matchTypeOpen = false
        }
    }

    fun submit() {
        if (gameId.isBlank()) {
            env.toast.show(
                title = "Game not ready",
                description = "Games are still loading — please try again in a moment.",
            )
            return
        }
        val e = mutableMapOf<String, String>()
        if (name.isBlank()) e["name"] = "Tournament name is required"
        if (startTime.isBlank()) e["startTime"] = "Date & time is required"
        if (mode.isBlank()) e["mode"] = "Match type is required"
        val fee = entryFee.toDoubleOrNull()
        if (entryFee.isBlank() || fee == null || fee < 0) {
            e["entryFee"] = "Entry fee must be 0 or more"
        }
        errors = e
        if (e.isNotEmpty()) return

        scope.launch {
            submitting = true
            val selectedGame = games.firstOrNull { it.id == gameId }
            val body = CreateTournamentRequest(
                gameId = gameId,
                name = name.trim(),
                mode = mode,
                map = map,
                perspective = perspective,
                entryFee = fee ?: 0.0,
                prizePool = prizePool,
                loserPrize = loserPrize,
                maxPlayers = slots,
                rules = rules,
                startTime = toUtcIso(startTime),
                image = selectedGame?.image?.takeIf { it.isNotBlank() },
            )
            when (val res = safeCall { env.api.createTournament(body) }) {
                is ApiResult.Success -> {
                    env.toast.show(
                        title = "Tournament hosted!",
                        description = "Your tournament is now live for players to join.",
                    )
                    env.navigate(
                        ScreenKeys.HOST_TOURNAMENT_SUCCESS,
                        mapOf(
                            "tournamentId" to res.data.tournament.id,
                            "name" to res.data.tournament.name,
                        ),
                    )
                }
                is ApiResult.Error -> env.toast.show(
                    title = "Failed to host tournament",
                    description = res.message,
                    variant = ToastVariant.Destructive,
                )
                is ApiResult.NetworkError -> env.toast.show(
                    title = "Failed to host tournament",
                    description = res.message,
                    variant = ToastVariant.Destructive,
                )
            }
            submitting = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent),
    ) {
        AppBar(
            title = selectedGame?.name ?: paramGameName ?: "Create Tournament",
            right = { BellButton() },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 96.dp), // pb-24
            verticalArrangement = Arrangement.spacedBy(24.dp), // gap-6
        ) {
            // ---- Form Card ----------------------------------------------------
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        RoundedCornerShape(24.dp),
                    )
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(
                    text = "Create your Tournament",
                    style = Type.headlineMd,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Tournament Name
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CreationLabel("Tournament Name")
                        CreationTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = "e.g. Creator Battle",
                            leadingIcon = "edit",
                        )
                        errors["name"]?.let { CreationError(it) }
                    }

                    // Date & Time — opens the core DateTimePickerModal
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CreationLabel("Date & Time")
                        Surface(
                            onClick = { dateOpen = true },
                            shape = RoundedCornerShape(percent = 50),
                            color = inputContainer,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (startTime.isNotBlank()) {
                                        formatDisplayDate(startTime)
                                    } else {
                                        "Select Date and Time"
                                    },
                                    style = Type.bodyLg,
                                    color = if (startTime.isNotBlank()) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    painter = painterResource(R.drawable.ic_calendar_month),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        errors["startTime"]?.let { CreationError(it) }
                    }

                    // Match Type — options come ONLY from the Admin Panel for this game
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CreationLabel("Match Type")
                        Surface(
                            onClick = { matchTypeOpen = true },
                            shape = RoundedCornerShape(percent = 50),
                            color = inputContainer,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = mode.ifBlank { "Select Match Type" },
                                    style = Type.bodyLg,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    painter = painterResource(R.drawable.ic_expand_more),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        errors["mode"]?.let { CreationError(it) }
                    }

                    // Entry Fee (Rs.)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CreationLabel("Entry Fee (Rs.)")
                        CreationTextField(
                            value = entryFee,
                            onValueChange = { entryFee = it },
                            placeholder = null,
                            leadingIcon = "payments",
                            numeric = true,
                        )
                        errors["entryFee"]?.let { CreationError(it) }
                    }

                    // Slots — fixed by the Admin Panel for the selected match type
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CreationLabel("Slots")
                        CreationTextField(
                            value = maxPlayers,
                            onValueChange = {},
                            placeholder = null,
                            leadingIcon = "groups",
                            enabled = false,
                            numeric = true,
                        )
                    }

                    // Rules — opens the centered rules modal
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            rulesDraft = rules
                            rulesOpen = true
                        },
                    ) {
                        CreationLabel("Rules")
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(inputContainer)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(16.dp),
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = rules.ifBlank { "Tap to add rules..." },
                                style = Type.bodyLg,
                                color = if (rules.isBlank()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(end = 32.dp),
                            )
                            Icon(
                                painter = painterResource(R.drawable.ic_description),
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // ---- CALCULATIONS PREVIEW --------------------------------------
                Column(
                    Modifier
                        .fillMaxWidth()
                        .dashedBorder(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            cornerRadius = 12.dp,
                        )
                        .background(areenaColors().primaryFixed.copy(alpha = 0.2f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_calculate),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "CALCULATIONS PREVIEW",
                            style = Type.labelLg.copy(letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { heading() }, // R5
                        )
                    }

                    val breakdown = calculateTop9Breakdown(slots, feeNum)

                    CalcRow("Total Slots:", "${breakdown.totalSlots}", MaterialTheme.colorScheme.onSurface)
                    CalcRow("Free Slots:", "${breakdown.freeSlots}", MaterialTheme.colorScheme.onSurface)
                    CalcRow("Paid Participants:", "${breakdown.paidParticipants}", MaterialTheme.colorScheme.onSurface)
                    CalcRow("Entry Fee:", "Rs. ${formatMoney(breakdown.entryFee)}", MaterialTheme.colorScheme.onSurface)
                    CalcRow("Collected Entry Pool:", "Rs. ${formatMoney(breakdown.collectedEntryPool)}", MaterialTheme.colorScheme.primary)

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(areenaColors().primaryFixed.copy(alpha = 0.2f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        breakdown.rankPrizes.forEachIndexed { i, prize ->
                            RankRow("Rank ${i + 1} Prize:", "Rs. ${formatMoney(prize)}")
                        }
                    }

                    CalcRow("Top 9 Prize Pool Total:", "Rs. ${formatMoney(breakdown.top9PrizePool)}", MaterialTheme.colorScheme.secondary)
                    CalcRow("Remaining Participants:", "${breakdown.remainingParticipants}", MaterialTheme.colorScheme.onSurface)
                    CalcRow("Loser Prize (per user):", "Rs. ${formatMoney(breakdown.consolationPerUser)}", MaterialTheme.colorScheme.onSurfaceVariant)
                    CalcRow("Total Loser Prize Pool:", "Rs. ${formatMoney(breakdown.consolationPool)}", MaterialTheme.colorScheme.onSurfaceVariant)
                    CalcRow("Remaining Balance:", "Rs. ${formatMoney(breakdown.remainingBalance)}", MaterialTheme.colorScheme.tertiary)
                }

                // ---- CTA --------------------------------------------------------
                val ctaInteraction = remember { MutableInteractionSource() }
                Button(
                    onClick = {
                        if (gate.requireAccount(
                                env.user?.isGuest == true,
                                "host your own tournament",
                            )
                        ) {
                            submit()
                        }
                    },
                    enabled = !submitting,
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    interactionSource = ctaInteraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .primaryGlow(RoundedCornerShape(percent = 50))
                        .pressScale(ctaInteraction, pressedScale = 0.98f),
                ) {
                    if (submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            trackColor = Color.Transparent,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Hosting...", style = Type.labelLg)
                    } else {
                        Text("Host Tournament", style = Type.labelLg)
                    }
                }
            }
        }
    }

    // ---- Match Type bottom sheet ----------------------------------------------
    if (matchTypeOpen) {
        MatchTypeSheet(
            modes = gameModes,
            selectedMode = mode,
            onSelect = selectMatchType,
            onDismiss = { matchTypeOpen = false },
        )
    }

    // ---- Rules centered modal ---------------------------------------------------
    if (rulesOpen) {
        RulesDialog(
            draft = rulesDraft,
            onDraftChange = { rulesDraft = it },
            onCancel = { rulesOpen = false },
            onSave = {
                rules = rulesDraft
                rulesOpen = false
            },
        )
    }

    // ---- Date & Time picker ------------------------------------------------------
    DateTimePickerModal(
        open = dateOpen,
        value = startTime,
        onClose = { dateOpen = false },
        onChange = { iso ->
            startTime = iso
            clearError("startTime")
        },
    )

    GuestGateDialog(gate, env)
}

/** Local ISO ("2025-10-25T11:06") → UTC ISO instant, mirroring `new Date(x).toISOString()`. */
private fun toUtcIso(localIso: String): String = try {
    LocalDateTime.parse(localIso.take(19))
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toString()
} catch (_: Exception) {
    localIso
}

@Composable
private fun CreationLabel(text: String) {
    Text(
        text = text,
        style = Type.bodyMd.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun CreationError(message: String) {
    Text(
        text = message,
        style = Type.labelMd,
        color = MaterialTheme.colorScheme.error,
    )
}

/** h-12 rounded-full input — shared [AreenaxPillField] (owner field concept). */
@Composable
private fun CreationTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String?,
    leadingIcon: String?,
    enabled: Boolean = true,
    numeric: Boolean = false,
) {
    AreenaxPillField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        enabled = enabled,
        keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
        imeAction = ImeAction.Default, // pre-migration KeyboardOptions.Default
    )
}

@Composable
private fun CalcRow(label: String, value: String, valueColor: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = Type.bodyMd,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = Type.labelLg,
            color = valueColor,
        )
    }
}

@Composable
private fun RankRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = Type.bodyMd,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = Type.labelMd.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Dashed stroke border (web `border-dashed`) — Compose BorderStroke can't dash. */
private fun Modifier.dashedBorder(color: Color, cornerRadius: Dp, dash: Dp = 6.dp, gap: Dp = 6.dp): Modifier =
    this.drawBehind {
        val strokePx = 1.dp.toPx()
        val radius = cornerRadius.toPx()
        val path = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    rect = androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                ),
            )
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokePx,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx())),
            ),
        )
    }

/** Match Type bottom sheet — bg-surface-container-low, rounded-t-[1.75rem]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchTypeSheet(
    modes: List<GameMode>,
    selectedMode: String,
    onSelect: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val sixtyVh = (LocalConfiguration.current.screenHeightDp.dp) * 0.6f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = sheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 48.dp, height = 6.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            )
        },
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Select Match Type",
                    style = Type.headlineMd,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Close",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = sixtyVh)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (modes.isEmpty()) {
                    Text(
                        text = "No match types configured for this game yet.",
                        style = Type.bodyMd,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    )
                }
                modes.forEach { option ->
                    val interaction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = { onSelect(option.name, option.slots) },
                        interactionSource = interaction,
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressScale(interaction, pressedScale = 0.98f),
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(areenaColors().primaryFixed.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                AreenaxIcon(
                                    name = option.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = option.name,
                                    style = Type.labelLg,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = option.sub,
                                    style = Type.labelMd,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                painter = painterResource(R.drawable.ic_check_circle),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(
                                    alpha = if (selectedMode == option.name) 1f else 0f,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Rules centered modal — rounded-[1.75rem] card with Cancel / Save Rules. */
@Composable
private fun RulesDialog(
    draft: String,
    onDraftChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 448.dp),
            ) {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Tournament Rules",
                            style = Type.headlineMd,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Surface(
                            onClick = onCancel,
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = "Close",
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    Column(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Owner field concept (multiline variant): animated charcoal
                        // container + 1dp focus ring via areenaxFieldColors; no icon.
                        var rulesFocused by remember { mutableStateOf(false) }
                        val rulesColors = areenaxFieldColors(rulesFocused)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 110.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(rulesColors.container)
                                .border(1.dp, rulesColors.ring, RoundedCornerShape(16.dp))
                                .onFocusChanged { rulesFocused = it.hasFocus },
                        ) {
                            BasicTextField(
                                value = draft,
                                onValueChange = onDraftChange,
                                textStyle = Type.bodyLg.copy(color = rulesColors.text),
                                cursorBrush = SolidColor(rulesColors.cursor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                decorationBox = { inner ->
                                    Column {
                                        if (draft.isEmpty()) {
                                            Text(
                                                text = "Enter tournament rules, terms, and conditions here...",
                                                style = Type.bodyLg,
                                                color = rulesColors.placeholder,
                                            )
                                        }
                                        inner()
                                    }
                                },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(
                                onClick = onCancel,
                                shape = RoundedCornerShape(percent = 50),
                                color = Color.Transparent,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Cancel",
                                        style = Type.labelLg,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            Surface(
                                onClick = onSave,
                                shape = RoundedCornerShape(percent = 50),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Save Rules",
                                        style = Type.labelLg,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
