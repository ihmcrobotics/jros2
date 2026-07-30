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

#!/bin/bash
# This build script is designed to work on Linux and Windows. For Windows, run from a bash shell launched with launchBashWindows.bat

# Clean
rm -rf cppbuild/us
#find src/main/java/us/ihmc/fastddsjava -maxdepth 1 -type f -not \( -name "fastddsjavaConfig.java" \) -delete

pushd .
mkdir -p cppbuild
cd cppbuild

FOONATHAN_MEMORY_VENDOR_VERSION=1.4.1
if [ ! -f "foonathan_memory_vendor-$FOONATHAN_MEMORY_VENDOR_VERSION.tar.gz" ]; then
  curl -o foonathan_memory_vendor-$FOONATHAN_MEMORY_VENDOR_VERSION.tar.gz https://codeload.github.com/eProsima/foonathan_memory_vendor/tar.gz/refs/tags/v$FOONATHAN_MEMORY_VENDOR_VERSION
fi
tar -xvf foonathan_memory_vendor-$FOONATHAN_MEMORY_VENDOR_VERSION.tar.gz

FASTCDR_VERSION=2.3.5
if [ ! -f "Fast-CDR-$FASTCDR_VERSION.tar.gz" ]; then
  curl -o Fast-CDR-$FASTCDR_VERSION.tar.gz https://codeload.github.com/eProsima/Fast-CDR/tar.gz/refs/tags/v$FASTCDR_VERSION
fi
tar -xvf Fast-CDR-$FASTCDR_VERSION.tar.gz

FASTDDS_VERSION=3.6.2
# Using git for libtinyxml and libasio submodules
rm -rf Fast-DDS-$FASTDDS_VERSION
git clone --depth 1 https://github.com/eProsima/Fast-DDS.git -b v$FASTDDS_VERSION Fast-DDS-$FASTDDS_VERSION

INSTALL_DIR=$(pwd)

COMPILER_ARGS=""
JNI_CXX="c++"
JNI_CXXFLAGS="-std=c++17 -O3 -Wall -fPIC -pthread -shared"
JNI_TARGET_NAME="libjnifastddsjava.so"
if [ "$ANDROID_COMPILE" == "1" ]; then
  # Android cross-compilation
  # Check if ANDROID_NDK is set
  if [ -z "$ANDROID_NDK" ]; then
    echo "Error: ANDROID_NDK environment variable is not set"
    exit 1
  fi
  # Set ANDROID_ABI (default: arm64-v8a) and ANDROID_API_LEVEL (default: 24) if needed
  ANDROID_ABI=${ANDROID_ABI:-arm64-v8a}
  # ANDROID_ABI=x86_64  # Commented out - use ANDROID_ABI env var or default arm64-v8a
  ANDROID_API_LEVEL=${ANDROID_API_LEVEL:-24}
  # Add flags to disable warnings that Android NDK clang treats as errors
  ANDROID_CXX_FLAGS="-Wno-error=gnu-offsetof-extensions"
  COMPILER_ARGS="-DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake -DANDROID_ABI=$ANDROID_ABI -DANDROID_PLATFORM=android-$ANDROID_API_LEVEL -DANDROID_NDK=$ANDROID_NDK -DANDROID_STL=c++_shared -DCMAKE_CXX_FLAGS=\"$ANDROID_CXX_FLAGS\""
elif [ "$MAC_COMPILE_X86_64" == "1" ]; then
  # Export compiler flags so cmake and all subproject builds pick up the target arch
  ARCH_FLAGS="-arch x86_64"
  export CFLAGS="$ARCH_FLAGS"
  export CXXFLAGS="$ARCH_FLAGS"
  export LDFLAGS="$ARCH_FLAGS"
  COMPILER_ARGS="-DCMAKE_OSX_ARCHITECTURES=x86_64"
  JNI_CXXFLAGS="$JNI_CXXFLAGS $ARCH_FLAGS"
  JNI_TARGET_NAME="libjnifastddsjava.dylib"
elif [ "$MAC_COMPILE_ARM64" == "1" ]; then
  # Export compiler flags so cmake and all subproject builds pick up the target arch
  ARCH_FLAGS="-arch arm64"
  export CFLAGS="$ARCH_FLAGS"
  export CXXFLAGS="$ARCH_FLAGS"
  export LDFLAGS="$ARCH_FLAGS"
  COMPILER_ARGS="-DCMAKE_OSX_ARCHITECTURES=arm64"
  JNI_CXXFLAGS="$JNI_CXXFLAGS $ARCH_FLAGS"
  JNI_TARGET_NAME="libjnifastddsjava.dylib"
