> ⚠️ **STATUS 2026-09-22 (R1 final-audit pass):** the push/security-vault stack this doc may reference — PushManager, AreenaxMessagingService, SecretVault, KeystoreCipher, CryptoStore, IntegrityGuard/Config/LockScreen, TransportPolicy, PinningConfig, AppConfigGate/Store, SupabaseConfigFetcher, ServerSetupScreen, `fcm_*` resources, `app/proguard-dicts/*`, `vault-gen.gradle.kts`, WhatsAppFab — was **REVERTED from the app tree** (owner ordered no Firebase; see audit/03-fixes/FIX_PLAN.md R1). The verifier tools `verify_build_7b.py`, `verify_vault_7a.mjs`, `verify_integrity_7c.mjs`, `verify_config_6a.mjs`, `gen_proguard_dicts.mjs` were removed with it — use `tools/check.sh`, `tools/verify_sweep_7e.mjs`, `tools/verify_visual.mjs`. Treat affected sections as historical.

# 05 — NATIVE GUIDE (Wave-3 screen-builder onboarding)

> **Read this first.** You are building ONE category of screen stubs
> (`ui/screens/<category>/`) into real Compose screens. Everything shared
> already exists under `core/**` + `data/**` — you NEVER touch those.
> The web panel under `/home/z/my-project/src/**` is the single source of
> truth; `SPEC/00..04` + `SPEC/ICONS.md` hold the extracted contracts.

---

## 1. Project map

```
AreenaxNativeAndroid/
├─ app/src/main/java/com/areenax/nativeapp/
│  ├─ AreenaxApplication.kt      service locator: session/connectivity/navigator/toast/unread/api/env singletons
│  ├─ MainActivity.kt            single Activity: splash → AreenaxTheme → AppScaffold
│  ├─ core/
│  │  ├─ nav/       ScreenKeys.kt (46 keys) · AppNavigator.kt (router) · NavEnv.kt · AppNavHost.kt (REGISTRY + transitions)
│  │  ├─ network/   Api.kt (Retrofit interface) · ApiClient.kt (x-token, Idempotency-Key, 401) · ApiResult.kt (safeCall)
│  │  ├─ session/   SessionManager.kt (DataStore "areena-app") · UnreadManager.kt (30s throttle) · SettingsCache.kt (/bootstrap cache)
│  │  ├─ theme/     Theme.kt (AreenaxTheme + areenaColors()) · Type.kt (Type.*) · Shapes.kt (AreenaxShapes.*)
│  │  ├─ ui/        shared components (§4 below) + AppShell.kt (AppScaffold)
│  │  └─ util/      Formatters.kt · QrPayload.kt · TournamentState.kt · ConnectivityObserver.kt
│  ├─ data/Models.kt               ALL @Serializable DTOs (field names = API JSON exactly)
│  └─ ui/screens/
│     ├─ auth/ (5)   main/ (5)   wallet/ (10)   social/ (8)   profile/ (12)   host/ (5)   info/ (4)
├─ app/src/main/res/
│  ├─ drawable/     154 icon VectorDrawables (ic_*) + areenax_logo.png
│  ├─ font/         hanken_grotesk_variable.ttf
│  └─ values/       strings.xml (app_base_url!), colors.xml, themes.xml
├─ SPEC/00..05, ICONS.md
└─ gradle/libs.versions.toml · app/build.gradle.kts
```

Stack: Kotlin 2.1.0, AGP 8.7.3, compileSdk/targetSdk 35, minSdk 24, JDK 17,
Compose BOM 2024.12.01 (Material 3), Retrofit + OkHttp + kotlinx-serialization,
Coil, ZXing core + zxing-android-embedded, DataStore Preferences. **Zero
WebView** — the whole panel is native Compose.

App flow: `MainActivity` → `AppScaffold` (AppShell port: hydration splash →
session restore → glow canvas → `AppNavHost` → BottomNav/ToastHost) → your
screen composable receives the one and only `NavEnv`.

