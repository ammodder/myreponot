# 00 — OVERVIEW: AREENAX Web User Panel → Native Android Specification

> **Purpose.** This document is the master specification for rebuilding the AREENAX web user
> panel (Next.js 16 SPA at `/` — Zustand screen-router, no URL routes) as a **fully native
> Android app (Kotlin + Jetpack Compose)**. The web app under `/home/z/my-project/src/**` is
> the single source of truth. Every value, string, color and behavior stated here was
> extracted from the actual code — nothing is invented.
>
> Companion docs in this folder:
> - `01-SCREENS.md` — per-screen spec for all 49 screen files (44 logical screens + AuthChrome helpers + admin sections)
> - `02-API.md` — complete API contract for every endpoint the user panel calls
> - `03-COMPONENTS.md` — shared component contract (design-system module)
> - `04-DESIGN-TOKENS.md` — color/type/spacing tokens mapped to Compose Material 3

---

## 1. App identity

| Item | Value | Source |
|---|---|---|
| App name | **AREENAX** (brand wordmark also rendered as "AREENA X" in auth brand row) | `src/lib/settings.ts` default, `AppShell` splash, `agent-ctx/DESIGN.md` |
| Display name (About) | "Areenax Tournaments" | `AboutScreen.tsx` |
| Tagline (desktop backdrop) | "Play. Compete. Win." / "Pakistan's esports tournament arena" | `AppShell.tsx` |
| Version | Delivered by `GET /api/bootstrap` → `settings.version` (server default `"1.2.0"`). About screen currently hardcodes "Version 1.0.0" as fallback text. | `src/app/api/bootstrap/route.ts`, `AboutScreen.tsx` |
| Currency | PKR, always prefixed `Rs ` (balance uses `Rs 1,250.00` — 2 decimals; other money `Rs 1,250` — 0–2 decimals) | `formatBalance` / `formatMoney` in `src/lib/api.ts` |
| Logo asset (splash/auth) | Remote Google-hosted PNG (URLs embedded in code, see `01-SCREENS.md` per screen). Native app MUST bundle a local logo — use `public/icons/areenax-512.png`. | `AppShell.tsx`, `AuthChrome.tsx`, `LoginScreen.tsx` |
| Brand primary (light) | `#004ac6` | `globals.css --color-primary` |
| Brand primary (dark) | `#2f6bff` | `globals.css .dark --color-primary` |
| Legal pages | Terms & Privacy are PUBLIC (viewable pre-auth) | `AppShell.tsx PUBLIC_SCREENS` |

Version check: the app has **no forced-update logic**; `settings.version` is informational only.

---

## 2. Global systems (must exist natively, exact behavior)

### a) Session / Auth

**Token + user persistence** (`src/lib/store.ts`):
- Zustand `persist` middleware under storage key **`areena-app`**, `partialize` persists ONLY
  `{ token: string|null, user: User|null }` (localStorage on web).
- **Native equivalent:** DataStore (Preferences/DataStore<Preferences>) or EncryptedSharedPreferences
  holding `token` and a serialized `user`. Nothing else is persisted (screen/stack are session-scoped).
- Initial state: `screen = "login"`, empty nav stack, `unread = 0`.

**Every API call carries the token** (`src/lib/api.ts`):
- Reads token from the persisted store and sets header **`x-token: <token>`** on every request
  (when a token exists). Also sets `Content-Type: application/json` on all calls.
- Every non-GET call also sends an **`Idempotency-Key`** header (UUID v4 via `crypto.randomUUID()`,
  fallback timestamp-random). Callers may pass `options.idempotencyKey` to reuse a key across
  retries of the same logical action.
- **Client-side double-submit guard:** one in-flight mutation per `METHOD path` — a second
  non-GET call to the same method+path while one is pending throws `ApiError("Request already in progress", 409)`.

**401 handling** (`src/lib/api.ts`): any non-OK response → error message from body `error` field
(fallback `Request failed (<status>)`). On **401 specifically**: remove the persisted token
(`localStorage.removeItem("areena-app")`) and call `logout()` → screen becomes `"login"`, user
cleared, unread reset, nav stack cleared. **Native equivalent:** on HTTP 401, wipe the session
DataStore and route to Login.

