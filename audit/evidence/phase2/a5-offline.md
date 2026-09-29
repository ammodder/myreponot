# A5 evidence — offline behavior (gate, recovery, DataStore, polls)

Agent: A5 (TASK-006). Read-only. All paths under /home/z/audit-wt/AreenaxNativeAndroid/app/src/main/java/com/areenax/nativeapp/.

## 1. Connectivity detection — NetworkCallback, VALIDATED capability

core/util/ConnectivityObserver.kt:27-41 (callback):
```
27    private val callback = object : ConnectivityManager.NetworkCallback() {
28        override fun onAvailable(network: Network) { _isOnline.value = true }
32        override fun onLost(network: Network) {
33            // Only report offline when NO validated network remains.
34            _isOnline.value = currentlyOnline()
35        }
37        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
38            _isOnline.value =
39                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
40        }
41    }
```
Registration (ConnectivityObserver.kt:43-52): registerNetworkCallback with
NET_CAPABILITY_INTERNET request; failures swallowed (:49-51). currentlyOnline() (:62-67) requires
INTERNET + NET_CAPABILITY_VALIDATED on the active network. ACCESS_NETWORK_STATE permission:
AndroidManifest.xml:6. start()/stop() are wired in AppScaffold DisposableEffect (AppShell.kt:86-89).

## 2. Gate behavior in AppShell

core/ui/AppShell.kt:120-129:
```
121    LaunchedEffect(isOnline) {
122        if (!isOnline) {
123            if (navigator.currentScreen != ScreenKeys.OFFLINE) {
124                navigator.navigate(ScreenKeys.OFFLINE)
125            }
126        } else if (navigator.currentScreen == ScreenKeys.OFFLINE) {
127            navigator.goBack()
128        }
129    }
```
- navigate() PUSHES the offline screen (AppNavigator.kt:49-61 — stack entry {screen,params}); the
  duplicate-push guard is the `currentScreen != OFFLINE` check (:123).
- Regain → goBack() pops exactly one entry (AppNavigator.kt:71-83); empty stack → home/login
  fallback (:73-75).

## 3. OfflineScreen — mount recovery + Try again (REQ-057)

ui/screens/info/OfflineScreen.kt:66-70:
```
68    LaunchedEffect(Unit) {
69        if (env.connectivity.isOnline.value) env.goBack()
70    }
```
Try again button (:132-145): pops when online (:136-137), else destructive "Still offline" toast
(:139-143). No retry loop, no polling — recovery is purely event-driven (auto-pop on the
`isOnline` StateFlow + mount check).

## 4. Mid-flow data loss — screen state is not preserved across the offline push/pop

