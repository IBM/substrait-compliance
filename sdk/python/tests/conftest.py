"""pytest configuration shared across all test modules.

Provides the REPO_ROOT fixture so integration tests can locate the real
test-suite files without hard-coding paths, and runs every test without the GitHub
Actions environment (see _no_github_actions_environment).
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


# Variables GitHub Actions sets for every step. Scripts under test write to them (the job
# summary, workflow commands), so a test run in CI would otherwise put its fixture results
# into the real job summary, e.g. made-up regression tables in the SDK Build Verification
# summary. A test that needs one sets it with monkeypatch.
GITHUB_ACTIONS_VARIABLES = ("GITHUB_ACTIONS", "GITHUB_STEP_SUMMARY", "GITHUB_OUTPUT", "GITHUB_ENV")


@pytest.fixture(autouse=True)
def _no_github_actions_environment(monkeypatch):
    for name in GITHUB_ACTIONS_VARIABLES:
        monkeypatch.delenv(name, raising=False)
