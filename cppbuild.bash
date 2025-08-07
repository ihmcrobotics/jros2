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
if [ "$LINUX_COMPILE_ARM64" == "1" ]; then
  COMPILER_ARGS="-DCMAKE_TOOLCHAIN_FILE=$INSTALL_DIR/../linux-aarch64-toolchain.cmake"
  JAVACPP_COMP_ARGS="-properties linux-arm64 -Dplatform.compiler=aarch64-linux-gnu-g++ -Dplatform.c.compiler=aarch64-linux-gnu-gcc -Dplatform=linux-arm64"
elif [ "$LINUX_COMPILE_ARMHF" == "1" ]; then
  COMPILER_ARGS="-DCMAKE_TOOLCHAIN_FILE=$INSTALL_DIR/../linux-armhf-toolchain.cmake"
  JAVACPP_COMP_ARGS="-properties linux-armhf -Dplatform.compiler=arm-linux-gnueabihf-g++ -Dplatform.c.compiler=arm-linux-gnueabihf-gcc -Dplatform=linux-armhf"
fi

# Build foonathan_memory_vendor
pushd .
cd foonathan_memory_vendor-$FOONATHAN_MEMORY_VENDOR_VERSION
mkdir -p build
cd build
cmake .. $COMPILER_ARGS -DCMAKE_INSTALL_PREFIX=$INSTALL_DIR/install
cmake --build . --config Release --target install
popd

# Build Fast-CDR
pushd .
cd Fast-CDR-$FASTCDR_VERSION
mkdir -p build
cd build
cmake .. $COMPILER_ARGS -DCMAKE_INSTALL_PREFIX=$INSTALL_DIR/install
cmake --build . --config Release --target install
popd

# Build Fast-DDS
pushd .
cd Fast-DDS-$FASTDDS_VERSION
git submodule update --init --recursive
mkdir -p build
cd build
cmake .. $COMPILER_ARGS -DQNX=OFF -DNO_TLS=ON -DSECURITY=OFF -DTHIRDPARTY_TinyXML2=FORCE -DTHIRDPARTY_Asio=FORCE -DCMAKE_INSTALL_PREFIX=$INSTALL_DIR/install
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
java -jar javacpp.jar us/ihmc/fastddsjava/pointers/*.java $JAVACPP_COMP_ARGS -d javainstall

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
popd

# xjc generation ###
pushd cppbuild

if command -v xjc >/dev/null 2>&1 && xjc; then
  xjc -no-header -p us.ihmc.fastddsjava.profiles.gen -d ../src/main/java Fast-DDS-$FASTDDS_VERSION/resources/xsd/fastdds_profiles.xsd

  find "../src/main/java/us/ihmc/fastddsjava/profiles/gen" -type f -name "*.java" -print0 | while IFS= read -r -d '' file; do
    # Replace javax.xml.* with jakarta.xml.*, but ignore javax.xml.namespace.QName
    # Replace @javax.xml.bind.annotation.* with @jakarta.xml.bind.annotation.*
    sed -i '
        /import javax\.xml\.namespace\.QName/!s/import javax\.xml\./import jakarta.xml./g
        s/@javax\.xml\.bind\.annotation\./@jakarta.xml.bind.annotation./g
        s/javax\.xml\.bind\.annotation\.XmlNsForm/jakarta.xml.bind.annotation.XmlNsForm/g
    ' "$file"

    if command -v dos2unix &> /dev/null; then
      dos2unix "$file"
    fi
  done
else
    echo "xjc not found"
fi

popd
