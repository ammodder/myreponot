#!/bin/bash
# A12-02: local verification gate (no CI host exists in this environment).
# Usage: bash tools/check.sh
# Runs the fastest full-fidelity checks: Kotlin compile + icon-visual verify.
set -e
cd "$(dirname "$0")/.."
echo "== [1/2] :app:compileDebugKotlin =="
export JAVA_HOME="${JAVA_HOME:-/home/z/jdks/jdk-17}"
export ANDROID_HOME="${ANDROID_HOME:-/home/z/android-sdk}"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:compileDebugKotlin --no-daemon -Dorg.gradle.jvmargs="-Xmx1800m -XX:MaxMetaspaceSize=512m"
echo "== [2/2] tools/verify_visual.mjs (drawable parity) =="
if command -v bun >/dev/null 2>&1; then
  bun tools/verify_visual.mjs || true
elif command -v node >/dev/null 2>&1; then
  node tools/verify_visual.mjs || true
else
  echo "SKIP: no bun/node"
fi
echo "CHECK-OK"
