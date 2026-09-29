package com.areenax.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AreenaxPillField
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.pressScale
import com.areenax.app.data.AdminGameConfigOp
import com.areenax.app.data.AdminGameCreateBody
import com.areenax.app.data.AdminGameUpdateBody
import com.areenax.app.data.AdminGamesResponse
import com.areenax.app.data.Game
import com.areenax.app.data.OkResponse

/**
 * GamesAdminSection — section composable rendered INSIDE AdminPanelScreen
 * (SPEC/01 E10; SUPER_ADMIN gate at the call site). Exact port of
 * `profile/GamesAdminSection.tsx`:
 *
 * - GET /admin/games → game rows (thumbnail, name, "«m» match types ·
 *   «maps» maps · «p» perspectives" + "· inactive", Activate/Deactivate,
 *   configure `edit`/`expand_less`, delete).
 * - Create: POST /admin/games {name, image} → "Game created".
 * - Update: POST /admin/games/:id ({isActive} | {matchDurationMinutes}) →
 *   "Game deactivated"/"Game activated"/"Match duration updated".
 * - Delete: DELETE /admin/games/:id → "Game deleted" (NO confirm on web).
 * - Config ops: POST /admin/games/:id/config {kind, op, …} → "Match type
 *   added"/"Match type removed"/"Map added"/"Map removed"/"Perspective
 *   added"/"Perspective removed".
 * - GameConfigEditor: "Match in Progress Duration (minutes)" (1..10080 +
 *   Save), Match Types list + add form, Maps chips + add, Perspectives
 *   chips + add. Empty: "No games yet — add the first one above."
 * - Failures → toasts "Failed to load games"/"Create failed"/"Update
 *   failed"/"Delete failed"/"Operation failed".
 */
