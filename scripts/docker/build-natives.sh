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

# Reproducible, Docker-based build of the jros2 natives (Fast-CDR, Fast-DDS with DDS-Security, libjnifastddsjava) for
# the Linux (glibc 2.31 baseline), musl and Android platforms. Used locally and by .github/workflows/build-natives.yml.
#
# usage: scripts/docker/build-natives.sh <platform> <repo-dir> <out-dir> [--jni-only] [--rebuild-deps]
#
#   platform   linux-x86_64 linux-arm64 linux-armhf linux-riscv64      (ubuntu:20.04 image, gcc 9 cross compilers)
#              linux-musl-x86_64 linux-musl-arm64                      (alpine:3.24 image, built natively for its arch;
#                                                                       arm64 runs under qemu on an x86_64 host)
#              android-arm64-v8a android-armeabi-v7a android-x86_64    (NDK r30, API 26)
#   repo-dir   jros2 checkout, mounted read-only; src/native/*.cpp is compiled, fastddsjava.java is used for checks
#   out-dir    receives <out-dir>/<platform>/ (replaced only after the build and its verification succeed):
#                Linux/musl: libfastcdr.so.2 libfastdds.so.3.6 libjnifastddsjava.so natives.list
#                Android:    libc++_shared.so libfastcdr.so libfastdds.so libjnifastddsjava.so
#   --jni-only      rebuild only libjnifastddsjava from src/native against the cached dependencies (fails if the
#                   dependency cache of the platform is missing or was built from other versions/scripts)
#   --rebuild-deps  rebuild the dependencies even if the cache is valid
#
# Without --jni-only the dependencies (OpenSSL 3.5 static, foonathan memory, Fast-CDR, Fast-DDS; versions and
# checksums in scripts/native-versions.env) are built once per platform into <work-root>/<platform>/deps and reused
# while their fingerprint (versions, Dockerfile, env.sh, deps.sh, src/native/fastdds-patches, SECURITY) is unchanged.
#
# Environment:
#   JROS2_WORK_ROOT     work/cache root (default ${XDG_CACHE_HOME:-~/.cache}/jros2-natives); contains _sources/,
#                       _cache/ (Android NDK) and one directory per platform (deps/, logs/, jni/)
#   JROS2_ANDROID_NDK   use this extracted NDK instead of downloading r30 (its source.properties must say r30)
#   JOBS                parallel compile jobs (default: nproc)
#   SECURITY            ON (default; DDS-Security with static OpenSSL) or OFF
#   JNI_ALLOW_MISSING   space-separated native method names the JNI library may lack (see verify-natives.sh)
#   KEEP_BUILD_TREES=1  keep the dependency source/build trees (default: deleted after a successful build)
#   DOCKER              docker command (default: docker)

# Braces: bash parses the whole script before running it, so editing this file during a long build is safe.
{
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERSIONS_FILE="$SCRIPT_DIR/../native-versions.env"
PLATFORMS="linux-x86_64 linux-arm64 linux-armhf linux-riscv64 linux-musl-x86_64 linux-musl-arm64 android-arm64-v8a android-armeabi-v7a android-x86_64"

usage() {
   sed -n '16,45p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
   exit 2
}

MODE=full
REBUILD_DEPS=0
POSITIONAL=()
for arg in "$@"; do
   case "$arg" in
      --jni-only) MODE=jni-only ;;
      --rebuild-deps) REBUILD_DEPS=1 ;;
      -h | --help) usage ;;
      -*) echo "unknown option $arg" >&2; usage ;;
      *) POSITIONAL+=("$arg") ;;
   esac
done
[ ${#POSITIONAL[@]} -eq 3 ] || usage
PLATFORM=${POSITIONAL[0]}
REPO=$(cd "${POSITIONAL[1]}" && pwd)
mkdir -p "${POSITIONAL[2]}"
OUT=$(cd "${POSITIONAL[2]}" && pwd)
case " $PLATFORMS " in *" $PLATFORM "*) ;; *) echo "unknown platform '$PLATFORM' (one of: $PLATFORMS)" >&2; exit 2 ;; esac
[ "$MODE" = jni-only ] && [ "$REBUILD_DEPS" = 1 ] && { echo "--jni-only and --rebuild-deps exclude each other" >&2; exit 2; }
[ -f "$REPO/src/native/jnifastddsjava.cpp" ] || { echo "not a jros2 checkout: $REPO" >&2; exit 2; }

