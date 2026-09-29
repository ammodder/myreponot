# TASK-003 — Compile fix pass 1: D1–D6 minimal fixes (owner-approved)

- Worklog Task ID: 8
- Owner prompt (2026-09-19): "DECISION 1 (C7): FIX-YES · DECISION 2 (D4): D4-OPTION-A ·
  DECISION 3 (Sequencing): ZIP-NO — Proceed to fix D1–D6 now."
- Scope mandate: D1, D2, D3 (import fixes only), D4 option (a), D5, D6.
  **D7 (type/property mismatches, nonexistent APIs) is NOT in this mandate** — those are
  collected from the fresh compile logs and reported for a separate approval.

## Approved fix plan (each ≤3 attempts per Master Command F.8)
- D1 colors.xml:3 — remove illegal `--` inside XML comment (reword only).
- D2 PlaceholderScreen.kt:24 — reword KDoc glob `ui/screens/**` (the `/*` sequence opens a
  nested block comment that never closes). Codebase-wide scan found no other such trap.
- D3 (verified head-dominos pre-applied this turn; the full file-by-file sweep runs AFTER
  the first fresh compile): NavEnv.kt +import ToastController (dissolves the 136 `show`
  errors); AccountRequiredDialog.kt +import getValue/setValue (dissolves the 4 delegate
  errors).
- D4 option (a) — keep material3 1.3.1: remove the 4 illegal `primaryFixed`/`onPrimaryFixed`
  params from the lightColorScheme()/darkColorScheme() builder calls (Theme.kt:80/81/120/121);
  switch the 4 screen call sites to `areenaColors().primaryFixed` (HostCreationScreen ×3 —
  already imports areenaColors; AccountRequiredDialog ×1 — +import areenaColors).
  Values are IDENTICAL (ExtendedColors already carries Light/DarkPrimaryFixed per theme) —
  zero visual change. NOTE: an `Extension property on ColorScheme` bridge was considered and
  REJECTED: areenaColors() is @Composable (CompositionLocal read) so a plain property getter
  cannot call it.
- D5 Type.kt — @OptIn(ExperimentalTextApi::class) on hankenFont + import. Verified from the
  resolved artifact: the experimental marker lives in ui-text FontKt.class (the Font()
  overload with variationSettings). Removing the variationSettings argument was REJECTED:
  without it the bundled variable TTF would render at its default axis for every weight
  (visual regression → FFR violation).
- D6 ApiClient.kt:119 — qualify the reference: `ApiClient.onUnauthorized?.invoke()`
  (UnauthorizedInterceptor is a top-level object; onUnauthorized is a member of ApiClient).

## Build environment (sandbox RAM fix, disclosed)
- Root cause of the "daemon disappeared" kill: two stale build JVMs (Gradle daemon 1.17 GB +
  Kotlin daemon 0.73 GB) were left from the Phase-0 probe; only ~1.7 GB was free for the
  compiler. Both stale JVMs were killed (freed ~1.9 GB; 3.5 GB now available).
- This build runs with CLI-only memory settings (repo gradle.properties untouched):
  -Dorg.gradle.jvmargs="-Xmx1700m -XX:MaxMetaspaceSize=512m",
  -Dkotlin.compiler.execution.strategy=in-process (no separate Kotlin daemon), --max-workers=2.

## Honesty expectations
- 319 was a FLOOR: ~41 of 93 Kotlin files were never compiled. The fresh log WILL list more
  errors — expected, not a regression.
- Success criterion for THIS task: D1–D6 defect sites gone from the compiler output;
  the remaining error set cleanly classified into D3 (mechanical imports) vs D7 (needs
  separate approval). D7 is fixed ONLY after owner approval.

## MUST NOT change
- Any behavior, design, wording, colors (FFR freeze): fixes are comments/imports/annotations/
  call-site color accessor only.
- material3/BOM versions (D4 option b explicitly rejected by owner decision).
- D7 sites (EmptyState minHeight API, ToastHost toastBody, TransferScreen pivot,
  AdminPanelScreen user.role, charAt receivers) — untouched until approved.
