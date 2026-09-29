package com.areenax.app.ui.screens.social

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.SuccessAnim
import com.areenax.app.core.ui.cardShadow
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.primaryGlow

/*
 * TeamCreationDoneScreen — key `teamCreationDone` (build record 3-d):
 * SuccessAnim "Team Created!" + subtitle with the team name (params.teamName,
 * fallback "Your Team") + "members notified" note card + "Go to My Team"
 * (replace(myTeam)) + "Back to Home" (replace(home)) — over the dual radial
 * ambient background; AppBar back → replace(myTeam).
 */

@Composable
fun TeamCreationDoneScreen(env: NavEnv) {
    val extended = areenaColors()
    val teamName = (env.params["teamName"] as? String).takeUnless { it.isNullOrBlank() } ?: "Your Team"

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                if (!extended.isDark) {
                    // Dual radial: top-center primary-fixed 50% + bottom-right container-highest 40%
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x80DBE1FF), Color.Transparent),
                            center = Offset(size.width / 2f, 0f),
                            radius = size.width * 0.8f,
                        ),
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x66D3E4FE), Color.Transparent),
                            center = Offset(size.width, size.height),
                            radius = size.width * 0.7f,
                        ),
                    )
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            AppBar(
                title = "My Team",
                onBack = { env.replace(ScreenKeys.MY_TEAM) },
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(0.35f))

                SuccessAnim(
                    title = "Team Created!",
                    subtitle = "$teamName has been created successfully.",
                )

                Spacer(Modifier.height(24.dp))

                // "members notified" note card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .cardShadow(RoundedCornerShape(16.dp)),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AreenaxIcon(
                            name = "notifications",
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Your squad members have been notified with an in-app invite.",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.weight(0.2f))

                // "Go to My Team" — replace(myTeam)
                val myTeamInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = { env.replace(ScreenKeys.MY_TEAM) },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    interactionSource = myTeamInteraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .primaryGlow(RoundedCornerShape(50))
                        .pressScale(myTeamInteraction, 0.98f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Go to My Team",
                            style = Type.bodyLg,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // "Back to Home" — replace(home)
                val homeInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = { env.replace(ScreenKeys.HOME) },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
                    interactionSource = homeInteraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .pressScale(homeInteraction, 0.98f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Back to Home",
                            style = Type.bodyLg,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Spacer(Modifier.height(48.dp))
                Spacer(Modifier.weight(0.2f))
            }
        }
    }
}
