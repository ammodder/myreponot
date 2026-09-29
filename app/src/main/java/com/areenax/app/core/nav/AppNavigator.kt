package com.areenax.app.core.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The web screen-router (src/lib/store.ts) as a plain Compose-state class.
 *
 * Semantics (1:1):
 *   navigate(screen, params)  push; stack capped at 25 (slice(-25))
 *   replace(screen, params)   clear stack (success screens / setAuth)
 *   goBack()                  pop; empty stack → home if logged in else login
 *   setAuth()                 home + clear stack
 *   logout()                  login + clear stack (session wipe is SessionManager's)
 *
 * Screens/params are SESSION-SCOPED in the web (never persisted — only
 * token/user are). A3-02: on NATIVE the activity recreates on uiMode/fontScale
 * changes (portrait is pinned, those are not), which used to reset the whole
 * router → splash → home. The router therefore persists a serializable
 * projection of itself via [persistHook] (DataStore, wired by
 * AreenaxApplication) and restores it with [restoreFrom] before the first
 * frame. Only string params survive persistence (all current call sites are
 * strings); non-string params would be dropped on recreation.
 */
class AppNavigator(initialScreen: String = ScreenKeys.LOGIN) {

    /** One back-stack entry = the screen we came FROM. */
    data class NavEntry(val screen: String, val params: Map<String, Any?>)

    var currentScreen: String by mutableStateOf(initialScreen)
        private set

    var currentParams: Map<String, Any?> by mutableStateOf(emptyMap())
        private set

    private val _stack = mutableStateListOf<NavEntry>()
    val navStack: List<NavEntry> get() = _stack

    /** Bumps on every navigation action — AppNavHost keys its transitions on it. */
    var generation: Int by mutableIntStateOf(0)
        private set

    /** Last action direction — drives push (slide-from-right) vs pop (slide-from-left). */
    var lastWasPush: Boolean by mutableStateOf(true)
        private set

    /**
     * Session flags the router needs for goBack fallbacks; wired by the
     * ServiceLocator so the navigator stays a pure state machine.
     */
    var isLoggedIn: () -> Boolean = { false }

    /** A3-02: fire-and-forget persistence sink (wired by AreenaxApplication). */
    var persistHook: ((NavPersistState) -> Unit)? = null

    private var restoring = false

    /** Serializable projection pushed to [persistHook] after every mutation. */
    private fun snapshot(): NavPersistState = NavPersistState(
        current = currentScreen,
        params = currentParams.filterValues { it is String }.mapValues { it.value as String },
        stack = navStack.map { entry ->
            NavPersistEntry(
                screen = entry.screen,
                params = entry.params.filterValues { it is String }.mapValues { it.value as String },
            )
        },
    )

    private fun notifyPersist() {
        if (restoring) return
        persistHook?.invoke(snapshot())
    }

    /** A3-02: apply a persisted router state (does NOT re-persist). Unknown
     *  keys fall back through the registry; enforceAuthGate re-runs in AppShell
     *  on the next user emission. */
    fun restoreFrom(state: NavPersistState?) {
        if (state == null || state.current.isBlank()) return
        restoring = true
        try {
            currentScreen = state.current
            currentParams = state.params
            _stack.clear()
            state.stack.forEach { entry -> _stack.add(NavEntry(entry.screen, entry.params)) }
            lastWasPush = false
            generation++
        } catch (_: Exception) {
            currentScreen = if (isLoggedIn()) ScreenKeys.HOME else ScreenKeys.LOGIN
            currentParams = emptyMap()
            _stack.clear()
        } finally {
            restoring = false
        }
    }

    fun navigate(screen: String, params: Map<String, Any?> = emptyMap()) {
        // Push the CURRENT screen (if it is a real screen) as the back target,
        // mirroring the web which pushes {previous screen, previous params}.
        _stack.add(NavEntry(currentScreen, currentParams))
        // Stack cap 25 — `[...stack, entry].slice(-25)`.
        while (_stack.size > MAX_STACK) {
            _stack.removeAt(0)
        }
        currentScreen = screen
        currentParams = params
        lastWasPush = true
        generation++
        notifyPersist()
    }

    fun replace(screen: String, params: Map<String, Any?> = emptyMap()) {
        _stack.clear()
        currentScreen = screen
        currentParams = params
        lastWasPush = true
        generation++
        notifyPersist()
    }

    fun goBack() {
        val stack = _stack
        if (stack.isEmpty()) {
            currentScreen = if (isLoggedIn()) ScreenKeys.HOME else ScreenKeys.LOGIN
            currentParams = emptyMap()
        } else {
            val prev = stack.removeAt(stack.size - 1)
            currentScreen = prev.screen
            currentParams = prev.params
        }
        lastWasPush = false
        generation++
        notifyPersist()
    }

    /** Login / register / guest success → home + cleared stack. */
    fun setAuth() {
        currentScreen = ScreenKeys.HOME
        currentParams = emptyMap()
        _stack.clear()
        lastWasPush = true
        generation++
        notifyPersist()
    }

    /** logout() / 401 → login + cleared stack (mirrors web store.logout()). */
    fun logout() {
        currentScreen = ScreenKeys.LOGIN
        currentParams = emptyMap()
        _stack.clear()
        lastWasPush = true
        generation++
        notifyPersist()
    }

    /**
     * Web `AppShell` force-correction: an unauthenticated visitor can only be
     * on PUBLIC_SCREENS; anything else snaps back to login. Call after session
     * restore / logout.
     */
    fun enforceAuthGate(publicScreens: Set<String> = ScreenKeys.PUBLIC_SCREENS) {
        if (!isLoggedIn() && currentScreen !in publicScreens) {
            currentScreen = ScreenKeys.LOGIN
            currentParams = emptyMap()
            _stack.clear()
        }
    }

    companion object {
        const val MAX_STACK = 25
    }
}

/** A3-02: one persisted stack entry (string params only). */
@kotlinx.serialization.Serializable
data class NavPersistEntry(
    val screen: String,
    val params: Map<String, String> = emptyMap(),
)

/** A3-02: serializable router snapshot stored in DataStore next to the token. */
@kotlinx.serialization.Serializable
data class NavPersistState(
    val current: String,
    val params: Map<String, String> = emptyMap(),
    val stack: List<NavPersistEntry> = emptyList(),
)
