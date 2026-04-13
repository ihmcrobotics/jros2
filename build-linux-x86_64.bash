#!/bin/bash
# Build script for Linux x86_64 architecture
# This script builds native libraries for standard Linux desktop systems

set -e

# Clean previous build
echo "Cleaning previous build..."
rm -rf cppbuild

# Run build
echo "Building for Linux x86_64..."
bash cppbuild.bash

echo ""
echo "Build complete! Libraries installed to:"
echo "  src/main/resources/fastddsjava/native/linux-x86_64/"
