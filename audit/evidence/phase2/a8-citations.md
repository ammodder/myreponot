A8 (TASK-006) evidence — screen sampling + web cross-references (code-level only).
Worktree /home/z/audit-wt/AreenaxNativeAndroid @ 22ce05c · 2026-09-20 · runtime NOT VERIFIED.

========================================================================================
S1. BUTTON-FEEDBACK SAMPLE — 10 screens (pressScale pairing audit)
========================================================================================
Legend: PS = pressScale+clickable(indication=null) scale pair; RIPPLE-RAW = .clickable{} default
M3 ripple; M3-BTN = material3 Button/Surface(onClick)/IconButton (ripple, no scale); ROW =
indication=null WITHOUT pressScale (silent press — acceptable for list rows/links iff web row
has no active:scale).

1. WalletScreen     — PS ×2 (filter pill :351-363, tx row wrapper :496-507);
                      scrim dismiss indication=null :384 (silent by design); M3-BTN 0;
                      SupportFab(offset) M3 ripple via core/ui.
2. DepositScreen    — PS ×2 (swap :246-258, CTA :294-303); AmountPad keys PS (core/ui/AmountPad.kt:96-99).
3. WithdrawScreen   — PS ×4 (pill :182, chip :293, CTA :344-350, row :400).
4. TransferScreen   — PS ×3 (change :312, CTA :469-475, key :497).
5. ConfirmWithdraw  — PS ×1 (CTA :287-293); GuestGateDialog M3-BTN (AccountRequiredDialog:174/187).
6. HomeScreen       — ROW ×2 (:452, :489 quick-action tiles, silent press); M3-BTN 0; SupportFab ripple.
7. LoginScreen      — PS ×2 (guest CTA :285-291 w/ enabled=!guestIn&&!loggingIn; primary CTA :546-557);
                      ROW :219 pw toggle, :330-336 "Create Account" link, :657 link.
8. ProfileScreen    — ROW ×3 (:288 theme row, :501, :543 settings rows — silent);
                      PS ×2; GuestGateDialog M3-BTN.
9. HostSuccessScreen— M3-BTN :240-252 (M3 Button, ripple, no scale) vs web
                      HostSuccessScreen.tsx:92 active:scale-95 → DIVERGENT; RIPPLE-RAW :265.
10. QrSheet         — M3-BTN ×8 (:388, :424, :484, :487, :493, :498, :589, :594; three are
                      enabled=false ghost buttons using M3 disabled defaults).

Sample verdict: 8/10 screens are internally scale-consistent; divergence clusters in (a) M3
components in sheets/success screens/dialogs, (b) 9 RIPPLE-RAW link sites, (c) FAB.

========================================================================================
S2. TRANSITION COVERAGE — per-screen enter animations
========================================================================================
Single AnimatedContent (AppNavHost.kt:63-84) projects ALL 46 ScreenKeys — no screen defines its
own enter/exit transition; per-screen inconsistency = none. Success screens add internal
SuccessAnim scale (SuccessAnim.kt:55-60 tween 420ms — port of web CSS keyframe, shared comp).
WalletScreen filter dropdown uses Popup (no animation, web identical absolute dropdown).
AnimatedContent is not sizeTransform/contentAlignment-animated — crossfade+slide only, as web.

========================================================================================
S3. INSETS COVERAGE TABLE (48 screen files)
========================================================================================
Covered via AppBar (AppBar.kt:68 statusBarsPadding) — 39 files:
  main/{Home,Tournaments,TournamentDetails,MyTournament,Results}
  wallet/{Wallet,Deposit,DepositConfirm,DepositSuccess,Withdraw,ConfirmWithdraw,WithdrawSuccess,Transfer,TransferSuccess,SelectBank}
  social/{Friends,TeamCreation,TeamCreationDone}
  profile/{Profile,EditProfile,MyStats,Achievements,Leaderboard,BindAccount,BindAccountSuccess,AdminPanel,AdminTournaments}
  host/{HostTournament,HostCreation,HostSuccess,HostDetails,HostCards→AppBarHost HostTournamentScreen.kt:232-234}
  info/{About,Terms,Privacy}
Own statusBarsPadding (AppBar=0) — 9 files:
  auth/{Login:127,Signup:117,SignupStep1:118,SignupStep2:111,SignupStep3:139}
  social/{Chat:353,MyTeam:494,Notifications:153,ReferEarn:162,Tasks:142}
  info/OfflineScreen:76
Top status-bar coverage: 48/48 screen files (incl. HostCards via AppBarHost).
Bottom nav-bar coverage: BottomNav (6 root tabs) BottomNav.kt:58; wallet flow screens pad
  navigationBarsPadding themselves (see a8-greps.log E3); remaining full-screen pages
  (About/Terms/Privacy/Leaderboard/Chat list…) rely on their own scrollable padding — whether
  last-row content clears the gesture-nav bar is a RUNTIME question → NOT VERIFIED.
imePadding: Login:129, Signup:119, Step1:120, Step2:113, Step3:141, Transfer:184,
  DepositConfirm:230, Chat:154 (matches web adjustResize equivalents; manifest
  windowSoftInputMode=adjustResize AndroidManifest.xml:31).
consumeWindowInsets: 0 uses — nothing consumes insets at shell level (AppShell paints full-bleed
  canvas by design), so no double-padding risk.

========================================================================================
S4. WEB CROSS-REFS USED
========================================================================================
AppShell.tsx:296-306        page transition 180ms easeOut, x:18→0 enter / 0→-18 exit (one direction).
HostSuccessScreen.tsx:92    CTA class `… active:scale-95` (ripple-less scale press).
SupportFab.tsx:26           `shadow-fab … hover:scale-105 active:scale-95 transition-transform`.
AboutScreen.tsx:73/99/112   `bg-green-50 dark:bg-secondary/15`, `bg-red-50 dark:bg-error/10`,
                            `bg-pink-50 dark:bg-tertiary/10` — dark variants the native
                            AboutScreen pastels (:210/:248/:276/:316) do not implement.
BottomNav.tsx:48            FILL'1' icon rendering → native BottomNav filled=true parity.
ui/sheet.tsx:61             sheet motion: 500ms open / 300ms close (data-[state=open/closed]).
globals.css:488-530         areena-toast-in/out keyframes (mirrored by ToastHost.kt:103-112).

========================================================================================
S5. FINDINGS WITHOUT RUNTIME (NOT VERIFIED list)
========================================================================================
- Actual predictive-back gesture behavior on an Android 14/15/16 device (flag + interception
  prove the code path is absent, but the on-device manifestation — instant cut vs fallthrough —
  was not observed).
- Whether M3 ModalBottomSheet/DatePicker default motion durations feel mismatched vs web's
  300/500ms in practice (code shows defaults kept; no device).
- Whether bottom content on full-screen pages without explicit navigationBarsPadding is
  obscured by gesture navigation (layout arithmetic only; no device).
- Perceived jank of the 260ms AnimatedContent transition (A7 performance scope).
