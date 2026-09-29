# ICON-AUDIT — PHASE 2 dedicated Icon Audit (owner decision Q9, TASK-006)

Agent: ICON (read-only for app code). Date: 2026-09-20.
Tool: `audit/tools/icon_audit.py` (python3, stdlib-only; app/** untouched — writes only under `audit/**`).
Subcommands run: `scan-native` → `scan-web` → `cross-check` → `fetch-missing` (×2, cache proof) + retry demo.

---

## 1. Headline numbers

| Metric | Value |
|---|---|
| Native icon files (res/) | **155** = 154 vector XML + 1 PNG (`areenax_logo.png`) |
| — of which Material Symbols `ic_*` | 135 (incl. **20 `_fill` variants**) |
| — of which lucide `ic_lucide_*` | 14 |
| — of which inline-era `ic_inline_*` | 5 (all hard-referenced) |
| mipmap dirs / files | **0 / 0** (launcher icon = `android:icon="@drawable/areenax_logo"` 512×512 RGBA PNG) |
| density variants | none — flat `res/drawable/` only (no `-mdpi`…`-xxxhdpi`); vectors are density-independent, so only the launcher PNG is density-sensitive |
| Inline-drawn icons (Kotlin Canvas/graphicsLayer/rotate) | 12 code sites (9 logical icons) — list in §5 |
| Unique `R.drawable.*` hard refs in Kotlin | 53 (+ manifest ref) |
| Referenced-but-missing drawables | **0** |
| Web panel distinct Material Symbols glyphs (live scope) | **115** — fresh scan reproduces the build-era `tools/reports/inventory.json` **exactly (115/115 names, 20/20 FILL glyphs)** |
| Web glyphs with FILL'1' usage | 20 |
| Design-HTML (upload/pages_extracted/pages) distinct glyphs | 78 |
| MISSING vs **live web panel** | **0** |
| MISSING fill variants | **0** |
| MISMATCHED-STYLE | **1** (`groups` — latent, dead web component; see ICON-04) |
| MISSING vs **design pages only** | **3** (`qr_code`, `sports_handball`, `unfold_more`) |
| EXTRA (native has, web never names, code never refs) | **14** (all `ic_lucide_*`) + 1 unreachable fill (`ic_groups_fill`) |
| Remote-loaded images in app | **4** hardcoded social-icon URLs (ProfileScreen, `AsyncImage`) |
| Fetch run 1 (cold) | **3 downloaded / 0 cache hits / 0 failed** |
| Fetch run 2 (warm) | **0 downloaded / 3 cache hits / 0 failed** ← cache proof |
| Retry demo (404, same repo) | 3 attempts, backoff 1s→2s→4s, wall 3.8 s, clean failure |

**Findings by severity: 0 BLOCKER · 0 MAJOR · 2 MINOR · 4 NOTE.**

---

## 2. Mapping rule (discovered & documented)

Learned by inspecting `tools/build_icons.mjs` (880-line era pipeline: `inventory → material → lucide → inline → verify → docs`), `tools/svg2vector.mjs`, `tools/reports/{inventory,material,lucide,inline}.json`, `tools/cache/ms/*.svg` (135 era-cached SVGs) and the runtime resolver `core/ui/AreenaxIcon.kt`:

| Web side | Native side |
|---|---|
| Material Symbol glyph `x` (`<span class="material-symbols-outlined">x</span>`) | `res/drawable/ic_x.xml` |
| glyph rendered with `fontVariationSettings: 'FILL' 1` (`FILLED_ICON` const, BottomNav inline style) | `res/drawable/ic_x_fill.xml` |
| lucide-react import `SomeIcon` (kebab `some-icon`) | `ic_lucide_some_icon.xml` (stroke style: transparent fill, 2 dp round stroke) |
| inline `<svg>` block in web screen X | `ic_inline_<screen>_<slug>.xml` |
| runtime resolution | `iconResId(name, filled)` → `ic_<base>` or `ic_<base>_fill` when `filled && base ∈ reserved`; aliases: `warning→error`, `tournament→tune`, `qr_code_scanner→qr_code_2`, `quiz→assignment`, `chevron-right→chevron_right`; unknown → fallback **`ic_help`** (`AreenaxIcon.kt:44-54`) |
| launcher | `drawable/areenax_logo.png` (no mipmap) |

Era provenance: `build_icons.mjs:313` — `https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/{name}/materialsymbolsoutlined/{name}_24px.svg` (+ `_fill1` variant), gstatic fallback; URL verified live this phase (HTTP 200).

---

## 3. Findings (9-field template)

### ICON-01
- **ID:** ICON-01 · **Severity:** MINOR
- **Location:** `app/src/main/java/com/areenax/nativeapp/ui/screens/profile/ProfileScreen.kt:280-296` (render), `:403-424` (4 hardcoded URLs); web parity `src/components/screens/profile/ProfileScreen.tsx:29-50`
- **Steps to reproduce:** Open Profile → social row. `links.forEach { link -> AsyncImage(model = link.iconUrl, …) }` loads `https://lh3.googleusercontent.com/aida-public/AB6AXu…` (Instagram :407, Telegram :412, Discord :417, YouTube :422) over the network at runtime.
- **Expected vs actual:** Expected: social marks render offline like every other icon (all 154 drawables are bundled; AboutScreen already draws its social marks natively). Actual: icons are network-dependent — offline / URL rot / aida-public asset expiry ⇒ blank 28 dp clickable boxes; also two different social renderers exist in the app (AboutScreen native-drawn vs ProfileScreen remote-downloaded).
- **Root cause:** Web design stores social icons as remote generated image URLs; the native port copied the URLs instead of bundling assets.
- **Proposed minimal fix (fix phase, owner-approved):** bundle the 4 brand marks as local drawables (re-use the AboutScreen native marks for consistency), replace `AsyncImage(model=link.iconUrl)` with `Icon(painterResource(...))`; keep `href` logic untouched.
- **Regression risk:** low — pure asset-source swap on one screen; must visually check 4 marks in light/dark.
- **Evidence:** `audit/evidence/phase2/icon-scan-web.json` (`remote_images[4]`), `icon-scan-native.json`

### ICON-02
- **ID:** ICON-02 · **Severity:** MINOR
- **Location:** missing vs design pages: `qr_code` (`upload/pages_extracted/pages/profile.html`), `sports_handball` (`…/hosttournamentcreation.html`), `unfold_more` (`…/freinds.html`); resolver fallback `AreenaxIcon.kt:51`
- **Steps to reproduce:** `icon_audit.py cross-check` → `missing_design_pages_only = [unfold_more, sports_handball, qr_code]`. Live-panel missing list is **empty** — no current screen can hit the fallback for these.
- **Expected vs actual:** Expected: every glyph the design/web universe names resolves to a bundled drawable. Actual: these 3 (design-intent only) would fall back to `ic_help` if ever requested by server data or future UI.
- **Root cause:** era extraction scope was live `src/**` only; design-page glyphs never bundled.
- **Proposed minimal fix (fix phase):** staged SVGs are ALREADY downloaded (`audit/evidence/phase2/icon-staging/svg/{qr_code,sports_handball,unfold_more}_24px.svg`, manifest with sha256) → convert via era tool (`node/bun tools/svg2vector.mjs` helpers) to `res/drawable/ic_qr_code.xml`, `ic_sports_handball.xml`, `ic_unfold_more.xml`; optionally add alias `qr_code → qr_code_2` alongside the existing `qr_code_scanner` alias.
- **Regression risk:** low — additive resources only.
- **Evidence:** `icon-cross-check.json` / `icon-cross-check.md`, `icon-fetch-runs.log`, `icon-staging/manifest.json`

### ICON-03
- **ID:** ICON-03 · **Severity:** NOTE
- **Location:** `app/src/main/AndroidManifest.xml:17` (`android:icon="@drawable/areenax_logo"`); `res/drawable/areenax_logo.png` (512×512 RGBA)
- **Steps to reproduce:** `icon_audit.py scan-native` → `mipmap_dirs: []`; no `res/mipmap-*`, no `mipmap-anydpi-v26` adaptive icon.
- **Expected vs actual:** Expected (modern-Android polish): adaptive launcher icon (foreground/background layers, anydpi-v26). Actual: single fixed raster; launchers mask/crop it as legacy icon.
- **Expected vs actual (functional):** works today — APK builds, icon shows; purely visual polish.
- **Root cause:** era pipeline never generated mipmap/adaptive assets.
- **Proposed minimal fix (fix phase, optional):** add `mipmap-anydpi-v26/ic_launcher.xml` adaptive icon (foreground = logo vector or padded PNG, background = brand color), point `android:icon` at it; keep PNG for ≤API 25.
- **Regression risk:** low — additive; launcher icon swap is device-visible so needs a launcher screenshot check.
- **Evidence:** `icon-scan-native.log`, `icon-scan-native.json` (`counts.mipmap_dirs`, `manifest_icon`)

### ICON-04
- **ID:** ICON-04 · **Severity:** NOTE
- **Location:** `AreenaxIcon.kt:35-39` (reserved fill set omits `groups`), `res/drawable/ic_groups_fill.xml` (unreferenced), web `src/components/shared/JoinTeamSheet.tsx:325-326` (FILL'1' `groups`)
- **Steps to reproduce:** `icon_audit.py cross-check` → `mismatched_style: [{glyph: groups, web: FILL'1', native_renders: outlined}]`; `scan-native` → `ic_groups_fill` classified "never resolvable".
- **Expected vs actual:** `iconResId("groups", filled=true)` returns `ic_groups` (outlined) even though the fill drawable ships. Mitigation found during audit: the ONLY web FILL usage of `groups` is inside **JoinTeamSheet, which is dead code** (zero imports in live web src; native `TeamJoinSheet.kt:56` KDoc also labels it dead; live "Use Your Team" chip uses `diversity_3`, unfilled, matching web TeamJoinSheet). So no reachable UI diverges today.
- **Root cause:** reserved fill set was built from live-usage FILL analysis; `groups` FILL only exists in dead web code — but `AreenaxIcon.kt:16-19` KDoc still lists `groups` as a fill case (doc/code drift).
- **Proposed minimal fix (fix phase, 1 line):** add `"groups"` to the reserved set (or drop `groups` from the KDoc) — aligns comment + makes shipped `ic_groups_fill` reachable.
- **Regression risk:** low — affects only `filled=true` calls with glyph `groups`, which don't exist today.
- **Evidence:** `icon-cross-check.json`, `icon-scan-native.log` (DEAD ic_groups_fill), `tools/reports/inventory.json` fill provenance

### ICON-05
- **ID:** ICON-05 · **Severity:** NOTE
- **Location:** `app/src/main/res/drawable/` (102 of 155 files not statically referenced); `ic_lucide_*` ×14 (~9 KB total)
- **Steps to reproduce:** `icon_audit.py scan-native` → hard refs 53; unreferenced 102 = 87 **dynamically reachable via `iconResId()`** (data-driven slots: Task.icon / Achievement.icon / notification-type icons / bank icons — by design) + 15 never resolvable (14 `ic_lucide_*`, 1 `ic_groups_fill` → ICON-04).
- **Expected vs actual:** lucide set was deliberately "bundled for completeness" (SPEC/ICONS.md: no live panel component imports lucide — recorded so future usage needs no new assets). `app/build.gradle.kts` has no `shrinkResources`/`minifyEnabled`, so the ~9 KB ships in the APK.
- **Root cause:** intentional era decision + no resource shrinking.
- **Proposed minimal fix:** none needed (documented intent); optional `shrinkResources true` in release would strip them but needs keep-rule care for `iconResId`'s reflection (`R.drawable::class.java.getField`) — **NOT recommended** without runtime testing. Zero urgency at ~9 KB.
- **Regression risk:** low (no change) / med (if shrinking enabled: reflection-based resolver + getIdentifier-style lookups can break under resource shrinking).
- **Evidence:** `icon-scan-native.json` (`unreferenced_classification`), `icon-scan-native.log`

### ICON-06
- **ID:** ICON-06 · **Severity:** NOTE
- **Location:** 12 Kotlin sites (9 logical icons) — inventory below (§5)
- **Steps to reproduce:** `icon_audit.py scan-native` → `inline_drawn_icons_kotlin` (regex: `Canvas(` / `drawPath(` / `rotationZ` / `.rotate(-Nf)`).
- **Expected vs actual:** all intentional Compose reimplementations of web CSS/Canvas effects (era `inline.json` explicitly skipped the dynamic progress ring with a "reimplement in Compose" note — AchievementsScreen did exactly that). No missing icon found behind them.
- **Root cause:** n/a (informational inventory for future icon work).
- **Proposed minimal fix:** none.
- **Regression risk:** low.
- **Evidence:** `icon-scan-native.json`

---

## 4. Cross-check tables (full)

Generated: `audit/evidence/phase2/icon-cross-check.md` (machine-written from `icon-cross-check.json`).

### MISSING (live panel uses, native lacks) — 0
∅ — every one of the 115 live-panel glyphs resolves (base + fill, aliases applied).

### MISSING fill variant (web FILL'1', no `ic_*_fill`) — 0
∅ — all 20 web-FILL glyphs have `ic_*_fill.xml`; the 5 bottom-nav tabs (`emoji_events, group, home, account_balance_wallet, person`) are all in the resolver's reserved set and `BottomNav.kt:82` renders `filled = true` → **REQ-076 fill parity statically verified**.

### MISMATCHED-STYLE — 1
| glyph | native target | where (web) / reason |
|---|---|---|
| `groups` | outlined (rendered) | `JoinTeamSheet.tsx:325` FILLED_ICON — **dead web component**; `ic_groups_fill.xml` ships but is unreachable (ICON-04) |

### MISSING (design pages only) — 3 (staged, ready for fix phase)
| glyph | native target | design page |
|---|---|---|
| `qr_code` | `ic_qr_code` | profile.html |
| `sports_handball` | `ic_sports_handball` | hosttournamentcreation.html |
| `unfold_more` | `ic_unfold_more` | freinds.html |

### EXTRA (native has, web never names, code never refs) — 14
`ic_lucide_arrow_left`, `ic_lucide_arrow_right`, `ic_lucide_check`, `ic_lucide_chevron_down`, `ic_lucide_chevron_left`, `ic_lucide_chevron_right`, `ic_lucide_chevron_up`, `ic_lucide_circle`, `ic_lucide_grip_vertical`, `ic_lucide_minus`, `ic_lucide_more_horizontal`, `ic_lucide_panel_left`, `ic_lucide_search`, `ic_lucide_x`
(+ `ic_groups_fill` unreachable — counted in ICON-04). All 14: documented "bundled for completeness" (SPEC/ICONS.md), ~9 KB, harmless.

### Scope notes
- `src/components/admin/**` is a separate web console, out of scope for the native user app per SPEC/ICONS.md — glyphs used only there (`fact_check`, `settings`, `security`, `replay`) are NOT counted missing.
- Fresh web scan (115 glyphs, 20 fills) is **byte-for-byte name-identical** with the build-era `tools/reports/inventory.json` — high confidence the mapping rule and extraction are correct.

---

## 5. Inline-drawn icons (Kotlin, no drawable file)

| Site | What it draws |
|---|---|
| `ui/screens/social/TeamCreationScreen.kt:516-517` | send icon `ic_inline_teamcreation_send` + `graphicsLayer { rotationZ = -45f }` (web CSS `-rotate-45`) |
| `ui/screens/info/AboutScreen.kt:240` | Telegram chip: `ic_send` + `.rotate(-25f)` |
| `ui/screens/info/AboutScreen.kt:253-270` | YouTube chip: red rounded Box + **Canvas** white play triangle (`drawPath`) |
| `ui/screens/info/AboutScreen.kt:280+` | Instagram chip: border Box + lens/dot (drawn) |
| `ui/screens/wallet/ConfirmWithdrawScreen.kt:230` | icon `.rotate(270f)` (web `rotate(270deg)`) |
| `ui/screens/profile/AchievementsScreen.kt:187-200` | Canvas circular progress ring (era `inline.json` skip note → reimplemented as required) |
| `ui/screens/info/OfflineScreen.kt:172-203` | 240 dp Canvas illustration, 3 `drawPath` strokes |
| `ui/screens/host/HostCreationScreen.kt:862` | `drawPath` decoration |
| `ui/screens/wallet/TransferScreen.kt:537` | `drawPath` on `drawContext.canvas` (TASK-004 D7-verified) |

---

## 6. fetch-missing: download / cache / retry mechanism (demonstrated)

Design (`audit/tools/icon_audit.py fetch-missing`):
- **Source:** official Google `material-design-icons` GitHub — `symbols/web/<name>/materialsymbolsoutlined/<name>_24px.svg` (fill: `<name>_fill1_24px.svg`). URL pattern verified live (HTTP 200) before any bulk run.
- **Retry:** 3 attempts, exponential backoff 1 s → 2 s → 4 s; every failure logged; non-zero exit if any job fails.
- **Cache:** `icon-staging/cache-index.json` maps URL → {md5, sha256, file, fetched_at}. Before each job: if the staged file exists and its recomputed md5 matches the index → **skip entirely (0 network calls)**.
- **Manifest:** `icon-staging/manifest.json` per item: source URL, sha256, md5, bytes, target res name (`ic_<name>`), attempts, status, fetched_at; mirrored to `audit/evidence/phase2/icon-fetch-manifest.json`.
- **Scope:** writes ONLY under `audit/evidence/phase2/icon-staging/**`. **app/res untouched** (fix phase does the copy — see §7).

Proof (transcripts: `audit/evidence/phase2/icon-fetch-runs.log`, `icon-fetch-retry-demo.log`):

```
RUN 1 (cold):  downloaded=3  cache_hits=0  failed=0   ← unfold_more(213 B), sports_handball(521 B), qr_code(429 B)
RUN 2 (warm):  downloaded=0  cache_hits=3  failed=0   ← CACHE PROOF (md5 match, 0 network)
RETRY DEMO:    404 (same repo) → attempt 1 fail → 1 s → attempt 2 fail → 2 s → attempt 3 fail → clean failure (wall 3.8 s)
```

---

## 7. Fix-phase plan: "download missing → load in app" (owner-ordered, later)

1. **Convert staged SVGs** (already downloaded + hashed in `audit/evidence/phase2/icon-staging/svg/`): use the era converter (`tools/svg2vector.mjs` → `parseSvg/toVectorXml`, or `bun tools/build_icons.mjs material` which re-runs with its own cache) to write
   `app/src/main/res/drawable/ic_qr_code.xml`, `ic_sports_handball.xml`, `ic_unfold_more.xml` (snake_case, `isValidResourceName`-checked, black `#FF000000` fill → tintable via Compose `Icon`).
2. **ICON-04 one-liner:** add `"groups"` to the reserved set in `AreenaxIcon.kt:35-39` (or fix the KDoc) — makes shipped `ic_groups_fill` reachable; aligns doc.
3. **ICON-01:** bundle the 4 social marks as drawables and swap `AsyncImage(model=link.iconUrl)` → local `Icon`/`Image` in `ProfileScreen.kt` (keep `href`/intent logic); prefer reusing AboutScreen's native marks for one consistent style. **License note:** Material Symbols = Apache-2.0 (attribution per project convention); lucide = ISC; **social/brand logos are trademarks, not Apache-2.0 assets** — keep them as locally drawn marks or verify brand-usage terms; do not re-distribute Google-hosted aida-public URLs in code.
4. **ICON-03 (optional polish):** adaptive launcher icon (`mipmap-anydpi-v26`), keep 512 px PNG for legacy densities; separate Play Store 512 px asset is a listing item, not an app resource.
5. **Verify:** rebuild `assembleRelease` → `aapt2 dump resources` shows the 3 new drawables; static re-run `icon_audit.py scan-native && cross-check` → MISSING design-only = 0; APK size delta ≈ +1-2 KB.
6. Runtime visual verification on device/emulator remains NOT VERIFIED here (no device in sandbox) — fix phase should include launcher + Profile social row + Join-team icon screenshots.

**Cache/retry in the app context:** the same md5-cache + 3-attempt backoff pattern is what any future build-time icon fetch should use; **runtime icon downloads stay prohibited** — icons are bundled at build time (this audit found and flagged the one place that violates that: ICON-01).

**Admin .EXE explicitly out of scope** (owner: after app verification).

---

## 8. NOT VERIFIED (with reasons)

| Item | Reason |
|---|---|
| On-device icon rendering / pixel parity | no device or emulator in sandbox (standing project limitation) |
| `AsyncImage` offline behavior (blank vs placeholder) | static analysis only; needs runtime |
| Visual filled-vs-outlined delta (`groups`, if ever reached) | runtime visual check impossible here |
| Adaptive-icon launcher appearance | no adaptive icon exists (ICON-03); would need device |
| Server data-driven glyph names beyond the code universe | not statically enumerable; resolver falls back to `ic_help` by design |
| APK re-verification after fix-phase conversion | fix phase not authorized in PHASE 2 (read-only) |

## 9. Evidence index (all under `audit/evidence/phase2/`)

- `icon-scan-native.json` / `.log` — 155-file inventory, refs, unreferenced classification, inline sites
- `icon-scan-web.json` / `.log` — 115 glyphs (era-parity), 20 fills, 78 design-page glyphs, 4 remote images
- `icon-cross-check.json` / `.md` / `.log` — MISSING/EXTRA/MISMATCH tables (§4)
- `icon-fetch-runs.log` — both fetch transcripts + retry demo + source URLs
- `icon-fetch-manifest.json`, `icon-fetch-retry-demo.log` — machine manifest (sha256/md5), retry transcript
- `icon-staging/{svg/*.svg, manifest.json, cache-index.json}` — staged downloads (staging ONLY)
- Tool: `audit/tools/icon_audit.py`

**STOP — PHASE 2 read-only honored; awaiting owner decisions (ICON-01/02/04 fix-phase approval, ICON-03 optional).**
