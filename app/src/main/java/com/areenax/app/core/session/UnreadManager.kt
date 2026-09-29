package com.areenax.app.core.session

import com.areenax.app.core.network.Api
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Unread badge manager (SPEC/00 §2e) — the web BellButton fires
 * `GET /notifications?countOnly=1` on every mount unless the last successful
 * check was < 30 s ago (module-level `lastUnreadCheck`). Native keeps ONE
 * singleton with the same throttle; call [refresh] from the AppBar bell on
 * mount, after FCM receipt, and after any mark-read/delete.
 */
class UnreadManager(
    private val session: SessionManager,
    private val apiProvider: () -> Api,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastSuccessfulCheck: Long = 0L

    /** Badge count — owned by SessionManager (web store.unread), session-scoped. */
    val unread: StateFlow<Int> get() = session.unread

    /** Milliseconds — web uses a 30s throttle. */
    var throttleMs: Long = 30_000L

    /**
     * Refresh the unread badge. Silent on failure (the web never toasts this
     * poll). When [force] is false the call is skipped inside the throttle window.
     */
    fun refresh(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSuccessfulCheck < throttleMs) return
        scope.launch {
            val result = safeCall { apiProvider().unreadCount() }
            if (result is ApiResult.Success) {
                lastSuccessfulCheck = System.currentTimeMillis()
                session.setUnread(result.data.unread)
            }
        }
    }

    /** Immediate local setter (optimistic mark-read / delete paths). */
    fun setLocal(n: Int) = session.setUnread(n)
}