# shellcheck source=../native-versions.env
. "$VERSIONS_FILE"

DOCKER=${DOCKER:-docker}
JOBS=${JOBS:-$(nproc)}
SECURITY=${SECURITY:-ON}
case "$SECURITY" in ON | OFF) ;; *) echo "SECURITY must be ON or OFF" >&2; exit 2 ;; esac
WORK_ROOT=${JROS2_WORK_ROOT:-${XDG_CACHE_HOME:-$HOME/.cache}/jros2-natives}
SOURCES=$WORK_ROOT/_sources
CACHE=$WORK_ROOT/_cache
WORK=$WORK_ROOT/$PLATFORM
mkdir -p "$SOURCES" "$CACHE" "$WORK/logs" "$WORK/home"
WORK_ROOT=$(cd "$WORK_ROOT" && pwd)
SOURCES=$WORK_ROOT/_sources
CACHE=$WORK_ROOT/_cache
WORK=$WORK_ROOT/$PLATFORM

exec 9> "$WORK/.lock"
if command -v flock > /dev/null && ! flock -n 9; then
   echo "another build of $PLATFORM is using $WORK" >&2
   exit 1
fi

LOG="$WORK/logs/build-$(date +%Y%m%d-%H%M%S).log"
exec > >(tee -a "$LOG") 2>&1
START=$(date +%s)
echo "[build-natives] $PLATFORM mode=$MODE security=$SECURITY jobs=$JOBS work=$WORK log=$LOG"

# ------------------------------------------------------------------------------------------------ sources
fetch() { # url file sha256 : download once, always verify
   local url=$1 file=$2 sha=$3
   if [ -f "$file" ] && echo "$sha  $file" | sha256sum -c --quiet - > /dev/null 2>&1; then return 0; fi
   echo "[build-natives] downloading $url"
   curl -fsSL --retry 5 --retry-delay 3 -o "$file.part" "$url"
   if ! echo "$sha  $file.part" | sha256sum -c --quiet -; then
      echo "checksum mismatch for $url (expected $sha)" >&2
      rm -f "$file.part"
      exit 1
   fi
   mv "$file.part" "$file"
}
# Only a dependency build reads the sources; --jni-only compiles the repository against the cache (works offline).
GH=https://codeload.github.com
if [ "$MODE" = full ]; then
   fetch "$GH/eProsima/Fast-DDS/tar.gz/$FASTDDS_COMMIT" "$SOURCES/Fast-DDS-$FASTDDS_COMMIT.tar.gz" "$FASTDDS_SHA256"
   fetch "$GH/chriskohlhoff/asio/tar.gz/$ASIO_COMMIT" "$SOURCES/asio-$ASIO_COMMIT.tar.gz" "$ASIO_SHA256"
   fetch "$GH/leethomason/tinyxml2/tar.gz/$TINYXML2_COMMIT" "$SOURCES/tinyxml2-$TINYXML2_COMMIT.tar.gz" "$TINYXML2_SHA256"
   fetch "$GH/michalsrb/android-ifaddrs/tar.gz/$ANDROID_IFADDRS_COMMIT" \
         "$SOURCES/android-ifaddrs-$ANDROID_IFADDRS_COMMIT.tar.gz" "$ANDROID_IFADDRS_SHA256"
   fetch "$GH/eProsima/Fast-CDR/tar.gz/refs/tags/v$FASTCDR_VERSION" "$SOURCES/Fast-CDR-$FASTCDR_VERSION.tar.gz" \
         "$FASTCDR_SHA256"
   fetch "$GH/eProsima/memory/tar.gz/$FOONATHAN_MEMORY_COMMIT" "$SOURCES/memory-$FOONATHAN_MEMORY_COMMIT.tar.gz" \
         "$FOONATHAN_MEMORY_SHA256"
   fetch "https://github.com/openssl/openssl/releases/download/openssl-$OPENSSL_VERSION/openssl-$OPENSSL_VERSION.tar.gz" \
         "$SOURCES/openssl-$OPENSSL_VERSION.tar.gz" "$OPENSSL_SHA256"
