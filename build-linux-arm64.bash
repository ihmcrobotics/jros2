#!/bin/bash
# Build script for Linux ARM64 architecture
# This script cross-compiles native libraries for ARM64 Linux systems

set -e

# Set Linux ARM64 cross-compilation
export LINUX_COMPILE_ARM64=1

# Clean previous build
echo "Cleaning previous build..."
rm -rf cppbuild

# Run build
echo "Building for Linux ARM64..."
bash cppbuild.bash

echo ""
echo "Build complete! Libraries installed to:"
echo "  src/main/resources/fastddsjava/native/linux-arm64/"
