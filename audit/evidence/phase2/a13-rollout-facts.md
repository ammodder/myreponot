# A13 evidence — ROLLOUT IDENTITY + SIGNING + ENV facts (read-only)
date: 2026-09-20T18:52:58Z

## 1. Version identity (app/build.gradle.kts)
    defaultConfig {
        applicationId = "com.areenax.nativeapp"
        minSdk = 24          // Android 7.0+
        targetSdk = 35       // Android 15 (Google Play 2025 requirement)
        versionCode = 2
        versionName = "2.0.0"
        resourceConfigurations += "en"
    }

## 2. Signing: absence of any signingConfig / keystore reference in app config
$ rg -n "signingConfig|keystore|storeFile|storePassword" app/build.gradle.kts app/src gradle/
exit=1 (1 = none)
(keystore lives owner-side per Q14; MISSING-INPUTS.md:18 forbids pasting keystore secrets into the audit)

## 3. Published APK facts re-verified from Phase-0 evidence (assembleDebug run2 log tail)

BUILD SUCCESSFUL in 15s
35 actionable tasks: 11 executed, 24 up-to-date

recorded badging (worklog Task 9 / REQ-061): com.areenax.nativeapp · versionCode 2 · versionName 2.0.0 · minSdk 24 · target 35 · 12,796,733 B · sha256 5ff0d2b5…8795a — artifact published to /downloads/ (WEBSITE download, NOT a Play upload; versionCode 2 therefore NOT consumed by Play)

## 4. strings.xml app_base_url (THE deployment gate)
    <!--
      STEP 1 FOR DEPLOYMENT (see README.md):
      Replace this placeholder with the LIVE AREENAX domain (same origin the web
      panel is deployed on). Every API call is prefixed "<base>/api" and QR
      deep links are built from the same origin, exactly like the web client.
    -->
    <string name="app_base_url" translatable="false">https://YOUR-AREENAX-DOMAIN.example.com</string>
</resources>

## 5. Server-driven version story — AboutScreen.kt:110
    val version = settings?.version?.takeIf { it.isNotBlank() } ?: "1.2.0"
    val mission = settings?.aboutMission?.takeIf { it.isNotBlank() } ?: FALLBACK_MISSION

## 6. Settings bootstrap consumers (what degrades when /bootstrap is down)
app/src/main/java/com/areenax/nativeapp/ui/screens/info/AboutScreen.kt:81:    var settings by remember { mutableStateOf(SettingsCache.getCached()) }
app/src/main/java/com/areenax/nativeapp/ui/screens/info/AboutScreen.kt:84:        SettingsCache.get { env.api }?.let { settings = it }
app/src/main/java/com/areenax/nativeapp/ui/screens/wallet/DepositScreen.kt:128:        SettingsCache.get { env.api }?.let { s ->
app/src/main/java/com/areenax/nativeapp/ui/screens/wallet/WithdrawScreen.kt:114:        SettingsCache.get { env.api }?.let { s ->
app/src/main/java/com/areenax/nativeapp/ui/screens/wallet/DepositConfirmScreen.kt:140:        SettingsCache.get { env.api }?.depositAccounts?.get(method)?.let { accountNumber = it }
app/src/main/java/com/areenax/nativeapp/ui/screens/main/HomeScreen.kt:115:            SettingsCache.cache(cached.settings)
app/src/main/java/com/areenax/nativeapp/ui/screens/main/HomeScreen.kt:125:                    SettingsCache.cache(res.data.settings)
app/src/main/java/com/areenax/nativeapp/AreenaxApplication.kt:79:            SettingsCache.get { api }
app/src/main/java/com/areenax/nativeapp/ui/screens/profile/ProfileScreen.kt:100:        SettingsCache.get { env.api }?.let { s ->
app/src/main/java/com/areenax/nativeapp/ui/screens/auth/SignupScreen.kt:490:            val settings = SettingsCache.get { env.api }
app/src/main/java/com/areenax/nativeapp/ui/screens/auth/LoginScreen.kt:597:            val settings = SettingsCache.get { env.api }
app/src/main/java/com/areenax/nativeapp/core/ui/SupportFab.kt:41:            val settings = SettingsCache.getCached()
app/src/main/java/com/areenax/nativeapp/core/ui/WhatsAppFab.kt:53:            val target = href ?: SettingsCache.getCached()?.whatsapp

fallbacks applied when settings==null (cited):
 - AboutScreen.kt:110 version fallback "1.2.0"; :111 mission FALLBACK_MISSION (:75)
 - DepositConfirmScreen.kt:129 FALLBACK_ACCOUNTS[method] ?: "0300-1234567" (hardcoded deposit accounts, C9/REQ-100)
 - SupportFab.kt:41-57 no whatsapp number → navigate(ABOUT)
 - WhatsAppFab.kt:53 target = href ?: cached?.whatsapp
 - HomeScreen.kt:115-125 games/banners fall back to empty (home still renders)
 - REQ-127 (render-optional-info-only-when-configured), REQ-151 (bootstrap cache; Attacker-verified no-TTL = web parity, settings.ts:11-30)

## 7. Release-build mechanics (facts, nothing built this phase)
 - R8: app/build.gradle.kts:21-30 minify+shrink ON for release; rules app/proguard-rules.pro (44 lines, no -assumenosideeffects)
 - Expected R8 mapping output path (AGP standard): app/build/outputs/mapping/release/mapping.txt — NOT VERIFIED (no release build has ever been run in this sandbox; REQ-145/A1 F-16)
 - Play requires AAB for NEW apps; current artifact = debug APK. Bundle command (NOT executed per task rules): ./gradlew bundleRelease
 - gradle.properties: org.gradle.jvmargs=-Xmx2048m (builds OK after RAM fix, worklog Task 5/8)
