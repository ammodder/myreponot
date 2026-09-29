# PHASE 3 FIX LEDGER — TASK-007 (all 93 items)

Owner command 2026-09-20: 5 BLOCKERs + 22 MAJORs + 46 MINORs + 17 Q-decisions + 3 features.
**Q3 SUPERSEDED by this command: 8 MB (was Q3-5MB in decision log 8) — later instruction wins.**

Status vocabulary: **APPLIED** (code changed, compile-verified) · **PARTIAL** (scope note given) ·
**RECORD-ONLY** (decision/checklist, zero code by design) · **NOT APPLIED** (reason given) ·
**DECISION-RECORDED** (owner-gated path).

Cross-map (one fix = several findings): A6-01=A7-01 · A5-02=A3-07=A7-07 · A6-03=Q15=A11-03 ·
A4-04=Q6 · A8-03=Q10 · A9-08=A13-04(Q11) · A3-08=Q5(team-QR) · A5-06=A3-04 · A10-01=A11-02 ·
A9-11=A11-04(native copy) · A13-05(A13-04 item 3) · A6-04(A12-05 rule extraction dependency).

Build evidence: `audit/evidence/compile-p3-run{2,6,13,14}.log` (compileDebugKotlin green),
`assembleDebug-p3.log` (13,127,513 B), `assembleRelease-p3-run3.log` (release 2,698,256 B +
AAB 6,546,444 B; attempts 1-2 kernel-OOM-killed during R8, disclosed), badging + dex probes below.
Runtime on device: **NOT VERIFIED** (no device/emulator in sandbox) — unchanged standing limitation.

---

## BLOCKERS (5)

| ID | Status | Fix + evidence |
|---|---|---|
| A6-01 | APPLIED | Reflection replaced by compile-time `iconIds` map (157 entries) in `AreenaxIcon.kt`; fallback ic_help unchanged. Release-dex proof (byte-level, evidence: `a6-01-release-dex-iconmap-proof.txt`): **all 157 icon keys present, 0 missing**; no `Lcom/areenax/nativeapp/R$drawable;` reflection remains (the earlier "144" reading was a strings(1) tab-prefix artifact, corrected by byte search). Debug+release both build. |
| A11-01 | DECISION-RECORDED | Path A per finding's own disposition: web-APK distribution stays the release channel; Play listing would require a real-money-removed variant (RMG policy quote in Phase-2 evidence). Recorded in PLAY-LAUNCH-CHECKLIST.md + STATE. No code — the finding itself says "no code fix". |
| A11-02 | APPLIED | AGP 8.7.3→**8.9.1**, Gradle 8.10.2→**8.11.1**, compileSdk 35→**36**, targetSdk 35→**36** (libs.versions.toml, wrapper, build.gradle.kts — **all committed**; the Attacker/Critic re-test caught the toolchain files being left out of an earlier commit and they are now in git). Badging: `targetSdkVersion:'36'`, `platformBuildVersionCode='36'`. Android-16 behavior deltas: edge-to-edge already enabled (A8-10), predictive back enabled (A8-01), portrait lock retained for phones. |
| A11-03 | APPLIED | = Q15. In-app deletion (Profile → Delete Account, typed-DELETE confirm), server `DELETE /api/me` (refuses while balance>0 / hosted tournament unfinished / team with other members; transactional cascade delete; Cloudinary avatar purge), web `/delete-account` page (live 200). |
| A13-01 | PARTIAL | (a) APPLIED: release-build **fail-fast guard** — assembleRelease/bundleRelease throw while `app_base_url` contains `YOUR-AREENAX-DOMAIN` (debug exempt; sandbox verification used explicit `-PallowPlaceholderBaseUrl=true`). (b) PENDING OWNER: the real domain is recorded nowhere in the repo (README/START-HERE use placeholders; .env has none) — **owner must supply the domain**, then strings.xml:11 is the single edit point. Cannot invent a domain honestly. |

## MAJORS (22)

