# 04 — DESIGN TOKENS (globals.css + agent-ctx/DESIGN.md → Compose Material 3 mapping)

> Source: `src/app/globals.css` (`@theme` block + `:root` + `:root.dark` + custom utilities) and
> `agent-ctx/DESIGN.md`. Font: **Hanken Grotesk** (Google Fonts, loaded via next/font →
> `--font-hanken`; layout also loads Material Symbols Outlined + Rounded variable fonts).
> Icons: Material Symbols (see 03-COMPONENTS.md for the name list).
>
> Compose mapping suggestion: expose these as a custom `AreenaxColors` object with
> `LightAreenaxColors` / `DarkAreenaxColors`, feed the M3 `lightColorScheme`/`darkColorScheme`
> where fields correspond, and keep the extra tokens (surface-container-*, fixed tones,
> lavender variants, balance-chip) as custom extension values.

---

## 1. Light theme (`:root`, the default)

### Surfaces & ink
| Token | Hex | Compose note |
|---|---|---|
| `--color-background` | `#f8f9ff` | M3 `background`/`surface` base |
| `--color-surface` | `#f8f9ff` | = background |
| `--color-surface-bright` | `#f8f9ff` | |
| `--color-surface-dim` | `#cbdbf5` | |
| `--color-surface-container-lowest` | `#ffffff` | cards / sheets / nav (pure white) |
| `--color-surface-container-low` | `#eff4ff` | |
| `--color-surface-container` | `#e5eeff` | |
| `--color-surface-container-high` | `#dce9ff` | |
| `--color-surface-container-highest` | `#d3e4fe` | |
| `--color-surface-variant` | `#d3e4fe` | = container-highest |
| `--color-on-surface` | `#0b1c30` | primary ink (navy) |
| `--color-on-surface-variant` | `#434655` | secondary ink |
| `--color-on-background` | `#0b1c30` | |

### Primary (blue)
| Token | Hex |
|---|---|
| `--color-primary` | `#004ac6` |
| `--color-on-primary` | `#ffffff` |
| `--color-primary-container` | `#2563eb` |
| `--color-on-primary-container` | `#eeefff` |
| `--color-primary-fixed` | `#dbe1ff` |
| `--color-primary-fixed-dim` | `#b4c5ff` |
| `--color-on-primary-fixed` | `#00174b` |
| `--color-on-primary-fixed-variant` | `#003ea8` |
| `--color-inverse-primary` | `#b4c5ff` |
| `--color-surface-tint` | `#0053db` |

### Secondary (green / money-in)
| Token | Hex |
|---|---|
| `--color-secondary` | `#006c49` |
| `--color-on-secondary` | `#ffffff` |
| `--color-secondary-container` | `#6cf8bb` |
| `--color-on-secondary-container` | `#00714d` |
| `--color-secondary-fixed` | `#6ffbbe` |
| `--color-secondary-fixed-dim` | `#4edea3` |
| `--color-on-secondary-fixed` | `#002113` |
| `--color-on-secondary-fixed-variant` | `#005236` |

### Tertiary (red/rose accents)
| Token | Hex |
|---|---|
| `--color-tertiary` | `#ad0033` |
| `--color-on-tertiary` | `#ffffff` |
| `--color-tertiary-container` | `#d22348` |
| `--color-on-tertiary-container` | `#ffecec` |
| `--color-tertiary-fixed` | `#ffdadb` |
| `--color-tertiary-fixed-dim` | `#ffb2b7` |
| `--color-on-tertiary-fixed` | `#40000d` |
| `--color-on-tertiary-fixed-variant` | `#92002a` |

### Error, outline, inverse
| Token | Hex |
|---|---|
| `--color-error` | `#ba1a1a` |
| `--color-on-error` | `#ffffff` |
| `--color-error-container` | `#ffdad6` |
| `--color-on-error-container` | `#93000a` |
| `--color-outline` | `#737686` |
| `--color-outline-variant` | `#c3c6d7` |
| `--color-inverse-surface` | `#213145` |
| `--color-inverse-on-surface` | `#eaf1ff` |

### AREENAX custom colors
| Token | Hex | Usage |
|---|---|---|
| `--color-surface-lavender` | `#faf8ff` | auth screens root bg |
| `--color-surface-container-lavender` | `#ededf8` | ALL rounded text-field fills |
| `--color-surface-container-high-lavender` | `#e7e7f2` | progress-bar track, hover fills |
| `--color-balance-chip` | `#f0f4ff` | wallet BalanceChip pill, tournament tabs container, JOINED chips |
| WhatsApp green | `#25D366` | WhatsAppFab (deprecated) |
| `tx-in-icon` bg | `#ecfdf5` | wallet money-in icon circle |
| `tx-in-icon` fg | `#059669` | " |
| Trophy gradient | `#ffd700 → #daa520` (135°) | `.trophy-icon` |
| Leaderboard crown | `#F59E0B` | LeaderboardScreen crown SVG |
| Gold medal | `#D4AF37` (rgb(212,175,55)) | MyStats medal icon |

