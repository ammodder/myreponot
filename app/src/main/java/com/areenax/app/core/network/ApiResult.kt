package com.areenax.app.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException
import java.io.IOException

/**
 * Result wrapper mirroring the web `api()` behavior:
 * - Success           → 2xx with parsed body
 * - Error(message)    → non-OK HTTP response; message extracted from the body
 *                       `error` field (fallback "Request failed (<status>)"),
 *                       exactly like the web toasts display it.
 * - NetworkError      → no response (offline / timeout / DNS).
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>
    data class NetworkError(val message: String = DEFAULT_NETWORK_MESSAGE) : ApiResult<Nothing>

    companion object {
        const val DEFAULT_NETWORK_MESSAGE = "Network error. Please check your connection."
    }
}

/** Client-side double-submit guard rejection (mirrors web ApiError 409). */
class RequestInFlightException(message: String = "Request already in progress") : IOException(message)

val ApiResult<*>.isSuccess: Boolean get() = this is ApiResult.Success
val ApiResult<*>.isError: Boolean get() = this !is ApiResult.Success

/** Extract the error message exactly like the web (`data?.error || fallback`). */
fun extractErrorMessage(status: Int, bodyText: String?): String {
    if (!bodyText.isNullOrBlank()) {
        try {
            val json = Json { ignoreUnknownKeys = true }
            val obj: JsonObject = json.parseToJsonElement(bodyText).jsonObject
            val error = obj["error"]
            if (error is JsonPrimitive && error.isString && error.content.isNotBlank()) {
                return error.content
            }
        } catch (_: Exception) {
            // non-JSON body → fall through to the generic message
        }
    }
    return "Request failed ($status)"
}

/**
 * Central safe-call helper — every screen/API call goes through this so error
 * surfaces and 401 handling behave identically to the web client.
 *
 * A5-01: raw OS exception text ("timeout", "Unable to resolve host …") used to
 * be shown verbatim in toasts. Transport failures are now mapped to stable,
 * user-appropriate copy by exception type (single point — all 40+ NetworkError
 * branches inherit it). HTTP-status errors still surface the server's own
 * `error` field, exactly like the web.
 */
suspend fun <T> safeCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: RequestInFlightException) {
    ApiResult.Error(e.message ?: "Request already in progress", code = 409)
} catch (e: HttpException) {
    val body = try {
        e.response()?.errorBody()?.string()
    } catch (_: Exception) {
        null
    }
    ApiResult.Error(extractErrorMessage(e.code(), body), code = e.code())
} catch (e: IOException) {
    ApiResult.NetworkError(networkMessageFor(e))
} catch (e: Exception) {
    // Malformed 2xx JSON and unknown failures — never leak raw serializer text.
    ApiResult.Error("Something went wrong. Please try again.")
}

/** Maps transport-level failures to user copy (A5-01). */
private fun networkMessageFor(e: IOException): String = when (e) {
    is java.net.SocketTimeoutException -> "Connection timed out. Please try again."
    is java.net.UnknownHostException -> "Couldn't reach the server. Check your connection."
    is java.net.ConnectException -> "Couldn't reach the server. Please try again."
    is javax.net.ssl.SSLException -> "Secure connection failed. Please try again."
    else -> ApiResult.DEFAULT_NETWORK_MESSAGE
}
