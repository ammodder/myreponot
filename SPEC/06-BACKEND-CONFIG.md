> ⚠️ **STATUS 2026-09-22 (R1 final-audit pass):** the push/security-vault stack this doc may reference — PushManager, AreenaxMessagingService, SecretVault, KeystoreCipher, CryptoStore, IntegrityGuard/Config/LockScreen, TransportPolicy, PinningConfig, AppConfigGate/Store, SupabaseConfigFetcher, ServerSetupScreen, `fcm_*` resources, `app/proguard-dicts/*`, `vault-gen.gradle.kts`, WhatsAppFab — was **REVERTED from the app tree** (owner ordered no Firebase; see audit/03-fixes/FIX_PLAN.md R1). The verifier tools `verify_build_7b.py`, `verify_vault_7a.mjs`, `verify_integrity_7c.mjs`, `verify_config_6a.mjs`, `gen_proguard_dicts.mjs` were removed with it — use `tools/check.sh`, `tools/verify_sweep_7e.mjs`, `tools/verify_visual.mjs`. Treat affected sections as historical.

# 06 — BACKEND CONFIG (runtime server discovery: app → Supabase → API)

> Status: implemented (Task 6-a). Owner-facing guide: `SETUP-BACKEND.md`;
> owner SQL: `sql/app-config.sql`. This SPEC documents the pipeline for
> developers — the interaction model is identical to the Admin Panel:
> the app never talks to Supabase for business data, only for the ONE config
> row; every panel API call still goes to the AREENAX API
> (app → API → shared Supabase database).

---

## 1. Why this exists

The original skeleton shipped `app_base_url` in `strings.xml` — a hand-typed
website URL baked into the APK. That was rejected: changing the deployment
domain required a rebuild, and the app carried a value that belongs to the
deployment, not the binary. The config pipeline replaces it with runtime
discovery using the project's own shared Supabase database.

## 2. Resolution order (AppConfigGate, core/config/AppConfigGate.kt)

AppShell runs the gate BEFORE anything renders (mirrors the hydration gate —
splash while Resolving). Order:

| # | Step | Result |
|---|---|---|
| 0 | Seed from cache (DataStore `areena-config` via `AppConfigStore.load()`) | warm starts resolve instantly |
| 1 | `default_api_url` (strings.xml) non-empty and URL-shaped | adopt directly, Supabase skipped |
| 2 | `supabase_url` + `supabase_anon_key` (strings.xml) non-empty | `SupabaseConfigFetcher.fetch()` → adopt; on failure keep cache |
| 3 | Nothing shipped | cached config → Ready; else `GateState.Setup` |

Every resolution path then runs the **Test step**: a lightweight
`GET /bootstrap` probe (the existing `SettingsCache.get { api }` — it also
warms the app-wide settings cache). Probe success → `GateState.Ready`; probe
failure → `GateState.Setup(error, canContinueAnyway = true)`. The setup screen
offers **"Continue anyway"** only when a usable config exists.

## 3. The Supabase fetch (SupabaseConfigFetcher.kt)

```
GET {supabase_url}/rest/v1/"Setting"?select=value&key=eq.app_config
apikey: <anon key>
Authorization: Bearer <anon key>
```

- Table/column names come from `prisma/schema.supabase.prisma` model
  `Setting` (`key String @id`, `value String`) — **no `@@map`**, so the
  Postgres table is `public."Setting"` (PascalCase).
- PostgREST needs PascalCase identifiers double-quoted in the path; the
  fetcher requests `/rest/v1/%22Setting%22` and falls back to the unquoted
  `Setting` form on 404 (PostgREST ≥ 9 resolves it case-insensitively).
- Plain OkHttp (no new dependency), all timeouts 8s, runs on
  `Dispatchers.IO`, never throws (failures → `Outcome.Failure(kind, detail)`).
- Response `[{"value": …}]`: empty array → `ROW_MISSING` (SQL not run).

### Value-cell formats

```json
{"apiBaseUrl": "https://areena.example.com", "cloudinaryCloudName": "my-cloud"}
```
or a plain string (`https://areena.example.com`) → treated as `apiBaseUrl`.
`apiBaseUrl` is normalized to the ORIGIN form (trailing `/` and `/api`
stripped) — the app always calls `<origin>/api/…` and builds QR/share deep
links from the same origin (the API must therefore be deployed at the domain
root, exactly like the web panel).

## 4. Dynamic base URL without breaking Retrofit (ApiClient.kt)

- Retrofit is built ONCE against the dummy base `https://localhost/api/`.
- `DynamicBaseInterceptor` (first in the chain) rewrites each request's
  **scheme/host/port** onto `ApiConfigHolder.apiBaseUrl`; the path
  (`/api/…`) is untouched.
- If a call ever fires while unresolved, the interceptor throws
  `UnresolvedApiConfigException` (an `IOException`) → `safeCall` maps it to
  `ApiResult.NetworkError` — the AppShell gate should make this unreachable.
- **Unchanged on purpose (byte-identical behavior):** endpoint definitions in
  `Api.kt`, `x-token` (AuthInterceptor), `Idempotency-Key` on non-GETs
  (IdempotencyInterceptor), the one-mutation-per-path in-flight guard, and
  HTTP 401 → `ApiClient.onUnauthorized` → session wipe + navigator logout.

