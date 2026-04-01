"""Exception classes for compliance testing."""


class ComplianceException(Exception):
    """Base exception for compliance testing errors."""
    
    def __init__(self, message: str, cause: Exception = None):
        super().__init__(message)
        self.cause = cause