**Guest login flow:**
- Login screen "Continue as Guest" → `POST /api/auth/guest` → `{token,user}` where `user.isGuest = true`.
- Guest users are gated by `useRequireAccount(feature)` (`src/hooks/use-require-account.tsx`):
  when a guest taps an account-dependent action the hook shows the **AccountRequiredDialog**
  ("Account Required" / "You're exploring AREENAX as a guest." / "Create a free account to <feature>."
  / buttons **Create Account** → navigate `signup`, **Continue as Guest** → dismiss).
  Guarded actions include: deposit, withdraw, transfer, join tournament, claim task, host,
  create team, bind account, edit profile, add friends, send team join request.
- Server also enforces `requireNonGuest` on wallet/team/quiz mutations.

**Logout** (`store.ts logout()` + `ProfileScreen.handleLogout`):
1. `POST /api/auth/logout` (best-effort; body `{}`; `EditProfileScreen` variant sends `{all:true}`
   which revokes ALL sessions server-side).
2. Then `logout()` locally: **`sessionStorage.removeItem("areena-pending-qr")`** (drop any stashed
   QR deep-link so the next session starts clean), `token=null, user=null, screen="login",
   params={}, navStack=[], unread=0`.
- `push-client.ts` unsubscribes the device push token for this user on logout
  (`POST /api/push/unregister {token}`) — see push section below.

**Session validation on app start** (`AppShell`):
- After store hydration, if a token exists → `GET /api/me` → `setUser(res.user)`. If the restored
  screen is an auth screen (login/signup/step1-3) it is replaced with `home`. A 401 here
  auto-logs-out via the api() path. Until this check completes (or there is no token) the app
  shows a **splash**: brand logo (height 7rem) + small primary spinner, on `bg-areena`.
- **Native equivalent:** app start → read DataStore → if token, call `GET /me` with `x-token`
  → route Home (or Login on 401), always showing splash first.

### b) Navigation (Zustand screen-router, `src/lib/store.ts`)

Imperative screen stack — no URLs. Native equivalent: a single-Activity Compose Navigation
backstack, or a custom `NavController` mirroring this state machine exactly:

```
navigate(screen, params = {}):
    screen = new; params = new;
    navStack.push({ previous screen, previous params });   // stack capped at 25 (slice(-25))

replace(screen, params = {}):          // also used after success screens / setAuth
    screen = new; params = new; navStack = [];

goBack():
    if stack empty → screen = user != null ? "home" : "login", params = {}
    else pop last entry into (screen, params)

setAuth(token, user):                  // login / register / guest success
    token, user set; screen = "home"; params = {}; navStack = []

logout():                              // see §a
setUnread(n): unread = max(0, n)       // badge count, session-scoped
```

- Initial screen: **`"login"`**.
- `AppShell` force-corrects: `activeScreen = !user && !PUBLIC_SCREENS.has(screen) ? "login" : screen`
  (PUBLIC = login, signup, signupStep1..3, terms, privacy).
- Bottom-nav roots (nav shows): `home, tournaments, myTournament, friends, wallet, profile` (`NAV_SCREENS`).

**COMPLETE `Screen` union** (`src/lib/types.ts` — 46 keys):

