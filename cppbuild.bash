#!/bin/bash
# This build script is designed to work on Linux and Windows. For Windows, run from a bash shell launched with launchBashWindows.bat

# Clean
rm -rf cppbuild/us
find src/main/java/us/ihmc/fastdds -maxdepth 1 -type f -not \( -name "fastddsConfig.java" \) -delete

pushd .
mkdir -p cppbuild
cd cppbuild

FOONATHAN_MEMORY_VENDOR_VERSION=1.3.1
if [ ! -f "foonathan_memory_vendor-$FOONATHAN_MEMORY_VENDOR_VERSION.tar.gz" ]; then
  curl -o foonathan_memory_vendor-$FOONATHAN_MEMORY_VENDOR_VERSION.tar.gz https://codeload.github.com/eProsima/foonathan_memory_vendor/tar.gz/refs/tags/v$FOONATHAN_MEMORY_VENDOR_VERSION
fi
tar -xvf foonathan_memory_vendor-$FOONATHAN_MEMORY_VENDOR_VERSION.tar.gz

FASTCDR_VERSION=2.2.6
if [ ! -f "Fast-CDR-$FASTCDR_VERSION.tar.gz" ]; then
  curl -o Fast-CDR-$FASTCDR_VERSION.tar.gz https://codeload.github.com/eProsima/Fast-CDR/tar.gz/refs/tags/v$FASTCDR_VERSION
fi
tar -xvf Fast-CDR-$FASTCDR_VERSION.tar.gz

FASTDDS_VERSION=3.1.2
if [ ! -f "Fast-DDS-$FASTDDS_VERSION.tar.gz" ]; then
  curl -o Fast-DDS-$FASTDDS_VERSION.tar.gz https://codeload.github.com/eProsima/Fast-DDS/tar.gz/refs/tags/v$FASTDDS_VERSION
fi
tar -xvf Fast-DDS-$FASTDDS_VERSION.tar.gz

INSTALL_DIR=$(pwd)

# Build foonathan_memory_vendor
pushd .
cd foonathan_memory_vendor-$FOONATHAN_MEMORY_VENDOR_VERSION
mkdir -p build
cd build
cmake .. -DCMAKE_INSTALL_PREFIX=$INSTALL_DIR/install
cmake --build . --target install
popd

# Build Fast-CDR
pushd .
cd Fast-CDR-$FASTCDR_VERSION
mkdir -p build
cd build
cmake .. -DCMAKE_INSTALL_PREFIX=$INSTALL_DIR/install
cmake --build . --target install
popd

# Build Fast-DDS
pushd .
cd Fast-DDS-$FASTDDS_VERSION
mkdir -p build
cd build
cmake .. -DCMAKE_INSTALL_PREFIX=$INSTALL_DIR/install
cmake --build . --target install -j $(nproc)
popd

rm -rf install/include/fastddsjava.h
cp ../src/native/fastddsjava.h install/include/fastddsjava.h

popd

### Java generation ####
cd cppbuild

cp -r ../src/main/java/* .

JAVACPP_VERSION=1.5.11
if [ ! -f javacpp.jar ]; then
  curl -L https://github.com/bytedeco/javacpp/releases/download/$JAVACPP_VERSION/javacpp-platform-$JAVACPP_VERSION-bin.zip -o javacpp-platform-$JAVACPP_VERSION-bin.zip
  unzip -j javacpp-platform-$JAVACPP_VERSION-bin.zip
fi

java -cp "javacpp.jar" org.bytedeco.javacpp.tools.Builder us/ihmc/fastdds/fastddsConfig.java

cp us/ihmc/fastdds/*.java ../src/main/java/us/ihmc/fastdds
cp us/ihmc/fastdds/global/*.java ../src/main/java/us/ihmc/fastdds/global/

#### JNI compilation ####
java -cp "javacpp.jar" org.bytedeco.javacpp.tools.Builder us/ihmc/fastdds/*.java us/ihmc/fastdds/global/*.java -d javainstall

##### Copy shared libs to resources ####
# Linux
mkdir -p ../src/main/resources/fastdds/native/linux-x86_64
if [ -f "install/lib/libfastcdr.so.2.2.6" ]; then
  cp install/lib/libfastcdr.so.2.2.6 ../src/main/resources/fastdds/native/linux-x86_64
fi
if [ -f "install/lib/libfastdds.so.3.1.2" ]; then
  cp install/lib/libfastdds.so.3.1.2 ../src/main/resources/fastdds/native/linux-x86_64
fi
if [ -f "install/lib/libfoonathan_memory-0.7.3.a" ]; then
  cp install/lib/libfoonathan_memory-0.7.3.a ../src/main/resources/fastdds/native/linux-x86_64
fi
if [ -f "javainstall/libjnifastdds.so" ]; then
  cp javainstall/libjnifastdds.so ../src/main/resources/fastdds/native/linux-x86_64
fi