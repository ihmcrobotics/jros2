#!/bin/bash
# Build script for Windows ARM64
# Run from a bash shell with the MSVC ARM64 toolchain active
# (e.g. GitHub Actions: ilammy/msvc-dev-cmd with arch=amd64_arm64, or a native ARM64 VS prompt).

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

export WINDOWS_COMPILE_ARM64=1

echo "Building for Windows ARM64..."
bash "$SCRIPT_DIR/cppbuild.bash"

echo ""
echo "Build complete! Libraries installed to:"
echo "  src/main/resources/fastddsjava/native/windows-arm64/"