---

## 2. NavEnv — the FULL API surface

Every screen is `@Composable fun XScreen(env: NavEnv)`. `NavEnv` (core/nav/NavEnv.kt)
bundles the web's `useAppStore()` + `useToast()` + `api`:

```kotlin
class NavEnv(
    val navigator: AppNavigator,     // router state machine (store.ts port)
    val session: SessionManager,     // DataStore token/user/theme/pending stores
    val unread: UnreadManager,       // bell badge (30s-throttled countOnly poll)
    val toast: ToastController,      // single-toast host
    val connectivity: ConnectivityObserver,  // isOnline: StateFlow<Boolean>
    val baseUrl: String,             // app_base_url (QR deep links, share links)
    val appContext: Context,         // clipboard / share intents
    private val apiProvider: () -> Api,
) {
    val api: Api                     // lazily built Retrofit interface
    val params: Map<String, Any?>    // CURRENT screen params  ← env.params["tournamentId"]
    val user: User?                  // session.user snapshot (collect for reactivity)
    val token: String?
    val context: Context             // == appContext

    fun navigate(screen: String, params: Map<String, Any?> = emptyMap())  // push (stack ≤ 25)
    fun replace(screen: String, params: Map<String, Any?> = emptyMap())   // CLEAR stack (success screens, setAuth targets)
    fun goBack()                                                          // pop; empty → home if logged in else login
    fun setAuth(token: String, user: User)                                // login/register/guest success → home + cleared stack
    fun logout()                                                          // local wipe + → login (POST /auth/logout is YOURS first)
    fun toast(message: String, variant: ToastVariant = ToastVariant.Default)
}
```

### Canonical screen skeleton

```kotlin
@Composable
fun TournamentDetailsScreen(env: NavEnv) {
    // 1. Read params ONCE at the top of the body.
    val tournamentId = env.params["tournamentId"] as? String

    var detail by remember { mutableStateOf<Tournament?>(null) }
    var loading by remember { mutableStateOf(true) }

    // 2. Load with the safeCall pattern (web api() parity: error strings,
    //    409 in-flight guard, 401 auto-logout are ALL handled for you).
    LaunchedEffect(tournamentId) {
        loading = true
        when (val res = safeCall { env.api.tournamentDetails(tournamentId ?: "") }) {
            is ApiResult.Success      -> detail = res.data.tournament
            is ApiResult.Error        -> env.toast(res.message, ToastVariant.Destructive)
            is ApiResult.NetworkError -> env.toast(res.message, ToastVariant.Destructive)
        }
        loading = false
    }

    // 3. Session reactivity: collect the StateFlows, never read-and-forget.
    val user by env.session.user.collectAsState()
    val isOnline by env.connectivity.isOnline.collectAsState()

    // 4. Screens paint TRANSPARENT roots — the dark canvas glow lives in AppScaffold.
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)          // ← do NOT paint background
            .verticalScroll(rememberScrollState()),
    ) {
        AppBar(mode = AppBarMode.Page, title = "Details", balance = true)
        // … body …
    }

    // 5. Sheets/dialogs live at the screen level (§4).
    var showRoom by remember { mutableStateOf(false) }
    RoomInfoSheet(open = showRoom, onClose = { showRoom = false },
                  tournamentId = tournamentId, env = env, tournamentName = detail?.name)
}
```

### More env recipes