| # | Screen key | # | Screen key |
|---|---|---|---|
| 1 | `login` | 24 | `transferSuccess` |
| 2 | `signup` | 25 | `selectBank` |
| 3 | `signupStep1` | 26 | `profile` |
| 4 | `signupStep2` | 27 | `editProfile` |
| 5 | `signupStep3` | 28 | `myStats` |
| 6 | `home` | 29 | `achievements` |
| 7 | `tournaments` | 30 | `leaderboard` |
| 8 | `tournamentDetails` | 31 | `myTeam` |
| 9 | `myTournament` | 32 | `teamCreation` |
| 10 | `results` | 33 | `teamCreationDone` |
| 11 | `hostTournament` | 34 | `friends` |
| 12 | `hostTournamentCreation` | 35 | `chat` |
| 13 | `hostTournamentSuccess` | 36 | `referEarn` |
| 14 | `hostTournamentCard` | 37 | `tasks` |
| 15 | `hostTournamentDetails` | 38 | `notifications` |
| 16 | `wallet` | 39 | `bindAccount` |
| 17 | `deposit` | 40 | `bindAccountSuccess` |
| 18 | `depositConfirm` | 41 | `about` |
| 19 | `depositSuccess` | 42 | `terms` |
| 20 | `withdraw` | 43 | `privacy` |
| 21 | `confirmWithdraw` | 44 | `adminPanel` |
| 22 | `withdrawSuccess` | 45 | `adminDashboard` (desktop Admin Console — rendered OUTSIDE the phone frame, only for `ADMIN_PANEL_ROLES`) |
| 23 | `transferMoney` | 46 | `offline` |

All 46 keys route to a screen component in `AppShell.REGISTRY` (fallback = LoginScreen if unknown).
`adminDashboard` maps to `src/components/admin/AdminDashboard.tsx` (not part of the 44 user-panel files;
documented for completeness — a privileged desktop-style console).

### c) AppShell (`src/components/AppShell.tsx`)

- **Phone frame:** outer page = dark desktop backdrop (only visible ≥768px: radial blue glows +
  dot grid + "AREENAX" wordmark). Inner frame: `max-w-[480px]`, centered, `bg-background`,
  `md:shadow-2xl md:ring-1 md:ring-white/10`, min-h-screen. **Native app = the frame is the
  whole screen** (full-bleed).
- **Screen transition:** `AnimatePresence` — each screen fades/slides in from +18px x, exits to
  -18px x, 0.18s easeOut. Native: horizontal slide/fade ~180ms.
- **Scroll-to-top on every screen change** (`window.scrollTo(0,0)` on `screen` effect).
- **BottomNav visibility:** rendered only when `user != null && NAV_SCREENS.has(screen)`
  (6 roots listed above). All other screens hide it.
- **Offline detection:** listens to `online`/`offline` events (`navigator.onLine`); on offline →
  `navigate("offline")` (OfflineScreen auto-recovers via `goBack()` on reconnect — see
  `01-SCREENS.md`). On start-up it also checks `navigator.onLine` once. **Native:** use
  `ConnectivityManager` NetworkCallback; on lost → navigate OfflineScreen; on regained → pop it.
- **QR deep-link capture:** on mount, reads `?qr=` URL param → stashes payload into
  `sessionStorage["areena-pending-qr"]` and cleans the URL. After auth (session restored or user
  set), `processPendingQr()` runs: parse payload → if `user` kind navigate `friends {qrUser:<id>}`,
  if `team` kind navigate `myTeam {qrTeam:<id>}`; invalid payload → discard.
  **Native:** stash pending QR in a process-death-safe store (DataStore) or handle via
  intent deep-link (`areenax://?qr=...`) with the same routing.
- **Notification tap deep-links:** `initPush()` (see push section) captures `?n=` param into
  `sessionStorage["areena-pending-link"]`; `processPendingLink()` routes after login via
  `parseDeepLink` mapping (`tournament:<id>` → tournamentDetails, `host:<id>` →
  hostTournamentDetails, `wallet|myTournament|notifications|home|myStats|results` → those screens).
- **Theme restore:** reads `localStorage["areena-theme"]`; if `"dark"` adds `dark` class.
  Profile toggles persist the same key. **Native:** persist a theme bool in DataStore; Compose
  theme reads it.
- **Push init:** calls `initPush()` once on mount (token lifecycle via a store subscription:
  on fresh token → `syncPushTokenIfPresent` + start desktop polling; on token cleared →
  `unregisterPushToken` + stop polling).

### d) Toast system (`src/hooks/use-toast.ts`, `src/components/ui/toast.tsx`, `ui/toaster.tsx`)

- API: **module-level** `toast({...})` and hook `const { toast } = useToast()`.
  Fields: `{ title?: string, description?: string, variant?: "default"|"destructive" }`.
