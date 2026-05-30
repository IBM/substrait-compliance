# Substrait Compliance TypeScript/JavaScript SDK

A TypeScript/JavaScript SDK for implementing and testing Substrait compliance in query engines.

## Features

- 🎯 **Type-Safe Interface**: Full TypeScript support with comprehensive type definitions
- 🔄 **Async/Await**: Modern async patterns for all operations
- 📦 **Easy Integration**: Simple API for implementing compliance engines
- 🧪 **Test Runner**: Built-in test runner with parallel execution support
- 📊 **Rich Results**: Detailed test results and reporting
- 🔍 **YAML Support**: Load test suites from YAML files
- ⚡ **Performance**: Efficient execution with configurable parallelism

## Installation

```bash
npm install @substrait/compliance
```

Or with yarn:

```bash
yarn add @substrait/compliance
```

## Quick Start

### 1. Implement the ComplianceEngine Interface

```typescript
import {
  ComplianceEngine,
  EngineInfo,
  EngineCapabilities,
  ComplianceResult,
  TableData,
  TestStatus,
} from '@substrait/compliance';

class MyQueryEngine implements ComplianceEngine {
  getInfo(): EngineInfo {
    return {
      name: 'My Query Engine',
      version: '1.0.0',
      vendor: 'My Company',
      description: 'A high-performance query engine',
    };
  }

  getCapabilities(): EngineCapabilities {
    return {
      supportedRelations: ['read', 'filter', 'project', 'aggregate', 'join'],
      supportedFunctions: ['add', 'subtract', 'equal', 'greater_than'],
      supportedTypes: ['i32', 'i64', 'fp64', 'string', 'boolean'],
    };
  }

  async executePlan(
    planBytes: Uint8Array,
    inputData: Map<string, TableData>
  ): Promise<ComplianceResult> {
    try {
      // Parse and execute the Substrait plan
      const result = await this.executeSubstraitPlan(planBytes, inputData);
      
      return new ComplianceResult(
        'test-id',
        TestStatus.PASSED,
        result,
        undefined,
        undefined,
        executionTimeMs
      );
    } catch (error) {
      return new ComplianceResult(
        'test-id',
        TestStatus.ERROR,
        undefined,
        error.message,
        error.stack,
        0
      );
    }
  }

  async validatePlan(planBytes: Uint8Array): Promise<ComplianceResult> {
    // Validate plan structure without executing
    const isValid = await this.validateSubstraitPlan(planBytes);
    
    return new ComplianceResult(
      'validation',
      isValid ? TestStatus.PASSED : TestStatus.FAILED,
      undefined,
      isValid ? undefined : 'Invalid plan structure',
      undefined,
      0
    );
  }
}
```

### 2. Run Compliance Tests

```typescript
import { ComplianceRunner, loadTestSuite } from '@substrait/compliance';

async function runTests() {
  const engine = new MyQueryEngine();
  
  // Create a test runner
  const runner = new ComplianceRunner(engine, {
    verbose: true,
    parallel: true,
    maxParallel: 4,
  });

  // Load a test suite
  const suite = await loadTestSuite('./test-suites/functions/comparison/equal.test');

  // Run the tests
  const report = await runner.runTestSuite(suite);

  // Display results
  console.log(`Passed: ${report.passedCount()}/${report.totalCount()}`);
  console.log(`Pass Rate: ${report.passRate().toFixed(1)}%`);
  console.log(`Total Time: ${report.totalExecutionTimeMs()}ms`);
}

runTests();
```

## API Reference

### ComplianceEngine Interface

The main interface that query engines must implement.

#### Methods

##### `getInfo(): EngineInfo`

Returns metadata about the engine.

```typescript
interface EngineInfo {
  name: string;
  version: string;
  vendor: string;
  description?: string;
}
```

##### `getCapabilities(): EngineCapabilities`

Returns the engine's capabilities.

```typescript
interface EngineCapabilities {
  supportedRelations: string[];
  supportedFunctions: string[];
  supportedTypes: string[];
  extensions?: Record<string, string>;
}
```

##### `executePlan(planBytes: Uint8Array, inputData: Map<string, TableData>): Promise<ComplianceResult>`

Executes a Substrait plan and returns the result.

##### `validatePlan(planBytes: Uint8Array): Promise<ComplianceResult>`

Validates a Substrait plan without executing it.

##### Optional Methods

- `initialize?(): Promise<void>` - Initialize engine resources
- `shutdown?(): Promise<void>` - Cleanup engine resources
- `canRunTest?(testId: string): boolean` - Check if engine can handle a specific test

### TableData Class

Represents tabular data with schema and rows.

```typescript
import { TableData, Column, ColumnType } from '@substrait/compliance';

// Create a table
const columns: Column[] = [
  { name: 'id', type: ColumnType.INTEGER, nullable: false },
  { name: 'name', type: ColumnType.STRING, nullable: true },
  { name: 'score', type: ColumnType.FLOAT, nullable: true },
];

const table = new TableData(columns);

// Add rows
table.addRow([1, 'Alice', 95.5]);
table.addRow([2, 'Bob', 87.3]);
table.addRows([
  [3, 'Charlie', 92.1],
  [4, 'Diana', null],
]);

// Query data
console.log(table.rowCount());        // 4
console.log(table.columnCount());     // 3
console.log(table.getRow(0));         // [1, 'Alice', 95.5]
console.log(table.getCell(1, 1));     // 'Bob'
console.log(table.isEmpty());         // false

// Clear data
table.clear();
```

