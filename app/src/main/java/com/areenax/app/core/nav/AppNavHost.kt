package com.areenax.app.core.nav

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CancellationException

/*
 * AppNavHost — the screen REGISTRY + transition layer, the native port of the
 * AppShell.tsx REGISTRY + AnimatePresence block (SPEC/00 §2b/§2c, §1-SCREENS.md).
 *
 * The router is AppNavigator (a pure state machine mirroring web store.ts):
 * AppNavHost simply projects `navigator.currentScreen` onto a composable and
 * animates the swap. There are NO Nav-Controller route strings — routes ARE
 * the 46 ScreenKeys strings, and params travel inside the navigator exactly
 * like the web ({screen, params} pairs), staying reachable via `env.params`.
 *
 * Transition language (SPEC/04 §6 motion: web crossfades with an 18px x-slide
 * in 180ms easeOut): native keeps the fade-dominant crossfade but gives it
 * real gesture direction — push slides in from the right, pop from the left,
 * easeOut — per the native punch list. Q10 (owner): 300 ms (industry standard
 * 250–350 ms), replacing the earlier 260 ms.
 *
 * System back (A8-01): PredictiveBackHandler — the Android 13+ system preview
 * animates during the gesture; on commit → goBack(), on cancel → nothing.
 * Falls back to the classic back dispatch on pre-T devices. The two stack
 * roots (`home`, `login`) are NOT intercepted (activity finish / nothing), and
 * back is disabled while the offline overlay covers the UI (A3-04).
 */

/** One immutable projection of the navigator — the AnimatedContent key. */
@Immutable
private data class NavState(
    val screen: String,
    val params: Map<String, Any?>,
    val generation: Int,
)

/** Screens where system back is NOT intercepted (activity handles it). */
private val BACK_ROOT_SCREENS = setOf(ScreenKeys.HOME, ScreenKeys.LOGIN)

/** Motion constants — SPEC/04 §6, adapted per the native punch list. Q10: 300ms. */
private const val SCREEN_TRANSITION_MS = 300
private val ScreenEasing = CubicBezierEasing(0f, 0f, 0.2f, 1f) // easeOut

@Composable
fun AppNavHost(env: NavEnv, modifier: Modifier = Modifier) {
    val navigator = env.navigator
    val isOnline by env.connectivity.isOnline.collectAsState()

    // A8-01: predictive back — commit → goBack(), cancel → nothing; disabled on
    // the two roots (system handles them) and while the offline overlay shows
    // (A3-04: the covered screen must not be poppable).
    PredictiveBackHandler(
        enabled = navigator.currentScreen !in BACK_ROOT_SCREENS && isOnline,
    ) { progress ->
        try {
            progress.collect { } // per-event progress hook (kept minimal)
            navigator.goBack()
        } catch (_: CancellationException) {
            // gesture cancelled — no navigation (cancellation IS the cancel
            // signal per the androidx PredictiveBackHandler contract)
        }
    }

    AnimatedContent(
        targetState = NavState(
            screen = navigator.currentScreen,
            params = navigator.currentParams,
            generation = navigator.generation,
        ),
        transitionSpec = {
            if (navigator.lastWasPush) {
                // push: enter from the right, exit toward the left, both fading
                (slideInHorizontally(ScreenSlideSpec) { it / 4 } + fadeIn(transitionTween)) togetherWith
                    (slideOutHorizontally(ScreenSlideSpec) { -it / 4 } + fadeOut(transitionTween))
            } else {
                // pop: enter from the left, exit toward the right
                (slideInHorizontally(ScreenSlideSpec) { -it / 4 } + fadeIn(transitionTween)) togetherWith
                    (slideOutHorizontally(ScreenSlideSpec) { it / 4 } + fadeOut(transitionTween))
            }
        },
        label = "screenTransition",
        modifier = modifier.fillMaxSize(),
    ) { state ->
        ScreenForKey(state.screen, env)
    }
}

/** Slide fraction (1/4 of the width) keeps the web's fade-dominant feel. */
private val ScreenSlideSpec: FiniteAnimationSpec<IntOffset> =
    tween(SCREEN_TRANSITION_MS, easing = ScreenEasing)

private val transitionTween = tween<Float>(SCREEN_TRANSITION_MS, easing = ScreenEasing)

/**
 * The REGISTRY — exactly one composable per ScreenKeys entry (fallback =
 * login, mirroring the web `REGISTRY[activeScreen] ?? LoginScreen`).
 * Screens receive [env]; nav params stay readable via `env.params`.
 */