- `TOAST_LIMIT = 1` — only ONE toast visible at a time (newest replaces older).
- Auto-dismiss: **5s** (Radix duration + animated timer bar at the bottom of the card; pauses on hover).
- **Visuals:** card (`rounded-2xl border p-4 pr-10`) that **slides in from the TOP edge on
  mobile** (springy `cubic-bezier(0.21,1.02,0.73,1)` 0.45s, from `translateY(-100%-16px) scale(0.95)`)
  and from the bottom on desktop; exit glides out the same side (0.3s ease-in).
  - `default` variant: `bg-surface-container-lowest`, `border-outline-variant/60`,
    `text-on-surface`, shadow `0 16px 48px -16px rgba(11,28,48,0.35)`.
  - `destructive` variant: Material-3 **error container** — `bg-error-container`
    (`#ffdad6`), `text-on-error-container` (`#93000a`), border `on-error-container/20`.
  - Timer bar: 3px, `bg-secondary/60` (default) or `bg-on-error-container/40` (destructive).
  - Title: 15px bold; description: 13px, opacity 90%; close X top-right.
  - Viewport: fixed top, full width, safe-area aware, `z-[100]`.
- **Native equivalent:** in-app Compose "Snackbar-like" top toast with the same layout/colors
  (do NOT use the platform Snackbar — copy must match). For foreground FCM toasts the web uses
  the same toast system (`push-client.ts`: `toast({ title: n.title, description: n.body })`).

### e) Unread badge

- Source of truth: **store `unread`** (`setUnread`), displayed ONLY inside the unified
  **`BellButton`** (`AppBar.tsx`) — red badge, `bg-error`, `text-on-error`, min-w 18px h 18px,
  10px bold count, `unread > 9 → "9+"`, 2px white ring, positioned -top/-right of the 40px bell.
- **Refresh pattern:** `BellButton` is memoized and mounted on ~30 screens; every mount fires
  `GET /api/notifications?countOnly=1` **unless the last successful check was < 30s ago**
  (module-level `lastUnreadCheck` timestamp). Badge count is therefore refreshed per screen
  navigation, throttled to 30s.
- Other writers of `setUnread`: NotificationsScreen (load + mark-read PATCH + delete DELETE),
  push foreground handler (`/notifications?countOnly=1`), desktop poll (native: FCM handler).
- **Native:** single "UnreadManager" (singleton) exposing `unread: StateFlow<Int>`, refresh
  function with the same 30s throttle, invoked on nav destination change + after FCM receive.

### f) Time & money formatters (`src/lib/api.ts`) — port-to-Kotlin pseudocode

