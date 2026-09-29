# A5 evidence — network core (timeouts / retries / error mapping)

Agent: A5 (TASK-006). Read-only. Sources: app/src/main/java/com/areenax/nativeapp/core/network/*.kt,
gradle/libs.versions.toml. All line numbers verified against the audit worktree at /home/z/audit-wt/AreenaxNativeAndroid.

## 1. OkHttpClient construction — NO explicit timeouts, NO retry configuration

ApiClient.kt:39-54:
```
39    fun create(baseUrl: String, sessionManager: SessionManager): Api {
40        val normalizedBase = normalizeBaseUrl(baseUrl)
41        val client = OkHttpClient.Builder()
42            .addInterceptor(AuthInterceptor(sessionManager))
43            .addInterceptor(IdempotencyInterceptor)
44            .addInterceptor(InFlightGuardInterceptor)
45            .addInterceptor(UnauthorizedInterceptor)
46            .build()
47
48        return Retrofit.Builder()
49            .baseUrl(normalizedBase)
50            .client(client)
51            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
52            .build()
53            .create(Api::class.java)
54    }
```
- `connectTimeout` / `readTimeout` / `writeTimeout` / `callTimeout`: NEVER called → okhttp 4.12.0
  library defaults: connect 10s, read 10s, write 10s, **callTimeout 0 (= no overall cap)**.
- `retryOnConnectionFailure`: NEVER called → OkHttp default `true` (silent transport-level
  retry on connection failures where the request was not yet transmitted).
- No application-level retry/backoff interceptor exists. Interceptor list is exactly
  Auth/Idempotency/InFlightGuard/Unauthorized (ApiClient.kt:42-45).

Library versions (gradle/libs.versions.toml:10-11,33-35):
```
10 retrofit = "2.11.0"
11 okhttp = "4.12.0"
```

## 2. Interceptors (full bodies)

AuthInterceptor — x-token on every request when a token exists (ApiClient.kt:64-75):
```
67        val token = sessionManager.token.value
68        val request = if (token.isNullOrBlank()) {
69            chain.request()
70        } else {
71            chain.request().newBuilder().header("x-token", token).build()
72        }
```

IdempotencyInterceptor — fresh UUID per non-GET (ApiClient.kt:77-88):
```
81        val withKey = if (request.method == "GET") {
82            request
83        } else {
84            request.newBuilder().header("Idempotency-Key", UUID.randomUUID().toString()).build()
85        }
```

InFlightGuardInterceptor — one mutation per `METHOD path` (ApiClient.kt:95-112):
```
96        private val inFlight = ConcurrentHashMap.newKeySet<String>()
...
102        val flightKey = "${request.method} ${request.url.encodedPath}"
103        if (!inFlight.add(flightKey)) {
104            throw RequestInFlightException()
105        }
106        try {
107            return chain.proceed(request)
108        } finally {
109            inFlight.remove(flightKey)
110        }
```

UnauthorizedInterceptor — 401 → handler (ApiClient.kt:114-123):
```
117        val response = chain.proceed(chain.request())
118        if (response.code == 401) {
119            ApiClient.onUnauthorized?.invoke()
120        }
121        return response
```

Handler wiring (AreenaxApplication.kt:70-75):
```
72        ApiClient.onUnauthorized = {
73            session.onSessionInvalid()
74            navigator.logout()
75        }
```
SessionManager.onSessionInvalid → logout() (SessionManager.kt:215-217) wipes token/user/unread +
DataStore keys (:197-208). AppNavigator.logout() → LOGIN + cleared stack (AppNavigator.kt:94-101).

## 3. Base URL / TLS posture

normalizeBaseUrl (ApiClient.kt:56-61):
```
58        val trimmed = raw.trim().trimEnd('/')
59        require(trimmed.startsWith("http")) { "app_base_url must be an absolute http(s) URL" }
60        return "$trimmed/api/"
```
- Accepts any string starting "http" — plain `http://` would be accepted by this check.
- Current value is an https placeholder (app/src/main/res/values/strings.xml:11):
  `<string name="app_base_url" translatable="false">https://YOUR-AREENAX-DOMAIN.example.com</string>`
- AndroidManifest.xml has NO `usesCleartextTraffic` and NO `networkSecurityConfig`
  (grep over AndroidManifest.xml: 0 hits) → targetSdk 35 platform default: cleartext denied.
- No CertificatePinner / sslSocketFactory anywhere in ApiClient.kt → no cert pinning.

## 4. Error mapping (safeCall) — full body (ApiResult.kt:55-70)

```
55 suspend fun <T> safeCall(block: suspend () -> T): ApiResult<T> = try {
56     ApiResult.Success(block())
57 } catch (e: RequestInFlightException) {
58     ApiResult.Error(e.message ?: "Request already in progress", code = 409)
59 } catch (e: HttpException) {
60     val body = try {
61         e.response()?.errorBody()?.string()
62     } catch (_: Exception) {
63         null
64     }
65     ApiResult.Error(extractErrorMessage(e.code(), body), code = e.code())
66 } catch (e: IOException) {
67     ApiResult.NetworkError(if (e.message.isNullOrBlank()) ApiResult.DEFAULT_NETWORK_MESSAGE else e.message!!)
68 } catch (e: Exception) {
69     ApiResult.Error(e.message ?: "Something went wrong")
70 }
```

extractErrorMessage (ApiResult.kt:34-49) — server `error` field, fallback "Request failed (status)":
```
41        try {
42            val json = Json { ignoreUnknownKeys = true }
43            val obj: JsonObject = json.parseToJsonElement(bodyText).jsonObject
44            val error = obj["error"]
45            if (error is JsonPrimitive && error.isString && error.content.isNotBlank()) {
46                return error.content
47            }
48        } catch (_: Exception) { … }
49    return "Request failed ($status)"
```
ApiResult types (ApiResult.kt:18-26): Success / Error(message, code) / NetworkError(message,
default "Network error. Please check your connection." :24). `code` (HTTP status) is carried on
Error but NEVER read by any UI branch (grep: `res.code` 0 hits in ui/**).
extractErrorMessage reads only `error`; the server's `code:"GUEST_BLOCKED"|"FORBIDDEN"` field
(auth-server.ts:104-107,116-121) is dropped.

Web parity reference (src/lib/api.ts:86-98): `data?.error || "Request failed (" + status + ")"`;
401 → localStorage.removeItem + store.logout (api.ts:89-95) — mirrored 1:1 by :65 + ApiClient.kt:118.

## 5. Catch-order note

RequestInFlightException extends IOException (ApiResult.kt:29); safeCall catches it BEFORE the
IOException branch (ApiResult.kt:57 before :66) → the 409-style local guard message wins. Correct.