@Composable
private fun ScreenForKey(screen: String, env: NavEnv) {
    when (screen) {
        // ---- A. Auth ---------------------------------------------------------
        ScreenKeys.LOGIN -> com.areenax.app.ui.screens.auth.LoginScreen(env)
        ScreenKeys.SIGNUP -> com.areenax.app.ui.screens.auth.SignupScreen(env)
        ScreenKeys.SIGNUP_STEP1 -> com.areenax.app.ui.screens.auth.SignupStep1Screen(env)
        ScreenKeys.SIGNUP_STEP2 -> com.areenax.app.ui.screens.auth.SignupStep2Screen(env)
        ScreenKeys.SIGNUP_STEP3 -> com.areenax.app.ui.screens.auth.SignupStep3Screen(env)

        // ---- B. Main ---------------------------------------------------------
        ScreenKeys.HOME -> com.areenax.app.ui.screens.main.HomeScreen(env)
        ScreenKeys.TOURNAMENTS -> com.areenax.app.ui.screens.main.TournamentsScreen(env)
        ScreenKeys.TOURNAMENT_DETAILS -> com.areenax.app.ui.screens.main.TournamentDetailsScreen(env)
        ScreenKeys.MY_TOURNAMENT -> com.areenax.app.ui.screens.main.MyTournamentScreen(env)
        ScreenKeys.RESULTS -> com.areenax.app.ui.screens.main.ResultsScreen(env)

        // ---- C. Wallet -------------------------------------------------------
        ScreenKeys.WALLET -> com.areenax.app.ui.screens.wallet.WalletScreen(env)
        ScreenKeys.DEPOSIT -> com.areenax.app.ui.screens.wallet.DepositScreen(env)
        ScreenKeys.DEPOSIT_CONFIRM -> com.areenax.app.ui.screens.wallet.DepositConfirmScreen(env)
        ScreenKeys.DEPOSIT_SUCCESS -> com.areenax.app.ui.screens.wallet.DepositSuccessScreen(env)
        ScreenKeys.WITHDRAW -> com.areenax.app.ui.screens.wallet.WithdrawScreen(env)
        ScreenKeys.CONFIRM_WITHDRAW -> com.areenax.app.ui.screens.wallet.ConfirmWithdrawScreen(env)
        ScreenKeys.WITHDRAW_SUCCESS -> com.areenax.app.ui.screens.wallet.WithdrawSuccessScreen(env)
        ScreenKeys.TRANSFER_MONEY -> com.areenax.app.ui.screens.wallet.TransferScreen(env)
        ScreenKeys.TRANSFER_SUCCESS -> com.areenax.app.ui.screens.wallet.TransferSuccessScreen(env)
        ScreenKeys.SELECT_BANK -> com.areenax.app.ui.screens.wallet.SelectBankScreen(env)

        // ---- D. Social -------------------------------------------------------
        ScreenKeys.MY_TEAM -> com.areenax.app.ui.screens.social.MyTeamScreen(env)
        ScreenKeys.TEAM_CREATION -> com.areenax.app.ui.screens.social.TeamCreationScreen(env)
        ScreenKeys.TEAM_CREATION_DONE -> com.areenax.app.ui.screens.social.TeamCreationDoneScreen(env)
        ScreenKeys.FRIENDS -> com.areenax.app.ui.screens.social.FriendsScreen(env)
        ScreenKeys.CHAT -> com.areenax.app.ui.screens.social.ChatScreen(env)
        ScreenKeys.REFER_EARN -> com.areenax.app.ui.screens.social.ReferEarnScreen(env)
        ScreenKeys.TASKS -> com.areenax.app.ui.screens.social.TasksScreen(env)
        ScreenKeys.NOTIFICATIONS -> com.areenax.app.ui.screens.social.NotificationsScreen(env)

        // ---- E. Profile (+ admin) -------------------------------------------
        ScreenKeys.PROFILE -> com.areenax.app.ui.screens.profile.ProfileScreen(env)
        ScreenKeys.EDIT_PROFILE -> com.areenax.app.ui.screens.profile.EditProfileScreen(env)
        ScreenKeys.MY_STATS -> com.areenax.app.ui.screens.profile.MyStatsScreen(env)
        ScreenKeys.ACHIEVEMENTS -> com.areenax.app.ui.screens.profile.AchievementsScreen(env)
        ScreenKeys.LEADERBOARD -> com.areenax.app.ui.screens.profile.LeaderboardScreen(env)
        ScreenKeys.BIND_ACCOUNT -> com.areenax.app.ui.screens.profile.BindAccountScreen(env)
        ScreenKeys.BIND_ACCOUNT_SUCCESS -> com.areenax.app.ui.screens.profile.BindAccountSuccessScreen(env)
        ScreenKeys.ADMIN_PANEL -> com.areenax.app.ui.screens.profile.AdminPanelScreen(env)
        ScreenKeys.ADMIN_DASHBOARD -> com.areenax.app.ui.screens.profile.AdminTournamentsScreen(env)

        // ---- F. Host ---------------------------------------------------------
        ScreenKeys.HOST_TOURNAMENT -> com.areenax.app.ui.screens.host.HostTournamentScreen(env)
        ScreenKeys.HOST_TOURNAMENT_CREATION -> com.areenax.app.ui.screens.host.HostCreationScreen(env)
        ScreenKeys.HOST_TOURNAMENT_SUCCESS -> com.areenax.app.ui.screens.host.HostSuccessScreen(env)
        ScreenKeys.HOST_TOURNAMENT_CARD -> com.areenax.app.ui.screens.host.HostCardsScreen(env)
        ScreenKeys.HOST_TOURNAMENT_DETAILS -> com.areenax.app.ui.screens.host.HostDetailsScreen(env)

        // ---- G. Info ---------------------------------------------------------
        ScreenKeys.ABOUT -> com.areenax.app.ui.screens.info.AboutScreen(env)
        ScreenKeys.TERMS -> com.areenax.app.ui.screens.info.TermsScreen(env)
        ScreenKeys.PRIVACY -> com.areenax.app.ui.screens.info.PrivacyScreen(env)
        ScreenKeys.OFFLINE -> com.areenax.app.ui.screens.info.OfflineScreen(env)

        // Unknown key → login (web REGISTRY fallback).
        else -> com.areenax.app.ui.screens.auth.LoginScreen(env)
    }
}