elif [ "$LINUX_COMPILE_ARM64" == "1" ]; then
  COMPILER_ARGS="-DCMAKE_TOOLCHAIN_FILE=$INSTALL_DIR/../linux-aarch64-toolchain.cmake"
  JNI_CXX="aarch64-linux-gnu-g++"
elif [ "$LINUX_COMPILE_ARMHF" == "1" ]; then
  COMPILER_ARGS="-DCMAKE_TOOLCHAIN_FILE=$INSTALL_DIR/../linux-armhf-toolchain.cmake"
  JNI_CXX="arm-linux-gnueabihf-g++"
elif [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || "$OS" == "Windows_NT" ]]; then
  # Fast-DDS is built with MSVC; JNI must use the same toolchain/ABI.
  WINDOWS_COMPILE=1
  JNI_TARGET_NAME="jnifastddsjava.dll"
  # Cross-compile ARM64 from an x64 host via msvc-dev-cmd arch=amd64_arm64 (or native ARM64 host).
  if [ "$WINDOWS_COMPILE_ARM64" == "1" ]; then
    COMPILER_ARGS="-A ARM64"
  fi
elif [[ "$OSTYPE" == "darwin"* ]]; then
  JNI_TARGET_NAME="libjnifastddsjava.dylib"
fi

WINDOWS_COMPILE=${WINDOWS_COMPILE:-0}
WINDOWS_COMPILE_ARM64=${WINDOWS_COMPILE_ARM64:-0}

# Build foonathan_memory_vendor
pushd .
cd foonathan_memory_vendor-$FOONATHAN_MEMORY_VENDOR_VERSION

# Patch foonathan_memory_vendor CMakeLists.txt to propagate CMAKE_ANDROID_FLAGS for Android
if [ "$ANDROID_COMPILE" == "1" ]; then
  if ! grep -q "CMAKE_ANDROID_FLAGS_PATCH" CMakeLists.txt; then
    sed -i "/list(APPEND extra_cmake_args -DCMAKE_POSITION_INDEPENDENT_CODE=\${CMAKE_POSITION_INDEPENDENT_CODE})/a\  # CMAKE_ANDROID_FLAGS_PATCH\n  list(APPEND extra_cmake_args \"-DCMAKE_CXX_FLAGS=$CMAKE_ANDROID_FLAGS\")" CMakeLists.txt
  fi
fi

mkdir -p build
cd build
cmake .. $COMPILER_ARGS -DCMAKE_INSTALL_PREFIX=$INSTALL_DIR/install -DCMAKE_PREFIX_PATH=$INSTALL_DIR/install
if [ "$ANDROID_COMPILE" == "1" ]; then
  # Modify CMakeCache to add warning suppression flags, then reconfigure
  sed -i "s/CMAKE_CXX_FLAGS:STRING=/CMAKE_CXX_FLAGS:STRING=$CMAKE_ANDROID_FLAGS /" CMakeCache.txt
  cmake .. $COMPILER_ARGS -DCMAKE_INSTALL_PREFIX=$INSTALL_DIR/install -DCMAKE_PREFIX_PATH=$INSTALL_DIR/install
fi
cmake --build . --config Release --target install
popd

# Build Fast-CDR
pushd .
cd Fast-CDR-$FASTCDR_VERSION
mkdir -p build
cd build
cmake .. $COMPILER_ARGS -DCMAKE_INSTALL_PREFIX=$INSTALL_DIR/install -DCMAKE_PREFIX_PATH=$INSTALL_DIR/install
if [ "$ANDROID_COMPILE" == "1" ]; then
  # Modify CMakeCache to add warning suppression flags, then reconfigure
  sed -i "s/CMAKE_CXX_FLAGS:STRING=/CMAKE_CXX_FLAGS:STRING=$CMAKE_ANDROID_FLAGS /" CMakeCache.txt
  cmake .. $COMPILER_ARGS -DCMAKE_INSTALL_PREFIX=$INSTALL_DIR/install -DCMAKE_PREFIX_PATH=$INSTALL_DIR/install
fi
cmake --build . --config Release --target install
popd

