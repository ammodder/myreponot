package com.areenax.app.core.network

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Builds the Retrofit/OkHttp stack and replicates the web client's cross-cutting
 * request rules (src/lib/api.ts, SPEC/02 "Global request rules"):
 *
 * 1. `Content-Type: application/json` on every call (converter handles it).
 * 2. `x-token: <session token>` on every request when a token exists.
 * 3. `Idempotency-Key: <uuid v4>` on every non-GET (server dedupes replays).
 * 4. Client-side double-submit guard: ONE in-flight mutation per `METHOD path`;
 *    a second concurrent call is rejected locally with
 *    "Request already in progress" (409 semantics), exactly like the web.
 * 5. HTTP 401 → wipe the local session + route to Login (via [onUnauthorized]).
 */
object ApiClient {

    /** JSON config mandated by the audit: tolerant reads, stable writes. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    /** Set by the ServiceLocator after the navigator exists (401 → login jump). */
    @Volatile
    var onUnauthorized: (() -> Unit)? = null

    fun create(baseUrl: String, tokens: TokenProvider): Api {
        val normalizedBase = normalizeBaseUrl(baseUrl)
        val client = OkHttpClient.Builder()
            // A5-02/A3-07/A7-07: explicit timeouts (were implicit library defaults:
            // 10 s connect/read/write, NO overall call cap). callTimeout is set to
            // 120 s so an 8 MB base64 upload (~10.7 MB body; Q3 proof cap) still
            // completes on a slow uplink (~600 kbps ≈ 2.4 min) while every call
            // stays bounded — the splash gate can no longer stall unbounded.
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true) // transport-level, pre-transmission only; server dedupes via Idempotency-Key
            .addInterceptor(AuthInterceptor(tokens))
            .addInterceptor(IdempotencyInterceptor)
            .addInterceptor(InFlightGuardInterceptor)
            .addInterceptor(UnauthorizedInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizedBase)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(Api::class.java)
    }

    /** `<base>/api/` — the web calls `fetch("/api" + path)`. 
     *  A6-07: HTTPS is required for real hosts (the session token rides the
     *  x-token header). Plain http stays allowed ONLY for loopback development
     *  hosts (localhost / 10.0.2.2 emulator); targetSdk 36 blocks cleartext for
     *  every other host regardless, so this just makes the contract explicit. */
    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        require(trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            "app_base_url must be an absolute https URL"
        }
        if (trimmed.startsWith("http://")) {
            val host = trimmed.removePrefix("http://").substringBefore('/')
            require(host.startsWith("localhost") || host.startsWith("127.0.0.1") || host.startsWith("10.0.2.2")) {
                "app_base_url must use https (cleartext allowed only for loopback dev hosts)"
            }
        }
        return "$trimmed/api/"
    }
}

/** A12-03: minimal seam — anything that can hand out the current session
 *  token. SessionManager implements it; tests can fake it without an
 *  Android Context. */
interface TokenProvider {
    val token: StateFlow<String?>
}

/** Adds `x-token` from the TokenProvider on every request (when present).
 *  A12-03: depends on the [TokenProvider] seam (SessionManager implements it)
 *  so the interceptor is constructible in a JVM test with a fake — the
 *  concrete SessionManager needs an Android Context. */
private class AuthInterceptor(tokens: TokenProvider) : Interceptor {
    private val tokenFlow = tokens.token
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenFlow.value
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder().header("x-token", token).build()
        }
        return chain.proceed(request)
    }
}

/** Adds a fresh UUID `Idempotency-Key` to every non-GET call. */
private object IdempotencyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val withKey = if (request.method == "GET") {
            request
        } else {
            request.newBuilder().header("Idempotency-Key", UUID.randomUUID().toString()).build()
        }
        return chain.proceed(withKey)
    }
}

/**
 * One mutation per `METHOD path` at a time — defeats double-tap double-submit.
 * The second concurrent call throws [RequestInFlightException] before it can
 * reach the network, which safeCall() maps to Error("Request already in progress", 409).
 */
private object InFlightGuardInterceptor : Interceptor {
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method == "GET") return chain.proceed(request)

        val flightKey = "${request.method} ${request.url.encodedPath}"
        if (!inFlight.add(flightKey)) {
            throw RequestInFlightException()
        }
        try {
            return chain.proceed(request)
        } finally {
            inFlight.remove(flightKey)
        }
    }
}

/** HTTP 401 → force re-login (mirrors web api.ts). Handler set post-init.
 *  A5-08: the login endpoint's own wrong-credentials 401 must NOT trigger the
 *  session-wipe/logout path — only real session invalidation does. */
private object UnauthorizedInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code == 401 && !request.url.encodedPath.endsWith("auth/login")) {
            ApiClient.onUnauthorized?.invoke()
        }
        return response
    }
}
