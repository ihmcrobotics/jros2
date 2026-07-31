#!/bin/bash
# Build script for Linux ARMHF architecture
# This script cross-compiles native libraries for ARMHF Linux systems (32-bit ARM)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

# Set Linux ARMHF cross-compilation
export LINUX_COMPILE_ARMHF=1

# Clean previous build
echo "Cleaning previous build..."
rm -rf cppbuild

# Run build
echo "Building for Linux ARMHF..."
bash "$SCRIPT_DIR/cppbuild.bash"

echo ""
echo "Build complete! Libraries installed to:"
echo "  src/main/resources/fastddsjava/native/linux-armhf/"
