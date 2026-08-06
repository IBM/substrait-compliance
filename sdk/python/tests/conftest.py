"""pytest configuration shared across all test modules.

Provides the REPO_ROOT fixture so integration tests can locate the real
test-suite files without hard-coding paths.
"""

import os
from pathlib import Path

import pytest


def _find_repo_root() -> Path:
    """Walk up from this file until we find the marker files."""
    candidate = Path(__file__).resolve().parent
    for _ in range(10):
        if (candidate / "test-suites").is_dir() and (candidate / "sdk").is_dir():
            return candidate
        candidate = candidate.parent
    raise RuntimeError(
        "Cannot locate repository root from %s" % Path(__file__).resolve()
    )


@pytest.fixture(scope="session")
def repo_root() -> Path:
    """Absolute path to the repository root directory."""
    return _find_repo_root()
