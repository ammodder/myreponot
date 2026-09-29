# A10 — Device/OS matrix minSdk 24 → API 36 (static analysis; shares are knowledge-based APPROXIMATIONS)

Run: 2026-09-20 · No live stats query was made (NOT VERIFIED). Share figures = order-of-magnitude estimates from prior knowledge of public Android distribution dashboards (±5pp, direction only), marked approximate per task rules.

App-level gates: `grep -rn "Build\.VERSION\|SDK_INT" app/src/main` → **0 matches**. The app contains ZERO hand-written API gates; every level-dependent behavior below comes from AndroidX/libraries (androidx.core, activity-compose, core-splashscreen, zxing-android-embedded). Config: minSdk=24 (build.gradle.kts:14), targetSdk=35 (:15).

| API | Android | Est. share (approx) | App-specific risk at this level |
|-----|---------|--------------------|--------------------------------|
| 24 | 7.0 | ~0.5-1% | Lowest floor; Compose + DataStore + OkHttp fine; TLS 1.2 default on 7.0; camera flow = library-requested runtime permission (works, 23+); no adaptive icons N/A yet; predictive back N/A (BackHandler-based). **LOW risk.** |
| 25 | 7.1.x | ~0.5-1% | Same as 24. **LOW.** |
| 26 | 8.0 | ~1-2% | Notification channels era begins — **N/A: app has 0 NotificationManager/NotificationChannel usages** (grep=0, a10-config-citations.log); nothing to migrate. Legacy non-adaptive icon may be circle-masked/shrunk on 8.0+ launchers (no mipmap-anydpi-v26 exists; icon = @drawable/areenax_logo; cross-ref ICON-AUDIT). Cosmetic. **LOW.** |
| 27 | 8.1 | ~2-3% | Same as 26. **LOW.** |
| 28 | 9.0 | ~4-6% | Scoped storage/biometric/HTTPS-default — all N/A (system photo pickers only; no storage perms, no biometric). **LOW.** |
| 29 | 10.0 | ~6-8% | Scoped storage N/A; activity embedding N/A. **LOW.** |
| 30 | 11.0 | ~10-13% | Package visibility (QUERIES) N/A — no getPackageInfo/queries use; CAMERA one-time permission N/A (not in the one-time category). **LOW.** |
| 31 | 12.0 | ~10-14% | Native splash — core-splashscreen bridges (SplashScreen.installSplashScreen, MainActivity:33); PendingIntent mutability N/A (0 PendingIntents). **LOW.** |
| 32 | 12L | ~1-2% | Large-screen stage 1 — app is portrait-locked single-column; desktop/large-screen untested (NOT VERIFIED). **LOW.** |
| 33 | 13.0 | ~12-16% | POST_NOTIFICATIONS runtime permission — **N/A by design: app has NO notifications permission, NO FCM, 0 NotificationManager usage** (Models.kt:665 notes FCM registration is "wired when FCM is activated"; AppBar unread is an in-app count via GET /notifications, countOnly poll, Api.kt:239-241). Nothing degrades — there is no notification surface at all. Photo Picker: PickVisualMedia (PhotoPickers.kt:110) uses system picker, no permission needed (pre-33 falls back to OPEN_DOCUMENT via the contract). Per-app language settings N/A (no localeConfig; only 2 string resources, all copy hardcoded en — A9). **LOW.** |
| 34 | 14.0 | ~18-25% | Implicit/explicit intent blocking N/A (no implicit component intents); FGS types N/A (no services); screenshot detection N/A. **LOW.** |
| 35 | 15.0 (current target) | ~15-22% | Edge-to-edge FORCED at targetSdk 35: app is already native e2e (enableEdgeToEdge, MainActivity:35 + theme-synced re-apply :44-58; A8 matrix: status-bar insets covered 48/48 screens, nav-bar on roots + wallet flow, imePadding ×8). Open runtime caveat: trailing-content clearance on full-screen non-NAV screens = NOT VERIFIED (A8). **LOW-MED.** |
| 36 | 16.0 | ~1-4% | Behavior changes activate ONLY when targetSdk 36 lands (cross-ref A11-02): predictive back default-on (BackHandler nav = no in-app predictive animation, cosmetic), orientation/aspect-ratio/resizability restrictions IGNORED on large screens (portrait lock won't hold on ≥600dp devices — phone-first layout must be responsive-checked), e2e opt-out removed (app already e2e-native — no impact), intent-redirection/ordered-broadcast/JobScheduler-quota changes N/A (0 PendingIntents, 0 receivers, 0 jobs). **MED risk at upgrade time, LOW today (target 35).** |

Matrix highlights (riskiest levels): **API 35** (e2e forced — mostly mitigated, runtime caveats), **API 36** (large-screen geometry + predictive-back polish, only at targetSdk bump), **API 26-27** (legacy icon masking — cosmetic; notification channels N/A).

Deprecation notes:
- Pre-26 degradation: none material — the app uses no notification/notification-channel API at all; surfaces that normally need channels simply don't exist in this app.
- Pre-33 degradation: none — no notifications; photo picking uses PickVisualMedia which handles pre-33 fallback internally.
- 16KB page size (16-era devices, e.g. some 2025+ flagship arm64): measured compliant (a10-16kb-page-size.log).

Cross-refs: A11-02 (Play target-36 BLOCKER + extension), A8 (insets matrix), A9 (font-scale clipping 28-44dp rows), A3-06 (background polls MyTournament 30s / ChatScreen 3s / TournamentDetails 1s / HostDetails 1s — LaunchedEffect loops keep polling while composed even when the app is backgrounded), Q12 (future location+contacts permissions will change this matrix and Play posture — out of scope today).
