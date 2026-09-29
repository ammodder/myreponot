# Phase 0 — Recon Inventory (read-only facts)

Declared: 2026-09-19 · by A0+A1 · evidence basis: 5 read-only code-inventory passes over all 110 Kotlin files + build files + manifest.

## 1. Modules & entry points

| Item | Value |
|---|---|
| Gradle modules | single `:app` (`settings.gradle.kts`), rootProject `AreenaxNativeAndroid` |
| Application class | `.AreenaxApplication` (ServiceLocator: session, connectivity, navigator, toast, unread, api, env) |
| Activities | exactly 1: `.MainActivity` — exported, `singleTask`, portrait, splash theme, `adjustResize`, MAIN/LAUNCHER intent-filter only (no VIEW/deep-link filters) |
| Services | 1: `.core.push.AreenaxMessagingService` (exported=false, `com.google.firebase.MESSAGING_EVENT`) |
| Receivers / Providers / Workers | none in manifest; no WorkManager dependency or usage |
| Deep links | none at OS level; in-app grammar only: `parseDeepLink` → `tournament:<id>`, `host:<id>`, `wallet`, `myTournament`, `notifications`, `home`, `myStats`, `results` (ScreenKeys.kt) |
| Package | `com.areenax.nativeapp` (namespace == applicationId) |

## 2. Screens (46 registered keys = 48 screen files)

Groups: auth 5 · main 5 · wallet 10 · social 8 · profile 12 · host 5 · info 4 · plus shared `core/ui` components (AppShell, AppBar, BottomNav, ToastHost, QrSheet, TeamJoinSheet†, BankSelectSheet, RoomInfoSheet, WhatsAppFab†, SuccessAnim, TournamentCard, ServerSetupScreen, QRCodeImage, AccountRequiredDialog, PlaceholderScreen†).
† = zero call sites in app code (orphaned/deprecated — frozen as-is, flagged for Phase 2).

Bottom-nav roots (`NAV_SCREENS`): home, tournaments, myTournament, friends, wallet, profile.
Public pre-auth: login, signup, signupStep1-3, terms, privacy.
Stack semantics: navigate/replace/goBack, cap 25, BackHandler everywhere except {home, login}; transitions 260 ms easeOut slide+fade; unknown key → login fallback.

## 3. Permissions (least privilege, fact)

`INTERNET`, `CAMERA` (optional feature, required=false), `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS` (runtime ask: API 33+, once, push-gated).
No location, storage, contacts, phone, QUERY_ALL_PACKAGES.

## 4. Dependencies (version catalog `gradle/libs.versions.toml`)

Compose BOM 2024.12.01 (ui/ui-util/foundation/animation/material3) · core-ktx · activity-compose · lifecycle-runtime-compose · core-splashscreen · datastore-preferences · coil-compose · retrofit (+ kotlinx-serialization converter) · okhttp · kotlinx-serialization-json · zxing core + embedded · firebase-messaging (dormant until 4 `fcm_*` strings filled; no google-services plugin).
No Hilt (ServiceLocator pattern) · no Room (DataStore only) · no WorkManager · no analytics/crash SDK.

## 5. Build variants & signing

- debug: unminified, standard debug keystore.
- release: R8 minify + resource shrink, custom `proguard-rules.pro` first; signs ONLY if git-ignored `keystore.properties` exists (absent here → unsigned). Credential vault codegen (`app/vault-gen.gradle.kts` → `:generateVaultKeys`) XOR-obfuscates owner credentials from git-ignored `secrets.properties`; absent file ⇒ empty arrays ⇒ Connect-screen path.
- `buildConfig=false`; no debug flags in release config; packaging excludes META-INF fingerprints.

## 6. Remote configuration (runtime config pipeline)

Resolution order (AppConfigGate): 1) vault `default_api_url` → adopt; 2) vault/entered Supabase creds → `GET {supabase}/rest/v1/Setting?key=eq.app_config` (JSON `{apiBaseUrl, cloudinaryCloudName}`) → adopt; 3) encrypted warm cache (`areena-config` DataStore); 4) else `ServerSetupScreen` (manual entry, incl. optional admin `x-api-key`). Every adopt validated by `GET /bootstrap` probe. https-only enforced (TransportPolicy + `network_security_config.xml`, cleartext blocked, no user CAs).
`GET /bootstrap` settings (`SettingsCache`, process-lifetime, no TTL): minDeposit(100), minWithdraw(500), depositAccounts, whatsapp, social handles, aboutMission, appDownloadUrl, version, welcome-bonus/commission keys (admin-editable).

## 7. Backend contract surface (client view — 75 typed endpoints in `core/network/Api.kt`)

auth(7) · wallet(4) · tournaments(9) · team(6) · friends/chat(6) · notifications(4) · tasks/referrals/stats/leaderboard/achievements(5) · bank(2) · qr/players lookup(2) · games/config(1) · push(3, one unused) · admin(17). Full list in the subsystem inventory (chat log of record) — RTM (Phase 1) will map each to requirements. Authorization is server-side; A5/A6 will audit backend handlers in the monorepo.

## 8. Feature-affecting constants (frozen facts)

- Polling: chat 3 s · myTournament 30 s · unread badge 30 s throttle · details/host countdown 1 s tick · bootstrap cache 60 s TTL.
- Upload caps: deposit receipt 8 MB · result proof 8 MB (app) / ~4 MB (alt endpoint) · host image 5 MB · avatar 8 MB — over-cap auto-downscale, never reject.
- Prize math: pool = 50 % of collection; ranks 50/30/20; loser prize from remaining half (two implementations: creation + details — consistent).
- Money: "Rs" everywhere; `formatBalance` 2 dp (wallet card) vs `formatMoney` 0–2 dp elsewhere; Results uses "Rs. " prefix (known inconsistency, frozen).
- Binding copy: bank list includes "Alphla Bank" (documented binding typo); chat divider "Today, 5:42 PM" static; About version hardcoded "1.0.0".

## 9. Immediate Phase-2 finding candidates (facts logged now, no fixes — Checkpoint 2 gate)

1. targetSdk 35 — Play requires targetSdk 36 for updates from 31 Aug 2026 (Section 8 of mission template; must re-verify against Play Console at fix time).
2. `IntegrityConfig.EXPECTED_SIGNATURE_SHA256` empty → signature integrity check skipped (IntegrityLockScreen unreachable as shipped).
3. `adminDashboard` route + `AdminTournamentsScreen` unreachable (no navigate call); `TeamJoinSheet`, `WhatsAppFab`, `PlaceholderScreen` orphaned; `push/poll` endpoint unused.
4. MyTeamScreen owner "Join Requests" fetched but never rendered (dead state + handler).
5. FriendsScreen "Suggested Players" hardcoded demo data with local-only "Sent" state (no API) — M5 "no demo data" candidate.
6. ResultsScreen fires `GET /tournaments//results` with empty id when opened via bare `results` deep link.
7. No explicit OkHttp timeouts on main client (relies on 10 s defaults) vs 8 s on config fetcher.
8. Error states without retry controls on several read screens (tournaments/myTournament/results/friends/chat/host lists).
9. Admin settings save sends raw string map without client-side numeric validation.
10. EditProfile email-clearing is a server-side no-op (DTO omits nulls).
