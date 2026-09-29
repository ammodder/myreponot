# A9 evidence — touch-target measurements (48dp rule)
Method: for every `clickable`/`Surface(onClick)`/`IconButton` site the enclosing modifier chain was
measured (padding + size arithmetic). Threshold: Material/AOSP 48×48dp recommendation (WCAG 2.5.8 AA
minimum = 24dp). Repo @ 22ce05c, read-only.

## Verified sub-48dp standalone clickable targets (18 distinct sites)

| # | Site | Clickable-node measurement | Size |
|---|------|----------------------------|------|
| 1 | core/ui/AppBar.kt:119-127 `AppBarCircleButton` | `Surface(onClick).size(40.dp)` | **40×40** |
| 2 | AppBar.kt:144-145 `BellButton(BellSize.Sm)` | same wrapper, `bellSize = 36.dp` | **36×36** (variant currently unused; all 15 call sites use default Md=40) |
| 3 | core/ui/BottomNav.kt:68 `Box.clickable` (each nav tab) | `.size(56.dp, 40.dp)` | **56×40** (5 tabs, every logged-in screen) |
| 4 | core/ui/ToastHost.kt:204 dismiss | `IconButton(modifier = Modifier.size(36.dp))` | **36×36** |
| 5 | core/ui/RoomInfoSheet.kt:224 copy | `IconButton(modifier = Modifier.size(36.dp))` | **36×36** |
| 6 | social/FriendsScreen.kt:697-706 `HeaderPlainIconButton` | `.padding(8.dp).size(22.dp)` on icon inside `Surface(onClick)` | **38×38** (used :277, :278) |
| 7 | auth/LoginScreen.kt:214-221 password visibility toggle | `Box.size(36.dp).clip(CircleShape).clickable` | **36×36** |
| 8 | auth/SignupStep2Screen.kt:232 same pattern (`// p-2 + 20dp glyph`) | `.size(36.dp)` | **36×36** |
| 9 | profile/EditProfileScreen.kt:528-535 password visibility | `Icon(…).size(24.dp).clickable` — **the Icon itself is the clickable** | **24×24** (worst) |
| 10 | profile/AdminPanelScreen.kt:2076-2082 `SmallQuietChip` | `Surface(onClick).height(28.dp)` | **28dp tall** (View Receipt / View Proof / four-eyes) |
| 11 | profile/GamesAdminSection.kt:324 configure toggle | `Surface.size(36.dp).pressScale` | **36×36** |
| 12 | profile/ResultProofsAdminSection.kt:412-420 `RefreshButton` | `Surface.size(36.dp)` | **36×36** |
| 13 | main/TournamentDetailsScreen.kt:897-907 AND :1630-1640 copy chips | `Box.size(36.dp).clickable` | **36×36 ×2** |
| 14 | social/ChatScreen.kt:317-322 emoji (`.padding(8.dp).size(20.dp)`) and :325-337 send | emoji **36×36**; send `Box.size(40.dp)` inside `Surface(onClick)` | **36 / 40** |
| 15 | profile/ProfileScreen.kt:283-290 social links | `AsyncImage.size(28.dp).clickable` | **28×28** (opens external URLs) |
| 16 | auth/SignupStep1Screen.kt:156, Step2:149, Step3:177 back/step circles | `.size(40.dp)` | **40×40 ×3** |
| 17 | host/HostDetailsScreen.kt:1090 | `.size(36.dp)` | **36×36** |
| 18 | social/ReferEarnScreen.kt:503-514 `HeaderBackCircle` | `Box.size(40.dp)` in `Surface(onClick)` | **40×40** |

Systemic multiplication: AppBar back/bell (#1) render on ~30 page screens; BottomNav (#3) on all 6 roots;
auth toggles on 4+ forms; Send (#14) on every chat screen → **60+ affected instances app-wide**.

Borderline (full-width rows at 40dp height — fail 48dp height, pass width):
- LoginScreen.kt:654 & SignupScreen.kt:547 & SignupStep3Screen.kt:268 — `.height(40.dp) // h-10` link pills.

Compliant references (no violation): AmountPad keys `.height(64.dp)` (AmountPad.kt:95); WalletScreen
quick actions `Box.size(56.dp)` (:497); AppBar 64dp header; form buttons `.height(56.dp)`/`.height(48.dp)`;
ResultProofSheet:144 default `IconButton` (48dp).

Raw scan (clickable nodes co-located with <48dp size/height, pre-noise-filter) saved as console output
during audit; the 18 rows above were each verified by reading the cited lines.
