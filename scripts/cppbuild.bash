#!/usr/bin/env bash
#  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
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

# Builds the jros2 natives directly on the host (no Docker) and installs them into the repository:
#   src/main/resources/fastddsjava/native/<platform>/   (Linux, macOS, Windows; with a natives.list load-order manifest)
#   android/src/main/jniLibs/<abi>/                     (Android)
# The CI builds macOS and Windows with this script. Linux and Android release natives are built reproducibly with
# scripts/docker/build-natives.sh (Ubuntu 20.04 / glibc 2.31 baseline, Alpine, NDK r30); this script still supports
# them for local development (the result then needs the host's glibc).
# Windows: run from a bash shell inside an MSVC developer environment (e.g. scripts/launchBashWindows.bat).
#
# Target selection (environment; default = the host platform):
#   ANDROID_COMPILE=1      Android; needs ANDROID_NDK (r28+ recommended: 16 KB pages), ANDROID_ABI (arm64-v8a,
#                          armeabi-v7a, x86_64), ANDROID_API_LEVEL (default 26)
#   LINUX_COMPILE_ARM64=1 / LINUX_COMPILE_ARMHF=1 / LINUX_COMPILE_RISCV64=1   Linux cross builds (gcc cross toolchains)
#   MAC_COMPILE_ARM64=1 / MAC_COMPILE_X86_64=1                                 macOS (either arch from either host)
#   WINDOWS_COMPILE_ARM64=1                                                    Windows ARM64 (amd64_arm64 MSVC env)
# Other options:
#   JROS2_SECURITY=ON|OFF  DDS-Security (SROS2) with a static, private OpenSSL. Default: ON for Linux and Android,
#                          OFF for macOS and Windows, where it is an opt-in that the jros2 CI does not exercise.
#   JOBS                   parallel build jobs (default: number of CPUs)
#   OPENSSL_PERL           (Windows) a native Win32 perl for OpenSSL's Configure (default: Strawberry Perl)

# shellcheck disable=SC2054,SC2153  # -Wl,a,b arguments are single array elements; versions come from native-versions.env
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"
# shellcheck source=native-versions.env
. "$SCRIPT_DIR/native-versions.env"

ANDROID_COMPILE=${ANDROID_COMPILE:-0}
LINUX_COMPILE_ARM64=${LINUX_COMPILE_ARM64:-0}
LINUX_COMPILE_ARMHF=${LINUX_COMPILE_ARMHF:-0}
LINUX_COMPILE_RISCV64=${LINUX_COMPILE_RISCV64:-0}
MAC_COMPILE_ARM64=${MAC_COMPILE_ARM64:-0}
MAC_COMPILE_X86_64=${MAC_COMPILE_X86_64:-0}
WINDOWS_COMPILE_ARM64=${WINDOWS_COMPILE_ARM64:-0}
JOBS=${JOBS:-$(nproc 2> /dev/null || sysctl -n hw.logicalcpu 2> /dev/null || echo 4)}

# ------------------------------------------------------------------------------------------------ target platform
KIND=""; PLATFORM=""; CROSS=""; TOOLCHAIN_FILE=""; MAC_ARCH=""
if [ "$ANDROID_COMPILE" = 1 ]; then
   KIND=android
   : "${ANDROID_NDK:?Error: ANDROID_NDK environment variable is not set}"
   ANDROID_ABI=${ANDROID_ABI:-arm64-v8a}
   ANDROID_API_LEVEL=${ANDROID_API_LEVEL:-$ANDROID_API}
   PLATFORM=android-$ANDROID_ABI
elif [ "$LINUX_COMPILE_ARM64" = 1 ]; then
   KIND=linux; PLATFORM=linux-arm64; CROSS=aarch64-linux-gnu-; TOOLCHAIN_FILE=$REPO_ROOT/linux-aarch64-toolchain.cmake
elif [ "$LINUX_COMPILE_ARMHF" = 1 ]; then
   KIND=linux; PLATFORM=linux-armhf; CROSS=arm-linux-gnueabihf-; TOOLCHAIN_FILE=$REPO_ROOT/linux-armhf-toolchain.cmake
elif [ "$LINUX_COMPILE_RISCV64" = 1 ]; then
   KIND=linux; PLATFORM=linux-riscv64; CROSS=riscv64-linux-gnu-; TOOLCHAIN_FILE=$REPO_ROOT/linux-riscv64-toolchain.cmake
elif [ "$MAC_COMPILE_ARM64" = 1 ]; then
   KIND=macos; PLATFORM=macos-arm64; MAC_ARCH=arm64
elif [ "$MAC_COMPILE_X86_64" = 1 ]; then
   KIND=macos; PLATFORM=macos-x86_64; MAC_ARCH=x86_64
elif [[ "${OSTYPE:-}" == msys || "${OSTYPE:-}" == cygwin || "${OS:-}" == Windows_NT ]]; then
   KIND=windows
   PLATFORM=windows-x86_64
   [ "$WINDOWS_COMPILE_ARM64" = 1 ] && PLATFORM=windows-arm64
