package com.areenax.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.areenax.app.R
import com.areenax.app.core.nav.AppNavHost
import com.areenax.app.core.nav.LocalNavEnv
import com.areenax.app.core.nav.LocalNavigator
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.nav.parseDeepLink
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.theme.DarkPrimary
import com.areenax.app.core.theme.DarkSurfaceDim
import com.areenax.app.core.util.QrKind
import com.areenax.app.core.util.parseQrPayload
import com.areenax.app.ui.screens.info.OfflineScreen
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.first

/*
 * AppScaffold — port of src/components/AppShell.tsx (SPEC/00 §2c, §03 header).
 * The single top-level composable the MainActivity hosts:
 *
 *  - SPLASH while hydrating (DataStore read) or validating a restored token
 *    (`GET /me`) — brand logo (7rem) + small primary spinner on the canvas,
 *    exactly the web `!hydrated || (token && !checked)` gate.
 *  - SESSION RESTORE: after hydration, a persisted token → GET /me → setUser;
 *    if the restored screen is an auth screen → replace(home). 401 auto-wipes
 *    via ApiClient.onUnauthorized.
 *  - PENDING QR / LINK routing once a user exists (web processPendingQr /
 *    processPendingLink): user QR → friends {qrUser}, team QR → myTeam
 *    {qrTeam}; deep links via parseDeepLink.
 *  - OFFLINE overlay (A3-04/A5-06): ConnectivityObserver loss → a blocking
 *    OfflineScreen OVERLAY (the covered screen stays composed; forms survive);
 *    it disappears by itself when connectivity returns.
 *  - AUTH GATE: an anonymous visitor can only sit on PUBLIC_SCREENS.
 *  - BottomNav ONLY when logged-in && currentScreen ∈ NAV_SCREENS (6 roots).
 *  - Unread badge refresh on every nav destination change (UnreadManager
 *    applies the web's exact 30s throttle).
 *  - Dark canvas glow: `#060608` + fixed top radial glow — screens paint
 *    transparent roots so the wallpaper is continuous (SPEC/04 §5).
 *  - ToastHost on top (web ui/toaster).
 *
 * FAB placement (SupportFab / WhatsAppFab) is owned by the SCREENS, exactly
 * like the web — AppShell never adds FABs.
 * `adminDashboard` renders as a normal screen route (the web's out-of-frame
 * desktop console is a stub here — AdminTournamentsScreen).
 */

/** Web app-wide dark canvas `#060608` + radial top glow rgba(47,107,255,0.07). */
private val DarkCanvas = DarkSurfaceDim
private const val CANVAS_GLOW_ALPHA = 0x12 // 0.07 * 255 ≈ 18

