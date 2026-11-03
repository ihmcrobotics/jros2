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

# Per-platform toolchain settings, sourced inside the build container by deps.sh and jni.sh.
# Changing this file invalidates the dependency caches (it is part of the cache fingerprint).
#
# Input:  PLATFORM (see build-natives.sh)
# Output: KIND (glibc|musl|android), CROSS, OSSL_TARGET, OSSL_ARGS_EXTRA, OSSL_CC, TOOLCHAIN_ARGS, EXTRA_CFLAGS, EXTRA_CXXFLAGS,
#         SHARED_LDFLAGS, STD_LIBS, STRIP, JNI_CXX, JNI_FLAGS, JNI_LIBS, JAVA_INC, ANDROID_ABI, NDK_TRIPLE, EXPECT_MACHINE

# shellcheck shell=bash
# shellcheck disable=SC2034,SC2153  # variables are consumed by the sourcing scripts; ANDROID_API comes from native-versions.env

: "${PLATFORM:?PLATFORM is not set}"

KIND=""
CROSS=""
SYSPROC=""
OSSL_TARGET=""
OSSL_ARGS_EXTRA=()
OSSL_CC=""
TOOLCHAIN_ARGS=()
ANDROID_ABI=""
NDK_TRIPLE=""
CLANG_TRIPLE=""
EXPECT_MACHINE=""   # `uname -m` of the container for native (non-cross) builds

case "$PLATFORM" in
   linux-x86_64)        KIND=glibc;   OSSL_TARGET=linux-x86_64;    EXPECT_MACHINE=x86_64 ;;
   linux-arm64)         KIND=glibc;   OSSL_TARGET=linux-aarch64;   CROSS=aarch64-linux-gnu-;   SYSPROC=aarch64 ;;
   linux-armhf)         KIND=glibc;   OSSL_TARGET=linux-armv4;     CROSS=arm-linux-gnueabihf-; SYSPROC=arm ;;
   linux-riscv64)       KIND=glibc;   OSSL_TARGET=linux64-riscv64; CROSS=riscv64-linux-gnu-;   SYSPROC=riscv64 ;;
   linux-musl-x86_64)   KIND=musl;    OSSL_TARGET=linux-x86_64;    EXPECT_MACHINE=x86_64 ;;
   linux-musl-arm64)    KIND=musl;    OSSL_TARGET=linux-aarch64;   EXPECT_MACHINE=aarch64 ;;
   android-arm64-v8a)   KIND=android; OSSL_TARGET=android-arm64;   ANDROID_ABI=arm64-v8a
                        NDK_TRIPLE=aarch64-linux-android;   CLANG_TRIPLE=aarch64-linux-android ;;
   android-armeabi-v7a) KIND=android; OSSL_TARGET=android-arm;     ANDROID_ABI=armeabi-v7a
                        NDK_TRIPLE=arm-linux-androideabi;   CLANG_TRIPLE=armv7a-linux-androideabi ;;
   android-x86_64)      KIND=android; OSSL_TARGET=android-x86_64;  ANDROID_ABI=x86_64
                        NDK_TRIPLE=x86_64-linux-android;    CLANG_TRIPLE=x86_64-linux-android ;;
   *) echo "env.sh: unknown platform '$PLATFORM'" >&2; return 2 ;;
esac

if [ -n "$EXPECT_MACHINE" ] && [ "$(uname -m)" != "$EXPECT_MACHINE" ]; then
   echo "env.sh: $PLATFORM must be built in a $EXPECT_MACHINE container, this one is $(uname -m)" >&2
   return 2
fi

# Build paths (/work/src, /work/build) never end up in the binaries: __FILE__ (Fast-DDS log messages) becomes relative.
EXTRA_CFLAGS="-ffile-prefix-map=/work/src/= -ffile-prefix-map=/work/build/="
EXTRA_CXXFLAGS="$EXTRA_CFLAGS"
# Applied to every shared library linked by CMake (libfastcdr, libfastdds).
SHARED_LDFLAGS="-Wl,-z,noexecstack -Wl,--gc-sections"
# Appended at the END of every CMake link line (after the objects), so a static archive listed here is really used.
STD_LIBS=""