fi

# ------------------------------------------------------------------------------------------------ image
case "$PLATFORM" in
   linux-musl-x86_64) FLAVOR=alpine; DOCKER_PLATFORM=linux/amd64; IMAGE_NAME=jros2-natives-alpine-x86_64 ;;
   linux-musl-arm64) FLAVOR=alpine; DOCKER_PLATFORM=linux/arm64; IMAGE_NAME=jros2-natives-alpine-arm64 ;;
   *) FLAVOR=focal; DOCKER_PLATFORM=linux/amd64; IMAGE_NAME=jros2-natives-focal ;;
esac
DOCKERFILE="$SCRIPT_DIR/Dockerfile.$FLAVOR"
IMAGE_TAG=$( (cat "$DOCKERFILE"; echo "$CMAKE_VERSION $CMAKE_LINUX_X86_64_SHA256") | sha256sum | cut -c1-12)
IMAGE="$IMAGE_NAME:$IMAGE_TAG"
if ! $DOCKER image inspect "$IMAGE" > /dev/null 2>&1; then
   echo "[build-natives] building image $IMAGE ($DOCKER_PLATFORM)"
   if ! $DOCKER build --platform "$DOCKER_PLATFORM" -t "$IMAGE" \
        --build-arg "CMAKE_VERSION=$CMAKE_VERSION" --build-arg "CMAKE_SHA256=$CMAKE_LINUX_X86_64_SHA256" \
        - < "$DOCKERFILE"; then
      echo "image build failed; for a foreign architecture register qemu first, e.g." >&2
      echo "  docker run --privileged --rm tonistiigi/binfmt --install arm64" >&2
      exit 1
   fi
fi

# The container runs a snapshot of these scripts, so editing them during a long build cannot break it.
rm -rf "$WORK/.scripts"
mkdir -p "$WORK/.scripts"
cp "$SCRIPT_DIR"/*.sh "$WORK/.scripts/"
DOCKER_ARGS=(--rm --platform "$DOCKER_PLATFORM" --user "$(id -u):$(id -g)" --env-file "$VERSIONS_FILE"
             -e "PLATFORM=$PLATFORM" -e "JOBS=$JOBS" -e "SECURITY=$SECURITY" -e HOME=/work/home
             -e "KEEP_BUILD_TREES=${KEEP_BUILD_TREES:-0}" -e "JNI_ALLOW_MISSING=${JNI_ALLOW_MISSING:-}"
             -v "$REPO:/repo:ro" -v "$WORK/.scripts:/scripts:ro" -v "$SOURCES:/sources:ro"
             -v "$WORK:/work" -v "$OUT:/out" -w /work)

# ------------------------------------------------------------------------------------------------ Android NDK
if [[ "$PLATFORM" == android-* ]]; then
   NDK=${JROS2_ANDROID_NDK:-$CACHE/android-ndk-$NDK_RELEASE}
   if [ ! -f "$NDK/source.properties" ] && [ -z "${JROS2_ANDROID_NDK:-}" ]; then
      zip="$CACHE/android-ndk-$NDK_RELEASE-linux.zip"
      fetch "https://dl.google.com/android/repository/android-ndk-$NDK_RELEASE-linux.zip" "$zip" "$NDK_SHA256"
      echo "$NDK_SHA1  $zip" | sha1sum -c --quiet -
      rm -rf "$CACHE/android-ndk-$NDK_RELEASE.part"
      mkdir -p "$CACHE/android-ndk-$NDK_RELEASE.part"
      unzip -q "$zip" -d "$CACHE/android-ndk-$NDK_RELEASE.part"
      mv "$CACHE/android-ndk-$NDK_RELEASE.part/android-ndk-$NDK_RELEASE" "$NDK"
      rm -rf "$CACHE/android-ndk-$NDK_RELEASE.part" "$zip"
   fi
   NDK=$(cd "$NDK" && pwd -P)
   rev=$(sed -n 's/^Pkg.Revision *= *//p' "$NDK/source.properties" | tr -d '\r')
   [ "$rev" = "$NDK_REVISION" ] || { echo "NDK at $NDK is $rev, expected $NDK_REVISION ($NDK_RELEASE)" >&2; exit 1; }
   DOCKER_ARGS+=(-v "$NDK:/ndk:ro")
