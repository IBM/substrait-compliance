import {
  ComplianceEngine,
  EngineInfo,
  EngineCapabilities,
  TableData,
  Column,
  ColumnType,
  ComplianceResult,
  TestStatus,
  ComplianceRunner,
  loadTestSuite,
} from '../src';

/**
 * Example implementation of a ComplianceEngine
 * This is a mock engine that demonstrates the interface
 */
class ExampleEngine implements ComplianceEngine {
  getInfo(): EngineInfo {
    return {
      name: 'Example Query Engine',
      version: '1.0.0',
      vendor: 'Example Corp',
      description: 'A simple example engine for demonstration',
    };
  }

  getCapabilities(): EngineCapabilities {
    return {
      supportedRelations: ['read', 'filter', 'project', 'aggregate'],
      supportedFunctions: ['add', 'subtract', 'multiply', 'divide', 'equal', 'greater_than'],
      supportedTypes: ['i32', 'i64', 'fp32', 'fp64', 'string', 'boolean'],
      extensions: {
        'custom_feature': 'enabled',
      },
    };
  }

  async executePlan(
    planBytes: Uint8Array,
    inputData: Map<string, TableData>
  ): Promise<ComplianceResult> {
    const startTime = Date.now();

    try {
      // In a real implementation, this would:
      // 1. Parse the Substrait plan (JSON or binary)
      // 2. Execute the plan against the input tables
      // 3. Return the result as ComplianceResult

      // For this example, we'll just return a simple result
      const columns: Column[] = [
        { name: 'id', type: ColumnType.INTEGER, nullable: false },
        { name: 'value', type: ColumnType.STRING, nullable: true },
      ];

      const outputData = new TableData(columns);
      outputData.addRow([1, 'example']);
      outputData.addRow([2, 'result']);

      const executionTime = Date.now() - startTime;

      return new ComplianceResult(
        'example-test',
        TestStatus.PASSED,
        outputData,
        undefined,
        undefined,
        executionTime
      );
    } catch (error) {
      const executionTime = Date.now() - startTime;
      const errorMessage = error instanceof Error ? error.message : String(error);
      const errorDetails = error instanceof Error ? error.stack : undefined;

      return new ComplianceResult(
        'example-test',
        TestStatus.ERROR,
        undefined,
        errorMessage,
        errorDetails,
        executionTime
      );
    }
  }

  async validatePlan(planBytes: Uint8Array): Promise<ComplianceResult> {
    try {
      // In a real implementation, this would validate the plan structure
      // For this example, we'll just check if the plan is not empty
      const isValid = planBytes.length > 0;

      if (isValid) {
        return new ComplianceResult(
          'validation',
          TestStatus.PASSED,
          undefined,
          undefined,
          undefined,
          0
        );
      } else {
        return new ComplianceResult(
          'validation',
          TestStatus.FAILED,
          undefined,
          'Plan is empty',
          undefined,
          0
        );
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      return new ComplianceResult(
        'validation',
        TestStatus.ERROR,
        undefined,
        errorMessage,
        undefined,
        0
      );
    }
  }
}

/**
 * Example usage of the SDK
 */
async function main() {
  console.log('=== Substrait Compliance SDK Example ===\n');

  // Create an instance of your engine
  const engine = new ExampleEngine();

  // Get engine information
  const info = engine.getInfo();
  console.log('Engine Info:');
  console.log(`  Name: ${info.name}`);
  console.log(`  Version: ${info.version}`);
  console.log(`  Vendor: ${info.vendor}`);
  console.log(`  Description: ${info.description}\n`);

  // Get engine capabilities
  const capabilities = engine.getCapabilities();
  console.log('Engine Capabilities:');
  console.log(`  Supported Relations: ${capabilities.supportedRelations.join(', ')}`);
  console.log(`  Supported Functions: ${capabilities.supportedFunctions.join(', ')}`);
  console.log(`  Supported Types: ${capabilities.supportedTypes.join(', ')}\n`);

  // Create a test runner
  const runner = new ComplianceRunner(engine, {
    verbose: true,
    parallel: false,
  });

  // Load and run a test suite
  try {
    const testSuitePath = process.argv[2] || '../../test-suites/functions/comparison/equal.test';
    console.log(`Loading test suite: ${testSuitePath}\n`);

    const suite = await loadTestSuite(testSuitePath);
    console.log(`Test Suite: ${suite.metadata.name}`);
    console.log(`Version: ${suite.metadata.version}`);
    console.log(`Total Tests: ${suite.totalCount()}\n`);

    console.log('Running tests...\n');
    const report = await runner.runTestSuite(suite);

    // Display results
    console.log('\n=== Test Results ===');
    console.log(`Total: ${report.totalCount()}`);
    console.log(`Passed: ${report.passedCount()}`);
    console.log(`Failed: ${report.failedCount()}`);
    console.log(`Errors: ${report.errorCount()}`);
    console.log(`Skipped: ${report.skippedCount()}`);
    console.log(`Pass Rate: ${report.passRate().toFixed(1)}%`);
    console.log(`Total Time: ${report.totalExecutionTimeMs()}ms`);

    // Show individual test results
    console.log('\nIndividual Results:');
    for (const result of report.getResults()) {
      const status = result.isPassed() ? '✓' : '✗';
      console.log(`  ${status} ${result.testId} (${result.executionTimeMs}ms)`);
      if (result.errorMessage) {
        console.log(`    Error: ${result.errorMessage}`);
      }
    }
  } catch (error) {
    console.error('Error running tests:', error);
    process.exit(1);
  }
}

// Run the example
if (require.main === module) {
  main().catch((error) => {
    console.error('Fatal error:', error);
    process.exit(1);
  });
}

export { ExampleEngine };

