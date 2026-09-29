# TASK-004 — Fix pass 2: D7 residuals (owner-approved D7-YES + LETTER-DEFAULT) + first full APK build

- Worklog Task ID: 9
- Owner prompt (2026-09-20): "DECISION 1 (D7): D7-YES · DECISION 2 (Chat letter): LETTER-DEFAULT ·
  Proceed to fix D7 and then compile the full apk."
- Baseline: compile-fix2-run3-2026-09-19.log = exactly 42 `e:` errors (authoritative list below).
- Success criteria: (1) `:app:compileDebugKotlin` = 0 errors; (2) `assembleDebug` produces
  app-debug.apk; both evidenced in audit/evidence/.

## Environment (re-bootstrap disclosed)
Sandbox reset wiped /home/z/android-sdk, ~/.gradle and public/downloads. JRE-only system JVM
(no javac). Re-bootstrap runs in background: /home/z/bootstrap-build-toolchain.sh → SDK at
/home/z/android-sdk (platforms;android-35, build-tools;35.0.0, platform-tools) + Temurin JDK 21
at /home/z/jdk21. Builds use JAVA_HOME=/home/z/jdk21 ANDROID_HOME=/home/z/android-sdk (or
local.properties sdk.dir) + the TASK-003 CLI-only memory flags (repo gradle.properties untouched):
`-Dorg.gradle.jvmargs="-Xmx1700m -XX:MaxMetaspaceSize=512m" -Dkotlin.compiler.execution.strategy=in-process --max-workers=2`

## Fix catalog — 42 errors / 21 files (ALL fixes minimal + behavior-preserving)

P1. core/ui/EmptyState.kt (2) — import:11 `foundation.layout.minHeight` → `foundation.layout.heightIn`;
    :48 `.minHeight(300.dp)` → `.heightIn(min = 300.dp)`. (Modifier.minHeight does not exist;
    heightIn(min=) is the identical min-height constraint; web min-h-[300px].)
P2. ui/screens/main/TournamentsScreen.kt (2) — import:14 same swap; :261 `.minHeight(300.dp)` →
    `.heightIn(min = 300.dp)` (TournamentsEmptyState mirrors EmptyState).
P3. core/ui/ToastHost.kt (1) — :197 `style = Type.toastBody.copy(alpha = 0.9f)`:
    TextStyle.copy has NO alpha parameter. Web toast.tsx:139 puts `opacity-90` on the description
    element → fix = add `modifier = Modifier.alpha(0.9f)` to that Text, drop the invalid
    `.copy(alpha = 0.9f)`, add `import androidx.compose.ui.draw.alpha`. (The Text's color comes
    from the `color` param at :198, so a style-color alpha would NOT render — modifier alpha is
    the faithful equivalent of web opacity-90.)
P4. ui/screens/auth/SignupScreen.kt (1) — :201 `if (gameNameError) gameNameError = ""` →
    `if (gameNameError.isNotEmpty()) gameNameError = ""` (gameNameError is a String error message;
    intent: clear error while typing; behavior identical for every non-empty value).
P5. ui/screens/host/HostDetailsScreen.kt (2) — :604/:648 `MaterialTheme.colorScheme.surfaceContainerLavender`
    → `areenaColors().surfaceContainerLavender` (exact D4a pattern; token EXISTS in ExtendedColors;
    areenaColors already imported ~line 70; same value in light+dark by construction).
P6. ui/screens/main/HomeScreen.kt (1) — :229 `togetherWith EnterTransition.None` →
    `togetherWith ExitTransition.None`; add `import androidx.compose.animation.ExitTransition`.
    (Arg is typed ExitTransition; ExitTransition.None = same no-op exit.)
P7. ui/screens/main/TournamentDetailsScreen.kt (2) — :1047 `leading = RankNumberCircle("2")` →
    `leading = { RankNumberCircle("2") }`; :1055 same for "3". (PrizeRow.leading is
    @Composable () -> Unit; the Rank-1 PrizeRow at :1024 already uses the lambda form.)