# Build Fast-DDS
pushd .
cd Fast-DDS-$FASTDDS_VERSION
git submodule update --init --recursive
mkdir -p build
cd build
if [ "$ANDROID_COMPILE" == "1" ]; then
  cmake .. $COMPILER_ARGS -DQNX=OFF -DNO_TLS=ON -DSECURITY=OFF -DTHIRDPARTY_TinyXML2=FORCE -DTHIRDPARTY_Asio=FORCE -DCMAKE_INSTALL_PREFIX=$INSTALL_DIR/install -DCMAKE_PREFIX_PATH=$INSTALL_DIR/install -Dfastcdr_DIR=$INSTALL_DIR/install/lib/cmake/fastcdr -Dfoonathan_memory_DIR=$INSTALL_DIR/install/lib/foonathan_memory/cmake
  # Append warning suppression flags to CMAKE_CXX_FLAGS in the cache after initial configuration
  sed -i "s/CMAKE_CXX_FLAGS:STRING=/CMAKE_CXX_FLAGS:STRING=$CMAKE_ANDROID_FLAGS /" CMakeCache.txt
  cmake .. $COMPILER_ARGS -DQNX=OFF -DNO_TLS=ON -DSECURITY=OFF -DTHIRDPARTY_TinyXML2=FORCE -DTHIRDPARTY_Asio=FORCE -DCMAKE_INSTALL_PREFIX=$INSTALL_DIR/install -DCMAKE_PREFIX_PATH=$INSTALL_DIR/install -Dfastcdr_DIR=$INSTALL_DIR/install/lib/cmake/fastcdr -Dfoonathan_memory_DIR=$INSTALL_DIR/install/lib/foonathan_memory/cmake
else
  cmake .. $COMPILER_ARGS -DQNX=OFF -DNO_TLS=ON -DSECURITY=OFF -DTHIRDPARTY_TinyXML2=FORCE -DTHIRDPARTY_Asio=FORCE -DCMAKE_INSTALL_PREFIX=$INSTALL_DIR/install -DCMAKE_PREFIX_PATH=$INSTALL_DIR/install
fi
cmake --build . --config Release --target install -j $(nproc 2>/dev/null || sysctl -n hw.logicalcpu)
popd

rm -rf install/include/fastddsjava.h
cp ../src/native/fastddsjava.h install/include/fastddsjava.h

popd

#### Hand-written JNI compilation ####
pushd cppbuild
mkdir -p javainstall
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_BIN="$(command -v java)"
  JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$JAVA_BIN" 2>/dev/null || echo "$JAVA_BIN")")")"
fi
JNI_INCLUDE="-I${JAVA_HOME}/include"
if [ -d "${JAVA_HOME}/include/linux" ]; then
  JNI_INCLUDE="$JNI_INCLUDE -I${JAVA_HOME}/include/linux"
elif [ -d "${JAVA_HOME}/include/darwin" ]; then
  JNI_INCLUDE="$JNI_INCLUDE -I${JAVA_HOME}/include/darwin"
elif [ -d "${JAVA_HOME}/include/win32" ]; then
  JNI_INCLUDE="$JNI_INCLUDE -I${JAVA_HOME}/include/win32"
fi

to_win_path() {
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -w "$1"
  else
    echo "$1"
  fi
}

if [ "$ANDROID_COMPILE" == "1" ]; then
  # Prefer NDK JNI headers. Host OpenJDK jni.h uses AttachCurrentThread(void**),
  # while Android's uses AttachCurrentThread(JNIEnv**) — required by our __ANDROID__ paths.
  JNI_INCLUDE=""
  ANDROID_TOOLCHAIN_BIN="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
  if [ "$ANDROID_ABI" == "arm64-v8a" ]; then
    ANDROID_COMPILER_PREFIX="aarch64-linux-android"
  elif [ "$ANDROID_ABI" == "armeabi-v7a" ]; then
    ANDROID_COMPILER_PREFIX="armv7a-linux-androideabi"
  elif [ "$ANDROID_ABI" == "x86_64" ]; then
    ANDROID_COMPILER_PREFIX="x86_64-linux-android"
  elif [ "$ANDROID_ABI" == "x86" ]; then
    ANDROID_COMPILER_PREFIX="i686-linux-android"
  fi
  JNI_CXX="${ANDROID_TOOLCHAIN_BIN}/${ANDROID_COMPILER_PREFIX}${ANDROID_API_LEVEL}-clang++"
  $JNI_CXX ../src/native/fastddsjava.cpp ../src/native/jnifastddsjava.cpp \
    -I../src/native -I${INSTALL_DIR}/install/include $JNI_INCLUDE \
    -std=c++17 -fexceptions -frtti -O3 -Wall -fPIC -pthread -shared \
    -o javainstall/libjnifastddsjava.so -L${INSTALL_DIR}/install/lib \
    -Wl,-rpath,${INSTALL_DIR}/install/lib \
    -lfastdds -lfastcdr -llog -lc++_shared
  if [ ! -f "javainstall/libjnifastddsjava.so" ]; then
    echo "Error: Android JNI build did not produce javainstall/libjnifastddsjava.so"
    exit 1
  fi
