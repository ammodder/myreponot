# 02 — API CONTRACT (every endpoint the USER PANEL calls)

> Paths below are relative to the API base: web calls `fetch("/api" + path)`; native must call
> `<app_base_url>/api` + path (base URL = string resource `app_base_url`, arenaX pattern).
>
> **Global request rules** (from `src/lib/api.ts`):
> - All requests: `Content-Type: application/json`; authenticated requests add **`x-token: <session token>`**.
> - Every non-GET adds **`Idempotency-Key: <uuid v4>`** (server dedupes replays via the
>   IdempotencyKey table; wallet mutations also rate-limit and return 429 with messages).
> - Non-OK responses carry `{error: string}` (fallback "Request failed (<status>)").
>   **401 ⇒ client wipes the session and routes to Login.**
> - Client also blocks concurrent duplicate non-GET calls per method+path (throws 409 locally).
>
> Response shapes below list the fields the frontend ACTUALLY consumes; several routes return
> more (noted). All `user` objects = the `publicUser` projection:
> `{id, email, fullName, gameName, phone, gameUid, uid, avatar, role, referralCode, referredById?,
> balance, isGuest, createdAt}`.

---

## 1. Bootstrap & session

### GET `/bootstrap` — auth: optional
- Response: `{ games: Game[], banners: Banner[], settings: AppSettings }`
  - `Game`: `{id, name, image, isActive, sortOrder, matchDurationMinutes?, modes?, maps?, perspectives?}`
    (bootstrap returns active games ordered by sortOrder; config endpoints add the arrays)
  - `Banner`: `{id, title, image, sortOrder}` (active only)
  - `settings`: `{appName, whatsapp, instagram, telegram, discord, youtube, minDeposit, minWithdraw,
    referralBonus, welcomeBonus, commission, version, aboutMission, appDownloadUrl,
    depositAccounts: Record<methodSpelling, accountNumber>}` — depositAccounts keys include raw /
    lowercase / UPPERCASE / Title-case spellings of each admin-configured account name.
- Consumers: HomeScreen (games/banners + cacheSettings), DepositConfirm (depositAccounts), all
  `getSettings()` users (Support WhatsApp, min amounts, appDownloadUrl), AboutScreen.

### POST `/auth/login` — auth: none
- Body: `{email, password}` OR `{phone, password}` (client auto-detects; phone tries digits-only
  first then as-typed).
- 200: `{token, user}`. Errors: 400 "Email or phone is required"/"Password is required";
  401 generic credentials error; 403 "This account has been suspended"; 429 rate-limit message.

### POST `/auth/guest` — auth: none
- 200: `{token, user}` where `user.isGuest = true` (welcome bonus per settings; guests may get 0).
- 429/500 possible ("Could not start guest session").

### POST `/auth/register` — auth: none
- Body: `{fullName, gameName, phone, gameUid, password, email, referralCode?}`.
- 200: `{token, user}` (welcome bonus + referral bonus transactions + notification created
  server-side in one atomic transaction).
- Errors: 400 policy/missing-field messages; 409 duplicates with `field` info (email / phone /
  gameName / gameUid); password policy (10+ chars, letter + digit/symbol, breached list).

### POST `/auth/check` — auth: none
- Body: any subset `{email?, phone?, gameName?, gameUid?}`.
- 200: `{taken: Record<field, boolean>, messages: Record<field, string>}`.
- Used by signup screens for pre-flight availability (best-effort; failures ignored).

### POST `/auth/logout` — auth: required
- Body: `{}` (single session) or `{all: true}` (EditProfile "Log out from all devices" — revokes
  every Session row of the user).
- 200: `{ok: true}`.

### GET `/me` — auth: required
- 200: `{user}` (session validation on app restore; JoinTeamSheet also polls it).

### PATCH `/me` — auth: required (non-guest for password changes)
- Body (profile save): `{fullName, gameName, email: string|null, password?}`.
- Body (password change): `{currentPassword, password}`.
- Optional `avatar` (data URL ≤ 8 MB) supported by the route (uploaded to Cloudinary) — the
  current panel doesn't send it but native can.
- 200: `{user}`. Errors: 400 invalid email/password-policy/"Current password is incorrect";
  502 avatar upload failure.

---

## 2. Wallet

