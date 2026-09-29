# TASK 13-d — READ-ONLY Attacker/Critic re-test: global pill-field migration

- **Claim under attack:** "every input field now follows the owner concept (dark-mode pill fields) with ZERO behavior change."
- **Repo:** `/home/z/my-project/AreenaxNativeAndroid`, working tree uncommitted on HEAD `d5f24bd` (19 modified Kotlin files + new `core/ui/PillField.kt`).
- **Method:** whole-source census greps, full read of `PillField.kt`, `git diff HEAD -- <file>` for all 19 modified files, mechanical leftover-import scan over every removed import, icon-name→drawable cross-check, APK existence check. **No project file was modified.** Files outside `AreenaxNativeAndroid` (web/prisma) ignored as out of scope.

---

## 1. FIELD CENSUS (completeness) — HELD

Raw text-input primitives in `app/src/main/java` — exhaustive grep for `BasicTextField(`, `OutlinedTextField(`, `TextField(` (word-boundary), `SearchBar(`, plus `EditText`, `TextFieldValue`, `WebView`:

| # | Hit | Classification |
|---|-----|----------------|
| 1 | `core/ui/PillField.kt:203` `BasicTextField(` | (a) shared implementation ✓ |
| 2 | `ui/screens/auth/SignupStep1Screen.kt:497` `BasicTextField(` | (b) documented PhoneField custom-layout (areenaxFieldColors) ✓ |
| 3 | `ui/screens/wallet/TransferScreen.kt:411` `BasicTextField(` | (b) documented Note multiline ✓ |
| 4 | `ui/screens/host/HostCreationScreen.kt:1049` `BasicTextField(` | (b) documented rules-dialog textarea ✓ |
| 5 | `ui/screens/profile/AdminPanelScreen.kt:2201` `BasicTextField(` | (b) documented FormTextArea ✓ |

- `OutlinedTextField(` / `TextField(` / `SearchBar(` **call sites: 0**. Only 3 *stale imports* remain (see §3 drift D2). No `EditText`/`WebView`/`TextFieldValue` anywhere. **No missed field — census is closed.**

### Pill fields migrated to `AreenaxPillField` — 59 call sites

| Surface | Wrapper (delegates to AreenaxPillField) | Field call sites |
|---|---|---|
| auth/LoginScreen.kt | private `PillTextField` (def :467) | 2 — :176 identifier "person", :189 password "lock" |
| auth/SignupScreen.kt | private `PillTextField` (def :361) | 2 — :184 "person", :199 "sports_esports" |
| auth/SignupStep1Screen.kt | private `PillTextField` (def :405) | 1 — :251 gameUID "sports_esports" (+ PhoneField concept site, below) |
| auth/SignupStep2Screen.kt | private `PillTextField` (def :390) | 1 — :212 password "lock" |
| auth/SignupStep3Screen.kt | private `PillTextField` (def :621) | 2 — :246 "mail", :290 "tag" |
| social/TeamCreationScreen.kt | private `PillTextField` (def :555) | 3 — :175 "groups", :214 "tag", :423 "person_search" |
| social/FriendsScreen.kt | private `SearchPill` (def :707) | 2 — :294 search "search", :548 add-UID "person_add" |
| social/ChatScreen.kt | — (direct) | 1 — :274 composer "chat_bubble" |
| wallet/SelectBankScreen.kt | internal `WalletPillTextField` (def :413) | 1 — :215 "search" |
| wallet/DepositConfirmScreen.kt | — (untouched cross-file caller) | 1 — :414 "tag" |
| wallet/TransferScreen.kt | — (cross-file caller) | 1 — :333 "person_search" |
| host/HostCreationScreen.kt | private `CreationTextField` (def :765) | 3 — :305 "edit", :404 "payments", :417 "groups" (disabled) |
| profile/BindAccountScreen.kt | private `IconPillInput` (def :503) | 2 — :320 "person", :348 "credit_card" |
| profile/EditProfileScreen.kt | private `PillField` (def :454) | 4 — :137 "person", :144 "mail", :152 "sports_esports", :503 "lock" (via PasswordField) |
| profile/AdminPanelScreen.kt | private `SmallInput` (def :2133) | 5 — :1355/:1362/:1528/:1536/:1544 all "edit" |
| profile/AdminPanelScreen.kt | private `FormField` (def :2164) | 13 — :237/:256/:264/:272/:290/:309/:318/:344/:362/:369/:376/:383/:390 |
| profile/AdminTournamentsScreen.kt | private `RoomFieldCompact` (def :766) | 4 — :643/:649/:655 direct + :750 inside RoomField, all "key" |
| profile/GamesAdminSection.kt | internal `MiniInput` (def :702) | 8 — :211 "sports_esports", :218/:406/:511/:518/:525/:599/:658 "edit" |
| core/ui/BankSelectSheet.kt | — (direct) | 1 — :135 "search" |
| core/ui/QrSheet.kt | — (direct) | 1 — :419 "tag" |
| core/ui/TeamJoinSheet.kt | — (direct) | 1 — :199 "person_search" |
| **Total** | 15 wrapper delegations + 4 direct sites | **59** |