elif [ "$WINDOWS_COMPILE" == "1" ]; then
  # Match Fast-DDS MSVC build: MinGW cannot consume MSVC import libs as -lfastdds.
  if ! command -v cl.exe >/dev/null 2>&1; then
    echo "Error: cl.exe not found. Launch from an MSVC developer environment (e.g. ilammy/msvc-dev-cmd)."
    exit 1
  fi
  if [ ! -f "install/lib/fastdds-3.6.lib" ] || [ ! -f "install/lib/fastcdr-2.3.lib" ]; then
    echo "Error: missing Fast-DDS/Fast-CDR MSVC import libraries under install/lib"
    ls -la install/lib || true
    exit 1
  fi
  # eProsima auto-link looks for libfastdds-3.6.lib / libfastcdr-2.3.lib unless DYN_LINK is set.
  cp -f install/lib/fastdds-3.6.lib install/lib/libfastdds-3.6.lib
  cp -f install/lib/fastcdr-2.3.lib install/lib/libfastcdr-2.3.lib
  rm -f javainstall/jnifastddsjava.dll javainstall/jnifastddsjava.lib javainstall/jnifastddsjava.exp
  rm -rf javainstall/obj
  mkdir -p javainstall/obj
  # Use dash-style MSVC flags: Git Bash rewrites leading-/ args as filesystem paths.
  MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' cl.exe -nologo -EHsc -std:c++17 -O2 -MD -LD \
    -DEPROSIMA_ALL_DYN_LINK \
    -I"$(to_win_path ../src/native)" \
    -I"$(to_win_path "${INSTALL_DIR}/install/include")" \
    -I"$(to_win_path "${JAVA_HOME}/include")" \
    -I"$(to_win_path "${JAVA_HOME}/include/win32")" \
    -Fo"$(to_win_path javainstall/obj)\\" \
    -Fe"$(to_win_path javainstall/jnifastddsjava.dll)" \
    "$(to_win_path ../src/native/fastddsjava.cpp)" \
    "$(to_win_path ../src/native/jnifastddsjava.cpp)" \
    -link -LIBPATH:"$(to_win_path "${INSTALL_DIR}/install/lib")" fastdds-3.6.lib fastcdr-2.3.lib
  if [ ! -f "javainstall/jnifastddsjava.dll" ]; then
    echo "Error: Windows JNI build did not produce javainstall/jnifastddsjava.dll"
    exit 1
  fi
else
  $JNI_CXX ../src/native/fastddsjava.cpp ../src/native/jnifastddsjava.cpp \
    -I../src/native -I${INSTALL_DIR}/install/include $JNI_INCLUDE \
    $JNI_CXXFLAGS \
    -o javainstall/$JNI_TARGET_NAME -L${INSTALL_DIR}/install/lib \
    -Wl,-rpath,'$ORIGIN' \
    -lfastdds -lfastcdr
  if [ ! -f "javainstall/$JNI_TARGET_NAME" ]; then
    echo "Error: JNI build did not produce javainstall/$JNI_TARGET_NAME"
    exit 1
  fi
fi

##### Copy shared libs to resources ####
# Linux
if [ "$LINUX_COMPILE_ARM64" == "1" ]; then
  LINUX_GEN_PATH="../src/main/resources/fastddsjava/native/linux-arm64"
elif [ "$LINUX_COMPILE_ARMHF" == "1" ]; then
  LINUX_GEN_PATH="../src/main/resources/fastddsjava/native/linux-armhf"
else
  LINUX_GEN_PATH="../src/main/resources/fastddsjava/native/linux-x86_64"
