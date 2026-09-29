# A10 — CAMERA runtime-permission flow on fresh install (static, bytecode-verified)

Scope: what exactly happens on a fresh install when the user taps "Open Camera" in QrSheet, on any Android 6.0+ device (API 24 minSdk → all supported levels are 6.0+).

## App-side facts (no app-level permission code exists)
- Manifest declares `android.permission.CAMERA` (AndroidManifest.xml:5) and `<uses-feature android:name="android.hardware.camera" android:required="false"/>` (:10-12) — comment: devices without a camera can still use the manual-UID fallback in QrSheet.
- `grep -rn "requestPermissions|RequestPermission|checkSelfPermission" app/src/main` → 0 app-side matches (only `rememberLauncherForActivityResult` for photo picker + ScanContract).
- Scan trigger: QrSheet.kt:282 `rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let { scope.launch { resolve(it) } } }`; launch button QrSheet.kt:388-397 (`ScanOptions().setDesiredBarcodeFormats(QR_CODE).setPrompt(...).setBeepEnabled(false).setOrientationLocked(true)` — no permission options customized).

## Library-side flow — zxing-android-embedded 4.3.0, bytecode-verified
Evidence: `audit/evidence/phase2/a10-capturemanager-javap.log` (javap -c -p of the exact AAR from `~/.gradle/caches/modules-2/files-2.1/com.journeyapps/zxing-android-embedded/4.3.0/148ce3de.../zxing-android-embedded-4.3.0.aar`).

1. ScanContract launches `com.journeyapps.barcodescanner.CaptureActivity` (merged into the APK; merged-manifest tree: activity with `screenOrientation=6` (sensorLandscape default), `clearTaskOnLaunch=true`, `stateNotNeeded=true` — a10-release-manifest-tree.log:110-113).
2. `CaptureManager.onResume()` → `openCameraWithPermission()`:
   - `ContextCompat.checkSelfPermission(activity, "android.permission.CAMERA")` == GRANTED → `barcodeView.resume()` (camera starts).
   - Otherwise, once per instance (`askedPermission` flag, bytecode offset 22-50) → `ActivityCompat.requestPermissions(activity, ["android.permission.CAMERA"], cameraPermissionReqCode)`. **The LIBRARY raises the standard system runtime-permission dialog; the app never shows its own rationale.**
3. `onRequestPermissionsResult` (bytecode :302-330):
   - GRANTED → `barcodeView.resume()` → scanning works. Nothing else needed from the app.
   - DENIED → `setMissingCameraPermissionResult()` (sets result intent with MISSING_CAMERA_PERMISSION, :567-583) then, since `showDialogIfMissingCameraPermission` is initialized `true` in the constructor (javap of constructor: `iconst_1; putfield showDialogIfMissingCameraPermission`), → `displayFrameworkBugMessageAndExit(missingCameraPermissionDialogMessage)`:
     - message field is initialized to `""` → bytecode :25-42: empty message is replaced with `activity.getString(R.string.zxing_msg_camera_framework_bug)` — i.e. the **"camera framework bug"** text is shown for a plain permission DENIAL (library default; known quirk of 4.3.0).
     - AlertDialog: title = `zxing_app_name`, positive button = `zxing_button_ok`, `setOnCancelListener` → `closeAndFinish()` → CaptureActivity finishes with CANCELED.
4. App-side outcome on denial/cancel: `result.contents == null` → the lambda at QrSheet.kt:282-286 does nothing → user is back in the QrSheet; the **manual "Or enter a Player UID" fallback (QrSheet.kt:411-424) is always visible** on the Scan tab, so QR-add remains possible without the permission.
5. "Don't ask again" (permanent denial): a subsequent attempt re-runs `requestPermissions`, the system auto-denies without showing a dialog → same DENIED path → same (misleading) library AlertDialog every attempt.
6. Devices with no camera: uses-feature not required → installable; launching the scanner on a camera-less device → `displayFrameworkBugMessageAndExit("camera framework bug")` via the camera-open failure path — text is factually wrong there too (minor UX wart, same root cause).

## One-line summary
Fresh install → tap "Open Camera" → CaptureActivity starts → the zxing library itself requests CAMERA (system dialog) → grant = scanner works; deny = library AlertDialog with the default "camera framework bug" message, then back to QrSheet with the manual UID fallback still available.

## NOT VERIFIED
- Actual on-device dialogs/camera start (no emulator/device in sandbox). Flow above is bytecode + manifest evidence only.