fi

# ------------------------------------------------------------------------------------------------ build
# Comment-only edits of the scripts do not invalidate the cache. The Fast-DDS patches that deps.sh applies count with
# their names (application order) and exact bytes.
DEPS_FINGERPRINT=$( (cat "$VERSIONS_FILE" "$DOCKERFILE" "$WORK/.scripts/env.sh" "$WORK/.scripts/deps.sh" \
                     | grep -v '^[[:space:]]*#'
                     for patch in "$REPO"/src/native/fastdds-patches/*.patch; do
                        if [ -f "$patch" ]; then echo "fastdds-patch ${patch##*/}"; cat "$patch"; fi
                     done
                     echo "$PLATFORM SECURITY=$SECURITY") | sha256sum | cut -c1-16)
DOCKER_ARGS+=(-e "DEPS_FINGERPRINT=$DEPS_FINGERPRINT")
CACHED=$(cat "$WORK/deps/.fingerprint" 2> /dev/null || true)
DEPS_SECONDS=0
if [ "$MODE" = full ]; then
   if [ "$REBUILD_DEPS" = 1 ] || [ "$CACHED" != "$DEPS_FINGERPRINT" ]; then
      echo "[build-natives] building dependencies (cache ${CACHED:-empty}, want $DEPS_FINGERPRINT)"
      t0=$(date +%s)
      $DOCKER run "${DOCKER_ARGS[@]}" "$IMAGE" bash /scripts/deps.sh
      DEPS_SECONDS=$(($(date +%s) - t0))
   else
      echo "[build-natives] dependency cache $DEPS_FINGERPRINT is up to date"
   fi
fi

rm -rf "$OUT/.staging-$PLATFORM"
t0=$(date +%s)
if ! $DOCKER run "${DOCKER_ARGS[@]}" "$IMAGE" bash /scripts/jni.sh; then
   rm -rf "$WORK/failed-stage"
   kept=""
   if [ -d "$OUT/.staging-$PLATFORM" ]; then
      mv "$OUT/.staging-$PLATFORM" "$WORK/failed-stage"
      kept="rejected output kept in $WORK/failed-stage; "
   fi
   echo "[build-natives] $PLATFORM FAILED (${kept}log $LOG)" >&2
   exit 1
fi
JNI_SECONDS=$(($(date +%s) - t0))
rm -rf "${OUT:?}/$PLATFORM"
mv "$OUT/.staging-$PLATFORM" "$OUT/$PLATFORM"

TOTAL=$(($(date +%s) - START))
{
   echo "platform=$PLATFORM mode=$MODE security=$SECURITY date=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
   echo "image=$IMAGE deps_fingerprint=$DEPS_FINGERPRINT repo=$REPO"
   echo "seconds: deps=$DEPS_SECONDS jni+stage+verify=$JNI_SECONDS total=$TOTAL"
   [ -f "$WORK/logs/deps-times.txt" ] && echo "dependency steps (s): $(tr '\n' ' ' < "$WORK/logs/deps-times.txt")"
   for f in "$OUT/$PLATFORM"/*; do printf '  %10d %s\n' "$(wc -c < "$f")" "${f##*/}"; done
} | tee "$WORK/last-build.txt"
echo "[build-natives] $PLATFORM OK -> $OUT/$PLATFORM (${TOTAL}s)"
exit 0
}