P8. ui/screens/profile/AchievementsScreen.kt (2) — :190/:199 MaterialTheme reads inside the
    Canvas DrawScope lambda (not @Composable). Hoist ABOVE `Canvas(...)` (inside the Box, composable
    context): `val ringColor = MaterialTheme.colorScheme.surfaceContainerHigh` and
    `val progressColor = MaterialTheme.colorScheme.primary`; use them in the two drawArc(color=…)
    calls. Rendering byte-identical (same values, read once per composition instead of per draw).
P9. ui/screens/profile/AdminPanelScreen.kt (3)
    - :1198 `… ?: "T").charAt(0)` → `.first()` (Kotlin String has no charAt; receiver guaranteed
      non-empty via takeIf?:fallback → first() never throws; result Char, same as charAt).
    - :1783 `safeCall<AdminSecurityResponse> { env.api.adminReconcile() }` →
      `safeCall<AdminReconcileResponse>` — the endpoint RETURNS AdminReconcileResponse
      (Api.kt:360) whose fields checkedCount/items:List<AdminReconcileRow> are exactly what
      :1785-1786 assign into reconcileChecked/reconcileDrifts. The declared type arg was a
      copy-paste bug; runtime would have been broken. Add
      `import com.areenax.nativeapp.core.network.AdminReconcileResponse` if absent.
    - :1786 dissolves with the :1783 fix.
P10. ui/screens/profile/AdminTournamentsScreen.kt (1) — :618 `…"?").uppercase().charAt(0)` →
     `.uppercase().first()`.
P11. ui/screens/profile/GamesAdminSection.kt (9)
     - :136 cluster (2) — ROOT CAUSE = scope, NOT generics: the when at :124 binds
       `when (val res = …)` whose scope ends at the when's closing brace :135; :136 uses `res`
       again → out of scope. Fix: bind BEFORE the when —
       `val res = safeCall<AdminGamesResponse> { env.api.adminCreateGame(...) }` then
       `when (res) { … }` (2-line diff; identical evaluation order; branches untouched;
       :130-131 double-cast still valid).
     - :386 cluster (7) — `mutableStateOf(String(game.matchDurationMinutes ?: 60))`: `String(…)`
       is a Java-ism, not a Kotlin callable. Fix:
       `mutableStateOf((game.matchDurationMinutes ?: 60).toString())` (matchDurationMinutes is
       Int? per Models.kt:82). All 7 inference/candidate errors stem from this one expression.
P12. ui/screens/profile/LeaderboardScreen.kt (5) — :254/:345/:372/:432/:460 `.charAt(0)` →
     `.first()` (same pattern; receivers guaranteed non-empty).
