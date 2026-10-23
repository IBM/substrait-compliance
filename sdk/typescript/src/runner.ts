import { promises as fs } from 'fs';
import { ComplianceEngine } from './engine';
import { TestSuite, TestCase } from './test-suite';
import { ComplianceResult, ComplianceReport, TestStatus } from './result';
import { CellValue, Column, ColumnType, TableData } from './table-data';
import { declaredScalesBeside } from './plan-types';

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

/** Absolute tolerance near zero; relative tolerance for larger magnitudes. */
export const NUMERIC_ABS_TOLERANCE = 1e-9;
export const NUMERIC_REL_TOLERANCE = 1e-9;

/** The type family a column is compared by. */
function typeFamily(type: ColumnType): string {
  switch (type) {
    case ColumnType.INTEGER:
    case ColumnType.FLOAT:
    case ColumnType.DECIMAL:
      return 'numeric';
    case ColumnType.STRING:
    case ColumnType.DATE:
    case ColumnType.TIMESTAMP:
    case ColumnType.TIME:
      return 'string';
    default:
      return String(type).toLowerCase();
  }
}

/**
 * Numbers within max(1e-9, 1e-9 * magnitude) and, for a column the plan declares as decimal
 * with scale S, within 10^-S. NaN matches NaN and infinity only the same infinity.
 */
export function numbersClose(expected: number, actual: number, declaredScale: number | null = null): boolean {
  if (Number.isNaN(expected) || Number.isNaN(actual)) return Number.isNaN(expected) && Number.isNaN(actual);
  if (!Number.isFinite(expected) || !Number.isFinite(actual)) return expected === actual;
  const magnitude = Math.max(Math.abs(expected), Math.abs(actual));
  let tolerance = Math.max(NUMERIC_ABS_TOLERANCE, NUMERIC_REL_TOLERANCE * magnitude);
  if (declaredScale !== null && declaredScale !== undefined) {
    tolerance = Math.max(tolerance, Math.pow(10, -declaredScale) * (1 + NUMERIC_REL_TOLERANCE));
  }
  return Math.abs(expected - actual) <= tolerance;
}

function parseNumber(v: CellValue): number | null {
  if (typeof v === 'number') return v;
  if (typeof v === 'string' && v.trim() !== '') {
    const n = Number(v.trim());
    if (!Number.isNaN(n) || /^[+-]?nan$/i.test(v.trim())) return n;
  }
  return null;
}

/**
 * Compares an engine's output with the expected output, as the leaderboard's comparators do
 * (sdk/conformance/comparison-vectors.json): same row and column counts, column names
 * case-insensitively, column types by family (all numerics are one family; dates and times
 * compare as text), rows in order, numbers within max(1e-9, 1e-9 * magnitude), everything else
 * as text. NULL matches only NULL; a numeric string matches the number.
 */
export class ResultComparator {
  /** Whether the outputs match. */
  compare(expected: TableData, actual: TableData, declaredScales: (number | null)[] = []): boolean {
    return this.describeMismatch(expected, actual, declaredScales) === null;
  }

  /**
   * null when the outputs match, otherwise a description of the first difference.
   * declaredScales holds per column the scale of the decimal type the plan declares (null for
   * other columns); such a column also matches within 10^-scale.
   */
  describeMismatch(expected: TableData, actual: TableData, declaredScales: (number | null)[] = []): string | null {
    const eCols = expected.getColumns();
    const aCols = actual.getColumns();
    const names = (cols: Column[]) => `[${cols.map((c) => c.name).join(', ')}]`;
    if (expected.rowCount() !== actual.rowCount()) {
      return `expected ${expected.rowCount()} rows, got ${actual.rowCount()}`;
    }
    if (eCols.length !== aCols.length) {
      return `expected ${eCols.length} columns ${names(eCols)}, got ${aCols.length} ${names(aCols)}`;
    }
    if (eCols.some((c, i) => c.name.trim().toLowerCase() !== aCols[i].name.trim().toLowerCase())) {
      return `expected column names ${names(eCols)}, got ${names(aCols)}`;
    }
    if (eCols.some((c, i) => typeFamily(c.type) !== typeFamily(aCols[i].type))) {
      return `expected column types [${eCols.map((c) => c.type).join(', ')}], got [${aCols.map((c) => c.type).join(', ')}]`;
    }
    const eRows = expected.getRows();
    const aRows = actual.getRows();
    for (let r = 0; r < eRows.length; r++) {
      for (let c = 0; c < eRows[r].length && c < aRows[r].length; c++) {
        const scale = c < declaredScales.length ? declaredScales[c] : null;
        const numeric = typeFamily(eCols[c].type) === 'numeric';
        if (!this.valuesMatch(eRows[r][c], aRows[r][c], scale, numeric)) {
          return `row ${r}, column ${eCols[c].name}: expected ${show(eRows[r][c])}, got ${show(aRows[r][c])}`;
        }
      }
    }
    return null;
  }

  /**
   * Text is read as a number in a numeric column, or when the other value is a number, as the
   * Java runner does; so "001" and "1" in a text column stay different.
   */
  private valuesMatch(expected: CellValue, actual: CellValue, scale: number | null, numeric: boolean): boolean {
    if (expected === null || expected === undefined || actual === null || actual === undefined) {
      return (expected === null || expected === undefined) && (actual === null || actual === undefined);
    }
    if (typeof expected === 'boolean' || typeof actual === 'boolean') {
      return (String(expected).toLowerCase() === 'true') === (String(actual).toLowerCase() === 'true');
    }
    if (numeric || typeof expected === 'number' || typeof actual === 'number') {
      const e = parseNumber(expected);
      const a = parseNumber(actual);
      if (e !== null && a !== null) return numbersClose(e, a, scale);
    }
    if (expected instanceof Date && actual instanceof Date) return expected.getTime() === actual.getTime();
    return String(expected) === String(actual);
  }
}

function show(v: CellValue): string {
  if (v === null || v === undefined) return 'NULL';
  return typeof v === 'string' ? `'${v}'` : String(v);
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

      // No expected output — cannot verify correctness; return SKIPPED rather
      // than letting the engine's raw PASSED status flow through unchallenged.
      if (!testCase.expectedOutput) {
        return new ComplianceResult(
          testCase.id,
          TestStatus.SKIPPED,
          result.outputData,
          'No expected output — cannot verify correctness',
          undefined,
          result.executionTimeMs
        );
      }

      // If we have expected output, compare it
      if (result.outputData) {
        // Decimal columns compare at the scale the plan declares: from the test case, or from
        // the plan's JSON form beside its binary.
        const scales = testCase.declaredScales ?? declaredScalesBeside(testCase.plan) ?? [];
        const mismatch = this.comparator.describeMismatch(testCase.expectedOutput, result.outputData, scales);

        if (mismatch !== null && result.status === TestStatus.PASSED) {
          // Override status if output doesn't match
          return new ComplianceResult(
            testCase.id,
            TestStatus.FAILED,
            result.outputData,
            `Output mismatch: ${mismatch}`,
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

