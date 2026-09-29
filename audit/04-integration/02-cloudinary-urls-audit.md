# Phase 2 — Cloudinary Image URL Storage Audit (`cloudinary-urls-audit.md`)

Audited against: live Supabase PostgreSQL (read-only) + public Cloudinary CDN
Evidence: `evidence/p1-p3-sql-facts.log` (row scan), `evidence/p2-full-url-sweep.log` (13/13 URLs)
Date: 2026-09-22 · Auditor: A0

## Verdict: ✓ GO on what exists — every stored Cloudinary URL is well-formed and loads; BUT the spec's 5 target columns are only partially in use (2 of 5 spec columns are entirely unused), and the upload credentials remain missing (Phase 6)

### 1. Full inventory + reachability sweep (100% of stored URLs tested, not a sample)

| Source column | Rows | Cloudinary URLs | Format `https://res.cloudinary.com/…` | Loads (HTTP 200, image/*) |
|---|---|---|---|---|
| Tournament.bannerImage | 12 | 9 | 9/9 ✓ | 9/9 ✓ (200, image/png) |
| Game.image (game artwork) | 3 | 3 | 3/3 ✓ | 3/3 ✓ (200, image/jpeg) |
| ResultProof.image | 1 | 1 | 1/1 ✓ | 1/1 ✓ (200, image/png) |
| User.avatar (spec profile_image_url) | 68 | 0 | — | column NEVER used (all NULL) |
| Transaction.image (spec proof_image_url) | 107 | 0 | — | column NEVER used (all NULL) |

**Full sweep result: 13/13 URLs return HTTP 200 with image content-types — zero broken links.**
One URL is a Cloudinary *authenticated* delivery type (`/image/authenticated/s--…`) — signed URL; loads correctly.

### 2. Spec assertions vs reality

| Spec assertion | Status |
|---|---|
| R3: all image URLs are full Cloudinary URLs (not file paths, not base64) | ✓ VERIFIED for all 13 stored URLs |
| R4: URLs stored in Supabase (DB), not on disk | ✓ VERIFIED (URL text columns + Cloudinary `*PublicId` companion columns for lifecycle) |
| profile_image_url loads | ~ NOT APPLICABLE — column exists (`User.avatar`) but 0 rows ever written |
| cover_image_url loads | ✓ 9/9 |
| logo_image_url (teams) | ✗ column does not exist (see Phase 1) |
| entry_proof_image_url loads | ~ spec column absent; actual `ResultProof.image` ✓ 1/1 |
| proof_image_url (transactions) loads | ~ column exists (`Transaction.image`) but 0 rows ever written |

### 3. Notes

- Cloud name visible in stored URLs: `vloinkza` (public CDN fact, not a secret).
- The two unused columns (avatar, transaction receipt) mean the **upload** half of the image pipeline is only exercised by admin banner/artwork upload and the result-proof flow. Upload credentials are missing from this workspace (`CLOUDINARY_*` not re-provided after sandbox reset) → upload path itself is audited in Phase 6 as BLOCKED.
- No cleanup/refresh issues observed; companion `imagePublicId` columns exist wherever Cloudinary is authoritative, enabling delete/replace lifecycle.