### GET `/wallet` — auth: required
- 200: `{balance: number, transactions: Transaction[]}` (newest first).
  - `Transaction`: `{id, userId, type: DEPOSIT|WITHDRAW|TRANSFER|PRIZE|ENTRY_FEE|REFUND|
    REFERRAL_BONUS|TASK_REWARD|ADJUSTMENT, amount (signed), method, status: PENDING|COMPLETED|
    FAILED|REJECTED, reference, note, createdAt}`.

### POST `/wallet/deposit` — auth: required, **non-guest**
- Body: `{amount, method, trxId (required), receiptName (required), image? (data:image ≤8MB),
  accountId?}`.
- 200: `{success: true, reference, balance}` (transaction created PENDING; receipt re-encoded +
  uploaded to Cloudinary; duplicate-proof hashing flags, never rejects).
- Errors: 400 `Minimum deposit is Rs X` / `Maximum deposit amount is Rs X.` /
  "Transaction ID is required" / "Payment receipt is required" / image validation; 409 replay
  (idempotent); 429 "Too many deposit requests. Please try again in a little while."

### POST `/wallet/withdraw` — auth: required, **non-guest**
- Body: `{amount, method, accountNumber (required — must be a bound account), accountTitle?}`.
- 200: `{success: true, reference, balance}` (amount HELD from balance; PENDING until admin).
- Errors: 400 "Minimum withdrawal is Rs X" / "Insufficient balance" / "Please select a withdrawal
  account" / "This account is not bound to your profile. Please bind it first."; 409 "You already
  have a pending withdrawal request."

### POST `/wallet/transfer` — auth: required, **non-guest**
- Body: `{recipient (uid or email), amount, note?}`.
- 200: `{success: true, reference, receiver: <gameName string>, balance}`.
- Errors: 400 "Enter a valid recipient and amount" / "Maximum transfer amount is Rs X." /
  "Insufficient balance" / "You cannot transfer to yourself" / "Transfers to guest accounts are
  not allowed."; 404 "Recipient not found. Check UID or email."

---

## 3. Tournaments

### GET `/tournaments?status=UPCOMING|ONGOING|COMPLETED&gameId=<id>?` — auth: required
- 200: `{tournaments: (Tournament & {joined?: boolean})[]}` — the list endpoint computes
  `joined` for the caller. `Tournament` fields consumed: id, gameId, hostId, name, mode, map,
  perspective, entryFee, prizePool, perKill, loserPrize, maxPlayers, currentPlayers, status,
  rules, roomId, roomPassword, roomExpiresAt, resultsPublishedAt, bannerImage, startTime,
  disabled?, proofActive?, myProofSubmitted?, host?{gameName…}, game?{name,image,…}.
  Route returns more fields; joined-only room credentials are stripped for non-joined users.

### GET `/tournaments/:id` — auth: optional (guests OK for viewing)
- 200: `{tournament: Tournament & {entries?: TournamentEntry[], myEntry, joined, hasRoom,
  proofActive?, myProofSubmitted?, myProofImage?}}`.
  - `TournamentEntry`: `{id, tournamentId, userId, teamName, kills, rank, prize, status,
    createdAt, user?: {id, gameName, fullName, uid, avatar}}`.
  - Room credentials only when `joined && hasRoom` (server logs `room.credentials_viewed`).
- 404 "Tournament not found"; 500.

### POST `/tournaments` — auth: required (host creation)
- Body: `{gameId, name, mode, map, perspective, entryFee, prizePool, loserPrize, maxPlayers,
  rules, startTime: ISO, image? (data URL ≤5MB)}` (panel sends perKill implicitly 0; prize
  economics computed client-side per the fixed rule and validated server-side).
- 200: `{tournament}`. Errors: 400 "Missing required fields"/"Invalid game"/"This game is not
  available"/"Invalid match type for this game" (+ validation messages).

### POST `/tournaments/:id/join` — auth: required, **non-guest**
- Body: solo join = `{}` (web details screen sends no body). Team join (JoinTeamSheet contract)
  = `{members: [{uid}...]}`. Optional `slotNumber` for slot-based tournaments (SlotPickerSheet).
- 200: `{entry}` (server also notifies the host, schedules T-20/T-5/T-1 reminders).
- Errors: entry-fee/insufficient-balance ("insufficient" triggers the dialog), slots full, match
  started, already joined, team-member validation — all surfaced as error strings.

### GET `/tournaments/:id/results` — auth: required (participants, host, or ADMIN only)
- 200: `{tournament: Tournament & {entries (with user), myEntry}, stats: {totalKills, topKills,
  totalPrize, players}}`.
