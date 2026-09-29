# Phase 5 — Admin Panel → Supabase API Verification (`admin-api-audit.md`)

Evidence: `evidence/p4-p5-rest-probes.log` (2026-09-22)
Date: 2026-09-22 · Auditor: A0
**F3 handling: the service key was used for READ-ONLY probes from this workspace, never printed in full, never written to any tracked file.**

## Verdict: ◐ PARTIAL — the service key DOES grant full read access to the actual tables (bypass RLS + GRANTs on 7 tables): admin read path PROVEN. Write probes (PATCH) were deliberately NOT executed against production data (read-only audit discipline) → write path = NOT TESTED. Spec table names 404 (same as Phase 4).

### Spec probes vs results

| Spec probe | Result | Finding |
|---|---|---|
| A. Admin auth via Supabase Auth (is_admin claim) | ✗ N/A | Supabase Auth has no configured users/claims for this system; admin identity lives in the app's `User.role` (`ADMIN`/`SUPERADMIN`) behind the middle layer's role gates. |
| B. Admin read all users (`GET /rest/v1/users?limit=1000`) | **✓ 200** on actual table: `GET /rest/v1/User?select=id,role&limit=2` → 2 rows returned | service_role BYPASSRLS + GRANT proven |
| C. Approve tournament entries (PATCH) | NOT TESTED (write) | actual `TournamentEntry` readable via service key (200 on read) — table name `tournament_entries` itself 404 |
| D. Approve payments (PATCH, balance via trigger) | NOT TESTED (write) | actual `Transaction` readable (200). **No balance trigger exists** — in the actual system, approval + balance crediting happens in the middle layer inside a transaction with the four-eyes chain (`reviewedById`/`approvedById`) and AuditLog entries. A direct PATCH would update status WITHOUT crediting balance — spec's trigger assumption is not satisfied. |
| E. View proof images (`entry_proof_image_url`) | ✓ equivalent | `ResultProof.image` stored, loads 200 (Phase 2) |
| F. View payment proofs (`proof_image_url`) | ~ | `Transaction.image` column exists but 0 rows ever written (nothing to view) |

### Actual admin surface (all VERIFIED in prior audits)

The real admin panel operates through the middle layer with strictly stronger controls than raw service-key REST: role-gated endpoints, admin mutation rate limits, append-only `AuditLog` (DB-trigger protected against UPDATE/DELETE), four-eyes financial approvals, security-event stream. Admin panel writes require the middle layer; raw service-key access should remain restricted to the owner's machine (spec R8) — which is satisfied here.

### Gap statement

Direct service-key REST writes are *technically possible today* (bypass RLS + grants) and would bypass every one of those controls (no four-eyes, no audit trail, no balance triggers). Recommendation stands: keep service-key access machine-local and admin operations inside the middle layer.