```kotlin
// Navigation
env.navigate(ScreenKeys.CHAT, mapOf("friendId" to friend.id))
env.replace(ScreenKeys.WALLET)                 // success-screen "done" buttons
env.goBack()
env.setAuth(res.data.token, res.data.user)     // after login/register/guest

// Toasts (single toast, 5s, destructive variant = red)
env.toast("Copied!")
env.toast(res.message ?: "Failed", ToastVariant.Destructive)
env.toast.show("Title", description = "Optional line", variant = ToastVariant.Default)

// API call — ALWAYS through safeCall; never try/catch bare suspend calls
when (val res = safeCall { env.api.wallet() }) { /* … */ }

// Session
env.session.setUser(res.data.user)             // GET /me / PATCH /me refresh
env.session.updateBalance(walletRes.balance)   // WalletScreen balance sync
env.session.setAuth(token, user)               // via env.setAuth instead
env.session.themeIsDark.collectAsState()       // theme toggle → env.session.setTheme(dark)
env.session.setBankSelection(method, accountId) // deposit/withdraw funding memory
env.session.pendingQr / setPendingQr / clearPendingQr
env.session.pendingLink / setPendingLink / clearPendingLink

// Unread badge (AppBar's BellButton already refreshes; call after mutations)
env.unread.refresh(force = true)               // after mark-read / delete
env.unread.setLocal(0)                         // optimistic local writes

// Connectivity
val online by env.connectivity.isOnline.collectAsState()

// Clipboard / share / external links (use env.context / appContext)
val cm = env.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
cm.setPrimaryClip(ClipData.newPlainText("AREENAX", text))
env.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")))
Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText) }
    .let { env.context.startActivity(Intent.createChooser(it, "Share")) }

// Formatters (core/util/Formatters.kt — Locale.US, web-exact)
formatMoney(n)      // "1,250" / "1,250.5"
formatBalance(n)    // "1,250.00"   (rsMoney/rsBalance prefix "Rs ")
formatDateTime(iso) // "05-10-2025 3:07 pm"
timeAgo(iso)        // "Just now" | "5m ago" | "3h ago" | "2d ago" | "05-10-2025"
countdownText(iso)  // "Starting soon" | "2d 4h 30m" | …
formatCompact(n)    // "12.5K" (ResultsScreen)
maskAccountNumber("PK36…") // "PK••••••••6702"
formatDisplayDate / formatShortDate / formatChatTime

// Theme tokens
MaterialTheme.colorScheme.*          // full M3 mapping (SPEC/04 §7)
areenaColors()                       // extended: surfaceLavender, surfaceContainerLavender,
                                     // surfaceContainerHighLavender, balanceChip, txInIconBg/Fg,
                                     // trophyGradient, leaderboardCrown, goldMedal,
                                     // whatsappGreen, offlineGradient, isDark
Type.displayLg / headlineLg / headlineLgMobile / headlineMd / bodyLg / bodyMd /
Type.labelLg / labelMd / labelSm / pageTitle / heroAmount / toastTitle / toastBody / successTitle
AreenaxShapes.Pill / Card(24) / Sheet(32 top) / Dialog(28) / MediumCard(16) / Tile(12) / Small(8)

// Photo picker → base64 data URL (respect the endpoint cap!)
val picker = rememberImagePicker(UploadCaps.RESULT_PROOF) { picked: PickedImage? -> … }
Button(onClick = { picker.launch() }) { Text("Choose from Gallery") }
// UploadCaps: RECEIPT 8MB · RESULT_PROOF 4MB · HOST_IMAGE 5MB · AVATAR 8MB
// picked.dataUrl = "data:image/…;base64,…" → straight into the JSON "image" field

// QR
buildQrLink(QrKind.USER, user.uid, env.baseUrl)   // https://<base>/?qr=u%3A123456
parseQrPayload(scannedText)                        // → QrPayload(kind, id) or null
QRCodeImage(content = link, modifier = Modifier.size(176.dp))   // ZXing 512px
QrSheet(open, onClose, env, target = QrTarget.USER/TEAM, teamId, teamName, initialTab)

// Tournament state machine (ONE source of truth for every card surface)
computeTournamentAction(t, joined)      // → TournamentCardAction(kind, label, interactive)
deriveTournamentStatus(t)               // time-aware UPCOMING/ONGOING/COMPLETED/CANCELLED
tournamentStatusLabel(t)                // "Open"/"Live"/status
proofModeActive(t)                      // result-proof window
hostPrizeMath(entryFee, slots)          // host creation preview economics
FUNDING_BANKS                            // ["Jazzcash","Easypaisa","Sadapay","Alphla Bank","Mezan Bank"] — typo is BINDING
newDepositDisplayRef()                   // "#DEP-123456"
```

