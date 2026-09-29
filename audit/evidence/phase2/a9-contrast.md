# A9 evidence — WCAG contrast math on token pairs actually used
Source of truth: core/theme/Tokens.kt @ 22ce05c (values byte-identical to SPEC/04 per TASK-004 Attacker).
Formula: WCAG 2.x relative luminance; srgb linearization (c/12.92 ≤0.04045 else ((c+0.055)/1.055)^2.4);
L = 0.2126R+0.7152G+0.0722B; ratio = (L1+0.05)/(L2+0.05). Alpha-composited text: L_eff = L_fg·a + L_bg·(1−a).

```python
def srgb(c):
    c/=255.0
    return c/12.92 if c<=0.04045 else ((c+0.055)/1.055)**2.4
def lum(h,a=1.0,b=None):
    L=0.2126*srgb((h>>16)&255)+0.7152*srgb((h>>8)&255)+0.0722*srgb(h&255)
    if a<1.0 and b is not None: L=L*a+lum(b)*(1-a)
    return L
def ratio(f,b,fa=1.0):
    x,y=sorted([lum(f,fa,b),lum(b)],reverse=True); return (x+.05)/(y+.05)
```

## Full table (threshold 4.5:1 normal text, 3.0:1 large text ≥18.66px bold / ≥24px; icons/UI 3.0:1)

| Pair (fg on bg) | Ratio | Verdict |
|---|---|---|
| L onSurface #0B1C30 on background #F8F9FF | 16.34 | PASS |
| L onSurface on white card #FFFFFF | 17.17 | PASS |
| L onSurface on container #E5EEFF | 14.72 | PASS |
| L onSurfaceVariant #434655 on background | 8.90 | PASS |
| L onSurfaceVariant on white card | 9.35 | PASS |
| L onSurfaceVariant on containerHigh #DCE9FF | 7.63 | PASS |
| **L onSurfaceVariant @alpha 0.7 on white** (ConfirmWithdrawScreen.kt:278, DepositConfirmScreen.kt:458) | **2.67** | **FAIL 4.5** |
| L same @0.7 on background #F8F9FF | 2.64 | **FAIL 4.5** |
| **L onSurfaceVariant @0.6 on background** (AboutScreen.kt:454 footer) | **2.15** | **FAIL 4.5** |
| L error #BA1A1A on background / white | 6.15 / 6.46 | PASS |
| L secondary #006C49 on secondary@0.15 chip | 6.17 | PASS |
| L white on primary button #004AC6 | 7.51 | PASS |
| L onPrimaryContainer #EEEFFF on primaryContainer #2563EB | 4.54 | PASS |
| L onPrimaryFixed #00174B on primaryFixed #DBE1FF | 13.25 | PASS |
| D onSurface #F5F5F7 on background #0A0A0C | 18.17 | PASS |
| D onSurface on dark canvas #060608 (DarkSurfaceDim; AppShell.kt:74 comment) | 18.59 | PASS |
| D onSurface on container #1B1B1F | 15.77 | PASS |
| D onSurfaceVariant #9D9DA6 on background | 7.35 | PASS |
| D onSurfaceVariant on containerLow #17171B / container #1B1B1F | 6.65 / 6.38 | PASS |
| D onSurfaceVariant on containerHigh #232327 / Highest #2A2A2F | 5.82 / 5.31 | PASS |
| D onSurfaceVariant @0.7 on container #1B1B1F | 4.77 | PASS |
| **D onSurfaceVariant @0.6 on container #1B1B1F** | **4.23** | large-only |
| D onSurfaceVariant @0.6 on background #0A0A0C | 4.81 | PASS |
| D onSurfaceVariant @0.9 (toast body, ToastHost.kt:197) on containerLowest #131316 | 6.30 | PASS |
| **D white #FFFFFF on primary button #2F6BFF (DarkPrimary)** | **4.4988** | **FAIL 4.5 by 0.0012 — large-text PASS** |
| D error #FF5449 on background / #131316 | 6.23 / 5.84 | PASS |
| D secondary #4EDEA3 on background / on chip | 11.59 / 10.06 | PASS |
| D onSecondary #002113 on secondary #4EDEA3 | 10.03 | PASS |
| D onPrimaryContainer #DBE1FF on primaryContainer #004AC6 | 5.81 | PASS |
| D onErrorContainer #FFDAD6 on errorContainer #93000A | 7.24 | PASS |
| D onPrimaryFixed #DBE1FF on primaryFixed #182338 | 12.14 | PASS |
| D tertiary #AD0033 on white | 7.43 | PASS |
| D onTertiaryContainer #FFECEC on tertiaryContainer #D22348 | 4.53 | PASS |
| L toast icon-chip #059669 on #ECFDF5 (ToastHost.kt:173/185) | 3.58 | icon pair → passes 3.0 UI threshold |
| D toast icon-chip #6EE7B7 on #064E3B | 6.38 | PASS |
| **L MyTeam note #94A3B8 11sp on white** (MyTeamScreen.kt:409) | **2.56** | **FAIL 4.5** |
| L same on lavender card #EDEDF8 | 2.21 | **FAIL 4.5** |
| D MyTeam note on container #1B1B1F | 6.70 | PASS |

Exact margin check (run during audit): `ratio(0xFFFFFF,0x2F6BFF) = 4.4988` (< 4.5, ≥ 3.0).

Excluded: WalletScreen.kt:212/:218 #B4C5FF/#FFDADB are Canvas glow decorations (glow(), :212/:218), not text.

## alpha-faded text sites found (the pattern behind failures 1-3)
- onSurfaceVariant.copy(alpha=0.7): ConfirmWithdrawScreen.kt:278, DepositConfirmScreen.kt:458
- onSurfaceVariant.copy(alpha=0.6): AboutScreen.kt:454
- onSurfaceVariant.copy(alpha=0.8): MyStatsScreen.kt:229 (light: ratio ≈ 3.4 on white — also below 4.5; dark pass)
- Text alpha(0.9f) modifier: ToastHost.kt:197 (passes: 5.10 light / 6.30 dark)
- Color(0xFF94A3B8) hardcoded: MyTeamScreen.kt:401 (tint), :409 (text)

Caveat (NOT VERIFIED): runtime dark-mode canvas radial glow (AppShell.kt:74-77) slightly lightens the
#060608/#0A0A0C backdrop — changes all dark ratios by <5% and flips no verdict except already-marginal rows.
