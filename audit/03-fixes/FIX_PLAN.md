# FIX_PLAN.md — Residual Triage & Work Order (v2 — after A15/A17 adversarial round)

Produced by A0 per owner command 2026-09-21 ("Run the full Prompt Pipeline. Start PHASE 3
(Triage). No code changes. … Save /audit/03-fixes/FIX_PLAN.md. CHECKPOINT 2 — STOP.").
**No application code has been changed for this plan.**
v1 was challenged by A15 (Attacker, 9 findings) and A17 (Critic, 8 findings); ALL 17 are
incorporated below (Section 7 lists each finding → its disposition). Findings that
invalidated v1 text: incomplete R1 revert list, false Option-B claim, missing branch
reconciliation, stale-src-tree hazard, R8 live-DB test hazard, R4 no-op premise.

---

## 1. Corrected queue size (FACT — Stage-1 correction to the owner's premise)

The Phase-3 ledger (`FIXES.md`) dispositioned all 93 items:

| Disposition | Count | Meaning |
|---|---|---|
| APPLIED | 70 | Code changed, compile-verified (evidence: `audit/evidence/compile-p3-run{2,6,13,14}.log`) |
| RECORD-ONLY / DECISION | 10 | Zero code by design (Play-console forms, post-release items, owner-only decisions) |
| PARTIAL | 8 | A step remains (the real fix queue) |
| NOT APPLIED | 5 | 4 blocked by the workspace "no test code" rule (A12-01/04/05/06), 1 owner scope gate (A9-07) |

**→ The actionable residual queue is 13 known residuals + 1 NEW S0 compile regression +
1 NEW S1 structural debt (branch divergence, A17-1) + owner-only inputs.** Cross-map
dedupe (A6-01=A7-01, A5-02=A3-07=A7-07, A6-03=Q15=A11-03, A4-04=Q6, A8-03=Q10,
A9-08=A13-04(Q11), A3-08=Q5, A5-06=A3-04, A10-01=A11-02, A9-11=A11-04,
A13-05(A13-04 item 3), A6-04(A12-05 dependency)) is already in FIXES.md — no duplicate IDs.

**CRITICAL CONTEXT (A17-1, FACT):** the 70 APPLIED fixes exist on branch
`audit/production-readiness` only. `main`'s app tree is pre-fix (compileSdk/targetSdk 35,
versionCode 2, no release fail-fast guard — verified by A17 vs `app/build.gradle.kts:20-28,
74-86`). The published v2.0.0 APK (badging targetSdk 35) matches main's pre-fix shape +
field-concept/GMD commits. Reconciliation is therefore item **R1b (Batch 0)**.

## 2. Severity legend (per Section 5 of MASTER_RULES.md)

S0 = core flow dead / blocks all work · S1 = major feature or release-gate weakness ·
S2 = partly wrong, workaround exists · S3 = cosmetic.

## 3. The residual queue, severity-ordered, dependency-ordered

### BATCH 0 — unblock-everything (must go first; R1 → R1b in that order)