### Params contract (SPEC/01-SCREENS.md)

Params travel inside AppNavigator (`{screen, params}` pairs) and are read via
`env.params`. Capture them ONCE at the top of the body
(`val id = env.params["tournamentId"] as? String`). Typed helpers you'll want:
`String` params as `as? String`; numeric params arrive as `Double`/`Int`
(deposit/withdraw `amount`), booleans/nullables per the screen spec. The full
per-screen list: **SPEC/01-SCREENS.md** ("Params in:" per section).

---

## 3. Screen keys & routing (what AppNavHost already does)

All 46 keys in `core/nav/ScreenKeys.kt` are registered 1:1 in
`core/nav/AppNavHost.kt` (fallback = login, like the web REGISTRY). You never
register routes — `env.navigate("…")` with a `ScreenKeys` constant just works.
Push/pop transitions (fade + quarter slide, 260ms easeOut) and the system
BackHandler (goBack everywhere except `home`/`login`) are handled by the host.

PUBLIC_SCREENS (no auth): login, signup, signupStep1-3, terms, privacy.
BottomNav shows ONLY on: home, tournaments, myTournament, friends, wallet, profile.

---

## 4. Shared component inventory (EXACT signatures)

All in `com.areenax.nativeapp.core.ui` unless noted.

```kotlin
// Layout / chrome
@Composable fun AppScaffold(env: NavEnv, modifier: Modifier = Modifier)          // AppShell port (do not call from screens)
@Composable fun AppBar(modifier: Modifier = Modifier, mode: AppBarMode = AppBarMode.Page,
    title: String? = null, balance: Boolean = false,
    right: (@Composable RowScope.() -> Unit)? = null,
    left:  (@Composable RowScope.() -> Unit)? = null, onBack: (() -> Unit)? = null)   // AppBarMode.Page | AppBarMode.Tab
@Composable fun AppBarBackButton(onClick: () -> Unit, iconRes: Int = R.drawable.ic_arrow_back)
@Composable fun AppBarCircleButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit)
@Composable fun BellButton(size: BellSize = BellSize.Md)                          // unread badge, 30s throttle, → notifications
@Composable fun BalanceChip(balance: Double? = null)                              // "Rs …" pill → wallet
enum class BellSize { Md, Sm }
@Composable fun BottomNav(modifier: Modifier = Modifier)                          // AppShell-owned; never call from screens

// Lists / states
@Composable fun TournamentCard(tournament: Tournament, modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null, joinLabel: String = "JOIN NOW", joined: Boolean = false,
    onAction: ((TournamentCardAction) -> Unit)? = null)
@Composable fun EmptyState(message: String, modifier: Modifier = Modifier,
    icon: String = "trophy", circleColor: Color? = null)                          // copy EXACTLY from SPEC/01
@Composable fun StatusChip(status: String, modifier: Modifier = Modifier, enlarged: Boolean = false)
@Composable fun SuccessAnim(title: String, modifier: Modifier = Modifier,
    subtitle: String? = null, children: @Composable () -> Unit = {})
@Composable fun PlaceholderScreen(screenKey: String, modifier: Modifier = Modifier)  // stubs ONLY
@Composable fun AreenaxSpinner(size: Int = 32, modifier: Modifier = Modifier, strokeWidth: Int = 2)

// FABs (screen-owned placement — AppShell adds none)
@Composable fun SupportFab(offset: Boolean = true)   // home/tournaments/wallet/profile → offset=true; tournamentDetails → false
@Composable fun WhatsAppFab(modifier: Modifier = Modifier, offset: Boolean = false, href: String? = null)  // DEPRECATED on web — use SupportFab

// Inputs / pads
@Composable fun AmountPad(onKey: (key: String) -> Unit, modifier: Modifier = Modifier)
fun appendAmountKey(current: String, key: String): String   // max 8 int digits, 2 decimals, "." → "0."
val AMOUNT_PAD_KEYS: List<String>

// Sheets & dialogs (all ModalBottomSheet-based, rounded-t-32dp; exact params)
@OptIn(ExperimentalMaterial3Api::class) @Composable
fun QrSheet(open: Boolean, onClose: () -> Unit, env: NavEnv, target: QrTarget,
    modifier: Modifier = Modifier, teamId: String? = null, teamName: String? = null,
    initialTab: QrTab = QrTab.MINE)                       // enum QrTab { MINE, SCAN } · QrTarget { USER, TEAM }
@Composable fun UserResultCard(data: QrUserLookup, busy: Boolean, env: NavEnv, onAddFriend: () -> Unit, onMessage: () -> Unit)
@Composable fun TeamResultCard(data: QrTeamLookup, busy: Boolean, env: NavEnv, onJoin: () -> Unit)
@Composable fun BankSelectSheet(open: Boolean, onClose: () -> Unit, title: String,
    selectedId: String?,
    onSelect: (id: String, bankName: String, account: BankAccount?) -> Unit,
    onConfirm: () -> Unit, modifier: Modifier = Modifier,
    searchPlaceholder: String = "Search bank", banks: List<String> = emptyList(),
    accounts: List<BankAccount> = emptyList(), accountsLabel: String = "Your Accounts",
    showBanks: Boolean = true, showAccounts: Boolean = true,
    confirmLabel: String = "Confirm", emptyAction: BankEmptyAction? = null)
sealed class TeamJoinChoice {                                     // Team Battle side-join (SPEC/03 §19)
    data class UseTeam(val memberUids: List<String>) : TeamJoinChoice()
    data class Manual(val memberUids: List<String>) : TeamJoinChoice()
}
@Composable fun TeamJoinSheet(open: Boolean, onClose: () -> Unit, env: NavEnv, mode: String,
    playersPerSide: Int, entryFee: Double, balance: Double, joining: Boolean,
    onConfirm: (choice: TeamJoinChoice) -> Unit)
@Composable fun RoomInfoSheet(open: Boolean, onClose: () -> Unit, tournamentId: String?,
    env: NavEnv, modifier: Modifier = Modifier, tournamentName: String? = null)
@Composable fun ResultProofSheet(open: Boolean, onClose: () -> Unit, tournamentId: String,
    env: NavEnv, modifier: Modifier = Modifier, submittedAt: String? = null,
    onUploaded: () -> Unit = {})
@Composable fun DateTimePickerModal(open: Boolean, value: String, onClose: () -> Unit, onChange: (iso: String) -> Unit)
@Composable fun InsufficientBalanceDialog(open: Boolean, entryFee: Double, balance: Double,
    onOpenChange: (Boolean) -> Unit, tournamentName: String? = null, env: NavEnv? = null)
@Composable fun AccountRequiredDialog(open: Boolean, feature: String?,
    onOpenChange: (Boolean) -> Unit, onCreateAccount: () -> Unit)
fun rememberGuestGate(): GuestGateState                  // stateful guest-gate helper
@Composable fun GuestGateDialog(gate: GuestGateState, env: NavEnv)
@Composable fun CenteredModalScaffold(open: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit)

// QR & icons
@Composable fun QRCodeImage(content: String, modifier: Modifier = Modifier, size: Int = 512)
@Composable fun UserAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp)
@Composable fun AreenaxIcon(name: String, contentDescription: String?, modifier: Modifier = Modifier,
    filled: Boolean = false, tint: Color = Color.Unspecified)
fun iconResId(name: String, filled: Boolean = false): Int

// Modifier utilities
Modifier.cardShadow(shape: Shape = CircleShape)     // web .card-shadow
Modifier.primaryGlow(shape: Shape = CircleShape)    // FAB / primary CTA glow
Modifier.navShadow(shape: Shape)                    // BottomNav top lift
Modifier.pressScale(interactionSource, pressedScale = 0.97f)  // web active:scale-95..0.98
```

