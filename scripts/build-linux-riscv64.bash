#!/bin/bash
# Build script for Linux RISC-V 64 (rv64gc, lp64d)
# This script cross-compiles native libraries for riscv64 Linux systems (needs g++-riscv64-linux-gnu).
# Release natives are built with: scripts/docker/build-natives.sh linux-riscv64 . <out-dir>

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

# Set Linux riscv64 cross-compilation
export LINUX_COMPILE_RISCV64=1

# Clean previous build
echo "Cleaning previous build..."
rm -rf cppbuild

# Run build
echo "Building for Linux riscv64..."
bash "$SCRIPT_DIR/cppbuild.bash"

echo ""
echo "Build complete! Libraries installed to:"
echo "  src/main/resources/fastddsjava/native/linux-riscv64/"