- 403 "Results are only available to tournament participants"; 404; also "not published" shape
  when incomplete (web treats failure/empty as the "Results not published yet." state).

### POST `/tournaments/:id/proof` — auth: required (joined player; admin-disabled tournaments)
- Body: `{image: data URL ≤8MB}` (one-time; replaces nothing — first submission wins).
- 200: created proof. 409 `{...ALREADY_SUBMITTED}` ("You have already submitted…"). 400 image
  validation. 404 tournament.

### POST `/tournaments/:id/result-proof` — auth: required (joined player; ≤ ~4MB)
- Same intent as `/proof` (ResultProofSheet contract; enforced only while the match is in
  progress and proof mode is enabled; re-upload REPLACES the previous screenshot).
- 200: `{proof}`. Errors: 400 "Please choose a screenshot image"/"Screenshot is too large (max
  ~4MB)"/"Result proofs are only available for host tournaments"/"Result proof submission is not
  enabled for this tournament"/"Result proofs can only be submitted while the match is in
  progress"; 403 "Only joined players can submit a result proof".

### GET `/my/tournaments` — auth: required
- 200: `{entries: (TournamentEntry & {tournament: Tournament (live status + proof fields)})[]}`.

### GET `/my/hosted` — auth: required
- 200: `{tournaments: Tournament[]}` (created by me; live status derived server-side).

---

## 4. Team

### GET `/team` — auth: required
- 200: `{team: Team|null, stats: {matches, wins, kills}}` —
  `Team`: `{id, name, tag, ownerId, createdAt, members: TeamMemberInfo[]}` with
  `TeamMemberInfo = {id, userId, role: "OWNER"|"MEMBER", user:{id,gameName,fullName,uid,avatar}}`.

### POST `/team` — auth: required, **non-guest**
- Body: `{name (required), tag?, memberUids?: string[]}` (invites resolved by UID server-side).
- 200: `{team: {..., invited}}`. Errors: 400 "Team name is required" / "You already belong to a
  team. Leave it first."

### DELETE `/team` — auth: required (any member leaves; owner disbands)
- 200: `{disbanded?: boolean, left?: boolean}`.

### GET `/team/join-requests` — auth: required
- Owner: `{requests: TeamJoinRequestRow[]}` (PENDING first; each `{id, status, createdAt,
  user:{id,gameName,fullName,uid,avatar}}`).
- Non-owner: `{requests: [], myRequest}`.

### POST `/team/join-requests` — auth: required, **non-guest**
- Body: `{teamId}`. 200: created request. Errors: 404 "Team not found"; 409 "You are already a
  member of this team" (+isMember) / team-full / duplicate-pending messages.

### POST `/team/join-requests/:id` — auth: required (team owner only)
- Body: `{action: "accept"|"decline"}`. 200: `{success: true, status: "ACCEPTED"|"DECLINED"}`.
- Errors: 400 "Invalid action"; 403 "Only the team owner can respond to join requests";
  404; 409 already-«status» / "This team is full (4/4 members)".

---

## 5. Friends & chat

### GET `/friends` — auth: required
- 200: `{friends: Friend[]}` — `Friend = {id, gameName, fullName, uid, avatar, friendshipId,
  lastMessage?, lastMessageAt?, unread?}` (isOnline is client-derived from lastMessageAt ≤30min).

### GET `/friends/requests` — auth: required
- 200: `{received: FriendRequestRow[], sent: FriendRequestRow[], receivedPending: number}`;
  row = `{id, status: PENDING|ACCEPTED|DECLINED, source: "UID"|"QR", createdAt,
  user:{id,gameName,fullName,uid,avatar}}` (user = the OTHER party; `via` label derives from source).

### POST `/friends/requests` — auth: required, **non-guest**
- Body: `{uid (6-digit player UID), source?: "UID"|"QR"}`.
- 200: `{request:{id,status}}` or `{request, autoAccepted: true}` (they had already sent you a
  pending request → auto-accept + friendship created + notification).
- Errors: 400 "Player UID is required" / "You cannot add yourself as a friend"; 404
  `No player found with UID «uid»`; 409 `{alreadyFriends}` "You are already friends with this
  player" / `{requestSent}` "Friend request already sent".

### POST `/friends/requests/:id` — auth: required (receiver for accept/decline, sender for cancel)
- Body: `{action: "accept"|"decline"|"cancel"}`. 200: `{success: true, status}`.
- Errors: 400/403/404/409 (already-«status», "Only the sender can cancel this request").