### Guest gate pattern (AccountRequiredDialog)

```kotlin
val gate = rememberGuestGate()
// In front of the gated action — returns true when the user may proceed:
Button(onClick = {
    if (gate.requireAccount(user.isGuest, "deposit money")) { /* proceed */ }
}) { … }
GuestGateDialog(gate, env)   // mount once per screen
```

---

## 5. Icons (SPEC/ICONS.md is the full table)

The web renders icons by Material-Symbol NAME. Native resolves the same names
to 154 bundled VectorDrawables via `AreenaxIcon`:

```kotlin
AreenaxIcon(name = "schedule", contentDescription = null, modifier = Modifier.size(18.dp))
AreenaxIcon(name = "check_circle", filled = true, tint = MaterialTheme.colorScheme.primary, …)
```

- Names use snake_case exactly as in the web (`account_balance_wallet`,
  `emoji_events`, `qr_code_2`). Aliases handled for you: `trophy`, `warning→error`,
  `tournament→tune`, `qr_code_scanner→qr_code_2`, `quiz→assignment`, `chevron-right`.
- **filled = true** only where the web sets `'FILL' 1` (BottomNav 5 tabs,
  visibility/visibility_off, check/done/done_all/check_circle, key, lock,
  verified, bolt, crown, groups, person_add, account_balance(_wallet),
  credit_card, emoji_events, home, person). Fill variants exist as
  `ic_<name>_fill.xml` — `AreenaxIcon(filled = true)` resolves them for you.
