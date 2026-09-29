# A3 — method, scope, counting rules (PHASE 2, read-only)

Date: 2026-09-20 · Agent: A3 (screen+control inventory) · Task: TASK-006
Worktree: /home/z/audit-wt/AreenaxNativeAndroid · HEAD at audit start: audit/production-readiness @ 91bac92
Constraints honored: ZERO app-code changes, no git writes, no builds; all outputs under audit/**.

## Scope read
- All 48 files under `app/src/main/java/com/areenax/nativeapp/ui/screens/**`
  (auth 5, main 5, wallet 10, social 8, profile 11, host 5, info 4).
  profile/GamesAdminSection.kt + profile/ResultProofsAdminSection.kt are SECTIONS
  composed inside AdminPanelScreen (AdminPanelScreen.kt:208/223) — inventoried as
  screens per the 48-file count, flagged as sections in the tables.
- core/nav: AppNavHost.kt, AppNavigator.kt, NavEnv.kt, ScreenKeys.kt (46 routes).
- core/ui: all 23 shared components (AppBar, BottomNav, SupportFab, WhatsAppFab,
  ToastHost, TournamentCard, AmountPad, QrSheet, RoomInfoSheet, BankSelectSheet,
  TeamJoinSheet, ResultProofSheet, AccountRequiredDialog, InsufficientBalanceDialog,
  DateTimePickerModal, PhotoPickers, SuccessAnim, EmptyState, StatusChip, AppShell,
  QRCodeImage, AreenaxIcon, Shadows).
- core/network: ApiClient.kt, ApiResult.kt (Api.kt endpoint list cross-checked in A5 scope only).
- core/session: SessionManager.kt, UnreadManager.kt, SettingsCache.kt.
- core/util: ConnectivityObserver.kt, QrPayload.kt, TournamentState.kt, Formatters.kt.
- core/theme: Theme.kt, Tokens.kt, Type.kt, Shapes.kt.
- app: MainActivity.kt, AreenaxApplication.kt, AndroidManifest.xml, res/values/*.

## Commands (reproducible)
rg-based sweeps via the audit Grep tool over
`app/src/main/java/com/areenax/nativeapp` (raw outputs in a3-grep-controls.log):
1. `\.clickable|\.combinedClickable`            → 122 occurrences / 39 files
2. `onClick\s*=`                                → 197 occurrences / 56 files
3. `Switch\(`                                   → 2 occurrences / 1 file (ProfileScreen custom ThemeSwitch area)
4. `TextField\(|OutlinedTextField\(|BasicTextField\(` → 48 occurrences / 20 files
5. `BackHandler|detectDragGestures|detectVerticalDragGestures|detectHorizontalDragGestures|HorizontalPager|ModalBottomSheet|AlertDialog|DropdownMenu|TabRow` → 33 occurrences / 10 files
6. `imePadding`                                 → 16 occurrences / 8 files
7. `rememberSaveable`                           → 59 occurrences / 13 files
8. `env\.params\[`                              → 40 occurrences (param consumers)
9. `WhatsAppFab\(|TeamJoinSheet\(|ResultProofSheet\(|BankSelectSheet\(|RoomInfoSheet\(|DateTimePickerModal\(` → call-site check (dead-code finding)
10. `SavedStateHandle|repeatOnLifecycle|Lifecycle` → 0 occurrences
11. Full-file reads of all 48 screens + 23 core/ui + nav + network + session + theme
    (every control row in A3.md cites file:line from those reads).

## Counting rules (make totals reproducible)
- ONE row per distinct interactive control instance; dynamic groups (list items)
  = ONE row tagged CARD with the group semantics stated (card tap / actions).
- Text fields: each on-screen input = 1 INPUT row (a shared wrapper used N times
  counts N; its single definition site is cited once).
- IME actions (Next/Done/Send) are folded into their field row's interaction test.
- Keypads (AmountPad / WithdrawKey pad / TransferKey pad) = 1 KEYPAD row each
  (12 keys each; key rules verified in the row test).
- Dialogs/sheets: the container = 1 DLG row; the buttons inside it are separate rows.
- Back: global BackHandler (1) + AppBar page-mode back (1 shared row) + per-screen
  onBack overrides (4 rows) are the only BACK rows; custom back arrows are ICON rows.
- Type tags on every row: BACK, INPUT, KEYPAD, TOGGLE, TAB, MENU, CHIP, BTN (M3
  Button/OutlinedButton/TextButton), ICON (circle/icon button), PILL (Surface/Box
  pill CTA / toggle-pill / plain-CTA), LINK (text link), CARD (list/card tap group
  or dismiss layer), DLG (dialog/sheet container), PICKER (system picker launch).
- Non-interactive chips (status pills, banners) are NOT counted as controls; they
  are named in the screen notes only where they replace a control state.

## Coverage rule
Every row in the A3.md tables carries an "Interaction test (code behavior)" cell =
code-level verification that the handler exists and what it does. So
TESTED == FOUND by construction; both numbers are stated per screen and in total.
No device exists → no row claims runtime verification.
