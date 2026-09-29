CLOUD EMULATOR TEST RESULTS
===========================

Device: Pixel 5 (API 33, Gradle Managed Device "pixel5api33", AOSP x86_64 image)
Date: 2026-09-21 (Asia/Karachi)
Command source: owner "GRADLE MANAGED DEVICES SETUP" (8 steps)
Honesty labels per audit/MASTER_RULES.md.

CONFIGURATION (STEPS 1-3)
-------------------------
- STEP 1 — GMD config check: NOT PRESENT in app/build.gradle.kts (verified by full read).
- STEP 2 — Config ADDED (VERIFIED): app/build.gradle.kts ->
  testOptions.managedDevices.localDevices.create("pixel5api33") { device = "Pixel 5";
  apiLevel = 33; systemImageSource = "aosp" }. AGP 8.7.3 accepts the DSL: task graph
  resolves (exit 0) and generates: pixel5api33Setup, pixel5api33Check,
  pixel5api33DebugAndroidTest, allDevicesCheck, allDevicesDebugAndroidTest,
  cleanManagedDevices (evidence: gmd-tasks-all.log).
- STEP 3 — Versions (execution-verified, not assumed):
  * Gradle 8.10.2  — requirement >= 8.0  -> PASS (gradle-version.log)
  * AGP 8.7.3      — requirement >= 8.1  -> PASS (gradle/libs.versions.toml)
  * JDK 21 Temurin (/home/z/jdk21)       -> PASS
  * No version upgrade was needed.
  NOTE (worklog discrepancy): worklog Task 12 claims wrapper 8.11.1 + AGP 8.9.1;
  the actual tree has wrapper 8.10.2 + AGP 8.7.3. The execution-verified values
  above are authoritative.

