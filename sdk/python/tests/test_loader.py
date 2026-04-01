"""Tests for loader module."""

import pytest
from pathlib import Path
from substrait_compliance import YamlTestSuiteLoader


def test_yaml_loader_supports():
    """Test YAML loader file detection."""
    loader = YamlTestSuiteLoader()
    
    assert loader.supports(Path("test.yaml"))
    assert loader.supports(Path("test.yml"))
    assert not loader.supports(Path("test.json"))
    assert not loader.supports(Path("test.txt"))
