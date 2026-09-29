# A9 evidence — font-scale (sp vs dp, fixed heights, line-height)

## 1. fontSize in dp — ZERO occurrences (PASS)
```
$ rg -n 'fontSize = [0-9]+\.dp' app/src/main/java   → 0 matches
$ rg    'fontSize = [0-9]+\.sp' app/src/main/java | wc -l → 192
```
All explicit font sizes are sp and scale with the system font scale. No dp-text finding.

## 2. Typography scale (core/theme/Type.kt) — proportional, some tight leading
```
:55-56  headlineLg 32sp / 40sp   :72-73  22sp/28sp   :80-81 20sp/28sp
:88-89  16sp/24sp   :96-97 14sp/20sp
:104-105 labelMd 14sp / lineHeight 16sp   (1.14× — tight)
:112-113 labelSm 12sp / lineHeight 14sp   (1.17× — tight)
:146-147 toastTitle 15sp, toastBody 13sp (no explicit lineHeight → default)
:165-171 M3 mapping (headlineSmall/titleLarge = 18sp/24sp, bodySmall = labelMd @16sp)
```
lineHeight is sp → scales with font scale (no hard cap found; no `lineHeight` in dp anywhere).
Tight 1.14-1.17× leading is a legibility note, not a scale blocker.

## 3. Fixed-height rows containing text — clipping risk at 1.3-2.0×
Scan: `.height(N.dp)` with 8 ≤ N ≤ 48 and a `Text(` within the following 13 lines → **82 raw sites**
(most are `Spacer` noise; the real fixed text-rows are listed below). Full `.height(N.dp)` universe: 306
sites; only 14 `heightIn(...)` sites (min-height — content can grow, preferred pattern).

Worst offenders (verified by reading the cited lines):
| Height | Site | Content | 2.0× behavior |
|---|---|---|---|
| 28dp | AdminPanelScreen.kt:2082 `SmallQuietChip` (`Surface.onClick.height(28.dp)`, padding(h=10dp)) | 11-12sp label + 16dp icon | label ≈ 24-28sp tall + vertical padding → clipped/overflowing chip |
| 36dp | AdminPanelScreen.kt:2041, :1262 filter/action chips (`height(36.dp)` + Text) | 13-14sp labels | ~28sp text + padding ≈ 36-40dp → tight-to-clipped |
| 40dp | SignupStep3Screen.kt:268 (`h-10` "Go to Login" pill), AdminPanelScreen.kt:2126 admin input Row, AdminPanelScreen.kt:1364, LoginScreen.kt:654, SignupScreen.kt:547 link rows | 14sp labels / inputs | 28sp text + padding → borderline clipped |
| 42dp | AdminTournamentsScreen.kt:782 | text row | borderline |
| 44dp | AdminPanelScreen.kt:719/:751/:1302/:1413/:1550, AdminTournamentsScreen.kt:560/:682, GamesAdminSection.kt:709, ProfileScreen.kt:441 (HeaderPillButton) | 14-16sp labels | 32sp text ≈ 40-44dp → borderline clipped |
| 48dp | 18 button sites (h-12: SignupStep1:479, HostCreation:324/:372/:1113/:1129, AdminPanel:2179, dialogs, etc.) | 16sp labels | 32sp ≈ 40dp + leading → mostly OK, descender-tight at 2.0× |
| 64dp | AppBar header rows (e.g. NotificationsScreen.kt:157) | headlineMd 22sp/28sp | 56sp ≈ 56dp < 64dp → OK even at 2.0× |

Compliant: `heightIn(min=…)` on the two EmptyState columns (TASK-004 P-sites); buttons at 56dp; AmountPad 64dp;
BottomNav icon-only (no text to clip); scrollable Column/verticalScroll screens grow.

## 4. Truncation
180 `maxLines`/`TextOverflow` sites (rg count over ui/**) — visual truncation grows at large font scale;
TalkBack still reads the full text (Compose Text semantics), so info loss is visual-only. NOT VERIFIED
at runtime (no device).

## 5. Runtime rendering at 1.3-2.0× — NOT VERIFIED (no device/emulator). All above is static measurement.
