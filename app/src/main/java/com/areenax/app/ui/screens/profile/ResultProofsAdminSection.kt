package com.areenax.app.ui.screens.profile

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AreenaxSpinner
import com.areenax.app.core.ui.AppBarCircleButton
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.util.timeAgo
import com.areenax.app.data.AdminImageResponse
import com.areenax.app.data.AdminProof
import com.areenax.app.data.AdminProofsResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ResultProofsAdminSection — section composable rendered INSIDE
 * AdminPanelScreen (SPEC/01 E11; MODERATOR+ gate at the call site). Exact
 * port of `profile/ResultProofsAdminSection.tsx`:
 *
 * - GET /admin/proofs → rows (48dp thumbnail / broken_image placeholder /
 *   spinner, "«gameName» • UID «uid»", "«game.name» • «tournament.name» •
 *   «mode»", timeAgo, chevron). The slim list no longer ships images — each
 *   data URL is fetched from GET /admin/proofs/:id and cached per proof id
 *   (prefetched for thumbnails, no refetch on re-open).
 * - Tap a row → full-screen viewer (black/80 backdrop, white card, close X,
 *   image object-contain or "Could not load image").
 * - Empty: "No result proofs submitted yet." Error toast "Couldn't load
 *   result proofs".
 */
@Composable
fun ResultProofsAdminSection(env: NavEnv) {
    var proofs by remember { mutableStateOf<List<AdminProof>?>(null) }
    var viewing by remember { mutableStateOf<AdminProof?>(null) }
    // per-proof-id image cache (data URL) — mirrors the web's imageCache ref
    val images = remember { mutableStateMapOf<String, String>() }
    val failed = remember { mutableStateMapOf<String, Boolean>() }
    val pending = remember { mutableSetOf<String>() }

    /** Fetch one proof image; notifyOnError only when the viewer opened it. */
    fun fetchImage(id: String, notifyOnError: Boolean) {
        if (images.containsKey(id)) return
        if (!pending.add(id)) return
        CoroutineScope(Dispatchers.Main).launch {
            when (val res = safeCall<AdminImageResponse> { env.api.adminProofImage(id) }) {
                is ApiResult.Success -> {
                    val image = res.data.image
                    if (image != null) {
                        images[id] = image
                    } else {
                        failed[id] = true
                        if (notifyOnError) {
                            env.toast.show(
                                title = "Couldn't load screenshot",
                                description = "Could not load image",
                                variant = ToastVariant.Destructive,
                            )
                        }
                    }
                }
                is ApiResult.Error -> {
                    failed[id] = true
                    if (notifyOnError) {
                        env.toast.show(
                            title = "Couldn't load screenshot",
                            description = res.message,
                            variant = ToastVariant.Destructive,
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    failed[id] = true
                    if (notifyOnError) {
                        env.toast.show(
                            title = "Couldn't load screenshot",
                            description = res.message,
                            variant = ToastVariant.Destructive,
                        )
                    }
                }
            }
            pending.remove(id)
        }
    }

    fun load() {
        CoroutineScope(Dispatchers.Main).launch {
            when (val res = safeCall<AdminProofsResponse> { env.api.adminProofs() }) {
                is ApiResult.Success -> {
                    proofs = res.data.proofs
                    res.data.proofs.forEach { fetchImage(it.id, notifyOnError = false) }
                }
                is ApiResult.Error -> {
                    proofs = emptyList()
                    env.toast.show(
                        title = "Couldn't load result proofs",
                        description = res.message,
                        variant = ToastVariant.Destructive,
                    )
                }
                is ApiResult.NetworkError -> {
                    proofs = emptyList()
                    env.toast.show(
                        title = "Couldn't load result proofs",
                        description = res.message,
                        variant = ToastVariant.Destructive,
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    SectionCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Result Proofs",
                style = Type.headlineMd,
                color = MaterialTheme.colorScheme.onSurface,
            )
            RefreshButton(onClick = { load() })
        }
        Text(
            text = "Match result screenshots uploaded by joined players from disabled tournaments. Tap one to view it full size.",
            style = Type.bodyMd,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val list = proofs
        when {
            list == null -> Box(
                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                AreenaxSpinner(size = 24)
            }
            list.isEmpty() -> Text(
                text = "No result proofs submitted yet.",
                style = Type.bodyMd,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                textAlign = TextAlign.Center,
            )
            else -> Column(
                Modifier
                    .fillMaxWidth()
                    .height(448.dp) // web max-h-[28rem]
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                list.forEach { p ->
                    Surface(
                        onClick = {
                            viewing = p
                            fetchImage(p.id, notifyOnError = true)
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = areenaColors().surfaceContainerLavender,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ProofThumbnail(
                                id = p.id,
                                image = images[p.id],
                                failed = failed[p.id] == true,
                                alt = "Result proof from ${p.user.gameName}",
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "${p.user.gameName} • UID ${p.user.uid}",
                                    style = Type.bodyMd,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = buildString {
                                        p.tournament.game?.name?.let { append("$it • ") }
                                        append("${p.tournament.name} • ${p.tournament.mode}")
                                    },
                                    style = Type.labelSm,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = timeAgo(p.createdAt),
                                    style = Type.labelSm,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            AreenaxIcon(
                                name = "chevron_right",
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    // Full screenshot viewer (black/80 backdrop + white card)
    viewing?.let { current ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { viewing = null }
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                onClick = { /* stopPropagation equivalent — keep the card open */ },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "${current.user.gameName} • UID ${current.user.uid}",
                                style = Type.bodyMd,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${current.tournament.name} • ${current.tournament.mode}",
                                style = Type.labelSm,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        AppBarCircleButton(onClick = { viewing = null }) {
                            AreenaxIcon(
                                name = "close",
                                contentDescription = "Close screenshot",
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val image = images[current.id]
                    when {
                        image != null -> AsyncImage(
                            model = image,
                            contentDescription = "Result proof from ${current.user.gameName}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.05f)),
                        )
                        failed[current.id] == true -> ViewerPlaceholder(
                            icon = "broken_image",
                            message = "Could not load image",
                        )
                        else -> Box(
                            Modifier
                                .fillMaxWidth()
                                .height(192.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            AreenaxSpinner(size = 24)
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ helpers

/** 48dp row thumbnail: image / broken_image / spinner. */
@Composable
private fun ProofThumbnail(id: String, image: String?, failed: Boolean, alt: String) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            image != null -> AsyncImage(
                model = image,
                contentDescription = alt,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp),
            )
            failed -> AreenaxIcon(
                name = "broken_image",
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> AreenaxSpinner(size = 16)
        }
    }
}

@Composable
private fun ViewerPlaceholder(icon: String, message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(192.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AreenaxIcon(
                name = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = message,
                style = Type.bodyMd,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** w-9 h-9 refresh circle (shared by the admin sections). */
@Composable
internal fun RefreshButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = androidx.compose.foundation.shape.CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .size(36.dp)
            .pressScale(interaction, 0.95f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            AreenaxIcon(
                name = "refresh",
                contentDescription = "Refresh",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

