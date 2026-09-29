-- ============================================================================
-- AREENAX native Android app — server-discovery row (run ONCE)
-- ============================================================================
-- Supabase Dashboard → SQL Editor → paste this whole file → Run.
--
-- What it does
-- ------------
-- 1. Writes the `app_config` row the Android app reads at startup:
--
--      GET {supabase_url}/rest/v1/"Setting"?select=value&key=eq.app_config
--
--    Table/column names are the EXACT Prisma mapping from
--    prisma/schema.supabase.prisma (model Setting has no @@map, so the
--    Postgres table is public."Setting" with columns "key" / "value").
--
-- 2. Re-opens EXACTLY ONE public read path through Row Level Security.
--    scripts/enable-rls.sql (already applied to every deployment) enables RLS
--    on "Setting" and REVOKES every grant from `anon` / `authenticated` /
--    `public` — so the app's anon key cannot read anything by default. The
--    GRANT + POLICY below allow SELECT for the anon role ONLY, restricted by
--    the policy to the single app_config row. Nothing else becomes readable,
--    and nothing becomes writable. Both statements are idempotent.
--
-- 3. The anon key is a PUBLIC value (it ships with every browser request to
--    your panel already). Never put service_role keys in the app.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) The config row
-- ---------------------------------------------------------------------------
-- Replace the two placeholders:
--   • apiBaseUrl          → your live AREENAX deployment origin (the SAME
--                           origin the web panel is served from — the app
--                           calls <apiBaseUrl>/api/..., and QR/share links
--                           are built from this origin too).
--   • cloudinaryCloudName → optional; only informational for the app (images
--                           arrive from the API as absolute Cloudinary URLs).
--                           Leave "" if you don't need it.
--
-- The value may ALSO be a plain string URL — the app treats a non-JSON value
-- as the apiBaseUrl directly:
--   '{"apiBaseUrl": "https://areena.example.com"}'   (recommended)
--   'https://areena.example.com'                     (accepted)

INSERT INTO "Setting" ("key", "value")
VALUES (
  'app_config',
  '{"apiBaseUrl": "https://YOUR-AREENAX-DEPLOYMENT.example.com", "cloudinaryCloudName": ""}'
)
ON CONFLICT ("key") DO UPDATE
SET "value" = EXCLUDED."value";

-- ---------------------------------------------------------------------------
-- 2) Public read path for the app (RLS-aware, idempotent, scoped to one row)
-- ---------------------------------------------------------------------------
-- enable-rls.sql revoked all table grants from anon — a SELECT policy alone
-- is not enough, the grant must be re-added as well. Only SELECT, only for
-- anon, only for this row (USING clause filters every other row out).

GRANT SELECT ON public."Setting" TO anon;

DROP POLICY IF EXISTS "Allow anon read app_config" ON public."Setting";
CREATE POLICY "Allow anon read app_config"
  ON public."Setting"
  FOR SELECT
  TO anon
  USING ("key" = 'app_config');

-- ---------------------------------------------------------------------------
-- Verify (optional) — should return exactly one row with your URL:
-- ---------------------------------------------------------------------------
-- SELECT "key", "value" FROM "Setting" WHERE "key" = 'app_config';
--
-- And from any machine (should return one JSON row, NOT an empty array):
--   curl -s 'https://YOUR-PROJECT.supabase.co/rest/v1/%22Setting%22?select=value&key=eq.app_config' \
--     -H 'apikey: YOUR-ANON-KEY' -H 'Authorization: Bearer YOUR-ANON-KEY'
