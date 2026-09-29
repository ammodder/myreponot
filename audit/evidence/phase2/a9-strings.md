# A9 evidence — strings / localization

Repo @ 22ce05c. Literal counting excludes comment lines (strips `*`/`//`/`/*` lines, then regex `"(?:[^"\\]|\\.)*"`).

## 1. Hardcoded string literals vs resources
```
code-only string literals: ui/screens=2265, core/ui=254, total=2962
  top: AdminPanelScreen.kt 345 · data/Models.kt 172 · TournamentDetailsScreen.kt 153 ·
       FriendsScreen.kt 118 · AdminTournamentsScreen.kt 107 · Api.kt 104 ·
       GamesAdminSection.kt 88 · HostDetailsScreen.kt 83
```
(A1 recorded "2,332 in screens" with a different counting method; verified here: 2,265 screens + 254 core/ui.
Same order of magnitude, same conclusion.)

String resources (res/values/strings.xml — the ONLY values file with strings):
```xml
<string name="app_name">Areenax</string>
<string name="app_base_url" translatable="false">https://YOUR-AREENAX-DOMAIN.example.com</string>
```
→ **2 resources; exactly one translatable="false" flag (app_base_url:11); no values-<locale> dirs exist;
no localeConfig; no resourceConfigurations in gradle.** UI copy is 100% hardcoded Kotlin strings.

## 2. RTL
```
AndroidManifest.xml:19  android:supportsRtl="true"
rg 'layoutDirection' java → only OfflineScreen.kt:300 (a draw-scope parameter, not a layout decision)
```
No `LocalLayoutDirection` override, no localeConfig, no per-locale resources. supportsRtl=true means an
Arabic/Hebrew system locale would MIRROR Compose layouts (start/end-aware modifiers) while every label
stays English → mixed-direction UI. Whether Arabic (or any second locale) is in scope = owner decision.
Date/number formatting uses system locale by default (DateTimeFormatter/Formatters.kt).

## 3. Placeholder / interpolation correctness
- Kotlin templates used correctly throughout: `"$percent% Completed"` (AchievementsScreen.kt:176),
  `"Version $version"` (AboutScreen.kt:173), `"+Rs ${formatMoney(...)}"` (TasksScreen.kt:310, ReferEarnScreen.kt:472).
- No `String.format` misuse, no concatenation-built sentences found EXCEPT:
  - **FriendsScreen.kt:260** — `"Added $accepted friend" + if (accepted == 1) "!" else "s!"` — hand-rolled
    plural (localization-hostile; ICU plurals have no resource to live in anyway here).
- `stringResource` usage: **0** (rg 'stringResource' → no hits in UI).

## 4. Where does policy/about copy live? (Q12 mapping)
- **PrivacyScreen.kt:43** `private val PRIVACY_SECTIONS: List<PrivacySection> = listOf(...)` — 8 sections
  VERBATIM hardcoded (:35 comment confirms "8 sections VERBATIM — section 8 with the bold support@gamingapp.com").
  Rendered at :113-148.
- **TermsScreen.kt:48** `private val TERMS_SECTIONS: List<Triple<String, String?, List<String>>>` —
  hardcoded, 10 sections (Triple entries :49-95; rendered :191).
- **AboutScreen.kt** — the ONLY server-driven copy: `settings.aboutMission` (:111, fallback
  `FALLBACK_MISSION` const :75-76), `settings.version` (:110, hardcoded fallback "1.2.0"), social links
  (:102-107 from SettingsCache / `SettingsCache.get { env.api }` :84).
- SettingsCache.kt — no-TTL cache of the settings endpoint (web parity per REQ-151).

→ Consequence for Q12: location+contacts disclosures CANNOT ride the existing server `aboutMission`
field; they must be added to (a) native PRIVACY_SECTIONS (hardcoded list → code change in a fix phase),
(b) the web privacy page (parity), (c) Play Console Data Safety (A11), (d) runtime permission prompts at
feature time. No manifest permission for location/contacts exists today (manifest :4-6 = INTERNET, CAMERA,
ACCESS_NETWORK_STATE).

## 5. Stale hardcoded fallback (cross-ref Q11/A13)
AboutScreen.kt:110 — `?: "1.2.0"` version fallback contradicts the shipped versionName 2.0.0 (APK badging,
TASK-004) and Q11 (display 1.0.0, fallback 2.0.0). Server-driven normally; fallback string is stale.

## 6. String-quality spots (owner gate C-items, cross-ref only)
- "Alphla Bank" typo also present on native (parity with web, worklog TASK-005; Q13 pending fix).
- Toasts/errors copy hardcoded at call sites (e.g. QrSheet.kt:428 "Player UID is required").
