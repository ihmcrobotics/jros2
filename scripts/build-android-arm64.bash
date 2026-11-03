#!/bin/bash
# Build script for Android ARM64-v8a architecture
# This script sets all required environment variables and builds native libraries

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

# Find Android NDK (try common locations)
if [ -z "${ANDROID_NDK:-}" ]; then
  if [ -d "$HOME/Android/Sdk/ndk" ]; then
    # Newest installed NDK (r28+ is needed for 16 KB page-size compatible arm64-v8a/x86_64 libraries)
    ANDROID_NDK=$(find "$HOME/Android/Sdk/ndk" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)
    export ANDROID_NDK
  fi

  if [ -z "${ANDROID_NDK:-}" ]; then
    echo "Error: ANDROID_NDK not found. Please set ANDROID_NDK environment variable."
    echo "Example: export ANDROID_NDK=/path/to/android-sdk/ndk/30.0.16248370"
    exit 1
  fi
fi

echo "Using Android NDK: $ANDROID_NDK"

# Set Android build configuration
export ANDROID_COMPILE=1
export ANDROID_ABI=arm64-v8a
export ANDROID_API_LEVEL=26

# Clean previous build
echo "Cleaning previous build..."
rm -rf cppbuild

# Run build
echo "Building for Android ARM64-v8a..."
bash "$SCRIPT_DIR/cppbuild.bash"

echo ""
echo "Build complete! Libraries installed to:"
echo "  android/src/main/jniLibs/arm64-v8a/"
