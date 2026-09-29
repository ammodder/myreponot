# RUNTIME ENVIRONMENT — what this sandbox CAN and CANNOT test (2026-09-19)

## What EXISTS here [VERIFIED — evidence/phase0-env-probe-2026-09-19.log]
- OS: Debian 13 (container), x86_64, ~7.1 GB free disk.
- Java: OpenJDK **21 JRE** (system, no javac) + **JDK 17.0.20.1** bootstrapped to `~/jdks` during Phase 0.
- Android SDK: NONE pre-installed → bootstrapped during Phase 0 into `~/android-sdk`
  (cmdline-tools 11076708, platform-tools, platforms;android-35, build-tools;35.0.0, licenses accepted;
  AGP also auto-installed build-tools;34.0.0 during the first build attempt).
- Gradle 8.10.2 wrapper distribution downloads fine (network is open, incl. dl.google.com + adoptium).
- Node/bun available (used by icon tools / web project).

## What we CAN do [VERIFIED by doing it]
- Full Gradle configuration and resource processing of the app (reached mergeDebugResources).
- Kotlin compilation attempts with the REAL compiler (currently failing — see PHASE0-BLOCKERS.md).
- Static analysis of any depth (grep/AST-level checks, route wiring, symbol resolution).
- Git evidence, packaging (zip), and document production.

## What we CANNOT do [NOT VERIFIED — reason: no hardware/emulator]
- NO emulator: KVM/HAXM hardware acceleration unavailable in this container; no AVD system images
  installed; booting an emulator here is not feasible.
- NO physical device: no USB/ADB device passthrough.
- Therefore: cannot run the app, cannot take any screenshot (light or dark), cannot measure
  cold-start time or idle memory, cannot exercise any UI flow, cannot run instrumented tests,
  cannot verify any runtime behavior against the backend.
- No keystore → cannot produce a SIGNED release APK (unsigned release build would be possible
  once the compile blockers are fixed).

## Honest summary for the owner
Everything in Phase 0 is static: files, configs, and compiler results. Any claim about how the
app LOOKS or BEHAVES on a phone is NOT VERIFIED until you either (a) provide a machine with
Android Studio / an emulator, or (b) build the APK on your side and run it.

## Toolchain bootstrap facts (reproducible)
- JDK: https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse → ~/jdks
- SDK: https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip → ~/android-sdk
  - `yes | sdkmanager --licenses` then platform-tools, platforms;android-35, build-tools;35.0.0
- Project link: `AreenaxNativeAndroid/local.properties` → `sdk.dir` (git-ignored file, machine-local).
- JAVA_HOME must point to ~/jdks/jdk-17.0.20.1+1 for Gradle builds.