## 5. Persistence (AppConfigStore.kt, DataStore `areena-config`)

| Key | Meaning |
|---|---|
| `api_base_url` | resolved origin (Ready → cached → warm starts skip discovery) |
| `cloudinary_cloud_name` | informational (images are absolute Cloudinary URLs) |
| `fetched_at` | epoch millis of resolution |
| `supabase_url` / `supabase_anon_key` | last connection attempt (setup-screen prefill) |

`SessionManager` (`areena-app` DataStore) is deliberately untouched.

## 6. The setup screen (core/ui/ServerSetupScreen.kt)

Full-screen AREENAX-styled gate: logo, "Connect to AREENAX", Supabase URL +
anon key fields (anon key masked with an eye toggle), "Use direct API URL
instead" advanced toggle, rounded-full Connect CTA with busy spinner, error
banner with a kind-specific helpful line (bad URL / network / row missing /
HTTP error / bad payload / probe failure), and "Continue anyway" when
offered. Values are validated for URL shape before the network call; results
are cached in DataStore immediately on success.

## 7. Supabase side (sql/app-config.sql, run once)

1. `INSERT … ON CONFLICT ("key") DO UPDATE` writes the `app_config` row.
2. `GRANT SELECT ON public."Setting" TO anon;` + a SELECT policy scoped with
   `USING ("key" = 'app_config')`. Both are REQUIRED because
   `scripts/enable-rls.sql` enables RLS on `Setting` AND revokes all grants
   from `anon`/`authenticated`/`public`. The grant+policy open exactly one
   read path (anon, SELECT, one row); writes stay impossible.

## 8. App wiring

- `AppShell` builds `AppConfigGate(env.context) { SettingsCache.get { env.api } != null }`,
  collects its state, renders splash (Resolving) / `ServerSetupScreen` (Setup)
  / the normal UI (Ready), and holds session-restore, auth-gate and
  offline-routing effects until Ready.
- `AreenaxApplication.baseAppUrl` is now a getter over
  `ApiConfigHolder.deepLinkBaseUrl` (NavEnv captures it once — the API
  pipeline re-reads the holder per request, so only QR-link bases need a
  restart after a domain change).
- `MainActivity` is unchanged (AppScaffold signature unchanged).

## 9. Invariants / review checklist

- No new Gradle dependencies (plain OkHttp for the Supabase fetch).
- No secrets in the app: anon key is public by design; no service_role,
  Cloudinary API secret, or SETUP_SECRET ever ships.
- `ui/screens/**` untouched — the gate lives entirely in core.
- Deleting the DataStore (app data clear) just re-opens the setup screen.
- Rotating a deployment domain = update the `app_config` row (no rebuild,
  unless the app was shipped via Option 3).

## 10. Credential obfuscation + encrypted storage (Task 7-a)

Infrastructure-only hardening layer; the resolution order in §2 and every
public API are unchanged.

**Build-time obfuscation (SecretVault).** The three owner values
(`supabase.url`, `supabase.anon_key`, `default.api.url`) no longer exist as
plaintext anywhere in the artifact. They live in git-ignored
`secrets.properties` (template `secrets.properties.example`) and are compiled
by `:generateVaultKeys` (`app/vault-gen.gradle.kts`, wired via the AGP 8.7.3
Variant API `sources.java.addGeneratedSourceDirectory` + a KotlinCompile
`dependsOn`) into `build/generated/vaultKeys/…/GeneratedVaultKeys.kt`: each
value XOR'd with its own fresh random pad, both sides split into 2–4
`IntArray` chunks (+ random DECOY pairs no accessor reads). Pads are
regenerated on EVERY build (task never up-to-date), so every APK is shaped
differently. `core/security/SecretVault.kt` re-assembles the bytes in RAM on
first use, self-checks at `AreenaxApplication.onCreate` (shape sanity +
re-assembly stability + dispose), and poisons itself to empty values on any
anomaly — the Connect-screen path stays fully functional either way. XOR is
obfuscation (defeats jadx/apktool/`strings` dumps and leak scanners), not
cryptography — documented honestly in the KDoc.

**At-rest encryption (KeystoreCipher/CryptoStore).** SessionManager
(token, user JSON, pending-QR/link, bank selection) and AppConfigStore
(cached apiBaseUrl/cloud name, last-typed credentials) persist values sealed
with AES-256-GCM by an AndroidKeyStore key `areena_master_v1`
(StrongBox → TEE → software fallback; fresh random IV per record, IV
captured AFTER `doFinal()` for the API 24/25 OEM quirk; StrongBox only
attempted on API 28+). Sealed wire format: `Base64Url(version‖iv‖ciphertext)`.
Every failure path returns null / skips the write — never a crash, never a
plaintext log. One-time silent migration re-seals legacy plaintext keys and
deletes them; decrypt failure == logged-out/value-absent (clean state).
Theme ("light"/"dark") and `fetched_at` stay plaintext (not sensitive).
UnreadManager/SettingsCache persist nothing (memory-only) — untouched.
