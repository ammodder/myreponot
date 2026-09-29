package com.areenax.app.core.nav

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.areenax.app.core.network.Api
import com.areenax.app.core.session.SessionManager
import com.areenax.app.core.session.UnreadManager
import com.areenax.app.core.util.ConnectivityObserver
import com.areenax.app.data.User
import com.areenax.app.core.ui.ToastController
import com.areenax.app.core.ui.ToastVariant

/**
 * The single object handed to EVERY screen composable — the native equivalent
 * of the web's `useAppStore()` + `useToast()` + `api` imports.
 *
 * ```kotlin
 * @Composable
 * fun WalletScreen(env: NavEnv) {
 *     val user = env.user
 *     LaunchedEffect(Unit) {
 *         safeCall { env.api.wallet() }.onSuccess { ... }
 *     }
 *     Button(onClick = { env.navigate("deposit", mapOf("amount" to 500)) }) { ... }
 * }
 * ```
 */
class NavEnv(
    val navigator: AppNavigator,
    val session: SessionManager,
    val unread: UnreadManager,
    val toast: ToastController,
    val connectivity: ConnectivityObserver,
    /** Base URL from the app_base_url resource (QR deep links, share links). */
    val baseUrl: String,
    /** Application context for clipboard / share intents. */
    val appContext: Context,
    private val apiProvider: () -> Api,
) {
    /** The Retrofit API — lazily resolved so ServiceLocator wiring stays simple. */
    val api: Api by lazy(apiProvider)

    // ------------------------------------------------------------ shortcuts

    /** Params of the CURRENT screen (`env.params["tournamentId"]`). */
    val params: Map<String, Any?> get() = navigator.currentParams

    /** Current user snapshot (collect `session.user` for reactivity). */
    val user: User? get() = session.user.value

    /** Current token snapshot. */
    val token: String? get() = session.token.value

    /** Application context (clipboard, share intents, resource reads). */
    val context: Context get() = appContext

    fun navigate(screen: String, params: Map<String, Any?> = emptyMap()) =
        navigator.navigate(screen, params)

    /** replace() clears the stack — success screens / setAuth targets. */
    fun replace(screen: String, params: Map<String, Any?> = emptyMap()) =
        navigator.replace(screen, params)

    fun goBack() = navigator.goBack()

    /** Login/register/guest success: persist credentials + jump to home. */
    fun setAuth(token: String, user: User) {
        session.setAuth(token, user)
        navigator.setAuth()
    }

    /** Best-effort POST /auth/logout is the SCREEN's job; this is the local wipe. */
    fun logout() {
        session.logout()
        navigator.logout()
    }

    // ---------------------------------------------------------------- toast

    /** Quick toast: `env.toast("Deposit failed", ToastVariant.Destructive)`. */
    fun toast(message: String, variant: ToastVariant = ToastVariant.Default) {
        toast.show(title = message, description = null, variant = variant)
    }
}

/** CompositionLocal providing [NavEnv] to every screen. */
val LocalNavEnv = compositionLocalOf<NavEnv> {
    error("LocalNavEnv not provided — AppScaffold must supply it")
}

/** The screen router — also exposed standalone for AppBar back behavior. */
val LocalNavigator = compositionLocalOf<AppNavigator> {
    error("LocalNavigator not provided — AppScaffold must supply it")
}

/** Screen-friendly accessor. */
@Composable
fun navEnv(): NavEnv = LocalNavEnv.current