### Documented multiline/custom-layout concept sites (areenaxFieldColors, not pills) — 4
- `SignupStep1Screen.kt:433 PhoneField` (country chip owns the leading slot; animated icon tint at :492-496, container :474, 1dp ring :475, cursor :489, placeholder :511)
- `TransferScreen.kt:390-435` Note field (`noteFocused` :393, `noteColors` :410, ring :423, container :422, cursor :409, placeholder :433)
- `HostCreationScreen.kt:1036-1063` RulesDialog textarea (`rulesFocused` :1040, `rulesColors` :1041, ring :1045, cursor :1052, placeholder :1061)
- `AdminPanelScreen.kt:2188-2224` FormTextArea (`focused` :2191, `c = areenaxFieldColors` :2192, ring :2206, container :2207, cursor :2204, placeholder :2216)

**63/63 input sites accounted for (59 pill + 4 concept multiline/custom). Zero unclassified raw inputs.**

---

## 2. CONCEPT COMPLIANCE — HELD (PillField.kt read in full)

`core/ui/PillField.kt` + theme tokens (`core/theme/Tokens.kt`, `Shapes.kt`, `Theme.kt`):

| Owner spec | Implementation | Verdict |
|---|---|---|
| Fully-rounded pill | `AreenaxShapes.Pill = RoundedCornerShape(percent = 50)` (Shapes.kt:19) applied at PillField.kt:161/167-169 | ✓ |
| Idle dark: matte charcoal container | `surfaceContainerHighest` dark = `0xFF2A2A2F` (Tokens.kt:91, PillField.kt:119) | ✓ |
| Idle dark: pure-white outline leading icon | `icon = Color.White` (PillField.kt:102), tint applied :187 | ✓ |
| Idle dark: NO visible cursor | cursor drawn only when focused (BasicTextField platform default); idle shows only the manual placeholder :194-201 | ✓ |
| Focused: icon animates to vibrant blue | `accent = colors.primary` = `DarkPrimary 0xFF2F6BFF` (Tokens.kt:97; PillField.kt:95/101) | ✓ |
| Focused: bright-white blinking cursor beside icon | `cursor = Color.White` (PillField.kt:123), `cursorBrush = SolidColor(...)` :210; blink = platform default; cursor sits immediately right of the icon (icon :183 → 12dp :189 → text :193) | ✓ |
| Focused: text in soft light-white | `text = colors.onSurface` = `DarkOnSurface 0xFFF5F5F7` (Tokens.kt:93; PillField.kt:120, applied :209) | ✓ |
| Focused: crisp subtle ring | dark focused `accent.copy(alpha = 0.55f)` (PillField.kt:111) at `1.dp` border :169 | ✓ |
| Blurred: all animates back | `animateColorAsState` is bidirectional — same spec drives white→blue and blue→white, transparent↔ring (PillField.kt:98-117) | ✓ |
| 300ms app-standard animation on BOTH icon and ring | single `tween<Color>(300, FastOutSlowInEasing)` (`spec`, :96) used by both `animateColorAsState` calls :98/:108 | ✓ |
| Placeholder = onSurfaceVariant | :121 (dark `0xFF9D9DA6`, Tokens.kt:94) | ✓ |
| Disabled = 0.6 alpha | `.alpha(if (enabled) 1f else 0.6f)` :178 | ✓ |
| isError = error icon + ring | icon → `colors.error` :100, ring → `colors.error` :110; passed through by TeamCreation (`isError = nameError` :176) and EditProfile (`isError = error != null` :477) | ✓ |
| Light theme: lavender container, outlineVariant→primary ring | container `extended.surfaceContainerLavender` :119 (else-branch); ring `outlineVariant` :113 → `accent` :111 | ✓ |
| ALL input fields | 59/59 pill sites + 4 sanctioned multiline/custom sites (§1) | ✓ |
| Performance | see §6 | ✓ |

