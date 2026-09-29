package com.areenax.app

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.areenax.app.R
import com.areenax.app.core.nav.AppNavigator
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.network.Api
import com.areenax.app.core.network.ApiClient
import com.areenax.app.core.push.PushManager
import com.areenax.app.core.session.CrashReporter
import com.areenax.app.core.session.SessionManager
import com.areenax.app.core.session.SettingsCache
import com.areenax.app.core.session.UnreadManager
import com.areenax.app.core.ui.ToastController
import com.areenax.app.core.util.ConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Service locator for the single-Activity Compose app — the native equivalent
 * of the web's module singletons (store.ts + api.ts + settings.ts).
 *
 * Everything is a lazy singleton; [env] is THE NavEnv handed to every screen
 * (MainActivity passes it into AppScaffold). Cross-cutting wiring happens in
 * [onCreate] BEFORE any UI can call the API:
 *
 *  - `ApiClient.onUnauthorized` → wipe session + jump to login (web api.ts 401).
 *  - `AppNavigator.isLoggedIn`   → goBack() home/login fallback.
 */
class AreenaxApplication : Application() {

    /** Persisted token/user/theme + pending-QR/link/bank stores (DataStore "areena-app"). */
    val session: SessionManager by lazy { SessionManager(this) }

    /** navigator.onLine replacement (ConnectivityManager NetworkCallback). */
    val connectivity: ConnectivityObserver by lazy { ConnectivityObserver(this) }

    /** The web screen-router state machine (initial screen = "login"). */
    val navigator: AppNavigator by lazy {
        AppNavigator(ScreenKeys.LOGIN).also { it.isLoggedIn = { session.user.value != null } }
    }

    /** Toast system (TOAST_LIMIT 1, 5s auto-dismiss). */
    val toast: ToastController by lazy { ToastController() }

    /** Unread badge manager with the web's 30s countOnly throttle. */
    val unread: UnreadManager by lazy { UnreadManager(session) { api } }

    /** `<app_base_url>/api/` Retrofit instance (x-token + Idempotency-Key wired). */
    val api: Api by lazy { ApiClient.create(baseAppUrl, session) }

    /** The single NavEnv every screen receives. */
    val env: NavEnv by lazy {
        NavEnv(
            navigator = navigator,
            session = session,
            unread = unread,
            toast = toast,
            connectivity = connectivity,
            baseUrl = baseAppUrl,
            appContext = this,
            apiProvider = { api },
        )
    }

    /** res/values/strings.xml → app_base_url (STEP 1 of README deployment). */
    val baseAppUrl: String by lazy { getString(R.string.app_base_url) }

    override fun onCreate() {
        super.onCreate()
        // A13-02: local crash capture — write filesDir/crash/last_crash.txt on
        // any uncaught exception (web-APK distribution has no Play vitals),
        // then continue with the platform's own handler (process dies normally).
        CrashReporter.install(this)
        // HTTP 401 → wipe the local session + route to Login (mirrors web api.ts:
        // localStorage.removeItem + logout → screen "login", stack cleared).
        ApiClient.onUnauthorized = {
            session.onSessionInvalid()
            navigator.logout()
        }
        // A3-02: persist the router on every navigation and restore the last
        // snapshot before the first frame (the splash gate covers the read).
        navigator.persistHook = { snapshot -> session.saveNavState(snapshot) }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = session.readNavState()
                if (snapshot != null) {
                    Handler(Looper.getMainLooper()).post {
                        try {
                            navigator.restoreFrom(snapshot)
                        } catch (_: Exception) {
                            if (session.user.value != null) {
                                navigator.replace(ScreenKeys.HOME)
                            } else {
                                navigator.replace(ScreenKeys.LOGIN)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // fall back to default router state
            }
        }
        // Warm the bootstrap settings cache (SupportFab/WhatsAppFab read it on tap;
        // failures are silent — callers apply defaults like the web).
        CoroutineScope(Dispatchers.IO).launch {
            SettingsCache.get { api }
        }

        // Initialize FCM push notification channel and sync push token on user session
        PushManager.createNotificationChannel(this)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                session.user.collect { user ->
                    if (user != null) {
                        PushManager.syncFcmToken(api, session)
                    }
                }
            } catch (_: Throwable) {
                // fail-safe
            }
        }
    }
}
