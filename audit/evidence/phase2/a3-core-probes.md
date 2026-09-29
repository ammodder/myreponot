# A3 — core-code probe evidence (file:line citations)

All paths relative to app/src/main/java/com/areenax/nativeapp/.

## N1 Navigation core
- AppNavHost.kt:47 `BACK_ROOT_SCREENS = setOf(HOME, LOGIN)`; :59-61 single global
  `BackHandler(enabled = navigator.currentScreen !in BACK_ROOT_SCREENS) { navigator.goBack() }`.
- AppNavHost.kt:100-163 registry — 46 ScreenKeys → composables; :162 unknown → login.
- AppNavigator.kt:21 initial = LOGIN; :49-61 navigate() pushes current + cap;
  :54-56 `while (_stack.size > MAX_STACK) removeAt(0)`; :117 MAX_STACK=25.
- AppNavigator.kt:63-69 replace() clears stack; :71-83 goBack() empty→home-if-logged-in
  else login (74); :86-92 setAuth(); :95-101 logout(); :108-114 enforceAuthGate().
- ScreenKeys.kt:86-88 NAV_SCREENS (6 roots); :91-93 PUBLIC_SCREENS (auth+terms+privacy);
  :96-98 AUTH_SCREENS.
- NavEnv.kt:58-65 navigate/replace/goBack wrappers; :68-71 setAuth; :74-77 logout.
- AreenaxApplication.kt:38-40 navigator.isLoggedIn = { session.user.value != null };
  :72-75 ApiClient.onUnauthorized = session wipe + navigator.logout().

## N2 Network / in-flight guard / timeouts
- ApiClient.kt:41-46 OkHttpClient.Builder() — interceptors ONLY; **no connectTimeout/
  readTimeout/writeTimeout/callTimeout anywhere in the file** → OkHttp defaults
  (connect 10s, read 10s, write 10s, no whole-call timeout).
- ApiClient.kt:95-112 InFlightGuardInterceptor: `if (request.method == "GET") proceed`;
  key = `"${method} ${url.encodedPath}"`; second concurrent non-GET →
  RequestInFlightException BEFORE the network; finally-remove (106-110).
- ApiResult.kt:29 RequestInFlightException : IOException("Request already in progress");
  :55-70 safeCall maps it to Error(message, code=409) (57-58), HttpException →
  Error(extractErrorMessage) (59-65), IOException → NetworkError (66-67).
- ApiClient.kt:114-123 UnauthorizedInterceptor: HTTP 401 → onUnauthorized().

## N3 Session / persistence / theme
- SessionManager.kt:64-73 init hydration (token/user/theme from DataStore "areena-app");
  :84-93 setAuth persists token+user; :197-208 logout wipes token/user/unread/pending-qr;
  :116-121 setTheme persists "dark"/"light" (KEY_THEME :226).
- MainActivity.kt:32-65: installSplashScreen, enableEdgeToEdge (35), darkTheme from
  `app.session.themeIsDark` (40) — the PERSISTED APP choice, not the system setting;
  SideEffect re-syncs system-bar icon contrast (44-58).
- Theme.kt:176-193 AreenaxTheme(darkTheme, followSystem=false) → useDark = darkTheme
  (182); LightScheme/DarkScheme + ExtendedColors (48-159); offlineGradient identical
  in both schemes (140 vs 157) — fixed gradient by design.
- AppShell.kt:83 `checked` in plain `remember`; :92-106 session-restore effect:
  token → GET /me → setUser; auth screen → replace(home); :116-118 enforceAuthGate
  on user change; :121-129 offline navigate(offline) / regain goBack; :132-134 unread
  refresh per navigation; :141-151 splash gate.

## N4 Connectivity / offline
- ConnectivityObserver.kt:24-41 StateFlow isOnline + NetworkCallback (onAvailable /
  onLost→recheck / onCapabilitiesChanged VALIDATED); :43-52 start(); :62-67
  currentlyOnline() (INTERNET + VALIDATED).
- AppShell.kt:121-129 offline swap. OfflineScreen.kt:132-156 "Try again" →
  goBack() only when isOnline else "Still offline" toast.

## N5 Polls / background / lifecycle
- MyTournamentScreen.kt:101-119 `LaunchedEffect(hasAccount) { while (isActive) {
  ...delay(30_000) } }` — GET /my/tournaments every 30s; errors only when never loaded.
- ChatScreen.kt:113-118 3s chat poll loop (CHAT_POLL_MS :78).
- TournamentDetailsScreen.kt:134-141 1s countdown tick `while(true) { delay(1_000); tick++ }`.
- UnreadManager.kt:38-48 refresh() 30s-throttled GET unreadCount (countOnly parity per
  Phase-1 note); AppBar.kt:145-147 bell refresh on mount.
- Grep: `SavedStateHandle|repeatOnLifecycle|Lifecycle` → **0 matches repo-wide**.
  No FCM receiver exists (no FirebaseMessagingService in the project file list).

## N6 Keyboard / windowSoftInputMode
- AndroidManifest.xml:29 `android:screenOrientation="portrait"`; :31
  `android:windowSoftInputMode="adjustResize"`; MainActivity.kt:35 enableEdgeToEdge.
- imePadding usage: 16 occurrences / 8 files (Login, Signup, Step1-3, Chat,
  TransferScreen root :184, DepositConfirm scroll column :230, ChatScreen root :154).
