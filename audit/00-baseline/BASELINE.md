# BASELINE — AreenaxNativeAndroid (verified on disk, 2026-09-19)

Every fact below was read directly from the files in this repo on branch `audit/production-readiness`.
Status markers: [V] = VERIFIED (evidence in audit/evidence/), [N] = NOT VERIFIED (reason given).
FAILED and NOT APPLICABLE are used in prose where they apply (see §7b and §11), per the
5-label honesty vocabulary in audit/MASTER_RULES.md.

## 1. Project structure
- Repo root `/home/z/my-project` = Next.js 16 web panel (src/**) + admin-panel + prisma/db.
  The web panel is OUT OF SCOPE for the Android audit and must not be touched.
- Android app: `/home/z/my-project/AreenaxNativeAndroid/`
  - `app/` — the only Gradle module (application). No other modules.
  - `app/src/main/java/com/areenax/nativeapp/` — 93 Kotlin files, 35,256 lines total [V]
    - root: `AreenaxApplication.kt`, `MainActivity.kt`
    - `core/nav/` (4 files): ScreenKeys (46 route constants), AppNavigator/AppNavHost/NavEnv
    - `core/network/` (3): Api (Retrofit interface, 71 suspend members), ApiClient, ApiResult
    - `core/session/` (3): SessionManager, UnreadManager, SettingsCache
    - `core/theme/` (4): Theme (Material3 + ExtendedColors), Tokens, Type, Shapes
    - `core/ui/` (24): shared components (AppBar, AppShell, BottomNav, ToastHost, QrSheet,
      EmptyState, dialogs, pickers, PlaceholderScreen+AreenaxSpinner, etc.)
    - `core/util/` (4): Formatters, TournamentState, ConnectivityObserver, QrPayload
    - `data/` (1): Models.kt (116 `data class` DTOs + 6 objects = 122 type declarations, all
      `@Serializable`; Roles included) — corrected from the stale "118" of the lost-era worklog
    - `ui/screens/` (48 files in 7 packages): auth 5, main 5, wallet 10, social 8,
      profile 11 (incl. admin sections), host 5, info 4
  - `app/src/main/res/`: 154 vector drawables + 1 PNG (`areenax_logo.png`, the app icon),
    1 variable font (Hanken Grotesk), values/{colors,strings,themes}.xml [V]
  - `SPEC/` — 7 spec documents (00-OVERVIEW … 05-NATIVE-GUIDE, ICONS)
  - `tools/` — icon build scripts (svg2vector, build_icons, verify_visual)
  - `README.md`, `START-HERE.md` (repo root)

## 2. Language & UI toolkit [V]
- 100% Kotlin (no Java sources), UI 100% Jetpack Compose (Material 3), NO WebView, NO XML layouts
  (XML used only for manifest/resources).

## 3. Architecture [V]
- Single-Activity (`MainActivity`, launchMode=singleTask, portrait, splash theme) Compose host.
- Navigation: self-built in-memory state machine mirroring the web `store.ts` —
  `AppNavigator.currentScreen` holds one of 46 `ScreenKeys` strings; `AppNavHost` maps key →
  composable with AnimatedContent transitions. No Navigation-Compose library.
- No DI framework (manual ServiceLocator-style single objects); no Hilt/Koin.
- State: Compose `remember`/`collectAsState` over Kotlin StateFlows (SessionManager etc.).
- Persistence: DataStore (preferences) for session/theme. No Room, no SQLite on device.
- Networking: Retrofit + OkHttp + kotlinx-serialization against the existing Next.js backend;
  `x-token` header sessions; base64 data-URL image uploads; polling instead of push
  (chat 3s, my-tournaments 30s, tournament details countdown 1s).
- Images: Coil. QR: zxing core (generate) + zxing-android-embedded (scan).

## 4. Screens / routes [V — ScreenKeys.kt]
46 route keys, 7 groups:
- A. Auth (5): login, signup, signupStep1, signupStep2, signupStep3
- B. Main (5): home, tournaments, tournamentDetails, myTournament, results
- C. Wallet (10): wallet, deposit, depositConfirm, depositSuccess, withdraw, confirmWithdraw,
  withdrawSuccess, transferMoney, transferSuccess, selectBank
- D. Social (8): myTeam, teamCreation, teamCreationDone, friends, chat, referEarn, tasks, notifications
- E. Profile (+admin) (9): profile, editProfile, myStats, achievements, leaderboard, bindAccount,
  bindAccountSuccess, adminPanel, adminDashboard
- F. Host (5): hostTournament, hostTournamentCreation, hostTournamentSuccess, hostTournamentCard,
  hostTournamentDetails
- G. Info (4): about, terms, privacy, offline
- Bottom-nav roots (when logged in): home, tournaments, myTournament, friends, wallet, profile.
- Public screens (no account): auth 5 + terms, privacy.
- 48 screen files vs 46 keys: profile package contains 2 non-route section files
  (GamesAdminSection, ResultProofsAdminSection).

## 5. Services / receivers / workers / deep links [V — AndroidManifest.xml]
- Services: NONE. Receivers: NONE. Workers: NONE (no WorkManager dependency).
- Manifest intent-filter deep links: NONE (only the MAIN/LAUNCHER filter).
- In-app deep-link grammar EXISTS: `parseDeepLink()` in ScreenKeys.kt
  (`tournament:<id>`, `host:<id>`, wallet, myTournament, notifications, home, myStats, results) —
  a port of the web push-client grammar, usable for internal navigation/notification payloads.
- Permissions (3 only): INTERNET, CAMERA, ACCESS_NETWORK_STATE.
  Camera is optional (`uses-feature required=false` — manual-UID fallback in QrSheet).

## 6. Libraries [V — gradle/libs.versions.toml + app/build.gradle.kts]
AGP 8.7.3 · Kotlin 2.1.0 · Compose BOM 2024.12.01 (material3 1.3.1 — confirmed resolved) ·
core-ktx 1.15.0 · activity-compose 1.9.3 · lifecycle-runtime-compose 2.8.7 · splashscreen 1.0.1 ·
datastore-preferences 1.1.1 · coil-compose 2.7.0 · retrofit 2.11.0 (+ kotlinx-serialization
converter) · okhttp 4.12.0 · kotlinx-serialization-json 1.7.3 · zxing core 3.5.3 + embedded 4.3.0.
No Firebase, no WorkManager, no Hilt, no Room, no Analytics.

## 7. Build variants [V]
- debug (no minify) / release (R8 minify + resource shrinking, proguard-android-optimize).
- No product flavors. versionCode 2, versionName "2.0.0", applicationId com.areenax.nativeapp.
- compileSdk 35, targetSdk 35, minSdk 24 (Android 7.0+), JVM target 17, resourceConfigurations "en".

## 7b. Owner item 3 — build / lint / tests status
- DEBUG BUILD: **FAILED** — first real compile ever; resources stage failed on colors.xml, and the
  Kotlin pass (probe copy) emitted ≥319 errors before the daemon was killed (see §11 and findings).
- RELEASE BUILD: **NOT RUN** (reason: blocked by the same compile blockers; no keystore → unsigned-only).
- LINT: **NOT RUN** (reason: lint analyzes compiled sources — blocked by the same blockers).
- EXISTING TESTS: **NOT APPLICABLE** (reason: zero test sources — `app/src` contains only `main/`).

## 8. Backend [V]
- The existing Next.js API of this repo (same origin as the web panel).
- `app_base_url` in strings.xml is STILL THE PLACEHOLDER `https://YOUR-AREENAX-DOMAIN.example.com`
  — deployment blocker (owner must set the live domain).

## 9. App size / cold start / idle memory / screenshots [N]
NOT VERIFIED — no emulator or physical device in this sandbox (see RUNTIME-ENVIRONMENT.md).
Cold-start, memory and per-screen screenshots (light + dark) require a device. App size can only
be stated after a successful build; the first real build FAILED (see findings).

## 10. Delivery artifact [N]
`public/downloads/AreenaxNativeAndroid.zip` DOES NOT EXIST in the workspace (folder gone).
The last verified package (conversation record): sha256 570c457f…3315fb0, 1,088,125 bytes, Task 11 era.
If the owner kept that download, it is the newest authoritative copy (it contains the lost
Task 6-11 app changes). The workspace cannot recreate it without re-applying those changes.

## 11. KNOWN DEFECT (major, new): the app source has NEVER compiled
First real build attempt (toolchain bootstrapped during this Phase 0): BUILD FAILED —
**≥319 compiler errors** (a floor, not a final count: the Gradle daemon was KILLED mid-run after
reaching only 51 of 93 files). Root causes: 2 parse-level defects (XML `--` in comment; Kotlin
NESTED-comment trap from a `/**` glob inside a KDoc), many missing imports, material3 1.3.1
having NO fixed color roles (Theme.kt + 4 screen call sites), a scope bug on the session-invalid
hook, and an experimental-API opt-in gap.
Details + fix options: `audit/02-findings/PHASE0-BLOCKERS.md` + evidence log.
The earlier worklog claim "should compile end-to-end on paper" (static QA only) did NOT survive
a real compiler. This also means the previously delivered zips were never compiler-validated.