| ID | Sev | Item | Files that will change | Verification | Regression risk | Changes | Disposition |
|---|---|---|---|---|---|---|---|
| R1 | **S0** | **Main tip does not compile** — b0bfd48 (push workstream) added 25 files to `AreenaxNativeAndroid/app` incl. TWO firebase-importing files: `core/push/AreenaxMessagingService.kt` (imports `com.google.firebase.*`, lines 13-14) and `core/push/PushManager.kt` (imports FirebaseApp/FirebaseOptions/FirebaseMessaging + gms.tasks, lines 10-13); zero firebase entries in any gradle file (A15 evidence, git grep). Blocks EVERY future main-line fix. | **Option A (recommended): revert ALL of b0bfd48's app-side additions** = `git diff a25623a b0bfd48 --name-status -M -- AreenaxNativeAndroid/app` (25 files: 24 added incl. `app/vault-gen.gradle.kts`, `core/push/*.kt` (2), `core/config/{AppConfig,AppConfigGate,SupabaseConfigFetcher}.kt`, `core/security/*` (8), `core/session/AppConfigStore.kt`, `core/theme/FieldStyle.kt`, `core/ui/{ServerSetupScreen,SmoothAsyncImage}.kt`, `proguard-dicts/*` (3), `res/xml/{backup_rules,data_extraction_rules,network_security_config}.xml`; PillField.kt mode-only 100644→100755). Safe because A15 proved NO pre-existing file references any added class. **Option B (NOT recommended):** add firebase-bom + firebase-messaging — A15 proved this STILL fails: PushManager reads 4 missing `fcm_*` string resources (no res/values files were added) and no manifest `<service>`/`MESSAGING_EVENT` exists; push cannot function without owner's Firebase project anyway. | Recompile assembleDebug + assembleRelease (D5); aapt2 badging (D8); grep BOTH push file names → 0 hits (A15-9); `git diff a25623a HEAD --stat -- AreenaxNativeAndroid/app` shows only intended deltas. | Option A: low (returns app tree to last-known-good; nothing references the reverted classes). Option B: medium + dead code. | Option A: none visible. Option B adds a **library** → NEEDS APPROVAL (F2). | **RECOMMEND: Option A.** Push belongs to a dedicated task WITH the owner's Firebase project; owner just said "No Firebase". |
| R1b | **S1** | **Reconcile the diverged branches (A17-1)** — main (web fixes, field concept, GMD, new audit docs) vs audit/production-readiness (the 70-fix app state: AGP 8.9.1/Gradle 8.11.1/SDK 36, fail-fast guard, signing scaffold, Q11 1.0.0, all APPLIED Kotlin changes). Without this, every future fix lands on a main that silently lacks the 70 fixes, and R2's guard verification is unexecutable (A17-2). | Git merge of `audit/production-readiness` INTO main (forward-only, no force-push, F4). Expected conflicts: Kotlin files touched by BOTH the fix waves and the field concept (AdminPanelScreen, ChatScreen, etc.), worklog.md / STATE.md / MASTER_RULES.md (identical-content conflicts), tool-results. Resolution rule: app files = BOTH changesets merged by file owner; docs = newest content (already mirrored). | Post-merge: assembleDebug + assembleRelease on main; aapt2 badging shows targetSdk 36 + versionName 1.0.0 (the ledger's A11-02/A13-04 state); Feature Freeze Register re-run; A15/A16/A17/A18 round on the merge delta. | **Medium-high: this is the one genuinely risky git operation** (large conflict surface in Kotlin). Mitigation: merge in one dedicated session, per-file resolution against both parents' evidence, compile after resolution, freeze re-run before commit. | None visible (combines two already-approved states) → **NEEDS YOUR APPROVAL as a step** (it rewrites main's app tree to the fix state). | **RECOMMEND: APPROVE** — it restores your own already-verified fix state; without it, approving R3-R5 fixes a tree that isn't the real app. |

### BATCH 1 — mechanical residuals (agent-safe AFTER R1+R1b; recommend approve all)