### ComplianceResult Class

Represents the result of a single test execution.

```typescript
class ComplianceResult {
  constructor(
    public readonly testId: string,
    public readonly status: TestStatus,
    public outputData?: TableData,
    public errorMessage?: string,
    public errorDetails?: string,
    public executionTimeMs: number = 0
  );

  isPassed(): boolean;
  isFailed(): boolean;
  isSkipped(): boolean;
  isError(): boolean;
  isUnsupported(): boolean;
}

enum TestStatus {
  PASSED = 'PASSED',
  FAILED = 'FAILED',
  SKIPPED = 'SKIPPED',
  ERROR = 'ERROR',
  UNSUPPORTED = 'UNSUPPORTED',
}
```

### ComplianceReport Class

Aggregated report for a test suite execution.

```typescript
const report = await runner.runTestSuite(suite);

// Get counts
console.log(report.totalCount());
console.log(report.passedCount());
console.log(report.failedCount());
console.log(report.errorCount());
console.log(report.skippedCount());
console.log(report.unsupportedCount());

// Get metrics
console.log(report.passRate());              // 0-100
console.log(report.totalExecutionTimeMs());
console.log(report.allPassed());             // boolean
console.log(report.summary());               // "Passed: 45/50 (90.0%)"

// Get individual results
for (const result of report.getResults()) {
  console.log(`${result.testId}: ${result.status}`);
}
```

### ComplianceRunner Class

Executes compliance test suites.

```typescript
const runner = new ComplianceRunner(engine, {
  failFast: false,        // Stop on first failure
  parallel: true,         // Run tests in parallel
  maxParallel: 4,         // Max parallel tests
  tags: ['arithmetic'],   // Filter by tags
  testIdPattern: '^add_', // Filter by test ID pattern
  verbose: true,          // Verbose output
});

// Run a single test suite
const report = await runner.runTestSuite(suite);

// Run a single test case
const result = await runner.runTestCase(testCase);
```

### Test Suite Loader

Load test suites from YAML files.

```typescript
import { loadTestSuite, loadTestSuites } from '@substrait/compliance';

// Load a single test suite
const suite = await loadTestSuite('./path/to/test.yaml');

// Load all test suites from a directory
const suites = await loadTestSuites('./test-suites/functions');

// Access test suite data
console.log(suite.metadata.name);
console.log(suite.metadata.version);
console.log(suite.totalCount());
console.log(suite.activeCount());
console.log(suite.skippedCount());

// Filter test cases
const arithmeticTests = suite.filterByTag('arithmetic');
const activeTests = suite.getActiveTestCases();
```

## Advanced Usage

### Custom Result Comparator

```typescript
import { ResultComparator } from '@substrait/compliance';

class CustomComparator extends ResultComparator {
  compare(expected: TableData, actual: TableData): boolean {
    // Custom comparison logic
    return super.compare(expected, actual);
  }
}

const runner = new ComplianceRunner(engine);
runner.comparator = new CustomComparator();
```

### Async Initialization

```typescript
class MyEngine implements ComplianceEngine {
  private connection: DatabaseConnection;

  async initialize(): Promise<void> {
    this.connection = await connectToDatabase();
  }

  async shutdown(): Promise<void> {
    await this.connection.close();
  }

  // ... other methods
}

const engine = new MyEngine();
await engine.initialize();

try {
  const runner = new ComplianceRunner(engine);
  const report = await runner.runTestSuite(suite);
} finally {
  await engine.shutdown();
}
```

### Selective Test Execution

```typescript
// Run only tests with specific tags
const runner = new ComplianceRunner(engine, {
  tags: ['arithmetic', 'comparison'],
});

// Run tests matching a pattern
const runner = new ComplianceRunner(engine, {
  testIdPattern: '^(add|subtract)_',
});

// Check if engine can run specific tests
class MyEngine implements ComplianceEngine {
  canRunTest(testId: string): boolean {
    // Skip tests that require unsupported features
    return !testId.includes('window_function');
  }
  
  // ... other methods
}
```

### Parallel Execution

```typescript
// Run tests in parallel with custom concurrency
const runner = new ComplianceRunner(engine, {
  parallel: true,
  maxParallel: 8,  // Run up to 8 tests concurrently
});

const report = await runner.runTestSuite(suite);
```

## Building and Testing

```bash
# Install dependencies
npm install

# Build the project
npm run build

# Run tests
npm test

# Run tests with coverage
npm run test:coverage

# Lint code
npm run lint

# Format code
npm run format
```

## Examples

See the [examples](./examples) directory for complete examples:

- `basic-engine.ts` - Basic engine implementation and usage

## TypeScript Configuration

The SDK is built with TypeScript and includes full type definitions. Your `tsconfig.json` should include:

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "module": "commonjs",
    "lib": ["ES2020"],
    "strict": true,
    "esModuleInterop": true
  }
}
```

## Contributing

Contributions are welcome! Please see the main project [CONTRIBUTING.md](../../CONTRIBUTING.md) for guidelines.

## License

Apache License 2.0 - See [LICENSE](../../LICENSE) for details.

## Support

- 📖 [Documentation](https://substrait.io)
- 💬 [Discussions](https://github.com/substrait-io/substrait-compliance/discussions)
- 🐛 [Issue Tracker](https://github.com/substrait-io/substrait-compliance/issues)