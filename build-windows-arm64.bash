#!/bin/bash
# Build script for Windows ARM64
# Run from a bash shell with the MSVC ARM64 toolchain active
# (e.g. GitHub Actions: ilammy/msvc-dev-cmd with arch=amd64_arm64, or a native ARM64 VS prompt).

set -e

export WINDOWS_COMPILE_ARM64=1

echo "Building for Windows ARM64..."
bash cppbuild.bash

echo ""
echo "Build complete! Libraries installed to:"
echo "  src/main/resources/fastddsjava/native/windows-arm64/"