elif [[ "${OSTYPE:-}" == darwin* ]]; then
   KIND=macos
   MAC_ARCH=$(uname -m)
   PLATFORM=macos-$MAC_ARCH
else
   KIND=linux
   case "$(uname -m)" in
      x86_64) PLATFORM=linux-x86_64 ;;
      aarch64) PLATFORM=linux-arm64 ;;
      armv7l) PLATFORM=linux-armhf ;;
      riscv64) PLATFORM=linux-riscv64 ;;
      *) echo "Error: unsupported Linux host architecture $(uname -m)" >&2; exit 1 ;;
   esac
fi
case "$KIND" in
   linux | android) SECURITY=${JROS2_SECURITY:-ON} ;;
   *) SECURITY=${JROS2_SECURITY:-OFF} ;;
esac
case "$SECURITY" in ON | OFF) ;; *) echo "Error: JROS2_SECURITY must be ON or OFF" >&2; exit 1 ;; esac
echo "Building jros2 natives for $PLATFORM (DDS-Security $SECURITY, $JOBS jobs)"

case "$KIND" in
   android) DEST="$REPO_ROOT/android/src/main/jniLibs/$ANDROID_ABI" ;;
   *) DEST="$REPO_ROOT/src/main/resources/fastddsjava/native/$PLATFORM" ;;
esac

to_win_path() {
   if command -v cygpath > /dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi
}
sha256_of() {
   if command -v sha256sum > /dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

# ------------------------------------------------------------------------------------------------ sources
rm -rf cppbuild/us
mkdir -p cppbuild
cd cppbuild
WORK=$(pwd)
INSTALL_DIR=$WORK/install
SRC=$WORK/src
rm -rf "$SRC" "$WORK/build" "$INSTALL_DIR" "$WORK/javainstall" "$WORK/openssl"
mkdir -p "$SRC" "$WORK/build" "$INSTALL_DIR" "$WORK/javainstall" "$WORK/downloads"

fetch() { # url file sha256
   local url=$1 file=$WORK/downloads/$2 sha=$3
   if [ ! -f "$file" ] || [ "$(sha256_of "$file")" != "$sha" ]; then
      curl -fsSL --retry 5 --retry-delay 3 -o "$file" "$url"
   fi
   if [ "$(sha256_of "$file")" != "$sha" ]; then
      echo "Error: checksum mismatch for $url" >&2
      rm -f "$file"
      exit 1
   fi
   echo "$file"
}
GH=https://codeload.github.com
tar -xzf "$(fetch "$GH/eProsima/memory/tar.gz/$FOONATHAN_MEMORY_COMMIT" "memory-$FOONATHAN_MEMORY_COMMIT.tar.gz" "$FOONATHAN_MEMORY_SHA256")" -C "$SRC"
mv "$SRC/memory-$FOONATHAN_MEMORY_COMMIT" "$SRC/memory-vendor-$FOONATHAN_VENDOR_VERSION"
tar -xzf "$(fetch "$GH/eProsima/Fast-CDR/tar.gz/refs/tags/v$FASTCDR_VERSION" "Fast-CDR-$FASTCDR_VERSION.tar.gz" "$FASTCDR_SHA256")" -C "$SRC"
tar -xzf "$(fetch "$GH/eProsima/Fast-DDS/tar.gz/$FASTDDS_COMMIT" "Fast-DDS-$FASTDDS_COMMIT.tar.gz" "$FASTDDS_SHA256")" -C "$SRC"
mv "$SRC/Fast-DDS-$FASTDDS_COMMIT" "$SRC/Fast-DDS-$FASTDDS_VERSION"
FASTDDS_SRC=$SRC/Fast-DDS-$FASTDDS_VERSION
tar -xzf "$(fetch "$GH/chriskohlhoff/asio/tar.gz/$ASIO_COMMIT" "asio-$ASIO_COMMIT.tar.gz" "$ASIO_SHA256")" \
   -C "$FASTDDS_SRC/thirdparty/asio" --strip-components=1
tar -xzf "$(fetch "$GH/leethomason/tinyxml2/tar.gz/$TINYXML2_COMMIT" "tinyxml2-$TINYXML2_COMMIT.tar.gz" "$TINYXML2_SHA256")" \
   -C "$FASTDDS_SRC/thirdparty/tinyxml2" --strip-components=1
tar -xzf "$(fetch "$GH/michalsrb/android-ifaddrs/tar.gz/$ANDROID_IFADDRS_COMMIT" "android-ifaddrs-$ANDROID_IFADDRS_COMMIT.tar.gz" "$ANDROID_IFADDRS_SHA256")" \
   -C "$FASTDDS_SRC/thirdparty/android-ifaddrs" --strip-components=1
# jros2's patches of Fast-DDS (src/native/fastdds-patches/*.patch, in name order; the same set as
# scripts/docker/deps.sh): they remove heap allocations from Fast-DDS's steady-state publish/receive paths and fix a
# races on writers and readers of this process (0008, 0009) and a reader history lookup (0010). Exact context (no fuzz), so a patch that no longer matches the pinned Fast-DDS
# fails the build. GNU patch (Linux, Git for Windows) and BSD patch (macOS) both accept these options.
for patch in "$REPO_ROOT"/src/native/fastdds-patches/*.patch; do
   [ -f "$patch" ] || continue
   echo "Applying Fast-DDS patch ${patch##*/}"
   patch -p1 --forward --batch --quiet -F 0 -d "$FASTDDS_SRC" < "$patch"