### shadcn-compat vars (light)
`--radius: 0.625rem` (10px); border/input `#c3c6d7`; ring `#004ac6`; destructive `#ba1a1a`;
secondary/muted/accent `#eff4ff`; card/popover `#ffffff`.

---

## 2. Dark theme (`:root.dark`, "Midnight Pills") — toggled by Profile switch, persisted as
`localStorage["areena-theme"] = "dark"` (native: DataStore bool)

### Surfaces & ink (dark)
| Token | Hex |
|---|---|
| `--color-background` | `#0a0a0c` |
| `--color-surface` | `#0a0a0c` |
| `--color-surface-bright` | `#232327` |
| `--color-surface-dim` | `#060608` |
| `--color-surface-container-lowest` | `#131316` |
| `--color-surface-container-low` | `#17171b` |
| `--color-surface-container` | `#1b1b1f` |
| `--color-surface-container-high` | `#232327` |
| `--color-surface-container-highest` | `#2a2a2f` |
| `--color-surface-variant` | `#232327` |
| `--color-on-surface` | `#f5f5f7` |
| `--color-on-surface-variant` | `#9d9da6` |
| `--color-on-background` | `#f5f5f7` |
| `--color-balance-chip` | `#16181d` |
| `--color-surface-lavender` | `#0a0a0c` |
| `--color-surface-container-lavender` | `#1b1b1f` |
| `--color-surface-container-high-lavender` | `#232327` |

### Primary (dark — ONE vivid accent)
`--color-primary #2f6bff` · `on-primary #ffffff` · `primary-container #004ac6` ·
`on-primary-container #dbe1ff` · `primary-fixed #182338` · `primary-fixed-dim #101a2e` ·
`on-primary-fixed #dbe1ff` · `on-primary-fixed-variant #9db9ff` · `inverse-primary #9db9ff` ·
`surface-tint #2f6bff`

### Secondary (dark)
`--color-secondary #4edea3` · `on-secondary #002113` · `secondary-container #0e3a2a` ·
`on-secondary-container #6ffbbe` · `secondary-fixed #0e3a2a` · `secondary-fixed-dim #0a2b1e` ·
`on-secondary-fixed #6ffbbe` · `on-secondary-fixed-variant #4edea3`

### Error (dark)
`--color-error #ff5449` · `on-error #ffffff` · `error-container #93000a` ·
`on-error-container #ffdad6`

### Outline (dark)
`--color-outline #3a3a41` · `--color-outline-variant #26262b` · `inverse-surface #ececf0` ·
`inverse-on-surface #141416`

### Dark canvas & ambient (CSS layer, native equivalent = a root Box background)
- App-wide dark canvas: `#060608` with a fixed top glow:
  `radial-gradient(120% 55% at 50% 0%, rgba(47,107,255,0.07) 0%, transparent 60%)`.
- `.dark .screen-root` and friends are forced transparent so the wallpaper is continuous —
  native: paint the ambient ONCE behind the NavHost; screens use `Color.Transparent`.
- Dark "re-ink": hardcoded light hexes (`#0b1c30`, `#434655`, `#edf2f7`, `#e2e2ec`, …) are
  remapped to tokens in dark mode — native avoids the problem entirely by using tokens only.
- Dark wallet money-in icon: bg `#064e3b`, fg `#6ee7b7`; dark emerald status text `#6ee7b7`.

---

## 3. Typography scale (`@theme` text tokens; rem values, root font is fluid — see §5)

| Class | Size | Line height | Weight | Letter spacing |
|---|---|---|---|---|
| `text-display-lg` | 2rem (32px) | 2.5rem (40px) | 700 | -0.02em |
| `text-headline-lg` | 1.5rem (24px) | 2rem | 700 | — |
| `text-headline-lg-mobile` | 1.375rem (22px) | 1.75rem | 700 | — |
| `text-headline-md` | 1.25rem (20px) | 1.75rem | 600 | — |
| `text-body-lg` | 1rem (16px) | 1.5rem | 500 | — |
| `text-body-md` | 0.875rem (14px) | 1.25rem | 400 | — |
| `text-label-lg` | 0.875rem (14px) | 1rem | 600 | — |
| `text-label-md` | 0.75rem (12px) | 0.875rem | 500 | 0.01em |
| `text-label-sm` | 0.75rem (12px) | 1rem | 600 | 0.05em |

