# A9 evidence — TalkBack static analysis (AreenaxNativeAndroid @ audit/production-readiness 22ce05c)
Date: 2026-09-20 · Agent: A9 · Read-only (no device → all runtime TalkBack behavior = NOT VERIFIED)

## 1. contentDescription coverage (re-verification of A1 "101 sites / 258 cds / 0 truly missing")

Command: `rg -c 'contentDescription' app/src/main/java` (from java/ root)
Result: **260 occurrences in 62 files** (A1 recorded 258 — delta +2, counting-method drift only; same files class).

Decorative count: `rg -c 'contentDescription = null'` → **171** explicit-null (decorative) icons.

Icon-call universe: `rg -c 'Icon\('` → 240 (includes `AreenaxIcon(` substring matches).

### 10-screen sample + core/ui (Icon/AreenaxIcon calls lacking a contentDescription parameter entirely)
```
HomeScreen.kt                    icon calls=0   no-cd-param=0
LoginScreen.kt                   icon calls=3   no-cd-param=0
WalletScreen.kt                  icon calls=3   no-cd-param=0
DepositScreen.kt                 icon calls=2   no-cd-param=0
TournamentDetailsScreen.kt       icon calls=25  no-cd-param=0
FriendsScreen.kt                 icon calls=8   no-cd-param=0
ChatScreen.kt                    icon calls=7   no-cd-param=0
ProfileScreen.kt                 icon calls=4   no-cd-param=0
AdminPanelScreen.kt              icon calls=16  no-cd-param=0
AboutScreen.kt                   icon calls=7   no-cd-param=0
TOTAL sampled: 75 icon calls, 0 without cd param
core/ui: 36 icon calls, 7 without cd param
```
The 7 core/ui cd-less icons (positional `null`, all decorative with adjacent text label):
- BankSelectSheet.kt:136 (`ic_search` in search pill with placeholder text)
- BankSelectSheet.kt:299 (`ic_check` selected-bank tick inside labeled row)
- QrSheet.kt:221 (`ic_share` + Text("Share")), :404 (`ic_qr_code_2` + Text("Open Camera")), :435 (`ic_search` + Text("Find"))
- RoomInfoSheet.kt:134 (`error` icon above "Could not load room details…"), :148 (`schedule` icon above "Room details not shared yet")

A heuristic scan for "Icon(cd=null) inside clickable context" produced 67 hits; manual inspection of
20 of them (WalletScreen:369, TournamentDetails:782, AboutScreen:391, NotificationsScreen:351,
EditProfileScreen:345, LoginScreen:669, SignupScreen:562, FriendsScreen:293/575/1126, WithdrawScreen:200/213,
TeamCreationScreen:460, DepositConfirmScreen:332, AdminPanelScreen:723, ResultProofSheet:172,
ProfileScreen:323/511, BankSelectSheet:325, BindAccountScreen:378) showed **every one is a decorative
icon inside a row/button that carries its own Text label** → A1's "0 truly missing contentDescription" HOLDS statically.
(Example, WalletScreen.kt:368-373: `Text("Filter" …)` then `AreenaxIcon("tune", contentDescription = null …)`.)

## 2. Semantics APIs — all zero
```
$ rg -n 'clearAndSetSemantics|Modifier\.semantics|\.semantics \{|liveRegion|stateDescription|\.heading\(\)|toggleable|selectable|Role\.' app/src/main/java
→ 0 matches
$ rg -n 'semantics' app/src/main/java        → 2 hits, both prose comments (ApiClient.kt:23, AppNavHost.kt:57)
$ rg -n 'mergeDescendants|Role' app/src/main/java → 27 hits, ALL app-RBAC `Roles` object (Models.kt / screens) —
  zero Compose `Role.*` usage.
```
- `liveRegion`: **0** — toasts (core/ui/ToastHost.kt) are plain Text in a Surface at composition end
  (AppShell.kt:169) with no semantics → no announcement mechanism coded.
- `stateDescription`: **0** — no custom toggle conveys on/off state to accessibility.
- `heading()`: **0** — no section title is marked as a heading (TalkBack heading navigation impossible).

## 3. Custom-drawn / custom-control semantics

### ToastHost.kt (timer bar + dismiss)
- ToastCard: title Text (:190) + description Text (:196) + dismiss IconButton(:204, cd="Dismiss", 36dp).
- 3dp timer bar :214-229 — two `Box.background()` — no semantics (invisible to TalkBack; progress not
  conveyed — accepted as decorative, but nothing announces auto-dismiss).
- No `liveRegion` anywhere → statically, a toast appearing top-center will NOT be announced.

### AchievementsScreen.kt:187-205 progress ring
`Canvas(Modifier.size(64.dp))` with two `drawArc` calls (ring :191, progress :200). No semantics on the
Canvas → TalkBack skips it; the same info is duplicated as text: `"$percent% Completed"` (:176) and
`"$unlockedCount of $total Unlocked"` (:181). VERDICT: acceptable (decorative, info in text), no fix needed.

Other Canvas draws (TermsScreen:111/116, AboutScreen:119/124, OfflineScreen:172, HomeScreen:313,
HostSuccessScreen:154-155) are decorative circles/glows with text nearby — same verdict.

### ThemeSwitch (ProfileScreen.kt:534-553) — dark-mode toggle
```
Box(Modifier.size(width=40.dp, height=24.dp).clip(CircleShape)
    .background(if (dark) primary else surfaceContainerHigh)
    .clickable(interactionSource, indication = null) { onToggle() })
```
No text, no contentDescription, no `Role.Switch`, no stateDescription. Used inside `MenuRow("Theme"…)`
(ProfileScreen.kt:224-229) where MenuRow itself is a clickable Row (:496-503) → nested clickables:
TalkBack gets (a) row "Theme … double-tap to activate" and (b) a separate **unlabeled** 40×24dp target.
State (dark/light) not conveyed.

### AgreementRow (SignupStep3Screen.kt:467-527) — signup-gating "checkbox"
20×20dp Box w/ check icon (cd=null); Row is clickable (no `toggleable`, no Role, no state text);
label is an AnnotatedString with inline Terms/Privacy links. TalkBack: no checked/unchecked state, no
checkbox role. This gate enables "Complete Account" — core signup flow.

### AdminPanel toggles
AdminPanelScreen.kt:1306/2302 use `AreenaxIcon(if (on) "toggle_on" else "toggle_off")` inside labeled
pills — icon is decorative (cd=null per scan); no stateDescription; state only via icon glyph.

## 4. Focus traversal (static risks)
- AppShell.kt:161-169: Column[AppBar?, AppNavHost(weight 1f), BottomNav?] then `ToastHost(…fillMaxSize())`
  last → toast node is composed last (TalkBack reaches it last; visually on top). OK for traversal, but
  combined with 0 liveRegion the toast is easy to miss.
- Nested clickable rows: MenuRow(onClick) containing ThemeSwitch (ProfileScreen:224-229+534);
  AgreementText inside AgreementRow (own link handlers) — extra focus targets inside labeled targets,
  no mergeDescendants anywhere.
- `clickable(indication = null)` is the dominant pattern (dozens of sites) — no ripple; with no focus
  outline override, keyboard/switch-access users get no visible focus feedback (runtime NOT VERIFIED).
- 180 `maxLines/TextOverflow` sites — ellipsized text keeps full semantics for TalkBack (Compose), so
  info loss is visual-only.

## 5. BottomNav labels
BottomNav.kt:84 `Icon(…, contentDescription = tab.contentDescription)` — tabs carry labels. ✓
AppBar back (:111 cd="Back") / bell (:156 cd="Notifications") labeled. ✓
