package com.areenax.app.ui.screens.info

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.theme.LightPrimaryContainer
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.theme.ghostInk
import com.areenax.app.core.ui.ToastVariant

/**
 * OfflineScreen — key `offline` (SPEC/01 G4; web OfflineScreen.tsx, pixel truth
 * offline.html). Fixed light gradient root (#f0f4f8→#dbe4ec, areenaColors()
 * .offlineGradient); the web's remote 3D ghost PNG is recreated NATIVELY as a
 * vector illustration (white ghost with wavy tail, worried face, blue ethernet
 * cable squiggles + ground shadow ellipse) — no hotlinking; "OOPSS!"
 * display-lg, the exact body copy, and the "Try again" rounded-full bg-primary
 * button. Web "Try again" = window.location.reload() — native wires it to the
 * AppShell offline-swap mechanism instead (goBack when connectivity is back;
 * AppShell auto-pops on the `online` regain, and the mount check mirrors the
 * web's recover-on-enter).
 *
 * A3-04/A5-06: AppShell now renders this screen as a BLOCKING OVERLAY
 * ([asOverlay] = true) instead of a route push — the covered screen stays
 * composed (form input survives a transient offline blip) and cannot be
 * backed out of. In overlay mode the screen never navigates: it disappears
 * automatically when connectivity returns.
 */
@Composable
fun OfflineScreen(env: NavEnv, asOverlay: Boolean = false) {
    val isOnline by env.connectivity.isOnline.collectAsState()

    // Web recover(): on mount — if navigator.onLine → goBack().
    // The `online`-event path is owned by AppScaffold. Overlay mode: the shell
    // removes the overlay on its own — navigation from here would pop the
    // real screen beneath it.
    LaunchedEffect(Unit) {
        if (!asOverlay && env.connectivity.isOnline.value) env.goBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(areenaColors().offlineGradient)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Minimal transactional header (web keeps two empty 32dp spacers)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.size(32.dp))
            Box(Modifier.size(32.dp))
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // ---- 3D ghost illustration (native vector recreation) --------------
            GhostIllustration(
                modifier = Modifier
                    .size(256.dp) // w-64 h-64
                    .padding(bottom = 16.dp), // mb-4
            )

            // Ground shadow (140x16 radial blur ellipse, -10 top, 40 bottom)
            Box(
                Modifier
                    .padding(bottom = 40.dp)
                    .offset(y = (-10).dp)
                    .width(140.dp)
                    .height(16.dp)
                    .blur(4.dp)
                    .clip(CircleShape)
                    .background(Color(0x1A004AC6)),
            )

            Text(
                text = "OOPSS!",
                style = Type.displayLg,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Text(
                text = "Something went wrong. Try refreshing the page or checking your connection. We'll see you in a moment.",
                style = Type.bodyLg,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(280.dp) // max-w-[280px]
                    .padding(bottom = 32.dp),
            )

            Button(
                onClick = {
                    // Web: window.location.reload() → native retry through the
                    // AppShell offline swap: pop when the connection is back.
                    // Overlay mode: never navigates (the shell auto-removes it).
                    if (isOnline) {
                        if (!asOverlay) env.goBack()
                    } else {
                        env.toast.show(
                            title = "Still offline",
                            description = "Try refreshing the page or checking your connection.",
                            variant = ToastVariant.Destructive,
                        )
                    }
                },
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .width(280.dp) // max-w-[280px]
                    .height(56.dp), // py-4 label-lg
            ) {
                Text("Try again", style = Type.labelLg)
            }
        }
    }
}

/**
 * GhostIllustration — native stand-in for the remote 3D "worried ghost tangled
 * in ethernet cables" PNG: white ghost body (GhostShape), worried eyes + "o"
 * mouth, blush, and blue cable squiggles weaving around it (Canvas), with the
 * soft drop shadow the web's drop-shadow-xl gives.
 */
