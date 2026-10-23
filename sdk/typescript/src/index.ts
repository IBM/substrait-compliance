// Core interfaces
export { ComplianceEngine, EngineInfo, EngineCapabilities } from './engine';

// Data structures
export { TableData, Column, ColumnType, Row, CellValue, columnTypeFromName } from './table-data';

// Results
export { ComplianceResult, ComplianceReport, TestStatus } from './result';

// Test suite
export { TestSuite, TestSuiteMetadata, TestCase } from './test-suite';

// Loader
export { TestSuiteLoader, loadTestSuite, loadTestSuites } from './loader';

// Runner
export { ComplianceRunner, RunnerOptions, ResultComparator, numbersClose } from './runner';
export { declaredScales, declaredScalesBeside, outputTypes, namedTableSchemas, parsePlanJson, SchemaColumn } from './plan-types';
export { isSuiteMetadata, loadSuiteMetadata, loadInputTable, loadTypedCsv } from './suite-metadata';

