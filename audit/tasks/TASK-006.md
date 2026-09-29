# TASK-006 — PHASE 2: Parallel Read-Only Audits (A3–A13 + Icon Agent)

Owner order (2026-09-20, condensed; decisions Q1–Q17 recorded in STATE.md decision log 8):
> Checkpoint 1 approved. Run the full Prompt Pipeline. Start PHASE 2 (parallel read-only
> audits). Do NOT change application code. Test on the RELEASE build (debug only for tools).
> Run agents A3 to A13 and save findings in /audit/02-findings/A3.md ... A13.md, each finding
> with: ID, severity, location, steps to reproduce, expected vs actual, root cause, proposed
> minimal fix, regression risk, evidence.
> Every status needs evidence in /audit/evidence. Anything you cannot test: NOT VERIFIED with
> the reason. When finished, show: number of findings per agent and severity, and the list of
> untested items. STOP and wait for me.

## Hard constraints
- ZERO application-code changes (`app/**`, `SPEC/**` read-only; all writes under `audit/**`).
- "Test on the RELEASE build (debug only for tools)" — interpretation disclosed to owner: we
  build `assembleRelease` (unsigned — no keystore exists; owner signs manually per Q14) and use
  it for build/scanning checks (R8 outcome, APK contents, secrets scan, size, 16 KB). Runtime
  on-device testing is impossible (no device/emulator) → those items are NOT VERIFIED with
  reasons. The debug APK (existing) is used only as a tooling input where a built artifact is
  needed and release cannot provide it.
- Finding template (ALL agents, every finding):
  `ID: <A#-nn> · Severity: BLOCKER|MAJOR|MINOR|NOTE · Location: path:line · Steps to reproduce ·
  Expected vs actual · Root cause · Proposed minimal fix · Regression risk: low|med|high + why ·
  Evidence: audit/evidence/phase2/<file>`
- Untestable item → status NOT VERIFIED + reason. No invented results. Honest labels only.
- Evidence transcripts in `audit/evidence/phase2/` (commands + outputs, reproducible).
- ≤3 fix attempts per problem (n/a mostly — no fixes this phase); hard STOP at the end.

## Agent plan
| Agent | Scope | Output | Depends |
|---|---|---|---|
| A3 | Screen+Control Inventory: EVERY screen, dialog, button, toggle, input, tab, menu, swipe, back; coverage matrix found==tested; code-level behavior probes: double-tap, rapid taps, rotation, back stack, keyboard overlap, process death, background, offline, slow net, dark mode, big font | 02-findings/A3.md | — |
| A4 | Every function/validation; validation matrix client vs server (endpoints × fields × rules) | 02-findings/A4.md | — |
| A5 | Timeouts, retries, error messages, offline behavior, backend permission matrix (user A vs B data; guest vs user vs admin) — runtime backend tests impossible → static matrix + NOT VERIFIED list | 02-findings/A5.md | — |
| A6 | MASVS v2.1 table (8 groups: storage, crypto, auth, network, platform, code, resilience, privacy); secrets scan code+git history+built APK; dependency vulnerabilities; manifest review; attack tests | 02-findings/A6.md | release APK |
| A7 | Performance baseline (startup, jank, memory, battery, size) low-end profile — static analysis + release-APK metrics; on-device items NOT VERIFIED | 02-findings/A7.md | release APK |
| A8 | UI consistency, transitions, animations, button feedback, predictive back, edge-to-edge; note Q10 300ms decision as pending fix | 02-findings/A8.md | — |
| A9 | Accessibility (TalkBack, touch size, contrast, font scale) + strings/localization; note Q12 privacy-policy update requirement | 02-findings/A9.md | — |
| A10 | Android 16 (API 36) behavior changes vs current targetSdk 35; 16 KB page-size check (expect N/A — no native code, verify via APK); device/OS matrix | 02-findings/A10.md | release APK |
| A11 | Google Play readiness: target API policy, data safety, privacy policy, account deletion (Q15), permissions, content rating, closed testing (14 testers), developer verification; **payments/real-money policy text FIRST (exact quotes)** from policy-research.md | 02-findings/A11.md | policy research |
| A12 | Automated tests: what exists (nothing) + what is missing; prioritized test plan (no code written) | 02-findings/A12.md | — |
| A13 | Crash reporting, logging without private data, rollout/rollback readiness (versioning, signing, staged rollout, changelog) | 02-findings/A13.md | — |
| ICON (Q9) | Icon audit agent: build read-only audit tool (audit/tools/icon_audit*) scanning app drawables/icons + web icons + downloaded SVGs; deep cross-check; missing-icon report + download/cache/retry design (downloads DEFERRED to fix phase); admin .EXE explicitly out of scope (after app verification per owner) | 02-findings/ICON-AUDIT.md | — |

## Q-decision effects on this phase (no code changes — analyze impact only)
- Q1-REMOVE fallbacks, Q2-REFUSE over-cap, Q3-5MB proof cap, Q6-countOnly poll, Q8-single
  SupportFab, Q10-300ms, Q13-Alphla→Alfalah fix, Q11-version 1.0.0 (fallback 2.0.0),
  Q12-real-friends via location+contacts (permissions+server+privacy), Q15-account deletion:
  each = pending fix-phase items; auditors map impact + regression risk now.
- Q5-BUILD-3-ONLY timeline estimate: client APIs already exist (Api.kt:197-207 join-request
  types, :250-251 DELETE notifications, countOnly :236) → estimate ≈ 3 fix sessions
  (join-inbox ≈1, team-QR popup ≈0.5, notifications select/delete ≈1, rebuild+verify ≈0.5).
- Q14 keystore: owner-side (Android Studio) → release stays unsigned here.
- Q16 force-update AFTER release; Q17 server audit AFTER release — recorded as scope notes.

## Acceptance
1. A3.md…A13.md + ICON-AUDIT.md exist, all findings use the 9-field template.
2. Every claim carries evidence; untestable items listed NOT VERIFIED + reason.
3. Consolidated counts (per agent × severity) + untested list delivered; STOP.
