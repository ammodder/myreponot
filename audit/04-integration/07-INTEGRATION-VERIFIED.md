# Phase 7 — FINAL INTEGRATION CHECKLIST (`INTEGRATION-VERIFIED.md`)

Command: SUPABASE COMPLETE INTEGRATION PROMPT (no custom domain, direct Supabase REST)
Date: 2026-09-22 · Auditor: A0 · Phases 1–6 audits in this folder; raw evidence in `evidence/`

# VERDICT: **NO-GO** for the architecture as spec'd (direct Supabase REST, no middle layer)
# The production system IS fully integrated end-to-end — over its existing, audited middle-layer API.

## Checklist as spec'd

☑/✗ | Item
---|---
✗ | App → Supabase URL direct: **anon REST denied** (401 42501, no grants/RLS policies); OTP provider absent; spec tables 404
✓ | Publishable key works for Auth settings + is safe to embed (deny-by-default confirmed)
✓ | Service key read access proven (admin read-all: User/Tournament/Transaction 200) — kept machine-local (R8 ✓)
✗ | Database tables: spec's 7 snake_case tables **do not exist**; actual 27-table Prisma schema covers the entities differently (Phase 1)
✓/◐ | Cloudinary URLs: **13/13 stored URLs load (200, image/*), zero broken links**; 2 of 5 spec columns never used; team logo column absent (Phase 2)
✗ | RLS policies: **0 of 6 spec policies exist**; RLS on + 0 policies = default-deny (safe, but spec's authorization model not implemented) (Phase 3)
✗ | App endpoint types over direct REST: 0/6 work as spec'd; 6/6 work over the actual API layer (Phase 4)
◐ | Admin operations: read ✓ over service key; writes deliberately not probed (production data); balance-update trigger assumed by spec **does not exist** — approvals credit balance in the middle layer with four-eyes controls (Phase 5)
◐ | Image upload cycle: store+render ✓ proven; upload blocked on missing Cloudinary credentials (Phase 6)
✗ | Auth flow: phone OTP **not configured** in Supabase (provider absent) — the spec's auth flow cannot work today (Phase 4A)
◐ | Transactions immutable: core fields immutable in app logic; `status` legitimately transitions through the approval workflow; **no DB trigger** enforces immutability (Phase 5)

## Why NO-GO is the honest verdict

The spec describes a **different system** than the one that exists. The existing system: Android app → Next.js API (`x-token` sessions, per-endpoint rate limits, idempotency, four-eyes money approvals, append-only audit/security logs) → Prisma → Supabase PostgreSQL. Every phase-1-through-6 capability the spec lists exists there and is verified (live probe 2026-09-22 + browser golden path + adversarial rounds A15–A18). Re-platforming the transport to raw PostgREST+RLS would discard those audited controls for a real-money app and requires migrations the spec does not cover.

## Decision matrix — three ways to "no domain"

| | Path A (RECOMMENDED): keep architecture, domain-free hosting | Path B: full direct-Supabase rewrite (spec literal) | Path C: hybrid — Supabase Edge Functions thin proxy |
|---|---|---|---|
| Code changes | **~0** (deploy the existing Next.js app) | rewrite app networking + re-implement ALL money/security logic as RLS/triggers + OTP onboarding | move API into Edge Functions |
| Domain needed | **No** — free `*.vercel.app`-style subdomain goes into `app_base_url` (R10) | No (Supabase URL) | No |
| Security posture | unchanged, adversarially audited | unproven; 6 policies to author correctly; high blast radius | moderate; still middle-layer logic |
| Time | minutes–hours | days–weeks + device QA | days |
| Loses nothing from the 70+ audit fixes | ✓ | re-derives everything | partial |

**GO conditions (any path):** Path A → owner deploys (or provides a deploy target/creds) and I finalize R10 (`app_base_url`) + rebuild APK. Path B → owner explicitly orders the rewrite after reading Phase 1/3/5 gaps (I will plan it as a new checkpointed work order, not silently). Path C → owner says "override to Edge Functions".

## Owner inputs still open (unchanged by this audit)

1. Cloudinary 4-tuple → unblocks Phase 6 upload verification (deposit proofs, avatars)
2. Deploy decision for Path A (or an explicit Path B/C order)
3. R11 keystore decision · R12 settings-write decision · version-downgrade choice (installed v2.0.0 vs v1.0.0 build)

---
*Bottom line: the integration the spec demands does not exist, and the fastest safe route to the spec's true goal — "the app works with no custom domain" — is Path A: deploy the already-verified backend to any free host and drop the resulting URL into `app_base_url`.*
