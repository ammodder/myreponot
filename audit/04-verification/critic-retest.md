# CRITIC RE-TEST — PHASE 3 FIX QUALITY (TASK-007, worklog task 12-f)

Role: Critic (read-only). Audits the QUALITY and COMPLETENESS of the Phase-3 fix work —
not "does it compile" but "is each claimed fix what the finding actually ordered, is it
minimal, does it match web parity, and does it leave no collateral drift."
Date: 2026-09-21. HEAD audited: dcba8d0 (audit/production-readiness). Web truth: /home/z/my-project/src/** (incl. phase-3 web commit 8e72e79).

Method: 17 ledger rows re-verified at source level (sample ≥2 per severity class), full
add/delete scan of `git diff 0beaf75..HEAD` on app/** (62 files, +2,564/−629), residue
greps for all owner decisions in BOTH repos, line-by-line read of the 4 new Kotlin files,
independent `aapt2 dump badging` on the release artifact, and arithmetic check of FIXES.md.
No application code was modified.

---

## Check 1 — Ledger honesty (17 rows sampled; overclaim = flag)

| Row | Claim | Verified | Verdict |
|---|---|---|---|
| A6-01 (B) | compile-time `iconIds` map (157), fallback ic_help, reflection gone | AreenaxIcon.kt:54 map counts exactly **157** `"…" to R.drawable.ic_…` entries; :105 fallback; no `getField` remains; deleted-line scan confirms reflection+cache removed | OK |
| A13-01 (B) | release fail-fast guard on placeholder base URL; owner domain pending | build.gradle.kts:27–28 + :80–82; strings.xml:11 is the single edit point; status PARTIAL is honest | OK |
| A3-02 (M) | router persisted via NavPersistState → DataStore `areena-nav`, restored at start | SessionManager.kt:199–212, :264; AreenaxApplication.kt:85–91 persistHook + restore | OK (nit: restore is async IO + main-looper post — "before first frame" is best-effort, not guaranteed; runtime NOT VERIFIED anyway) |
| A7-02 (M) | 1 s tick moved into LiveCountdownText leaf; screen tick 15 s | LiveCountdown.kt:24–53 leaf owns `rememberLiveNow(1_000L)`; used by detail screens | OK |
| A8-02 (M) | ONE press language via LocalIndication at AppShell root | PressScaleIndication.kt (IndicationNodeFactory, draw-time 0.95) + AppShell.kt:163 wire-up | OK |
| A9-01 (M) | 5 text pairs → full-opacity onSurfaceVariant | all 5 sites confirmed: ConfirmWithdraw:278, DepositConfirm:467, About:458, MyStats:229, MyTeam:610 (raw #94A3B8 replaced) | OK (nit: the paired 14 dp decorative info icon at MyTeamScreen:602 still uses raw #94A3B8 — decorative (null description), outside the text-pair finding) |
| A3-05 (m) | DepositConfirm footer `.imePadding()` | DepositConfirmScreen.kt:456 | OK |
| A4-07 (m) | TrxID capped 64 + live counter | TRX_ID_MAX=64 (:105), `take` (:412), counter (:416–418) | OK |
| A7-05 (m) | receipt decode inSampleSize ≤144 + LRU cache max 8 | DepositConfirmScreen.kt:518–521; AdminPanelScreen.kt:472–475 (`removeEldestEntry = size > 8`) | OK |
| A10-03 (m) | denial copy via `zxing_msg_camera_framework_bug` resource override | strings.xml override present with honest comment; runtime NOT VERIFIED disclosed | OK |
| Q2 | refuse over-cap at pickup, silent downscale deleted | PhotoPickers.kt:99–101 `onError("Image is too large (max …)…")`; compressToCap/decodeScaled fully deleted (0 callers) | OK |
| Q6 | `unreadCount(@Query("countOnly") countOnly = 1)` | Api.kt:265 byte-for-byte as claimed; server route checks `countOnly` (notifications/route.ts:14) | OK |
| Q8 | WhatsAppFab deleted, one SupportFab | file gone; zero code call sites; SupportFab = glow+scale per A8-05 | OK (two stale comments still name WhatsAppFab — see Notes) |
| Q11 | versionCode 1 / versionName 1.0.0 | build.gradle.kts:38–39; **independent `aapt2 dump badging` on the release APK: `versionCode='1' versionName='1.0.0'`** | OK |
| Q13 | "Alphla"→"Alfalah" native lists+comments **and web (agent 12-c)** | native clean (TournamentState.kt:174, BindAccountScreen.kt:495 + lists); **WEB RESIDUE: `src/components/screens/wallet/DepositConfirmScreen.tsx:17` still has `"alphla bank"` — commit 8e72e79 touched BindAccount/Deposit lists but not this file** | **ISSUE-2 (overclaim on the web half)** |
| Q15 | typed-DELETE dialog + server DELETE /api/me with refusals | ProfileScreen.kt:422–445 dialog; me/route.ts:132–188 refuses balance>0 / open hosted tournaments / team-with-members, transactional cascade | OK (note below on balance-gate nuance) |
| Q5 feature 3 | notifications select/delete | NotificationsScreen.kt:87–131 selectMode/toggleSelect/selectAll + deleteNotifications; mark-read flows untouched; documented native addition of an explicit "Select" toggle (web = long-press) | OK |

**Overclaims found: 1 substantive (Q13 web), 2 precision nits (A3-02 "before first frame";
A6-01 "144 ic_ keys" vs its own proof file's 157/157 — see Check 7).**

## Check 2 — Web parity of native changes

- **Q10 (300 ms)**: web truth = AppShell.tsx:302 `transition={{ duration: 0.18 }}` (180 ms);
  native = `SCREEN_TRANSITION_MS = 300` (AppNavHost.kt:58). The deviation is **AUTHORIZED by
  Q10** ("research typical Android animation 250–350 ms; use 300 ms" — decision logs 8 & 9)
  and is documented: FIXES.md A8-03 records 260→300, and TASK-007's constraints list Q10
  among the decisions that intentionally break web parity. OK.
- **Q2/Q3 refuse behavior**: native refuses over-cap picks with a message; web rejects
  over-cap uploads (REQ-143, Phase-1 Attacker correction); server result-proof now
  re-encodes (`validateAndReencodeImage(…, "proof")` in result-proof/route.ts:31). Parity of
  *behavior* (refuse, not downscale) holds on all three layers. OK.
- **Q12 UI**: native FriendsScreen mirrors web FriendsScreen.tsx — `GET /friends/suggest`
  mode=nearby / mode=contacts&phone=, demo rows removed both sides, suggestions "silently
  empty — never critical" both sides, prominent disclosure before the location dialog,
  PickContact (no READ_CONTACTS). Only documented delta: native adds an explicit "Select"
  toggle next to web's long-press. OK.
- **Q15 dialog vs Play policy wording**: dialog states permanence + typed-DELETE confirm;
  server copy names each unblock action ("Withdraw your balance…", "Cancel or finish your
  hosted tournaments…", "Transfer or disband your team…"). Matches the Play User-Data
  deletion-policy language (in-app deletion, permanent + cannot be undone). Note: the
  balance>0 gate means a user must zero the balance before self-serve deletion — defensible
  (withdrawal is the documented unblock), and moot under the recorded Path-A distribution.
  OK with note.
- **A9-01 contrast math (both themes)**: light `onSurfaceVariant` **#434655** vs the surfaces
  at the 5 sites — background/surface #F8F9FF → **8.90:1**; surfaceContainer #E5EEFF →
  **8.02:1**; containerHigh #DCE9FF → **7.63:1**; containerHighest/variant #D3E4FE →
  **7.26:1**; containerLowest #FFFFFF → **9.35:1**. Dark `onSurfaceVariant` **#9D9DA6** vs
  #0A0A0C → **7.35:1**; #1B1B1F → **6.38:1**; #232327 → **5.82:1**; #2A2A2F → **5.31:1**;
  #131316 → **6.90:1**. Worst case 5.31:1 — **full opacity passes 4.5:1 in BOTH themes at
  every site**. OK.

## Check 3 — Regressions introduced

- `git log --oneline -8` + full add/delete scan of `0beaf75..HEAD` (62 files): every deleted
  line maps to an authorized fix scope — WhatsAppFab.kt whole-file deletion (Q8-KEEP-VISIBLE),
  silent-downscale path (Q2), 260 ms constant (Q10), BackHandler→PredictiveBackHandler (A8-01),
  AuthInterceptor(SessionManager)→(TokenProvider) (A12-03), offline route-push→overlay (A3-04),
  40 dp circle→48 dp (A9-04), `.height`→`.heightIn` (A9-06), icon reflection+cache (A6-01),
  QR setPixel loop→setPixels (A7-06). **No collateral deletions or renames found.**
- `compressToCap` / `decodeScaled`: **zero remaining callers** (rg exit 1 across app/src). ✓
- **ISSUE-1 (version control): the A11-02 toolchain bump is UNCOMMITTED.** Working tree has
  `libs.versions.toml` agp 8.9.1 and `gradle-wrapper.properties` Gradle 8.11.1, but HEAD
  (dcba8d0) still pins agp 8.7.3 / Gradle 8.10.2 while the committed build.gradle.kts already
  has compileSdk/targetSdk 36. So: (a) a clean checkout of HEAD is internally inconsistent
  (SDK 36 with AGP 8.7.3 — unsupported pairing), and (b) batch-1 commit fea1496's message
  claims "AGP 8.9.1" but the file carrying it was never committed. The "APPLIED,
  compile-verified" state exists only in the working tree. Fix: commit the two files (plus
  the untracked dex-proof evidence file).

## Check 4 — Owner-decision fidelity

- **Q3-8MB**: `UploadCaps.RESULT_PROOF = 8 * 1024 * 1024` (PhotoPickers.kt:33, comment cites
  the 4 MB-dead/8 MB-live unification); TournamentDetailsScreen.kt:119 `PROOF_MAX_BYTES = 8MB`;
  no `4 * 1024 * 1024` / "5 MB" residue on any result-proof surface. Remaining "≤ 5 MB" hits
  are the **host-tournament image** cap (Api.kt:100, PhotoPickers.kt:20, Models.kt:910,
  HostCreationScreen.kt:100) — a different, pre-existing cap, in scope. ✓
- **Q11-1.0.0**: versionCode/versionName verified (independent badging); rg `1\.2\.0|2\.0\.0`
  over app/src AND bootstrap route = zero hits; About fallback + server default aligned
  (commit 8e72e79 bootstrap changes; checklist §1 still correctly asks admin to SET
  settings.version). ✓
- **Q13**: native clean; **web residue "alphla bank" at DepositConfirmScreen.tsx:17 → ISSUE-2**
  (same as above).
- **Q1**: native `FALLBACK_ACCOUNTS` deleted (only a KDoc reference to the removal remains);
  blank → "Not configured". **QUESTION-1**: the web-side map still exists
  (DepositConfirmScreen.tsx:13–20, referenced :43) — FIXES.md says "Web-side map owned by
  agent 12-c scope", but phase-3 web commit 8e72e79 did not touch that file, so the web half
  is neither applied nor explicitly ledgered as deferred → native/web behavior now diverges
  (native: server-only accounts; web: hardcoded fallback incl. the "alphla bank" key).
  Owner decision needed: apply Q1 to web, or ledger the divergence. Separate but important:
  the same web file line 43 contains a **pre-existing syntax error**
  (`FALLBACK_ACCOUNTSethod.toLowerCase()]`, committed 2026-09-05, i.e. original-build era —
  NOT phase-3 collateral) which makes that screen's fallback logic dead code on web and makes
  any Q1/Q13 web verification there unreliable. Recommend fixing the web file independently
  of the audit.

## Check 5 — Kotlin quality of the 4 new files

- **CrashReporter.kt**: clean, allowlist-honest (no deps, no network), chains to the previous
  handler so the process dies as the platform expects, 256 KB cap, backup-excluded. No silent
  catches that hide the failure path the finding cared about (`runCatching` around the write
  is correct — the crash must still propagate). Nits: SimpleDateFormat is fine for minSdk 24;
  FIXES.md says `filesDir/last_crash.txt` while the code writes `filesDir/crash/last_crash.txt`
  (the file's own KDoc is right — one-word ledger imprecision).
- **LiveCountdown.kt**: minimal leaf-composable design, `remember(keyed on startTimeIso, now)`
  correct, no recreation churn. Clean.
- **SocialMarks.kt**: local brand marks, `alt.lowercase()` routing, decorative fallback
  ic_help. Nits: inline fully-qualified `androidx.compose.ui.graphics.Path` (works, slightly
  unidiomatic); brand hexes hardcoded by design (documented in KDoc). Clean.
- **PressScaleIndication.kt**: idiomatic Compose 1.7 IndicationNodeFactory/DrawModifierNode;
  draw-time scale (no relayout); press count ref-counted with coerceAtLeast(0) — release/cancel
  cannot underflow; `invalidateDraw` on interaction. Redundant equals/hashCode on a singleton
  (harmless, arguably intentional for the Indication contract). No silent catches. Clean.
- Import hygiene: all four files have complete, ordered, no-unused imports. No silent-catch
  patterns anywhere that mask a real failure path.

## Check 6 — Docs

- **Totals arithmetic**: BLOCKERS 5 rows ✓; MAJORS 22 rows (16 APPLIED + A11-06
  APPLIED-BY-DESIGN + 2 PARTIAL + 2 RECORD-ONLY + 1 NOT APPLIED = 22) ✓; MINORS 46 rows
  counted ✓; Q 17 ✓; FEATURES 3 ✓; 5+22+46+17+3 = **93** ✓; statuses **70 APPLIED +
  8 PARTIAL + 10 RECORD-ONLY/DECISION + 5 NOT APPLIED = 93** ✓; the PARTIAL/RECORD-ONLY/
  NOT-APPLIED name lists match the tables exactly. Zero silent skips confirmed on sample.
- **STATE.md phase line**: "PHASE 3 COMPLETE — RE-TEST + FINAL REPORT IN PROGRESS … 70/8/10/5"
  with the re-test and final-report checkboxes still unchecked — honest, matches reality.
- **PLAY-LAUNCH-CHECKLIST.md**: §1 ↔ A13-05/A13-04-item-3/Q1+Q13 admin note; §2 ↔ A11-05
  (incl. coarse-location and single-picked-contact scoping that mirror Q12's implementation);
  §3 ↔ A11-01 Path A; §4 ↔ A11-07 (12 testers/14 days); §5 ↔ A13-03+Q14+A11-09; §6 ↔ A11-08;
  §7 ↔ A11-10; §8 ↔ A6-04 residual. Matches. ✓
- **Worklog gap (process)**: no "Task ID: 12*" entries exist in either worklog yet
  (TASK-007 acceptance item 8 "worklog Task 12 appended" is open; STATE honestly marks the
  final report pending). Consequence: the per-agent claims credited to "agent 12-c/12-d"
  inside FIXES.md currently have no worklog trail to cross-check — the web commit 8e72e79
  message and the code were used as the substitute evidence in this re-test.

## Check 7 — Compile evidence

- compile-p3-run13.log + run14.log: `BUILD SUCCESSFUL in 27s / 30s` ✓ (run14 = the commit
  04fc5b5 gate for A9-02 admin toggles + A7-05 cache bound, as claimed).
- assembleDebug-p3.log: `BUILD SUCCESSFUL in 2m 15s`; artifact on disk = **13,127,513 B** —
  matches the ledger byte-for-byte ✓.
- assembleRelease-p3-run3.log: `BUILD SUCCESSFUL in 4m 36s` (53 tasks, bundleRelease
  executed); release APK = **2,698,256 B**, AAB = **6,546,444 B** — both match ✓. Runs 1–2
  logs end with "Gradle build daemon disappeared unexpectedly" — consistent with the
  kernel-OOM disclosure.
- Independent artifact probe (this re-test, not relied on the ledger):
  `aapt2 dump badging` → `versionCode='1' versionName='1.0.0' targetSdkVersion:'36'
  compileSdkVersion='36'` — matches A11-02/Q11/A13-04 claims exactly. ✓
- Evidence curation: `audit/evidence/phase2/a6-01-release-dex-iconmap-proof.txt` is
  **untracked** and lives in the phase2 folder despite being TASK-007 evidence; its summary
  says `checked=157 missing=0 PASS` while FIXES.md A6-01 says "144 `ic_*` name keys present"
  — both support the pass, but the two numbers are never reconciled (144 presumably from the
  older phase-2 probe). Commit the file and align the number (**QUESTION-2** — which probe
  produced 144?).

---

## Findings summary

- **ISSUE-1** — A11-02/A10-01 toolchain bump (AGP 8.9.1, Gradle 8.11.1) is uncommitted;
  HEAD is internally inconsistent (compileSdk 36 committed, enabling AGP version not
  committed). Ledger "APPLIED" is only true of the working tree. Action: commit the two
  gradle files (+ untracked dex-proof evidence).
- **ISSUE-2** — Q13 web overclaim: "alphla bank" persists at web DepositConfirmScreen.tsx:17;
  commit 8e72e79 fixed the web bank lists but not this key, while the ledger credits
  "web (agent 12-c)". Action: one-word web edit or correct the ledger row to native-only.
- **QUESTION-1 (owner)** — Q1 web scope: hardcoded FALLBACK_ACCOUNTS remain on web
  (DepositConfirmScreen.tsx:13–20) while native deleted them → native/web divergence on a
  real-money surface; ledger defers to "agent 12-c scope" without a disposition. Also
  disclosed: that file has had a syntax error since 2026-09-05 (pre-existing, not phase-3
  collateral) — worth an independent web-side fix.
- **QUESTION-2 (owner)** — reconcile "144 ic_ keys" (FIXES.md) vs "157/157" (proof file).
- **NOTES** — (1) A3-02 restore is best-effort async, not guaranteed pre-first-frame;
  (2) stale comments still name the deleted WhatsAppFab (AppShell.kt:68,
  AreenaxApplication.kt:92); (3) MyTeamScreen:602 decorative info icon keeps raw #94A3B8
  (outside the text-pair finding; decorative); (4) FIXES.md A13-02 path imprecision
  (filesDir/crash/last_crash.txt); (5) no Task-12 worklog entries exist yet (TASK-007
  acceptance item 8 open); (6) runtime on device remains NOT VERIFIED — standing limitation,
  honestly disclosed everywhere sampled.

**CRITIC VERDICT: PASS-WITH-NOTES** (2 issues — both fixable without touching fix logic;
2 owner questions; 6 notes). The 17 sampled dispositions are real, minimal, and
web-parity-correct except where the owner decision itself moved the target (Q2/Q3/Q10/Q11/
Q12); build claims are artifact-proven to the byte.