Deviations found: **none material.** Notes: (1) `containerOverride: Color?` (:156) is a dead API — no call site passes it (grep: only :156/:168). (2) PillField hardcodes `singleLine = true` :208 — correct per "single-line pill" spec; multiline lives in the 4 sanctioned sites. (3) Light-mode placeholder ink changed outline→onSurfaceVariant at wrapper sites — documented in 13-a stage summary, concept-consistent.

---

## 3. ZERO BEHAVIOR CHANGE — HELD (0 functional breaks; 1 minor drift class + hygiene)

Mechanical check run over **every removed import of every changed file**: symbol grepped as whole word in the post-change file body → **0 leftovers**. Compile is green (assembleDebug, §7), so deleted private bodies have no dangling references.

### Verified identical, per behavior class (evidence = unchanged diff context or in-diff byte-identical lines)
- **onValueChange filters byte-identical:** Login `{ identifier = it }`/`{ password = it }`; Signup gameName error-clear (:196-198); Step1 phone error-clear (:224-227) + uid error-clear; TeamCreation name `if (it.length <= 24)` + `nameError = false` (:168-173), tag `isLetterOrDigit().uppercase().take(4)` (:213), uid `filter { isDigit() }` (:421); Friends addUid `filter { c -> c.isDigit() }` (:546); QrSheet `filter { isDigit() }.take(12)` (:417); TeamJoinSheet digits+take(12) block byte-identical (:187-196); AdminPanel rank/kills `isDigit`, prize `isDigit() || ch == '.'` (:1526-1545 context); GamesAdmin duration `filter { isDigit() }` (:405); HostCreation/DepositConfirm call sites not in any diff hunk.
- **keyboardType/capitalization:** Email/Password/Number/Phone/Ascii preserved everywhere (all diffs show no changes to these args); TeamCreation Ascii→`KeyboardCapitalization.Characters` recomputed identically (:563-567).
- **VisualTransformation + toggles:** Login eye toggle + `showPw` :201-205 ✓; Step2 eye `showPassword` :220-235 ✓; EditProfile PasswordField `show` toggle + `PasswordVisualTransformation()` :505-516 ✓.
- **focusRequester chain:** Login identifier `onAction = { passwordFocus.requestFocus() }` :183 + `focusRequester = passwordFocus` :206; PillField applies focusRequester to the Row (PillField.kt:171-177) exactly as the old Box did.
- **Error text logic:** TeamCreation `isError = nameError` :176 + error Text :178-181; EditProfile `error` → `isError` + error Text kept below field (context unchanged).
- **Enabled logic:** HostCreation slots `enabled = false` :417 (0.6 alpha preserved by PillField :178); AdminPanel `enabled = !readOnly` on rank/kills/prize + SmallInput passthrough; all enabled args unchanged in diffs.
- **keyboardActions onSend:** ChatScreen `imeAction = ImeAction.Send` + `KeyboardActions(onSend = { send() })` :275-276 — explicit keyboardActions bypasses PillField's default handler; emoji `input += "😊"` and send `send()` tap targets preserved byte-identically.
- **WalletPillTextField signature byte-identical** (value/onValueChange/placeholder/leadingIcon/modifier/keyboardType) → DepositConfirmScreen (untouched, not in git status) and TransferScreen :329-337 compile unchanged.
- **The two sanctioned `ImeAction.Default` preservations verified against old code:** HostCreation `CreationTextField` old = `KeyboardOptions.Default` (diff hunk shows it) → new explicit `imeAction = ImeAction.Default` (:772, comment present); BindAccount `IconPillInput` old = `KeyboardOptions(keyboardType = Text)` → new explicit Default (:511). ✓ Both were Default before.

### Drifts found (reported with file:line — none functionally breaking)

**D1 — imeAction Default→Done on 8 wrapper definitions (37 field instances), functionally inert.**
Old code used `KeyboardOptions(...)`/M3 defaults ⇒ `ImeAction.Default`; the new delegates inherit PillField's `imeAction = ImeAction.Done` default without passing it explicitly. **No** affected field wires `onAction`/`keyboardActions`, and all are single-line, so the action key was a no-op before and is a no-op now — the only user-visible delta is the IME action-key label/layout. Affected:
- `AdminPanelScreen.kt:2128 SmallInput` (old `KeyboardOptions(Text)`) and `:2151 FormField` (old `KeyboardOptions(inputMode)`) — 18 instances
- `AdminTournamentsScreen.kt:760 RoomFieldCompact` (old `KeyboardOptions(Text)`) — 4 instances
- `GamesAdminSection.kt:695 MiniInput` (old `KeyboardOptions(Text)`) — 8 instances
- `EditProfileScreen.kt:454 PillField` (old `KeyboardOptions(keyboardType)`) — 4 instances
- `BankSelectSheet.kt:131` (M3 OutlinedTextField default), `QrSheet.kt:415` (old `KeyboardOptions(Number)`), `TeamJoinSheet.kt:188` (old `KeyboardOptions(Number)`) — 3 instances
Inconsistent with agents 13-b (explicitly preserved Default in its two files, disclosed in its stage summary note 3) — agent 13-c/orchestrator sites did not. One-line fix if the owner wants strict parity: pass `imeAction = ImeAction.Default` in these 8 delegates.

