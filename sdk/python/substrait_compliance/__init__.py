"""
Substrait Compliance Testing SDK for Python

This package provides interfaces and utilities for engines to test
their Substrait compliance in a decentralized manner.
"""

from .engine import ComplianceEngine, EngineInfo, EngineCapabilities
from .runner import ComplianceRunner
from .result import ComplianceResult, ComplianceReport, TestStatus
from .test_suite import TestSuite, TestCase, TestSuiteMetadata
from .table_data import TableData, Column, DataType
from .loader import TestSuiteLoader, YamlTestSuiteLoader
from .exceptions import ComplianceException

__version__ = "1.0.0"

__all__ = [
    "ComplianceEngine",
    "EngineInfo",
    "EngineCapabilities",
    "ComplianceRunner",
    "ComplianceResult",
    "ComplianceReport",
    "TestStatus",
    "TestSuite",
    "TestCase",
    "TestSuiteMetadata",
    "TableData",
    "Column",
    "DataType",
    "TestSuiteLoader",
    "YamlTestSuiteLoader",
    "ComplianceException",
]