P13. ui/screens/profile/ProfileScreen.kt (1) — :165 `.charAt(0)` → `.first()`.
P14. ui/screens/social/ChatScreen.kt (2)
     - :142 `description = res.message` → `description = (res as? ApiResult.Error)?.message ?:
       (res as? ApiResult.NetworkError)?.message` — merged branch `is Error, is NetworkError`
       smart-casts to a GLB without `message`; both DTOs carry `message: String`; the double-cast
       is the existing house pattern (GamesAdminSection:130-131) and behavior-identical inside
       that branch.
     - :247 `FriendBubble(letter, m)` — LETTER-DEFAULT (owner decision 6): the bare `letter`
       identifier was NEVER defined (ChatHeader's `letter` at :159 is a call-site named arg).
       Fix: pass the EXACT expression already used for the header:
       `FriendBubble((friend?.gameName ?: "?").firstOrNull()?.uppercaseChar()?.toString() ?: "?", m)`.
       FriendBubble(letter: String) at :436 — types match. = friend-name initial, same as web.
P15. ui/screens/social/FriendsScreen.kt (2) — :200/:232 same double-cast as P14.
P16. ui/screens/social/MyTeamScreen.kt (1) — :438 same double-cast.
P17. ui/screens/social/NotificationsScreen.kt (1) — :122 same double-cast.
P18. ui/screens/social/TasksScreen.kt (1) — :114 same double-cast.
P19. ui/screens/social/TeamCreationScreen.kt (1) — :126 same double-cast.
P20. ui/screens/wallet/ConfirmWithdrawScreen.kt (1) — :266 `.align(Alignment.BottomCenter)` →
     `.align(Alignment.CenterHorizontally)`. Parent is a Column (ColumnScope.align takes
     Alignment.Horizontal — that is the error). Footer Surface is the LAST child → bottom by
     order; only the horizontal-center component of BottomCenter was meaningful. Layout identical.
P21. ui/screens/wallet/TransferScreen.kt (1) — :559 `scale(scale, scale, pivot = Offset.Zero)` →
     `scale(scale, scale)` — androidx.compose.ui.graphics.Canvas.scale has no pivot parameter and
     pivots at the origin (0,0) by default = Offset.Zero. Behavior byte-identical. Leave imports
     untouched if Offset is used elsewhere in the file; if it becomes fully unused, remove it.

## Result (2026-09-20, orchestrator)
- Fix trajectory: 42 (run3 baseline) → 2 (run4) → **0 (run5, BUILD SUCCESSFUL)**. Evidence:
  compile-fix3-run4-2026-09-20.log (attempt 1), compile-fix3-run5-2026-09-20.log (clean).
- Attempt-2 refinement on TWO catalog items (same fix item, attempt 2 of ≤3): P9-AdminPanelScreen:1198
  and P10-AdminTournamentsScreen:618 — after charAt dissolved, the compiler surfaced a hidden
  cascade: the Text(text=…) arg is a bare Char (charAt/first both return Char; Text needs String;
  the original error had masked this). Refined to `.first().toString()` — same first character,
  now as String; semantics unchanged.
- Full APK (first ever): assembleDebug BUILD SUCCESSFUL. Artifact app-debug.apk = 12,796,733 bytes,
  sha256 5ff0d2b5da5a95daa2f088088aeb726963316621154fca73ab1baa188e18795a; aapt2 badging:
  com.areenax.nativeapp, versionCode 2, versionName 2.0.0, minSdk 24, targetSdk 35; permissions
  per aapt2 dump permissions: INTERNET, CAMERA, ACCESS_NETWORK_STATE (+tooling-generated
  DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION) — CORRECTED after Attacker 9-b caught an earlier
  "INTERNET only" misstatement here; the manifest itself predates this pass and was not touched;
  apksigner verify OK (debug cert). Evidence: assembleDebug-run2-2026-09-20.log; the first launch
  attempt died with the tool-session client (NOT a build failure — daemon had finished all tasks;
  kept as assembleDebug-run2a-client-cancelled-2026-09-20.log for honesty).
- Published: /home/z/my-project/public/downloads/AreenaxNativeAndroid-v2.0.0-debug.apk
  (byte-identical sha256 verified after copy).
- Not verified (no device/emulator in sandbox): install, launch, runtime behavior, screenshots.

## Verification loop
1. After ALL edits: fresh full-file reads of every edited hunk (assume-incorrect discipline).
2. Wait for /home/z/toolchain-bootstrap.log "BOOTSTRAP DONE" (poll; it is a background job).
3. Probe: `:app:compileDebugKotlin` with the memory flags → must be 0 `e:` lines. Save log to
   audit/evidence/compile-fix3-run4-2026-09-20.log. ≤3 attempts per problem (F.8); if an attempt
   changes the fix hypothesis, re-verify against the web/DTO definitions and record it.
4. Do NOT commit (orchestrator commits). Do NOT touch any file outside the 21 listed ones
   (exception: a genuinely required NEW import inside those same files).

## MUST NOT change (FFR freeze)
- No hex colors, strings, copy, paddings/sizes, token values, layout order, animation durations.
- No API/DTO/model edits (P9's type ARG is a call-site generic, not a model edit).
- No behavior edits beyond the 42 compile errors' sites; `letter` = LETTER-DEFAULT exactly.
- No gradle.properties / build.gradle edits; no library upgrades.
