# Phase 3 — RLS (Row-Level Security) Policies Audit (`rls-policies-audit.md`)

Audited against: live Supabase PostgreSQL (read-only: pg_class, pg_policies, pg_roles, role_table_grants)
Evidence: `evidence/p1-p3-sql-facts.log` (2026-09-22)
Date: 2026-09-22 · Auditor: A0

## Verdict: ✗ 0 of the 6 spec policies exist — but RLS is ENABLED on every table with ZERO policies, which means direct REST access is default-DENY. The system's authorization lives in the middle layer instead. This is the single most important finding of the audit.

### 1. What exists

**RLS enabled: 27/27 tables** (`relrowsecurity = true`, `relforcerowsecurity = false`).
**Policies defined: 0** (`pg_policies` on schema `public` returns an empty set).
**Roles present:** `anon` (no bypass), `authenticated` (no bypass), `service_role` (BYPASSRLS), `postgres` (BYPASSRLS).

**Grants (information_schema.role_table_grants):**
| Role | Tables | Privileges |
|---|---|---|
| service_role | 7 (User, Tournament, Team, TeamMember, TournamentEntry, Transaction, Notification) | full (SELECT/INSERT/UPDATE/DELETE/REFERENCES/TRIGGER/TRUNCATE) |
| anon | **none** | — |
| authenticated | **none** | — |

### 2. Spec policies — all absent

| Spec policy | Exists? | Practical meaning |
|---|---|---|
| users: read/update own (auth.uid() = id), no self-delete | ✗ | not defined |
| tournaments: all read, creator update/delete | ✗ | not defined |
| teams: member read, captain update | ✗ | not defined |
| transactions: own read, admin read all, no UPDATE | ✗ | not defined |
| tournament_entries: captain/admin read, admin update | ✗ | not defined |
| notifications: own read only | ✗ | not defined |

### 3. What this actually means (verified empirically in Phase 4)

With RLS on + zero policies, PostgREST denies every row to `anon`/`authenticated` — **on top of which there are also zero GRANTs** for those roles. REST probes confirm: anon `GET /rest/v1/User` → **401 42501 permission denied**. So the database is NOT exposed even if someone holds the publishable key. Defense-in-depth state: `RLS(on) + no policies + no grants` = deny-by-default.

### 4. Why the app still works — the actual security model

The production app does NOT talk to PostgREST. It talks to the middle-layer API (Next.js `/api/*`), which connects as the `postgres` role (BYPASSRLS) via Prisma and enforces authorization in application code:
- `x-token` session auth (DB-backed sessions, 30-day sliding TTL, deny-on-expiry)
- Role gates (`requireRoleResponse`), guest blocks, per-user data scoping in every query
- Per-endpoint rate limits (register 3/h/IP, login 8/15min, withdraw 4/h/user, …)
- Idempotency store, four-eyes money approvals, append-only `AuditLog` + `SecurityEvent` (DB trigger blocks UPDATE/DELETE)
All of this was adversarially audited (A15–A18) and live-probed (Task 19-f).

### 5. Gap statement

If the owner wants the spec's direct-connect architecture, the 6 policies + role GRANTs must be authored, **and** the middle layer's enforcement (ownership scoping, immutability rules, admin gates, money-movement integrity) must be faithfully re-implemented as RLS predicates/triggers — for a real-money app this is a high-stakes migration, not a checkbox. See decision matrix in `07-INTEGRATION-VERIFIED.md`.
