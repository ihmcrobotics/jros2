#!/usr/bin/env bash
#  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

# Builds libjnifastddsjava from the repository's src/native/*.cpp against the dependency cache, then stages, strips and
# verifies the complete platform directory in /out/.staging-<platform>. Runs inside the build container.

# shellcheck disable=SC2054  # -Wl,a,b arguments are single array elements on purpose
set -euo pipefail

: "${PLATFORM:?}" "${DEPS_FINGERPRINT:?}" "${SECURITY:?}"
# shellcheck source=env.sh
. /scripts/env.sh

D=/work/deps
I=$D/install
J=/work/jni
STAGE=/out/.staging-$PLATFORM

log() { echo "[jni $PLATFORM] $*"; }

if [ ! -f "$D/.fingerprint" ]; then
   echo "ERROR: no dependency cache in $D; run build-natives.sh without --jni-only first" >&2
   exit 3
fi
if [ "$(cat "$D/.fingerprint")" != "$DEPS_FINGERPRINT" ]; then
   echo "ERROR: the dependency cache in $D was built from different versions/scripts" >&2
   echo "       (cache $(cat "$D/.fingerprint"), expected $DEPS_FINGERPRINT); run build-natives.sh without --jni-only" >&2
   exit 3
fi

t0=$SECONDS
rm -rf "$J"
mkdir -p "$J"
shopt -s nullglob
SRCS=(/repo/src/native/*.cpp)
shopt -u nullglob
[ ${#SRCS[@]} -gt 0 ] || { echo "ERROR: no sources in /repo/src/native" >&2; exit 2; }
log "sources: ${SRCS[*]##*/}"

# Only Java_* / JNI_On* are exported (every entry point is JNIEXPORT); --no-undefined turns a missing symbol into a
# link error instead of an UnsatisfiedLinkError at runtime.
FLAGS=(-std=c++17 -O3 -DNDEBUG -Wall -fPIC -shared
       -fvisibility=hidden -fvisibility-inlines-hidden -ffile-prefix-map=/repo/=
       -I/repo/src/native "-I$I/include")
LDFLAGS=("-L$I/lib" -Wl,--no-undefined -Wl,-soname,libjnifastddsjava.so -Wl,-z,noexecstack -Wl,--as-needed)
if [ "$KIND" = android ]; then
   # NDK jni.h comes from the sysroot. No rpath: bionic resolves NEEDED libraries by SONAME from the APK lib dir.
   FLAGS+=(-fexceptions -frtti)
   if [ "$ANDROID_ABI" != armeabi-v7a ]; then LDFLAGS+=(-Wl,-z,max-page-size=16384); fi
   LIBS=(-lfastdds -lfastcdr -llog -lc++_shared)
else
   FLAGS+=(-pthread "-I$JAVA_INC" "-I$JAVA_INC/linux")
   LDFLAGS+=(-Wl,--enable-new-dtags "-Wl,-rpath,\$ORIGIN")
   LIBS=(-lfastdds -lfastcdr)
   # Static libatomic must come after the objects (see env.sh); the -pthread driver spec adds '-latomic' as well,
   # which the libatomic.a-only search directory turns into the static archive too.
   # shellcheck disable=SC2206  # STD_LIBS is a list of flags
   if [ -n "$STD_LIBS" ]; then LDFLAGS+=(-L/work/static-libatomic); LIBS+=($STD_LIBS -Wl,--exclude-libs,libatomic.a); fi
fi
"$JNI_CXX" "${FLAGS[@]}" "${SRCS[@]}" -o "$J/libjnifastddsjava.so" "${LDFLAGS[@]}" "${LIBS[@]}"
log "[time] jni: $((SECONDS - t0)) s"

# ---------------------------------------------------------------- stage
rm -rf "$STAGE"
mkdir -p "$STAGE"
soname_of() { readelf -dW "$1" | sed -n 's/.*(SONAME).*\[\(.*\)\]/\1/p'; }
if [ "$KIND" = android ]; then
   # Current jniLibs names (System.loadLibrary): libc++_shared.so, libfastcdr.so, libfastdds.so, libjnifastddsjava.so.
   cp "$I/lib/libfastcdr.so" "$I/lib/libfastdds.so" "$J/libjnifastddsjava.so" "$STAGE/"
   # NDK r28+ ships libc++_shared.so with debug info (~9 MB); stripped it is ~1.4 MB.
   cp "$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/$NDK_TRIPLE/libc++_shared.so" "$STAGE/"
else
   # Linux/musl ship each dependency under its SONAME (libfastcdr.so.2, libfastdds.so.3.6): musl's loader only finds an
   # already extracted dependency by that name through libfastdds' RUNPATH $ORIGIN.
   for lib in fastcdr fastdds; do
      real=$(readlink -f "$I/lib/lib$lib.so")
      soname=$(soname_of "$real")
      [ -n "$soname" ] || { echo "ERROR: $real has no SONAME" >&2; exit 1; }
      cp "$real" "$STAGE/$soname"
      echo "$soname" >> "$STAGE/natives.list.tmp"
   done
   cp "$J/libjnifastddsjava.so" "$STAGE/"
   # Load order, one file name per line, JNI library last (read by fastddsjavaNativeLibrary).
   { cat "$STAGE/natives.list.tmp"; echo libjnifastddsjava.so; } > "$STAGE/natives.list"
   rm -f "$STAGE/natives.list.tmp"
fi
for f in "$STAGE"/*.so*; do
   "$STRIP" --strip-unneeded "$f"
done
chmod 0644 "$STAGE"/*

# ---------------------------------------------------------------- verify
JAVA_SRC=/repo/src/main/java/us/ihmc/fastddsjava/natives/fastddsjava.java
EXPECT_SECURITY=$([ "$SECURITY" = ON ] && echo 1 || echo 0) \
   bash /scripts/verify-natives.sh "$PLATFORM" "$STAGE" "$JAVA_SRC"