**D2 — 15 now-unused imports left in 5 files (warning-only, build green):**
`BankSelectSheet.kt:25 OutlinedTextField, :44 areenaColors`; `QrSheet.kt:31 OutlinedTextField, :23 KeyboardOptions`; `TeamJoinSheet.kt:20 OutlinedTextField, :34 KeyboardOptions, :16 Icon, :32 painterResource`; `ChatScreen.kt:24 BasicTextField, :26 KeyboardOptions, :40 Brush`; `EditProfileScreen.kt:20 BasicTextField, :36 SolidColor, :21 KeyboardOptions, :51 areenaColors`. (Agents 13-a/13-b/13-c cleaned their own files perfectly — all 26 removed imports verified unused-and-gone; the residue is in orchestrator-migrated files.)

**D3 — presentation deltas (authorized or negligible, listed for completeness):** BankSelectSheet search field 56dp→48dp (:131); ChatScreen composer lost its `shadowElevation = 2.dp` Surface wrapper (:269); wrapper trailing end-padding 16dp→8dp (PillField.kt:226); TeamJoinSheet M3 internal label relocated to a `Text` above the field (:184-191); 2dp static borders → 1dp animated rings; permanent dark outlineVariant border → transparent idle ring (owner-concept intended).

**FriendsScreen caller-icon removal (authorized):** only the decorative duplicate `AreenaxIcon("search")` overlay was deleted (:289-297 removed); search logic `onValueChange = { query = it }` :293, clear button `{ query = "" }` :296-314, and the Add-tab digit filter :546 are untouched. Logic-neutral ✓.

---

## 4. ICON VALIDITY — HELD

16 distinct names used across all `leadingIcon =` sites + computed params (`SearchPill` if/else, BindAccount `icon =` params) — each has a matching `app/src/main/res/drawable/ic_<name>.xml`:

`person` ic_person.xml ✓ · `lock` ic_lock.xml ✓ · `sports_esports` ic_sports_esports.xml ✓ · `call` ic_call.xml ✓ · `mail` ic_mail.xml ✓ · `tag` ic_tag.xml ✓ · `groups` ic_groups.xml ✓ · `person_search` ic_person_search.xml ✓ · `edit` ic_edit.xml ✓ · `payments` ic_payments.xml ✓ · `calculate` ic_calculate.xml ✓ · `key` ic_key.xml ✓ · `person_add` ic_person_add.xml ✓ · `search` ic_search.xml ✓ · `chat_bubble` ic_chat_bubble.xml ✓ · `credit_card` ic_credit_card.xml ✓

`drawableNameFor()` (AreenaxIcon.kt:23-41) maps symbol→`ic_<symbol>` with no alias rewriting for any of these names (`filled = false` everywhere, so the `_fill` reserved set is irrelevant). **No site can hit the `ic_help` fallback** — every resolved `ic_<name>` file exists. (Note: `iconResId` still resolves via cached reflection `R.drawable::class.java.getField` — pre-existing HEAD mechanism, unchanged by this migration; release-dex proof was Task-12 scope.)

---

## 5. PERFORMANCE — HELD

- Exactly **two** `animateColorAsState` (icon :98, ring :108), both on one shared `tween<Color>(300, FastOutSlowInEasing)` :96; `AreenaxFieldColors` is `@Immutable` :73. Animations run only during transitions; idle = zero per-frame work.
- The 4 multiline/custom sites call `areenaxFieldColors` once per field with a single `focused` Boolean state each (one remember per field — no duplicates).
- Plain `BasicTextField` :203 — no Material3 field chrome (no `OutlinedTextFieldDefaults`, no decoration-box overhead beyond the manual placeholder `Text`).
- No allocations that run per frame while idle; the per-recomposition `AreenaxFieldColors`/`TextStyle.copy` allocations only coincide with the 300ms transitions.
- `git diff` grep: **zero** newly added `animate*`/`InfiniteTransition`/`rememberInfinite` APIs outside the new PillField.kt — all "animated" matches in the diff are doc comments.
- No new recomposition hazards: added state holders are stable Booleans (`noteFocused`, `rulesFocused`, `focused`); TeamJoinSheet's keyed `remember(open, i)` preserved; no unstable captures introduced (ChatScreen's `send()` captures are the pre-existing pattern).