done
if [ "$SECURITY" = ON ]; then
   tar -xzf "$(fetch "https://github.com/openssl/openssl/releases/download/openssl-$OPENSSL_VERSION/openssl-$OPENSSL_VERSION.tar.gz" \
      "openssl-$OPENSSL_VERSION.tar.gz" "$OPENSSL_SHA256")" -C "$WORK/build"
fi

# ------------------------------------------------------------------------------------------------ toolchain settings
# CMAKE_INSTALL_LIBDIR=lib: Fast-CDR and foonathan memory use GNUInstallDirs, which picks lib64 on Fedora/openSUSE
# hosts, while Fast-DDS always installs to lib and the JNI link and staging below read install/lib.
CMAKE_ARGS=(-DCMAKE_BUILD_TYPE=Release "-DCMAKE_INSTALL_PREFIX=$INSTALL_DIR" "-DCMAKE_PREFIX_PATH=$INSTALL_DIR"
            -DCMAKE_INSTALL_LIBDIR=lib -DBUILD_TESTING=OFF)
if [ "$KIND" != windows ] && command -v ninja > /dev/null 2>&1; then CMAKE_ARGS+=(-G Ninja); fi
SHARED_LDFLAGS=""
CXX_FLAGS=""
STD_LIBS=""
OSSL_TARGET=""
OSSL_ARGS=()
STRIP="strip"
case "$KIND" in
   linux)
      [ -n "$TOOLCHAIN_FILE" ] && CMAKE_ARGS+=("-DCMAKE_TOOLCHAIN_FILE=$TOOLCHAIN_FILE")
      STRIP=${CROSS}strip
      SHARED_LDFLAGS="-Wl,-z,noexecstack -Wl,--gc-sections -Wl,--enable-new-dtags"
      case "$PLATFORM" in
         linux-x86_64) OSSL_TARGET=linux-x86_64 ;;
         linux-arm64) OSSL_TARGET=linux-aarch64 ;;
         linux-armhf) OSSL_TARGET=linux-armv4 ;;
         linux-riscv64)
            OSSL_TARGET=linux64-riscv64
            # gcc does not inline sub-word atomics on RISC-V and Fast-DDS does not link libatomic: link it statically
            # (libatomic.so.1 is missing from common riscv64 JRE images). STANDARD_LIBRARIES go after the objects; the
            # libatomic.a-only search directory makes any other '-latomic' (OpenSSL adds one) static as well.
            mkdir -p "$WORK/static-libatomic"
            cp "$("${CROSS}gcc" -print-file-name=libatomic.a)" "$WORK/static-libatomic/libatomic.a"
            STD_LIBS="-Wl,--push-state,-Bstatic,-latomic,--pop-state"
            SHARED_LDFLAGS="$SHARED_LDFLAGS -L$WORK/static-libatomic -Wl,--exclude-libs,libatomic.a" ;;
      esac
      [ -n "$CROSS" ] && OSSL_ARGS+=("--cross-compile-prefix=$CROSS")
      ;;
   android)
      ANDROID_BIN=$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
      STRIP=$ANDROID_BIN/llvm-strip
      CMAKE_ARGS+=("-DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake" "-DANDROID_ABI=$ANDROID_ABI"
                   "-DANDROID_PLATFORM=android-$ANDROID_API_LEVEL" -DANDROID_STL=c++_shared
                   -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON)
      # clang >= 19 flags offsetof on non-standard-layout types in foonathan memory, which builds with -Werror.
      CXX_FLAGS="-Wno-error=gnu-offsetof-extensions"
      SHARED_LDFLAGS="-Wl,-z,noexecstack"
      case "$ANDROID_ABI" in
         arm64-v8a) OSSL_TARGET=android-arm64; NDK_TRIPLE=aarch64-linux-android; CLANG_TRIPLE=aarch64-linux-android
                    SHARED_LDFLAGS="$SHARED_LDFLAGS -Wl,-z,max-page-size=16384" ;;
         armeabi-v7a) OSSL_TARGET=android-arm; NDK_TRIPLE=arm-linux-androideabi; CLANG_TRIPLE=armv7a-linux-androideabi ;;
         x86_64) OSSL_TARGET=android-x86_64; NDK_TRIPLE=x86_64-linux-android; CLANG_TRIPLE=x86_64-linux-android
                 SHARED_LDFLAGS="$SHARED_LDFLAGS -Wl,-z,max-page-size=16384" ;;
         x86) OSSL_TARGET=android-x86; NDK_TRIPLE=i686-linux-android; CLANG_TRIPLE=i686-linux-android ;;
         *) echo "Error: unsupported ANDROID_ABI $ANDROID_ABI" >&2; exit 1 ;;
      esac
      OSSL_ARGS+=("-D__ANDROID_API__=$ANDROID_API_LEVEL")
      ;;
   macos)
      # Deployment target macOS 12.0 (Monterey), because:
      #  - it is older than every macOS release Apple still patches, so Intel Macs that stopped at 12 or 13 are
      #    covered too;
      #  - it is above the C++17 runtime minimums of Fast-DDS (aligned new needs 10.14, std::filesystem 10.15);
      #  - the Xcode versions on the macos-14+ runners support it;
      #  - it replaces the accidental minos 14.0 (the runner SDK's default), which stopped macOS 12 and 13 from
      #    loading the natives.
      export MACOSX_DEPLOYMENT_TARGET=$MACOS_DEPLOYMENT_TARGET
      CMAKE_ARGS+=("-DCMAKE_OSX_ARCHITECTURES=$MAC_ARCH" "-DCMAKE_OSX_DEPLOYMENT_TARGET=$MACOS_DEPLOYMENT_TARGET")
      OSSL_TARGET=darwin64-$MAC_ARCH-cc
      ;;
   windows)
      [ "$WINDOWS_COMPILE_ARM64" = 1 ] && CMAKE_ARGS+=(-A ARM64)
      if [ "$WINDOWS_COMPILE_ARM64" = 1 ]; then OSSL_TARGET=VC-WIN64-ARM; else OSSL_TARGET=VC-WIN64A; fi
      ;;
