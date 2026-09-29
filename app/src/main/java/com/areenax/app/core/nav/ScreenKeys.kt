package com.areenax.app.core.nav

/*
 * The 46 screen keys — EXACT string constants from the web `Screen` union
 * (src/lib/types.ts). Routes ARE these keys; AppNavigator.currentScreen holds
 * one of them and AppNavHost maps each to its composable.
 */
object ScreenKeys {
    // A. Auth
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val SIGNUP_STEP1 = "signupStep1"
    const val SIGNUP_STEP2 = "signupStep2"
    const val SIGNUP_STEP3 = "signupStep3"

    // B. Main
    const val HOME = "home"
    const val TOURNAMENTS = "tournaments"
    const val TOURNAMENT_DETAILS = "tournamentDetails"
    const val MY_TOURNAMENT = "myTournament"
    const val RESULTS = "results"

    // C. Wallet
    const val WALLET = "wallet"
    const val DEPOSIT = "deposit"
    const val DEPOSIT_CONFIRM = "depositConfirm"
    const val DEPOSIT_SUCCESS = "depositSuccess"
    const val WITHDRAW = "withdraw"
    const val CONFIRM_WITHDRAW = "confirmWithdraw"
    const val WITHDRAW_SUCCESS = "withdrawSuccess"
    const val TRANSFER_MONEY = "transferMoney"
    const val TRANSFER_SUCCESS = "transferSuccess"
    const val SELECT_BANK = "selectBank"

    // D. Social
    const val MY_TEAM = "myTeam"
    const val TEAM_CREATION = "teamCreation"
    const val TEAM_CREATION_DONE = "teamCreationDone"
    const val FRIENDS = "friends"
    const val CHAT = "chat"
    const val REFER_EARN = "referEarn"
    const val TASKS = "tasks"
    const val NOTIFICATIONS = "notifications"

    // E. Profile (+ admin)
    const val PROFILE = "profile"
    const val EDIT_PROFILE = "editProfile"
    const val MY_STATS = "myStats"
    const val ACHIEVEMENTS = "achievements"
    const val LEADERBOARD = "leaderboard"
    const val BIND_ACCOUNT = "bindAccount"
    const val BIND_ACCOUNT_SUCCESS = "bindAccountSuccess"
    const val ADMIN_PANEL = "adminPanel"
    const val ADMIN_DASHBOARD = "adminDashboard"

    // F. Host
    const val HOST_TOURNAMENT = "hostTournament"
    const val HOST_TOURNAMENT_CREATION = "hostTournamentCreation"
    const val HOST_TOURNAMENT_SUCCESS = "hostTournamentSuccess"
    const val HOST_TOURNAMENT_CARD = "hostTournamentCard"
    const val HOST_TOURNAMENT_DETAILS = "hostTournamentDetails"

    // G. Info
    const val ABOUT = "about"
    const val TERMS = "terms"
    const val PRIVACY = "privacy"
    const val OFFLINE = "offline"

    /** All 46 keys (diagnostics/tests/guide). */
    val ALL: List<String> = listOf(
        LOGIN, SIGNUP, SIGNUP_STEP1, SIGNUP_STEP2, SIGNUP_STEP3,
        HOME, TOURNAMENTS, TOURNAMENT_DETAILS, MY_TOURNAMENT, RESULTS,
        WALLET, DEPOSIT, DEPOSIT_CONFIRM, DEPOSIT_SUCCESS,
        WITHDRAW, CONFIRM_WITHDRAW, WITHDRAW_SUCCESS,
        TRANSFER_MONEY, TRANSFER_SUCCESS, SELECT_BANK,
        MY_TEAM, TEAM_CREATION, TEAM_CREATION_DONE, FRIENDS, CHAT,
        REFER_EARN, TASKS, NOTIFICATIONS,
        PROFILE, EDIT_PROFILE, MY_STATS, ACHIEVEMENTS, LEADERBOARD,
        BIND_ACCOUNT, BIND_ACCOUNT_SUCCESS, ADMIN_PANEL, ADMIN_DASHBOARD,
        HOST_TOURNAMENT, HOST_TOURNAMENT_CREATION, HOST_TOURNAMENT_SUCCESS,
        HOST_TOURNAMENT_CARD, HOST_TOURNAMENT_DETAILS,
        ABOUT, TERMS, PRIVACY, OFFLINE,
    )

    /** Bottom-nav roots — AppShell shows BottomNav ONLY on these (when logged in). */
    val NAV_SCREENS: Set<String> = setOf(
        HOME, TOURNAMENTS, MY_TOURNAMENT, FRIENDS, WALLET, PROFILE,
    )

    /** Screens reachable without an account (pre-signup): auth flow + legal pages. */
    val PUBLIC_SCREENS: Set<String> = setOf(
        LOGIN, SIGNUP, SIGNUP_STEP1, SIGNUP_STEP2, SIGNUP_STEP3, TERMS, PRIVACY,
    )

    /** Auth-flow screens — replaced with `home` when a session is restored. */
    val AUTH_SCREENS: Set<String> = setOf(
        LOGIN, SIGNUP, SIGNUP_STEP1, SIGNUP_STEP2, SIGNUP_STEP3,
    )
}

data class DeepLinkTarget(val screen: String, val params: Map<String, Any?>)

/**
 * parseDeepLink — notification/link deep-link grammar
 * (`tournament:<id>` | `host:<id>` | wallet | myTournament | notifications |
 * home | myStats | results), ported from src/lib/push-client.ts.
 */
fun parseDeepLink(raw: String?): DeepLinkTarget? {
    if (raw.isNullOrBlank()) return null
    val value = try {
        java.net.URLDecoder.decode(raw.trim(), "UTF-8")
    } catch (_: Exception) {
        raw.trim()
    }
    if (value.isEmpty()) return null
    val head = value.substringBefore(':')
    val id = value.substringAfter(':', missingDelimiterValue = "")
    return when (head) {
        "tournament" -> if (id.isNotEmpty()) {
            DeepLinkTarget(ScreenKeys.TOURNAMENT_DETAILS, mapOf("tournamentId" to id))
        } else {
            DeepLinkTarget(ScreenKeys.MY_TOURNAMENT, emptyMap())
        }
        "host" -> if (id.isNotEmpty()) {
            DeepLinkTarget(ScreenKeys.HOST_TOURNAMENT_DETAILS, mapOf("tournamentId" to id))
        } else {
            null
        }
        "wallet", "myTournament", "notifications", "home", "myStats", "results" ->
            DeepLinkTarget(head, emptyMap())
        else -> null
    }
}
