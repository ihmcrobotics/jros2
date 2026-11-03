#!/bin/bash
#
#  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
# Installs the instrumentation APK with a forced ABI and runs every instrumented test with it on the connected device.
#
# connectedDebugAndroidTest always uses the device's primary ABI, which is x86_64 on emulators. The x86_64 images of
# API 30+ also list arm64-v8a in ro.product.cpu.abilist and run arm64 code through Android's ARM translation
# (libndk_translation). Installing with --abi arm64-v8a therefore executes the arm64-v8a jniLibs, which real phones load,
# on an x86_64 host, including on a 16 KB page size image.
#
# Usage: instrument-abi.sh <abi> <test-apk> [-e <instrumentation argument> <value> ...]
#   e.g. instrument-abi.sh arm64-v8a build/outputs/apk/androidTest/debug/jros2-android-debug-androidTest.apk -e expectSecurity true
# Uses $ADB, else adb from PATH, else $ANDROID_HOME/platform-tools/adb, and the device selected by $ANDROID_SERIAL.
# Exits 0 without running anything when the device cannot run <abi>, 1 when a test fails.
set -euo pipefail

[ $# -ge 2 ] || { echo "usage: $0 <abi> <test-apk> [-e <name> <value> ...]" >&2; exit 2; }
ABI=$1
APK=$2
shift 2
ADB=${ADB:-$(command -v adb || echo "${ANDROID_HOME:-}/platform-tools/adb")}
PACKAGE=us.ihmc.jros2.test
RUNNER=$PACKAGE/androidx.test.runner.AndroidJUnitRunner

abilist=$("$ADB" shell getprop ro.product.cpu.abilist | tr -d '\r')
case ",$abilist," in
   *",$ABI,"*) ;;
   *) echo "The device runs only $abilist, not $ABI: skipped"; exit 0 ;;
esac

"$ADB" uninstall "$PACKAGE" > /dev/null 2>&1 || true
"$ADB" install --abi "$ABI" -r -t "$APK"
primary=$("$ADB" shell dumpsys package "$PACKAGE" | tr -d '\r' | sed -n 's/^ *primaryCpuAbi=//p' | head -1)
if [ "$primary" != "$ABI" ]; then
   echo "$PACKAGE was installed with primaryCpuAbi=$primary instead of $ABI" >&2
   exit 1
fi

# am instrument exits with 0 even when tests fail; the result is in its output ("OK (n tests)" or "FAILURES!!!").
output=$("$ADB" shell am instrument -w -r "$@" "$RUNNER" | tr -d '\r')
"$ADB" uninstall "$PACKAGE" > /dev/null 2>&1 || true
if echo "$output" | grep -q '^OK ('; then
   echo "$ABI: $(echo "$output" | grep '^OK (')"
   exit 0
fi
echo "$output"
echo "$ABI: instrumented tests FAILED" >&2
exit 1