core/nav/AppNavHost.kt:63-84: AnimatedContent keyed on NavState(screen,params,generation) renders
exactly ONE screen composable (ScreenForKey :82-84). When `offline` is pushed, the form screen
LEAVES composition; all form fields across ui/screens use `remember { mutableStateOf(...) }`
(e.g. ChatScreen.kt:85-90 input; TournamentDetailsScreen.kt:126; MyTournamentScreen.kt:96-99), NOT
rememberSaveable → on pop the screen is re-created with its params but EMPTY local state.
Verified by grep: `rememberSaveable` → 0 hits in ui/** (input survives only while the composable
stays composed; the offline push disposes it).
Web parity: the web REGISTRY/AnimatePresence also unmounts the previous screen on
navigate("offline") — same data-loss shape.

## 5. Session restore / DataStore offline

core/session/SessionManager.kt:64-73 — hydration is a LOCAL DataStore read (no network):
```
65        scope.launch {
66            val prefs = store.data.first()
67            _token.value = prefs[KEY_TOKEN]
68            val userJson = prefs[KEY_USER]
69            _user.value = userJson?.let { decodeUser(it) }
70            _themeIsDark.value = prefs[KEY_THEME] == THEME_DARK
71            _hydrated.value = true
72        }
```
AppShell.kt:92-106 — restored token triggers GET /me validation:
```
94        if (token != null) {
95            val result = safeCall { env.api.me() }
96            if (result is ApiResult.Success) {
97                env.session.setUser(result.data.user)
...
103        // Failure (401) auto-logged-out via ApiClient.onUnauthorized.
104        }
105        checked = true
```
→ NetworkError (offline) does NOT clear the hydrated DataStore user; only Success overwrites and
only 401 wipes (ApiClient.kt:118-119 → AreenaxApplication.kt:72-75). Offline start with a valid
persisted token therefore lands IN-APP with the cached profile; splash ends when /me fails
(fast offline) — `checked = true` in both branches (:105).
Splash gate: AppShell.kt:141 `if (!hydrated || (token != null && !checked))` — with a black-holed
network the /me call takes the full 10s read timeout before the splash clears.

Auth gate: AppNavigator.enforceAuthGate (:108-114) snaps anonymous visitors to LOGIN for anything
outside ScreenKeys.PUBLIC_SCREENS (ScreenKeys.kt:91-93 — LOGIN/SIGNUP×3/TERMS/PRIVACY; OFFLINE is
NOT in PUBLIC_SCREENS; gate only re-runs on user changes, AppShell.kt:116-118).

## 6. Poll loops and repeated-failure behavior (A1 F-13 follow-up)

All loops found (grep `while|delay` over ui/**):
- ChatScreen.kt:78 `CHAT_POLL_MS = 3_000L`; :113-118 `while (isActive) { load(); delay(...) }`;
  failure branch :105-107 `// Poll errors are silent; first-load failure surfaces via !friend.`
  → NO toast per failed poll. Send-failure toast is user-triggered only (:139-145).
- MyTournamentScreen.kt:106-118 `while (isActive)` 30s; :113-115 comment "Only surface the error
  when we never loaded — later poll failures must not wipe an already-rendered list"
  (`else -> if (!loaded) error = true`) → NO toast per failed poll; stale list retained.
- UnreadManager.kt:38-48 — 30s throttle (:32 `throttleMs = 30_000L`), `lastSuccessfulCheck`
  updated ONLY on success (:44); refresh() fired per nav generation (AppShell.kt:132-134) →
  while offline every navigation launches one silent failing GET /notifications?countOnly=1;
  "Silent on failure (the web never toasts this poll)" (:39). NO toast loop.
- Local (non-network) loops: TournamentDetailsScreen.kt:137-138 + HostDetailsScreen.kt:107-110
  1s countdown ticks; ReferEarnScreen.kt:109 / HostDetailsScreen.kt:125 `delay(2000)` copy-flash.
- push/poll is declared (Api.kt:310-311) but NEVER called from any screen (grep pushPoll in
  ui/** + core/** → only Api.kt) and no FirebaseMessagingService exists (grep FirebaseMessaging
  → 0 hits) — native has no push-poll loop at all.

Conclusion: NO toast-loop exists from automated polling (A1 F-13's risk is NOT realized — polls
are silent by construction; ToastController shows ONE toast (TOAST_LIMIT 1, ToastHost.kt:50), so
even user-triggered failures replace rather than stack). Residual: failing polls keep firing at a
fixed cadence with no backoff/jitter and no log — battery/server-load + silent-outage note.

## 7. Representative per-screen error branches (mapping-table anchors)

- LoginScreen.kt:385-391: Error AND NetworkError → toast "Login failed" + res.message.
- WalletScreen.kt:157-180: initial GET /wallet — Error :164-171 / NetworkError :172-179 both
  `txs = emptyList()` + toast "Couldn't load wallet" + res.message (destroys list on failure).
- DepositConfirmScreen.kt:201-210 / TransferScreen.kt:163-172 / ConfirmWithdrawScreen.kt:131:
  money paths → "Deposit failed"/"Transfer failed" + res.message (destructive).
- TournamentDetailsScreen.kt:152-158: `else -> failed = true` merges NetworkError with Error →
  :209/:235 renders EmptyState "Tournament not found." — 404 styling for any failure.
- HostDetailsScreen.kt:113-120: same conflation (`else -> notFound = true`).
- LeaderboardScreen.kt:84-111: one-shot per mount, Error/NetworkError → "Failed to load
  leaderboard" + message. Not a loop.
- AdminPanelScreen.kt:160: `loadError = res.message` rendered in the section body (not a toast).