Other recurring styles (ad-hoc in screens): page titles also use `text-[1.25rem] leading-[1.625rem]
font-semibold`; hero amounts `text-[2rem]..text-[3rem] font-bold tracking-tight`; card stat
values `text-headline-md font-bold`; chips `text-[0.625rem]..[0.75rem]` bold uppercase with
`tracking-wider/widest`; toast title 15px bold / description 13px.

**Font family:** `"Hanken Grotesk", sans-serif` — bundle the variable TTF (weights 100–900) or
Google Fonts download; set as the Compose Typography default family.

---

## 4. Radii, borders, shadows, spacing

### Radii (from usage)
| Usage | Value |
|---|---|
| `--radius` (shadcn base) | 10px (sm 6 / md 8 / lg 10 / xl 14 derived) |
| Bottom sheets | `rounded-t-[2rem]` (32px: RoomSheet, JoinTeamSheet; 28px AmountKeyboard; 24px AmountKeyboardSheet, DateTimePickerModal) |
| Cards (large) | `rounded-[1.5rem]` (24px) — tournament cards, stat cards, list cards |
| Team/admin cards | `rounded-3xl` (24px) |
| Small cards/inputs | `rounded-2xl` (16px), `rounded-xl` (12px) |
| ALL text fields | `rounded-full` (pill) |
| Buttons | `rounded-full` pills (heights: 40/44/48/52/56px; primary CTAs h-14/h-[3.5rem]) |
| Chips/badges | `rounded-full` |
| Icon tiles | `rounded-xl`/`rounded-2xl`; circles for avatars/icon buttons (w-10 h-10 standard) |
| Dialog cards | `rounded-[1.75rem]` (28px); DateTimePicker `rounded-[24px]` |

### Shadows (custom utilities + tokens)
| Name | Value | Usage |
|---|---|---|
| `shadow-card` (token) | `0 4px 6px -1px rgba(0,0,0,0.05), 0 2px 4px -1px rgba(0,0,0,0.03)` | game cards, hero banner |
| `--shadow-fab` | `0 10px 15px -3px rgba(0,74,198,0.3), 0 4px 6px -2px rgba(0,74,198,0.15)` | SupportFab |
| `.card-shadow` | `0 4px 20px rgba(11,28,48,0.05)` | universal card/appbar-button shadow |
| `.premium-shadow` | `0 10px 40px -10px rgba(0,74,198,0.1)` | wallet transactions card |
| `.nav-shadow` | `0 -4px 20px rgba(11,28,48,0.05)` | BottomNav |
| `.glass-tab` | `0 2px 8px rgba(0,74,198,0.08)` | active tab pills |
| glass-card | `0 4px 20px rgba(11,28,48,0.05)` + `outline-variant/40` border | (legacy solid card) |
| Toast | `0 16px 48px -16px rgba(11,28,48,0.35)` (dark: `rgba(0,0,0,0.8)`) | |
| Sheets | `0 -8px 30px rgba(0,0,0,0.12)` (AmountKeyboard `0 -12px 40px rgba(0,0,0,0.14)`) | |
| Key/amount pad key | `0 2px 10px rgba(0,0,0,0.04)` | |
| Primary CTA | `0 8px 20px rgba(0,74,198,0.25)` (recurring) | |
| Hero (details) | `0px 4px 20px rgba(0,0,0,0.04)` | |

### Spacing conventions
- Screen gutters: `px-4` (16px) everywhere; section gaps `gap-3`..`gap-6` (12–24px).
- Bottom clearance: `pb-24` (96px) on every bottom-nav screen (nav ≈ 76px + support FAB);
  `pb-28/32` on screens with floating bottom bars.
- Card padding: `p-4`/`p-5` (16/20px); appbar h-16 (64px) px-4.
- Fluid root font-size: `clamp(12px, 4vw, 16.5px)` — 320px→12.8, 360px→14.4, 400px→16 (design
  size), ≥412px→16.5; desktop 17px. **Native:** use `sp`/`dp` directly (Compose scales with the
  system font scale) — design reference width is 400dp; the 480px max frame = full-bleed phone.
- Hidden scrollbars (`no-scrollbar`); hairline dividers `border-outline-variant/10..30`
  or `#edf2f7`-family (dark re-inked).

---

## 5. Gradients & ambient glows (exact definitions)