### GET `/friends/:id/messages` — auth: required
- 200: `{friend: {id, gameName, fullName, uid, avatar, isOnline?, lastSeen?}, messages:
  ChatMessage[]}` — `ChatMessage = {id, senderId, receiverId, text, createdAt, read}`.
  (Marks incoming as read; marks my messages delivered/read → drives done/done_all.)

### POST `/friends/:id/messages` — auth: required
- Body: `{text}` (server may auto-reply for bot/demo users — the 3s poll picks it up).
- 200: `{message: ChatMessage}`. 400 "Message is empty"; 404.

---

## 6. Notifications

### GET `/notifications` — auth: required
- 200: `{notifications: AppNotification[], unread: number}` —
  `AppNotification = {id, title, message, type, isRead, link?, createdAt}` (link =
  `tournament:<id>` | `host:<id>` | `wallet` | `myTournament` | `notifications` | `home` |
  `myStats` | `results`).

### GET `/notifications?countOnly=1` — auth: required
- 200: `{unread: number}` (the BellButton throttled poll; the push foreground handler too).

### POST `/notifications` — auth: required
- Mark ALL read. 200: `{success: true}`.

### PATCH `/notifications` — auth: required
- Body: `{ids: string[]}` — mark specific read. 200: `{success: true, unread}`.

### DELETE `/notifications` — auth: required
- Body: `{ids: string[]}` — delete only these. 200: `{success: true, unread}`.

---

## 7. Tasks / Referrals / Stats / Leaderboard / Achievements

### GET `/tasks` — auth: required
- 200: `{tasks: Task[]}` — `{id, title, description, icon (Material Symbol name), reward, type,
  claimed, completed}` (daily-reset tasks; `claimed` = claimed in the current period).

### POST `/tasks` — auth: required, **non-guest**
- Body: `{taskId}`. 200: `{success: true, reward}` (credits wallet + TASK_REWARD transaction +
  "Task Completed!" notification; idempotent per period). 409 already-claimed message; 404.

### GET `/referrals` — auth: required
- 200: `{referralCode, bonus (current per-referral amount), referrals: [{id, gameName, fullName,
  createdAt, bonusEarned}], totalEarned, count}`.

### GET `/stats` — auth: required
- 200: `{stats: {totalMatches, wins, kills, earnings, winRate, top3, leaderboardRank, kd}}`.

