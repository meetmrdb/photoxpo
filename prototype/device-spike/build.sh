#!/usr/bin/env bash
# PROTOTYPE: build the spike APK with the Android SDK tools only (no Gradle, no downloads).
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
BT=$SDK/build-tools/36.0.0
JAR=$SDK/platforms/android-35/android.jar
rm -rf out && mkdir -p out/classes
javac --release 17 -cp "$JAR" -d out/classes src/spike/*.java
$BT/d8 --min-api 34 --lib "$JAR" --output out out/classes/spike/*.class
$BT/aapt2 link --manifest AndroidManifest.xml -I "$JAR" --min-sdk-version 34 --target-sdk-version 35 -o out/base.apk
(cd out && cp base.apk app.apk && zip -qj app.apk classes.dex)
$BT/zipalign -f 4 out/app.apk out/aligned.apk
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias spike -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=spike" >/dev/null 2>&1
$BT/apksigner sign --ks debug.keystore --ks-pass pass:android --out photoxpo-spike.apk out/aligned.apk
echo "built: $(pwd)/photoxpo-spike.apk"
