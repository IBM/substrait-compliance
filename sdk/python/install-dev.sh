#!/usr/bin/env bash
# Install the Python SDK and its dev dependencies (editable local install).
#
# Why this script exists:
#   pip reads a global/user pip.conf before every install. On machines
#   configured to use a private or corporate package mirror the PEP 517
#   build-backend bootstrap subprocess hits that mirror — and fails if the
#   mirror is unreachable (e.g. off VPN, or in a CI environment without
#   network access to the mirror).
#
#   PIP_INDEX_URL overrides pip.conf for all subprocesses pip spawns.
#   We export it before every pip call so the build-backend bootstrap,
#   transitive-dependency resolution, and wheel fetches all use PyPI.
#   The package itself is installed as an editable local source tree
#   (pip install -e), not downloaded from PyPI.
#
# Usage (from sdk/python/):
#   python3 -m venv venv
#   source venv/bin/activate
#   ./install-dev.sh
#   pytest tests/ -v

set -euo pipefail

export PIP_INDEX_URL="https://pypi.org/simple"

echo "📦 Seeding build tools (setuptools, wheel) from PyPI..."
pip install --upgrade pip setuptools wheel

echo "📦 Installing substrait-compliance[dev] as editable local install..."
pip install -e ".[dev]"

echo ""
echo "✅ Installation complete. Run the tests with:"
echo "   pytest tests/ -v"
