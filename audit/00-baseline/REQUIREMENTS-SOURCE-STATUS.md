# REQUIREMENTS SOURCE STATUS (2026-09-19, post-rollback)

## What sources exist RIGHT NOW
| Source | Status | Content |
|---|---|---|
| `worklog.md` (repo root) | SURVIVED, ends at Task 5 | Tasks 1-5: web foundation, native app spec audit, skeleton, 48 screens, QA integration, zip packaging. Owner requirements of that era are embedded here. |
| `AreenaxNativeAndroid/SPEC/00-05 + ICONS` | SURVIVED | 2,325-line spec: overview, per-screen contracts, API shapes, components, design tokens, native guide. |
| On-disk app source (93 kt files) | SURVIVED | The authoritative current behavior (Task 5 era). |
| Orchestrator conversation record | PARTIAL — reconstructed, not verbatim | Knowledge of Tasks 6-12 (dark-mode v1, AI-slop audit, Midnight Charcoal final theme, zip v2, Master Command, Phase 0 v1, ledger seeding REQ-001-024). Stored in audit docs as [reconstructed] notes. |
| `audit/` workspace | REBUILT this session | Previous audit files lost with the rollback. |

## Lost with the rollback (cannot be recovered locally)
- Worklog records after Task 5 · verbatim Master Command · 96-row FFR · REQUIREMENTS_LEDGER.md
- The final zip · CLAUDE.md · audit branch commits.

## Consequences for the requirements ledger (next task after Phase 0 STOP releases)
- The ledger WILL be rebuilt (REQ-### numbering restarts), sourced from: worklog Tasks 1-5 + SPEC/
  + the owner messages preserved in the conversation record (listed as reconstructed where verbatim
  text is not available).
- If the owner wants anything in the ledger that is NOT in worklog Tasks 1-5 and NOT in the
  conversation record, they must paste it as text.

## Owner rule reminders that shape the ledger
- "Chat history" = the current conversation; never ask for files; missing info → ask owner to paste text.
- Ledger groups: feature / UI / business rule / security / performance / admin-data.