esac
[ -n "$CXX_FLAGS" ] && CMAKE_ARGS+=("-DCMAKE_CXX_FLAGS=$CXX_FLAGS")
[ -n "$SHARED_LDFLAGS" ] && CMAKE_ARGS+=("-DCMAKE_SHARED_LINKER_FLAGS=$SHARED_LDFLAGS")
[ -n "$STD_LIBS" ] && CMAKE_ARGS+=("-DCMAKE_C_STANDARD_LIBRARIES=$STD_LIBS" "-DCMAKE_CXX_STANDARD_LIBRARIES=$STD_LIBS")

# ------------------------------------------------------------------------------------------------ OpenSSL (static)
OSSL_PREFIX=/opt/jros2-openssl
OPENSSL_DIR=""
if [ "$SECURITY" = ON ]; then
   pushd "$WORK/build/openssl-$OPENSSL_VERSION" > /dev/null
   if [ "$KIND" = windows ]; then
      # OpenSSL's VC-* targets need a native Win32 perl (Git Bash's msys perl is rejected) and nmake.
      OPENSSL_PERL=${OPENSSL_PERL:-/c/Strawberry/perl/bin/perl.exe}
      [ -x "$OPENSSL_PERL" ] || OPENSSL_PERL=perl
      OPENSSL_DIR=$WORK/openssl
      OSSL_WIN_ARGS=(no-shared no-module no-tests no-docs no-apps
                     "--prefix=$(to_win_path "$OPENSSL_DIR")" "--openssldir=$(to_win_path "$OPENSSL_DIR/ssl")")
      # The x64 assembly needs NASM; without it (or for ARM64) use the portable C implementation.
      if [ "$WINDOWS_COMPILE_ARM64" = 1 ] || ! command -v nasm > /dev/null 2>&1; then OSSL_WIN_ARGS+=(no-asm); fi
      MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' "$OPENSSL_PERL" Configure "$OSSL_TARGET" "${OSSL_WIN_ARGS[@]}"
      MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' nmake -nologo build_libs
      MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' nmake -nologo install_dev
      test -f "$OPENSSL_DIR/lib/libcrypto.lib" && test -f "$OPENSSL_DIR/lib/libssl.lib"
   else
      OSSL_CFLAGS=(-fPIC -ffunction-sections -fdata-sections)
      # ld64 has no --exclude-libs: hide OpenSSL's C symbols at compile time (two-level namespaces keep the rest
      # bound to libfastdds).
      [ "$KIND" = macos ] && OSSL_CFLAGS+=(-fvisibility=hidden "-mmacosx-version-min=$MACOS_DEPLOYMENT_TARGET")
      (
         if [ "$KIND" = android ]; then
            export ANDROID_NDK_ROOT=$ANDROID_NDK PATH="$ANDROID_BIN:$PATH" CC=clang
         fi
         # OSSL_ARGS is empty on macOS: the ${a[@]+...} form keeps macOS' bash 3.2 from failing under set -u.
         ./Configure "$OSSL_TARGET" no-shared no-module no-tests no-docs no-apps "${OSSL_CFLAGS[@]}" \
            "--prefix=$OSSL_PREFIX" "--openssldir=$OSSL_PREFIX/ssl" --libdir=lib ${OSSL_ARGS[@]+"${OSSL_ARGS[@]}"}
         make -j"$JOBS" build_libs
         make install_dev DESTDIR="$WORK/openssl"
      )
      OPENSSL_DIR=$WORK/openssl$OSSL_PREFIX
      test -f "$OPENSSL_DIR/lib/libcrypto.a" && test -f "$OPENSSL_DIR/lib/libssl.a"
   fi
   popd > /dev/null
