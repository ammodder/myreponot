# /audit — Production-Readiness Audit Workspace

Branch: `audit/production-readiness` · started 2026-09-19 · rules: mission template (R1–R14).

Layout:

```
audit/
  00-baseline/    RUNTIME-ENVIRONMENT.md · BASELINE.md · RECON-INVENTORY.md ·
                  FEATURE-FREEZE-REGISTER.md · MISSING-INPUTS.md
  01-requirements/ REQUIREMENTS-SOURCE-STATUS.md (RTM lands here in Phase 1)
  02-findings/    one file per agent A1–A13 (Phase 2)
  03-fixes/       defect→commit→test log (Phase 4; gated by Checkpoint 2)
  04-verification/ test results, benchmarks, security scans
  evidence/       redacted logs/outputs — env-probe.log, assembleDebug-attempt.log,
                  static-verifiers-phase0.log
  05-report/      final Go/No-Go report (Phase 6)
```

Statuses (R5): VERIFIED · PARTIAL · FAILED · NOT VERIFIED (reason) · NOT APPLICABLE (reason).
Golden rule until Checkpoint 2: **no application code changes** — workspace + read-only tooling only.
