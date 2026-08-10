#!/usr/bin/env bash
#
# build-apk-manual.sh — offline APK build for Raijin's Lawncher.
#
# Builds the APK WITHOUT Gradle by driving the Android SDK toolchain directly:
#   aapt2 (resources) → javac (classes) → d8 (dex) → cmake+NDK (native) →
#   zip/zipalign (packaging) → apksigner (signing).
#
# Usage:
#   ./build-apk-manual.sh [output-dir]        (defaults to build/offline)
#
# Env overrides:
#   ANDROID_HOME   path to the SDK root (defaults to the documented location)
#   ANDROID_NDK    path to the NDK     (defaults to $ANDROID_HOME/ndk/29.0.14206865)
#   APK_OUT        final apk path
#
set -euo pipefail

SDK="${ANDROID_HOME:-/run/media/quantumcreeper/TVPG/linuxFiles/Applications/android-ndk}"
NDK="${ANDROID_NDK:-$SDK/ndk/29.0.14206865}"
BT="$SDK/build-tools/37.0.0"
PLATFORM="$SDK/platforms/android-36.1"
JAR="$PLATFORM/android.jar"
CMAKE="$SDK/cmake/3.22.1/bin/cmake"

OUT="${1:-build/offline}"
FINAL="${APK_OUT:-$OUT/lawncher-debug.apk}"

ABIS=(arm64-v8a armeabi-v7a)

require() { command -v "$1" >/dev/null 2>&1 || { echo "missing: $1"; exit 1; }; }
require "$BT/aapt2"; require "$BT/d8"; require "$BT/zipalign"; require "$BT/apksigner"
require javac; require keytool; require zip; require unzip
[ -f "$JAR" ] || { echo "platform jar not found: $JAR"; exit 1; }
[ -f "$NDK/build/cmake/android.toolchain.cmake" ] || { echo "NDK toolchain not found under $NDK"; exit 1; }

rm -rf "$OUT" && mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex"

# 1. Compile resources
echo "== aapt2 compile =="
while IFS= read -r f; do
  rel="${f#app/src/main/res/}"
  dir="$(dirname "$rel")"
  mkdir -p "$OUT/res/$dir"
  "$BT/aapt2" compile -o "$OUT/res/$dir" "$f"
done < <(find app/src/main/res -type f)

# 2. Link (aapt2 needs a package attribute — AGP injects this from namespace)
echo "== aapt2 link =="
sed 's|<manifest xmlns:android=|<manifest package="net.kiwi.lawncher" xmlns:android=|' \
  app/src/main/AndroidManifest.xml > "$OUT/Manifest.xml"
"$BT/aapt2" link -o "$OUT/apk-unsigned.apk" \
  -I "$JAR" --manifest "$OUT/Manifest.xml" --java "$OUT/gen" \
  --min-sdk-version 24 --target-sdk-version 36 \
  --version-code 1 --version-name "1.0" \
  -A app/src/main/assets \
  $(find "$OUT/res" -name '*.flat')

# 3. Compile Java → classes
echo "== javac =="
javac -Xlint:-options -nowarn -classpath "$JAR" -d "$OUT/classes" \
  $(find app/src/main/java -name '*.java') "$OUT/gen/net/kiwi/lawncher/R.java"

# 4. Dex
echo "== d8 =="
"$BT/d8" --release --lib "$JAR" --min-api 24 --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class')

# 5. Native libraries (both ABIs)
for abi in "${ABIS[@]}"; do
  echo "== cmake $abi =="
  "$CMAKE" -S app/src/main/cpp -B "$OUT/cmake-$abi" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" -DANDROID_PLATFORM=android-24 \
    -DCMAKE_BUILD_TYPE=Release > /dev/null
  "$CMAKE" --build "$OUT/cmake-$abi" -j 4
done

# 6. Package: dex at root, .so stored uncompressed (needed on modern Android)
echo "== packaging =="
cp "$OUT/apk-unsigned.apk" "$OUT/stage.apk"
zip -j "$OUT/stage.apk" "$OUT/dex/classes.dex" > /dev/null
rm -rf "$OUT/libstage" && mkdir -p "$OUT/libstage/lib"
for abi in "${ABIS[@]}"; do
  mkdir -p "$OUT/libstage/lib/$abi"
  cp "$OUT/cmake-$abi/liblawncher.so" "$OUT/libstage/lib/$abi/"
  # Prebuilt GlossHook shared lib from jniLibs (liblawncher.so links against it)
  cp "app/src/main/jniLibs/$abi/libGlossHook.so" "$OUT/libstage/lib/$abi/"
done
(cd "$OUT/libstage" && zip -0 -r "../stage.apk" lib > /dev/null)

# 7. Align + sign with a debug key
echo "== zipalign + sign =="
"$BT/zipalign" -f -p 4 "$OUT/stage.apk" "$OUT/aligned.apk"
if [ ! -f "$OUT/debug.keystore" ]; then
  keytool -genkeypair -keystore "$OUT/debug.keystore" -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android \
    -dname "CN=Android Debug,O=Android,C=US"
fi
"$BT/apksigner" sign --ks "$OUT/debug.keystore" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android --out "$FINAL" "$OUT/aligned.apk"

"$BT/apksigner" verify "$FINAL"
echo
echo "BUILT: $FINAL"