fi

# ------------------------------------------------------------------------------------------------ CMake projects
cmake_install() { # source-dir build-dir extra-args...
   local src=$1 bld=$2
   shift 2
   cmake -S "$src" -B "$bld" "${CMAKE_ARGS[@]}" "$@"
   cmake --build "$bld" --config Release --target install --parallel "$JOBS"
}

# foonathan_memory_vendor 1.4.1 builds eProsima/memory at tag vendor-1.4.1 with these library options (its default
# also builds the unshipped nodesize_dbg tool, which is skipped here).
cmake_install "$SRC/memory-vendor-$FOONATHAN_VENDOR_VERSION" "$WORK/build/foonathan" -Wno-dev \
   -DFOONATHAN_MEMORY_BUILD_EXAMPLES=OFF -DFOONATHAN_MEMORY_BUILD_TESTS=OFF -DFOONATHAN_MEMORY_BUILD_TOOLS=OFF \
   -DBUILD_SHARED_LIBS=OFF -DCMAKE_POSITION_INDEPENDENT_CODE=ON
cmake_install "$SRC/Fast-CDR-$FASTCDR_VERSION" "$WORK/build/fastcdr"

FOONATHAN_CMAKE_DIR=$INSTALL_DIR/lib/foonathan_memory/cmake
[ -d "$FOONATHAN_CMAKE_DIR" ] || FOONATHAN_CMAKE_DIR=$INSTALL_DIR/share/foonathan_memory/cmake
FASTDDS_ARGS=("-Dfastcdr_DIR=$INSTALL_DIR/lib/cmake/fastcdr" "-Dfoonathan_memory_DIR=$FOONATHAN_CMAKE_DIR"
              -DTHIRDPARTY_TinyXML2=FORCE -DTHIRDPARTY_Asio=FORCE -DTHIRDPARTY_UPDATE=OFF -DQNX=OFF -DNO_TLS=ON "-DSECURITY=$SECURITY"
              -DCOMPILE_TOOLS=OFF -DCOMPILE_EXAMPLES=OFF -DINSTALL_TOOLS=OFF -DINSTALL_EXAMPLES=OFF)
[ -d "$INSTALL_DIR/lib/cmake/fastcdr" ] || FASTDDS_ARGS[0]="-Dfastcdr_DIR=$INSTALL_DIR/share/fastcdr/cmake"
FASTDDS_LDFLAGS="$SHARED_LDFLAGS"
if [ "$SECURITY" = ON ]; then
   if [ "$KIND" = windows ]; then
      # FindOpenSSL ignores OPENSSL_*_LIBRARY under MSVC; LIB_EAY/SSL_EAY pin the static libraries instead.
      W_OSSL=$(to_win_path "$OPENSSL_DIR")
      FASTDDS_ARGS+=("-DOPENSSL_ROOT_DIR=$W_OSSL" -DOPENSSL_USE_STATIC_LIBS=TRUE -DOPENSSL_MSVC_STATIC_RT=FALSE
                     "-DOPENSSL_INCLUDE_DIR=$W_OSSL\\include"
                     "-DLIB_EAY_RELEASE=$W_OSSL\\lib\\libcrypto.lib" "-DSSL_EAY_RELEASE=$W_OSSL\\lib\\libssl.lib"
                     "-DLIB_EAY_DEBUG=$W_OSSL\\lib\\libcrypto.lib" "-DSSL_EAY_DEBUG=$W_OSSL\\lib\\libssl.lib")
   else
      FASTDDS_ARGS+=("-DOPENSSL_ROOT_DIR=$OPENSSL_DIR" -DOPENSSL_USE_STATIC_LIBS=TRUE
                     "-DOPENSSL_INCLUDE_DIR=$OPENSSL_DIR/include"
                     "-DOPENSSL_CRYPTO_LIBRARY=$OPENSSL_DIR/lib/libcrypto.a" "-DOPENSSL_SSL_LIBRARY=$OPENSSL_DIR/lib/libssl.a")
      # Keep the static OpenSSL private to libfastdds (GNU ld and lld).
      if [ "$KIND" = linux ] || [ "$KIND" = android ]; then
         FASTDDS_LDFLAGS="$FASTDDS_LDFLAGS -Wl,--exclude-libs,libssl.a:libcrypto.a"
      fi
   fi
