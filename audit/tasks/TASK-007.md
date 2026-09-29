# TASK-007 — PHASE 3: FIX PHASE (owner command 2026-09-20)

## Owner command (verbatim, abridged only where repeated)
"Apply all Checkpoint 1 decisions (Q1–Q17) PLUS all Phase 2 findings. Fix order:
1. 5 BLOCKERs first (A6-01, A11-01, A11-02, A11-03, A13-01)
2. Then 22 MAJORs
3. Then 46 MINORs
4. Apply all 17 Q-decisions from Checkpoint 1: [Q1-REMOVE … Q17-AFTER-RELEASE]
5. Build Q5's 3 features (join-request, QR, notifications)
6. Re-test everything (Attacker + Critic + Independent Verifier)
7. Keep /audit/STATE.md updated
Total fixes: 5 blockers + 22 majors + 46 minors + 17 decisions + 3 features = 93 items.
fix these everything in app"

Note on Q3: this command says **Q3-8MB** ("set proof limit to 8MB"), superseding
STATE decision log 8's "Q3-5MB". Later instruction wins → proof cap = **8 MB**.

## Constraints
- Fix order respected: BLOCKERs → MAJORs → MINORs → Q-decisions → 3 features → re-test.
- Every fix keeps web parity unless the finding/decision itself changes it (Q2/Q3/Q10/Q11/Q12…).
- One finding may be fixed by another's change (e.g. A7-01 = A6-01; A5-02 = A3-07 = A7-07;
  A6-03 = Q15; A4-04 = Q6; A8-03 = Q10; A9-08 = Q11; A3-08 = Q5 team-QR; A5-06 = A3-04 overlay;
  A10-01 = A11-02 chain; A9-11 = Q12 privacy copy). Ledger records the cross-map once.
- Q4-OK / Q7-KEEP-OLD / Q16-ADD-LATER / Q17-AFTER-RELEASE / A11-01 (Path A) = recorded
  decisions, zero code change.
- Environment constraint (disclosed): this sandbox's build agent cannot write test code
  (hard rule of the workspace) → A12-01/A12-04/A12-05/A12-06 are **NOT APPLIED (constraint)**
  with exact remediation steps documented for the owner; A12-02 → tools/check.sh; A12-03 →
  applied only if the TokenProvider seam is trivially verifiable, else deferred with reason.
- Honesty labels per MASTER_RULES.md; every status carries evidence or a NOT VERIFIED reason.
- Small commits on audit/production-readiness; never force-push; app code changes ONLY in
  AreenaxNativeAndroid/**, web/server changes ONLY in src/** (mirrored to main tree so the
  live panel picks them up).
- ≤3 fix attempts per problem, then STOP that item and record.

## Build gates
- After each batch: `./gradlew :app:compileDebugKotlin` (fast gate).
- End of phase: `assembleDebug` + `assembleRelease` + `bundleRelease` (AAB proof for A11-09),
  dex probe to confirm icon fix, badging check (targetSdk 36, versionName 1.0.0).
- Toolchain: re-bootstrapped this phase (sandbox rollback lost JDK 17 + SDK). JDK 17 + build-tools
  35.0.0 + platforms 35 AND 36 (36 needed by A11-02 fix). Gradle wrapper 8.10.2 → 8.11.1, AGP
  8.7.3 → 8.9.1 per A10-01.

## Deliverables
- `audit/03-fixes/FIXES.md` — ledger of all 93 items with status + evidence per item.
- `audit/03-fixes/PLAY-LAUNCH-CHECKLIST.md` — owner-side items (A11-05/07/08/10, A13-04 item 3,
  A13-05, A11-01 Path-A record, A13-02 vitals note, A13-03 runbook).
- Updated STATE.md, RTM.md (PARTIAL rows re-verified), START-HERE.md (Q11/Q14/A13-01 notes).
- Re-test: Attacker + Critic + Independent Verifier reports under audit/04-verification/.
- Rebuilt APKs (debug + unsigned release + AAB) published to public/downloads/ (main tree).

## Acceptance
1. All 5 BLOCKERs applied or explicitly decision-resolved with evidence.
2. All 22 MAJORs applied or ledgered with honest status (no silent skips).
3. All 46 MINORs applied or ledgered (NOT APPLIED only for the test-code constraint).
4. 17 Q-decisions all recorded; the 14 that touch code implemented.
5. 3 Q5 features built and compile-verified (runtime = NOT VERIFIED, no device — disclosed).
6. Debug + release + AAB builds green; zero NEW compile errors vs Phase-0 baseline (0).
7. Attacker/Critic/Verifier re-test files exist with verdicts.
8. STATE.md current; worklog Task 12 appended.
