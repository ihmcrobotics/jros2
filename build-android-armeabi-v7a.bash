#!/bin/bash
# Build script for Android armeabi-v7a architecture
# This script sets all required environment variables and builds native libraries

set -e

# Find Android NDK (try common locations)
if [ -z "$ANDROID_NDK" ]; then
  if [ -d "$HOME/Android/Sdk/ndk" ]; then
    export ANDROID_NDK=$(ls -d $HOME/Android/Sdk/ndk/* 2>/dev/null | head -1)
  fi

  if [ -z "$ANDROID_NDK" ]; then
    echo "Error: ANDROID_NDK not found. Please set ANDROID_NDK environment variable."
    echo "Example: export ANDROID_NDK=/path/to/android-sdk/ndk/30.0.14904198"
    exit 1
  fi
fi

echo "Using Android NDK: $ANDROID_NDK"

# Set Android build configuration
export ANDROID_COMPILE=1
export ANDROID_ABI=armeabi-v7a
export ANDROID_API_LEVEL=26

# Clean previous build
echo "Cleaning previous build..."
rm -rf cppbuild

# Run build
echo "Building for Android armeabi-v7a..."
bash cppbuild.bash

echo ""
echo "Build complete! Libraries installed to:"
echo "  android/src/main/jniLibs/armeabi-v7a/"