fi
mkdir -p "$LINUX_GEN_PATH"
# Linux desktop platforms use versioned library names.
# Fast-DDS 3.6+ installs as libfastdds.so.<version>.0; ship as libfastdds.so.<version>.
if [ -f "install/lib/libfastcdr.so.$FASTCDR_VERSION" ]; then
  cp install/lib/libfastcdr.so.$FASTCDR_VERSION "$LINUX_GEN_PATH"
  strip "$LINUX_GEN_PATH/libfastcdr.so.$FASTCDR_VERSION"
fi
if [ -f "install/lib/libfastdds.so.$FASTDDS_VERSION.0" ]; then
  cp install/lib/libfastdds.so.$FASTDDS_VERSION.0 "$LINUX_GEN_PATH/libfastdds.so.$FASTDDS_VERSION"
  strip "$LINUX_GEN_PATH/libfastdds.so.$FASTDDS_VERSION"
elif [ -f "install/lib/libfastdds.so.$FASTDDS_VERSION" ]; then
  cp install/lib/libfastdds.so.$FASTDDS_VERSION "$LINUX_GEN_PATH"
  strip "$LINUX_GEN_PATH/libfastdds.so.$FASTDDS_VERSION"
fi
if [ -f "javainstall/libjnifastddsjava.so" ]; then
  cp javainstall/libjnifastddsjava.so "$LINUX_GEN_PATH"
  strip "$LINUX_GEN_PATH/libjnifastddsjava.so"
