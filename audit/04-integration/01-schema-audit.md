# Phase 1 — Database Schema Audit (`schema-audit.md`)

Command: owner's SUPABASE COMPLETE INTEGRATION PROMPT (no-domain, direct-Supabase spec)
Audited against: **live Supabase PostgreSQL** (`imstrrlksvvwsldzchxq`, session pooler 5432, read-only)
Evidence: `evidence/p1-p3-sql-facts.log` (information_schema queries, 2026-09-22)
Date: 2026-09-22 · Auditor: A0 (Backend Architect per command)

## Verdict: ✗ NO-GO for the literal spec — the 7 spec tables do not exist; a RICHER 27-table schema exists in their place (semantic coverage detailed below)

### 1. Spec tables (exact snake_case names) — existence check

| Spec table | Exists? | Reality |
|---|---|---|
| users | ✗ | REST probe: `404 PGRST205 "Could not find the table public.users"` |
| tournaments | ✗ | not present in information_schema |
| teams | ✗ | a `Team` table exists (PascalCase, different columns) |
| team_members | ✗ | a `TeamMember` table exists (PascalCase, different columns) |
| tournament_entries | ✗ | REST probe 404; a `TournamentEntry` exists (different model) |
| transactions | ✗ | a `Transaction` table exists (PascalCase, different columns) |
| notifications | ✗ | a `Notification` table exists (PascalCase, different columns) |

**Actual schema: 27 tables** (Prisma-managed, PascalCase): Achievement, AuditLog, BankAccount, Banner, FriendRequest, Friendship, Game, GameMap, GameMode, GamePerspective, IdempotencyKey, Message, Notification, PushToken, ResultProof, SecurityEvent, Session, Setting, Team, TeamJoinRequest, TeamMember, Tournament, TournamentEntry, Transaction, Task, User, UserAchievement, UserTask, TournamentReminder — live production data present (68 users, 12 tournaments, 28 entries, 107 transactions, 107 notifications, 2 teams, 1 result proof).

### 2. Column-level mapping (spec → actual) — semantic audit

**users → `User`** — PARTIAL COVER (~)
| Spec column | Actual | Status |
|---|---|---|
| id uuid PK | id text PK (cuid) | ~ type differs |
| email text unique | email text? unique | ✓ (+nullable) |
| phone text unique | phone text? unique | ✓ |
| full_name | fullName | ✓ renamed |
| profile_image_url (Cloudinary) | avatar text (0/68 rows used — never written) | ~ column exists, unused |
| balance decimal ≥0 | balance Float | ~ (float, app-enforced ≥0) |
| created_at / updated_at | createdAt / updatedAt | ✓ |
| is_admin boolean | role text `USER\|ADMIN` (+SUPERADMIN in roles lib) | ~ different mechanism, same semantics |

**tournaments → `Tournament`** — PARTIAL COVER (~)
| Spec | Actual | Status |
|---|---|---|
| created_by FK | hostId FK → User | ✓ renamed |
| max_teams int | maxPlayers Int (default 48) — **individual-entry model** | ~ different participation model |
| entry_fee / prize_pool | entryFee / prizePool (+ perKill, loserPrize extras) | ✓ superset |
| status open/ongoing/closed | status `UPCOMING\|ONGOING\|COMPLETED\|CANCELLED` | ✗ different value domain |
| cover_image_url | bannerImage (9/12 rows Cloudinary) | ✓ renamed, in active use |
| description | rules text | ~ |

**teams → `Team`** — LOW COVER (✗)
- Spec: tournament_id FK, captain_id, logo_image_url, balance → Actual `Team`: name, tag, ownerId. **No tournament_id** (teams are standalone squads), **no logo_image_url**, **no balance**. Tournament participation is per-user (`TournamentEntry.userId`), not per-team.

**team_members → `TeamMember`** — PARTIAL (~): teamId/userId/role ✓ (`OWNER\|MEMBER` vs spec `captain\|member`); **no joined_at**.

**tournament_entries → `TournamentEntry`** — STRUCTURAL DIFFERENCE (✗): spec is team-based (`team_id`, status pending/approved/rejected with admin approval flow). Actual is **individual-entry, pay-to-join**: tournamentId + userId (unique pair), status `JOINED\|PLAYED`, kills/rank/prize. Proof screenshots live in a separate `ResultProof` table (1 row, Cloudinary). No admin_notes column (admin workflow in app layer + AuditLog).

**transactions → `Transaction`** — PARTIAL (~): type superset `DEPOSIT\|WITHDRAW\|TRANSFER\|PRIZE\|ENTRY_FEE\|REFUND\|REFERRAL_BONUS\|TASK_REWARD` (vs spec 4 values); status ✓ `PENDING\|COMPLETED\|FAILED\|REJECTED`; proof_image_url → `image` (0/107 rows used); bank_account → separate `BankAccount` table (normalized, multi-account, isPrimary); reference_number → reference ✓. Extras beyond spec: four-eyes approval chain (reviewedById/approvedById), clientRef dedupe, proofHash perceptual hash. Immutability: spec R7 "never edit" — actual: core fields immutable in app logic, but `status` IS updated by the approval workflow (design difference, not a bug).

**notifications → `Notification`** — GOOD COVER (~): title/message/is_read/created_at ✓; type value domain `INFO\|SUCCESS\|WARNING\|TOURNAMENT\|PAYMENT` vs spec `tournament\|team\|payment\|admin`; extra `link` deep-link column.

### 3. Bottom line

The spec's schema is NOT the schema this system runs on. The production database is a 27-table Prisma schema that covers (and exceeds) the spec's entities with different naming (camelCase/PascalCase), cuid keys (not uuid), and a different participation model (individual entries instead of team entries with approval). Rewriting tables to the spec's shape would mean migrating a live production dataset — or rewriting the spec to match reality. That is an owner decision (see `07-INTEGRATION-VERIFIED.md`).
