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

FOONATHAN_MEMORY_VENDOR_VERSION=1.3.1
if [ ! -f "foonathan_memory_vendor-$FOONATHAN_MEMORY_VENDOR_VERSION.tar.gz" ]; then
  curl -o foonathan_memory_vendor-$FOONATHAN_MEMORY_VENDOR_VERSION.tar.gz https://codeload.github.com/eProsima/foonathan_memory_vendor/tar.gz/refs/tags/v$FOONATHAN_MEMORY_VENDOR_VERSION
fi
tar -xvf foonathan_memory_vendor-$FOONATHAN_MEMORY_VENDOR_VERSION.tar.gz

FASTCDR_VERSION=2.3.0
if [ ! -f "Fast-CDR-$FASTCDR_VERSION.tar.gz" ]; then
  curl -o Fast-CDR-$FASTCDR_VERSION.tar.gz https://codeload.github.com/eProsima/Fast-CDR/tar.gz/refs/tags/v$FASTCDR_VERSION
fi
tar -xvf Fast-CDR-$FASTCDR_VERSION.tar.gz

FASTDDS_VERSION=3.2.2
# Using git for libtinyxml and libasio submodules
git clone https://github.com/eProsima/Fast-DDS.git -b v$FASTDDS_VERSION Fast-DDS-$FASTDDS_VERSION

INSTALL_DIR=$(pwd)

COMPILER_ARGS=""
JAVACPP_COMP_ARGS=""
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
  COMPILER_ARGS="-DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake -DANDROID_ABI=$ANDROID_ABI -DANDROID_PLATFORM=android-$ANDROID_API_LEVEL -DANDROID_NDK=$ANDROID_NDK -DCMAKE_CXX_FLAGS=\"$ANDROID_CXX_FLAGS\""
  JAVACPP_COMP_ARGS="-properties android-$ANDROID_ABI -Dplatform=android-$ANDROID_ABI"
elif [ "$MAC_COMPILE_X86_64" == "1" ]; then
  # Export compiler flags so cmake and all subproject builds pick up the target arch
  export CFLAGS="-arch x86_64"
  export CXXFLAGS="-arch x86_64"
  COMPILER_ARGS="-DCMAKE_OSX_ARCHITECTURES=x86_64"
  JAVACPP_COMP_ARGS="-properties macosx-x86_64 -Dplatform=macosx-x86_64"
elif [ "$LINUX_COMPILE_ARM64" == "1" ]; then
  COMPILER_ARGS="-DCMAKE_TOOLCHAIN_FILE=$INSTALL_DIR/../linux-aarch64-toolchain.cmake"
  JAVACPP_COMP_ARGS="-properties linux-arm64 -Dplatform.compiler=aarch64-linux-gnu-g++ -Dplatform.c.compiler=aarch64-linux-gnu-gcc -Dplatform=linux-arm64"
elif [ "$LINUX_COMPILE_ARMHF" == "1" ]; then
  COMPILER_ARGS="-DCMAKE_TOOLCHAIN_FILE=$INSTALL_DIR/../linux-armhf-toolchain.cmake"
  JAVACPP_COMP_ARGS="-properties linux-armhf -Dplatform.compiler=arm-linux-gnueabihf-g++ -Dplatform.c.compiler=arm-linux-gnueabihf-gcc -Dplatform=linux-armhf"
fi

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

### Windows hack ###
if [ -f "install/lib/fastcdr-2.3.lib" ]; then
  cp install/lib/fastcdr-2.3.lib install/lib/libfastcdr-2.3.lib
fi
if [ -f "install/lib/fastdds-3.2.lib" ]; then
  cp install/lib/fastdds-3.2.lib install/lib/libfastdds-3.2.lib
fi

popd

### Java generation ####
pushd cppbuild

