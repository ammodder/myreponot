package com.areenax.app.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.areenax.app.R
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.theme.bannerDarkStart
import com.areenax.app.core.util.TournamentCardAction
import com.areenax.app.core.util.TournamentCardActionKind
import com.areenax.app.data.Tournament
import com.areenax.app.core.util.computeTournamentAction
import com.areenax.app.core.util.formatDateTime
import com.areenax.app.core.util.formatMoney

/*
 * TournamentCard — port of src/components/shared/TournamentCard.tsx
 * (SPEC/03 §15; pixel truth = upload/pages_extracted/pages/tournamentcard.html).
 *
 * Card anatomy (top → bottom):
 *   1. Banner h-48 (192dp): bannerImage ?? game.image via Coil over a dark
 *      gradient fallback; bottom scrim black/70→transparent; top-right mode
 *      chip (primary pill); bottom-left schedule + formatDateTime (white).
 *   2. Header: 40dp host-initial circle + name + "Tournament Lead" | right
 *      "Tournament Status" + statusLabel (Open/Live/status, primary bold).
 *   3. Stats band (top/bottom hairlines): Total Prize (only when >0, primary)
 *      / Entry Fee (always; spans full width alone).
 *   4. "Filled Slots" + "cur / max Players" + h-2 progress (primary/20 light,
 *      solid primary dark).
 *   5. Bottom action h-14 pill — the per-user state machine from
 *      lib/tournament-state.ts (computeTournamentAction): JOIN / JOINED /
 *      ROOM / RESULTS / PROOF (+ submitted chip) / LIVE ping / SLOTS FULL /
 *      CANCELLED-COMPLETED hourglass. Room/results/proof call [onAction]
 *      (web stopPropagation) instead of the card click.
 *
 * Used by: TournamentsScreen, MyTournamentScreen (HostTournamentCard keeps its
 * own local replica per the web).
 */

/** Web `from-gray-900 to-black` banner fallback when no image is set. */
private val BannerFallback = Brush.verticalGradient(listOf(bannerDarkStart, Color.Black))

