#!/bin/bash
# AREENAX Android build toolchain bootstrap (sandbox resets wipe /home/z tools).
# Proven combo (worklog Tasks 9/12/13/15): Temurin JDK 21 (full JDK, javac included)
# + cmdline-tools + platforms;android-35 + build-tools 34/35 + platform-tools
# at /home/z/android-sdk. Idempotent: skips parts already present.
set -e
cd /home/z

# 1) Temurin JDK 21 -> /home/z/jdk21
if [ ! -x /home/z/jdk21/bin/javac ]; then
  echo "[bootstrap] installing Temurin JDK 21..."
  curl -fsSL -o /home/z/jdk21.tar.gz "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
  rm -rf /home/z/jdk21 && mkdir -p /home/z/jdk21
  tar -xzf /home/z/jdk21.tar.gz -C /home/z/jdk21 --strip-components=1
  rm -f /home/z/jdk21.tar.gz
fi
echo "[bootstrap] JDK: $(/home/z/jdk21/bin/javac -version 2>&1)"

# 2) Android cmdline-tools
SDK=/home/z/android-sdk
mkdir -p "$SDK/cmdline-tools"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "[bootstrap] installing cmdline-tools..."
  curl -fsSL -o /tmp/cmt.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  rm -rf /tmp/cmt && python3 -m zipfile -e /tmp/cmt.zip /tmp/cmt
  rm -rf "$SDK/cmdline-tools/latest" && mv /tmp/cmt/cmdline-tools "$SDK/cmdline-tools/latest"
  chmod -R u+x "$SDK/cmdline-tools/latest/bin"
  rm -f /tmp/cmt.zip
fi

# 3) Licenses (all known hashes)
mkdir -p "$SDK/licenses"
echo "8933bad161af4178b1185d1a37fbf41ea5269c55" > "$SDK/licenses/android-sdk-license"
echo "d56f5187479451eabf01fb78af6dfcb131a6481e" > "$SDK/licenses/android-sdk-license"
echo "24333f8a63b6825ea9c5514f83c2829b004d1fee" > "$SDK/licenses/android-sdk-license"

# 4) Platform / build-tools / platform-tools via sdkmanager
export JAVA_HOME=/home/z/jdk21
export ANDROID_HOME=$SDK
echo "[bootstrap] sdkmanager installing platform + build-tools + platform-tools (this takes a few minutes)..."
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" \
  "platforms;android-35" "build-tools;34.0.0" "build-tools;35.0.0" "platform-tools" \
  > /tmp/sdkmanager.log 2>&1 || { tail -n 20 /tmp/sdkmanager.log; exit 1; }
echo "[bootstrap] DONE. Installed:"
ls "$SDK/platforms" "$SDK/build-tools" "$SDK/platform-tools" | sed -n '1,12p'