if [ "$KIND" = musl ]; then
   # Fast-DDS compiles its POSIX thread implementation (thread names, and every ThreadSettings field: affinity, scheduling
   # policy, priority, stack size) only when _POSIX_SOURCE is defined (src/cpp/utils/SystemInfo.cpp, thread.hpp);
   # otherwise it falls back to threading_empty.ipp and std::thread and silently ignores ROS2ThreadSettings. glibc's
   # <features.h> defines _POSIX_SOURCE under g++'s implicit _GNU_SOURCE, musl's never does. C++ only: g++ always
   # defines _GNU_SOURCE, so this does not narrow what musl's headers declare.
   EXTRA_CXXFLAGS="$EXTRA_CXXFLAGS -D_POSIX_SOURCE"
fi

if [ "$KIND" = android ]; then
   NDK=/ndk
   NDK_BIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
   export ANDROID_NDK_ROOT=$NDK ANDROID_NDK_HOME=$NDK
   export PATH="$NDK_BIN:$PATH"
   STRIP=$NDK_BIN/llvm-strip
   JNI_CXX="$NDK_BIN/${CLANG_TRIPLE}${ANDROID_API}-clang++"
   TOOLCHAIN_ARGS=(
      "-DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake"
      "-DANDROID_ABI=$ANDROID_ABI"
      "-DANDROID_PLATFORM=android-$ANDROID_API"
      "-DANDROID_STL=c++_shared"
      # Default since NDK r28; explicit so an older NDK cannot silently produce 4 KB aligned libraries.
      "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
   )
   # clang >= 19 warns about offsetof on non-standard-layout types in foonathan memory, which builds with -Werror.
   EXTRA_CXXFLAGS="$EXTRA_CXXFLAGS -Wno-error=gnu-offsetof-extensions"
   if [ "$ANDROID_ABI" = arm64-v8a ] || [ "$ANDROID_ABI" = x86_64 ]; then
      # Android 15+ devices may use 16 KB pages; every LOAD segment of a 64-bit library must be 16 KB aligned.
      SHARED_LDFLAGS="$SHARED_LDFLAGS -Wl,-z,max-page-size=16384"
   fi
   OSSL_ARGS_EXTRA=("-D__ANDROID_API__=$ANDROID_API")
   OSSL_CC=clang   # OpenSSL's android-* targets combine this with the NDK triple found via ANDROID_NDK_ROOT
else
   STRIP=${CROSS}strip
   JNI_CXX=${CROSS}g++
   SHARED_LDFLAGS="$SHARED_LDFLAGS -Wl,--enable-new-dtags"
   if [ -n "$CROSS" ]; then
      cat > /work/toolchain.cmake <<EOF
set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR $SYSPROC)
set(CMAKE_C_COMPILER ${CROSS}gcc)
set(CMAKE_CXX_COMPILER ${CROSS}g++)
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE ONLY)
EOF
      TOOLCHAIN_ARGS=("-DCMAKE_TOOLCHAIN_FILE=/work/toolchain.cmake")
      OSSL_ARGS_EXTRA=("--cross-compile-prefix=$CROSS")
   fi
   if [ "$PLATFORM" = linux-riscv64 ]; then
      # gcc 9 does not inline 1- and 2-byte atomics on RISC-V (Fast-DDS ReaderProxy, DataSharingListener and
      # SharedMemTransport call __atomic_exchange_1). Fast-DDS' FindAtomic probe only tests 8-byte atomics and links
      # -lpthread rather than -pthread, so nothing adds libatomic, and an unresolved __atomic_* kills the JVM with a
      # 'symbol lookup error'. libatomic.so.1 is missing from common riscv64 JRE images, so link it statically and
      # keep its symbols private. Two parts:
      #  - STD_LIBS adds the static archive at the end of every link line (nothing else may ask for libatomic);
      #  - a search directory that contains ONLY libatomic.a comes first, so a plain '-latomic' added by a dependency
      #    (OpenSSL's riscv64 target lists it in its link libraries) also resolves to the static archive instead of
      #    libatomic.so.1.
      mkdir -p /work/static-libatomic
      cp "$("${CROSS}gcc" -print-file-name=libatomic.a)" /work/static-libatomic/libatomic.a
      STD_LIBS="-Wl,--push-state,-Bstatic,-latomic,--pop-state"
      SHARED_LDFLAGS="$SHARED_LDFLAGS -L/work/static-libatomic -Wl,--exclude-libs,libatomic.a"
   fi
   JAVA_INC="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")/include"
fi
