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

# Builds the native dependencies of one platform into the dependency cache /work/deps:
#   OpenSSL (static, PIC; only when SECURITY=ON), foonathan memory (static), Fast-CDR and Fast-DDS (shared).
# Runs inside the build container (see build-natives.sh). Changing this file invalidates the dependency caches.
#
# Container layout: /sources (checksummed tarballs, read-only), /scripts (this directory, read-only),
# /work (per-platform work dir): src/ build/ logs/ deps/{install,openssl}/ deps/.fingerprint

set -euo pipefail

: "${PLATFORM:?}" "${JOBS:?}" "${SECURITY:?}" "${DEPS_FINGERPRINT:?}"
# shellcheck source=env.sh
. /scripts/env.sh

S=/work/src
B=/work/build
D=/work/deps
I=$D/install
LOGS=/work/logs
# OpenSSL is configured for a neutral prefix and installed with DESTDIR, so no build path is compiled into it.
# OPENSSLDIR is jros2-specific on purpose: the private OpenSSL never reads the system's openssl.cnf (which belongs to
# the system OpenSSL version and may activate providers this build does not have). OPENSSL_CONF still works.
OSSL_PREFIX=/opt/jros2-openssl
O=$D/openssl$OSSL_PREFIX

log() { echo "[deps $PLATFORM] $*"; }
fail_with_log() { echo "[deps $PLATFORM] FAILED, last lines of $1:" >&2; tail -n "${2:-60}" "$1" >&2; exit 1; }
timed() { # name command...
   local name=$1 t0=$SECONDS
   shift
   "$@"
   log "[time] $name: $((SECONDS - t0)) s"
   echo "$name $((SECONDS - t0))" >> "$LOGS/deps-times.txt"
}

rm -rf "$S" "$B" "$D"
mkdir -p "$S" "$B" "$I" "$LOGS"
: > "$LOGS/deps-times.txt"
export SOURCE_DATE_EPOCH

extract_sources() {
   tar -xzf "/sources/memory-$FOONATHAN_MEMORY_COMMIT.tar.gz" -C "$S"
   mv "$S/memory-$FOONATHAN_MEMORY_COMMIT" "$S/memory-vendor-$FOONATHAN_VENDOR_VERSION"
   tar -xzf "/sources/Fast-CDR-$FASTCDR_VERSION.tar.gz" -C "$S"
   tar -xzf "/sources/Fast-DDS-$FASTDDS_COMMIT.tar.gz" -C "$S"
   mv "$S/Fast-DDS-$FASTDDS_COMMIT" "$S/Fast-DDS-$FASTDDS_VERSION"
   local tp=$S/Fast-DDS-$FASTDDS_VERSION/thirdparty
   tar -xzf "/sources/asio-$ASIO_COMMIT.tar.gz" -C "$tp/asio" --strip-components=1
   tar -xzf "/sources/tinyxml2-$TINYXML2_COMMIT.tar.gz" -C "$tp/tinyxml2" --strip-components=1
   tar -xzf "/sources/android-ifaddrs-$ANDROID_IFADDRS_COMMIT.tar.gz" -C "$tp/android-ifaddrs" --strip-components=1
   test -f "$tp/asio/asio/include/asio.hpp"
   test -f "$tp/tinyxml2/tinyxml2.cpp"
   apply_fastdds_patches
}

# jros2's patches of Fast-DDS (src/native/fastdds-patches/*.patch, in name order; scripts/cppbuild.bash applies the same
# set). They remove heap allocations from Fast-DDS's steady-state publish/receive paths and fix races on writers and readers
# of this process (0008, 0009); each file starts with a description of its change. Exact context (no fuzz): a patch that no longer matches the
# pinned Fast-DDS fails the build. Their names and contents are part of the dependency fingerprint (build-natives.sh).
apply_fastdds_patches() {
   local patch n=0
   for patch in /repo/src/native/fastdds-patches/*.patch; do
      [ -f "$patch" ] || continue
      log "applying Fast-DDS patch ${patch##*/}"
      patch -p1 --forward --batch --quiet -F 0 -d "$S/Fast-DDS-$FASTDDS_VERSION" < "$patch"
      n=$((n + 1))
   done
   log "applied $n Fast-DDS patch(es)"
}

build_openssl() {
   tar -xzf "/sources/openssl-$OPENSSL_VERSION.tar.gz" -C "$B"
   (
      cd "$B/openssl-$OPENSSL_VERSION"
      if [ -n "$OSSL_CC" ]; then export CC=$OSSL_CC; fi
      # no-shared/no-module: only libcrypto.a/libssl.a (legacy provider built in). Keep ENGINE (pkcs11: keys).
      # -ffunction-sections/-fdata-sections + --gc-sections on libfastdds drop the unused parts of libcrypto.
      # shellcheck disable=SC2086  # EXTRA_CFLAGS is a list of flags
      ./Configure "$OSSL_TARGET" no-shared no-module no-tests no-docs no-apps \
         -fPIC -ffunction-sections -fdata-sections $EXTRA_CFLAGS \
         "--prefix=$OSSL_PREFIX" "--openssldir=$OSSL_PREFIX/ssl" --libdir=lib "${OSSL_ARGS_EXTRA[@]}"
      make -j"$JOBS" build_libs
      make install_dev DESTDIR="$D/openssl"
   ) > "$LOGS/openssl.log" 2>&1 || fail_with_log "$LOGS/openssl.log"
   test -f "$O/lib/libcrypto.a" && test -f "$O/lib/libssl.a"
   rm -rf "$B/openssl-$OPENSSL_VERSION"
}

