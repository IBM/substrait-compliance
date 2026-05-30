import { TableData } from './table-data';

/**
 * Test execution status
 */
export enum TestStatus {
  PASSED = 'PASSED',
  FAILED = 'FAILED',
  SKIPPED = 'SKIPPED',
  ERROR = 'ERROR',
  UNSUPPORTED = 'UNSUPPORTED',
}

/**
 * Result of a single test case execution
 */
export class ComplianceResult {
  constructor(
    public readonly testId: string,
    public readonly status: TestStatus,
    public outputData?: TableData,
    public errorMessage?: string,
    public errorDetails?: string,
    public executionTimeMs: number = 0
  ) {}

  /**
   * Create a result with output data
   */
  withOutput(data: TableData): ComplianceResult {
    return new ComplianceResult(
      this.testId,
      this.status,
      data,
      this.errorMessage,
      this.errorDetails,
      this.executionTimeMs
    );
  }

  /**
   * Create a result with error message
   */
  withError(message: string): ComplianceResult {
    return new ComplianceResult(
      this.testId,
      this.status,
      this.outputData,
      message,
      this.errorDetails,
      this.executionTimeMs
    );
  }

  /**
   * Create a result with error details
   */
  withErrorDetails(details: string): ComplianceResult {
    return new ComplianceResult(
      this.testId,
      this.status,
      this.outputData,
      this.errorMessage,
      details,
      this.executionTimeMs
    );
  }

  /**
   * Create a result with execution time
   */
  withExecutionTime(ms: number): ComplianceResult {
    return new ComplianceResult(
      this.testId,
      this.status,
      this.outputData,
      this.errorMessage,
      this.errorDetails,
      ms
    );
  }

  /**
   * Check if test passed
   */
  isPassed(): boolean {
    return this.status === TestStatus.PASSED;
  }

  /**
   * Check if test failed
   */
  isFailed(): boolean {
    return this.status === TestStatus.FAILED;
  }

  /**
   * Check if test was skipped
   */
  isSkipped(): boolean {
    return this.status === TestStatus.SKIPPED;
  }

  /**
   * Check if test had an error
   */
  isError(): boolean {
    return this.status === TestStatus.ERROR;
  }

  /**
   * Check if test is unsupported
   */
  isUnsupported(): boolean {
    return this.status === TestStatus.UNSUPPORTED;
  }
}

/**
 * Aggregated report for a test suite execution
 */
export class ComplianceReport {
  private results: ComplianceResult[] = [];

  /**
   * Add a test result to the report
   */
  addResult(result: ComplianceResult): void {
    this.results.push(result);
  }

  /**
   * Get all results
   */
  getResults(): ComplianceResult[] {
    return this.results;
  }

  /**
   * Get total number of tests
   */
  totalCount(): number {
    return this.results.length;
  }

  /**
   * Get number of passed tests
   */
  passedCount(): number {
    return this.countByStatus(TestStatus.PASSED);
  }

  /**
   * Get number of failed tests
   */
  failedCount(): number {
    return this.countByStatus(TestStatus.FAILED);
  }

  /**
   * Get number of skipped tests
   */
  skippedCount(): number {
    return this.countByStatus(TestStatus.SKIPPED);
  }

  /**
   * Get number of error tests
   */
  errorCount(): number {
    return this.countByStatus(TestStatus.ERROR);
  }

  /**
   * Get number of unsupported tests
   */
  unsupportedCount(): number {
    return this.countByStatus(TestStatus.UNSUPPORTED);
  }

  /**
   * Calculate pass rate (0-100)
   */
  passRate(): number {
    if (this.results.length === 0) return 0;
    return (this.passedCount() / this.results.length) * 100;
  }

  /**
   * Get total execution time in milliseconds
   */
  totalExecutionTimeMs(): number {
    return this.results.reduce((sum, r) => sum + r.executionTimeMs, 0);
  }

  /**
   * Check if all tests passed
   */
  allPassed(): boolean {
    return this.passedCount() === this.totalCount();
  }

  /**
   * Get summary string
   */
  summary(): string {
    return `Passed: ${this.passedCount()}/${this.totalCount()} (${this.passRate().toFixed(1)}%)`;
  }

  private countByStatus(status: TestStatus): number {
    return this.results.filter((r) => r.status === status).length;
  }
}

// Made with Bob
