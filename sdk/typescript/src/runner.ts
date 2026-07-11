import { promises as fs } from 'fs';
import { ComplianceEngine } from './engine';
import { TestSuite, TestCase } from './test-suite';
import { ComplianceResult, ComplianceReport, TestStatus } from './result';
import { TableData } from './table-data';

/**
 * Configuration options for the test runner
 */
export interface RunnerOptions {
  /** Stop execution on first failure */
  failFast?: boolean;
  /** Run tests in parallel */
  parallel?: boolean;
  /** Maximum number of parallel tests */
  maxParallel?: number;
  /** Filter tests by tag */
  tags?: string[];
  /** Filter tests by ID pattern */
  testIdPattern?: string;
  /** Verbose output */
  verbose?: boolean;
}

/**
 * Comparator for comparing expected and actual results
 */
export class ResultComparator {
  /**
   * Compare two TableData objects for equality
   */
  compare(expected: TableData, actual: TableData): boolean {
    // Check column count
    if (expected.columnCount() !== actual.columnCount()) {
      return false;
    }

    // Check row count
    if (expected.rowCount() !== actual.rowCount()) {
      return false;
    }

    // Check column definitions
    const expectedCols = expected.getColumns();
    const actualCols = actual.getColumns();
    for (let i = 0; i < expectedCols.length; i++) {
      if (expectedCols[i].name !== actualCols[i].name) {
        return false;
      }
      if (expectedCols[i].type !== actualCols[i].type) {
        return false;
      }
    }

    // Check row data
    const expectedRows = expected.getRows();
    const actualRows = actual.getRows();
    for (let i = 0; i < expectedRows.length; i++) {
      if (!this.compareRows(expectedRows[i], actualRows[i])) {
        return false;
      }
    }

    return true;
  }

  private compareRows(expected: any[], actual: any[]): boolean {
    if (expected.length !== actual.length) {
      return false;
    }

    for (let i = 0; i < expected.length; i++) {
      if (!this.compareValues(expected[i], actual[i])) {
        return false;
      }
    }

    return true;
  }

  private compareValues(expected: any, actual: any): boolean {
    // Handle null values
    if (expected === null && actual === null) return true;
    if (expected === null || actual === null) return false;

    // Handle NaN
    if (typeof expected === 'number' && typeof actual === 'number') {
      if (isNaN(expected) && isNaN(actual)) return true;
      if (isNaN(expected) || isNaN(actual)) return false;
    }

    // Handle dates
    if (expected instanceof Date && actual instanceof Date) {
      return expected.getTime() === actual.getTime();
    }

    // Handle floating point comparison with tolerance
    if (typeof expected === 'number' && typeof actual === 'number') {
      const tolerance = 1e-10;
      return Math.abs(expected - actual) < tolerance;
    }

    // Default comparison
    return expected === actual;
  }
}

/**
 * Test runner for executing compliance test suites
 */
export class ComplianceRunner {
  private engine: ComplianceEngine;
  private comparator: ResultComparator;
  private options: RunnerOptions;

  constructor(engine: ComplianceEngine, options: RunnerOptions = {}) {
    this.engine = engine;
    this.comparator = new ResultComparator();
    this.options = {
      failFast: false,
      parallel: false,
      maxParallel: 4,
      verbose: false,
      ...options,
    };
  }

  /**
   * Run a complete test suite
   */
  async runTestSuite(suite: TestSuite): Promise<ComplianceReport> {
    const report = new ComplianceReport();
    let testCases = suite.getActiveTestCases();

    // Filter by tags if specified
    if (this.options.tags && this.options.tags.length > 0) {
      testCases = testCases.filter((tc) =>
        this.options.tags!.some((tag) => tc.tags?.includes(tag))
      );
    }

    // Filter by test ID pattern if specified
    if (this.options.testIdPattern) {
      const pattern = new RegExp(this.options.testIdPattern);
      testCases = testCases.filter((tc) => pattern.test(tc.id));
    }

    if (this.options.parallel) {
      await this.runTestsParallel(testCases, report);
    } else {
      await this.runTestsSequential(testCases, report);
    }

    return report;
  }

  /**
   * Run a single test case
   */
  async runTestCase(testCase: TestCase): Promise<ComplianceResult> {
    try {
      // Read the plan file
      const planContent = await fs.readFile(testCase.plan);

      // Convert input data from Record to Map
      const inputMap = testCase.inputData
        ? new Map(Object.entries(testCase.inputData))
        : new Map<string, TableData>();

      // Execute the plan - engine returns ComplianceResult
      const result = await this.engine.executePlan(planContent, inputMap);

      // If we have expected output, compare it
      if (testCase.expectedOutput && result.outputData) {
        const matches = this.comparator.compare(testCase.expectedOutput, result.outputData);
        
        if (!matches && result.status === TestStatus.PASSED) {
          // Override status if output doesn't match
          return new ComplianceResult(
            testCase.id,
            TestStatus.FAILED,
            result.outputData,
            'Output does not match expected result',
            undefined,
            result.executionTimeMs
          );
        }
      }

      // Return result with correct test ID
      return new ComplianceResult(
        testCase.id,
        result.status,
        result.outputData,
        result.errorMessage,
        result.errorDetails,
        result.executionTimeMs
      );
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      const errorDetails = error instanceof Error ? error.stack : undefined;

      return new ComplianceResult(
        testCase.id,
        TestStatus.ERROR,
        undefined,
        errorMessage,
        errorDetails,
        0
      );
    }
  }

  private async runTestsSequential(
    testCases: TestCase[],
    report: ComplianceReport
  ): Promise<void> {
    for (const testCase of testCases) {
      if (this.options.verbose) {
        console.log(`Running test: ${testCase.id}`);
      }

      const result = await this.runTestCase(testCase);
      report.addResult(result);

      if (this.options.verbose) {
        console.log(`  Status: ${result.status}`);
        if (result.errorMessage) {
          console.log(`  Error: ${result.errorMessage}`);
        }
      }

      if (this.options.failFast && !result.isPassed()) {
        break;
      }
    }
  }

  private async runTestsParallel(
    testCases: TestCase[],
    report: ComplianceReport
  ): Promise<void> {
    const maxParallel = this.options.maxParallel || 4;
    const chunks: TestCase[][] = [];

    // Split test cases into chunks
    for (let i = 0; i < testCases.length; i += maxParallel) {
      chunks.push(testCases.slice(i, i + maxParallel));
    }

    // Execute chunks sequentially, tests within chunk in parallel
    for (const chunk of chunks) {
      const promises = chunk.map((tc) => this.runTestCase(tc));
      const results = await Promise.all(promises);

      for (const result of results) {
        report.addResult(result);

        if (this.options.verbose) {
          console.log(`Test ${result.testId}: ${result.status}`);
        }

        if (this.options.failFast && !result.isPassed()) {
          return;
        }
      }
    }
  }
}

