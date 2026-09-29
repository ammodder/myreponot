# ATTACKER RE-TEST — TASK-007 Phase 3 fixes (12-e)

Agent: attacker (read-only). Scope: try to PROVE the Phase-3 fixes BROKEN, incomplete, or
behavior-changing beyond owner authorization. No application code was modified by this task.
Method: every FIXES.md claim re-derived from code (file:line), then attacked; release dex
re-probed independently; server routes re-read for bypass paths; web workstream re-diffed.
Artifacts probed: worktree @ dcba8d0 (+ uncommitted files noted below), release APK
`app/build/outputs/apk/release/app-release-unsigned.apk` (2,698,256 B), web repo @ 8e72e79.

Classifications: **BROKEN** (real regression, must fix) · **HELD** (fix survives) · **N/A**.

---

## 1. A6-01/A7-01 — compile-time icon map — **HELD**

- Map completeness: 157/157 — mechanical diff of `res/drawable/ic_*.xml` (157 files) vs the
  `iconIds` keys (`core/ui/AreenaxIcon.kt:54-100`) = 0 missing, 0 extra (`comm` of sorted lists).
- Independent dex re-probe (release APK `classes.dex`, `strings` scan): all **157** `ic_*` map-key
  strings present; the ONLY reflection-shaped strings are zxing's own
  `Lcom/google/zxing/client/android/R$drawable;` + generic `getField` — no
  `com/areenax/nativeapp/R$drawable`, no app getField. (FIXES.md's "144 keys" understates its own
  evidence file, which says `checked=157 missing=0` — record nit only.)
- Alias/fill attack: `drawableNameFor` (:23-44) maps the 6 aliases to drawables that exist
  (warning→error :27, tournament→tune :28, qr_code_scanner→qr_code_2 :29, quiz→assignment :30,
  chevron-right :31); the 20-entry fill-reserved set (:38-42) matches 1:1 with the 20 shipped
  `_fill` drawables; `BottomNav.kt:82` filled tabs (emoji_events, group, home,
  account_balance_wallet, person) all resolve. Server-data names outside the 157
  (e.g. achievement seeds `workspace_premium`, `sports_martial_arts`, `crosshairs`,
  `waving_hand` in prisma seed) still fall back to `ic_help` (:105) — identical to the pre-fix
  reflection behavior and documented by design (ICON-AUDIT §8).

## 2. Q15 — DELETE /api/me — **HELD** (with notes)

- Gates verified in order at `src/app/api/me/route.ts:138` (balance>0), :145-153 (hosted
  tournaments not COMPLETED/CANCELLED), :155-169 (owned team with OTHER members). No bypass
  found: statuses are a closed enum (schema.prisma:143), team gate counts members ≠ self
  (:160-161), and self-only teams/finished+cancelled hosts are pre-deleted inside the tx
  (:187-188) before `user.delete` (:190), satisfying the two Restrict FKs
  (`Tournament.host` schema.prisma:160, `Team.owner` :240 — both have NO onDelete).
- Cascade sweep: every other User relation in schema.prisma (sessions, entries, transactions,
  proofs, notifications, userAchv/userTasks, bankAccounts, memberships, messages both sides,
  friendships both sides, requests both sides, joinRequests, pushTokens, reminders) carries
  `onDelete: Cascade`; `referredById` is pre-nulled (:179-182). No orphan/block path found.
  Guest self-clean allowed by design (no requireNonGuest on DELETE).
- Native 400 handling: `ProfileScreen.kt:455-470` + `ApiResult.kt:35-48 extractErrorMessage`
  surface the frozen refusal strings verbatim in a destructive toast; double-submit blocked by
  the `deleting` flag (:451, :453) and dialog-dismiss lock (:430).
- Web `/delete-account` page: signed-in/anonymous split derives from the persisted token
  (`page.tsx:33`); on error it re-enters confirm with the server message (:50-53); success wipes
  persisted storage + logout (:44-48). No auth bypass; anonymous users only get the login CTA.
- NOTE 1 (TOCTOU): the three gates run OUTSIDE `$transaction` — a concurrent credit (friend
  transfer) landing between gate and delete could destroy a funded account. Requires precise
  timing and a second party; recommend re-checking balance inside the tx (cheap hardening).
- NOTE 2: `AuditLog`/`SecurityEvent` keep actorId as plain strings (schema.prisma:465, :488) —
  they survive as historical rows pointing at a deleted id (append-only by design, no FK orphan).

## 3. Q2/Q3 — refuse-at-pickup + 8 MB parity — **HELD** (no parity gap)