- No imePadding: WithdrawScreen, DepositScreen (no text inputs — keypads only),
  TournamentDetailsScreen join bar, DepositConfirm FIXED FOOTER (Surface :443-488 is a
  sibling of the imePadding scroll column, so the keyboard can cover "Confirm Deposit").

## N7 Double-tap guards on money+join actions (synchronous busy-flags)
- Join: TournamentDetailsScreen.kt:266-293 handleJoin sets joining=true synchronously
  before launch; Button enabled=!joining (1540) + spinner (1558-1564).
- Proof: proofUploading set synchronously (166-167); Button enabled=!proofUploading (1461).
- Deposit confirm: submitting=true before scope.launch (176); enabled=!submitting (473);
  second-tap also lands on `submitting -> Unit` (163).
- Withdraw confirm: :101-140 same pattern; enabled=!submitting (293).
- Transfer: submitting guard (123,140); enabled=!submitting (475).
- Global net for ALL non-GET: InFlightGuardInterceptor (N2) → second concurrent call
  becomes local Error 409 "Request already in progress" (safeCall :57-58).
- Logout: ProfileScreen.kt:388-393 spawns `CoroutineScope(Dispatchers.Main)` per tap;
  double-tap → two POST /auth/logout (second rejected by in-flight guard; result ignored).
- Guest/visitor CTA guards: rememberGuestGate (AccountRequiredDialog.kt:56-91) used on
  deposit/withdraw/transfer/join/claim/bind/team/profile-edit paths.

## N8 Dead controls / unconsumed params
- TeamJoinSheet.kt:67 `fun TeamJoinSheet(` — ZERO call sites (grep a3-grep-controls.log §9).
- ResultProofSheet.kt:61 — ZERO call sites.
- WhatsAppFab.kt:41 — ZERO call sites (deprecated per Theme.kt:40 comment).
- AppShell.kt:230-235 processPendingQr navigates `friends {qrUser}` / `myTeam {qrTeam}`;
  grep `env.params[` (a3-grep-controls.log §8) shows NO screen reads "qrUser" or
  "qrTeam" — FriendsScreen.kt and MyTeamScreen.kt absent from the consumer list.

## N9 Back stack
- Cap 25 (AppNavigator.kt:54-56,117): silent drop of the OLDEST entry beyond 25.
- goBack fallback (74) + BackHandler roots (AppNavHost.kt:47,59).
- Success screens break the back chain with replace(): DepositSuccessScreen.kt:82,125,143;
  WithdrawSuccessScreen.kt:75,117,135; TransferSuccessScreen.kt:71,107,125;
  TeamCreationDoneScreen.kt:82,137,162.
- Auth screens are NOT back-roots → back pops normally; on session restore while on an
  auth screen → replace(home) (AppShell.kt:99-101); anonymous on private screen →
  enforceAuthGate snaps to login (AppNavigator.kt:108-114).

## N10 Rotation / process death / config changes
- Manifest pins portrait (N6) → orientation recreation impossible; BUT uiMode/fontScale/
  density changes still recreate (Activity has no configChanges override; manifest :25-36).
- Nav state = in-memory only: AppNavigator (mutableStateOf/mutableStateListOf —
  AppNavigator.kt:26-41) never persisted (KDoc :19 "never persisted"); SessionManager
  persists only token/user/theme/bank/pending (KDoc :28-36).
- rememberSaveable = 59 uses / 13 files — auth (5 files), wallet (6 files), Friends tab.
  Everything else is `remember` (e.g., TournamentsScreen.kt:87-92 tab/results/room;
  TournamentDetailsScreen.kt:124-132 tab/joining/room/proof; ProfileScreen.kt:95-96).
- After recreation: AppShell splash → session restore → auth-screen/replace(home) logic
  (AppShell.kt:92-106) → user is back on HOME with the stack cleared; any half-filled
  form NOT in the rememberSaveable list is reset (TournamentDetails room reveal, deposit
  confirm TrxID… wait — DepositConfirm IS saveable; TrxID survives. Non-saveable
  examples: FriendsScreen query (remember :108), TournamentsScreen filter state,
  EditProfileScreen fields (remember :95-100), AdminPanel form (remember :125)).

## N11 Dark mode hardcoded colors (all isDark-paired)
- HomeScreen.kt:98 homeHairline (#E2E2EC light only, outlineVariant dark);
  MyTournamentScreen.kt:88-89 + ResultsScreen.kt:85-86 primaryFixedTone (#DBE1FF/#182338);
  NotificationsScreen.kt:382-383 PAYMENT pair; MyTeamScreen.kt:276-281 tag chip pair;
  AppShell.kt:70 DarkCanvas #060608 painted only when isDark (137,147,158);
  OfflineScreen gradient fixed both modes (Theme.kt:140/157 — web parity comment).

## N12 Big-font risk spots (dp-fixed boxes with sp text, singleLine)
- Inputs h=48dp singleLine: LoginScreen.kt:479/495, SignupScreen.kt:373/389,
  Step1 PhoneField :479/526, TeamCreation PillTextField :567/581.
- 56dp CTAs with maxLines=1: TournamentCard.kt:405 + 412-413 (ActionButton);
  StatePill 48dp maxLines=1 (TournamentDetailsScreen.kt:1670,1686).
- 64dp stat rows: TournamentDetailsScreen.kt:845; ResultsScreen.kt:221.
- Type scale is sp throughout (Type.kt:52-155) — scaling itself works; the risk is
  fixed-height containers clipping, not non-scaling text.