| ID | Sev | Item | Files that will change | Verification | Regression risk | Changes | Disposition |
|---|---|---|---|---|---|---|---|
| R3 | S3 | **Token sweep** (A8-07 remainder, scope widened per A17-3): shadow-tint literals AND AboutScreen pastel chips (`0xFFF0FDF4`:210,377 / `0xFFFEF2F2`:248 / `0xFFFDF2F8`:276 / `0xFFEFF6FF`:319) + social brand colors → Tokens.kt. `core/ui/Shadows.kt` is the WHITELISTED home for shadow constants (4 of 7 live there legitimately — A17-3); sweep targets literals outside Tokens.kt + Shadows.kt. | `core/theme/Tokens.kt`, `core/ui/Shadows.kt`, `ui/screens/info/AboutScreen.kt`, other grep-cited literal sites. | grep = 0 raw literals outside Tokens.kt+Shadows.kt (D2); assembleDebug (D5); values byte-identical (diff review). | Low — pure refactor, identical values. | Design tokens only (values unchanged) → no approval. | **APPROVE.** |
| R3b | S3 | **Server version default never aligned** (A15-8): `src/app/api/bootstrap/route.ts:55` `s.version ?? "1.2.0"` + `prisma/seed.ts:119` `["version","1.2.0"]` contradict the ledger's A9-08/A13-04 claim (1.0.0); any reseed reverts the setting. | Those 2 files, one literal each. | grep; curl /bootstrap shows 1.0.0 fallback. | Very low. | Behavior of a fallback default only → no approval. | **APPROVE.** |
| R4 | S3 | **Touch-target remainder — RE-SCOPED to verify-and-document (A15-4)**: all three cited controls are M3 `Surface(onClick)` on compose-bom 2024.12.01 which enforces `minimumInteractiveComponentSize` 48dp (repo never disables `LocalMinimumInteractiveComponentEnforcement`) ⇒ runtime targets are ALREADY ≥48dp. Resizing visuals would CONTRADICT "visuals unchanged" (chip +71%). Action: verify enforcement facts + record in A9-04 row; resize NOTHING. | None (RECORD-ONLY), or a comment in A9-04 finding row. | Code citations (ChatScreen.kt:319-347, AdminPanelScreen.kt:2090-2098, libs.versions.toml:6). | Zero. | None. | **APPROVE (as documentation).** |
| R5 | S3 | **Heading-sweep remainder** (A9-05): ~20 section-header Texts missing `heading()`. | ~8 screen files in `ui/screens/**` (mechanical semantics). | grep count before/after; assembleDebug. | Very low — semantics-only. | A11y semantics only → no approval. | **APPROVE.** |

### BATCH 2 — NEEDS YOUR APPROVAL (flag named per item)

