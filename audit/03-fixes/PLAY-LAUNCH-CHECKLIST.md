# PLAY-LAUNCH / RELEASE CHECKLIST (owner-side items from Phase 2 findings)

App-side work is done (see FIXES.md). Everything below is outside the sandbox:
owner accounts, Console forms, real hosting, signing material. Section = the finding.

## 1. Pre-launch server population (A13-05, A13-04 item 3, Q1/Q13 admin note)
- [ ] Bring the server up on the production domain FIRST.
- [ ] Set `settings.version` = **1.0.0** in the admin panel (About screen is server-driven).
- [ ] Populate bootstrap fields: whatsapp, mission, socials, **Deposit Accounts** (per-method).
- [ ] Verify one Deposit-Confirm screen shows SERVER account numbers — not "Not configured".
- [ ] If a Deposit-Accounts key reads **"Alphla Bank"**, rename it **"Alfalah Bank"** (Q13) —
      otherwise that method intentionally shows "Not configured" (Q1: no hardcoded fallbacks).

## 2. Data safety form (A11-05) — declare, consistently with the privacy policy
Account (name, email, phone, game UID) · Financial info (deposit receipts, bank account numbers,
transaction records) · Photos/videos (receipt images) · Location (coarse, only when opening
Players Nearby) · Contacts (ONLY a single picked contact's phone for matching — no address book
upload). All collected in-app; no sharing beyond verification staff.

## 3. Distribution path decision (A11-01 — DECISION RECORDED)
**Path A (current): web-APK distribution** — Google Play's real-money policy does not apply;
the Payments policy explicitly permits this model (Phase-2 policy evidence). No app change.
If a **Play listing** is ever wanted: the real-money loop (deposit → entry fee → cash prize →
withdraw) must be removed or licensed per the RMG policy — a product-defining decision for
another day. In-app self-updates are permitted on web-APK distribution (Q16 relevance),
but prohibited on Play (Device-and-Network-Abuse quote, A11-11).

## 4. Closed testing (A11-07)
Personal developer account created after 2023-11-13 → **≥12 opted-in testers for 14 consecutive
days** before production access. Organization accounts skip this but need a D-U-N-S number (§7).

## 5. Signing + first upload (A13-03, Q14, A11-09)
- [ ] Android Studio: Build → Generate Signed App Bundle / APK → *Create new…* keystore
      (alias + ≥25-year validity). This project's release build AUTO-DETECTS a
      `keystore.properties` at the repo root (gitignored): storeFile/storePassword/keyAlias/keyPassword.
- [ ] NEVER commit the keystore or keystore.properties; back the keystore up OFFLINE
      (losing it without Play App Signing = the app can never be updated under this package name).
- [ ] Keep **Play App Signing ON** at first upload (Google holds the app signing key; local key = upload key).
- [ ] Upload the **AAB** (`bundleRelease` proven — app-release.aab builds green).
- [ ] Upload `mapping.txt` (app/build/outputs/mapping/release/) with EVERY release, named by versionCode.
- [ ] **versionCode is write-once from the first upload** — never decrease, never reuse.
- [ ] Rollback = "halt staged rollout" + roll FORWARD with a higher versionCode (Android refuses downgrades).

## 6. Content rating + audience (A11-08)
- [ ] IARC questionnaire answered truthfully (money/prize questions) — expect Adult/18.
- [ ] Target audience: adults only (RMG apps must not target under-18).

## 7. Developer verification (A11-10)
- [ ] Personal account: legal name/address + verified email/phone.
- [ ] Organization account: D-U-N-S number (free, up to 30 days) BEFORE account creation;
      payments-profile name must match D-U-N-S exactly.

## 8. Dependency hygiene (A6-04 residual)
Pins are current within their major lines. Schedule periodic advisory checks; major-version
bumps (okhttp 5.x, coil 3.x, BOM) = owner-approved upgrades with a build + smoke pass.

- ⚠️ 2026-09-22 (A17-1): any device still running the published v2.0.0 debug APK (versionCode 2) must UNINSTALL it before installing the v1.0.0 build (versionCode 1, Q11) — Android rejects downgrades. Or authorize a versionCode ≥ 3 bump for the published artifact.
