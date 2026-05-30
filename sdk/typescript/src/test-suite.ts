import { TableData } from './table-data';

/**
 * Metadata about a test suite
 */
export interface TestSuiteMetadata {
  name: string;
  version: string;
  description?: string;
  category?: string;
  tags?: string[];
}

/**
 * A single test case within a test suite
 */
export interface TestCase {
  id: string;
  description?: string;
  plan: string; // Path to Substrait plan file (JSON or binary)
  inputData?: Record<string, TableData>; // Named input tables
  expectedOutput?: TableData;
  tags?: string[];
  skip?: boolean;
  skipReason?: string;
}

/**
 * Complete test suite definition
 */
export class TestSuite {
  constructor(
    public readonly metadata: TestSuiteMetadata,
    public readonly testCases: TestCase[]
  ) {}

  /**
   * Get test case by ID
   */
  getTestCase(id: string): TestCase | undefined {
    return this.testCases.find((tc) => tc.id === id);
  }

  /**
   * Get all test case IDs
   */
  getTestCaseIds(): string[] {
    return this.testCases.map((tc) => tc.id);
  }

  /**
   * Filter test cases by tag
   */
  filterByTag(tag: string): TestCase[] {
    return this.testCases.filter((tc) => tc.tags?.includes(tag));
  }

  /**
   * Get non-skipped test cases
   */
  getActiveTestCases(): TestCase[] {
    return this.testCases.filter((tc) => !tc.skip);
  }

  /**
   * Get skipped test cases
   */
  getSkippedTestCases(): TestCase[] {
    return this.testCases.filter((tc) => tc.skip);
  }

  /**
   * Get total number of test cases
   */
  totalCount(): number {
    return this.testCases.length;
  }

  /**
   * Get number of active test cases
   */
  activeCount(): number {
    return this.getActiveTestCases().length;
  }

  /**
   * Get number of skipped test cases
   */
  skippedCount(): number {
    return this.getSkippedTestCases().length;
  }
}

// Made with Bob