COMMON=(-G Ninja -DCMAKE_BUILD_TYPE=Release "-DCMAKE_INSTALL_PREFIX=$I" "-DCMAKE_PREFIX_PATH=$I" -DBUILD_TESTING=OFF
        "-DCMAKE_C_FLAGS=$EXTRA_CFLAGS" "-DCMAKE_CXX_FLAGS=$EXTRA_CXXFLAGS"
        "-DCMAKE_SHARED_LINKER_FLAGS=$SHARED_LDFLAGS"
        "${TOOLCHAIN_ARGS[@]}")
# Only when needed: an explicit value replaces the toolchain default (the NDK's is "-latomic -lm ...").
if [ -n "$STD_LIBS" ]; then
   COMMON+=("-DCMAKE_C_STANDARD_LIBRARIES=$STD_LIBS" "-DCMAKE_CXX_STANDARD_LIBRARIES=$STD_LIBS")
fi

cmake_build() { # name source-dir extra-args...
   local name=$1 src=$2
   shift 2
   cmake -S "$src" -B "$B/$name" "${COMMON[@]}" "$@" > "$LOGS/$name.log" 2>&1 || fail_with_log "$LOGS/$name.log"
   cmake --build "$B/$name" --target install -j "$JOBS" >> "$LOGS/$name.log" 2>&1 || {
      grep -E "error|Error" "$LOGS/$name.log" | head -40 >&2 || true
      fail_with_log "$LOGS/$name.log" 40
   }
}

build_foonathan() {
   # The library options foonathan_memory_vendor 1.4.1 passes to its ExternalProject (static, PIC, no tests/examples).
   # The vendor also builds the nodesize_dbg tool by default; it is not needed (node sizes are computed at configure
   # time by compiling, which also works when cross-compiling) and not shipped, so it is skipped.
   cmake_build foonathan "$S/memory-vendor-$FOONATHAN_VENDOR_VERSION" -Wno-dev \
      -DFOONATHAN_MEMORY_BUILD_EXAMPLES=OFF -DFOONATHAN_MEMORY_BUILD_TESTS=OFF -DFOONATHAN_MEMORY_BUILD_TOOLS=OFF \
      -DBUILD_SHARED_LIBS=OFF -DCMAKE_POSITION_INDEPENDENT_CODE=ON
}

build_fastcdr() {
   cmake_build fastcdr "$S/Fast-CDR-$FASTCDR_VERSION"
}

build_fastdds() {
   local args=(
      "-Dfastcdr_DIR=$I/lib/cmake/fastcdr" "-Dfoonathan_memory_DIR=$I/lib/foonathan_memory/cmake"
      # Use the checksummed asio/tinyxml2 extracted into thirdparty/ (no 'git submodule update').
      -DTHIRDPARTY_TinyXML2=FORCE -DTHIRDPARTY_Asio=FORCE -DTHIRDPARTY_UPDATE=OFF
      -DCOMPILE_TOOLS=OFF -DCOMPILE_EXAMPLES=OFF -DINSTALL_TOOLS=OFF -DINSTALL_EXAMPLES=OFF
      -DNO_TLS=ON "-DSECURITY=$SECURITY"
   )
   local shld="$SHARED_LDFLAGS"
   if [ "$SECURITY" = ON ]; then
      # Explicit paths: FindOpenSSL honors them, while the cross/NDK toolchain files would otherwise re-root
      # OPENSSL_ROOT_DIR into the sysroot and not find the static libraries.
      args+=("-DOPENSSL_ROOT_DIR=$O" -DOPENSSL_USE_STATIC_LIBS=TRUE "-DOPENSSL_INCLUDE_DIR=$O/include"
             "-DOPENSSL_CRYPTO_LIBRARY=$O/lib/libcrypto.a" "-DOPENSSL_SSL_LIBRARY=$O/lib/libssl.a")
      # Keep the statically linked OpenSSL private to libfastdds: no exported SSL_*/EVP_*/... symbols that could clash
      # with another OpenSSL in the same process (e.g. the JDK's or the system's).
      shld="$shld -Wl,--exclude-libs,libssl.a:libcrypto.a"
   fi
   if [ "$KIND" != android ]; then
      # libfastdds finds libfastcdr.so.2 next to itself. Required on musl, whose loader does not match an already
      # System.load()ed library by SONAME.
      args+=("-DCMAKE_INSTALL_RPATH=\$ORIGIN")
   fi
   cmake_build fastdds "$S/Fast-DDS-$FASTDDS_VERSION" "${args[@]}" "-DCMAKE_SHARED_LINKER_FLAGS=$shld"
   grep -E "Found OpenSSL|SECURITY|Build type" "$LOGS/fastdds.log" | head -5 || true
}

log "toolchain: $(${JNI_CXX} --version | head -1)"
log "cmake: $(cmake --version | head -1)"
timed sources extract_sources
if [ "$SECURITY" = ON ]; then timed openssl build_openssl; fi
timed foonathan build_foonathan
timed fastcdr build_fastcdr
timed fastdds build_fastdds

# Only build products needed by the JNI build and the staging step are kept; the build trees are dropped unless asked.
if [ "${KEEP_BUILD_TREES:-0}" != 1 ]; then rm -rf "$B" "$S"; fi
echo "$DEPS_FINGERPRINT" > "$D/.fingerprint"
log "dependency cache ready: $D ($(du -sh "$D" | cut -f1))"
