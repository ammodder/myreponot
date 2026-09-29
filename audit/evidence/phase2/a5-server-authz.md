# A5 evidence — server-side authorization map (every route the native app can reach)

Agent: A5 (TASK-006). Read-only. Server tree: /home/z/my-project/src/app/api/**. Helpers:
/home/z/my-project/src/lib/auth-server.ts, roles.ts, rate-limit.ts. Commands: grep for
getSessionUser/requireRoleResponse/requireNonGuest/hasRole/isSuperAdmin + targeted file reads.
Client side: /home/z/audit-wt/AreenaxNativeAndroid/.../core/network/Api.kt (71 call-sites),
data/Models.kt Roles, ui gates.

## 0. Session + role primitives

auth-server.ts:63-89 getSessionUser: reads `x-token` header (:64), db.session.findUnique({token})
(:66-69), rejects inactive users (:70) and expired sessions (destroyed on sight, :72-75), sliding
renewal to 30d (:77-87). Token = crypto.randomBytes(32).hex (:25-27).
auth-server.ts:96-110 requireRoleResponse: no user → 401 "Authentication required."; role below
minimum → 403 FORBIDDEN. auth-server.ts:123-126 requireNonGuest: null-or-guest → 403
GUEST_BLOCKED ("An account is required for this action…", :116-121).
roles.ts:18-24 rank USER(0) < MODERATOR(1) < FINANCE_ADMIN(2) < SUPER_ADMIN/ADMIN(3);
isAdminRole = >= MODERATOR (:44-46); "never trust client-supplied roles" (:6).

## 1. Gate map — grep transcript (all route.ts files; verbatim)

Command: for f in $(find . -name route.ts|sort); do grep -n "getSessionUser\|requireRoleResponse\|requireNonGuest\|isSuperAdmin\|hasRole" $f; done

achievements/route.ts                 6 getSessionUser → 7 !user 401
admin/games/route.ts                  GET :10-12 / POST :33-35 getSessionUser + isSuperAdmin else 403
admin/games/[id]/route.ts             POST :9-11 / DELETE :89-91 getSessionUser + isSuperAdmin else 403
admin/games/[id]/config/route.ts      :24-26 getSessionUser + isSuperAdmin else 403
admin/proofs/route.ts                 :14-15 getSessionUser + requireRoleResponse(MODERATOR)
admin/proofs/[id]/route.ts            :20-21 getSessionUser + requireRoleResponse(MODERATOR)
admin/requests/route.ts               :16-17 getSessionUser + requireRoleResponse(FINANCE_ADMIN)
admin/requests/[id]/route.ts          GET :61-62 / POST :103-104 requireRoleResponse(FINANCE_ADMIN); extra 403 :202
admin/security/route.ts               :21-22 getSessionUser + requireRoleResponse(SUPER_ADMIN)
admin/settings/route.ts               GET :66-67 / POST :91-92 requireRoleResponse(MODERATOR); per-field hasRole :140
admin/teams/route.ts                  :26-27 requireRoleResponse(MODERATOR)
admin/teams/[id]/route.ts             :16-17 requireRoleResponse(SUPER_ADMIN)
admin/tournaments/route.ts            GET :29-30 SUPER_ADMIN; POST :173-174 MODERATOR
admin/tournaments/[id]/route.ts       :18-19 SUPER_ADMIN (DELETE)
admin/tournaments/[id]/disable/route.ts   :14-15 MODERATOR
admin/tournaments/[id]/results/route.ts   :27-28 MODERATOR
admin/tournaments/[id]/room/route.ts      :12-13 MODERATOR
admin/tournaments/[id]/status/route.ts    :23-24 MODERATOR; prize ops escalate FINANCE_ADMIN :54
admin/tournaments/[id]/match-progress/route.ts  :10-12 getSessionUser + `user.role !== "ADMIN"` (legacy literal — NOT called by native)
admin/transactions/route.ts           :29-30 FINANCE_ADMIN (not in Api.kt)
admin/users/route.ts                  :31-32 MODERATOR (not in Api.kt)
admin/users/[id]/route.ts             :17-18 SUPER_ADMIN; cannot modify super admin :72
admin/users/[id]/balance/route.ts     :20-21 FINANCE_ADMIN (not in Api.kt)
admin/users/[id]/role/route.ts        :20-21 SUPER_ADMIN (not in Api.kt)
auth/check/route.ts                   public + IP rate limit (rate-limit import :3)
auth/guest/route.ts                   public + rate limit (:10)
auth/login/route.ts                   public; 401 on bad credentials :76/:111; 403 suspended :79/:114
auth/logout/route.ts                  deletes token by x-token header (documented :7)
auth/register/route.ts                public + rate limit (:11)
bank/route.ts                         GET :6-7 401; POST :17-19 401 + requireNonGuest
bootstrap/route.ts                    public (games/banners/settings read-only)
friends/route.ts                      GET :6-7 401
friends/requests/route.ts             GET :56-58 401 + requireNonGuest; POST :31-32 401 (send target checked in body)
friends/requests/[id]/route.ts        :12-14 401 + requireNonGuest; sender-only cancel 403 :39; receiver-only respond 403 :46
friends/[id]/messages/route.ts        GET :20-21 401; POST :52-55 401 + requireNonGuest — NO friendship check (see §3a)
games/config/route.ts                 public (active games + modes/maps)
leaderboard/route.ts                  :7 getSessionUser (tolerated-null in try block)
me/route.ts                           GET :11-12 401; PATCH :17-19 401 + requireNonGuest
my/hosted/route.ts                    :8-9 401
my/tournaments/route.ts               :9-10 401; query where userId: user.id (:11-12)
notifications/route.ts                GET :10-11 401 (countOnly :14-16); POST :30-31; PATCH :40-41; DELETE :61-62 — ALL scoped user.id (see §3c)
players/lookup/route.ts               :15-16 401; no rate limit (see §3d)
push/poll/route.ts                    :13-14 401; where userId: user.id (:24-26)
push/register/route.ts               :17-18 401; rate limit :20; upsert keyed by token, userId: user.id (:46-58)
push/unregister/route.ts              :12-13 401; `where: { token, userId: user.id }` :25 — own tokens only
qr/lookup/route.ts                    :14-15 401; guests allowed by design (doc :10-11); no rate limit (see §3d)
referrals/route.ts                    :6-7 401
stats/route.ts                        :6-7 401
tasks/route.ts                        GET :36-37 401; POST :68-70 401 + requireNonGuest + rate limit :75
team/route.ts                         GET :7-8 401; POST :43-45 401 + requireNonGuest; DELETE :93-94 401
team/join-requests/route.ts           GET :9-10 401; POST :35-38 401 + requireNonGuest
team/join-requests/[id]/route.ts      :10-12 401 + requireNonGuest; owner-only 403 :31-32
tournaments/route.ts                  GET public (hosted=1 requires session :21-23); POST :78-80 401 + requireNonGuest
tournaments/[id]/route.ts             GET public (session optional)
tournaments/[id]/join/route.ts        :25-27 401 + requireNonGuest
tournaments/[id]/proof/route.ts       :25-28 401 + requireNonGuest; joined-only 403 :41-46; one-time 409 :68-73; rate limit 5/h :77-87
tournaments/[id]/result-proof/route.ts :14-15 401; joined-only 403 :61
tournaments/[id]/results/route.ts     :8 session optional; participant/host/admin-only 403 :28-42
wallet/route.ts                       :6-7 401; where userId: user.id (:9)
wallet/deposit/route.ts               :17-19 401 + requireNonGuest (+ rate limit)
wallet/transfer/route.ts              :14-16 401 + requireNonGuest (+ rate limit :21)
wallet/withdraw/route.ts              :18-20 401 + requireNonGuest (+ rate limit)
cron/dispatch/route.ts                :23 403 (secret header — not in Api.kt)
setup/super-admin/route.ts            :74 403 guard (not in Api.kt)
route.ts (root)                       public `{ message: "Hello, world!" }` (not in Api.kt)

## 2. Client-side gates (native UI)

- Auth gate (login screen for anonymous): AppNavigator.kt:108-114 enforceAuthGate, run on user
  change (AppShell.kt:116-118); PUBLIC_SCREENS = login/signup×3/terms/privacy (ScreenKeys.kt:91-93).
- Guest gate dialog: core/ui/AccountRequiredDialog.kt:56-77 GuestGateState.requireAccount;
  wired at WalletScreen.kt:298/310/322 (deposit/withdraw/transfer), DepositConfirmScreen.kt:475,
  ConfirmWithdrawScreen.kt:295, TournamentDetailsScreen.kt:381 (join), HostTournamentScreen.kt:115,
  HostCreationScreen.kt:651, TeamCreationScreen.kt:107 ("create a team"), EditProfileScreen.kt:164/237,
  BindAccountScreen.kt:397, ProfileScreen.kt:256. MyTournamentScreen.kt:94/101-104 skips the fetch
  for guests so the poll can't trip a 401.
- Admin gate: ProfileScreen.kt:241 (menu item visible only for Roles.isAdminPanelRole), in-screen
  deny UI AdminPanelScreen.kt:179 + AdminTournamentsScreen.kt:244. Roles = data/Models.kt:32-36
  (ADMIN_PANEL_ROLES = MODERATOR/FINANCE_ADMIN/SUPER_ADMIN/ADMIN — mirrors web roles.ts:27-32).
- Server remains the enforcement layer by design ("client gate; server enforces RBAC",
  AdminPanelScreen.kt:108).

## 3a. friends/[id]/messages — session-pair-scoped but NOT friendship-scoped

GET (route.ts:19-48): messages `where OR [{senderId:user.id, receiverId:id},
{senderId:id, receiverId:user.id}]` (:36-45) — user A can NEVER read B↔C traffic. But the target
`id` is arbitrary: friend lookup (:24-28) returns {id, gameName, fullName, uid, avatar} for ANY
existing user, no friendship check. POST (:51-79): any non-guest session can message ANY user id
(:62-67), no friendship requirement; demo bot reply :69-77.
Native UI only opens chat from the friends list (FriendsScreen → ChatScreen friendId param).

## 3b. notifications PATCH/DELETE with client-supplied ids — properly scoped

route.ts PATCH :51-54 `updateMany({ where: { userId: user.id, id: { in: ids } } })`;
DELETE :72-74 `deleteMany({ where: { userId: user.id, id: { in: ids } } })` — foreign ids are
silently no-ops, never another user's rows.

## 3c. tournaments/:id/proof + result-proof + results — ownership verified

proof/route.ts: joined-player check :38-46 (`tournamentId_userId {tournamentId:id, userId:user.id}`),
one-time 409 :68-73 (incl. P2002 race :112-120), upload re-encode + rate limit :61-87, private
Cloudinary asset :89-105. result-proof: joined-only 403 :61. results: viewer must be participant,
host, or admin (:28-42), 403 otherwise.

## 3d. qr/lookup + players/lookup — public-ish profile fields by design, unthrottled

qr/lookup (uid): returns {id, gameName, fullName, uid, avatar, isGuest} for ANY 6-digit uid
(:22-27); relation computed against session user (:29-52). teamId branch returns team + member
summaries (:61-99). players/lookup: same select + isActive/isGuest filter (:23-33).
Both require a session (:14-16 / :15-16) but NEITHER has checkRateLimit (grep rate-limit over
app/api: 23 files, not these two) → scripted enumeration of the 900k uid space is unthrottled.

## 3e. wallet/transfer — recipient validation server-side

route.ts: recipient = uid OR email (:74-78), self-transfer blocked :80-82, atomic guard
`updateMany where {id:user.id, balance:{gte:amt}}` decrement :90-96, credit receiver.id :96.
Client never proposes amounts already applied server-side; balance re-read :131-134.

## 4. Client call-sites with NO server-side guest block on GET (read paths)

friends GET, notifications GET, wallet GET, my/* GET, stats/referrals/leaderboard/achievements GET,
chatMessages GET, team GET, bank GET — all 401-gated, guest-allowed (matches web; guests are
logged-in sessions with isGuest=true, so no privilege escalation — guest = restricted WRITE set).