- Direct drawables (`painterResource(R.drawable.ic_…)`): use for FIXED icons —
  135 Material Symbols + `ic_inline_whatsappfab_whatsapp_logo` (literal-white),
  `ic_inline_leaderboard_crown`, `ic_inline_teamcreation_shield_check`,
  `ic_inline_teamcreation_send`, `ic_inline_about_chevron_right` + 14 `ic_lucide_*`.
- Tint rules: icons inherit tint from the composable; pass `tint =` explicitly
  for status colors (`MaterialTheme.colorScheme.error` etc.). The WhatsApp logo
  and leaderboard crown are pre-colored — do not tint.
- AchievementsScreen's circular progress ring is NOT a drawable — draw it with
  Compose Canvas / CircularProgressIndicator (instructions in SPEC/ICONS.md §4).
- The chat send icon needs a **-45° rotation** applied in Compose
  (`Modifier.rotate(-45f)` on `ic_inline_teamcreation_send` / `ic_send`).

---

## 6. FILE-OWNERSHIP RULES (hard rules)

1. You may **rewrite ONLY your own category's stub files**
   (`ui/screens/<your-category>/*.kt`). The two admin section stubs
   (`GamesAdminSection`, `ResultProofsAdminSection`) belong to the `profile`
   builder.
2. **NEVER edit `core/**`, `data/**`, other categories, `AreenaxApplication.kt`,
   `MainActivity.kt`, gradle files, manifest or res/**.** If a shared piece is
   missing, build it **privately inside your own screen file** (private
   composables/data classes in the same file). If it's genuinely cross-screen,
   note it in the worklog — the orchestrator promotes it to core.
3. Never `import` another category's screens; navigation is only via
   `env.navigate(ScreenKeys.X, …)`.
4. Keep `PlaceholderScreen(...)` in any file you have not built yet; delete it
   from the files you build (grep must show zero placeholders outside stubs).
5. Copy strings/labels EXACTLY from SPEC/01 — no rewording.
6. Do not add new Gradle dependencies or permissions.

---

## 7. API cheat sheet (full contract: SPEC/02-API.md)

`Api` (core/network/Api.kt) groups:

| Group | Endpoints |
|---|---|
| Bootstrap & session | bootstrap · auth/login · auth/guest · auth/register · auth/check · auth/logout · me · PATCH me |
| Wallet | wallet · wallet/deposit · wallet/withdraw · wallet/transfer |
| Tournaments | tournaments(?) · tournaments/:id · POST tournaments · tournaments/:id/join · tournaments/:id/results · tournaments/:id/proof (≤8MB one-time) · tournaments/:id/result-proof (≤4MB replaceable) · my/tournaments · my/hosted |
| Team | team · POST team · DELETE team · team/join-requests(+/:id) |
| Friends & chat | friends · friends/requests(+/:id) · friends/:id/messages |
| Notifications | notifications · unreadCount (countOnly) · markAllRead · markRead · deleteNotifications |
| Tasks/referrals/stats | tasks(+claim) · referrals · stats · leaderboard · achievements |
| Bank | bank · POST bank |
| QR & lookup | qr/lookup(uid?, teamId?) · players/lookup(uid) |
| Games config | games/config |
| Push | push/register · push/unregister · push/poll |
| Admin | admin/requests(+/:id) · admin/tournaments(+status/room/disable/results) · admin/proofs(+/:id) · admin/settings · admin/security(+reconcile) · admin/games(+/:id, /:id/config) |

Rules already handled by ApiClient: `x-token` header, `Idempotency-Key` on
non-GET, one-in-flight-mutation guard (second call → Error 409
"Request already in progress"), 401 → wipe + login. You wrap calls in
`safeCall { }` and surface `.message` via toasts exactly as the web does.

---

## 8. Polling patterns (exact intervals, SPEC/00 §4)

```kotlin
// Chat — poll every 3s; skip the state write when the list is identical.
LaunchedEffect(friendId) {
    while (isActive) {
        safeCall { env.api.chatMessages(friendId) }.onSuccess { … }
        delay(3_000)
    }
}

// MyTournament — live status refresh every 30s.
LaunchedEffect(Unit) {
    while (isActive) {
        safeCall { env.api.myTournaments() }.onSuccess { … }
        delay(30_000)
    }
}

// Countdown tick (details/host details) — 1s.
LaunchedEffect(tournamentId) { while (isActive) { now = System.currentTimeMillis(); delay(1_000) } }

// Unread — do NOT hand-roll; UnreadManager owns the 30s throttle.
env.unread.refresh()
```

Home's bootstrap uses a 60s module cache — `SettingsCache.get { env.api }`
(cached-first, concurrent callers share one request).

---

## 9. Base URL & config

`res/values/strings.xml → app_base_url` is the ONLY deployment knob (same
origin as the web panel). ApiClient normalizes it to `<base>/api/`; QR deep
links and share links are built from `env.baseUrl`. Replace the placeholder
before building (README STEP 1).

## 10. Compose conventions in this codebase

- Screens paint **transparent roots** (dark canvas glow is AppScaffold's).
- Column scroll pattern: `Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()))`.
- Buttons are pill-shaped (`AreenaxShapes.Pill` / `CircleShape`), primary CTA
  height 56dp (`h-14`), `pressScale` for the web's active-scale feedback.
- Sheets: `ModalBottomSheet(shape = AreenaxShapes.Sheet, containerColor = surfaceContainerLowest)`.
- Text: use `Type.*` styles, never raw `sp` unless matching a one-off web size.
- Money ALWAYS via formatters (`Rs ` prefix included in `rsMoney`/`rsBalance`
  or `"Rs ${formatMoney(…)}"` like the web).
- Loading = `AreenaxSpinner()`; errors via `env.toast(msg, Destructive)`;
  empty states via `EmptyState` with the web's exact copy.