@Composable
fun TournamentCard(
    tournament: Tournament,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    joinLabel: String = "JOIN NOW",
    joined: Boolean = false,
    /** Card-level actions (room popup / results / proof) — web stopPropagation. */
    onAction: ((TournamentCardAction) -> Unit)? = null,
) {
    val action = computeTournamentAction(tournament, joined)
    val pct = if (tournament.maxPlayers > 0) {
        (tournament.currentPlayers.toFloat() / tournament.maxPlayers) * 100f
    } else {
        0f
    }
    val statusLabel = when (tournament.status) {
        "UPCOMING" -> "Open"
        "ONGOING" -> "Live"
        else -> tournament.status
    }
    val extended = areenaColors()
    val shape = RoundedCornerShape(24.dp) // rounded-[1.5rem]
    val interaction = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, pressedScale = 0.99f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = onClick != null,
                onClick = { onClick?.invoke() },
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
        ),
        shadowElevation = 1.dp,
    ) {
        Column {
            TournamentBanner(tournament = tournament)

            Column(Modifier.padding(16.dp)) {
                // ---- Header: host identity + status -------------------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (tournament.host?.gameName ?: "A").take(1).uppercase(),
                            style = Type.headlineMd,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(
                            text = tournament.name,
                            style = Type.bodyLg.copy(
                                fontWeight = FontWeight.Bold,
                                lineHeight = 20.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Tournament Lead",
                            style = Type.labelMd.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Tournament Status",
                            style = Type.labelMd.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Text(
                            text = statusLabel,
                            style = Type.bodyLg.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ---- Stats band (prize/entry) -------------------------------
                val hasPrize = tournament.prizePool > 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (hasPrize) StatCell(
                        label = "Total Prize",
                        value = "Rs ${formatMoney(tournament.prizePool)}",
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    StatCell(
                        label = "Entry Fee",
                        value = "Rs ${formatMoney(tournament.entryFee)}",
                        valueColor = MaterialTheme.colorScheme.onSurface,
                        modifier = if (hasPrize) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                    )
                }

                // ---- Filled slots -------------------------------------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Filled Slots",
                        style = Type.labelLg.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${tournament.currentPlayers} / ${tournament.maxPlayers} Players",
                        style = Type.labelLg.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(pct / 100f)
                            .height(8.dp)
                            .background(
                                if (extended.isDark) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            ),
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ---- Bottom action (state machine) --------------------------
                when (action.kind) {
                    TournamentCardActionKind.JOIN -> ActionButton(
                        label = joinLabel,
                        icon = "chevron_right",
                        onClick = onClick,
                    )

                    TournamentCardActionKind.ROOM -> ActionButton(
                        label = action.label,
                        icon = "key",
                        onClick = { onAction?.invoke(action) },
                    )

                    TournamentCardActionKind.RESULTS -> ActionButton(
                        label = action.label,
                        icon = "emoji_events",
                        onClick = { onAction?.invoke(action) },
                    )

                    TournamentCardActionKind.PROOF ->
                        if (tournament.myProofSubmitted == true) {
                            JoinedChip(label = "Result Submitted")
                        } else {
                            ActionButton(
                                label = action.label,
                                icon = "upload",
                                onClick = { onAction?.invoke(action) },
                            )
                        }

                    TournamentCardActionKind.JOINED -> JoinedChip(label = action.label)

                    TournamentCardActionKind.LIVE -> LiveChip(label = action.label)

                    TournamentCardActionKind.SLOTS_FULL -> StateChip(
                        label = action.label,
                        icon = "groups",
                        container = MaterialTheme.colorScheme.surfaceContainerHigh,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    else -> StateChip(
                        label = action.label,
                        icon = "hourglass_top",
                        container = MaterialTheme.colorScheme.surfaceContainer,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Banner image (bannerImage ?? game.image) with scrim, mode chip + start time. */
@Composable
private fun TournamentBanner(tournament: Tournament) {
    val imageUrl = tournament.bannerImage ?: tournament.game?.image ?: ""
    Box(
        Modifier
            .fillMaxWidth()
            .height(192.dp) // h-48
            .background(BannerFallback),
    ) {
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Bottom scrim — linear-gradient(to top, rgba(0,0,0,0.7), transparent)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.7f),
                    ),
                ),
        )
        // Mode chip — top-right primary pill
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 12.dp),
        ) {
            Text(
                text = tournament.mode,
                style = Type.labelMd.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        // Start time — bottom-left
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.Icon(
                painter = painterResource(R.drawable.ic_schedule),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.White,
            )
            Text(
                text = formatDateTime(tournament.startTime),
                style = Type.bodyMd.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = Type.labelMd.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = Type.bodyMd.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = valueColor,
            textAlign = TextAlign.Center,
        )
    }
}

/** Primary CTA pill (JOIN NOW / ROOM ID & PASSWORD / RESULTS / SUBMIT RESULT PROOF). */
@Composable
private fun ActionButton(label: String, icon: String, onClick: (() -> Unit)?) {
    Button(
        onClick = { onClick?.invoke() },
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp), // h-14
    ) {
        AreenaxIcon(name = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            text = label,
            style = Type.bodyLg.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

/** JOINED / Result Submitted — clean balance-chip pill (wallet pill language). */
@Composable
private fun JoinedChip(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .cardShadow(RoundedCornerShape(percent = 50))
            .clip(RoundedCornerShape(percent = 50))
            .background(areenaColors().balanceChip)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(percent = 50),
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AreenaxIcon(
            name = "check_circle",
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            filled = true,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = label,
            style = Type.bodyLg.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

/** MATCH PROCESSING — balance-chip pill with the red animate-ping live dot. */
@Composable
private fun LiveChip(label: String) {
    val transition = rememberInfiniteTransition(label = "livePing")
    val pingAlpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "livePingAlpha",
    )
    val pingScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "livePingScale",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .cardShadow(RoundedCornerShape(percent = 50))
            .clip(RoundedCornerShape(percent = 50))
            .background(areenaColors().balanceChip)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(percent = 50),
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Halo (animate-ping)
            Box(
                Modifier
                    .size(10.dp)
                    .scale(pingScale)
                    .alpha(pingAlpha)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
            )
            Box(
                Modifier
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
            )
        }
        Spacer(Modifier.size(10.dp))
        Text(
            text = label,
            style = Type.bodyLg.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** SLOTS FULL / CANCELLED / COMPLETED — muted informational state pill. */
@Composable
private fun StateChip(label: String, icon: String, container: Color, content: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(container),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AreenaxIcon(name = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = content)
        Spacer(Modifier.size(8.dp))
        Text(
            text = label,
            style = Type.bodyLg.copy(fontWeight = FontWeight.Bold),
            color = content,
            maxLines = 1,
        )
    }
}
