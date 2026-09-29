package com.areenax.app.ui.screens.host

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.areenax.app.R
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.Type
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.BellButton
import com.areenax.app.data.Tournament

/**
 * HostSuccessScreen — key `hostTournamentSuccess` (SPEC/01 F3; web
 * HostSuccessScreen.tsx, pixel truth hosttournamentsuccess.html).
 *
 * Bouncing glowing check hero (w-32 primary circle + blur glow ring, check_circle
 * filled), "Tournament Hosted Successfully!" + quoted-name body, Tournament
 * Summary card (Name / Entry Fee "Rs. x" / Slots fallback 48 / Match Type
 * fallback "Solo" text-primary — fed by GET /tournaments/:id with the nav-params
 * fallback kept on failure), and the two actions:
 * "View Tournament Details" → hostTournamentDetails {tournamentId};
 * "Created Tournaments" → hostTournamentCard.
 */
@Composable
fun HostSuccessScreen(env: NavEnv) {
    val tournamentId = env.params["tournamentId"] as? String
    val paramName = env.params["name"] as? String

    var tournament by remember { mutableStateOf<Tournament?>(null) }

    LaunchedEffect(tournamentId) {
        if (tournamentId.isNullOrBlank()) return@LaunchedEffect
        // summary stays generic if fetch fails
        when (val res = safeCall { env.api.tournamentDetails(tournamentId) }) {
            is ApiResult.Success -> tournament = res.data.tournament
            else -> Unit
        }
    }

    val tName = tournament?.name?.takeIf { it.isNotBlank() } ?: paramName ?: "Creator Battle"

    // animate-bounce (CSS): -25% at 0%/100%, 0 at 50%;
    // 0→50% accelerates (fall), 50→100% decelerates (rise)
    val bounce = rememberInfiniteTransition(label = "successBounce")
    val bounceY by bounce.animateFloat(
        initialValue = -32f,
        targetValue = -32f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                -32f at 0 using CubicBezierEasing(0.8f, 0f, 1f, 1f)
                0f at 500 using CubicBezierEasing(0f, 0f, 0.2f, 1f)
                -32f at 1000
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "successBounceY",
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState()),
    ) {
        AppBar(title = "Success", right = { BellButton() })

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp), // gap-6
        ) {
            // ---- Hero: bouncing glowing check --------------------------------
            Box(
                modifier = Modifier
                    .size(128.dp) // w-32
                    .offset(y = bounceY.dp),
                contentAlignment = Alignment.Center,
            ) {
                // -inset-3 ring: bg-primary/15
                Box(
                    Modifier
                        .size(152.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                )
                // inset-0 bg-primary/25 blur-2xl
                Box(
                    Modifier
                        .size(128.dp)
                        .blur(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                )
                // primary circle + white filled check (shadow 0 0 36px rgba(0,74,198,.4))
                Box(
                    Modifier
                        .size(128.dp)
                        .drawBehind {
                            // soft two-ring approximation of the 36px primary glow
                            val r = size.minDimension / 2f
                            drawCircle(Color(0x1A004AC6), radius = r * 1.4f)
                            drawCircle(Color(0x33004AC6), radius = r * 1.2f)
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check_circle_fill),
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            // ---- Headings ------------------------------------------------------
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Tournament Hosted Successfully!",
                    style = Type.pageTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text(
                    text = buildAnnotatedString {
                        append("Your tournament ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("\"$tName\"")
                        }
                        append(" has been created and is now live for players to join.")
                    },
                    style = Type.bodyLg.copy(fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // ---- Details Card ----------------------------------------------------
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(24.dp),
                    )
                    .padding(20.dp),
            ) {
                Text(
                    text = "Tournament Summary",
                    style = Type.labelLg,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                SummaryRow(label = "Name", value = tName, divider = true)
                SummaryRow(
                    label = "Entry Fee",
                    value = "Rs. ${com.areenax.app.core.util.formatMoney(tournament?.entryFee ?: 0.0)}",
                    divider = true,
                )
                SummaryRow(
                    label = "Slots",
                    value = (tournament?.maxPlayers ?: 48).toString(),
                    divider = true,
                )
                SummaryRow(
                    label = "Match Type",
                    value = tournament?.mode?.takeIf { it.isNotBlank() } ?: "Solo",
                    valueColor = MaterialTheme.colorScheme.primary,
                )
            }

            // ---- Actions ---------------------------------------------------------
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Button(
                    onClick = {
                        env.navigate(
                            ScreenKeys.HOST_TOURNAMENT_DETAILS,
                            buildMap { tournamentId?.let { put("tournamentId", it) } },
                        )
                    },
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text("View Tournament Details", style = Type.labelLg)
                }
                Text(
                    text = "Created Tournaments",
                    style = Type.labelLg,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { env.navigate(ScreenKeys.HOST_TOURNAMENT_CARD) }
                        .padding(vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface, divider: Boolean = false) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (divider) Modifier.padding(bottom = 12.dp) else Modifier),
    ) {
        Row(
            Modifier.fillMaxWidth(),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (divider) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}
