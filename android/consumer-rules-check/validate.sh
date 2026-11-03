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
# Validates android/consumer-rules.pro with R8 itself.
#
# The sample app (src/, a few lines that use jros2 like an Android app) and the jros2 jar are shrunk, optimized and
# obfuscated by R8 twice:
#   with-rules    : sample-rules.pro + ../consumer-rules.pro   positive control, every check must pass
#   without-rules : sample-rules.pro only                      negative control, checks must fail
# Each configuration is checked in two ways:
#   1. dex output (--min-api 26 against android.jar, what an app ships): check_dex.py verifies with mapping.txt + dexdump
#      that the JNI class/natives, the callback interface and the reflectively used message members exist under their
#      original names.
#   2. class-file output (--classfile against the running JDK), run on this JVM: R8Sample performs every runtime lookup
#      (Class.getField, getDeclaredConstructor, getDeclaredMethods, JNI natives and the native->Java callback).
#      This creates one DDS participant on domain $ROS_DOMAIN_ID (default 176). Set SKIP_JVM_RUN=1 to skip it.
#
# Usage: validate.sh <jros2.jar>     (the root project's ./gradlew jar output, classes + natives)
# Env:   ANDROID_HOME (needs build-tools/<v>/lib/d8.jar, which contains R8, build-tools/<v>/dexdump and platforms/android-NN)
#        BUILD_TOOLS (optional: the build-tools directory that provides R8 and dexdump; default: the newest one in ANDROID_HOME)
#        WORK (output directory, default consumer-rules-check/build)
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
JROS2_JAR=$(readlink -f "$1")
: "${ANDROID_HOME:?ANDROID_HOME must point to the Android SDK}"
BUILD_TOOLS=${BUILD_TOOLS:-$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)}
R8_JAR=$BUILD_TOOLS/lib/d8.jar
DEXDUMP=$BUILD_TOOLS/dexdump
PLATFORM=$(ls -d "$ANDROID_HOME"/platforms/android-* | sort -V | tail -1)
ANDROID_JAR=$PLATFORM/android.jar
JAVA_BIN=$(readlink -f "$(command -v java)")
JDK_HOME=${JAVA_HOME:-$(dirname "$(dirname "$JAVA_BIN")")}
DOMAIN=${ROS_DOMAIN_ID:-176}
WORK=${WORK:-$HERE/build}

echo "R8:          $R8_JAR ($(java -cp "$R8_JAR" com.android.tools.r8.R8 --version 2>/dev/null | head -1))"
echo "android.jar: $ANDROID_JAR"
echo "jros2 jar:   $JROS2_JAR"

rm -rf "$WORK"
mkdir -p "$WORK/sample-classes" "$WORK/resources"

javac --release 17 -encoding UTF-8 -cp "$JROS2_JAR" -d "$WORK/sample-classes" $(find "$HERE/src" -name '*.java')
jar cf "$WORK/sample.jar" -C "$WORK/sample-classes" .

# Natives and other resources of the jar, for the JVM run of the class-file output.
(cd "$WORK/resources" && unzip -q "$JROS2_JAR" -x '*.class' 'META-INF/*' && jar cf "$WORK/resources.jar" .)

EXPECT_TYPE_HASH=false
if javap -cp "$JROS2_JAR" std_msgs.String_ | grep -q ' typeHash;'; then
   EXPECT_TYPE_HASH=true
fi
NATIVE_COUNT=$(javap -p -cp "$JROS2_JAR" us.ihmc.fastddsjava.natives.fastddsjava | grep -c ' native ')
echo "jar has $NATIVE_COUNT native methods; typeHash fields: $EXPECT_TYPE_HASH"

declare -A DEX_FAILURES JVM_EXIT
for mode in with-rules without-rules; do
   out=$WORK/$mode
   mkdir -p "$out/dex"
   confs=(--pg-conf "$HERE/sample-rules.pro")
   if [ "$mode" = with-rules ]; then
      confs+=(--pg-conf "$HERE/../consumer-rules.pro")
   fi
   printf -- '-printmapping %s\n-printseeds %s\n-printusage %s\n' "$out/mapping.txt" "$out/seeds.txt" "$out/usage.txt" > "$out/report.pro"
   printf -- '-printmapping %s\n' "$out/mapping-classfile.txt" > "$out/report-classfile.pro"

   echo
   echo "===== $mode: R8 dex (--min-api 26)"
   java -cp "$R8_JAR" com.android.tools.r8.R8 --release --min-api 26 --lib "$ANDROID_JAR" --output "$out/dex" \
        "${confs[@]}" --pg-conf "$out/report.pro" "$WORK/sample.jar" "$JROS2_JAR" 2>&1 | grep -v '^Warning: Resource' || true
   "$DEXDUMP" "$out"/dex/classes*.dex > "$out/dexdump.txt"
   set +e
   python3 -I "$HERE/check_dex.py" "$out/mapping.txt" "$out/dexdump.txt" "$NATIVE_COUNT" "$EXPECT_TYPE_HASH"
   DEX_FAILURES[$mode]=$?
   set -e

   if [ "${SKIP_JVM_RUN:-0}" != 1 ]; then
      echo "===== $mode: R8 class files, run on the JVM (DDS domain $DOMAIN)"
      java -cp "$R8_JAR" com.android.tools.r8.R8 --classfile --release --lib "$JDK_HOME" --output "$out/shrunk.jar" \
           "${confs[@]}" --pg-conf "$out/report-classfile.pro" "$WORK/sample.jar" "$JROS2_JAR" 2>&1 | grep -v '^Warning: Resource' || true
      set +e
      timeout 120 java -Dexpect.typeHash="$EXPECT_TYPE_HASH" -Dros.domain.id="$DOMAIN" -cp "$out/shrunk.jar:$WORK/resources.jar" \
           us.ihmc.jros2.r8check.R8Sample 2>&1 | grep -E '^\[r8-check\]|Exception|Error' | head -40
      JVM_EXIT[$mode]=${PIPESTATUS[0]}
      set -e
   else
      JVM_EXIT[$mode]=skipped
   fi
done

echo
echo "===== summary"
echo "with-rules:    dex check failures=${DEX_FAILURES[with-rules]}  JVM run exit=${JVM_EXIT[with-rules]}"
echo "without-rules: dex check failures=${DEX_FAILURES[without-rules]}  JVM run exit=${JVM_EXIT[without-rules]}"
ok=1
[ "${DEX_FAILURES[with-rules]}" = 0 ] || ok=0
[ "${DEX_FAILURES[without-rules]}" != 0 ] || ok=0
if [ "${SKIP_JVM_RUN:-0}" != 1 ]; then
   [ "${JVM_EXIT[with-rules]}" = 0 ] || ok=0
   [ "${JVM_EXIT[without-rules]}" != 0 ] || ok=0
fi
if [ $ok = 1 ]; then
   echo "consumer-rules.pro VALIDATED: positive control passes, negative control fails"
else
   echo "consumer-rules.pro NOT validated"
   exit 1
fi