@Composable
private fun GhostIllustration(modifier: Modifier = Modifier) {
    val cable = Color(0x73004AC6) // primary @ ~45%
    Box(modifier, contentAlignment = Alignment.Center) {
        // Cables — behind the ghost (two S-squiggles + a loop)
        Canvas(Modifier.size(240.dp)) {
            val stroke = Stroke(width = 10f, cap = StrokeCap.Round)
            // left cable
            val left = Path().apply {
                moveTo(size.width * 0.10f, size.height * 0.30f)
                cubicTo(
                    size.width * 0.28f, size.height * 0.16f,
                    size.width * 0.16f, size.height * 0.52f,
                    size.width * 0.30f, size.height * 0.62f,
                )
            }
            drawPath(left, color = cable, style = stroke)
            // right cable
            val right = Path().apply {
                moveTo(size.width * 0.92f, size.height * 0.26f)
                cubicTo(
                    size.width * 0.72f, size.height * 0.14f,
                    size.width * 0.86f, size.height * 0.50f,
                    size.width * 0.70f, size.height * 0.64f,
                )
            }
            drawPath(right, color = cable, style = stroke)
            // bottom cable across the tail
            val bottom = Path().apply {
                moveTo(size.width * 0.22f, size.height * 0.86f)
                cubicTo(
                    size.width * 0.40f, size.height * 0.78f,
                    size.width * 0.60f, size.height * 0.94f,
                    size.width * 0.80f, size.height * 0.82f,
                )
            }
            drawPath(bottom, color = cable, style = stroke)
            // plug tips
            drawRoundRect(
                color = LightPrimaryContainer,
                topLeft = Offset(size.width * 0.055f, size.height * 0.255f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.06f, size.height * 0.075f),
                cornerRadius = CornerRadius(8f, 8f),
            )
            drawRoundRect(
                color = LightPrimaryContainer,
                topLeft = Offset(size.width * 0.885f, size.height * 0.215f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.06f, size.height * 0.075f),
                cornerRadius = CornerRadius(8f, 8f),
            )
        }

        // Ghost body
        Box(
            Modifier
                .size(width = 150.dp, height = 165.dp)
                .dropShadow()
                .clip(GhostShape),
        ) {
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(52.dp))
                // Eyes + mouth
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 42.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(width = 13.dp, height = 20.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(ghostInk),
                    )
                    Box(
                        Modifier
                            .size(width = 13.dp, height = 20.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(ghostInk),
                    )
                }
                Spacer(Modifier.height(10.dp))
                // worried "o" mouth (slightly left of center)
                Box(
                    Modifier
                        .padding(start = 62.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(ghostInk),
                )
                Spacer(Modifier.height(8.dp))
                // blush
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 30.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(0x26F472B6)),
                    )
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(0x26F472B6)),
                    )
                }
            }
        }
    }
}

/** Soft drop shadow behind the ghost (web drop-shadow-xl). */
private fun Modifier.dropShadow(): Modifier = this.shadow(
    elevation = 12.dp,
    shape = GhostShape,
    clip = false,
    ambientColor = Color(0x1A0B1C30),
    spotColor = Color(0x260B1C30),
)

/**
 * Classic ghost silhouette: half-dome top, straight sides, four-scallop wavy
 * bottom tail.
 */
private object GhostShape : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(
        Path().apply {
            val w = size.width
            val h = size.height
            val dome = w / 2f
            reset()
            moveTo(0f, h)
            lineTo(0f, dome)
            // top dome (half circle)
            cubicTo(0f, dome * 0.25f, dome * 0.2f, 0f, dome, 0f)
            cubicTo(w - dome * 0.2f, 0f, w, dome * 0.25f, w, dome)
            lineTo(w, h)
            // four scallops, bulging UP (tail)
            val scallop = w / 4f
            var x = w
            repeat(4) {
                val nextX = x - scallop
                cubicTo(
                    x - scallop * 0.25f, h - 18f,
                    nextX + scallop * 0.25f, h - 18f,
                    nextX, h,
                )
                x = nextX
            }
            close()
        },
    )
}