UNIT TESTS — STEP 4
-------------------
- Ran (owner's exact command): ./gradlew testReleaseOnPixel5Api33
- Result: BUILD FAILED — "Task 'testReleaseOnPixel5Api33' not found in root project".
  The task does not exist in AGP 8.7.3: Gradle Managed Devices generate INSTRUMENTED-
  test tasks only (unit tests never execute on a device).
- Correct equivalent run: ./gradlew testReleaseUnitTest
- Result: BUILD SUCCESSFUL — "> Task :app:testReleaseUnitTest NO-SOURCE"
  → 0 passed, 0 failed. The project contains ZERO unit test sources
  (app/src/test/ does not exist — audit finding A12).
- Evidence: unit-tests.log

UI/COMPOSE TESTS — STEP 5
-------------------------
- Ran (owner's exact command): ./gradlew connectedReleaseAndroidTest
- Result: task does not exist (only connectedDebugAndroidTest is generated; the
  release build type is not instrumented-testable).
- GMD equivalent attempted: ./gradlew pixel5api33DebugAndroidTest
- Result: FAILED at ":app:pixel5api33Setup" — 0 passed, 0 failed.
  * What DID succeed: Android Emulator v37.1.11 + "Intel x86_64 Atom System Image
    API 33 (rev 2)" downloaded and installed into /home/z/android-sdk (licenses
    accepted automatically). These remain installed and reusable on a KVM host.
  * What failed: managed-device snapshot creation, 5 attempts / 5 failures —
    "The emulator failed to open the managed device to generate the snapshot ...
    the emulator closed unexpectedly".
  * ROOT CAUSE (emulator's own diagnostics, environment-checks.log):
    - /dev/kvm DOES NOT EXIST in this sandbox
    - `emulator -accel-check` exit code 3: "KVM requires a CPU that supports
      vmx or svm" — the sandbox CPU has no virtualization extensions
    => hardware-accelerated x86_64 emulation is impossible here; this is an
       environment blocker, not a project or config defect.
  * Additional blocker: ZERO instrumented test sources exist
    (app/src/androidTest/ does not exist — audit finding A12), so even on a
    bootable device the run would execute 0 tests.
- Evidence: ui-tests.log, environment-checks.log

MONKEY STRESS TEST (5000 events) — STEP 6
-----------------------------------------
- Ran: adb devices (platform-tools installed at /home/z/android-sdk/platform-tools)
- Result: empty device list (0 devices attached).
- VERDICT: NOT RUN — monkey requires a booted device via adb shell; no device can
  boot in this sandbox (no KVM — see STEP 5). "0 crashes, 0 ANRs" would be a false
  claim of success, so it is NOT claimed. Package prepared for a capable host:
  com.areenax.nativeapp, throttle 100, 5000 events.
- Evidence: monkey.log, environment-checks.log

SUMMARY
-------
- All critical tests: FAIL (none of the three runs could execute end-to-end in
  this sandbox; root cause is environmental: no KVM/vmx-svm, 4 GB RAM, 2 vCPU).
- No regressions: N/A — no tests executed. Side evidence the app itself is
  healthy: compileReleaseKotlin and compileDebugKotlin completed green during
  the unit-test and GMD runs (unit-tests.log, ui-tests.log).
- Ready for Phase 7: NO (blocked on real test execution; see Open items).

OPEN ITEMS TO REACH A REAL RUN
------------------------------
1. Run on a KVM-capable machine (or enable nested virt): the committed GMD config
   then works as-is — `./gradlew pixel5api33DebugAndroidTest` (first run provisions
   the device; emulator + API 33 image are already cached in ANDROID SDK dir).
2. Author tests: 0 unit tests + 0 instrumented tests exist today (A12). Until test
   sources are written, every runner will report NO-SOURCE regardless of host.
3. Optional true-cloud path (needs owner decision + credentials): Firebase Test
   Lab via GMD FTL integration (AGP `managedDevices.devices` + gcloud auth) —
   this is what actually runs devices in Google's cloud; see DEVIATIONS D-1.

DEVIATIONS FROM THE OWNER COMMAND (must-read)
---------------------------------------------
- D-1 "Cloud" misnomer: Gradle Managed Devices `localDevices` run the emulator ON
  THE BUILD MACHINE, not in Google's cloud. The owner's steps describe a cloud
  emulator; the implemented DSL is the local flavor (exactly as the command
  specified). True cloud testing = Firebase Test Lab GMD integration, which needs
  gcloud credentials/billing and was NOT configured without owner approval.
- D-2 Task names: `testReleaseOnPixel5Api33` and `connectedReleaseAndroidTest` do
  not exist in this AGP version. Real equivalents documented in STEPS 4-5.
- D-3 Test-source gap: with zero test sources, PASSED/FAILED counts are 0/0 by
  definition. Infrastructure is ready; tests must be authored (A12) for the
  results table to become meaningful.

EVIDENCE INDEX (/audit/evidence/cloud-emulator-tests/)
------------------------------------------------------
- gradle-version.log     — Gradle 8.10.2 / launcher+daemon JDK 21
- gmd-tasks-all.log      — full task list (proves which tasks exist / not exist)
- unit-tests.log         — owner command failure + testReleaseUnitTest NO-SOURCE
- ui-tests.log           — pixel5api33DebugAndroidTest full provisioning failure log
- environment-checks.log — date, /dev/kvm probe, accel-check exit 3, AVD probe
- monkey.log             — adb devices (empty) + KVM/disk/RAM facts

ADDENDUM A — FIREBASE TEST LAB (OPTION A) — 2026-09-21 (later same day)
----------------------------------------------------------------------
Owner chose Option A (true cloud) and supplied project ID areenax-dev.
Executed in order, with evidence:
1. Google Cloud SDK 585.0.0 installed in sandbox (gcloud-install.log;
   stable-channel URL 404 -> rapid-channel OK).
2. Owner OAuth (application-default credentials): attempt 1 crashed on a
   gcloud 585 scope-negotiation bug (Google returned fewer scopes than
   requested; userinfo.email dropped). Attempt 2 with explicit
   --scopes="openid,cloud-platform" SUCCEEDED. ADC stored OUTSIDE the repo
   (/home/z/.config/gcloud) — never committed. Token mint verified.
3. APIs enabled on areenax-dev (owner's OAuth, verified afterwards):
   testing.googleapis.com + toolresults.googleapis.com -> ENABLED (operation
   finished; re-list confirms both).
4. Cloud device catalog pulled: 208 models = 199 PHYSICAL + 9 VIRTUAL.
   DEVIATION D-4: NO Pixel 5 exists in the VIRTUAL fleet. Available virtual
   (ARM ATD emulators): Pixel2.arm (APIs 26-33), SmallPhone.arm, MediumPhone.arm,
   MediumTablet.arm, TVs/tablets. Closest match to owner spec = Pixel2.arm @ API 33.
   Physical Pixel 5s exist but physical devices consume the scarce free quota.
5. APK rebuilt: assembleDebug at LAST-KNOWN-GOOD native commit a25623a
   (12,763,965 bytes — byte-identical to Task-13 verified build).
   ENV NOTE: sandbox rootfs was replaced mid-session (4th toolchain wipe);
   recovered via the (recreated) /home/z/bootstrap-build-toolchain.sh, now also
   committed to tools/ for resilience.
   REGRESSION ALERT (not this task): current main tip b0bfd48 DOES NOT COMPILE —
   newly landed 25-file Firebase-push workstream (AreenaxMessagingService.kt +
   vault-gen.gradle.kts + res/xml) has unresolved 'firebase' references (the
   dependency was never added). FTL therefore tests a25623a, not main tip.
6. Robo run ATTEMPT 1: FAILED BEFORE EXECUTION — 403 "The billing account for
   the owning project is disabled in state absent" on APK upload to the
   auto-created results bucket gs://test-lab-rhypt391py6z0-ikf9qq9rt5qsq
   (bucket existence verified via Storage API). CONCLUSION / D-5: Test Lab
   requires a linked billing account even on the free Spark tier (results-bucket
   storage is billing-gated). CORRECTION of the agent's earlier "no card
   needed" claim: a billing account IS required; within free daily quotas the
   charge is $0. Not fixable agent-side — owner-only action.

FTL PHASE STATUS: infrastructure + auth + APIs + build = READY; FIRST CLOUD
RUN = BLOCKED ON OWNER (link billing to areenax-dev), then re-run:
  gcloud firebase test android run --type=robo --app=<apk> \
    --device=model=Pixel2.arm,version=33 --timeout=5m --project=areenax-dev
Everything else is pre-staged; retry costs one command.

EVIDENCE INDEX ADDITIONS
------------------------
- gcloud-install.log     — SDK install + version
- assembleDebug-ftl.log  — last-good-commit build (SUCCESSFUL, 12,763,965 B)
- ftl-robo-run1.log      — 403 billing-absent blocker + bucket/billing probes