| ID | Sev | Item | Files | Verification | Risk | Changes | Disposition |
|---|---|---|---|---|---|---|---|
| R6 | S2 | **Dependency major bumps + verification metadata** (A6-04). No CVE was citable (honestly disclosed). | `gradle/libs.versions.toml`, `app/build.gradle.kts`, maybe proguard. | Compile + badging + R8 icon-map proof per bump, one bump per commit. | Medium-high (R8/shrink history; no device). | **Libraries (F2).** | **RECOMMEND: SKIP** — unproven payoff, uncatchable regressions. |
| R7 | S2 | **LazyColumn conversion sweep** (A7-03: 44 of 47 sites; LeaderboardScreen INTENTIONALLY deferred — server `take:50` caps rows, per A17-4 — recorded so nothing reads as dropped). | AdminPanel/AdminTournaments/Friends/Notifications (+sub-sections). | Compile + review; perf proof impossible without device (D14). | High blind (nested-scroll/sticky regressions). | **Behavior/perf (F2).** | **RECOMMEND: SKIP until device QA exists.** |
| R8 | S2 | **Chat delta-sync** (A7-04): additive `?after=<id>` on `src/app/api/friends/[id]/messages/route.ts` + ChatScreen poll. **A15-5 hazards adopted: the GET handler WRITES (marks messages read, route :31-34) and POST schedules BOT_REPLIES authored AS the friend (:70-77) — testing with real user ids corrupts real unread state and forges chat; there is no 304 (plain JSON 200, :47).** Mandatory safe sequence: (1) code on /home/z/my-project ONLY (A15-6: the audit worktree's src/ is STALE — its messages/route.ts lacks the A5-04 friendship gate and its DepositConfirmScreen.tsx still carries the pre-repair corruption — NEVER edit there); (2) verify against two disposable test accounts only, never real ids; (3) prove no-`after` ⇒ byte-identical response (backward compat) before anything else; (4) re-run A5-04 stranger-404 probe; (5) disclose residual test rows. | route.ts + ChatScreen.kt. | Sequence above (D11, controlled). | Medium (live social surface). | **Behavior + API surface (F2).** | **Approve only if chat load matters now; otherwise defer to Q17.** |
| R9 | S1 | **Test suites A12-01/04/05/06** (G2/G6 unreachable: 0 test sources). Per-ID prereqs (A17-5): A12-01 = testImplementation tier (JUnit4, coroutines-test, MockWebServer) + P0 golden tests (A12.md:29); A12-04 = pure-JVM Formatters/QrPayload/TournamentState/ApiResult values; A12-05 = gated on A4 validation-matrix output + hoisting file-private regexes; **A12-06 = Robolectric + Compose UI test (createComposeRule, fake NavEnv)**. | new `app/src/test/…`, `app/build.gradle.kts`. | The tests themselves (D7). | Low on app code; **workspace rule hard-blocks the agent from writing test code (F7 disclosure)**. | **Architecture + libraries (F2) + workspace escalation.** | **Approve in principle; execution needs another environment/agent, or an explicit override attempt (may still be blocked — will report honestly).** |
| R10 | S3 | **Full i18n string extraction** (A9-07): thousands of edits, 62 files. | ~every UI Kotlin file + strings.xml. | Compile; visual diff impossible. | High churn. | Design-neutral in theory. | **RECOMMEND: SKIP permanently (single-market app); route NEW copy through resources.** |
| R14 | S2 | **Dispose committed clutter (A15-3)**: b0bfd48 added ~600 non-building files — `.tmp-package/` 296 (incl. duplicate web `src/` 191 files + parallel Capacitor project), `tool-results/` 88 new, `AreenaxNativeAndroid/svgs/` 155, `agent-ctx/`; nothing references them (vault-gen is an orphan — no `apply(from=…)` in app/build.gradle.kts); A15 filename scan: no live secrets (only `*.example`). Duplicate source trees poison every future grep (D2) and invite wrong-tree edits (exactly what A15-6 caught). | `git rm -r` those paths + .gitignore additions. | Post-delete grep: zero dangling references; compile green. | Low — unreferenced; but it IS deletion → F2. | **File deletion → NEEDS APPROVAL.** | **RECOMMEND: APPROVE after R1** (do it inside the R1/R1b session). |
| R12 | S2 | **Admin data population** (A13-05/checklist §1): `settings.version`="1.0.0"; "Alphla Bank"→"Alfalah Bank". **A15-7 mechanics adopted: `depositAccounts` is ONE settings row holding "Name: number | …" (bootstrap/route.ts:13-28); the rename = GET→edit→POST of the WHOLE string (admin/settings/route.ts:193-202) — reconstructing from memory wipes every other method's account (all → "Not configured"; real-money support incident). Writes need FINANCE_ADMIN (depositAccounts) / SUPER_ADMIN (version) creds — the owner holds these. Client caches never expire (SettingsCache.kt:39 no TTL; web settings.ts:23) ⇒ verify via fresh /bootstrap only. Keep exactly ONE entry per method (case-variant overwrite, bootstrap/route.ts:22-25).** | None by agent unless owner says YES. | Fresh /bootstrap curl after cold start. | Real-money surface. | **Production data write (F2).** | **RECOMMEND: owner does it in the admin panel; if you want the agent to do it, say "agent may edit settings" + provide which admin credentials to use — then the exact safe sequence above runs.** |

### BATCH 3 — owner-only inputs (no agent code)

| ID | Sev | Item | Who |
|---|---|---|---|
| R2 | **S1** (relabeled per A17-6; release-gate consistent with R11) | **Production domain missing** (A13-01b, A11-04 hosting). Release fail-fast guard exists ONLY on the audit branch (app/build.gradle.kts:20-28, 74-86) — after R1b it gates main. `strings.xml:11` = the single edit point. | **OWNER supplies the domain.** Agent then sets it + probes `/delete-account`, `/privacy` (D11). |
| R11 | S1 (release gate) | **Keystore + offline backup** (Q14/checklist §5). | Owner (Android Studio). |
| R13 | RECORD | **Firebase Test Lab / billing — SKIPPED BY OWNER ORDER 2026-09-21.** Won't-fix(by owner). G6 device-matrix gates stay NOT VERIFIED. | Closed unless reopened. |

## 4. Recommended approve/skip summary (simple words)

- **Approve now (unblocks everything):** R1 Option A (revert the 25 broken files) + **R1b (merge the fix state into main)** + R14 (delete the 600-file clutter) — do these together in one session.
- **Approve (safe mechanics):** R3, R3b, R4 (documentation-only), R5.
- **Approve only if you want it now:** R8 (chat delta-sync — with the strict test-account rules), R12 (agent edits production settings — needs your YES + which admin creds).
- **Recommend SKIP:** R6 (dependency majors), R7 (LazyColumn sweep), R10 (i18n), R13 (Firebase — your order).
- **Only you can do:** R2 (domain), R11 (keystore), R12 (admin panel path).
- **Cannot be done in this workspace even with approval:** R9 (test code — platform rule; escalation recorded).

## 5. Dependency order if approved

R1 → R14 → **R1b (merge + freeze re-run + adversarial round)** → R3, R3b, R4, R5 (independent, one commit each) → R8/R12 only on explicit approval → Checkpoint 5 report.
R2/R11 slot in anytime after you supply the inputs. R6/R7/R10 stay skipped unless you overrule.

## 6. Verification-ladder honesty (applies to every batch)

No emulator/device (no KVM), no test sources ⇒ runtime pixels, animations, touch, TalkBack,
camera, performance = **NOT VERIFIED — no device/emulator in sandbox**. Agent proof ceiling:
D5 compile + D8 artifact inspection + D2 grep file:line + D11 read-only API probes (writes
only with your explicit approval). All fix execution happens on `/home/z/my-project` (main)
after R1b — the audit worktree's src/ is stale and is NEVER edited (A15-6).

## 7. Adversarial round — all 17 findings and their dispositions

| # | Agent | Sev | Finding | Disposition in this v2 |
|---|---|---|---|---|
| 1 | A15 | S1 | R1 revert list incomplete — 25-file diff; PushManager.kt also firebase-importing; no pre-existing file references added classes | Adopted: full 25-file revert scope in R1 |
| 2 | A15 | S1 | Option B "compiles" false — 4 missing fcm_* resources + no manifest service | Adopted: Option B marked NOT recommended with facts |
| 3 | A15 | S2 | ~600 committed clutter files; disposal recommendation | Adopted: new R14 (needs approval) |
| 4 | A15 | S3 | R4 premise no-op — M3 already enforces 48dp; resize contradicts "visuals unchanged" | Adopted: R4 re-scoped to verify-and-document |
| 5 | A15 | S1 | R8 live-DB testing writes production data (GET marks read; POST forges BOT_REPLIES); no 304 | Adopted: strict test-account sequence in R8 |
| 6 | A15 | S1 | R8/R12 tree ambiguity — audit worktree src/ stale (0 friendship checks; corrupted DepositConfirmScreen; "Alphla" remains) | Adopted: execution pinned to /home/z/my-project post-R1b |
| 7 | A15 | S2 | R12 mechanics: single-row full-string rewrite, FINANCE_ADMIN/SUPER_ADMIN, never-expiring caches, case-variant overwrite | Adopted verbatim into R12 |
| 8 | A15 | S3 | Server version default still "1.2.0" (bootstrap/route.ts:55, seed.ts:119) — ledger's A9-08 claim wrong | Adopted: new R3b |
| 9 | A15 | S3 | No manifest additions exist; PillField.kt mode-only; grep must cover both push files | Adopted: R1 wording + verification |
| 10 | A17 | S1 | Branch divergence unreconciled — 70 fixes exist only on audit branch; main lacks them | Adopted: new R1b (Batch 0) |
| 11 | A17 | S2 | Fail-fast guard is branch-local; R2 verification unexecutable on main pre-merge | Adopted: R1b gate + R2 wording |
| 12 | A17 | S2 | R3 under-scoped (pastels/brand colors) + Shadows.kt whitelist conflict | Adopted: R3 widened + whitelist |
| 13 | A17 | S3 | LeaderboardScreen omitted from R7 | Adopted: explicit deferral note |
| 14 | A17 | S3 | R9 per-ID prereqs incomplete (Robolectric/Compose for A12-06; A4 matrix gate for A12-05) | Adopted into R9 |
| 15 | A17 | S3 | R2 S0 vs R11 S1 inconsistent | Adopted: R2 → S1 |
| 16 | A17 | S3 | Order: R1-first correct; reconciliation slot missing | Adopted: §5 order |
| 17 | A17 | S3 | R1 enumeration add PushManager.kt | Adopted (= #1) |

A15 verdict: PLAN NEEDS CHANGES (9) → all incorporated. A17 verdict: PLAN NEEDS CHANGES (8) → all incorporated.