@Composable
fun AppScaffold(env: NavEnv, modifier: Modifier = Modifier) {
    val navigator = env.navigator
    val extended = areenaColors()
    val user by env.session.user.collectAsState()
    val token by env.session.token.collectAsState()
    val hydrated by env.session.hydrated.collectAsState()
    val isOnline by env.connectivity.isOnline.collectAsState()

    // Web `checked` — true once the restored-token validation finished.
    var checked by remember { mutableStateOf(false) }

    // ---- Connectivity callbacks (web online/offline listeners) ----------------
    DisposableEffect(Unit) {
        env.connectivity.start()
        onDispose { env.connectivity.stop() }
    }

    // ---- Session restore (AppShell validate-session effect) -------------------
    LaunchedEffect(hydrated, token) {
        if (!hydrated) return@LaunchedEffect
        if (token != null) {
            val result = safeCall { env.api.me() }
            if (result is ApiResult.Success) {
                env.session.setUser(result.data.user)
                // Restored a session while sitting on an auth screen → home.
                if (navigator.currentScreen in ScreenKeys.AUTH_SCREENS) {
                    navigator.replace(ScreenKeys.HOME)
                }
            }
            // Failure (401) auto-logged-out via ApiClient.onUnauthorized.
        }
        checked = true
    }

    // ---- Pending QR / notification-link routing (fires when a user appears) ---
    LaunchedEffect(user?.id) {
        if (user == null) return@LaunchedEffect
        try {
            processPendingQr(env)
            processPendingLink(env)
        } catch (_: Exception) {
            // fail-safe
        }
    }

    // ---- Auth gate: anonymous users only on PUBLIC_SCREENS ---------------------
    LaunchedEffect(user) {
        navigator.enforceAuthGate()
    }

    // ---- OFFLINE → blocking overlay (A3-04/A5-06) -----------------------------
    // Was: navigate(offline) push on loss + goBack() on regain — that DISPOSED
    // the screen beneath (every `remember` form field was lost on a transient
    // blip) and BackHandler could pop the gate while still offline. Now the
    // current screen stays composed under a full-size overlay rendered in the
    // layout below; it disappears by itself when connectivity returns.

    // ---- Unread badge refresh per destination change (30s-throttled) -----------
    LaunchedEffect(navigator.generation) {
        if (user != null) env.unread.refresh()
    }

    // ---- Render ----------------------------------------------------------------
    val canvasColor = if (extended.isDark) DarkCanvas else MaterialTheme.colorScheme.background

    // Splash gate (web `!hydrated || (token && !checked)`): nothing renders —
    // not even the NavHost — until hydration + session validation complete.
    if (!hydrated || (token != null && !checked)) {
        Box(
            modifier
                .fillMaxSize()
                .background(canvasColor),
        ) {
            if (extended.isDark) CanvasGlow()
            SplashScreenContent()
        }
        return
    }

    Box(
        modifier
            .fillMaxSize()
            .background(canvasColor),
    ) {
        if (extended.isDark) CanvasGlow()

        // A8-02: one press language app-wide — M3 pressables render a scale
        // press instead of a ripple (web parity), provided via LocalIndication.
        CompositionLocalProvider(
            LocalNavEnv provides env,
            LocalNavigator provides env.navigator,
            LocalIndication provides PressScaleIndication,
        ) {
            Column(Modifier.fillMaxSize()) {
                AppNavHost(env, Modifier.weight(1f))
                // showNav = user && NAV_SCREENS.has(screen)
                if (user != null && navigator.currentScreen in ScreenKeys.NAV_SCREENS) {
                    BottomNav()
                }
            }

            // Blocking offline overlay (A3-04/A5-06, Attacker-retest hardening):
            // covers everything incl. the bottom nav; the covered screen stays
            // composed underneath. The overlay CONSUMES all pointer input so
            // taps cannot fall through to the covered screen.
            if (!isOnline) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(2f)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        },
                ) {
                    OfflineScreen(env, asOverlay = true)
                }
            }

            // Single toast layer above everything (web ui/toaster, z-100) —
            // ABOVE the offline overlay so "Still offline" remains visible.
            Box(Modifier.zIndex(3f)) {
                ToastHost(controller = env.toast, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * Dark canvas glow — `radial-gradient(120% 55% at 50% 0%, rgba(47,107,255,.07),
 * transparent 60%)` over `#060608` (SPEC/04 §5). Painted ONCE behind the
 * NavHost; screens paint transparent roots so the wallpaper is continuous.
 */
@Composable
private fun CanvasGlow() {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            DarkPrimary.copy(alpha = CANVAS_GLOW_ALPHA / 255f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.width * 1.2f,
                    ),
                )
            },
    )
}

/** Hydration splash — brand logo (h-[7rem]) + 28dp primary spinner on the canvas. */
@Composable
private fun SplashScreenContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.areenax_logo),
                contentDescription = "AREENAX",
                contentScale = ContentScale.FillHeight,
                modifier = Modifier.height(112.dp), // h-[7rem]
            )
            Spacer(Modifier.height(40.dp)) // mt-10
            AreenaxSpinner(size = 28, strokeWidth = 2) // w-7 h-7 border-2
        }
    }
}

/**
 * Web processPendingQr (AppShell.tsx): parse the stashed payload; invalid →
 * discard; no user → KEEP stashed (a visitor finishing auth gets routed);
 * user QR → friends {qrUser}, team QR → myTeam {qrTeam}.
 */
private suspend fun processPendingQr(env: NavEnv) {
    val raw = env.session.pendingQr.first()
    if (raw.isNullOrBlank()) return
    val payload = parseQrPayload(raw)
    if (payload == null) {
        env.session.clearPendingQr()
        return
    }
    if (env.session.user.value == null) return
    env.session.clearPendingQr()
    if (payload.kind == QrKind.USER) {
        env.navigate(ScreenKeys.FRIENDS, mapOf("qrUser" to payload.id))
    } else {
        env.navigate(ScreenKeys.MY_TEAM, mapOf("qrTeam" to payload.id))
    }
}

/**
 * Web processPendingLink (push-client.ts): the key is consumed FIRST, then the
 * target routes only when a user exists; unparseable links are dropped.
 */
private suspend fun processPendingLink(env: NavEnv) {
    val raw = env.session.pendingLink.first()
    if (raw.isNullOrBlank()) return
    env.session.clearPendingLink()
    val target = parseDeepLink(raw) ?: return
    if (env.session.user.value == null) return
    env.navigate(target.screen, target.params)
}
