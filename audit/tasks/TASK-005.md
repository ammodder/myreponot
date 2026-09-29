# TASK-005 — PHASE 1: Requirements Traceability Matrix + A1 Architecture Map + Attacker Pass

Owner order (verbatim, 2026-09-20):

> Phase 0 is approved. Run the full Prompt Pipeline. Start PHASE 1 (Requirements Traceability).
> No application code changes.
> 1. A2: check EVERY requirement in the ledger against the real code. For each REQ write:
>    status (VERIFIED / PARTIAL / MISSING / FAILED / CONFLICT / OBSOLETE), file:line, the test
>    that covers it, evidence link. Save as /audit/01-requirements/RTM.md.
> 2. List conflicts (later instruction wins), assumptions you made, and a gap list: things a
>    production app of this type normally has but I never asked for (for example account
>    deletion, empty/error/offline screens, force-update, accessibility).
> 3. A1: finish the architecture map and code-health findings in /audit/02-findings/A1.md.
> 4. Attacker: try to find requirements that are "marked done" but not truly working.
> CHECKPOINT 1 — STOP. Show me: counts by status, all conflicts, all assumptions, and every
> question you need answered. Explain in simple words.

## Constraints (hard)
- ZERO application code changes. `AreenaxNativeAndroid/app/**`, `SPEC/**`, published APK, web
  panel `src/**` are READ-ONLY for this task. All writes go to `AreenaxNativeAndroid/audit/**`
  (on branch `audit/production-readiness` via worktree `/home/z/audit-wt`) and the shared
  worklog. Git commits are made by the orchestrator only.
- The REQUIREMENTS_LEDGER.md was LOST in the 2026-09-19 sandbox rollback (see
  `00-baseline/REQUIREMENTS-SOURCE-STATUS.md`). Phase 1 therefore starts by REBUILDING the
  ledger (REQ numbering restarts) from surviving sources: `worklog.md` native era (Task ID 1
  at worklog line 376 onward), `SPEC/00-05 + ICONS` (3,015 lines), and [reconstructed]
  conversation-record notes inside the audit docs. Anything not recoverable from these must be
  declared as an assumption or asked at CHECKPOINT 1 — never invented.
- Honest labels only (MASTER_RULES). Every RTM status must cite real `file:line` evidence from
  the app source read fresh (not from memory or the SPEC). Where no automated test exists, the
  test column says exactly that (repo has NO `app/src/test` / `androidTest` — verified).
- ≤3 fix attempts per problem; CHECKPOINT 1 is a hard STOP.

## RTM row template (all verifiers use this)
`| REQ-### | Requirement (one sentence) | STATUS | Evidence file:line (native app) | Test | Notes |`
- STATUS ∈ VERIFIED / PARTIAL / MISSING / FAILED / CONFLICT / OBSOLETE
  - VERIFIED: implemented as specified; cited code proves it.
  - PARTIAL: partly implemented or deviates in a documented way.
  - MISSING: required by ledger, absent from code.
  - FAILED: code contradicts the requirement (present but wrong).
  - CONFLICT: requirement sources disagree; resolution = later owner instruction wins.
  - OBSOLETE: superseded by a later owner instruction (cite it).
- Evidence paths relative to `AreenaxNativeAndroid/` (e.g. `app/src/main/java/com/areenax/nativeapp/ui/screens/HomeScreen.kt:42`).
- Test column: `none (no automated tests in repo)` unless a concrete verification artifact exists
  (e.g. compile probe, Attacker check, API-shape diff) — then name it + evidence link.

## Subagent plan
| Agent | Task ID | Output | Depends on |
|---|---|---|---|
| ledger-builder | 10-a | `audit/01-requirements/REQUIREMENTS_LEDGER.md` (REQ-### grouped feature/UI/business-rule/security/performance/admin-data, each with source cite) + conflict candidates | — |
| a1-mapper | 10-b | `audit/02-findings/A1.md` (architecture map + code-health metrics/findings, all claims file:line-cited) | — |
| rtm-verifier-feature-1 | 10-c | `audit/evidence/phase1-rtm/part-feature-1.md` (auth, wallet, tournaments, home, games REQs) | 10-a |
| rtm-verifier-feature-2 | 10-d | `audit/evidence/phase1-rtm/part-feature-2.md` (teams, friends/chat, notifications, host, admin REQs + admin-data group) | 10-a |
| rtm-verifier-cross | 10-e | `audit/evidence/phase1-rtm/part-cross.md` (UI/design tokens, business rules, security, performance, cross-cutting REQs) | 10-a |
| attacker | 10-f | `audit/evidence/phase1-rtm/attacker.md` — hunt "marked done but not truly working"; propose status corrections with proof | draft RTM assembled |
| orchestrator | 10 | spot-check ≥10% of RTM rows (evidence: `phase1-rtm/spotcheck.md`), assemble `RTM.md`, synthesize conflicts/assumptions/gap list, commit, CHECKPOINT 1 report + STOP | all |

## MUST NOT change
App code, SPEC, gradle files, manifest, published APK, `public/downloads/**`, main worktree,
git history (no force-push, no rewrites). Fix commits stay as they are; Phase 1 is documentation
only. D1–D7 fixes remain committed and untouched (`git diff main..audit/production-readiness --
AreenaxNativeAndroid/app/src` = the approved fix set, nothing more after this task).

## Acceptance
1. REQUIREMENTS_LEDGER.md exists, grouped per the owner's six groups, every REQ has a source cite.
2. RTM.md covers 100% of ledger REQs with status + file:line + test column + evidence link.
3. A1.md exists with architecture map + code-health findings, claims cited.
4. Conflicts (later-wins resolution), assumptions, gap list delivered at CHECKPOINT 1.
5. Attacker pass done; any accepted status corrections folded into RTM.md.
6. Evidence files under `audit/evidence/phase1-rtm/`; small audit-branch commits.
7. Pipeline Report + "NEXT STEP FOR YOU" ends the owner reply; hard STOP at CHECKPOINT 1.