mkdir -p us/ihmc/fastddsjava/pointers
cp ../src/main/java/us/ihmc/fastddsjava/pointers/*.java us/ihmc/fastddsjava/pointers/

JAVACPP_VERSION=1.5.11
if [ ! -f javacpp.jar ]; then
  curl -L https://github.com/bytedeco/javacpp/releases/download/$JAVACPP_VERSION/javacpp-platform-$JAVACPP_VERSION-bin.zip -o javacpp-platform-$JAVACPP_VERSION-bin.zip
  unzip -j javacpp-platform-$JAVACPP_VERSION-bin.zip
fi

java -jar javacpp.jar us/ihmc/fastddsjava/pointers/fastddsjavaInfoMapper.java

cp us/ihmc/fastddsjava/pointers/*.java ../src/main/java/us/ihmc/fastddsjava/pointers/

#### JNI compilation ####
if [ "$ANDROID_COMPILE" == "1" ]; then
  # For Android, we need to specify the compiler path and additional flags
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
  # Set environment for Android compilation
  JAVACPP_CXX="${ANDROID_TOOLCHAIN_BIN}/${ANDROID_COMPILER_PREFIX}${ANDROID_API_LEVEL}-clang++"

  # First generate the code
  java -jar javacpp.jar us/ihmc/fastddsjava/pointers/*.java $JAVACPP_COMP_ARGS \
    -Dplatform.compiler="$JAVACPP_CXX" \
    -Dplatform.includepath="${INSTALL_DIR}/install/include/" \
    -Dplatform.linkpath="${INSTALL_DIR}/install/lib/" \
    -d javainstall -nocompile

  # Patch the generated code to replace char_traits<unsigned short> with char16_t workaround
  for file in javainstall/jni*.cpp; do
    if [ -f "$file" ]; then
      # Replace std::char_traits<unsigned short>::length with a custom strlen implementation
      sed -i 's/std::char_traits<unsigned short>::length(ptr)/([](const unsigned short* p){size_t len=0;while(p[len])len++;return len;}(ptr))/g' "$file"
    fi
  done

  # Now compile with proper flags (static libc++ to avoid runtime dependency)
  cd javainstall
  $JAVACPP_CXX -I${INSTALL_DIR}/install/include jnifastddsjava.cpp jnijavacpp.cpp \
    -std=c++14 -fexceptions -frtti -O3 -Wall -fPIC -pthread -shared \
    -static-libstdc++ \
    -o libjnifastddsjava.so -L${INSTALL_DIR}/install/lib \
    -Wl,-rpath,${INSTALL_DIR}/install/lib \
    -lfastdds -lfastcdr -llog
  cd ..
else
  java -jar javacpp.jar us/ihmc/fastddsjava/pointers/*.java $JAVACPP_COMP_ARGS -d javainstall
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
# Linux desktop platforms use versioned library names
if [ -f "install/lib/libfastcdr.so.2.3.0" ]; then
  cp install/lib/libfastcdr.so.2.3.0 "$LINUX_GEN_PATH"
  strip "$LINUX_GEN_PATH/libfastcdr.so.2.3.0"
fi
if [ -f "install/lib/libfastdds.so.3.2.2" ]; then
  cp install/lib/libfastdds.so.3.2.2 "$LINUX_GEN_PATH"
  strip "$LINUX_GEN_PATH/libfastdds.so.3.2.2"
fi
if [ -f "javainstall/libjnifastddsjava.so" ]; then
  cp javainstall/libjnifastddsjava.so "$LINUX_GEN_PATH"
  strip "$LINUX_GEN_PATH/libjnifastddsjava.so"
fi
# Windows
mkdir -p ../src/main/resources/fastddsjava/native/windows-x86_64
if [ -f "install/bin/fastcdr-2.3.dll" ]; then
  cp install/bin/fastcdr-2.3.dll ../src/main/resources/fastddsjava/native/windows-x86_64
fi
if [ -f "install/bin/fastdds-3.2.dll" ]; then
  cp install/bin/fastdds-3.2.dll ../src/main/resources/fastddsjava/native/windows-x86_64
fi
if [ -f "javainstall/jnifastddsjava.dll" ]; then
  cp javainstall/jnifastddsjava.dll ../src/main/resources/fastddsjava/native/windows-x86_64
fi
# macOS
if [ "$MAC_COMPILE_ARM64" == "1" ]; then
  MACOS_GEN_PATH="../src/main/resources/fastddsjava/native/macos-arm64"
else
  MACOS_GEN_PATH="../src/main/resources/fastddsjava/native/macos-x86_64"
fi
mkdir -p "$MACOS_GEN_PATH"
if [ -f "install/lib/libfastcdr.2.3.0.dylib" ]; then
  cp install/lib/libfastcdr.2.3.0.dylib "$MACOS_GEN_PATH"
fi
if [ -f "install/lib/libfastdds.3.2.2.dylib" ]; then
  cp install/lib/libfastdds.3.2.2.dylib "$MACOS_GEN_PATH"
fi
if [ -f "javainstall/libjnifastddsjava.dylib" ]; then
  cp javainstall/libjnifastddsjava.dylib "$MACOS_GEN_PATH"
fi
# Android
if [ "$ANDROID_COMPILE" == "1" ]; then
  # Map ANDROID_ABI to Android jniLibs directory naming
  if [ "$ANDROID_ABI" == "arm64-v8a" ]; then
    ANDROID_GEN_PATH="../android/src/main/jniLibs/arm64-v8a"
  elif [ "$ANDROID_ABI" == "x86_64" ]; then
    ANDROID_GEN_PATH="../android/src/main/jniLibs/x86_64"
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
fi
popd

# xjc generation ###
pushd cppbuild

if command -v xjc >/dev/null 2>&1 && xjc; then
  echo "Generating Java classes from XSD with xjc..."
  xjc -no-header -p us.ihmc.fastddsjava.profiles.gen -d ../src/main/java Fast-DDS-$FASTDDS_VERSION/resources/xsd/fastdds_profiles.xsd

  echo "Converting JAXB annotations to Jackson annotations..."
  python3 ../convert_jaxb_to_jackson.py ../src/main/java/us/ihmc/fastddsjava/profiles/gen

  # Delete JAXB-specific files that are not needed with Jackson
  rm -f ../src/main/java/us/ihmc/fastddsjava/profiles/gen/ObjectFactory.java
  rm -f ../src/main/java/us/ihmc/fastddsjava/profiles/gen/package-info.java

  if command -v dos2unix &> /dev/null; then
    find "../src/main/java/us/ihmc/fastddsjava/profiles/gen" -type f -name "*.java" -exec dos2unix {} \;
  fi
else
    echo "xjc not found"
fi

popd