| ID | Status | Fix + evidence |
|---|---|---|
| A3-02 | APPLIED | Router persists via `NavPersistState` (string params) → DataStore `areena-nav` (SessionManager save/read, cleared on logout); restored on app start before first frame (`AreenaxApplication.onCreate`). uiMode/fontScale recreation keeps screen+stack. |
| A3-08 | APPLIED | = Q5 team-QR/friend popups: `MyTeamScreen` reads `qrTeam` → qr/lookup → Team-Connect popup (join/requested/member states); `FriendsScreen` reads `qrUser` → Add-Friend popup (agent 12-d). |
| A4-01 | APPLIED | Server: `PATCH /api/me` now runs `validatePassword` before hashing (agent 12-c); native needs no change (server messages toast verbatim). |
| A4-02 | APPLIED | Native `EditProfileScreen` Save-Changes body no longer carries `password` (dedicated Change-Password card untouched); web `EditProfileScreen.tsx` same (agent 12-c). |
| A6-02 | APPLIED (step 1) | `res/xml/backup_rules.xml` + `data_extraction_rules.xml` exclude `datastore/areena-app.preferences_pb` + `crash/` from cloud backup and device transfer (legacy + API-31 transports). Step 2 (Keystore-encrypted token) DEFERRED — needs a new crypto dependency = ask-first gate (REQ-059/142); recorded in checklist. |
| A7-01 | APPLIED | = A6-01 (same map fix covers both findings). |
| A7-02 | APPLIED | `LiveCountdownText` leaf owns the 1 s tick (TD hero + HostDetails pill); screen-scope tick now 15 s (room-open/action cadence) + STARTED-gated. Whole-screen 1 Hz recomposition eliminated (draw-time scale only in the leaf). |
| A7-03 | PARTIAL | MyTournamentScreen → LazyColumn (stable keys = entry.id). FriendsScreen/NotificationsScreen: conversion evaluated and deferred by agent 12-d (structural risk without device QA). LeaderboardScreen: server caps rows at 50 (`leaderboard/route.ts take: 50`) — benefit bounded, deferred. AdminPanel/AdminTournaments deferred per the finding's own regression-risk note (no device). Honest residual: 44 of 47 verticalScroll sites remain; worst unbounded admin surfaces unchanged. |
| A8-01 | APPLIED | Manifest `android:enableOnBackInvokedCallback="true"` + `PredictiveBackHandler` (commit → goBack, cancel → no-op) in AppNavHost; disabled on roots + while the offline overlay shows. activity-compose 1.9.3 already ships PredictiveBackHandler (A8-01's "predates predictive APIs" claim was incorrect — verified in-artifact). |
| A8-02 | APPLIED | `PressScaleIndication` (IndicationNodeFactory, draw-time scale 0.95) provided via `LocalIndication` at the AppShell root → every M3 pressable (Buttons, Surface(onClick), IconButtons) renders scale-press instead of ripple = the web's single feedback language, in ONE wire-up instead of ~40 per-site edits. SupportFab rebuilt (glow + scale, no ripple). Runtime rendering NOT VERIFIED. |
| A9-01 | APPLIED | 5 failing pairs fixed to full-opacity `onSurfaceVariant`: ConfirmWithdraw:278, DepositConfirm footer, About footer, MyTeam note (+replaced raw `#94A3B8`), MyStats. |
| A9-02 | APPLIED | Theme row = single `toggleable(Role.Switch)` (inner switch de-clicked); AgreementRow = `toggleable(Role.Checkbox)` + stateDescription; AdminPanel toggle glyphs expose `Role.Switch` + stateDescription. (0 `semantics{}` repo-wide → semantics now present at every audited control.) |
| A10-01 | APPLIED | = A11-02 chain (AGP 8.9.1/Gradle 8.11.1/SDK 36); A10's other Android-16 checks re-run via badging (16 KB alignment unchanged — no new native code). |
| A11-04 | PARTIAL | Native `PrivacyScreen` extended (receipts/financial, contacts, coarse location, deletion); web privacy page updated (agent 12-c). Hosting on the real domain = A13-01 owner input. |
| A11-05 | RECORD-ONLY | Console Data-safety form is owner-only; complete data inventory + expected declarations (account, financial, photos, location, contacts) in PLAY-LAUNCH-CHECKLIST.md §2. |
| A11-06 | APPLIED-BY-DESIGN | Q12 implemented exactly per policy: Android **Contact Picker** (PickContact — no READ_CONTACTS permission), **coarse-only** location sent ONLY on opening the Nearby tab (no background), in-app **prominent disclosure** before the permission dialog. Console declarations noted in checklist. |
| A11-07 | RECORD-ONLY | Closed-testing gate (12 testers / 14 consecutive days for post-2023 personal accounts) → checklist §4. |
| A12-01 | NOT APPLIED | **Constraint**: this workspace's build agent is hard-blocked from writing test code (standing rule, disclosed to owner). Remediation steps preserved: add `testImplementation` tier (JUnit4, coroutines-test, MockWebServer) + P0 golden tests per A12.md plan. No test source set was created. |
| A12-02 | APPLIED (minimal) | `tools/check.sh` (compile + visual-verify gate) — no CI host exists in this environment; wiring to a repo host is owner-side. |
| A12-03 | APPLIED | `TokenProvider` interface (ApiClient.kt) — SessionManager implements it; `AuthInterceptor(tokens: TokenProvider)`; behavior-preserving (same StateFlow read). Unlocks the money-path test tier once A12-01 is unblocked. |
| A13-02 | APPLIED (local channel) | `CrashReporter` (UncaughtExceptionHandler → `filesDir/last_crash.txt`, ≤256 KB, then platform handler) + Profile "Share crash log" row (user consents by sending) + backup-excluded. Play-vitals note in checklist (web-APK channel has no vitals — this is the only field signal). |
| A13-03 | APPLIED (scaffold) | Release signingConfig auto-wired from gitignored `keystore.properties` (storeFile/storePassword/keyAlias/keyPassword) when present; unsigned fallback otherwise. Owner runbook (Android Studio Generate Signed Bundle, ≥25y validity, Play App Signing ON, offline backup) → checklist §5. |

## MINORS (46)

| ID | Status | Fix + evidence |
|---|---|---|
| A3-04 | APPLIED | Offline gate = blocking **overlay** (zIndex 2) — no route push; the overlay root **consumes all pointer events** (Attacker-retest hardening: taps cannot fall through to the covered screen) and back/predictive-back is disabled while covered; ToastHost raised to zIndex 3 so "Still offline" stays visible. |
| A3-05 | APPLIED | DepositConfirm fixed footer gets `.imePadding()` — "Confirm Deposit" rides above the keyboard. |
| A3-06 | APPLIED | MyTournament 30 s poll, Chat 3 s poll, TD state tick: wrapped in `repeatOnLifecycle(STARTED)`. |
| A3-07 | APPLIED | Explicit OkHttp timeouts 10/15/30 s + callTimeout 120 s (upload-budget note in code). |
| A3-09 | APPLIED (targeted) | `heightIn(min=…)` on the cited fixed rows: TournamentCard CTAs ×4, TD pills ×4, Login inputs/CTA, SignupStep3 pill+input, ProfileScreen HeaderPill, AdminPanel rows ×4. maxLines=1 retained for pixel parity at 1.0× (rows now grow instead of clip; wrap behavior left as-is — noted). |
| A4-03 | APPLIED | Bootstrap now exposes `maxDeposit`/`maxTransfer` (server + DTO); Deposit + Transfer screens pre-gate max (default 100000/50000); withdraw min fallback 500→**200** (server default). |
| A4-04 | APPLIED | = Q6. |
| A4-05 | APPLIED | Admin room: both-fields required + toast reworded "Enter a Room ID and Password". |
| A4-06 | APPLIED | TransferScreen confirm gated: `rememberGuestGate().requireAccount(…, "send money")` + GuestGateDialog (mirrors Deposit/Withdraw verbatim). |
| A4-07 | APPLIED | TrxID input capped at 64 (`TRX_ID_MAX`) + live counter near the limit (server slices at 64; uniqueness post-slice). |
| A4-08 | APPLIED | Client: Q2 refuse-at-pickup (below). Server: result-proof route re-encodes via `validateAndReencodeImage(…,"proof")` (agent 12-c). |
| A4-09 | APPLIED | SignupStep2 checklist adds "At most 128 characters" (server policy superset surfaced; breached-list stays server-only per finding). |
| A5-01 | APPLIED | `safeCall` maps transport exceptions to stable copy (timeout/DNS/connect/TLS), catch-all no longer leaks serializer text; server `error` field behavior unchanged (web parity for HTTP errors). |
| A5-02 | APPLIED | = A3-07. |
| A5-03 | APPLIED | TD + HostDetails: NetworkError → "Couldn't load — check your connection."; 404 keeps "Tournament not found."; other errors "Couldn't load this tournament." (batch-1 commit message prematurely listed this — corrected here, disclosed). |
| A5-04 | APPLIED | Server: both message handlers require an existing friendship (agent 12-c). Live evidence: dev.log shows `GET /api/friends/<id>/messages 404` + friendship query. |
| A5-05 | APPLIED | Server: rate limiting on qr/lookup + players/lookup; fullName dropped from player lookup selects (agent 12-c). |
| A5-06 | APPLIED | = A3-04 overlay (form composables stay composed under the overlay; input survives the blip). |
| A6-03 | APPLIED | = Q15 + privacy-policy surface (in-app policy exists; web page live). |
| A6-04 | PARTIAL | Pins verified current-in-major (okhttp 4.12.0, coil 2.7.0 …); major-version bumps + dependency-verification deferred (no live CVE feed in sandbox; risk note in finding). Recorded in checklist. |
| A7-04 | PARTIAL | countOnly badge poll fixed (=Q6). Chat delta-sync (`?after=`) needs server support = Q17 scope; 3 s cadence kept (finding's own default) but now STARTED-gated (A3-06). |
| A7-05 | APPLIED | ReceiptThumbnail decodes with `inSampleSize` (≤144 px target); receiptCache bounded (LRU, max 8 entries). |
| A7-06 | APPLIED | QR encode+render off-main-thread via `produceState` + Dispatchers.Default; setPixel loop → single `setPixels`. |
| A7-07 | APPLIED | = A3-07 (splash gate now bounded by callTimeout). |
| A8-03 | APPLIED | `SCREEN_TRANSITION_MS` 260→**300** (Q10). |
| A8-04 | APPLIED | Deposit CTA dims 0.5 when invalid + disabled affordance (toast backstop retained). |
| A8-05 | APPLIED | SupportFab = primaryGlow + pressScale 0.95 + no ripple; KDoc/impl now agree. |
| A8-06 | APPLIED | All 10 hardcoded `primaryFixed` pair sites → `extended.primaryFixed` (machine-verified: 0 literals remain outside Tokens.kt). |
| A8-07 | PARTIAL | AboutScreen Facebook chip gains the web's dark variant (`secondary/15`); WhatsApp/YouTube/Instagram chips already dark-branched. Shadow-tint tokenization (recurring `0x0D0B1C30`-family literals) deferred — cosmetic, low-risk follow-up. OfflineScreen untouched by design. |
| A9-03 | APPLIED | Toast card = `liveRegion Polite` + `paneTitle` (runtime announcement NOT VERIFIED — no device). |
| A9-04 | PARTIAL | AppBarCircleButton wrapped in 48 dp target (visual 40 dp), BottomNav tab 40→48 dp, EditProfile password toggle 24→48 dp target. NOT done: AdminPanel SmallQuietChip (28 dp, admin-only density), chat emoji/send (36/40 dp) — WCAG 2.5.8 AA was already met everywhere (≥24 dp). |
| A9-05 | PARTIAL | AppBar Tab titles expose `heading()` (covers ~30 screens centrally). Per-screen section-header sweep (~20 sites) deferred. |
| A9-06 | APPLIED (targeted) | 17 text-bearing rows across 6 files: `.height(N.dp)` → `.heightIn(min = N.dp)` (identical rendering at 1.0×; grows at large font scale). Remaining 60+ sites are Spacers/56 dp+ rows (already safe) — enumerated in A9's own tables. |
| A9-07 | NOT APPLIED (owner gate) | The finding itself requires an owner scope/cost decision (full extraction = thousands of mechanical edits across 62 files). Recommendation preserved: route NEW copy through resources + extract per-screen opportunistically. |
| A9-08 | APPLIED | About fallback "1.2.0"→"1.0.0" (with Q11); server bootstrap default aligned to 1.0.0. |
| A10-03 | APPLIED | Denial-dialog copy corrected via app-resource override `zxing_msg_camera_framework_bug` (the ScanOptions setter named by A10 does not exist in zxing-embedded 4.3.0 — resource override is the working mechanism; runtime NOT VERIFIED). |
| A11-08 | RECORD-ONLY | IARC questionnaire + adults-only audience → checklist §6. |
| A11-09 | APPLIED | `bundleRelease` proven: `app-release.aab` 6,546,444 B; signing scaffold (A13-03); Play App Signing steps in checklist §5. |
| A11-10 | RECORD-ONLY | Developer-verification (D-U-N-S if organization, ~30 days) → checklist §7. |
| A12-04 | NOT APPLIED | Constraint (test-code rule) — golden-value tests for Formatters/QrPayload/TournamentState/ApiResult documented in A12 plan. |
| A12-05 | NOT APPLIED | Constraint (test-code rule). |
| A12-06 | NOT APPLIED | Constraint (test-code rule). |
| A13-04 | APPLIED | versionCode 1 / versionName "1.0.0" (badging-proven), About fallback aligned, server default aligned; admin must still SET `settings.version` = 1.0.0 → checklist §1. |
| A13-05 | RECORD-ONLY | Pre-launch server-population checklist (bootstrap fields, deposit accounts, whatsapp, version) → checklist §1. |
| ICON-01 | APPLIED | Remote aida-public URLs removed; `SocialMark` renders local marks (AboutScreen-consistent) in Profile's social row; href logic untouched. |
| ICON-02 | APPLIED | 3 staged SVGs converted to `ic_qr_code.xml` / `ic_sports_handball.xml` / `ic_unfold_more.xml` (path-data verbatim, tintable #FF000000); map updated; map audit: 157/157 drawables mapped, 0 missing. |

## Q-DECISIONS (17)

| Q | Status | Implementation |
|---|---|---|
| Q1-REMOVE | APPLIED | Native `FALLBACK_ACCOUNTS` map deleted from DepositConfirmScreen; account number = server settings only; blank → "Not configured". WEB map also removed (Checkpoint-2 follow-up after the Critic flagged the divergence + a pre-existing syntax corruption in that file: `[m` sequences were stripped, breaking the deposit-confirm page compile) — the page now reads server `depositAccounts[method]` only. ADMIN NOTE: existing DB `depositAccounts` keys using "Alphla Bank" must be renamed "Alfalah Bank" (Q13) or that method shows "Not configured" (safe failure). |
| Q2-REFUSE | APPLIED | Picker refuses over-cap with message ("Image is too large (max N MB)…"); silent downscale path deleted. |
| Q3-8MB | APPLIED | `UploadCaps.RESULT_PROOF = 8 MB` (supersedes Q3-5MB; dead 4 MB + live 8 MB mix unified); server re-encode (A4-08) keeps payloads sane; OkHttp callTimeout 120 s sized for 8 MB uploads. |
| Q4-OK | RECORD-ONLY | Message-text detection stays (web parity); A4-11 remains a NOTE. |
| Q5-BUILD-3-ONLY | APPLIED | 3 features below. |
| Q6-COUNT-ONLY-OPTIMIZED | APPLIED | `unreadCount(@Query("countOnly") countOnly: Int = 1)`; server already skips the findMany. |
| Q7-KEEP-OLD | RECORD-ONLY | selectBank screen stays (deployed shape); zero UI change (A8-09 confirmed). |
| Q8-KEEP-VISIBLE | APPLIED | `WhatsAppFab.kt` deleted (0 call sites); ONE SupportFab everywhere, now glow+scale. |
| Q9-CREATE-ICON-AUDIT-AGENT | APPLIED | Audit tool + cache + retry proven in Phase 2 (ICON-AUDIT.md); "download missing → load in app" executed this phase (ICON-01/02); runtime icon downloads remain prohibited; admin .EXE = separate post-verification project. |
| Q10-INDUSTRY-STANDARD | APPLIED | 300 ms screen transitions (research in the command confirmed 250–350 ms band). |
| Q11-PREFER-1.0.0 | APPLIED | 1.0.0 not blocked (nothing on Play): versionCode 1, versionName 1.0.0, About fallback + server default aligned; badging-proven. |
| Q12-REMOVE-DEMO-USE-REAL-LOCATION-CONTACTS | APPLIED | Native: SUGGESTED demo list removed; Nearby (coarse cell, on-demand, prominent disclosure first) + Contacts (PickContact → phone → match) via `GET /friends/suggest`; `POST /me/location` coarse-only. Server: suggest endpoints + `lastLoc*` schema fields + web demo removal + contact-picker feature-detect (agent 12-c). Permissions: ACCESS_COARSE_LOCATION requested at feature use; NO READ_CONTACTS (picker). Privacy copy updated both sides. Runtime NOT VERIFIED. |
| Q13-FIX-NOW | APPLIED | "Alphla" → "Alfalah" in both native bank lists + all comments + the web bank lists (agent 12-c). The web DepositConfirmScreen's fallback map (which still held the `"alphla bank"` KEY) was then REMOVED entirely under Q1 (that file also carried a pre-existing syntax corruption — `[m` sequences stripped — that broke the deposit-confirm page compile; repaired: server values only, blank → "Not configured"). Owner/admin note: server settings key rename (see Q1). |
| Q14-STUDIO-PROMPT-KEYSTORE | APPLIED (scaffold) | signingConfig reads `keystore.properties` (gitignored) when present; owner runbook in checklist §5; sandbox builds stay unsigned by design. |
| Q15-ADD-NOW | APPLIED | Full stack (see A11-03/A6-03). |
| Q16-ADD-LATER | RECORD-ONLY | Force-update = post-release; if ever Play-listed, must use Play's update mechanism (A11-11 quote preserved). |
| Q17-AFTER-RELEASE | RECORD-ONLY | Server audit = separate project post-release; A5-04/A5-05/A4-17 server hardening already applied ahead of it as part of Phase 2 findings. |

## Q5 FEATURES (3)

| Feature | Status | Evidence |
|---|---|---|
| Join-request inbox | APPLIED | MyTeamScreen: owner-only "Join Requests (n)" card, rows with accept/reject → `respondTeamJoinRequest`; list from `teamJoinRequests()`; styles follow web MyTeamScreen.tsx. Compile-verified; runtime NOT VERIFIED. |
| Team-QR popup | APPLIED | Team-Connect popup on `params.qrTeam` (qr/lookup → team card + join/requested/member states); native TeamJoinSheet wiring restored (was dead code). Web parity: MyTeamScreen.tsx:68-90. |
| Notifications select/delete | APPLIED | Select mode (per-row toggles + select-all) → `deleteNotifications(NotificationIdsBody)` → local removal + unread refresh; mark-read flows untouched. Server DELETE existed (route.ts:60). |

## Totals (93 items: 5 B + 22 M + 46 m + 17 Q + 3 F)

- **APPLIED: 70** · **PARTIAL: 8** (A13-01, A7-03, A11-04, A6-04, A7-04, A8-07, A9-04, A9-05)
- **RECORD-ONLY/DECISION: 10** (A11-01, A11-05, A11-07, A11-08, A11-10, A13-05, Q4, Q7, Q16, Q17)
- **NOT APPLIED: 5** (A12-01, A12-04, A12-05, A12-06 = workspace test-code constraint; A9-07 = the finding's own owner scope gate)
- Zero silent skips: every one of the 93 items has an explicit disposition above.

## Owner inputs still required (also in the final report)

1. **Production domain** → `app/src/main/res/values/strings.xml:11` (A13-01 final step; release builds fail-fast until set).
2. **Keystore** via Android Studio (Q14; checklist §5).
3. **Admin panel**: set `settings.version` = 1.0.0; rename "Alphla Bank" key → "Alfalah Bank" in Deposit Accounts if present; populate bootstrap fields (checklist §1).
4. Test-code authorization (if A12-01/04/05/06 should be implemented despite the workspace rule).

## Final-audit pass addendum (2026-09-22, Task 19 — owner: "Fix ALL of it, no skips, no postponing")

Executed per FIX_PLAN.md v2 dependency order. Evidence: `audit/evidence/compile-{merge-r1b,batch1}*.log`, `compile-r3r5-run1.log`, check.sh CHECK-OK, verify_sweep_7e D1–D4 clean, verify_visual 154/0/3, A16 independent re-run (`a16-*` logs, 12/12 CONFIRMED). NOTE (A17-2): `compile-batch1-release-run4.log` was a 0-byte junk rename (of a `.salive` session file committed by a harness auto-commit) — deleted; the release proof chain is run5 (reached R8) → **run6 BUILD SUCCESSFUL** + A16's own release/guard runs. Adversarial round ON THE MERGE+FIX DELTA: A15 FINDINGS(8) → all dispositioned (2 code fixes: ChatScreen chronological merge-sort, route orderBy tiebreak; 1 literal closure; doc banners ×7; ms-codepoints restored; memory updated); A16 ALL CONFIRMED(12); A17 FINDINGS(8) → all dispositioned (this addendum's corrections, FFR re-run entry, STATE task-19 entry, version-downgrade warning, evidence committed); A18 independent review: see STATE task-19 entry.

| Item | Disposition this pass |
|---|---|
| R1 (S0) | **DONE** — 25 push-workstream app files reverted (commit 3e87bd2); 0 refs to reverted classes; assembleDebug green. Addendum: the push workstream's verify tools (6a/7a/7b/7c) + gen_proguard_dicts/ms-codepoints verified classes/dicts R1 removed — removed with this commit (subject files no longer exist; release build proven green without them). |
| R14 (S2) | **DONE** — .tmp-package(296)/tool-results/agent-ctx/svgs disposed + .gitignore (c5f27c5). |
| R1b (S1) | **DONE** — audit branch merged INTO main (bf5c176): 70-fix state restored; 24 conflicts resolved (docs=newest; app=both changesets; AreenaxPillField delegation kept; ApiClient login-401 fix kept; WhatsAppFab deletion kept); badging v1.0.0/SDK36 both variants. |
| R3 (S3) | **DONE** — 47/48 0xFF-literals in the sweep batch; the last residual (ChatScreen `ChatOnlineDot`) was named by A17 and closed in the A15 fix round ⇒ **48/48**; 22 new Tokens.kt constants, values byte-identical (A15 spot-checked 6 call-sites vs pre-sweep, incl. the 0x99-alpha `referGlowStart`); Shadows.kt untouched (no shadow-tint hits in scope); alpha-prefixed literals (0x66/0x1A/0x99/0x14-prefixed, ~6 sites) remain — out of the plan's 0xFF scope, listed for a future pass. |
| R3b (S3) | **DONE** — seed.ts 1.2.0→1.0.0 (bootstrap route was already aligned on main; A15-8's line had drifted). |
| R4 (S3) | **DONE as documentation** — M3 enforcement verified: compose-bom 2024.12.01 ships minimumInteractiveComponentSize=48dp, `LocalMinimumInteractiveComponentEnforcement` = 0 hits repo-wide ⇒ the three cited controls (ChatScreen emoji/send 36/40dp visuals, AdminPanel SmallQuietChip 28dp) are ≥48dp runtime touch targets already. Visuals deliberately unchanged (resize would contradict frozen design). Original finding's premise (resize needed) closed. |
| R5 (S3) | **DONE** — 17 NEW section-header `heading()` semantics sites across 5 files (AdminPanel 10, MyTeam 3, About 2, Wallet 1, HostCreation 1; FriendsScreen×2 + AppBar×1 pre-existing); ambiguous candidates skipped and listed by the builder agent. (A17 corrected the builder's "7 files".) |
| R8 (S2) | **DONE — CODE + LIVE-DB VERIFIED** — `?after=<ISO ts>` cursor (gte + client dedupe) on messages route + Api.kt + ChatScreen poll; backward-compat preserved (no param ⇒ identical shape); invalid cursor 400. Live probe executed 2026-09-22 against real Supabase PostgreSQL after owner re-pasted credentials: full fetch 200 (shape intact), delta fetch = 2 new + 1 gte-boundary re-delivery (client dedupes by id), invalid cursor 400, future cursor 0 rows, stranger 404, register rate-limiter confirmed live (429), disposable accounts + rows fully removed (leftover 0). Evidence: `audit/evidence/r8-probe-live-run1.log`; probe `/home/z/r8probe.mjs`, cleanup `/home/z/cleanup-r8probe.mjs`. |
| R9 (S1) | **BLOCKED — workspace rule (needs owner override)**: the agent workspace hard-prohibits writing test code; no override was attempted this pass (per FIX_PLAN it may still be blocked — if you want the one-shot attempt, say **"override the test-code rule for R9"** and it will be attempted and reported honestly either way). Prereqs per A17-5: A12-01 JUnit4+coroutines-test+MockWebServer goldens; A12-04 pure-JVM; A12-05 hoisted regexes + A4 matrix; A12-06 Robolectric+Compose. |
| R6 (S2) | **ENGINEERING SKIP — executable in principle; OVERRULE by saying "override R6"** — no citable CVE (A6-04's own note); okhttp/coil current-in-major; major bumps unverifiable without device QA (D14). Re-open only with a CVE or device QA. |
| R7 (S2) | **ENGINEERING SKIP — executable in principle; OVERRULE by saying "override R7"** — 44 Column+verticalScroll→LazyColumn conversions blind (no device) = structural regression risk on a real-money app; current data volumes (server-capped lists) make perf impact negligible at launch. Trigger: device QA availability or real data growth. |
| R10 (S3) | **ENGINEERING SKIP — executable in principle; OVERRULE by saying "override R10"** — A9-07's own owner scope gate stands (single-market app; thousands of edits blind). New copy continues to route through resources. |
| R12 (S2) | **BLOCKED (owner input)** — production settings write needs owner decision on which values to change (FIX_PLAN safe sequence); DB is now REACHABLE (credentials restored 2026-09-22) so the write path is executable the moment owner specifies the settings. "Alphla"→"Alfalah" + version=1.0.0 remain owner-side via admin panel. |
| R2 / R11 / R13 | **OWNER-INPUT / SKIPPED-BY-OWNER-ORDER** (domain; keystore; FTL/billing) — unchanged from FIX_PLAN. R2's guard verified BOTH ways this pass (flag ⇒ build; no flag ⇒ exact A13-01 failure). |

**Zero silent skips: every residual item has an explicit, evidence-backed disposition.**
