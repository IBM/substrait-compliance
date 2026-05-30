#!/bin/bash

# Install all SDK build tools
# This script installs Go, CMake, .NET, and SBT on macOS

set -e

echo "Installing SDK build tools..."
echo ""

# Check if Homebrew is installed
if ! command -v brew &> /dev/null; then
    echo "Error: Homebrew is not installed. Please install it first:"
    echo "/bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
    exit 1
fi

echo "✓ Homebrew is installed"
echo ""

# Install Go
echo "1. Installing Go..."
if command -v go &> /dev/null; then
    echo "✓ Go is already installed: $(go version)"
else
    brew install go
    echo "✓ Go installed successfully"
fi
echo ""

# Install CMake and build tools
echo "2. Installing CMake and C++ tools..."
if command -v cmake &> /dev/null; then
    echo "✓ CMake is already installed: $(cmake --version | head -n 1)"
else
    brew install cmake
    echo "✓ CMake installed successfully"
fi
echo ""

# Install .NET SDK
echo "3. Installing .NET SDK..."
if command -v dotnet &> /dev/null; then
    echo "✓ .NET is already installed: $(dotnet --version)"
else
    brew install --cask dotnet-sdk
    echo "✓ .NET SDK installed successfully"
fi
echo ""

# Install SBT (Scala Build Tool)
echo "4. Installing SBT..."
if command -v sbt &> /dev/null; then
    echo "✓ SBT is already installed"
else
    brew install sbt
    echo "✓ SBT installed successfully"
fi
echo ""

echo "════════════════════════════════════════"
echo "All SDK build tools installed successfully!"
echo "════════════════════════════════════════"
echo ""
echo "Installed tools:"
echo "  - Go: $(go version 2>/dev/null || echo 'Not found')"
echo "  - CMake: $(cmake --version 2>/dev/null | head -n 1 || echo 'Not found')"
echo "  - .NET: $(dotnet --version 2>/dev/null || echo 'Not found')"
echo "  - SBT: $(sbt --version 2>/dev/null | grep 'sbt version' || echo 'Not found')"
echo ""

# Made with Bob