- All four native pick paths route through the single refuse hook: DepositConfirmScreen.kt:150
  (RECEIPT 8 MB), ResultProofSheet.kt:77 + TournamentDetailsScreen.kt:119/:184 (RESULT_PROOF
  8 MB — local const duplicates the object, value consistent), HostCreationScreen.kt:136
  (HOST_IMAGE 5 MB). Refusal at `PhotoPickers.kt:98-104` fires before any read into state;
  the silent-downscale path is gone (repo-wide rg for `compressToCap/createScaledBitmap`: 0).
  No avatar picker exists on native or web (`UploadCaps.AVATAR` is an unused constant).
- Server caps are DECODED-BYTE caps per kind (`src/lib/upload-security.ts:21-26`: proof/receipt/
  avatar 8 MB, banner 5 MB), applied after base64 decode — so the 4/3 base64 inflation is
  irrelevant to enforcement: an 8,388,607-byte pick (≈11.19 M base64 chars) passes the client at
  8,388,607 ≤ 8,388,608 bytes and decodes to the same byte count server-side. **An 8 MB pick can
  NOT be rejected server-side after passing the client.** The "5.5 M chars" cap does not exist
  anywhere in the live routes (rg `_000_000|too large` over api/**): result-proof/route.ts:31,
  proof:61, deposit:94, me:41 all use `validateAndReencodeImage`. `me/route.ts:9,57` MAX_IMAGE_CHARS
  = 11 M gates ONLY the http(s)-URL avatar branch, which no client sends as a pick.

## 4. Q12 — friends/suggest privacy — **HELD** (with notes)

- Contacts enumeration attack: `mode=contacts` accepts ANY phone (not just real contacts) and
  returns profile fields for digit-matches (`suggest/route.ts:68-96`). That is the owner-approved
  feature (Q12); it requires a session + non-guest (:51-54) and is rate-limited 30/min **per
  user** (:56, `RATE_LIMITS.lookup` rate-limit.ts:85). `identityKey` pins the bucket to `user.id`
  (rate-limit.ts:60-62), so X-Forwarded-For spoofing can NOT mint new buckets; multi-account
  parallelism remains the inherent bound — acceptable, noted.
- Nearby precision: `POST /api/me/location` truncates to 2 decimals BEFORE storage
  (`me/location/route.ts:40-42`, ≈1.1 km cell), never returns coordinates, only profile fields,
  max 20, only when the CALLER's own fix is < 7 days old (suggest:107-135). Policy-acceptable.
- Prominent disclosure: two-step in `FriendsScreen.kt:749-783` — first tap shows the disclosure
  text ("uses your approximate location only… never in the background"), only the second tap
  launches `ACCESS_COARSE_LOCATION`. Contacts uses the system PickContact (no READ_CONTACTS,
  FriendsScreen.kt:9,109).
- Demo data: `rg SUGGESTED|demo|DEMO` over FriendsScreen.kt → only comments stating removal
  (:107, :145); suggestion lists are server-fed (`nearbyUsers`/contacts results only).

## 5. A3-02 — nav persistence — **HELD** (with note)

- Restore loop: `restoreFrom` guards `restoring` and never persists
  (AppNavigator.kt:59, :73-76, :81-94); logout clears KEY_NAV in the same atomic `store.edit`
  as the token (`SessionManager.kt:233-240`), so a tokenless-nav state cannot arise from the
  app itself (DataStore edits are atomic) — only via rooted-file tampering.
- Tampered-DataStore param injection: params are strings consumed exactly like normal navigation
  (ids into Retrofit `@Path` with default encoding; `qrUser`/`qrTeam` only open existing popups);
  no local privilege sink found.
- NOTE: the auth gate re-runs keyed on user EMISSION only (`AppShell.kt:121-123
  LaunchedEffect(user) → enforceAuthGate`). If a tampered/persisted auth-screen snapshot lands
  AFTER first composition while logged out, no emission change re-fires the gate until the
  screen's own API calls 401 → auto-logout (AppShell.kt:79-82 wiring). Transient, no data
  exposure (all requests fail unauthorized) — suggest keying the gate on
  `navigator.generation` too.

## 6. A3-04/A5-06 — offline "blocking" overlay — **BROKEN (minor, 2 defects)**

- Defect A — touch passthrough: the overlay root has NO pointer-blocking modifier — AppShell
  renders `Box(Modifier.fillMaxSize().zIndex(2f)) { OfflineScreen(asOverlay = true) }`
  (AppShell.kt:174-182) and OfflineScreen's root is `Column(fillMaxSize().background(...))`
  (OfflineScreen.kt:80-84); `background` is draw-only. Compose hit-testing skips non-input
  nodes, so taps in any blank region fall THROUGH to the covered screen's buttons/fields —
  the covered screen is still interactive in those areas, which is exactly the pre-fix
  behavior A3-04 was supposed to remove (pre-fix it was a route push = fully replaced).
- Defect B — toasts render UNDER the overlay: the overlay's `zIndex(2f)` outranks ToastHost's
  default 0 despite declaration order (AppShell.kt:185), contradicting the file's own KDoc
  "ToastHost on top (web ui/toaster, z-100)" (AppShell.kt:66). Reachable in two taps: offline →
  "Try again" → OfflineScreen shows the "Still offline" destructive toast
  (OfflineScreen.kt:148-152) → it is drawn beneath the opaque overlay and is invisible.
  The covered screen's failed-request feedback is likewise swallowed while offline.
- BackHandler half of the fix is correct: `PredictiveBackHandler(enabled = … && isOnline)`
  (AppNavHost.kt:69-70). Form-state preservation goal (stays composed) is achieved.
  Fix cost is small: add `pointerInput(Unit){}`-style consumption + give ToastHost `zIndex(3f)`
  (or overlay zIndex < ToastHost).

## 7. A8-01 — PredictiveBackHandler — **HELD**

- The `try { progress.collect {}; goBack() } catch (CancellationException) {}` shape
  (AppNavHost.kt:69-79) IS the androidx contract (cancellation = gesture cancel); no other
  exception is swallowed, no suspension between collect-end and goBack, so no lost-navigation
  or swallowed-external-cancellation hazard. `enabled=false` mid-gesture (connectivity drop)
  unregisters the callback and cancels the block → caught → no-op; no lifecycle crash path.
- Residual: `goBack()` pops whatever is current if the screen changed DURING the gesture —
  inherent to the API, sub-300 ms window, noted not fixed.

## 8. A13-02 — CrashReporter privacy — **HELD** (with note)

- File content = timestamp + thread name + throwable stack only (CrashReporter.kt:49-57); no
  token/user code path writes into it; the crash dir is backup/transfer-excluded
  (backup_rules.xml + data_extraction_rules.xml, both `crash/` excludes present).
- NOTE: a serializer failure's exception MESSAGE can embed the raw JSON body it choked on —
  if such an exception ever crashed uncaught, `last_crash.txt` could contain response PII.
  Not deterministic, not demonstrated; consider sanitizing messages if hardening further.
- Recursion: `write` is wrapped in `runCatching` and the ORIGINAL platform handler still runs
  (:34-37) — a crash inside the handler cannot loop.

## 9. A8-02 — PressScaleIndication — **HELD** (with note)

- `pressedCount` is floor-coerced (PressScaleIndication.kt:53) so missed Releases can never go
  negative; the collector lives in the node's own `coroutineScope` (:46-58), cancelled on detach.
- NOTE (theoretical): if the SAME node instance detaches between Press and Release and re-attaches
  (node reuse across attach cycles), the stale count ≥ 1 would pin the 0.95 scale until the next
  press/release pair. Current call sites recreate nodes per composition entry, so no reachable
  site reproduces it; flagged for when runtime QA becomes possible (runtime rendering remains
  NOT VERIFIED — no device).

## 10. Server hardening (A4-01/05, A4-08/17, A5-04/05) — **HELD**

- A4-01: `validatePassword` imported from `@/lib/password-policy` and enforced before hashing
  (`me/route.ts:6, :75-78`); password change still requires currentPassword (:66-71).
- A4-05: native admin room gate requires BOTH fields with the new toast
  (AdminTournamentsScreen.kt:152-157) and a `saving` re-entry guard (:144).
- A4-08: re-encode applied on all four image mutations — result-proof :31, proof :61, deposit
  receipt :94 (+ dHash :100), avatar :41 — client prefix/size never trusted.
- A4-17: `requireNonGuest` present in 18 route files; spot-verified on result-proof:19,
  suggest:53, location:21, deposit/withdraw/transfer (2 hits each), join (2).
- A5-04: friendship gate on BOTH message handlers (GET `friends/[id]/messages/route.ts:28-37`,
  POST :75-84) → 404 for strangers; stranger-profile oracle closed.
- A5-05: `checkRateLimit` on qr/lookup (:20) and players/lookup (:23), 30/min per-user; players
  select dropped `fullName` (:38). Bypass attempt: identityKey is user-pinned (no header bypass);
  XFF spoofing only affects unauthenticated scopes. qr/lookup still exposes fullName (:36) —
  by design (UID-gated exact lookup, rate-limited), matching FIXES.md wording exactly.

## 11. Q5 features — **HELD**

- Join-request inbox: one-in-flight guard `joinBusyId` (MyTeamScreen.kt:154-157, :376 release);
  accept/reject refresh team + requests after each action (:377-378) — no desync path found.
- Team-QR popup: 404/unknown → friendly toast + popup dismissed via `teamClosedUid`
  (MyTeamScreen.kt:196-219, both Success-null and Error/NetworkError branches); join action
  double-tap guarded (`teamActionBusy`, :222-224) + guest gate (:225).
- Notifications select/delete: `deleting` + empty-selection guards (NotificationsScreen.kt:138);
  after delete the server's fresh `unread` is applied AND `refresh(force=true)` (:148-149) —
  no unread desync; select-all/exit-reset clean (:127-133).

## 12. Web workstream (agent 12-c) — **HELD** (with notes) + 1 repo finding

- EditProfileScreen.tsx: Save-Changes body carries only fullName/gameName/email (:106-110);
  Change-Password card still submits `{currentPassword, password}` to PATCH /me (:133-136) and
  works against the now-policy-enforcing server. Native twin identical
  (EditProfileScreen.kt:173-181 + PatchProfileRequest without password).
- DepositScreen.tsx:15 = "Alfalah Bank" (Q13); native live strings clean — the 2 remaining
  "Alphla" hits are fix-documentation comments only (TournamentState.kt:174, BindAccountScreen.kt:495).
- Bootstrap additive fields: `maxDeposit/maxTransfer` default 100000/50000, `version` default
  "1.0.0" (`bootstrap/route.ts:55-62`); web `AppSettings` type untouched (extra keys ignored,
  no consumer breaks — rg maxDeposit over src/components: 0); native DTO defaults 0.0 are
  guarded at both consumers (`DepositScreen.kt:131`, `TransferScreen.kt:124` use `if (s.x > 0)`).
- NOTE (pre-existing): client-side new-password checks still demand 6 chars (web
  EditProfileScreen.tsx:87, native EditProfileScreen.kt:242) vs server policy 10 — server
  rejects with its message surfaced; cosmetic-only mismatch.
- NOTE: stale KDoc EditProfileScreen.kt:74 still lists `password?` in the Save body.

## 13. EXTRA (found while attacking) — **BROKEN (repo integrity)**

- **A11-02/A10-01 toolchain half is UNCOMMITTED.** At HEAD dcba8d0,
  `gradle/libs.versions.toml` still pins `agp = "8.7.3"` and `gradle-wrapper.properties` still
  pins Gradle 8.10.2 — the 8.9.1/8.11.1 bumps exist ONLY as uncommitted working-tree
  modifications (git diff; none of fea1496…dcba8d0 touched those files, while
  app/build.gradle.kts SDK 36 IS committed). A fresh clone/checkout of the audit branch
  reproduces the exact state FIXES.md calls build-breaking ("compileSdk/targetSdk 36 requires
  AGP 8.9.1+"). FIXES.md "APPLIED" is true of the disk, false of the repo history.
  Fix = commit the two files (and the dangling untracked evidence file
  `audit/evidence/phase2/a6-01-release-dex-iconmap-proof.txt`).

---

## Verdict

| # | Surface | Result |
|---|---|---|
| 1 | A6-01/A7-01 icon map | HELD |
| 2 | Q15 DELETE /api/me | HELD (+TOCTOU note) |
| 3 | Q2/Q3 8 MB parity | HELD (no gap) |
| 4 | Q12 suggest privacy | HELD (+notes) |
| 5 | A3-02 nav persistence | HELD (+gate note) |
| 6 | A3-04/A5-06 offline overlay | **BROKEN** (touch passthrough + toasts under zIndex 2) |
| 7 | A8-01 predictive back | HELD |
| 8 | A13-02 CrashReporter | HELD (+PII note) |
| 9 | A8-02 press indication | HELD (+re-attach note) |
| 10 | Server hardening | HELD |
| 11 | Q5 features | HELD |
| 12 | Web workstream | HELD |
| 13 | A11-02 commit state | **BROKEN** (fix uncommitted at HEAD) |

Both BROKEN items are cheap to fix (overlay: add pointer-consumption + fix ToastHost zIndex;
repo: commit 2 gradle files + 1 evidence file) and neither undermines the money-path or
privacy claims tested above; core blockers (icon map, deletion gates, upload parity,
rate limiting, authz gates) all survived direct attack.

ATTACKER VERDICT: FAIL (2 broken)
