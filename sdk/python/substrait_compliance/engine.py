"""Engine interface and metadata classes."""

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Dict, List, Optional
from .table_data import TableData
from .result import ComplianceResult


@dataclass
class EngineInfo:
    """Metadata about a Substrait engine."""
    
    name: str
    version: str
    vendor: str
    description: str = ""
    
    def __str__(self) -> str:
        return f"{self.name} {self.version} by {self.vendor}"


@dataclass
class EngineCapabilities:
    """Describes what Substrait features an engine supports."""
    
    supported_relations: List[str] = field(default_factory=list)
    supported_functions: List[str] = field(default_factory=list)
    supported_types: List[str] = field(default_factory=list)
    max_plan_depth: int = 100
    supports_extensions: bool = True
    
    def supports_relation(self, relation_type: str) -> bool:
        """Check if engine supports a relation type."""
        return relation_type in self.supported_relations
    
    def supports_function(self, function_name: str) -> bool:
        """Check if engine supports a function."""
        return function_name in self.supported_functions


class ComplianceEngine(ABC):
    """
    Interface that engines must implement for compliance testing.
    
    Engines implement this interface to participate in decentralized
    compliance testing.
    """
    
    @abstractmethod
    def get_info(self) -> EngineInfo:
        """Return engine metadata."""
        pass
    
    @abstractmethod
    def get_capabilities(self) -> EngineCapabilities:
        """Return engine capabilities."""
        pass
    
    @abstractmethod
    def execute_plan(
        self,
        plan_bytes: bytes,
        input_data: Dict[str, TableData]
    ) -> ComplianceResult:
        """
        Execute a Substrait plan with given input data.
        
        Args:
            plan_bytes: Binary Substrait plan
            input_data: Map of table name to table data
            
        Returns:
            ComplianceResult with execution outcome
        """
        pass
    
    @abstractmethod
    def validate_plan(self, plan_bytes: bytes) -> ComplianceResult:
        """
        Validate a Substrait plan without executing it.
        
        Args:
            plan_bytes: Binary Substrait plan
            
        Returns:
            ComplianceResult with validation outcome
        """
        pass