### GET `/leaderboard` — auth: optional
- 200: `{leaderboard: LeaderboardRow[]}` — `{rank, userId, gameName, fullName, uid, avatar,
  points, wins, kills, isMe?}` (isMe flags the caller's row).

### GET `/achievements` — auth: required
- 200: `{achievements: Achievement[], unlockedCount, total}` —
  `Achievement = {id, title, description, icon, category: TOURNAMENTS|KILLS|EARNINGS|SOCIAL,
  target, reward, progress, unlocked, unlockedAt}`.

---

## 8. Bank accounts

### GET `/bank` — auth: required
- 200: `{accounts: BankAccount[]}` — `{id, bankName, accountTitle, accountNumber, isPrimary}`.

### POST `/bank` — auth: required, **non-guest**
- Body: `{bankName, accountTitle, accountNumber}`. 200: `{account: BankAccount}`.
- 400 "All fields are required".

---

## 9. QR & player lookup

### GET `/qr/lookup?uid=<uid>` / `?teamId=<id>` — auth: required
- User: 200 `{kind:"user", user:{id,gameName,fullName,uid,avatar}, relation:
  "self"|"friend"|"request_sent"|"request_received"|"none"}`; 404 "Invalid QR code — no player
  found".
- Team: 200 `{kind:"team", team:{id,name,tag,memberCount,maxMembers:4,owner:{id,gameName,uid},
  members:[{id,gameName,uid,avatar}]}, isMember, joinRequested}`; 404 "Invalid QR code — no team
  found"; 400 "Missing uid or teamId".

### GET `/players/lookup?uid=<uid>` — auth: required
- 200: `{user: {id, gameName, fullName, uid, avatar}}` (JoinTeamSheet live teammate verification).
- 404 "UID is required" / "This user is not registered in the app. Please enter a valid UID."
  (+ guest variants).

---

## 10. Games config (host flow)

### GET `/games/config` — auth: optional
- 200: `{games: Game[]}` — active games incl. Admin-managed `modes:[{id,name,slots(FIXED),sub,
  icon,sortOrder,isActive}]`, `maps:[{id,name,sortOrder,isActive}]`,
  `perspectives:[{id,name,sortOrder,isActive}]` and `matchDurationMinutes`.

---

## 11. Push

### POST `/push/register` — auth: required
- Body: `{token (FCM), platform: "ANDROID"|"WINDOWS"|"WEB", deviceId: uuid}`. 200 `{success:true}`.
- 429 "Too many registration attempts"; 400 "Invalid push token".

### POST `/push/unregister` — auth: required
- Body: `{token}`. 200 `{success:true}` (logout + account switch).

### GET `/push/poll?after=<ISO>?` — auth: required (web/windows only; native uses FCM)
- 200: `{unread, latest: [{id,title,message,link,createdAt}], now}`.

---

## 12. Admin (user-panel mobile admin; server RBAC enforced)

| Endpoint | Method | Min role | Request | Response |
|---|---|---|---|---|
| `/admin/requests` | GET | FINANCE_ADMIN | — | `{requests: PaymentRequest[]}` (id, type, amount, method, reference, note, createdAt, reviewedById, approvedById, user{fullName,gameName,uid,email,phone,balance}) |
| `/admin/requests/:id` | GET | FINANCE_ADMIN | — | `{id, image (data URL|null)}` (deposit receipt) |
| `/admin/requests/:id` | POST | FINANCE_ADMIN (four-eyes per server) | `{action:"approve"|"reject"}` | `{success:true}` |
| `/admin/tournaments` | GET | MODERATOR | — | `{tournaments: AdminTournament[]}` (full Tournament + entries[] with user + disabled + resultsPublishedAt) |
| `/admin/tournaments/:id/status` | POST | MODERATOR | `{status:"UPCOMING"|"ONGOING"|"CANCELLED"}` | ok (COMPLETED 400s: "Use Publish Results to complete a tournament") |
| `/admin/tournaments/:id/room` | POST | MODERATOR | `{roomId, roomPassword, roomExpiresAt: ISO|null}` | ok ("Room ID and Password are required" 400) |
| `/admin/tournaments/:id/disable` | POST | MODERATOR | `{disabled: boolean}` | ok |
| `/admin/tournaments/:id/results` | POST | MODERATOR | `{entries:[{entryId, rank, kills, prize}]}` | ok (409 "Results have already been published.") |
| `/admin/proofs` | GET | MODERATOR | — | `{proofs: AdminProof[]}` (id, createdAt, user{gameName,fullName,uid}, tournament{id,name,mode,status,game?}) |
| `/admin/proofs/:id` | GET | MODERATOR | — | `{id, image (data URL|null)}` |
| `/admin/settings` | GET | any panel role (keys filtered server-side) | — | `{settings: Record<string,string>}` incl. fourEyesEnabled |
| `/admin/settings` | POST | per-key authority | `{settings: Record<string,string>}` | `{success:true, updated: string[]}` |
| `/admin/security?type=events|audit&take=50` | GET | SUPER_ADMIN | — | `{ok, items}` (events: id, createdAt, userId, endpoint, action, decision, reason, riskScore, ip; audit: id, createdAt, actorId, actorRole, action, targetType, targetId) |
| `/admin/security?type=reconcile` | GET | SUPER_ADMIN | — | `{ok, items: [{userId, gameName, ledgerSum, balance, drift}], checkedCount}` |
| `/admin/games` | GET/POST | SUPER_ADMIN | POST `{name, image}` | `{games}` / created |
| `/admin/games/:id` | POST/DELETE | SUPER_ADMIN | POST `{isActive?}`, `{matchDurationMinutes?}` | ok |
| `/admin/games/:id/config` | POST | SUPER_ADMIN | `{kind:"mode"|"map"|"perspective", op:"add"|"delete", name?, slots?, sub?, id?}` | ok |

Errors: 401 "Unauthorized"; 403 "Admin access required" (role gate); 404/409 per table;
429 on mutation rate limits (`Retry-After` header).

---

## 13. Error-status conventions summary

- `400` validation (message in `error`), `401` session invalid → force logout, `403` role/guest
  gate, `404` not found, `409` duplicates/pending conflicts (and client-side in-flight guard),
  `429` rate limit, `500` generic ("Failed to …", "Something went wrong" client fallback).
- Guests hitting non-guest endpoints receive the guest-gate message (client pre-blocks with the
  Account Required dialog; server double-enforces with `requireNonGuest`).
