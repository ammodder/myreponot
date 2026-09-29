# FEATURE FREEZE REGISTER — AreenaxNativeAndroid (Task-5-era baseline)

- **Purpose:** this register lists EVERY feature that exists in the native app as of the
  Task-5-era baseline (93 Kotlin files / 46 route keys / 48 screen files, versionCode 2).
  Any future code change must be checked against it: **does this change break a frozen feature?**
- **Freeze rule:** any code change must not alter any row below without owner approval.
  After every change, re-check this register and re-verify the touched rows with the
  "How to verify" method.
- **Date:** 2026-09-19 (Phase 0, audit TASK-001, agent ffr-builder; rebuilt after sandbox rollback).
- **Verification is STATIC ONLY** — no device/emulator in this sandbox (an Android SDK was bootstrapped during Phase 0 for compile-only checks; see
  `audit/00-baseline/RUNTIME-ENVIRONMENT.md`). "How to verify" = grep/read the named file,
  or cross-check SPEC (`SPEC/01-SCREENS.md`, `SPEC/00-OVERVIEW.md`, `SPEC/02-API.md`).
- **Provenance caveat:** rows marked "(QA-C verified)" inherit copy checks whose evidence logs were lost in the sandbox rollback. Spot re-diffs were re-run 2026-09-19 (Terms/Privacy headings, polling constants, upload caps — all passed), but not every such row was individually re-diffed.
- **Path convention:** bare `X.kt` = `AreenaxNativeAndroid/app/src/main/java/com/areenax/nativeapp/…`
  (`ui/screens/<area>/X.kt` unless prefixed `core/`, `data/`, or root). Web truth =
  `/home/z/my-project/src/components/screens/**`. Rows cite the strongest static check.
- Sources: worklog Tasks 2-b/3-a/3-b/3-d/4/5, SPEC/00+01, direct code reads. Nothing invented;
  uncertain items are marked `[?]` with what to check.

---

## A. Auth & session (5 screens + session core)

| ID | Screen/Component | Feature | Trigger (how user reaches it) | Expected result | How to verify (static) |
|---|---|---|---|---|---|
| A01 | LoginScreen.kt | Login form: identifier pill field + password w/ eye toggle, "Welcome back" copy | App start (no session) | Pill fields, FILL visibility icons, lavender root | Read LoginScreen.kt; copy vs `src/.../auth/LoginScreen.tsx` |
| A02 | LoginScreen.kt | Identifier auto-detect: phone regex `^\+?[0-9]{7,15}$` → `{phone}` (retry with as-typed), else `{email}` | Tap "Login" | `POST /auth/login` w/ correct shape; failure retry per web | Grep phone regex in LoginScreen.kt; vs SPEC/02 §1 |
| A03 | LoginScreen.kt | Login/guest success → `env.setAuth(token,user)` → home, stack cleared | Success response | Home shown; token+user persisted | Grep `setAuth` in LoginScreen.kt; SessionManager.kt |
| A04 | LoginScreen.kt | "Continue as Guest" → `POST /auth/guest` w/ own spinner | Tap guest button | Guest user set, `isGuest=true` | Grep `guestLogin` in LoginScreen.kt + Api.kt path |
| A05 | LoginScreen.kt / SignupScreen.kt | PendingQrBanner: stashed QR payload shown pre-auth w/ "Download the App" pill | QR captured before login | Banner on login+signup; pill opens ACTION_VIEW app URL | Grep `PendingQrBanner` in both files |
| A06 | SignupScreen.kt | Game-name availability check before step 1 | Fill name+gameName → "Continue" | `POST /auth/check {gameName}`; taken → inline error; failure → proceed anyway | Grep `authCheck` in SignupScreen.kt |
| A07 | SignupStep1Screen.kt | Phone (+92 static chip) + numeric-only UID w/ availability check | Signup step 1 "Continue" | `POST /auth/check {phone, gameUid}`; per-field errors; nav → step2 | Read SignupStep1Screen.kt |
| A08 | SignupStep2Screen.kt | Live password checklist (letter, digit/symbol, ≥10) | Type in password field | Check items toggle w/ check_circle/radio icons; no API | Grep regex literals in SignupStep2Screen.kt |
| A09 | SignupStep3Screen.kt | Email check + register + agreement checkbox w/ inline Terms/Privacy links | Step 3 "Complete Account" | `POST /auth/check {email}` then `POST /auth/register`; links nav to TERMS/PRIVACY | Grep `register` + TERMS in SignupStep3Screen.kt |
| A10 | SignupStep3Screen.kt | Email-409 → inline error + "Go to Login" pill | Register returns 409 (email) | Detect via message; pill navigates LOGIN | Read 409 comment in SignupStep3Screen.kt (web branch is dead code — live on native) |
| A11 | SessionManager.kt | Session restore: DataStore "areena-app" (token/user/theme) → splash → `GET /me` → home | App start with stored token | Auth screens replaced by home; 401 → wipe → login | Read SessionManager.kt + AppShell.kt splash states |
| A12 | ApiClient.kt | `x-token` on every call; `Idempotency-Key` UUID v4 on every non-GET | Any API call | Headers present when token exists | Read ApiClient.kt interceptors |
| A13 | AppNavigator.kt + AppShell.kt | Guest gate on navigation: anonymous users only on PUBLIC_SCREENS (login/signup×4/terms/privacy) | Deep nav while logged out | Snaps back to login | Grep `enforceAuthGate` + `PUBLIC_SCREENS` |
| A14 | AccountRequiredDialog.kt | Guest action gating (`rememberGuestGate` → AccountRequiredDialog "Create Account"/"Continue as Guest") | Guest taps money/host/team/profile action | Dialog w/ feature string; Create → signup | Grep `rememberGuestGate` call sites (13 files) |
| A15 | ProfileScreen.kt | Logout: best-effort `POST /auth/logout` then local logout (clears pending stores) | Tap "Logout" | Back to login, state wiped | Grep `logout` in ProfileScreen.kt |

