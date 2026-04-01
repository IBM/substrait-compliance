"""Tests for engine module."""

import pytest
from substrait_compliance import (
    EngineInfo,
    EngineCapabilities,
    ComplianceEngine,
    ComplianceResult,
    TestStatus
)


def test_engine_info():
    """Test EngineInfo creation."""
    info = EngineInfo(
        name="TestEngine",
        version="1.0.0",
        vendor="TestVendor",
        description="Test engine"
    )
    
    assert info.name == "TestEngine"
    assert info.version == "1.0.0"
    assert str(info) == "TestEngine 1.0.0 by TestVendor"


def test_engine_capabilities():
    """Test EngineCapabilities."""
    caps = EngineCapabilities(
        supported_relations=["read", "filter"],
        supported_functions=["add", "subtract"]
    )
    
    assert caps.supports_relation("read")
    assert caps.supports_relation("filter")
    assert not caps.supports_relation("join")
    
    assert caps.supports_function("add")
    assert not caps.supports_function("multiply")


class MockEngine(ComplianceEngine):
    """Mock engine for testing."""
    
    def get_info(self):
        return EngineInfo("Mock", "1.0", "Test")
    
    def get_capabilities(self):
        return EngineCapabilities()
    
    def execute_plan(self, plan_bytes, input_data):
        return ComplianceResult("test", TestStatus.PASSED)
    
    def validate_plan(self, plan_bytes):
        return ComplianceResult("test", TestStatus.PASSED)


def test_mock_engine():
    """Test mock engine implementation."""
    engine = MockEngine()
    
    info = engine.get_info()
    assert info.name == "Mock"
    
    result = engine.execute_plan(b"", {})
    assert result.is_success()