---

## 6. COMPILE PROOF — HELD

`app/build/outputs/apk/debug/app-debug.apk` exists — 12,763,965 bytes, timestamped **2026-09-21 14:38** (post-migration). Gradle not re-run per instructions.

---

## 7. PER-SURFACE VERDICTS

| Surface | Verdict | Evidence / notes |
|---|---|---|
| core/ui/PillField.kt (shared impl) | **HELD** | §2 — full spec compliance, line-cited |
| core/ui/BankSelectSheet.kt | **HELD** | filter/type preserved; drift D1 + D2 (2 unused imports); 56→48dp note |
| core/ui/QrSheet.kt | **HELD** | digits+take(12) preserved :417; drift D1 + D2 |
| core/ui/TeamJoinSheet.kt | **HELD** | filter block byte-identical :187-196; error text kept; drift D1 + D2 |
| auth/LoginScreen.kt | **HELD** | focus chain :183/:206, pw toggle :201-216, 0 leftover symbols |
| auth/SignupScreen.kt | **HELD** | gameName error-clear preserved |
| auth/SignupStep1Screen.kt | **HELD** | PhoneField concept path §1; KeyboardOptions(Phone) :505 |
| auth/SignupStep2Screen.kt | **HELD** | pw toggle + transformation preserved |
| auth/SignupStep3Screen.kt | **HELD** | email/referral args unchanged |
| social/TeamCreationScreen.kt | **HELD** | all 3 filters byte-identical; isError passthrough; Ascii→Characters |
| social/FriendsScreen.kt | **HELD** | authorized icon-removal verified logic-neutral |
| social/ChatScreen.kt | **HELD** | Send/keyboardActions preserved; shadow-drop note; D2 (3 unused imports) |
| wallet/SelectBankScreen.kt | **HELD** | WalletPillTextField signature byte-identical |
| wallet/TransferScreen.kt | **HELD** | Note multiline concept site; onValueChange unchanged; wallet pill call untouched |
| wallet/DepositConfirmScreen.kt (caller) | **HELD** | untouched; compiles against unchanged internal signature |
| host/HostCreationScreen.kt | **HELD** | ImeAction.Default preserved :772; disabled slots alpha; rules dialog concept site |
| profile/BindAccountScreen.kt | **HELD** | ImeAction.Default preserved :511; 24dp icons; call sites untouched |
| profile/EditProfileScreen.kt | **HELD** | errors→isError + error Text kept; pw toggle kept; drift D1 (4 fields) + D2 |
| profile/AdminPanelScreen.kt | **HELD** | 18 pill sites + FormTextArea; filters/enabled preserved; drift D1 (18 fields) |
| profile/AdminTournamentsScreen.kt | **HELD** | 4 sites; RoomField label intact; drift D1 (4 fields) |
| profile/GamesAdminSection.kt | **HELD** | 8 sites; duration digit filter preserved; drift D1 (8 fields) |

## OVERALL: **HELD**

**0 BROKEN items.** The owner concept is fully and correctly implemented on all 63 input sites (59 pill + 4 sanctioned multiline/custom), with zero missed raw inputs, zero functional behavior drift, valid icons, and a clean performance profile.

**Non-blocking findings (3 drift classes):**
1. **D1 — imeAction Default→Done** on 8 wrapper definitions / 37 field instances (file:line in §3) — functionally inert (no-op action before and after, single-line fields); recommend passing `imeAction = ImeAction.Default` in those delegates if strict parity is demanded, or blessing Done as the app standard.
2. **D2 — 15 unused imports** across BankSelectSheet, QrSheet, TeamJoinSheet, ChatScreen, EditProfileScreen (warning-only; 13-a/13-b/13-c files are clean).
3. **D3 — minor presentation deltas** (BankSelect 56→48dp, ChatScreen shadow dropped, trailing padding 16→8dp, TeamJoin label relocation) — all consistent with the owner's unification mandate.

*Standing limitation: on-device runtime (keyboard rendering, blink, animation feel) remains unverifiable in this environment — verified at source + compile level only, consistent with the Task-12 standing rule.*