## B. Main & tournaments (5 screens)

| ID | Screen/Component | Feature | Trigger (how user reaches it) | Expected result | How to verify (static) |
|---|---|---|---|---|---|
| B01 | HomeScreen.kt | Hero banner: first banner image (Coil) else gradient + "AREENAX" wordmark + 9-dot grid; 3 dots when >1 banner | Open home tab | Aspect 2:1 hero per home.html | Read HomeScreen.kt hero block |
| B02 | HomeScreen.kt | Games/Host pill switcher + 2-column games grid; Games → `tournaments{gameId}`, Host → `hostTournament{gameId,gameName}` | Tap pill / game card | AnimatedContent fade+slide tab swap; grid cards | Grep `AnimatedContent` + nav targets in HomeScreen.kt |
| B03 | HomeScreen.kt | `GET /bootstrap` with module-level 60s TTL cache (errors never cached); settings pushed to SettingsCache | Home (re)entered | Cache hit skips fetch within 60s | Grep `BOOTSTRAP_TTL_MS = 60_000L` |
| B04 | TournamentsScreen.kt | Per-status tabs Upcoming/Ongoing/Completed w/ per-tab snapshot + per-tab error memory; each switch refetches `GET /tournaments?status=<tab>[&gameId]` | Open tournaments tab / switch pill | Instant cached tab render; isolated errors | Read TournamentsScreen.kt state block |
| B05 | TournamentsScreen.kt | Exact empty copy per tab "No upcoming/ongoing/completed tournaments." + trophy empty circle | Empty tab | bg-balance-chip circle + outlined trophy | Grep strings in TournamentsScreen.kt |
| B06 | TournamentCard.kt (core/ui) | Card actions: tap → details; ROOM → RoomInfoSheet (fresh fetch); RESULTS → results; PROOF → details | Tap card / card buttons | Per web TournamentCard.tsx | Read TournamentCard.kt + RoomInfoSheet.kt |
| B07 | TournamentDetailsScreen.kt | Load + hero 200dp w/ status pill ("Registration ends in "+countdown / "Match in progress" / "Match completed") | Open a tournament | bannerImage ?? game.image, gradient fallback | Read hero block of TournamentDetailsScreen.kt |
| B08 | TournamentDetailsScreen.kt | 1s countdown tick drives countdownText, isRoomOpen, computeTournamentAction (live state machine); summary cards (Prize Pool >0, Entry Fee always, Per Kill >0); Overview/Rules/Prizes tabs w/ numbered rules + copy pill | Details screen visible | Status/CTA change live w/o refetch; per-B3 grid 1–3 cols | Grep `delay(1_000)`; read tabs block |
| B09 | TournamentDetailsScreen.kt | Room reveal: ID & Pass rows w/ View/Hide toggle when roomOpen&&joined; copy buttons + "Room ID/Room password copied to clipboard" toasts; "Visible until «date»" | Joined + room open | Clipboard + exact toasts | Grep `copied to clipboard` in TournamentDetailsScreen.kt |
| B10 | TournamentDetailsScreen.kt | Join flow: guest gate → `POST /tournaments/:id/join` → success toast + balance update + refresh | Tap "Join Now - Rs «fee»" | "Joined successfully!" toast; "Room ID & password will appear before the match starts." | Grep `join` + toast strings |
| B11 | TournamentDetailsScreen.kt | Insufficient-balance error → InsufficientBalanceDialog(entryFee, balance, name) → "Deposit Now" | Join fails w/ "insufficient" | Dialog opens; else "Join failed" toast | Grep `InsufficientBalanceDialog` |
| B12 | TournamentDetailsScreen.kt | Result-proof upload: 8MB cap picker → `POST /tournaments/:id/proof {image}`; success/optimistic UI; 409 "already submitted" → lock + toast | Disabled-tournament joined card → SUBMIT RESULT PROOF | One-time submit; lock state | Grep `PROOF_MAX_BYTES` + 409 handling |
| B13 | TournamentDetailsScreen.kt | Share: chooser "«name» on AREENAX" + prize/fee/room lines; clipboard fallback toast | AppBar share icon | ACTION_SEND w/ FLAG_ACTIVITY_NEW_TASK | Grep `ACTION_SEND` in TournamentDetailsScreen.kt |
| B14 | MyTournamentScreen.kt | 30s live poll `GET /my/tournaments` (while(isActive)); guests/logged-out skip; error only if never loaded | My Tournaments tab open | Status changes appear within 30s | Grep `delay(30_000)` |
| B15 | MyTournamentScreen.kt | Filter pills (client-side deriveTournamentStatus); tap → results (completed) else details; "Browse Games" pill on empty | Use filters / tap card | Exact mytournament.html empty state | Read MyTournamentScreen.kt |
| B16 | ResultsScreen.kt | Match Statistics grid + winners list sorted rank/kills; isMe row tinted (#DBE1FF/#182338); "Rs." + Intl-compact prize pool | Open results | 403/empty → "Results not published yet." | Read ResultsScreen.kt |
| B17 | ResultsScreen.kt | Sticky "Share Results" → chooser + clipboard fallback "Results copied to clipboard" | Tap share button | ACTION_SEND per web | Grep share block in ResultsScreen.kt |

## C. Wallet (10 screens)

| ID | Screen/Component | Feature | Trigger (how user reaches it) | Expected result | How to verify (static) |
|---|---|---|---|---|---|
| C01 | WalletScreen.kt | Primary balance card "Current Balance" `Rs «formatBalance»` + `GET /wallet` syncs user.balance | Open wallet tab | Balance in card matches server | Read WalletScreen.kt |
| C02 | WalletScreen.kt | Transactions list w/ type icons/titles per type + method overrides; All/Money In/Money Out filter (IN_TYPES) | Use filter pill | Signed amounts; status colors; empty "No transactions yet" | Read WalletScreen.kt tx block |
| C03 | WalletScreen.kt | Quick actions Deposit/Withdraw/Transfer — each guest-gated ("make a deposit" / "withdraw your winnings" / "send money") | Tap action | Gate dialog for guests, else navigate | Grep `gate.requireAccount` in WalletScreen.kt |
| C04 | DepositScreen.kt | AmountPad always visible (max 8 significant digits, single "."); default method "Jazzcash"; bank selection persisted per session | Enter amount | Validation: "Enter an amount" / "Minimum deposit Rs «min»" | Read DepositScreen.kt + AmountPad.kt |
| C05 | DepositScreen.kt | Funding-source card + BankSelectSheet ("Choose Funding Bank"); FUNDING_BANKS incl. "Alphla Bank" (sic, binding) | Tap swap button | 5 banks + bound accounts; selection committed on confirm | Grep `FUNDING_BANKS` in core/util/TournamentState.kt |
| C06 | DepositConfirmScreen.kt | Receipt image upload ≤8MB (data URL) REQUIRED + TrxID REQUIRED; per-field toasts | Deposit confirm screen | Thumbnail + "Tap to change"; missing → exact toasts | Grep `UploadCaps.RECEIPT` in DepositConfirmScreen.kt |
| C07 | DepositConfirmScreen.kt | `POST /wallet/deposit {amount, method, trxId, receiptName, image?, accountId?}`; account number from settings.depositAccounts w/ hardcoded fallbacks | Tap "Confirm Deposit" (guest-gated) | Success → depositSuccess{amount, method, reference} | Read POST body in DepositConfirmScreen.kt vs SPEC/02 |
| C08 | DepositSuccessScreen.kt | SuccessAnim "Deposit Request Submitted!" + StatusChip PENDING + summary rows; back+CTA → repl(wallet); "Go to Home" → repl(home) | After deposit POST | Stack cleared to wallet/home | Read DepositSuccessScreen.kt |
| C09 | WithdrawScreen.kt | Account pill ("Select account"/"Bind Account"); quick chips 100/500/1000/5000; validation (min `Rs «min»`, > balance); CTA disabled w/o account | Withdraw screen | Sheet empty-action → `bindAccount{returnTo:"withdraw"}` | Read WithdrawScreen.kt |
| C10 | ConfirmWithdrawScreen.kt | `POST /wallet/withdraw {amount, method, accountNumber?, accountTitle?}`; hero + summary; 409 "pending withdrawal" surfaced | Tap "Confirm Withdraw" (guest-gated) | Success → withdrawSuccess | Read ConfirmWithdrawScreen.kt |
| C11 | WithdrawSuccessScreen.kt | "Withdrawal Request Submitted!" + held-balance copy + New Balance row; repl(wallet)/repl(home) | After withdraw POST | StatusChip PENDING | Read WithdrawSuccessScreen.kt |
| C12 | TransferScreen.kt | Recipient by UID or email (search → chip + Change); amount + optional note; `POST /wallet/transfer {recipient, amount, note?}` | Transfer screen → "Transfer Money" | Receiver gameName returned; error toasts per SPEC | Read TransferScreen.kt |
| C13 | TransferSuccessScreen.kt | "Transfer Successful!" + "You sent Rs «amount» to «receiver»" + rows; repl(wallet)/repl(home) | After transfer POST | SuccessAnim, no StatusChip | Read TransferSuccessScreen.kt |
| C14 | SelectBankScreen.kt | Legacy routable screen: dimmed deposit mock + sheet w/ search, 5 methods, "Your Accounts"; confirm writes session bank method → back() | Navigate selectBank | Kept for route parity | Read SelectBankScreen.kt |
| C15 | Formatters.kt + BalanceChip.kt (core) | Formatting rules: balance always 2 decimals (`Rs 1,250.00`); other money 0–2 decimals; `Rs ` prefix | Everywhere money renders | Byte-equivalent port of api.ts | Read Formatters.kt vs SPEC/00 §2f |

## D. Social (8 screens)

| ID | Screen/Component | Feature | Trigger (how user reaches it) | Expected result | How to verify (static) |
|---|---|---|---|---|---|
| D01 | MyTeamScreen.kt | No-team state: "No Team Yet" + "Create Team" pill (guest gate "create a team") → teamCreation | Open My Team w/o team | Exact myteam.html radial bg + copy | Read MyTeamScreen.kt |
| D02 | MyTeamScreen.kt | With-team: hero name+tag, stats cards, member rows (Owner chip), support note, Leave/Disband → `DELETE /team` + toast | Open My Team with team | "Team disbanded"/"You left the team"; error state w/ Try Again | Grep `leaveTeam` in MyTeamScreen.kt |
| D03 | TeamCreationScreen.kt | Form: name (max 24) + optional tag (4, uppercase) + leader card + 1–3 member UID inputs (add/remove) + `POST /team {name, tag?, memberUids}` | Fill + "Create Team" | Error toast covers "already belong to a team" | Read TeamCreationScreen.kt |
| D04 | TeamCreationDoneScreen.kt | SuccessAnim "Team Created!" + "Go to My Team" / "Back to Home" (replace navs) | After create | teamCreationDone w/ teamName param | Read TeamCreationDoneScreen.kt |
| D05 | FriendsScreen.kt | Tabs All/Online/Requests + search; online = lastMessageAt within 30min; meta row + cards w/ unread badge (9+ cap) | Open Friends tab | Exact freinds.html states + empty copies | Read FriendsScreen.kt |
| D06 | FriendsScreen.kt | Requests: received accept/decline, "Accept All" sequential, sent cancel (Pending chip); toasts per action | Requests tab | `GET/POST /friends/requests…` per SPEC/02 | Grep friendRequests/respondFriendRequest |
| D07 | FriendsScreen.kt | Add by Player UID: numeric input → guest gate → `POST /friends/requests {uid, source:"UID"}`; auto-accept → "You are now friends!" | Add tab → Send | 404/409 handled w/ exact toasts | Read Add-tab block |
| D08 | FriendsScreen.kt | Suggested Players demo rows w/ local "Sent" (owner gate C5: demo data) | Add tab bottom | Static demo entries | Grep "Suggested" in FriendsScreen.kt |
| D09 | ChatScreen.kt | 3s polling `GET /friends/{id}/messages` (while(isActive), diff-guarded, silent errors); send → `POST` append + auto-scroll | Open chat | New messages appear ≤3s | Grep `CHAT_POLL_MS = 3_000L` |
| D10 | ChatScreen.kt | Messenger UI: bubbles (mine primary right), online dot, STATIC "Today, 5:42 PM" divider (C3 owner gate), emoji append, IME Send | Open chat | Exact chat.html layout; failure state | Read ChatScreen.kt |
| D11 | ReferEarnScreen.kt | Referral code hero + "Copy Code" (2s "Copied!") + Share (chooser w/ clipboard fallback) + stats + referred list | Open Refer & Earn | `GET /referrals`; copy/share toasts exact | Read ReferEarnScreen.kt |
| D12 | TasksScreen.kt | Task rows w/ reward chips; Claim → guest gate → `POST /tasks` → balance sync + "+Rs X" toast; claimed state | Tasks (from Profile) | Empty: "No tasks available." | Grep `claimTask` in TasksScreen.kt |
| D13 | NotificationsScreen.kt | Type-icon list; tap expands in place (stays open); unread dots; read rows dimmed; tap-to-read PATCH optimistic | Open notifications | `GET/PATCH /notifications`; unread sync via setLocal | Read NotificationsScreen.kt |
| D14 | NotificationsScreen.kt | Mark-all-read (done_all header + inline link) → POST + setLocal(0) + refresh(force); expanded rows w/ link → parseDeepLink "Open →" | Tap mark-all / "Open →" | Navigates per deep-link grammar | Grep `markAllRead` + `parseDeepLink` |
| D15 | QrSheet.kt (core/ui) + FriendsScreen.kt + ProfileScreen.kt | QR sheet: MINE/SCAN tabs, ZXing scan, manual "Or enter a Player UID" fallback → `GET /qr/lookup` result cards | Friends QR button / Profile "QR Code" | `QrTarget.USER` w/ initialTab SCAN per code | Read QrSheet.kt; grep call sites |

## E. Profile & admin (11 files)

| ID | Screen/Component | Feature | Trigger (how user reaches it) | Expected result | How to verify (static) |
|---|---|---|---|---|---|
| E01 | ProfileScreen.kt | Header card: 96dp initial avatar ring, gameName+PK chip, email/"Guest user", UID, Share pill (chooser + clipboard fallback), QR Code pill | Open Profile tab | Share text w/ UID + referral code per web | Read ProfileScreen.kt header block |
| E02 | ProfileScreen.kt | Menu card 1: Edit Profile / Refer & Earn / My Team / Tasks / Theme switch / About / Terms / Privacy | Tap rows | Navigate to each key; theme toggles | Grep navigate targets in ProfileScreen.kt |
| E03 | ProfileScreen.kt | Theme switch → `env.session.setTheme(!themeIsDark)`; persisted in DataStore (NOT system setting) | Toggle Theme row | Dark/light applies app-wide | Grep `setTheme` + Theme.kt doc |
| E04 | ProfileScreen.kt | Menu card 2: My Stats / My Tournaments / Achievements / Leaderboard / Bind Account (guest gate) | Tap rows | Navigate; bind gated | Read menu card 2 block |
| E05 | ProfileScreen.kt | Admin Console row visible only when role ∈ ADMIN_PANEL_ROLES → navigates `adminPanel` (native) | Logged in as moderator+ | Row hidden for USER/guests | Grep `ADMIN_PANEL` in ProfileScreen.kt |
| E06 | ProfileScreen.kt | Social icons row (only configured) + Logout primary pill | Scroll profile | Browser intents; logout per A15 | Read ProfileScreen.kt |
| E07 | EditProfileScreen.kt | Save profile `PATCH /me {fullName, gameName, email, password?}` → "Profile updated" → back; validation messages | Edit + "Save Changes" | Guest gate "edit your profile" | Read EditProfileScreen.kt save block |
| E08 | EditProfileScreen.kt | Change password (current/new/confirm, ≥6 + match) w/ "Current password is incorrect" inline; "Log out from all devices" → `POST /auth/logout {all:true}` | Change-password card | Toasts "Password changed" / "Logged out from all devices." | Grep `all = true` in EditProfileScreen.kt |
| E09 | MyStatsScreen.kt | Win Rate hero (progress bar + %) + 2×2 stats grid + tappable Leaderboard card (`GET /stats`) | Open My Stats | Leaderboard row → leaderboard | Read MyStatsScreen.kt |
| E10 | LeaderboardScreen.kt | Podium 1/2/3 (crown, badges, points) + ranked list; isMe highlighted "You"; not-listed → bottom "You" row w/ stats rank | Open Leaderboard | Parallel `GET /leaderboard` + `GET /stats` | Read LeaderboardScreen.kt |
| E11 | AchievementsScreen.kt | Summary ring "% Completed" + category chips (All/Tournaments/Kills/Earnings/Social) + cards w/ progress; tap → detail bottom sheet | Open Achievements | `GET /achievements`; locked/unlocked styles | Read AchievementsScreen.kt |
| E12 | BindAccountScreen.kt | Saved accounts list + form (bank via sheet, holder, number ≥8) → `POST /bank` → "Account bound" → bindAccountSuccess{returnTo?} | Bind Account (guest-gated) | Footnote copy exact; errors per SPEC | Read BindAccountScreen.kt |
| E13 | BindAccountSuccessScreen.kt | "Account Linked!" + Verified chip + rows; doneTarget = returnTo=="withdraw" ? withdraw : wallet; "Back to Home" | After bind | repl(doneTarget) / repl(home) | Read BindAccountSuccessScreen.kt |
| E14 | AdminPanelScreen.kt | Gate + form + save: role ∈ ADMIN_PANEL_ROLES else "Admin access required."; 403 → denied; `GET /admin/settings` form keys; Save CTA → `POST /admin/settings {settings}` ("Settings saved"/"Save failed"; section headers SUPER_ADMIN-only) | Open admin panel / edit + save | Sections role-gated per RBAC matrix | Read AdminPanelScreen.kt header + gate; grep `Settings saved` |
| E15 | AdminPanelScreen.kt | Payment Requests (FINANCE_ADMIN+): list, View Receipt viewer (`GET /admin/requests/:id`), approve/reject, four-eyes "Final Approve" | Admin panel section | Exact toasts; four-eyes chip | Grep `approve` + `fourEyes` |
| E16 | AdminPanelScreen.kt | Tournament Management (MODERATOR+): status chips (400 on COMPLETED), proof-mode disable toggle, room form w/ expiry, results editor w/ auto-prize 50/30/20 + publish | Expand tournament row | POSTs to `/admin/tournaments/:id/…` per SPEC/02 | Read tournament-mgmt block |
| E17 | GamesAdminSection.kt | Games management (SUPER_ADMIN): create/update/activate/delete, config ops (modes w/ slots, maps, perspectives) | Admin panel section | Toasts "Game created"/"Match type added"/… | Read GamesAdminSection.kt |
| E18 | ResultProofsAdminSection.kt | Result proofs (MODERATOR+): thumbnails (prefetched data URLs), rows, full-screen viewer; empty copy | Admin panel section | `GET /admin/proofs[/:id]` | Read ResultProofsAdminSection.kt |
| E19 | AdminTournamentsScreen.kt (routed as key `adminDashboard`) | "Tournament Manager" port of web dead-code E9, rewired to LIVE contract (room `{roomId,roomPassword,roomExpiresAt}`, results `{entries}`); deny state | Navigate `adminDashboard` (programmatic only) | Expandable cards w/ Room/Results tabs | Read AdminTournamentsScreen.kt KDoc + AppNavHost.kt:146 |

## F. Host (5 screens)

| ID | Screen/Component | Feature | Trigger (how user reaches it) | Expected result | How to verify (static) |
|---|---|---|---|---|---|
| F01 | HostTournamentScreen.kt | Tab pills Upcoming/Ongoing/Completed/Created over `GET /my/hosted` (client-side scope by gameId); title = gameName | Home Host tab / nav | Per-tab empty copy per SPEC F1 | Read HostTournamentScreen.kt |
| F02 | HostTournamentScreen.kt | HostCard replica (banner, mode chip, status label, prize/fee grid, slots progress, "JOIN NOW ›" display-only) + floating create FAB | View host list | FAB → guest gate "host your own tournament" → creation | Grep `requireAccount` in HostTournamentScreen.kt |
| F03 | HostCreationScreen.kt | `GET /games/config` → auto-select game + first mode/map/perspective; Match Type bottom sheet sets FIXED slots | Open creation | "No match types configured…" empty state | Grep `games/config` in HostCreationScreen.kt |
| F04 | HostCreationScreen.kt | Form: name, date/time picker ("Oct 25, 2025, 11:06 AM" format), entry fee (default 50), disabled slots, rules modal | Fill creation form | Validation per SPEC F2; "Game not ready" toast | Read HostCreationScreen.kt |
| F05 | HostCreationScreen.kt | CALCULATIONS PREVIEW: collection = fee×slots; pool = 50%; rank1/2/3 = 50/30/20%; loser refund per SPEC; live update | Change fee/slots | Formula exactness vs SPEC F2 | Grep calculation block |
| F06 | HostCreationScreen.kt | Tournament image (optional) ≤5MB data URL via picker; `POST /tournaments {…}` → "Tournament hosted!" → success | Tap "Host Tournament" (guest-gated) | `UploadCaps.HOST_IMAGE` | Grep `UploadCaps.HOST_IMAGE` |
| F07 | HostSuccessScreen.kt | Bouncing check hero + "Tournament Hosted Successfully!" + summary card; "View Tournament Details" / "Created Tournaments" | After POST | Fetch `GET /tournaments/:id` w/ param fallbacks | Read HostSuccessScreen.kt |
| F08 | HostCardsScreen.kt | My created tournaments list of HostCards → details; trophy empty state | Navigate hostTournamentCard | `GET /my/hosted` | Read HostCardsScreen.kt |
| F09 | HostDetailsScreen.kt | Host view: hero + countdown pill w/ 1s tick, summary cards, tabs, "Copy room info" single-button + per-field copy (2s "Copied!"), participants list | Open own tournament | Host-variant room copy per SPEC F5 | Grep `delay(1000)` + "Copy room info" in HostDetailsScreen.kt |

## G. Info & system (4 screens + system-level)

| ID | Screen/Component | Feature | Trigger (how user reaches it) | Expected result | How to verify (static) |
|---|---|---|---|---|---|
| G01 | AboutScreen.kt | Identity block + server-driven version line `settings.version ?: "1.2.0"` (owner gate C4: server/fallback 1.2.0 vs versionName 2.0.0) + FALLBACK_MISSION text + Follow Us (wa.me/t.me/etc) + legal links + © 2026 | Open About | Links open browser intents; Privacy/Terms nav | Read AboutScreen.kt:106-112,173 |
| G02 | TermsScreen.kt | 10 static sections verbatim + "Last updated: March 2026"; PUBLIC (pre-auth); bell only when signed in | Terms link | Copy matches TermsScreen.tsx (QA-C verified) | Grep section headings |
| G03 | PrivacyScreen.kt | 8 static sections verbatim (incl. contact "support@gamingapp.com"); PUBLIC | Privacy link | Copy matches PrivacyScreen.tsx | Grep section headings |
| G04 | OfflineScreen.kt | "OOPSS!" + exact body + "Try again"; auto `goBack()` when connectivity returns; "Try again" retries | Offline → screen auto-navigated | Light gradient #F0F4F8→#DBE4EC | Read OfflineScreen.kt |
| S01 | AppShell.kt | Bottom nav ONLY when logged-in && currentScreen ∈ NAV_SCREENS (home, tournaments, myTournament, friends, wallet, profile) | Any navigation | Hidden elsewhere incl. offline | Grep `NAV_SCREENS` in AppShell.kt |
| S02 | AppShell.kt + ConnectivityObserver.kt | Offline detection: NetworkCallback → navigate(offline) on loss, pop on regain | Toggle connectivity | navigator.onLine equivalent | Grep `isOnline` in AppShell.kt |
| S03 | AppBar.kt + BellButton | AppBar bell w/ unread badge (red, 9+ cap); refresh on mount throttled to 30s (`UnreadManager.throttleMs = 30_000L`) | Navigate between screens | `GET /notifications?countOnly=1` ≤1/30s | Read UnreadManager.kt |
| S04 | SupportFab.kt (+ WhatsAppFab.kt) | Support FAB → `https://wa.me/<settings.whatsapp digits>` w/ FLAG_ACTIVITY_NEW_TASK; hidden when offline; failure → nav(about) | Visible on most screens | Settings-sourced number | Read SupportFab.kt |
| S05 | QRCodeImage.kt (core/ui) | QR generation via ZXing QRCodeWriter for deep-link QR (user/team payloads, app_base_url origin) | QrSheet "My QR" tab | 512px, white bg | Read QRCodeImage.kt |
| S06 | ToastHost.kt / ToastController (core/ui) | In-app toast system w/ default/destructive variants, exact copy per screen, single visible toast | Any success/error path | Matches web toast copy (QA-C) | Read ToastHost/ToastController |
| S07 | AppShell.kt + themes.xml | Splash: splash theme on launch + SPLASH state while hydrating/validating session | Cold start | Logo + spinner until session resolved | Grep `SPLASH` in AppShell.kt |
| S08 | Theme.kt (core/theme) | Dark/light follows SessionManager DataStore flow (NOT system setting); Task-5-era M3 scheme via Tokens.kt | Theme toggle / app start | Light+dark palettes per SPEC/04 | Read Theme.kt doc + Tokens.kt |
| S09 | ChatScreen / MyTournamentScreen / TournamentDetails+HostDetails | Polling/timers: chat 3s · my-tournaments 30s · details countdown tick 1s | Respective screens | Constants as coded | Grep `3_000L`, `30_000`, `delay(1_000)`/`delay(1000)` |
| S10 | PhotoPickers.kt + TournamentDetailsScreen.kt | Upload caps: receipt 8MB (C06), tournament-proof 8MB (B12), host image 5MB (F06); compressToCap + base64 data-URL | Any upload | `UploadCaps` constants 8/4/5/8 MB | Read UploadCaps object |
| S11 | app/build.gradle.kts | versionCode 2 / versionName "2.0.0"; minSdk 24 / target+compile 35; app id com.areenax.nativeapp | Build config | Frozen values | Read app/build.gradle.kts:13-17 |
| S12 | AndroidManifest.xml | Permissions: INTERNET + CAMERA + ACCESS_NETWORK_STATE; camera uses-feature required=false; NO deep-link intent filters | Manifest review | 3 permissions only | Read AndroidManifest.xml |
| S13 | AndroidManifest.xml + MainActivity.kt | Single activity, singleTask, portrait lock, splash theme, adjustResize; AreenaxApplication app class | Manifest review | No orientation change, no multi-window assumptions | Read AndroidManifest.xml:25-36 |
| S14 | strings.xml | `app_base_url` placeholder `https://YOUR-AREENAX-DOMAIN.example.com` — MUST be set before any deploy (worklog Task 5) | Deploy prep | ApiClient prefixes `<base>/api` | Read strings.xml:11 |
| S15 | ScreenKeys.kt | 46 route keys, 0 unrouted/double-routed in AppNavHost; NAV_SCREENS(6) / PUBLIC_SCREENS(7) / AUTH_SCREENS(5) sets | Any navigation | QA-A verified mapping | Read AppNavHost.kt bindings |
| S16 | parseDeepLink (ScreenKeys.kt) | Notification-link grammar: `tournament:<id>`, `host:<id>`, wallet/myTournament/notifications/home/myStats/results; URL-decode tolerant | Notification w/ link (in-app only) | Routes to mapped screen | Read ScreenKeys.kt:108-133 |

**Row count: A15 + B17 + C15 + D15 + E19 + F09 + G04 + S16 = 110 rows.**

---

## Known orphans & preserved quirks (frozen as-is; owner gates referenced)

1. **parseDeepLink has NO manifest deep links** — the grammar (S16) is reachable ONLY from
   in-app notification payloads (NotificationsScreen.kt:252,337) and AppShell pending-link
   processing (AppShell.kt:245). AndroidManifest.xml defines no VIEW intent-filters, so no
   external `areenax://`/https deep link can enter the app. Changing one without the other
   breaks the pairing.
2. **Unwired core sheets:** `TeamJoinSheet.kt` and `ResultProofSheet.kt` exist in core/ui but
   are called by NO screen (React truth: main screens join inline + upload proof inline;
   JoinTeamSheet/SlotPickerSheet are web dead code). `QrSheet` IS wired (FriendsScreen.kt:678,
   ProfileScreen.kt:352, both `QrTarget.USER`/`initialTab SCAN`). Do not "wire up" or delete
   the orphan sheets without owner approval.
3. **"Alphla Bank" typo is BINDING** (web parity) — `core/util/TournamentState.kt:175` and
   `BindAccountScreen.kt:495` (`BANK_OPTIONS`). Fixing the spelling breaks web parity. Owner gate C1.
4. **`app_base_url` is still the placeholder** `https://YOUR-AREENAX-DOMAIN.example.com`
   (strings.xml:11) — deployment blocker, not a code change (worklog Task 5 "USER ACTION REQUIRED").
5. **Theme is the Task-5-era M3 scheme** (core/theme/Tokens.kt, created by Task 4 FIX 1, values
   from SPEC/04 §1–2; ExtendedColors.primaryFixed additive FIX 4). The lost Task 11 "Midnight
   Charcoal" dark theme (#1C1C1E canvas / #3C3C3C input pills) does NOT exist in this source
   (grep-verified, STATE.md). Do not "restore" it without owner approval.
6. **`adminDashboard` key routes to AdminTournamentsScreen** (AppNavHost.kt:146) — a port of the
   web's DEAD-code AdminTournamentsScreen.tsx (SPEC/01 E9), rewired to the live room/results
   contract. On web, the Profile "Admin Console" row goes to the desktop AdminDashboard; on
   native it goes to `adminPanel` (ProfileScreen.kt:243). Divergence is intentional (Task 4 QA).
7. **Avatar upload: DTO-only.** Models.kt:855 defines an optional ≤8MB `avatar` on the profile
   update body and UploadCaps.AVATAR exists, but NO screen implements an avatar picker
   (EditProfileScreen.kt has none) — avatars render only as initial circles (QrSheet.kt:605 et al.).
   `[?]` Confirm with owner whether an avatar-upload UI was ever required.
8. **Email-409 "Go to Login" flow is live on native, unreachable on web** (web ApiError has no
   `field`; native detects via message — Task 3-a). Preserved per SPEC/01 A5 intent.
9. **Intentional divergences from current React (3-d build-record scope):** MyTeam has no
   join-requests/QR-deep-link popups; NotificationsScreen has no selection-mode/long-press
   delete. Owner gates C3 (static chat date divider / online status) and C5 (Friends demo
   "Suggested Players") remain open.
10. **About version is server-driven, not hardcoded**: `settings?.version?.takeIf { it.isNotBlank() } ?: "1.2.0"` (AboutScreen.kt:106) — divergence is server/fallback 1.2.0 vs versionName 2.0.0. Owner gate C4 (restated 2026-09-19 after Attacker correction).
11. **No account-deletion flow** anywhere in the app — owner gate C6 (Play policy risk).
12. **No runtime verification exists** for any row: sandbox has no device/emulator
    (RUNTIME-ENVIRONMENT.md). First Gradle build on a real SDK machine is an owner gate (C7).

---

## RE-RUN 2026-09-22 (R1b merge reconciliation + final-audit fix pass — FIX_PLAN R1b verification column)

Post-merge (main = audit 70-fix state ⊕ field-concept ⊕ GMD) and post-fix-batch freeze check:

- Drawable/visual parity re-run: `verify_visual.mjs` **154 pass / 0 FAIL / 3 skipped** (skips = deleted svg cache sources; icons present in res). Evidence: `audit/evidence/a16-visual.log`, pre-merge `compile-r3r5-run1.log` era runs consistent.
- Static sweep: `verify_sweep_7e.mjs` D1 0 unbalanced (97 kt) / D2 0 missing resource refs / D3 46 ScreenKeys 0 unrouted / D4 46 screen fns. Evidence: `audit/evidence/a16-sweep7e.log`.
- Build gates: assembleDebug + assembleRelease(R8+lintVital) BUILD SUCCESSFUL; badging both = com.areenax.nativeapp, versionCode 1, versionName 1.0.0, minSdk 24, SDK 36. Evidence: `compile-merge-r1b-run3.log`, `compile-batch1-run1.log`, `compile-batch1-release-run6.log`, `a16-badging-*.txt`.
- Release guard A13-01 verified BOTH ways (no flag ⇒ exact failure; flag ⇒ build). Evidence: `a16-release-guard-negative.log`, `compile-batch1-release-run6.log`.
- FFR rows are UNCHANGED by this pass by design: R3 token sweep = values byte-identical (A15 spot-check); R5 = semantics-only; R4 = no visual change (M3 48dp enforcement documented); R8 = additive network behavior only (chat poll cursor).
- Adversarial round on the delta: A15(8→dispositioned), A16(12/12 CONFIRMED), A17(8→dispositioned), A18 (independent review) — see `audit/STATE.md` task-19 entry.
- A18 note: FFR row 10 ("About fallback 1.2.0 vs versionName 2.0.0") predates R3b/A9-08 — current tree: fallback is 1.0.0 (AboutScreen) and versionName is 1.0.0 (Q11); row kept for history, superseded by this RE-RUN section.
- A18 note (pre-existing S4, recorded): ChatScreen cursor = last-listed message createdAt; a bot reply created in the gap between an in-flight poll snapshot and a newer optimistic append is delivered on chat re-entry (full fetch), not by later delta polls — cosmetic, self-healing; fix = track max-ever-seen cursor (follow-up).
