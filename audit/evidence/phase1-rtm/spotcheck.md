# Orchestrator independent spot-check — Phase 1 RTM (Task 10)

Date: 2026-09-20 · Checker: orchestrator (main agent, Stage-5 independent verification)
Method: re-ran my OWN probes (rg/sed/ls + file reads) against 25 sampled RTM rows (~15% of 165),
WITHOUT looking at the verifier's session — comparing only the cited file:line + claim to the
actual code in the audit worktree (branch audit/production-readiness @ 9dcc614 + Phase-1 docs).

| # | REQ | Probe I ran | Result | Note |
|---|---|---|---|---|
| 1 | REQ-058 | rg countOnly/unreadCount in core/network/Api.kt | PASS | :237 notifications(countOnly…) exists; :241 unreadCount() param-less → PARTIAL fair (web sends countOnly=1 per Attacker, AppBar.tsx:30) |
| 2 | REQ-013 | rg "already submitted" TournamentDetailsScreen | PASS | :179 message-text 409 lock, ignoreCase — web parity per Attacker |
| 3 | REQ-133 | rg x-token ApiClient.kt | PASS | :64/:71 interceptor header |
| 4 | REQ-135 | rg Idempotency-Key ApiClient.kt | PASS | :77/:84 UUID v4 on non-GET |
| 5 | REQ-008 | rg env.params/qrUser/qrTeam in Friends+MyTeam | PASS | 0 hits → payload dropped; attacker downgrade V→P justified |
| 6 | REQ-142 | rg -i firebase app/src | PASS | 0 hits — absence holds |
| 7 | REQ-064 | rg 0xFF060608 Tokens.kt | PASS | :86 DarkSurfaceDim = Color(0xFF060608) |
| 8 | REQ-157 | rg "Final Approve" AdminPanelScreen | PASS | :731 four-eyes chip |
| 9 | REQ-139 | rg maskAccountNumber | PASS | core/util/Formatters.kt:132 |
| 10 | REQ-101 | rg Alphla native + web src | PASS | typo present BOTH sides (BindAccountScreen.kt:495 comment "BINDING"; DepositScreen.tsx:15) — parity holds |
| 11 | REQ-054 | rg stack cap | PASS | core/nav/AppNavigator.kt:13,:53 "stack capped at 25 … slice(-25)" |
| 12 | REQ-062 | WalletScreen.kt:291 vs WalletScreen.tsx:111 | PASS | label "Add" vs web TSX "Deposit"; in-code comment discloses; web HTML (wallet.html:143) also "Add" → design-source rule |
| 13 | REQ-100/C9 | sed DepositConfirmScreen.kt:97-107,126-131 | PASS | hardcoded FALLBACK_ACCOUNTS (6 entries) + fallback default "0300-1234567" — real-money path confirmed |
| 14 | REQ-048/122 | sed AboutScreen.kt:108-112 | PASS | settings?.version ?: "1.2.0" at :110 (STATE.md's old :106 cite is stale — fixed in STATE this task) |
| 15 | REQ-061 | rg versionCode/versionName app/build.gradle.kts | PASS | :16-17 → 2 / "2.0.0" |
| 16 | REQ-145 | rg minify/proguard app/build.gradle.kts | PASS | :24 isMinifyEnabled=true + proguard files; no keystore (matches PARTIAL) |
| 17 | REQ-125 | rg app_base_url strings.xml | PASS | :11 placeholder https://YOUR-AREENAX-DOMAIN.example.com |
| 18 | REQ-073 | ls res/drawable + ext count | PASS | 154 xml + 1 png = 155 files; ledger's "154 XML drawables" exact |
| 19 | REQ-052 | rg GET me Api.kt + splash refs | PASS | Api.kt:126-127 me(); restore wiring in MainActivity/AppShell |
| 20 | REQ-151 | rg TTL HomeScreen.kt | PASS | :91 BOOTSTRAP_TTL_MS=60_000L; :112 TTL check (attacker overturn to VERIFIED accepted) |
| 21 | REQ-147 | rg 3000/3_000 ChatScreen.kt | PASS | :78 CHAT_POLL_MS=3_000L, LaunchedEffect-scoped |
| 22 | REQ-117 | rg Online/divider ChatScreen.kt | PASS | :71/:347 "static" header docs + ChatOnlineDot |
| 23 | REQ-083/018 | rg SelectBank DepositScreen.kt | PASS | :245 comment + :257 env.navigate(ScreenKeys.SELECT_BANK, …) |
| 24 | REQ-097 | rg onUnauthorized | PASS | AreenaxApplication.kt:72 wiring; ApiClient.kt:119 invoke |
| 25 | REQ-136 | rg inFlight/409 ApiClient.kt | PASS | :96-109 ConcurrentHashMap guard → Error("Request already in progress", 409) |

Result: 25/25 citations ACCURATE. Zero fabricated line numbers found in the sample.
Corrections applied from Attacker 10-f (3): REQ-008 V→P, REQ-143 V→P, REQ-151 P→V — each
re-probed by me before acceptance (rows 5, 20 above + PhotoPickers last-resort branch read).
Drift found & fixed: STATE.md cited AboutScreen.kt:106; real line is :110 (doc-only, audit file).
