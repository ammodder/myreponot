# PHASE 0 FINDINGS — compile blockers (2026-09-19)

Source: first REAL compile ever (throwaway probe copy at /tmp/buildprobe; project tree untouched).
Full raw log: `audit/evidence/compile-probe-run1-2026-09-19.log`.
Status: **FAILED — ≥319 compiler errors (a FLOOR, not a final count)**. The Gradle daemon was
KILLED mid-run ("daemon disappeared unexpectedly"): only 51 of 93 Kotlin files were reached and
the log is truncated mid-error-line — more errors WILL surface once D1-D3 are fixed and the
remaining ~42 files are compiled.

## D1 — XML comment defect [BLOCKER, 1 line]
`app/src/main/res/values/colors.xml:3` — the comment contains `--` ("Web token --color-background").
`--` is illegal inside XML comments → `mergeDebugResources` fails for EVERY build (debug + release).
Fix (pending approval): reword the comment.

## D2 — Kotlin NESTED-COMMENT defect [BLOCKER, cascades widely]
`core/ui/PlaceholderScreen.kt:24` — the doc comment contains the glob `` `ui/screens/**` ``.
Kotlin block comments NEST (unlike Java): the `/*` inside `screens/**` opens a nested comment that
is never closed → the whole file fails to parse ("line 75:1 Unclosed comment") → every symbol
defined there (`AreenaxSpinner`, `PlaceholderScreen`) becomes unresolved → cascade errors in
AppShell.kt, QrSheet.kt, and many screens.
Fix (pending approval): reword the comment (e.g. `ui/screens/…`).

## D3 — Missing imports [BLOCKER group, ~dozens of files / ~280 errors]
Typical patterns (286 of 319 errors are "Unresolved reference"):
- `areenaColors()` used without import (QrSheet.kt, BankSelectSheet.kt, …)
- `AreenaxSpinner` used without import (AppShell.kt, QrSheet.kt, …)
- `ToastController` in core/nav/NavEnv.kt (only ToastVariant imported)
- `Box` / `minHeight` (QRCodeImage.kt, EmptyState.kt — foundation.layout imports)
- `getValue/setValue` delegate imports (AccountRequiredDialog.kt GuestGateState)
- Many screens reference helpers without the right imports (heaviest: AdminPanelScreen 44,
  GamesAdminSection 25, FriendsScreen 20, WalletScreen 18, TransferScreen 14).
Fix (pending approval): mechanical import additions; then re-compile to reveal any next layer.

## D4 — material3 1.3.1 has NO fixed color roles at all [BLOCKER, 6 sites + cascades]
CORRECTED after Attacker round 1 (byte-level inspection of the RESOLVED artifact in the gradle
cache, material3-android/1.3.1): neither the `ColorScheme` CLASS nor the `lightColorScheme()`/
`darkColorScheme()` builders contain `primaryFixed`/`onPrimaryFixed` (or any `*Fixed` role) in
1.3.1 — the strings simply do not exist in the compiled class files. Affected call sites:
Theme.kt:80 + :120 (scheme builders), HostCreationScreen.kt:588/630/968 and
AccountRequiredDialog.kt:143 (`colorScheme.primaryFixed`). The ExtendedColors properties themselves
(Theme.kt:33-34, Tokens.kt) are fine — only the M3 wiring is impossible on 1.3.1.
Fix options (pending owner approval — EITHER is an owner gate):
(a) keep material3 1.3.1 and drop the scheme-level wiring; expose fixed tones ONLY via
    ExtendedColors (screens read `areenaColors().primaryFixed` instead of
    `MaterialTheme.colorScheme.primaryFixed`) — small, no dependency change; or
(b) bump material3 (BOM/dependency change → ASK-FIRST rule F.4).

## D5 — Experimental API opt-in missing [BLOCKER, 1 site]
`core/theme/Type.kt:32` — experimental Compose API used without `@OptIn`/compiler flag.

## D6 — Session-invalid hook: SCOPE bug [BLOCKER, 1 site]
CORRECTED after Attacker round 1: `onUnauthorized` IS declared (`ApiClient.kt:37`,
`@Volatile var onUnauthorized: (() -> Unit)? = null`, KDoc "Set by the ServiceLocator").
The error at ApiClient.kt:119 is a SCOPE bug — the private `UnauthorizedInterceptor` object
references it unqualified. Fix = `ApiClient.onUnauthorized?.invoke()`.

## D7 — Remaining type/syntax errors [~33 non-"Unresolved" errors]
6 "No parameter" (incl. the D4 sites), 5 argument type mismatches, 4 inference failures,
delegate getValue/setValue, composable-context, one "package name found", etc. — exact list in
the evidence log. A precise enumeration only makes sense AFTER D1-D6 land: the compile pass was
incomplete (daemon killed) and cascades may shrink or grow this set.

## What this means (honest)
- The claim "compiles on paper" from the pre-rollback static QA is RETIRED. Only the real
  compiler counts from now on.

## Owner item 3 statuses (build / lint / tests)
- DEBUG BUILD: **FAILED** — evidence: evidence/assembleDebug-run1-2026-09-19.log (resources)
  + evidence/compile-probe-run1-2026-09-19.log (Kotlin, probe copy).
- RELEASE BUILD: **NOT RUN** (reason: blocked by the same D1-D6 compile blockers; would also be
  unsigned-only — no keystore in sandbox).
- LINT: **NOT RUN** (reason: Android lint analyzes compiled sources — blocked by the same blockers).
- EXISTING TESTS: **NOT APPLICABLE** (reason: the module has ZERO test sources — app/src contains
  only main/; no test/, no androidTest/ — verified by file listing).
- The previously delivered zips were never compiler-validated.
- No fix may be applied until the owner approves (Phase 0 = zero app-code changes; also the
  app source is currently the Task-5-era tree, and the owner may prefer to restore the lost
  Task-11-era source from their downloaded zip FIRST, then fix once).