```kotlin
// formatMoney(n): "1,250" / "1,250.5" — grouping, 0..2 decimals
fun formatMoney(n: Double?): String =
    NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 0; maximumFractionDigits = 2
    }.format(n ?: 0.0)

// formatBalance(n): ALWAYS 2 decimals — canonical balance "1,250.00"
fun formatBalance(n: Double?): String =
    NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 2
    }.format(n ?: 0.0)

// formatDateTime(iso): "05-10-2025 3:07 pm"  (dd-MMM-yyyy h:mm am/pm, MONTHS = Jan..Dec)
fun formatDateTime(iso: String): String {
    val d = parse(iso)
    var h = d.hours; val ampm = if (h >= 12) "pm" else "am"
    h = (h % 12).let { if (it == 0) 12 else it }
    return "%02d-%s-%d %02d:%02d %s".format(d.date, MONTHS[d.month], d.year, h, d.minutes, ampm)
}

// timeAgo(iso): "Just now" | "5m ago" | "3h ago" | "2d ago" | "05-10-2025" (date part only, ≥7d)
fun timeAgo(iso: String): String {
    val diff = now - parse(iso).time
    val mins = diff / 60000
    if (mins < 1)  return "Just now"
    if (mins < 60) return "${mins}m ago"
    val hours = mins / 60
    if (hours < 24) return "${hours}h ago"
    val days = hours / 24
    if (days < 7)  return "${days}d ago"
    return formatDateTime(parse(iso)).split(" ")[0]   // "dd-MMM-yyyy"
}

// countdownText(iso, now): future time until tournament start
//   ≤0 → "Starting soon"
//   days>0 → "{d}d {h}h {m}m"   hours>0 → "{h}h {m}m {s}s"   else "{m}m {s}s"
fun countdownText(startIso: String): String {
    val diff = parse(startIso).time - now
    if (diff <= 0) return "Starting soon"
    val d = diff / 86400000; val h = (diff % 86400000) / 3600000
    val m = (diff % 3600000) / 60000; val s = (diff % 60000) / 1000
    return if (d > 0) "${d}d ${h}h ${m}m" else if (h > 0) "${h}h ${m}m ${s}s" else "${m}m ${s}s"
}

// maskAccountNumber(acc) — from src/lib/qr.ts (used in bank rows)
//   "" | null → "—"
//   len ≤ 7   → "••••" + last4
//   else      → first2 + "••••••••" (8 dots) + last4
//   e.g. "PK36SCBL0000001123456702" → "PK••••••••6702"

// ResultsScreen compact number: Intl "compact" notation, maxFractionDigits 1
//   e.g. 12500 → "12.5K"  (Kotlin: custom or DoubleRounder-based)

// LeaderboardScreen fmt(n): Locale.US number grouping (0 decimals)
// MyStats/HostCreation formatMoney as above; dates like "Oct 25, 2025, 11:06 AM"
// (HostCreationScreen.formatDisplayDate: "MMM d, yyyy, h:mm AM/PM")
// BindAccountSuccess linkedDate: toLocaleDateString en-US { month:"short", day:"numeric", year:"numeric" } → "Oct 25, 2025"
// Chat bubble time: toLocaleTimeString 2-digit hour/minute ("05:42 PM" style, locale-dependent)
```

### g) Base URL

- Web calls `fetch("/api" + path)` — relative. **Native app must use a configurable base URL**
  string resource, following the arenaX pattern: `res/values/strings.xml → <string name="app_base_url">`
  (e.g. `https://areena.app`), and the API client prefixes every path with `<base>/api`.
- Complete list of top-level API paths the USER PANEL calls (full contract in `02-API.md`):
  `auth/{login,guest,register,check,logout}` · `me` · `bootstrap` · `wallet{,/deposit,/withdraw,/transfer}` ·
  `tournaments{,/:id,/:id/join,/:id/results,/:id/proof,/:id/result-proof}` · `my/{tournaments,hosted}` ·
  `team{,/join-requests,/join-requests/:id}` · `friends{,/requests,/requests/:id,/:id/messages}` ·
  `notifications` · `tasks` · `referrals` · `stats` · `leaderboard` · `achievements` · `bank` ·
  `qr/lookup` · `players/lookup` · `games/config` · `push/{register,unregister,poll}` ·
  `admin/{requests,requests/:id,tournaments,tournaments/:id/status,tournaments/:id/room,`
  `tournaments/:id/disable,tournaments/:id/results,proofs,proofs/:id,settings,security,games,games/:id,games/:id/config}`

### h) Images (Cloudinary / remote URLs)

- Game images, tournament banners, user avatars, receipts and result proofs are **remote image
  URLs** (Cloudinary CDN after upload; the web passes data-URLs in JSON bodies and the server
  re-encodes + uploads). The frontend renders them with plain `<img>` (no next/image) —
  `object-cover` for banners/games, `object-contain` for proofs/receipts.
- **Avatar fallback everywhere:** when `user.avatar` is null (the current UI renders only the
  letter-initial variant — a colored circle `bg-primary text-on-primary` with the FIRST character
  of `gameName` uppercased). Tournament cards/details show the HOST's initial the same way.
  Native: Coil AsyncImage with `error`/`fallback` painter = initial circle.
- **Banner fallback:** `bannerImage ?? game.image`; when both are empty the hero shows a
  gradient placeholder (from-gray-900 to-black) with a centered `stadium` Material Symbol
  (tournament details) or the "AREENAX + 9-dot" wordmark (home hero).
- Receipt/proof images come back as **data URLs** (`data:image/...`) in API responses
  (`/admin/requests/:id`, `/admin/proofs/:id`) — native decodes base64 directly.
