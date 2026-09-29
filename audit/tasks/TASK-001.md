# TASK-001 — Phase 0: Recon & Freeze (fresh run after sandbox rollback)

- Worklog Task ID: 6
- Owner prompt: "Run the full Prompt Pipeline. Start PHASE 0 (Recon & Freeze). Do NOT change application code in this phase. 1) branch + folders. 2) write down structure/toolkit/architecture/screens/routes/services/receivers/workers/deep links/permissions/libraries/variants/backend/SDK levels. 3) run clean build (debug and release), lint, existing tests; save results. 4) record baseline size/cold start/memory/screenshots. 5) Feature Freeze Register. 6) environment: what can and cannot be tested. 7) list missing information. Attacker and Critic must review Phase 0 output before report. STOP and wait."

## Goal
Produce the complete Phase 0 evidence package on the branch `audit/production-readiness`
WITHOUT modifying any application code.

## Critical context discovered during this task (disclose in report)
The sandbox was ROLLED BACK to the Task 5 era (initial packaging). Lost: the previous
audit workspace, MASTER_RULES.md (verbatim), STATE.md, the 96-row FFR, REQUIREMENTS_LEDGER.md,
CLAUDE.md, the delivered zip, git branch + commits of the audit era, worklog Tasks 6-12,
and the app-side changes of Tasks 6-12 (incl. the final "Midnight Charcoal" dark theme of Task 11).
Surviving truth on disk: app source = 93 Kotlin files / 35,256 lines / 48 screens / 46 route keys /
versionCode 2 ("2.0.0"), SPEC/00-05, worklog Tasks 1-5, web panel src/**.

## Tasks
1. Create branch `audit/production-readiness` + 8 folders (00-baseline, 01-requirements,
   02-findings, 03-fixes, 04-verification, 05-report, evidence, tasks). DONE.
2. Recon: BASELINE.md + RUNTIME-ENVIRONMENT.md from actual files (manifest, gradle, sources).
3. Build: bootstrap JDK 17 + Android SDK into $HOME (no root), attempt debug build;
   probe latent blockers in a THROWAWAY COPY at /tmp/buildprobe (project untouched).
4. Baseline metrics/screenshots: impossible (no device/emulator) — record honestly.
5. FEATURE-FREEZE-REGISTER.md rebuilt from code + SPEC via ffr-builder agent.
6. MISSING-INPUTS.md + REQUIREMENTS-SOURCE-STATUS.md.
7. Attacker + Critic review round 1 (read-only), then fixes, evidence, STATE/worklog/commit, report, STOP.

## Acceptance
- [x] Branch + folders exist
- [x] Baseline docs complete and accurate to the on-disk source (corrected per Attacker round 1)
- [x] Build attempt evidence saved (toolchain bootstrapped; ≥319 compile errors — floor, daemon killed)
- [x] FFR rebuilt (110 rows, 8 areas + quirks) and reviewed
- [x] Attacker + Critic round logged and all accepted findings fixed
- [x] STATE.md + worklog Task 6 updated; committed on audit branch
- [x] Report ends with Pipeline Report + NEXT STEP FOR YOU + STOP

## MUST NOT change
- `AreenaxNativeAndroid/app/**` (all Kotlin, res, manifest, gradle files)
- `src/**`, `admin-panel/**`, `prisma/**`, `db/**`, `AreenaxNativeAndroid/SPEC/**`, worklog history
- Only NEW files under `AreenaxNativeAndroid/audit/**`, `CLAUDE.md`, `worklog.md` (append) may be written.

## Agents
- ffr-builder (general-purpose): rebuild Feature Freeze Register (writes only the FFR file).
- attacker + critic (general-purpose, read-only): review round 1.

## Round log
- Round 1 (Attacker 6-c + Critic 6-d, both read-only):
  - Attacker: 0 blockers / 3 majors / 4 minors / 4 notes. Decisive catches: (1) D4 root cause
    WRONG — material3 1.3.1 contains NO fixed color roles anywhere (verified byte-level on the
    resolved artifact); proposed .copy() fix would also fail; real blast radius 6 sites.
    (2) "319 errors" is a FLOOR — Gradle daemon killed mid-run, only 51/93 files reached.
    (3) C4 misstated — About version is server-driven `settings.version ?: "1.2.0"`, not
    hardcoded 1.0.0. Minors: D6 misdiagnosed (scope bug, not declaration), DTO count 116 not 118,
    154 vectors + 1 PNG not 155 vectors, Phase 0 uncommitted. ATTACKS THAT HELD: all counts,
    manifest facts, D1/D2/D3/D5, FFR spot-checks (all 6 resolved), zero app-code drift.
  - Critic: 0 blockers / 3 majors / 5 minors / 6 notes, grade B+. Decisive catches: owner item 3
    one-third silent (release/lint/tests statuses missing), same ≥319 framing issue, everything
    uncommitted against rollback risk. Minors: FFR stale "no SDK" wording, STATE stale,
    honesty-vocabulary mapping, D7 fuzziness, bootstrap evidence log, FFR provenance caveat,
    D4 re-verify request (resolved: Attacker's artifact-level check stands), C9 surfaced
    (hardcoded deposit fallback accounts), ledger sequencing needs owner blessing.
  - FIX PASS: all accepted findings applied to BLOCKERS/BASELINE/STATE/MISSING-INPUTS/
    RUNTIME-ENVIRONMENT/FFR; toolchain-bootstrap log saved; commit made on audit branch.
- Round 2: NOT NEEDED for doc-level findings; the compile fix loop itself awaits owner approval
  (Phase 0 forbids app-code changes).

## Evidence
- evidence/phase0-env-probe-2026-09-19.log
- evidence/assembleDebug-attempt-2026-09-19.log (JRE-only failure, pre-bootstrap)
- evidence/assembleDebug-run1-2026-09-19.log (real toolchain; failed at colors.xml)
- evidence/compile-probe-run1-2026-09-19.log (throwaway copy; ≥319 errors — floor, daemon killed)
- evidence/toolchain-bootstrap-2026-09-19.log