@Composable
fun GamesAdminSection(env: NavEnv) {
    var games by remember { mutableStateOf<List<Game>?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var newImage by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    fun load() {
        CoroutineScopeLaunch {
            when (val res = safeCall<AdminGamesResponse> { env.api.adminGames() }) {
                is ApiResult.Success -> games = res.data.games
                is ApiResult.Error -> {
                    env.toast.show(
                        title = "Failed to load games",
                        description = res.message,
                        variant = ToastVariant.Destructive,
                    )
                    games = emptyList()
                }
                is ApiResult.NetworkError -> {
                    env.toast.show(
                        title = "Failed to load games",
                        description = res.message,
                        variant = ToastVariant.Destructive,
                    )
                    games = emptyList()
                }
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    fun createGame() {
        val name = newName.trim()
        val image = newImage.trim()
        if (name.isEmpty() || image.isEmpty() || creating) return
        creating = true
        CoroutineScopeLaunch {
            val res = safeCall<AdminGamesResponse> {
                env.api.adminCreateGame(AdminGameCreateBody(name = name, image = image))
            }
            when (res) {
                is ApiResult.Success -> env.toast.show(title = "Game created")
                else -> env.toast.show(
                    title = "Create failed",
                    description = (res as? ApiResult.Error)?.message
                        ?: (res as? ApiResult.NetworkError)?.message
                        ?: "Something went wrong",
                    variant = ToastVariant.Destructive,
                )
            }
            if (res is ApiResult.Success) {
                newName = ""
                newImage = ""
                load()
            }
            creating = false
        }
    }

    fun updateGame(id: String, body: AdminGameUpdateBody, okTitle: String) {
        CoroutineScopeLaunch {
            when (val res = safeCall<OkResponse> { env.api.adminUpdateGame(id, body) }) {
                is ApiResult.Success -> {
                    env.toast.show(title = okTitle)
                    load()
                }
                is ApiResult.Error -> env.toast.show(
                    title = "Update failed", description = res.message, variant = ToastVariant.Destructive,
                )
                is ApiResult.NetworkError -> env.toast.show(
                    title = "Update failed", description = res.message, variant = ToastVariant.Destructive,
                )
            }
        }
    }

    fun deleteGame(id: String) {
        CoroutineScopeLaunch {
            when (val res = safeCall<OkResponse> { env.api.adminDeleteGame(id) }) {
                is ApiResult.Success -> {
                    env.toast.show(title = "Game deleted")
                    if (expandedId == id) expandedId = null
                    load()
                }
                is ApiResult.Error -> env.toast.show(
                    title = "Delete failed", description = res.message, variant = ToastVariant.Destructive,
                )
                is ApiResult.NetworkError -> env.toast.show(
                    title = "Delete failed", description = res.message, variant = ToastVariant.Destructive,
                )
            }
        }
    }

    fun configOp(gameId: String, body: AdminGameConfigOp, okTitle: String) {
        CoroutineScopeLaunch {
            when (val res = safeCall<OkResponse> { env.api.adminGameConfig(gameId, body) }) {
                is ApiResult.Success -> {
                    env.toast.show(title = okTitle)
                    load()
                }
                is ApiResult.Error -> env.toast.show(
                    title = "Operation failed", description = res.message, variant = ToastVariant.Destructive,
                )
                is ApiResult.NetworkError -> env.toast.show(
                    title = "Operation failed", description = res.message, variant = ToastVariant.Destructive,
                )
            }
        }
    }

    SectionCard {
        Text(
            text = "Games Management",
            style = Type.headlineMd,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Games, match types (with fixed slots), maps and perspectives defined here feed the Host tournament creation flow automatically.",
            style = Type.bodyMd,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Create game row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniInput(
                value = newName,
                onValueChange = { newName = it },
                placeholder = "New game name (e.g. Free Fire)",
                leadingIcon = "sports_esports",
                modifier = Modifier.weight(1f),
            )
            MiniInput(
                value = newImage,
                onValueChange = { newImage = it },
                placeholder = "Game image URL",
                leadingIcon = "edit",
                modifier = Modifier.weight(1f),
            )
            SmallCta(
                label = "Add Game",
                enabled = !creating && newName.isNotBlank() && newImage.isNotBlank(),
            ) { createGame() }
        }

        // Games list
        val list = games
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                com.areenax.app.core.ui.AreenaxSpinner(size = 24)
            }
            list.isEmpty() -> Text(
                text = "No games yet — add the first one above.",
                style = Type.bodyMd,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                list.forEach { g ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(16.dp),
                            )
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        // game row
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(
                                model = g.image,
                                contentDescription = g.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(8.dp),
                                    ),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = g.name,
                                    style = Type.labelLg,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${g.modes.size} match types · ${g.maps.size} maps · ${g.perspectives.size} perspectives" +
                                        if (!g.isActive) " · inactive" else "",
                                    style = Type.labelMd,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // Activate / Deactivate
                            Surface(
                                onClick = {
                                    updateGame(
                                        g.id,
                                        AdminGameUpdateBody(isActive = !g.isActive),
                                        if (g.isActive) "Game deactivated" else "Game activated",
                                    )
                                },
                                shape = CircleShape,
                                color = Color.Transparent,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                ),
                            ) {
                                Text(
                                    text = if (g.isActive) "Deactivate" else "Activate",
                                    style = Type.labelMd,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                            // Configure
                            val configureInteraction = remember { MutableInteractionSource() }
                            Surface(
                                onClick = { expandedId = if (expandedId == g.id) null else g.id },
                                interactionSource = configureInteraction,
                                shape = CircleShape,
                                color = Color.Transparent,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                ),
                                modifier = Modifier.size(36.dp).pressScale(configureInteraction, 0.95f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    AreenaxIcon(
                                        name = if (expandedId == g.id) "expand_less" else "edit",
                                        contentDescription = "Configure ${g.name}",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            // Delete
                            val deleteInteraction = remember { MutableInteractionSource() }
                            Surface(
                                onClick = { deleteGame(g.id) },
                                interactionSource = deleteInteraction,
                                shape = CircleShape,
                                color = Color.Transparent,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                ),
                                modifier = Modifier.size(36.dp).pressScale(deleteInteraction, 0.95f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    AreenaxIcon(
                                        name = "delete",
                                        contentDescription = "Delete ${g.name}",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // per-game configuration
                        if (expandedId == g.id) {
                            GameConfigEditor(
                                game = g,
                                configOp = ::configOp,
                                updateGame = ::updateGame,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ editor

@Composable
private fun GameConfigEditor(
    game: Game,
    configOp: (gameId: String, body: AdminGameConfigOp, okTitle: String) -> Unit,
    updateGame: (id: String, body: AdminGameUpdateBody, okTitle: String) -> Unit,
) {
    var modeName by remember { mutableStateOf("") }
    var modeSlots by remember { mutableStateOf("") }
    var modeSub by remember { mutableStateOf("") }
    var mapName by remember { mutableStateOf("") }
    var perspectiveName by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf((game.matchDurationMinutes ?: 60).toString()) }

    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Match in Progress duration
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Match in Progress Duration (minutes)",
                style = Type.labelLg,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MiniInput(
                    value = duration,
                    onValueChange = { duration = it.filter { ch -> ch.isDigit() } },
                    placeholder = "e.g. 60",
                    leadingIcon = "edit",
                    modifier = Modifier.weight(1f),
                )
                val d = duration.toLongOrNull() ?: 0L
                SmallCta(
                    label = "Save",
                    enabled = duration.isNotBlank() && d >= 1 && d <= 10080,
                ) {
                    updateGame(
                        game.id,
                        AdminGameUpdateBody(matchDurationMinutes = d.toInt()),
                        "Match duration updated",
                    )
                }
            }
            Text(
                text = "How long \"Match in Progress\" stays on this game's tournaments after the room window ends. After it elapses, joined players are asked to submit a result proof screenshot.",
                style = Type.labelMd,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Match types — fixed slots
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Match Types (fixed slots)",
                style = Type.labelLg,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (game.modes.isEmpty()) {
                    Text(
                        text = "No match types yet.",
                        style = Type.labelMd,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                game.modes.forEach { m ->
                    Surface(
                        shape = CircleShape,
                        color = areenaColors().surfaceContainerLavender,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (m.icon.isNotBlank()) {
                                AreenaxIcon(
                                    name = m.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = m.name,
                                fontWeight = FontWeight.SemiBold,
                                style = Type.bodyMd,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = m.sub,
                                style = Type.labelMd,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${m.slots} slots",
                                style = Type.labelMd,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            val delInteraction = remember { MutableInteractionSource() }
                            Box(
                                Modifier
                                    .clickable(
                                        interactionSource = delInteraction,
                                        indication = null,
                                    ) { configOp(game.id, AdminGameConfigOp(kind = "mode", op = "delete", id = m.id), "Match type removed") },
                            ) {
                                AreenaxIcon(
                                    name = "close",
                                    contentDescription = "Delete ${m.name}",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MiniInput(
                    value = modeName,
                    onValueChange = { modeName = it },
                    placeholder = "Name (e.g. Squad)",
                    leadingIcon = "edit",
                    modifier = Modifier.weight(1f),
                )
                MiniInput(
                    value = modeSlots,
                    onValueChange = { modeSlots = it },
                    placeholder = "Fixed slots",
                    leadingIcon = "edit",
                    modifier = Modifier.weight(1f),
                )
                MiniInput(
                    value = modeSub,
                    onValueChange = { modeSub = it },
                    placeholder = "Subtitle (optional)",
                    leadingIcon = "edit",
                    modifier = Modifier.weight(1f),
                )
                SmallCta(
                    label = "Add Match Type",
                    enabled = modeName.isNotBlank() && modeSlots.isNotBlank(),
                ) {
                    configOp(
                        game.id,
                        AdminGameConfigOp(
                            kind = "mode",
                            op = "add",
                            name = modeName.trim(),
                            slots = modeSlots.toIntOrNull() ?: 0,
                            sub = modeSub.trim(),
                        ),
                        "Match type added",
                    )
                    modeName = ""
                    modeSlots = ""
                    modeSub = ""
                }
            }
        }

        // Maps
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Maps", style = Type.labelLg, color = MaterialTheme.colorScheme.onSurface)
            if (game.maps.isEmpty()) {
                Text(text = "No maps yet.", style = Type.labelMd, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    game.maps.forEach { m ->
                        Surface(
                            shape = CircleShape,
                            color = areenaColors().surfaceContainerLavender,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            ),
                        ) {
                            Row(
                                Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(text = m.name, style = Type.bodyMd, color = MaterialTheme.colorScheme.onSurface)
                                val delInteraction = remember { MutableInteractionSource() }
                                Box(
                                    Modifier.clickable(
                                        interactionSource = delInteraction,
                                        indication = null,
                                    ) { configOp(game.id, AdminGameConfigOp(kind = "map", op = "delete", id = m.id), "Map removed") },
                                ) {
                                    AreenaxIcon(
                                        name = "close",
                                        contentDescription = "Delete ${m.name}",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MiniInput(
                    value = mapName,
                    onValueChange = { mapName = it },
                    placeholder = "New map name",
                    leadingIcon = "edit",
                    modifier = Modifier.weight(1f),
                )
                SmallCta(label = "Add Map", enabled = mapName.isNotBlank()) {
                    configOp(game.id, AdminGameConfigOp(kind = "map", op = "add", name = mapName.trim()), "Map added")
                    mapName = ""
                }
            }
        }

        // Perspectives
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Perspectives", style = Type.labelLg, color = MaterialTheme.colorScheme.onSurface)
            if (game.perspectives.isEmpty()) {
                Text(text = "No perspectives yet.", style = Type.labelMd, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    game.perspectives.forEach { p ->
                        Surface(
                            shape = CircleShape,
                            color = areenaColors().surfaceContainerLavender,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            ),
                        ) {
                            Row(
                                Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(text = p.name, style = Type.bodyMd, color = MaterialTheme.colorScheme.onSurface)
                                val delInteraction = remember { MutableInteractionSource() }
                                Box(
                                    Modifier.clickable(
                                        interactionSource = delInteraction,
                                        indication = null,
                                    ) { configOp(game.id, AdminGameConfigOp(kind = "perspective", op = "delete", id = p.id), "Perspective removed") },
                                ) {
                                    AreenaxIcon(
                                        name = "close",
                                        contentDescription = "Delete ${p.name}",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MiniInput(
                    value = perspectiveName,
                    onValueChange = { perspectiveName = it },
                    placeholder = "New perspective name",
                    leadingIcon = "edit",
                    modifier = Modifier.weight(1f),
                )
                SmallCta(label = "Add Perspective", enabled = perspectiveName.isNotBlank()) {
                    configOp(game.id, AdminGameConfigOp(kind = "perspective", op = "add", name = perspectiveName.trim()), "Perspective added")
                    perspectiveName = ""
                }
            }
        }
    }
}

// ------------------------------------------------------------------ shared pieces

/** bg-surface-container-lowest rounded-3xl border p-5 section wrapper. */
@Composable
internal fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

/** Web MINI_INPUT: h-11 pill input (Areenax field concept). */
@Composable
internal fun MiniInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: String? = null,
) {
    AreenaxPillField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        textStyle = Type.bodyMd,
        height = 44.dp,
        imeAction = ImeAction.Default, // pre-migration KeyboardOptions(Text)
    )
}

/** Web SMALL_CTA: h-11 px-5 primary pill. */
@Composable
internal fun SmallCta(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.pressScale(interaction, 0.95f),
    ) {
        Text(
            text = label,
            style = Type.labelMd,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

/** Local main-dispatcher launch helper for section callbacks. */
internal fun CoroutineScopeLaunch(block: suspend () -> Unit) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
        .launch { block() }
}
