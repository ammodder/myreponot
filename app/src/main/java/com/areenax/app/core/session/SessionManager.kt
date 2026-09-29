package com.areenax.app.core.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.areenax.app.core.nav.NavPersistState
import com.areenax.app.core.network.ApiClient
import com.areenax.app.core.network.TokenProvider
import com.areenax.app.data.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Session + app-preferences store — the native equivalent of the web's
 * zustand persist ("areena-app" {token,user}) + the separate localStorage /
 * sessionStorage keys (SPEC/00 §2a/§3):
 *
 *   localStorage "areena-app"        → DataStore file "areena-app", keys token/user
 *   localStorage "areena-theme"      → key areena-theme ("light"/"dark")
 *   sessionStorage "areena-pending-qr"  → key areena-pending-qr (cleared on logout)
 *   sessionStorage "areena-pending-link"→ key areena-pending-link
 *   sessionStorage "areena_bank_method" → key areena_bank_method
 *   sessionStorage "areena_bank_selection" → key areena_bank_selection (JSON)
 *
 * Everything the web kept ONLY in memory (screen, navStack, unread) lives in
 * AppNavigator / here as StateFlows and is never persisted.
 */
class SessionManager(context: Context) : TokenProvider {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = STORE_NAME)
    private val store = context.dataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ------------------------------------------------------------------ state

    private val _token = MutableStateFlow<String?>(null)
    override val token: StateFlow<String?> = _token.asStateFlow() // A12-03 TokenProvider

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    private val _themeIsDark = MutableStateFlow(false)
    val themeIsDark: StateFlow<Boolean> = _themeIsDark.asStateFlow()

    private val _fcmToken = MutableStateFlow<String?>(null)
    val fcmToken: StateFlow<String?> = _fcmToken.asStateFlow()

    /** True once the persisted token/user have been read from DataStore. */
    private val _hydrated = MutableStateFlow(false)
    val hydrated: StateFlow<Boolean> = _hydrated.asStateFlow()

    // ------------------------------------------------------------- hydration

    init {
        scope.launch {
            val prefs = store.data.first()
            _token.value = prefs[KEY_TOKEN]
            val userJson = prefs[KEY_USER]
            _user.value = userJson?.let { decodeUser(it) }
            _themeIsDark.value = prefs[KEY_THEME] == THEME_DARK
            _fcmToken.value = prefs[KEY_FCM_TOKEN]
            _hydrated.value = true
        }
    }

    private fun decodeUser(json: String): User? = try {
        ApiClient.json.decodeFromString(User.serializer(), json)
    } catch (_: Exception) {
        null
    }

    // ------------------------------------------------------------- mutations

    /** setAuth — persist credentials (the screen jump to `home` is AppNavigator's job). */
    fun setAuth(token: String, user: User) {
        _token.value = token
        _user.value = user
        scope.launch {
            try {
                store.edit {
                    it[KEY_TOKEN] = token
                    it[KEY_USER] = ApiClient.json.encodeToString(User.serializer(), user)
                }
            } catch (_: Exception) {}
        }
    }

    /** setUser — merge refreshed profile (GET /me, PATCH /me, balance syncs). */
    fun setUser(user: User) {
        _user.value = user
        scope.launch {
            try {
                store.edit { it[KEY_USER] = ApiClient.json.encodeToString(User.serializer(), user) }
            } catch (_: Exception) {}
        }
    }

    /** Local balance sync from GET /wallet (web WalletScreen behavior). */
    fun updateBalance(balance: Double) {
        val current = _user.value ?: return
        if (current.balance != balance) setUser(current.copy(balance = balance))
    }

    fun setUnread(n: Int) {
        _unread.value = maxOf(0, n)
    }

    fun setFcmToken(token: String) {
        _fcmToken.value = token
        scope.launch {
            try {
                store.edit { it[KEY_FCM_TOKEN] = token }
            } catch (_: Exception) {}
        }
    }

    // ------------------------------------------------------------ theme (areena-theme)

    /** Persisted exactly like the web: "dark" / "light" string values. */
    fun setTheme(dark: Boolean) {
        _themeIsDark.value = dark
        scope.launch {
            try {
                store.edit { it[KEY_THEME] = if (dark) THEME_DARK else THEME_LIGHT }
            } catch (_: Exception) {}
        }
    }

    // ------------------------------------------------- pending QR (areena-pending-qr)

    /** Stashed QR payload from a pre-auth scan; routed after login, wiped on logout. */
    val pendingQr: Flow<String?> = store.data.map { it[KEY_PENDING_QR] }

    suspend fun setPendingQr(payload: String) {
        store.edit { it[KEY_PENDING_QR] = payload }
    }

    suspend fun clearPendingQr() {
        store.edit { it.remove(KEY_PENDING_QR) }
    }

    // ----------------------------------------------- pending link (areena-pending-link)

    /** Notification deep-link captured before authentication. */
    val pendingLink: Flow<String?> = store.data.map { it[KEY_PENDING_LINK] }

    suspend fun setPendingLink(link: String) {
        store.edit { it[KEY_PENDING_LINK] = link }
    }

    suspend fun clearPendingLink() {
        store.edit { it.remove(KEY_PENDING_LINK) }
    }

    // ------------------------- bank selection memory (areena_bank_method/_selection)

    /**
     * Deposit/Withdraw funding-source memory. Web stores
     * `sessionStorage["areena_bank_selection"] = {method, accountId}` (legacy
     * `areena_bank_method` = method only). Selection is per-visit state, so the
     * getters are suspending reads rather than cached flows.
     */
    suspend fun setBankSelection(method: String, accountId: String?) {
        store.edit {
            it[KEY_BANK_METHOD] = method
            it[KEY_BANK_SELECTION] = ApiClient.json.encodeToString(
                BankSelection.serializer(),
                BankSelection(method = method, accountId = accountId),
            )
        }
    }

    suspend fun bankSelection(): BankSelection? {
        val prefs = store.data.first()
        val json = prefs[KEY_BANK_SELECTION]
        if (json != null) {
            try {
                return ApiClient.json.decodeFromString(BankSelection.serializer(), json)
            } catch (_: Exception) {
                // fall through to legacy key
            }
        }
        val legacy = prefs[KEY_BANK_METHOD] ?: return null
        return BankSelection(method = legacy, accountId = null)
    }

    suspend fun clearBankSelection() {
        store.edit {
            it.remove(KEY_BANK_SELECTION)
            it.remove(KEY_BANK_METHOD)
        }
    }

    // ------------------------------------------------- router persistence (A3-02)

    /**
     * A3-02: the native activity recreates on uiMode/fontScale/locale changes
     * (portrait is pinned, those are not) and the in-memory router used to
     * reset → splash → home. The router now persists a serializable snapshot
     * here (same DataStore file, separate key). Cleared on logout so a fresh
     * device/session never opens on the previous user's last screen.
     */
    suspend fun readNavState(): NavPersistState? {
        val json = store.data.first()[KEY_NAV] ?: return null
        return try {
            ApiClient.json.decodeFromString(NavPersistState.serializer(), json)
        } catch (_: Exception) {
            null
        }
    }

    fun saveNavState(state: NavPersistState) {
        scope.launch {
            try {
                store.edit {
                    it[KEY_NAV] = ApiClient.json.encodeToString(NavPersistState.serializer(), state)
                }
            } catch (_: Exception) {
                // persistence is best-effort; the in-memory router stays correct
            }
        }
    }

    // ---------------------------------------------------------------- logout

    /**
     * logout() — mirrors web store.logout():
     * wipe token/user/unread AND drop any stashed pending-QR so the next
     * session starts clean. NOTE: pending-link is deliberately KEPT
     * (web only clears pending-qr) so a notification tapped while logged out
     * still routes after re-login.
     */
    fun logout() {
        _token.value = null
        _user.value = null
        _unread.value = 0
        scope.launch {
            try {
                store.edit {
                    it.remove(KEY_TOKEN)
                    it.remove(KEY_USER)
                    it.remove(KEY_PENDING_QR)
                    it.remove(KEY_NAV) // A3-02: a fresh session must not open on the old user's screen
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * 401 path (web api.ts): localStorage.removeItem + logout() synchronously.
     * DataStore edit is queued; the in-memory state is cleared immediately so
     * the navigator can jump to login without a visible flash of stale data.
     */
    fun onSessionInvalid() {
        logout()
    }

    companion object {
        const val STORE_NAME = "areena-app"
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"

        private val KEY_TOKEN = stringPreferencesKey("token")
        private val KEY_USER = stringPreferencesKey("user")
        private val KEY_THEME = stringPreferencesKey("areena-theme")
        private val KEY_PENDING_QR = stringPreferencesKey("areena-pending-qr")
        private val KEY_PENDING_LINK = stringPreferencesKey("areena-pending-link")
        private val KEY_BANK_METHOD = stringPreferencesKey("areena_bank_method")
        private val KEY_BANK_SELECTION = stringPreferencesKey("areena_bank_selection")
        private val KEY_NAV = stringPreferencesKey("areena-nav") // A3-02 router snapshot
        private val KEY_FCM_TOKEN = stringPreferencesKey("areena-fcm-token")
    }
}

/** Shape of the web's `sessionStorage["areena_bank_selection"]` value. */
@kotlinx.serialization.Serializable
data class BankSelection(
    val method: String,
    val accountId: String? = null,
)