| Name | Definition | Usage |
|---|---|---|
| Auth aurora | `radial-gradient(85% 52% at 50% 58%, rgba(59,130,246,0.40) 0%, rgba(37,99,235,0.16) 48%, rgba(3,11,29,0) 100%), linear-gradient(180deg, #030b1d 0%, #051740 38%, #0a2f7a 60%, #04102e 84%, #020817 100%)` | AuthChrome backdrop (reference) |
| Dark canvas glow | `radial-gradient(120% 55% at 50% 0%, rgba(47,107,255,0.07) 0%, transparent 60%), #060608` | app-wide dark background |
| `.bg-areena` (home) | `radial-gradient(circle at 50% 0%, var(--color-surface-container-low) 0%, var(--color-surface-lavender) 50%)` on `--color-surface-lavender` | HomeScreen |
| `.bg-ambient` (profile) | `radial-gradient(circle at 0% 0%, #ffdadb20 0%, transparent 50%), radial-gradient(circle at 100% 100%, #dbe1ff30 0%, transparent 50%), #f8f9ff` | ProfileScreen |
| Deposit/confirm pages | `radial-gradient(100% 100% at 50% 0%, var(--color-surface-container-low) 0%, var(--color-background) 100%)` (confirm variant uses `surface-container`) | wallet screens |
| MyTeam | `radial-gradient(circle at top right, color-mix(in srgb, var(--color-primary-fixed-dim) 40%, transparent) 0%, transparent 50%), radial-gradient(circle at bottom left, rgba(108,248,187,0.2) 0%, transparent 50%), var(--color-background)` | MyTeamScreen |
| Referrals | `radial-gradient(circle at 50% -20%, color-mix(in srgb, var(--color-primary-fixed) 60%, transparent) 0%, var(--color-background) 70%)` | ReferEarnScreen |
| Leaderboard blobs | `radial-gradient(circle, color-mix(in srgb, var(--color-primary-container) 10%, transparent) 0%, transparent 70%)` blurred 40px circles (300px) | LeaderboardScreen |
| Tournament banner scrim | `linear-gradient(to top, rgba(0,0,0,0.7), transparent)` | cards/heroes |
| Success glow | `bg-primary/25 blur-2xl` circle + `shadow 0 0 32px color-mix(primary 45%)` | SuccessAnim, HostSuccess |
| Offline page | `linear-gradient(180deg, #f0f4f8 0%, #dbe4ec 100%)` | OfflineScreen |
| Desktop backdrop (n/a native) | `radial-gradient(circle at 15% 20%, rgba(47,107,255,0.14), transparent 45%), radial-gradient(circle at 85% 80%, rgba(47,107,255,0.09), transparent 45%), linear-gradient(160deg,#060608,#0c0f16)` + 22px dot grid | AppShell desktop dressing |
| `.progress-bar-gradient` | `linear-gradient(90deg, #004ac6 0%, #2563eb 100%)` | (utility, rarely used) |

---

## 6. Motion

| Effect | Spec |
|---|---|
| Screen transition | fade + x-slide 18px, 180ms easeOut (exit mirrored) |
| Bottom sheets | slide-up 250ms easeOut; backdrop fade 200ms; drag handle |
| Amount keyboard | 320ms tween `cubic-bezier(0.22, 1, 0.36, 1)`; sheet variant 280ms `cubic-bezier(0.32, 0.72, 0, 1)` |
| Toast | in: 450ms `cubic-bezier(0.21, 1.02, 0.73, 1)` from top (mobile); out: 300ms ease-in; 5s timer bar (`scaleX 1→0`, pauses on hover/focus) |
| `.fade-in` | 300ms ease, translateY(4px)→0 (home tab content) |
| Success check draw | stroke 600ms `cubic-bezier(0.65,0,0.45,1)` + check 300ms @0.8s (legacy) |
| Live dot | `animate-ping` red halo (Match Processing chips) |
| Press feedback | `active:scale-95..0.98` on nearly every button (Compose: scale on pressed interaction) |
| Host success hero | CSS `animate-bounce` on the glow circle |

---

## 7. Material-3 → Compose mapping cheat-sheet

| Web token | M3 ColorScheme slot |
|---|---|
| primary / on-primary | primary / onPrimary |
| primary-container / on-primary-container | primaryContainer / onPrimaryContainer |
| secondary / on-secondary / secondary-container | secondary* family |
| tertiary family | tertiary* family |
| error family | error* family |
| background / on-background | background / onBackground |
| surface-container-lowest → highest | surfaceContainerLowest → surfaceContainerHighest (Compose M3 1.2+) |
| outline / outline-variant | outline / outlineVariant |
| inverse-surface / inverse-on-surface / inverse-primary | same slots |
| surface-lavender / *-lavender / balance-chip | **custom extension colors** (AreenaxColors) |
| primary-fixed family | custom extension colors |
| destructive | error |
| `tx-in-icon` emerald pair | custom (successChip) colors |

Dark mode: identical token names, dark hex tables above; remember the custom dark canvas glow
behind everything, and that `on-primary` stays WHITE in dark (blue + white CTAs).
