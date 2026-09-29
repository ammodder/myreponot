package com.areenax.app.core.session

import com.areenax.app.core.network.Api
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.data.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/**
 * Module-level cache for `/bootstrap` app settings — direct port of the web
 * `src/lib/settings.ts`. Lets any component (e.g. SupportFab, DepositConfirm,
 * Profile social links) read settings without every screen re-fetching.
 */
object SettingsCache {

    @Volatile
    private var cached: AppSettings? = null

    private var inflight: Deferred<AppSettings?>? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    /** HomeScreen (and any bootstrap consumer) pushes settings here. */
    fun cache(settings: AppSettings?) {
        if (settings != null) cached = settings
    }

    fun getCached(): AppSettings? = cached

    /**
     * Cached-first fetch; concurrent callers share one request. Failures
     * return null (callers apply their own defaults, like the web).
     */
    suspend fun get(apiProvider: () -> Api): AppSettings? {
        cached?.let { return it }
        val existing = inflight
        if (existing != null && existing.isActive) return existing.await()
        val deferred = scope.async { fetch(apiProvider) }
        inflight = deferred
        return deferred.await()
    }

    private suspend fun fetch(apiProvider: () -> Api): AppSettings? {
        val result = safeCall { apiProvider().bootstrap() }
        if (result is ApiResult.Success) {
            cache(result.data.settings)
            return result.data.settings
        }
        return null
    }
}