- Uploads: the client reads the picked file via FileReader → `data:image/...;base64` string and
  sends it in the JSON body (`image` field). Server caps: deposit receipt ≤ 8 MB, result proof
  ≤ ~4 MB, host tournament image ≤ 5 MB, avatar ≤ 8 MB. **Native:** pick with Photo Picker /
  GetContent, downscale if needed, base64-encode into the same JSON field.

### i) QR system (`src/lib/qr.ts`, `QrSheet.tsx`, JoinTeamSheet/TeamJoinSheet, pending-qr)

**Payload model:**
- user QR payload = `"u:<in-app 6-digit uid>"` (User.uid is unique); team QR = `"t:<team id>"`.
  Long forms `"user:<id>"` / `"team:<id>"` are also accepted by the scanner parser.
- The **scannable QR image encodes a full deep link**: `https://<origin>/?qr=u%3A123456`
  (`buildQrLink(kind,id)` uses `window.location.origin`, fallback `https://areena.app`).
  **Native:** base URL must come from the same configurable `app_base_url` resource.
- Generation (web): `qrcode` npm lib → 512px data URL, margin 2, dark `#0b1c30` on white.
  **Native:** use **ZXing core** (`QRCodeWriter`, size 512, margin 2, colors `#0b1c30`/`#FFFFFF`)
  — the spec mandates no extra server dependency.

**Display vs scan vs manual — the app supports ALL THREE:**
1. **Display ("My QR" tab):** renders the deep-link QR for the signed-in user (target `"user"`,
   payload `u:<uid>`, caption "Scan to add me as a friend") or for a team (target `"team"`,
   `t:<id>`, "Scan to view & join my team"). Share button → `navigator.share` with text
   ("Add me on AREENAX! Scan my QR or use my UID <uid>." / "Join my team <name> on AREENAX! Scan this QR.")
   falling back to clipboard + "Copied!" toast. **Native: Android share sheet (Intent.ACTION_SEND).**
2. **Scan ("Scan QR" tab):** live camera scanner (web: html5-qrcode, facingMode environment,
   fps 10, qrbox 220) with a QR watermark. Decoded text → `parseQrPayload` → `GET /qr/lookup?uid=`
   or `?teamId=` → result card (add friend / message / request-to-join). Scanner pauses after a
   scan and restarts for another. **Native: ML Kit Barcode Scanning or ZXing-android-embedded +
   CameraX; same lookup calls and result cards.**
3. **Manual UID entry fallback:** numeric-only field ("Or enter a Player UID", placeholder
   `e.g. 782104`) → treated as `u:<uid>` → same lookup. Also the primary add path in the
   Friends "Add" tab.

**Pending-QR flow (crossed auth):** `AppShell` captures `?qr=` → `sessionStorage["areena-pending-qr"]`
→ auth screens show the **PendingQrBanner** ("You scanned an AREENAX QR code" / "Log in or create
an account to add this player as a friend." / "...to join this team." + "Download the App" button
using `settings.appDownloadUrl`). After login/registration `processPendingQr()` routes to
`friends {qrUser}` / `myTeam {qrTeam}` which open the respective connection popup. Logout wipes
the key. **Native: store pending QR in DataStore (survives process death) and clear after routing/logout.**

**JoinTeamSheet / TeamJoinSheet (team joins):** these are NOT QR — they are the team-tournament
join flows (see `03-COMPONENTS.md`): `JoinTeamSheet` = legacy multi-step join (choose → manual UIDs
with live verification via `/players/lookup` → confirm & pay) — **currently dead code (not imported)**;
`TeamJoinSheet` = the 1v1/2v2/4v4 side-join sheet used for Team Battle tournaments; slot-based
48-slot joins use `SlotPickerSheet` (also currently unreferenced by screens). Native must implement
the live flows (solo join + TeamJoinSheet semantics + RoomInfoSheet) and may skip the dead ones.

---

## 3. Cross-cutting behavior notes (web-only APIs → native equivalents)