fi
# Windows: Fast-DDS installs versioned DLLs (e.g. fastdds-3.6.dll, fastcdr-2.3.dll).
if [ "$WINDOWS_COMPILE" == "1" ]; then
  if [ "$WINDOWS_COMPILE_ARM64" == "1" ]; then
    WINDOWS_GEN_PATH="../src/main/resources/fastddsjava/native/windows-arm64"
  else
    WINDOWS_GEN_PATH="../src/main/resources/fastddsjava/native/windows-x86_64"
  fi
  mkdir -p "$WINDOWS_GEN_PATH"
  # Drop stale DLLs so we never ship an old JNI binary linked against a previous Fast-DDS SONAME.
  rm -f "$WINDOWS_GEN_PATH"/*.dll
  if ! ls install/bin/fastcdr*.dll >/dev/null 2>&1 || ! ls install/bin/fastdds*.dll >/dev/null 2>&1; then
    echo "Error: Fast-DDS/Fast-CDR Windows DLLs missing from install/bin"
    ls -la install/bin || true
    exit 1
  fi
  if [ ! -f "javainstall/jnifastddsjava.dll" ]; then
    echo "Error: javainstall/jnifastddsjava.dll missing; Windows JNI build failed"
    exit 1
  fi
  cp install/bin/fastcdr*.dll "$WINDOWS_GEN_PATH/"
  cp install/bin/fastdds*.dll "$WINDOWS_GEN_PATH/"
  cp javainstall/jnifastddsjava.dll "$WINDOWS_GEN_PATH/"
fi
# macOS
if [ "$MAC_COMPILE_ARM64" == "1" ]; then
  MACOS_GEN_PATH="../src/main/resources/fastddsjava/native/macos-arm64"
elif [ "$MAC_COMPILE_X86_64" == "1" ]; then
  MACOS_GEN_PATH="../src/main/resources/fastddsjava/native/macos-x86_64"
else
  MACOS_GEN_PATH=""
fi
if [ -n "$MACOS_GEN_PATH" ]; then
  mkdir -p "$MACOS_GEN_PATH"
  if [ -f "install/lib/libfastcdr.$FASTCDR_VERSION.dylib" ]; then
    cp install/lib/libfastcdr.$FASTCDR_VERSION.dylib "$MACOS_GEN_PATH"
  fi
  if [ -f "install/lib/libfastdds.$FASTDDS_VERSION.0.dylib" ]; then
    cp install/lib/libfastdds.$FASTDDS_VERSION.0.dylib "$MACOS_GEN_PATH/libfastdds.$FASTDDS_VERSION.dylib"
  elif [ -f "install/lib/libfastdds.$FASTDDS_VERSION.dylib" ]; then
    cp install/lib/libfastdds.$FASTDDS_VERSION.dylib "$MACOS_GEN_PATH"
  fi
  if [ -f "javainstall/libjnifastddsjava.dylib" ]; then
    cp javainstall/libjnifastddsjava.dylib "$MACOS_GEN_PATH"
  fi
fi
# Android
if [ "$ANDROID_COMPILE" == "1" ]; then
  # Map ANDROID_ABI to Android jniLibs directory naming
  if [ "$ANDROID_ABI" == "arm64-v8a" ]; then
    ANDROID_GEN_PATH="../android/src/main/jniLibs/arm64-v8a"
    ANDROID_NDK_TRIPLE="aarch64-linux-android"
  elif [ "$ANDROID_ABI" == "armeabi-v7a" ]; then
    ANDROID_GEN_PATH="../android/src/main/jniLibs/armeabi-v7a"
    ANDROID_NDK_TRIPLE="arm-linux-androideabi"
  elif [ "$ANDROID_ABI" == "x86_64" ]; then
    ANDROID_GEN_PATH="../android/src/main/jniLibs/x86_64"
    ANDROID_NDK_TRIPLE="x86_64-linux-android"
  elif [ "$ANDROID_ABI" == "x86" ]; then
    ANDROID_GEN_PATH="../android/src/main/jniLibs/x86"
    ANDROID_NDK_TRIPLE="i686-linux-android"
  else
    echo "Unsupported ANDROID_ABI: $ANDROID_ABI"
    exit 1
  fi
  mkdir -p "$ANDROID_GEN_PATH"
  # Android libraries without version numbers
  if [ -f "install/lib/libfastcdr.so" ]; then
    cp -f install/lib/libfastcdr.so "$ANDROID_GEN_PATH/libfastcdr.so"
    ${ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip "$ANDROID_GEN_PATH/libfastcdr.so"
  fi
  if [ -f "install/lib/libfastdds.so" ]; then
    cp -f install/lib/libfastdds.so "$ANDROID_GEN_PATH/libfastdds.so"
    ${ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip "$ANDROID_GEN_PATH/libfastdds.so"
  fi
  if [ -f "javainstall/libjnifastddsjava.so" ]; then
    cp -f javainstall/libjnifastddsjava.so "$ANDROID_GEN_PATH/libjnifastddsjava.so"
    ${ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip "$ANDROID_GEN_PATH/libjnifastddsjava.so"
  fi
  # Bundle the C++ shared runtime required by Fast-DDS / JNI on Android.
  ANDROID_LIBCPP_SHARED="${ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/${ANDROID_NDK_TRIPLE}/libc++_shared.so"
  if [ -f "$ANDROID_LIBCPP_SHARED" ]; then
    cp -f "$ANDROID_LIBCPP_SHARED" "$ANDROID_GEN_PATH/libc++_shared.so"
  else
    echo "Warning: libc++_shared.so not found at $ANDROID_LIBCPP_SHARED"
  fi
  # Ensure the Android linker records the C++ runtime dependency (needed when symbols are unresolved).
  if command -v patchelf >/dev/null 2>&1; then
    for lib in libfastdds.so libjnifastddsjava.so; do
      if [ -f "$ANDROID_GEN_PATH/$lib" ] && ! readelf -d "$ANDROID_GEN_PATH/$lib" | grep -q 'libc++_shared.so'; then
        patchelf --add-needed libc++_shared.so "$ANDROID_GEN_PATH/$lib"
      fi
    done
  fi
fi
popd

# xjc generation ###
pushd cppbuild

if command -v xjc >/dev/null 2>&1; then
  echo "Generating Java classes from XSD with xjc..."
  xjc -no-header -p us.ihmc.fastddsjava.profiles.gen -d ../src/main/java Fast-DDS-$FASTDDS_VERSION/resources/xsd/fastdds_profiles.xsd

  echo "Stripping JAXB annotations from generated profile classes..."
  python3 ../strip_jaxb_annotations.py ../src/main/java/us/ihmc/fastddsjava/profiles/gen

  # Delete JAXB-specific files that are not needed for plain POJO marshalling
  rm -f ../src/main/java/us/ihmc/fastddsjava/profiles/gen/ObjectFactory.java
  rm -f ../src/main/java/us/ihmc/fastddsjava/profiles/gen/package-info.java

  if command -v dos2unix &> /dev/null; then
    find "../src/main/java/us/ihmc/fastddsjava/profiles/gen" -type f -name "*.java" -exec dos2unix {} \;
  fi
else
  echo "xjc not found"
fi

popd
