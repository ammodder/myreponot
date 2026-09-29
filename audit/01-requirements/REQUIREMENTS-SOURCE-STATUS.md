# Phase 0 — Requirements Source Status (input to Phase 1 / Checkpoint 1)

## Owner decision — 2026-09-19 (SUPersedes the assumption below)

The owner clarified the missing-input question directly:

> "wherever I have mentioned chat history, it means the current chat that is running.
> From where you started building the full native Android app Arena X, look at all the
> chat requirements from there."

Interpretation (fact of record):
- **Requirements source = the current running chat session**, starting at the instruction
  that kicked off the native Android app build, covering every instruction since.
- Its durable, verbatim capture in this repo is **`/home/z/my-project/worklog.md`**
  (Task 1 → 12; every task entry records the user's instruction and what shipped)
  plus **`AreenaxNativeAndroid/SPEC/`** design docs the code was built against.
- The earlier assumption A2 ("build the RTM from worklog.md + SPEC") is therefore
  **CONFIRMED by the owner** — no separate chat export is required or expected.
- Phase 1 RTM rows must cite `worklog.md:Task-N` and/or `SPEC/xx`; conflicts resolve
  "later instruction wins" (R8) with worklog task order as the authoritative timeline.

## Fact (original finding, kept for traceability)

- The mission template names `/docs/requirements/chat_history.md` as requirements source.
  **That path does not exist in this repo** — and per the owner decision above it never
  needs to; the live chat is the source, worklog.md is its capture.
- Available sources, priority order (R8: later instruction wins):
  1. `/home/z/my-project/worklog.md` — 3,200+ lines, Task 1 → 12, each task records the
     user's verbatim instruction and what was implemented (riches: Tasks 7-a/b, 8, 9-a..f,
     10, 11, 12).
  2. `AreenaxNativeAndroid/SPEC/` — design tokens / screen specs the code cites (SPEC/00, 01, 04…).
  3. `README.md`, `BEGINNER-GUIDE.md`, `SETUP-BACKEND.md`, `SECURITY-BUILD.md`, `SECURITY-RUNTIME.md`.
  4. Web app (`src/**`) as behavioral parity reference (the native app is a 1:1 port by charter).

## Known requirement conflicts to resolve at Checkpoint 1

| # | Conflict / ambiguity | Current shipped state |
|---|---|---|
| C1 | "Alphla Bank" typo | preserved deliberately (SPEC/00 binding) |
| C2 | Results error vs empty message | identical copy (web parity) |
| C3 | Chat "Online" status + date divider static | web parity (static) |
| C4 | About version "1.0.0" hardcoded | differs from versionName 2.0.0 (frozen; Phase 2 will flag) |
| C5 | Suggested Players demo data | present in code; conflicts with M5 "no demo data in production" — needs owner decision |
| C6 | No account-deletion flow | Play policy blocker candidate (Phase 5) |