| Web behavior | Where | Native equivalent |
|---|---|---|
| `sessionStorage["areena-pending-qr"]` | AppShell, Login/Signup banners, logout | DataStore key `pending_qr` (or intent extra on deep-link launch) |
| `sessionStorage["areena-pending-link"]` | push-client deep links | DataStore key `pending_link` |
| `sessionStorage["areena_bank_selection"]` + legacy `areena_bank_method` | Deposit/Withdraw selection memory | In-memory session state (selection is per-visit) or DataStore |
| `localStorage["areena-app"]` (token+user) | store persist | EncryptedSharedPreferences / DataStore |
| `localStorage["areena-theme"]` | Profile theme toggle | DataStore theme bool |
| `localStorage["areena-push-token"]`, `["areena-device-id"]` | push-client | FCM token kept by Firebase SDK + a persisted UUID |
| `navigator.share` (results/details/profile/QR share) | 4 screens | Intent.ACTION_SEND (Android share sheet); keep clipboard fallback |
| `navigator.clipboard.writeText` | Room IDs, rules, referral code | ClipboardManager + same toasts ("Room ID copied to clipboard" etc.) |
| `window.location.reload()` | OfflineScreen "Try again" | Recreate activity / retry network call |
| `window.open(https://wa.me/...)` | SupportFab, About links | Intent ACTION_VIEW (WhatsApp deep link or browser) |
| `FileReader` → data URL uploads | deposit receipt, result proof, host image, avatar | Photo Picker + base64 (respect per-endpoint MB caps) |
| `window.scrollTo(0,0)` on screen change | AppShell | each destination starts scrolled to top (default) |
| Framer-motion slide-up sheets (0.25s easeOut, backdrop fade 0.2s, rounded-t-[2rem] drag handle) | all sheets | ModalBottomSheet with matching radii/durations |
| Material Symbols icon font (Outlined + Rounded variants, FILL axis) | everywhere | bundle Material Symbols variable font, or map each icon name to Material Icons (appendix in 03-COMPONENTS.md lists the names) |

---

## 4. Polling intervals & timers inventory (exact)

| Timer | Interval | Location |
|---|---|---|
| Chat message polling | **3000 ms** | ChatScreen (GET /friends/:id/messages; skipped state-write when list identical) |
| My Tournaments live status refresh | **30000 ms** | MyTournamentScreen (GET /my/tournaments) |
| Bell unread throttle | **30000 ms** min gap between actual fetches | AppBar BellButton |
| Unread check on screen mount | every mount (throttled above) | BellButton useEffect |
| Tournament details countdown tick | 1000 ms | TournamentDetailsScreen, HostDetailsScreen |
| RoomSheet visible-until countdown | 1000 ms | RoomSheet |
| JoinTeamSheet live balance poll | 4000 ms | JoinTeamSheet (dead code — documented) |
| Desktop push poll | 30000 ms | push-client (web/windows only — native uses FCM) |
| Home bootstrap cache | 60 s TTL module cache | HomeScreen |
| QrSheet camera restart | on each lookup completion | QrSheet (scanEpoch) |
| Notifications long-press threshold | 1000 ms hold, cancel on 12px move | NotificationsScreen |

---

## 5. Roles & the in-app admin panel

- Role model (`src/lib/roles.ts`): `USER < MODERATOR < FINANCE_ADMIN < SUPER_ADMIN`
  (legacy literal `"ADMIN"` = SUPER_ADMIN rank). Client visibility gate only; server enforces RBAC.
- `ADMIN_PANEL_ROLES = [MODERATOR, FINANCE_ADMIN, SUPER_ADMIN, ADMIN]`.
- ProfileScreen shows the "Admin Console" row ONLY for these roles → `adminDashboard`
  (desktop Admin Console, outside the phone frame). The in-frame **AdminPanelScreen** (screen key
  `adminPanel`) is reached programmatically and hosts the mobile admin sections
  (Games / Payment Requests / Tournament Mgmt / Result Proofs / Settings / Security) with
  per-section role gates — fully specified in `01-SCREENS.md` (profile category).
- Guests: `isGuest=true`; every money/host/team action is blocked by the guest dialog
  (client) and `requireNonGuest` (server).