fi
# Linux: libfastdds finds libfastcdr.so.2 next to itself (required by musl's loader, harmless on glibc).
# macOS: the same with @loader_path. Fast-DDS and Fast-CDR force CMAKE_INSTALL_RPATH to their install prefix on Apple
# (src/cpp/CMakeLists.txt), so the staging step below also replaces every LC_RPATH with @loader_path and checks it.
case "$KIND" in
   linux) FASTDDS_ARGS+=("-DCMAKE_INSTALL_RPATH=\$ORIGIN") ;;
   macos) FASTDDS_ARGS+=("-DCMAKE_INSTALL_RPATH=@loader_path") ;;
esac
[ -n "$FASTDDS_LDFLAGS" ] && FASTDDS_ARGS+=("-DCMAKE_SHARED_LINKER_FLAGS=$FASTDDS_LDFLAGS")
cmake_install "$FASTDDS_SRC" "$WORK/build/fastdds" "${FASTDDS_ARGS[@]}"

# ------------------------------------------------------------------------------------------------ JNI library
JNI_SRCS=()
for f in "$REPO_ROOT"/src/native/*.cpp; do JNI_SRCS+=("$f"); done
if [ -z "${JAVA_HOME:-}" ] && [ "$KIND" != android ]; then
   JAVA_BIN="$(command -v java)"
   JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$JAVA_BIN" 2> /dev/null || echo "$JAVA_BIN")")")"
fi
JNI_COMMON=(-std=c++17 -O3 -DNDEBUG -Wall -fPIC -fvisibility=hidden -fvisibility-inlines-hidden
            "-I$REPO_ROOT/src/native" "-I$INSTALL_DIR/include")
J=$WORK/javainstall
case "$KIND" in
   linux)
      JNI_LIBS=(-lfastdds -lfastcdr)
      # shellcheck disable=SC2206  # STD_LIBS is a list of flags; static libatomic must follow the objects
      [ -n "$STD_LIBS" ] && JNI_LIBS+=("-L$WORK/static-libatomic" $STD_LIBS -Wl,--exclude-libs,libatomic.a)
      "${CROSS}g++" "${JNI_COMMON[@]}" -shared -pthread "-I$JAVA_HOME/include" "-I$JAVA_HOME/include/linux" \
         "${JNI_SRCS[@]}" -o "$J/libjnifastddsjava.so" "-L$INSTALL_DIR/lib" -Wl,--no-undefined \
         -Wl,-soname,libjnifastddsjava.so -Wl,-z,noexecstack -Wl,--as-needed -Wl,--enable-new-dtags \
         "-Wl,-rpath,\$ORIGIN" "${JNI_LIBS[@]}"
      ;;
   android)
      # NDK jni.h (sysroot). No rpath: bionic finds the dependencies by SONAME in the APK lib dir.
      PAGE=()
      case "$ANDROID_ABI" in arm64-v8a | x86_64) PAGE=(-Wl,-z,max-page-size=16384) ;; esac
      "$ANDROID_BIN/${CLANG_TRIPLE}${ANDROID_API_LEVEL}-clang++" "${JNI_COMMON[@]}" -shared -fexceptions -frtti \
         "${JNI_SRCS[@]}" -o "$J/libjnifastddsjava.so" "-L$INSTALL_DIR/lib" -Wl,--no-undefined \
         -Wl,-soname,libjnifastddsjava.so -Wl,-z,noexecstack "${PAGE[@]}" -lfastdds -lfastcdr -llog -lc++_shared
      ;;
   macos)
      c++ "${JNI_COMMON[@]}" -dynamiclib -arch "$MAC_ARCH" "-mmacosx-version-min=$MACOS_DEPLOYMENT_TARGET" \
         "-I$JAVA_HOME/include" "-I$JAVA_HOME/include/darwin" "${JNI_SRCS[@]}" -o "$J/libjnifastddsjava.dylib" \
         "-L$INSTALL_DIR/lib" -install_name @rpath/libjnifastddsjava.dylib -Wl,-rpath,@loader_path -lfastdds -lfastcdr
      ;;
   windows)
      # Match the Fast-DDS MSVC build: MinGW cannot consume MSVC import libs.
      command -v cl.exe > /dev/null 2>&1 || { echo "Error: cl.exe not found; run inside an MSVC developer environment" >&2; exit 1; }
      if [ ! -f "$INSTALL_DIR/lib/fastdds-3.6.lib" ] || [ ! -f "$INSTALL_DIR/lib/fastcdr-2.3.lib" ]; then
         echo "Error: missing Fast-DDS/Fast-CDR MSVC import libraries under install/lib" >&2
         ls -la "$INSTALL_DIR/lib" >&2 || true
         exit 1
      fi
      # eProsima auto-link looks for libfastdds-3.6.lib / libfastcdr-2.3.lib unless DYN_LINK is set.
      cp -f "$INSTALL_DIR/lib/fastdds-3.6.lib" "$INSTALL_DIR/lib/libfastdds-3.6.lib"
      cp -f "$INSTALL_DIR/lib/fastcdr-2.3.lib" "$INSTALL_DIR/lib/libfastcdr-2.3.lib"
      mkdir -p "$J/obj"
      WIN_SRCS=()
      for f in "${JNI_SRCS[@]}"; do WIN_SRCS+=("$(to_win_path "$f")"); done
      # Dash-style MSVC flags: Git Bash rewrites leading-/ arguments as paths.
      MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' cl.exe -nologo -EHsc -std:c++17 -O2 -MD -LD -DNDEBUG \
         -DEPROSIMA_ALL_DYN_LINK \
         -I"$(to_win_path "$REPO_ROOT/src/native")" -I"$(to_win_path "$INSTALL_DIR/include")" \
         -I"$(to_win_path "$JAVA_HOME/include")" -I"$(to_win_path "$JAVA_HOME/include/win32")" \
         -Fo"$(to_win_path "$J/obj")\\" -Fe"$(to_win_path "$J/jnifastddsjava.dll")" "${WIN_SRCS[@]}" \
         -link -LIBPATH:"$(to_win_path "$INSTALL_DIR/lib")" fastdds-3.6.lib fastcdr-2.3.lib
      ;;
esac

# ------------------------------------------------------------------------------------------------ stage
mkdir -p "$DEST"
case "$KIND" in
   linux)
      # Ship each dependency under its SONAME (libfastcdr.so.2, libfastdds.so.3.6) + natives.list (load order).
      find "$DEST" -mindepth 1 -maxdepth 1 \( -name '*.so*' -o -name natives.list \) -exec rm -f {} +
      : > "$DEST/natives.list"
      for lib in fastcdr fastdds; do
         real=$(readlink -f "$INSTALL_DIR/lib/lib$lib.so")
         soname=$(readelf -dW "$real" | sed -n 's/.*(SONAME).*\[\(.*\)\].*/\1/p')
         cp "$real" "$DEST/$soname"
         echo "$soname" >> "$DEST/natives.list"
      done
      cp "$J/libjnifastddsjava.so" "$DEST/"
      echo libjnifastddsjava.so >> "$DEST/natives.list"
      for f in "$DEST"/*.so*; do "$STRIP" --strip-unneeded "$f"; done
      chmod 0644 "$DEST"/*
      # A host build links against the host's glibc/libstdc++, so the Ubuntu 20.04 baseline is only reported here;
      # release natives come from scripts/docker/build-natives.sh, which enforces it.
      EXPECT_SECURITY=$([ "$SECURITY" = ON ] && echo 1 || echo 0) \
         MAX_GLIBC=${MAX_GLIBC:-99} MAX_GLIBCXX=${MAX_GLIBCXX:-99} MAX_CXXABI=${MAX_CXXABI:-99} \
         bash "$SCRIPT_DIR/docker/verify-natives.sh" "$PLATFORM" "$DEST" \
         "$REPO_ROOT/src/main/java/us/ihmc/fastddsjava/natives/fastddsjava.java"
      ;;
   android)
      find "$DEST" -mindepth 1 -maxdepth 1 -name '*.so' -exec rm -f {} +
      cp "$INSTALL_DIR/lib/libfastcdr.so" "$INSTALL_DIR/lib/libfastdds.so" "$J/libjnifastddsjava.so" "$DEST/"
      # NDK r28+ ships libc++_shared.so with debug info (~9 MB); stripped it is ~1.4 MB.
      cp "$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/$NDK_TRIPLE/libc++_shared.so" "$DEST/"
      for f in "$DEST"/*.so; do "$STRIP" --strip-unneeded "$f"; done
      chmod 0644 "$DEST"/*
      EXPECT_SECURITY=$([ "$SECURITY" = ON ] && echo 1 || echo 0) \
         bash "$SCRIPT_DIR/docker/verify-natives.sh" "$PLATFORM" "$DEST" \
         "$REPO_ROOT/src/main/java/us/ihmc/fastddsjava/natives/fastddsjava.java"
      ;;
   macos)
      find "$DEST" -mindepth 1 -maxdepth 1 \( -name '*.dylib' -o -name natives.list \) -exec rm -f {} +
      : > "$DEST/natives.list"
      for lib in fastcdr fastdds; do
         real=$(cd "$INSTALL_DIR/lib" && pwd)/$(basename "$(readlink -f "$INSTALL_DIR/lib/lib$lib.dylib")")
         # Ship under the install-name leaf (libfastcdr.2.dylib, libfastdds.3.6.dylib) so @loader_path resolves it.
         name=$(basename "$(otool -D "$real" | tail -1)")
         cp "$real" "$DEST/$name"
         chmod u+w "$DEST/$name"
         # Fast-DDS/Fast-CDR force their install prefix as LC_RPATH on Apple: replace it with @loader_path.
         for rp in $(otool -l "$DEST/$name" | awk '$1 == "cmd" && $2 == "LC_RPATH" { getline; getline; print $2 }'); do
            install_name_tool -delete_rpath "$rp" "$DEST/$name"
         done
         install_name_tool -add_rpath @loader_path "$DEST/$name"
         echo "$name" >> "$DEST/natives.list"
      done
      cp "$J/libjnifastddsjava.dylib" "$DEST/"
      echo libjnifastddsjava.dylib >> "$DEST/natives.list"
      for f in "$DEST"/*.dylib; do
         strip -x "$f"
         codesign --force --sign - "$f"   # install_name_tool/strip invalidate the (ad-hoc) signature
      done
      # Verify: architecture, deployment target, rpaths and dependencies.
      fail=0
      for f in "$DEST"/*.dylib; do
         lipo -archs "$f" | grep -qx "$MAC_ARCH" || { echo "ERROR: $f is not $MAC_ARCH-only"; fail=1; }
         minos=$(otool -l "$f" | awk '$1 == "minos" { print $2; exit }')
         [ "$minos" = "$MACOS_DEPLOYMENT_TARGET" ] || { echo "ERROR: $f minos $minos != $MACOS_DEPLOYMENT_TARGET"; fail=1; }
         for rp in $(otool -l "$f" | awk '$1 == "cmd" && $2 == "LC_RPATH" { getline; getline; print $2 }'); do
            [ "$rp" = @loader_path ] || { echo "ERROR: $f has LC_RPATH $rp"; fail=1; }
         done
         for dep in $(otool -L "$f" | tail -n +2 | awk '{ print $1 }'); do
            case "$dep" in
               /usr/lib/* | /System/* | @rpath/libjnifastddsjava.dylib) ;;
               @rpath/*) [ -f "$DEST/${dep#@rpath/}" ] || { echo "ERROR: $f needs $dep, which is not shipped"; fail=1; } ;;
               *) echo "ERROR: $f depends on $dep"; fail=1 ;;
            esac
         done
      done
      [ $fail = 0 ] || exit 1
      ;;
   windows)
      # Drop stale DLLs so we never ship an old JNI binary linked against a previous Fast-DDS version.
      find "$DEST" -mindepth 1 -maxdepth 1 \( -name '*.dll' -o -name natives.list \) -exec rm -f {} +
      if ! ls "$INSTALL_DIR"/bin/fastcdr*.dll > /dev/null 2>&1 || ! ls "$INSTALL_DIR"/bin/fastdds*.dll > /dev/null 2>&1; then
         echo "Error: Fast-DDS/Fast-CDR Windows DLLs missing from install/bin" >&2
         ls -la "$INSTALL_DIR/bin" >&2 || true
         exit 1
      fi
      [ -f "$J/jnifastddsjava.dll" ] || { echo "Error: Windows JNI build did not produce jnifastddsjava.dll" >&2; exit 1; }
      cp "$INSTALL_DIR"/bin/fastcdr*.dll "$INSTALL_DIR"/bin/fastdds*.dll "$J/jnifastddsjava.dll" "$DEST/"
      # Load order: Fast-CDR, Fast-DDS, then the JNI library.
      { (cd "$DEST" && ls fastcdr*.dll && ls fastdds*.dll); echo jnifastddsjava.dll; } > "$DEST/natives.list"
      ;;
esac
echo "Installed $PLATFORM natives into $DEST:"
ls -la "$DEST"
cd "$WORK"

# ------------------------------------------------------------------------------------------------ xjc (optional)
# Run it rather than look for it: macOS has an /usr/bin/xjc stub that fails unless a JDK with xjc is installed (JDK 11 removed it).
if xjc -version > /dev/null 2>&1; then
   echo "Generating Java classes from XSD with xjc..."
   xjc -no-header -p us.ihmc.fastddsjava.profiles.gen -d ../src/main/java "$FASTDDS_SRC/resources/xsd/fastdds_profiles.xsd"

   echo "Stripping JAXB annotations from generated profile classes..."
   python3 "$SCRIPT_DIR/strip_jaxb_annotations.py" ../src/main/java/us/ihmc/fastddsjava/profiles/gen

   # Delete JAXB-specific files that are not needed for plain POJO marshaling
   rm -f ../src/main/java/us/ihmc/fastddsjava/profiles/gen/ObjectFactory.java
   rm -f ../src/main/java/us/ihmc/fastddsjava/profiles/gen/package-info.java

   if command -v dos2unix > /dev/null 2>&1; then
      find "../src/main/java/us/ihmc/fastddsjava/profiles/gen" -type f -name "*.java" -exec dos2unix {} \;
   fi
else
   echo "xjc not found (skipping profile class generation)"
fi
