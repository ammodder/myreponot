# Phase 6 — Cloudinary Upload → Supabase Storage Flow (`cloudinary-flow-audit.md`)

Evidence: `evidence/p2-full-url-sweep.log` (13/13), code audit (A15–A18), `.env` state
Date: 2026-09-22 · Auditor: A0

## Verdict: ◐ PARTIAL — the RETRIEVE side of the cycle is fully proven (URLs stored in DB render at the client: banners + game artwork verified live in browser session; result proof loads 200). The UPLOAD side is **BLOCKED: Cloudinary API credentials are not present in this workspace** (owner re-pasted only Supabase credentials after the sandbox reset).

### Flow step audit (per spec)

| Step | Status | Evidence |
|---|---|---|
| 1. Upload to `api.cloudinary.com/v1_1/{cloud}/image/upload` → secure_url | **BLOCKED (owner input)** | `CLOUDINARY_CLOUD_NAME / API_KEY / API_SECRET / TOKEN_KEY` absent from `.env` since sandbox reset; upload endpoints return configuration errors without them. NOT VERIFIED — reason: missing credentials. |
| 2. Store URL in Supabase | ✓ mechanism VERIFIED | URL text columns + `*PublicId` companions; 13 live rows written by admin upload flows and result-proof flow (banner 9, game 3, proof 1) |
| 3. Retrieve URL from Supabase | ✓ VERIFIED | app API returns assets; DB query confirms values |
| 4. Client renders `<img src=cloudinary>` | ✓ VERIFIED | 13/13 URLs HTTP 200 image/* (full sweep); banners/artwork rendered in the browser-verified session |

### Per image type (spec list)

| Image type | Spec column | Actual | Upload | Store+Render |
|---|---|---|---|---|
| User profile pictures | users.profile_image_url | `User.avatar` (0/68 used) | BLOCKED (creds) | column unused — NOT VERIFIED |
| Tournament covers | tournaments.cover_image_url | `Tournament.bannerImage` (9) | ✓ admin flow in production | ✓ 9/9 |
| Team logos | teams.logo_image_url | column ABSENT (Phase 1) | N/A | N/A |
| Entry proofs | tournament_entries.entry_proof_image_url | `ResultProof.image` (1) | ✓ user flow in production | ✓ 1/1 |
| Payment proofs | transactions.proof_image_url | `Transaction.image` (0/107 used) | BLOCKED (creds) | column unused — NOT VERIFIED |

### Unblock path

Owner re-pastes the four `CLOUDINARY_*` values → upload endpoints become testable end-to-end (deposit proof + avatar), and the two dormant columns can be exercised. Until then every upload-side check remains honestly NOT VERIFIED.
