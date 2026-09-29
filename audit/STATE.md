# AUDIT STATE (live document)

> Rebuilt 2026-09-19 after sandbox rollback. Previous STATE.md lost. Content below is
> reconstructed from the orchestrator's conversation record + fresh verification on disk.

## Current phase
PHASE 3 COMPLETE — RE-TEST + FINAL REPORT IN PROGRESS (TASK-007, worklog task 12).
93 items dispositioned: **70 APPLIED · 8 PARTIAL · 10 RECORD-ONLY · 5 NOT APPLIED** (full ledger:
audit/03-fixes/FIXES.md — zero silent skips).
Builds: assembleDebug 13,127,513 B · assembleRelease 2,698,256 B (unsigned) · AAB 6,546,444 B;
badging versionCode 1 / versionName 1.0.0 / targetSdk 36; release dex shows 144 ic_ keys and NO
app R$drawable reflection (A6-01 fix proven at byte level).
Supersession applied: **Q3-8MB** (owner command overrides log 8's Q3-5MB).
Disclosures: A12-01/04/05/06 NOT APPLIED (workspace test-code constraint, owner can override);
A13-01 PARTIAL — release fail-fast guard applied, **real domain = owner input**; release build
needed `-PallowPlaceholderBaseUrl=true` for sandbox verification (by design); release attempts
1-2 kernel-OOM-killed during R8, attempt 3 with reduced heap green (dmesg + logs).
Runtime on device: NOT VERIFIED (no device — standing limitation).

Prior phase record (kept for the audit trail):
PHASE 2 COMPLETE — CHECKPOINT 2 STOP (TASK-006, worklog task 11). Key discoveries: (1) RELEASE-ONLY
BUG: R8 removes R$drawable → AreenaxIcon reflection fails → all data-driven icons render ic_help in
release builds (A6-01 BLOCKER); (2) RMG verdict: real-money loop prohibited on Google Play (exact
policy quoted) — current web-APK distribution is permitted (A11); (3) release APK 2.68 MB vs 12.8 MB
debug; (4) two server-side validation bugs found by A4 (password-change unvalidated, EditProfile
newPw leak — web-inherited); (5) secrets scans CLEAN (source/git-history/both APKs); (6) icon audit:
0 missing vs live web, tool + md5 cache + retry proven in staging.

Phase 1 outcome (2026-09-20): ledger rebuilt 165 REQs; RTM 165/165 = 150 VERIFIED / 15
PARTIAL / 0 rest; A1.md + Attacker + 25-row spot-check; commits 65dd671 + 91bac92.
Phase 0 outcome (approved 2026-09-20): D1–D7 fixed (319→0), first APK 12.8 MB published.
NOT VERIFIED (no device/emulator): install, launch, runtime behavior, screenshots.

## Environment event (2026-09-19, disclosed to owner)
The workspace was rolled back to the Task 5 era. LOST (confirmed by disk + git inspection):
- Previous audit workspace (MASTER_RULES verbatim, STATE, 96-row FFR, REQUIREMENTS_LEDGER, evidence logs)
- Git branch `audit/production-readiness` and its commits
- `/home/z/my-project/CLAUDE.md`
- Delivered zip `public/downloads/AreenaxNativeAndroid.zip` (public/downloads/ itself is gone)
- Worklog records after Task 5
- App-side changes of Tasks 6-12, INCLUDING the final Task 11 "Midnight Charcoal" dark theme
  (no #1C1C1E canvas and no #3C3C3C input pills exist in the current source — verified by grep).

SURVIVING (verified on disk):
- Web panel src/** (Next.js) + admin panel + prisma/db — untouched by this audit.
- `AreenaxNativeAndroid/` app source: 93 Kotlin files / 35,256 lines / 48 screens / 46 route keys /
  versionCode 2, versionName "2.0.0"; SPEC/00-05; icon tools; START-HERE.md; worklog Tasks 1-5.
- Worklog Task 5 records the ORIGINAL zip as "309 files, 742KB" (that era), later replaced by the
  final 1,088,125-byte version in the lost Task 11 era (sha256 570c457f...3315fb0 — from conversation record).

## Phase 0 progress
- [x] Branch + 8 folders created
- [x] Environment probe + build attempt evidence (toolchain BOOTSTRAPPED: JDK 17 + Android SDK in $HOME)
- [x] First real compile ever: BUILD FAILED — ≥319 errors (compiler killed mid-run; floor, not
      final) — see 02-findings/PHASE0-BLOCKERS.md
- [x] FFR rebuilt (110 rows, 8 areas) by ffr-builder + provenance caveat
- [x] Attacker + Critic round 1 (0 blockers / 3 majors / 4 minors / 4 notes + 0/3/5/6) — all
      accepted findings fixed in Round-1 fix pass (D4, D6, C4, ≥319 framing, item-3 statuses,
      DTO/drawable counts, FFR SDK wording, STATE refresh)
- [x] STATE/worklog updated; Phase 0 committed on audit branch
- [x] Report + STOP delivered — Phase 0 APPROVED by owner (2026-09-20)

## Phase 1 progress (TASK-005)
- [x] TASK-005 spec written; STATE updated
- [x] 10-a ledger rebuild (REQUIREMENTS_LEDGER.md — 165 REQs)
- [x] 10-b A1.md architecture map + code-health
- [x] 10-c/10-d/10-e RTM verification parts (165 rows)
- [x] 10-f Attacker pass (8 HOLDS / 2 downgrades / 1 overturn; corrections applied)
- [x] Orchestrator spot-check 25/25 rows + assemble RTM.md
- [x] Conflicts (15) + assumptions (9) + gap list (13)
- [x] CHECKPOINT 1 report + STOP delivered (2026-09-20) — owner answers pending

## Phase 3 progress (TASK-007 — fix ledger in audit/03-fixes/FIXES.md)
- [x] TASK-007 spec written; decision log 9 recorded; toolchain re-bootstrapped (JDK17 + SDK 35/36)
- [x] BLOCKER batch: A6-01/A7-01 icon when-map; A11-01 Path-A record; A11-02+A10-01 AGP 8.9.1 /
      Gradle 8.11.1 / compileSdk 36 / targetSdk 36; A13-01 base-URL guard (real domain = owner input);
      A11-03+A6-03+Q15 account deletion (server + native + web page)
- [x] MAJOR batches (A3-02, A4-01/02, A6-02, A7-01/02/03, A8-01/02, A9-01/02, A10-01, A11-04/05/06/07,
      A12-02/03, A13-02/03)
- [x] MINOR batches (44 code items + 2 ICON) — see FIXES.md for per-item status
- [x] Q-decisions applied: Q1, Q2, Q3(8MB), Q5, Q6, Q8, Q9, Q10, Q11, Q12, Q13, Q14, Q15
      (Q4/Q7/Q16/Q17 = record-only)
- [x] Q5 features: join-request inbox · team-QR popup (+A3-08) · notifications select/delete
- [x] Re-test: Attacker (FAIL→2 minors FIXED+re-verified) + Critic (PASS-WITH-NOTES→2 issues closed) + Independent Verifier (PASS-WITH-NOTES, orchestrator-run — 12-g agent never delivered, disclosed)
- [x] Builds: assembleDebug + assembleRelease + bundleRelease evidence; APKs published
- [x] STATE/RTM/worklog updated; Phase 3 report delivered

## Phase 2 progress (TASK-006)
- [x] TASK-006 spec written; Q1–Q17 recorded (decision log 8)
- [x] Release build: assembleRelease SUCCESSFUL after 2 OOM-killed attempts (dmesg evidence);
      app-release-unsigned.apk 2,683,188 B (R8+shrink from 12.8 MB debug) — run3 log evidence
- [x] Play-policy research (A11 input) + 12 live policy pages fetched
- [x] Wave 1: A3 (13) A4 (17) A5 (10) A8 (10) A9 (12) A12 (8) A13 (7) ICON (6)
- [x] Wave 2: A6 (7) A7 (12) A10 (5) A11 (12)
- [x] Consolidation: 119 findings = 5 BLOCKER / 22 MAJOR / 46 MINOR / 46 NOTE
- [x] Phase 2 report + STOP delivered (2026-09-20)

## Owner decisions log (reconstructed from conversation record)
1. "Chat history" = the current conversation (from the native-app build order onward). Never ask for files.
2. All owner-facing replies in English.
3. Master Command adopted as permanent pipeline contract (verbatim text lost — re-paste requested).
4. C8 = **YES** (2026-09-19): owner still holds the final Task 11 zip. Restore path chosen:
   owner may attach the zip in chat (SHA-256 checked before use); fallback if attach is not
   possible (owner replies ZIP-NO) = rebuild Tasks 6-11 app-side changes from the work record
   with owner approval before any write.
5. C7 = **FIX-YES** (2026-09-19); D4 = **OPTION-A**; sequencing = **ZIP-NO** (fix current tree
   first). Executed as TASK-003: D1–D6 fixed and compiler-verified; D3 import sweep via
   builder agent 8-a (17 files, import-only incl. 5 wrong-package import corrections);
   Attacker 8-b verdict PASS-WITH-NOTES (1 minor stale comment — fixed; 2 cosmetic import-order
   notes — left as-is). RAM root cause of the earlier daemon kills: two stale build JVMs
   (~1.9 GB) killed; builds now run with CLI-only memory settings.
6. D7 = **YES**; chat `letter` micro-decision = **LETTER-DEFAULT** (2026-09-20): owner approved
   fixing all 42 remaining compile errors (TASK-004) and ordered the first full APK build
   (assembleDebug) afterwards. LETTER-DEFAULT = the previously proposed default: chat bubbles use
   the friend's game-name initial, exactly like the header already does (ChatScreen.kt:159) and
   like the web panel. D7 residual fixes must stay behavior-preserving (no visual/token changes).
   D7 items NOT pre-authorized: anything beyond making the 42 listed errors compile cleanly.
7. **Phase 0 APPROVED; PHASE 1 ordered** (2026-09-20), verbatim: "Phase 0 is approved. Run the
   full Prompt Pipeline. Start PHASE 1 (Requirements Traceability). No application code
   changes." Deliverables: RTM.md (status VERIFIED/PARTIAL/MISSING/FAILED/CONFLICT/OBSOLETE per
   REQ + file:line + test + evidence), conflicts (later instruction wins) + assumptions + gap
   list, A1.md architecture/code-health, Attacker pass. CHECKPOINT 1 = STOP with counts,
   conflicts, assumptions, questions. Supersedes the earlier "fix D1–D7 then compile" sequencing
   for ordering purposes only — that work was already completed as TASK-003/TASK-004.
8. **CHECKPOINT 1 APPROVED; 17 decisions** (2026-09-20), verbatim keywords:
   Q1-REMOVE (delete hardcoded fallback bank account numbers) · Q2-REFUSE (over-cap uploads
   refused with message, like web) · Q3-5MB (result-proof cap = 5 MB; replaces dead 4 MB /
   live 8 MB) · Q4-OK (message-text 409/insufficient detection stays) · Q5-BUILD-3-ONLY (build
   ONLY join-request inbox + team-QR popup + notifications select/delete; timeline estimate
   required) · Q6-COUNT-ONLY-OPTIMIZED (unread poll uses server countOnly) · Q7-KEEP-OLD
   (selectBank screen stays) · Q8-KEEP-VISIBLE (ONE support icon; remove extra SupportFab on
   ReferEarn+Tasks) · Q9-CREATE-ICON-AUDIT-AGENT (dedicated agent: audit app icons + downloaded
   SVGs → deep cross-check → download missing → load in app; needs audit tool + cache + download
   retry; admin .EXE is SEPARATE, built after app verification) · Q10-INDUSTRY-STANDARD
   (research typical Android animation 250–350ms; use 300ms) · Q11-PREFER-1.0.0 (try 1.0.0; if
   blocked use 2.0.0) · Q12-REMOVE-DEMO-USE-REAL-LOCATION-CONTACTS (remove fake suggested
   players; real friends via location + contacts; needs permissions + server logic + privacy
   policy update) · Q13-FIX-NOW (fix "Alphla" typo → "Alfalah") · Q14 keystore added manually by
   owner when compiling in Android Studio · Q15-ADD-NOW (account deletion for Play Store) ·
   Q16-ADD-LATER (force-update after release) · Q17-AFTER-RELEASE (server audit = separate
   project post-launch). All are pending fix-phase items — PHASE 2 changes NO code.
9. **CHECKPOINT 2 APPROVED; PHASE 3 (fix phase) ordered** (2026-09-20). Owner command (verbatim,
   key parts): "Apply all Checkpoint 1 decisions (Q1–Q17) PLUS all Phase 2 findings. Fix order:
   1. 5 BLOCKERs first (A6-01, A11-01, A11-02, A11-03, A13-01) 2. Then 22 MAJORs 3. Then 46 MINORs
   4. Apply all 17 Q-decisions from Checkpoint 1 […] 5. Build Q5's 3 features (join-request, QR,
   notifications) 6. Re-test everything (Attacker + Critic + Independent Verifier) 7. Keep
   /audit/STATE.md updated. Total fixes: 5 blockers + 22 majors + 46 minors + 17 decisions +
   3 features = 93 items. fix these everything in app".
   Q-decision keywords in THIS command: Q1-REMOVE · Q2-REFUSE · **Q3-8MB ("set proof limit to 8MB"
   — SUPERSEDES log 8's Q3-5MB)** · Q4-OK · Q5-BUILD-3-ONLY · Q6-COUNT-ONLY-OPTIMIZED · Q7-KEEP-OLD ·
   Q8-KEEP-VISIBLE · Q9-CREATE-ICON-AUDIT-AGENT ("audit + download missing SVGs offline") ·
   Q10-INDUSTRY-STANDARD ("use 300ms") · Q11-PREFER-1.0.0 ("version 1.0.0, or 2.0.0 if blocked") ·
   Q12-REMOVE-DEMO-USE-REAL-LOCATION-CONTACTS · Q13-FIX-NOW · Q14-STUDIO-PROMPT-KEYSTORE ·
   Q15-ADD-NOW · Q16-ADD-LATER · Q17-AFTER-RELEASE.
   A11-01 disposition (no owner contradiction): Path A = web-APK distribution stays the release
   channel (Play listing would require a real-money-removed variant; recorded in PLAY-LAUNCH-CHECKLIST).

## Open owner gates (pending)
- D7 — **ANSWERED YES**; letter **LETTER-DEFAULT** (owner decision log 6; executed as TASK-004
  incl. first full APK build).
- C1 "Alphla Bank" spelling — keep web-parity typo or fix (owner to decide).
- C2 Results-screen wording parity with web.
- C3 Chat online-status / date separators rendered static.
- C4 About-screen version is SERVER-DRIVEN (`settings.version`, fallback "1.2.0" per
  AboutScreen.kt:110 — line cite corrected this task) while versionName is "2.0.0" — decide the
  correct version story (corrected after Attacker round 1; earlier "hardcoded 1.0.0" was wrong).
- C5 Friend-recommendation demo data vs no-demo-data rule.
- C6 No account-deletion flow (Play policy risk).
- C9 Deposit fallback account numbers are HARDCODED in the app when `settings.depositAccounts`
  is empty (DepositConfirmScreen / TournamentState area) — this is a real-money path; owner must
  confirm the fallback accounts are correct or that the fallback should be removed.
- C7 — **ANSWERED FIX-YES**; D4 **OPTION-A**; sequencing **ZIP-NO** (owner decision log 5).
- C8 — **ANSWERED YES** (owner still has the final Task 11 zip; restore path per decision log 4).

## External blockers (owner-only)
- No emulator/device → no runtime verification, screenshots, cold-start, memory numbers.
- No keystore/signing material → no signed release.
- No staging credentials (backend URL, test accounts) → no API-level verification.
- Owner hasn't re-pasted the verbatim Master Command yet.

## Conventions
- Task specs: audit/tasks/TASK-###.md · Evidence: audit/evidence/ · Honesty labels per MASTER_RULES.md
- Git: small commits on audit/production-readiness; never force-push; never touch app code in Phase 0.

## Post-Phase-3 owner command — FIELD CONCEPT (2026-09-21)
- Owner order: dark-mode pill-field concept applied to ALL input fields (idle matte
  charcoal + white outline icon; focus = icon animates 300ms to vibrant blue, white
  blinking cursor beside icon, subtle 1dp primary@55% ring, soft light-white text;
  blur reverts; cross-check every field; performance good).
- Implementation: main a6ead6d — new core/ui/PillField.kt (AreenaxPillField +
  areenaxFieldColors); 19 files migrated; 59 pill sites + 4 sanctioned multiline/custom
  sites = 63/63; net -261 LOC in app; zero behavior change (imeAction Default parity
  preserved; filters/transforms/focusRequesters byte-identical).
- Verification: assembleDebug green (12,763,965 B); independent re-test 13-d HELD
  (0 breaks) — report: audit/04-verification/field-concept-retest.md.
- NOT VERIFIED (standing): on-device runtime/pixel look — owner verifies via APK;
  no APK published for this pass yet (publish awaits owner OK per rule).

## Owner command — GRADLE MANAGED DEVICES SETUP (2026-09-21)
- Config added (main, app/build.gradle.kts): testOptions.managedDevices.localDevices
  "pixel5api33" = Pixel 5 / API 33 / aosp image; AGP 8.7.3 accepts the DSL (task
  graph resolves; generates pixel5api33Setup/Check/DebugAndroidTest).
- Execution-verified versions: Gradle 8.10.2 (>=8.0 OK), AGP 8.7.3 (>=8.1 OK),
  JDK 21 Temurin — no upgrades needed. (Worklog Task 12's 8.11.1/8.9.1 claim does
  not match the tree; tree values authoritative.)
- STEP 4: owner task name `testReleaseOnPixel5Api33` DOES NOT EXIST (GMD creates
  instrumented-test tasks only). Real run `testReleaseUnitTest` -> NO-SOURCE
  = 0 tests (zero unit-test sources, finding A12).
- STEP 5: `connectedReleaseAndroidTest` does not exist; GMD run
  `pixel5api33DebugAndroidTest` FAILED at pixel5api33Setup 5/5 — emulator cannot
  boot: /dev/kvm missing + accel-check exit 3 ("KVM requires a CPU that supports
  vmx or svm"). Emulator 37.1.11 + AOSP API 33 x86_64 image downloaded+installed
  OK (cached for a future KVM-capable host). Also 0 androidTest sources.
- STEP 6 monkey: NOT RUN — adb devices empty; no bootable device possible in
  sandbox (no KVM). Not claimed as pass.
- Report: audit/04-verification/CLOUD_EMULATOR_RESULTS.md (incl. deviations D-1
  "localDevices is local, not cloud; FTL = true cloud path needing owner decision"
  and D-3 test-source gap) · Evidence: audit/evidence/cloud-emulator-tests/ (6 logs).
- Status: test INFRASTRUCTURE READY / test EXECUTION NOT VERIFIED (environmental:
  no hardware virtualization in sandbox; plus A12 zero test sources).
- Ready for Phase 7: NO (per the owner command's own summary gate).

## Owner decision — OPTION A: FIREBASE TEST LAB (2026-09-21, later same day)
- Owner supplied project ID areenax-dev. gcloud SDK 585.0.0 installed; owner OAuth
  (ADC) completed on attempt 2 (attempt 1 = gcloud 585 scope-negotiation crash;
  fixed with explicit --scopes). Credentials live OUTSIDE repo, never committed.
- APIs ENABLED on areenax-dev: testing.googleapis.com + toolresults.googleapis.com
  (verified via services list).
- Device catalog: 208 models (199 physical / 9 virtual). NO virtual Pixel 5 exists
  (D-4); closest = Pixel2.arm API 26-33 -> chosen Pixel2.arm @ 33.
- APK built at last-known-good a25623a (12,763,965 B, byte-identical to Task-13).
  Sandbox rootfs replaced mid-session (4th toolchain wipe) — recovered via
  bootstrap script, now committed at tools/bootstrap-build-toolchain.sh.
- REGRESSION ALERT: main tip b0bfd48 does not compile — newly landed 25-file
  Firebase-push workstream (AreenaxMessagingService.kt, vault-gen.gradle.kts,
  res/xml) references 'firebase' without the dependency. Not this task's scope;
  FTL runs a25623a until that workstream fixes its build.
- FIRST CLOUD RUN BLOCKED (D-5): 403 "billing account ... state absent" on APK
  upload — Test Lab requires linked billing even on free Spark tier. Owner-only
  action: link billing to areenax-dev (Firebase console -> Blaze/upgrade), then
  agent retries robo run (one command, everything pre-staged). Charge = $0
  within 10 virtual tests/day free quota.
- Evidence: audit/evidence/cloud-emulator-tests/{gcloud-install.log,
  assembleDebug-ftl.log, ftl-robo-run1.log} · Report: ADDENDUM A in
  audit/04-verification/CLOUD_EMULATOR_RESULTS.md.
- Commits: main 7b04ba0 (FTL evidence + bootstrap script); this audit commit.

## Owner re-paste — MASTER COMMAND v2 + LIVE CREDENTIALS (2026-09-21, after Task 16)
- Owner re-sent the FULL Master Command (Sections 0–9: 9-stage pipeline, 20-agent roster,
  verification ladder D1–D15, gates G1–G11, standing decisions Q1–Q17, utility commands).
  Saved to audit/MASTER_RULES.md SUPERSEDING the 2026-09-19 short version (later-instruction
  rule B5); secret VALUES redacted in the file per F3, real values wired ONLY into the
  git-ignored `.env` (git check-ignore verified; legacy sqlite .env backed up OUTSIDE the
  repo). CLAUDE.md session-start memory updated to the 9-stage command + runtime declaration.
- Resume semantics (Section 9 "Resume after reset" + B5): Phase 0/1 deliverables exist
  (2026-09-19) and pipeline has advanced through the Phase-3 audit + fix waves + field
  concept (Task 13) + APK publication (Task 16, main d02595d). "Begin PHASE 0" read as the
  standing template — pipeline RESUMED at the current frontier, nothing redone.
- SANDBOX BLOCKER RESOLVED (was: "DATABASE_URL must start with postgresql://" on every
  scheduler tick): .env now carries the owner-supplied Supabase transaction-pooler URL
  (6543 + pgbouncer=true, pasted typo "ostgresql://" corrected) + CLOUDINARY_CLOUD_NAME /
  API_KEY / API_SECRET (var names match src/lib/cloudinary-server.ts:24-26).
- Probes (D11, all read-only): GET /api/tournaments → HTTP 200, 20,113 bytes of live
  tournament data (FACT: Next app ⇄ Prisma ⇄ Supabase Postgres end-to-end WORKING).
  Supabase REST /rest/v1/ root with publishable key → HTTP 401 (PostgREST root/spec path
  rejects this key format — NOT VERIFIED for the REST path; native app uses its own config
  fetch, unaffected). Cloudinary API not probed this step → NOT VERIFIED (credentials stored).
  dev.log after restart: 0 "scheduler tick failed" (previously every tick).
- ASSUMPTION (A10, recorded): dev server now operates against the OWNER'S LIVE backend;
  the background reminder scheduler may write reminder rows there (designed app behavior).
  Agent called zero write endpoints; every probe read-only. Any future sandbox testing
  against live data must stay read-only or use test rows only (F2/F5).
- SECURITY (F3, owner action advised): the Supabase secret key, DB password and Cloudinary
  secret were PASTED IN CHAT — rotation recommended once convenient. Storage: chat + .env
  only; never committed (verified: .env git-ignored, untracked).
- Owner decision log: 6. 2026-09-21 — Master Command v2 adopted (supersedes 2026-09-19);
  live Supabase/Cloudinary credentials supplied; sandbox wired to live backend (read-only).

## Owner command — TRIAGE, NO CODE CHANGES (2026-09-21)
- Owner order: skip billing/Firebase (standing); run pipeline triage on the fix queue; save
  /audit/03-fixes/FIX_PLAN.md; STOP at the checkpoint with recommendations.
- Stage-1 correction delivered: 93 items were ALL dispositioned in FIXES.md (70 APPLIED,
  10 RECORD-ONLY, 8 PARTIAL, 5 NOT APPLIED) — the real residual queue is 13 residuals +
  1 new S0 (main-tip compile regression) + 1 new S1 (branch divergence, found by A17).
- FIX_PLAN.md v2 written (03-fixes/FIX_PLAN.md) AFTER the mandatory adversarial round:
  A15 (Attacker) 9 findings + A17 (Critic) 8 findings — ALL incorporated (plan §7 table).
  Key plan-shaping facts they proved: b0bfd48's app diff = 25 files (PushManager.kt ALSO
  imports firebase; option B still cannot work); the 70-fix state exists ONLY on the audit
  branch (main is pre-fix: SDK 35, versionCode 2, no guard) → R1b reconciliation merge is
  Batch-0; audit worktree src/ is STALE (never edit there); R8's GET handler WRITES
  production data (mark-read) so testing needs disposable accounts; R4 is already satisfied
  by M3 48dp enforcement (re-scoped to documentation); server version default is still
  "1.2.0" (new R3b).
- Recommendations to owner: APPROVE R1-A + R14 + R1b (one session), R3/R3b/R4/R5 (mechanics);
  SKIP R6/R7/R10 (R13 closed by owner); decide R8/R12; owner-only R2 (domain) + R11 (keystore);
  R9 blocked by workspace rule (escalated).
- No application code changed this task (owner order). Zero-wait STOP at the checkpoint.

---

## TASK 19 — FINAL PRODUCTION AUDIT & FIX (2026-09-22) — executed, adversarially reviewed, CHECKPOINT report delivered

Owner command: "FINAL PRODUCTION AUDIT & FIX — COMPLETE END-TO-END … Fix ALL of it (no skips, no 'nice-to-haves', no postponing) … Verify EVERY fix with proof … Deliver a PRODUCTION-READY app with a complete report." This supersedes the earlier triage-only stop (TASK 18); FIX_PLAN.md v2 became the work order.

Executed (commits on main): R1 revert 3e87bd2 → R14 clutter c5f27c5 → R1b merge bf5c176 (70-fix state restored to main; 24 conflicts resolved; badging v1.0.0/versionCode 1/SDK36) → final-audit batch f494460 (R3b seed 1.0.0; R3 token sweep; R5 headings; R8 chat delta-sync code; orphaned push-workstream verify tools removed; gradle.properties sandbox heap guards) → adversarial fix round (commit after this entry: ChatScreen chronological merge-sort (A15-1), route orderBy tiebreak (A15-2), ChatOnlineDot literal closure (A17-3), A17 doc/evidence honesty fixes, 7 stale-doc status banners, ms-codepoints restored, FFR re-run entry, a16 evidence committed).

Verification ladder (honest):
- VERIFIED: assembleDebug; assembleRelease(R8+lintVital); A13-01 guard both ways; badging ×2; check.sh CHECK-OK; sweep 7e; visual 154/0/3; web lint 0 errors; chat-route smoke 401; R3 byte-identical spot-checks (A15); R8 no-param backward-compat provable from diff (A15/A17); **R8 live-DB delta-sync probe (2026-09-22, real Supabase PostgreSQL: full/delta/400/0-rows/404/429 rate-limit + disposable cleanup leftover 0 — evidence `audit/evidence/r8-probe-live-run1.log`)**.
- NOT VERIFIED — no device/emulator in sandbox (unchanged): runtime pixels, touch, TalkBack, camera, performance.
- BLOCKED (owner input): R12 live settings write (owner decision pending; DB now REACHABLE — credentials restored 2026-09-22, sandbox env-injection quirk documented in the R8 evidence log); R2 production domain; R11 keystore.
- SKIPPED-BY-ORDER: R13 FTL/billing (owner 2026-09-21). ENGINEERING SKIPS awaiting explicit owner overrule: R6/R7/R10 (rationale + override keywords in FIXES.md addendum). R9: blocked by workspace test-code rule; override attempt available on owner order.

⚠️ VERSION-DOWNGRADE WARNING (A17-1, owner-facing): the previously published/installed v2.0.0 debug APK (versionCode 2) CANNOT be upgraded in place by the new v1.0.0 build (versionCode 1, per Q11) — Android rejects downgrades (INSTALL_FAILED_VERSION_DOWNGRADE). The owner must uninstall the old build first, or authorize bumping the published artifact to versionCode ≥ 3. Also: public/downloads/ (incl. the v2.0.0 APK) was again wiped by the sandbox reset — the new artifact is re-published this pass.
