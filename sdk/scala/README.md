# Substrait Compliance Framework - Scala SDK

A comprehensive Scala SDK for testing Substrait plan compliance across different query engines. This SDK provides a functional, type-safe interface with Future-based async support for running compliance tests.

## Features

- **Functional Design**: Immutable case classes and functional programming patterns
- **Type Safety**: Strong typing with sealed traits and pattern matching
- **Async Support**: Future-based asynchronous execution with ExecutionContext
- **Parallel Execution**: Configurable concurrent test execution
- **YAML Support**: Load test suites from YAML files using Circe
- **Comprehensive Testing**: ScalaTest integration with async test support
- **Idiomatic Scala**: Follows Scala best practices and conventions

## Requirements

- Scala 2.13.x or 3.x
- SBT 1.9.x or later
- JDK 11 or later

## Installation

Add the following to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "io.substrait" %% "substrait-compliance" % "1.0.0",
  "io.circe" %% "circe-core" % "0.14.6",
  "io.circe" %% "circe-generic" % "0.14.6",
  "io.circe" %% "circe-parser" % "0.14.6",
  "io.circe" %% "circe-yaml" % "0.15.1",
  "org.typelevel" %% "cats-core" % "2.10.0"
)
```

## Quick Start

### 1. Implement the ComplianceEngine Trait

```scala
import io.substrait.compliance._
import scala.concurrent.{Future, ExecutionContext}

class MyEngine(implicit ec: ExecutionContext) extends ComplianceEngine {
  
  override def getInfo(): EngineInfo = {
    EngineInfo(
      name = "My Query Engine",
      version = "1.0.0",
      description = Some("My custom query engine")
    )
  }

  override def getCapabilities(): EngineCapabilities = {
    EngineCapabilities(
      supportedFormats = Set("json", "binary"),
      supportedFunctions = Set("add", "subtract", "multiply", "divide"),
      supportedTypes = Set("i32", "i64", "fp32", "fp64", "string"),
      maxParallelism = 4
    )
  }

  override def executePlan(
    plan: Array[Byte],
    inputTables: Map[String, TableData]
  ): Future[TableData] = {
    Future {
      // Parse and execute the Substrait plan
      // Return the result as TableData
      ???
    }
  }

  override def validatePlan(plan: Array[Byte]): Future[ValidationResult] = {
    Future {
      // Validate the Substrait plan
      ValidationResult(
        isValid = true,
        errors = Seq.empty,
        warnings = Seq.empty
      )
    }
  }
}
```

### 2. Run Compliance Tests

```scala
import io.substrait.compliance._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._

object Main {
  def main(args: Array[String]): Unit = {
    // Create engine instance
    val engine = new MyEngine()

    // Configure runner
    val config = RunnerConfig(
      parallelism = 4,
      verbose = true,
      tags = Set("basic", "arithmetic")
    )

    // Create runner
    val runner = ComplianceRunner(engine, config)

    // Run tests from directory
    val resultFuture = runner.runFromDirectory("test-suites/functions", recursive = true)

    // Wait for results
    val result = Await.result(resultFuture, 5.minutes)

    // Print summary
    println(result.summary)
    println(result.detailedReport)

    // Exit with appropriate code
    System.exit(if (result.allPassed) 0 else 1)
  }
}
```

## Core Concepts

### ComplianceEngine

The main trait that your engine must implement:

```scala
trait ComplianceEngine {
  def getInfo(): EngineInfo
  def getCapabilities(): EngineCapabilities
  def executePlan(plan: Array[Byte], inputTables: Map[String, TableData]): Future[TableData]
  def validatePlan(plan: Array[Byte]): Future[ValidationResult]
}
```

### TableData

Represents tabular data with schema and rows:

```scala
// Create a table with columns
val table = TableData(
  columns = Seq(
    Column("id", ColumnType.Integer),
    Column("name", ColumnType.String),
    Column("value", ColumnType.Float)
  )
)

// Add rows
table.addRow(Seq(1, "Alice", 100.5))
table.addRow(Seq(2, "Bob", 200.75))

// Access data
val rowCount = table.rowCount
val firstRow = table.getRow(0)
val cell = table.getCell(0, 1)

// Functional operations
val mapped = table.mapRows(row => row.head)
val filtered = table.filterRows(row => row(2).asInstanceOf[Double] > 150)
```

### TestSuite and TestCase

Define test suites using case classes:

```scala
val testSuite = TestSuite(
  name = "Arithmetic Tests",
  description = Some("Basic arithmetic operations"),
  testCases = Seq(
    TestCase(
      name = "Addition Test",
      description = Some("Test addition of two integers"),
      planPath = "plans/add.bin",
      tags = Set("arithmetic", "basic")
    ),
    TestCase(
      name = "Multiplication Test",
      planPath = "plans/multiply.bin",
      tags = Set("arithmetic")
    )
  )
)
```

### Loading Test Suites from YAML

```scala
import io.substrait.compliance.TestSuiteLoader

// Load single suite
val suite = TestSuiteLoader.loadFromFile("test-suite.yaml").get

// Load multiple suites from directory
val suites = TestSuiteLoader.loadFromDirectory("test-suites", recursive = true).get
```

Example YAML format:

```yaml
name: "Arithmetic Functions"
description: "Test suite for arithmetic operations"
version: "1.0"
test_cases:
  - name: "Add Integers"
    description: "Test addition of two integers"
    plan_path: "plans/add_i32.bin"
    tags:
      - arithmetic
      - basic
  - name: "Multiply Floats"
    plan_path: "plans/multiply_fp64.bin"
    tags:
      - arithmetic
      - floating_point
```

## Configuration

### RunnerConfig

Configure test execution behavior:

```scala
val config = RunnerConfig(
  parallelism = 4,              // Number of parallel test executions
  stopOnFirstFailure = false,   // Stop on first failure
  verbose = true,               // Print detailed output
  tags = Set("basic"),          // Only run tests with these tags
  excludeTags = Set("slow")     // Exclude tests with these tags
)
```

## Advanced Usage

### Custom Test Suite Builder

```scala
import io.substrait.compliance.TestSuiteBuilder

val suite = TestSuiteBuilder("My Suite")
  .withDescription("Custom test suite")
  .withVersion("2.0")
  .addTestCase(TestCase("Test 1", planPath = "plan1.bin"))
  .addTestCase(TestCase("Test 2", planPath = "plan2.bin"))
  .addMetadata("author", "John Doe")
  .build()
```

### Filtering Tests by Tags

```scala
// Filter by single tag
val basicTests = testSuite.filterByTag("basic")

// Filter by all tags (AND)
val filteredTests = testSuite.filterByAllTags(Set("arithmetic", "basic"))

// Filter by any tag (OR)
val anyTests = testSuite.filterByAnyTag(Set("arithmetic", "string"))
```

### Handling Results

```scala
val resultFuture = runner.runTestSuite(testSuite)

resultFuture.map { result =>
  println(s"Total tests: ${result.totalTests}")
  println(s"Passed: ${result.passedTests}")
  println(s"Failed: ${result.failedTests}")
  println(s"Pass rate: ${result.passRate}%")
  
  // Get failed tests
  val failures = result.testResults.filter(_.isFailed)
  failures.foreach { test =>
    println(s"Failed: ${test.testName}")
    test.message.foreach(msg => println(s"  Message: $msg"))
    test.error.foreach(e => println(s"  Error: ${e.getMessage}"))
  }
}
```

### Async Patterns

The SDK uses Scala Futures for async operations:

```scala
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// Sequential execution
for {
  validation <- engine.validatePlan(plan)
  result <- if (validation.isValid) {
    engine.executePlan(plan, inputTables)
  } else {
    Future.failed(new RuntimeException("Invalid plan"))
  }
} yield result

// Parallel execution
val futures = testCases.map { testCase =>
  engine.executePlan(testCase.plan, testCase.inputs)
}
Future.sequence(futures)
```

## Testing

The SDK includes comprehensive ScalaTest tests:

```scala
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

class MyEngineSpec extends AsyncFlatSpec with Matchers {
  
  "MyEngine" should "execute plans correctly" in {
    val engine = new MyEngine()
    val plan = Array[Byte](1, 2, 3)
    val inputs = Map.empty[String, TableData]
    
    engine.executePlan(plan, inputs).map { result =>
      result shouldBe a[TableData]
      result.rowCount should be > 0
    }
  }
}
```

Run tests with:

```bash
sbt test
```

## Building

```bash
# Compile
sbt compile

# Run tests
sbt test

# Create JAR
sbt package

# Create assembly (fat JAR)
sbt assembly

# Run example
sbt "runMain io.substrait.compliance.examples.ExampleUsage"
```

## Project Structure

```
sdk/scala/
├── build.sbt                          # SBT build configuration
├── project/
│   └── build.properties               # SBT version
├── src/
│   ├── main/
│   │   └── scala/
│   │       └── io/substrait/compliance/
│   │           ├── ComplianceEngine.scala      # Core trait
│   │           ├── TableData.scala             # Data structures
│   │           ├── ComplianceResult.scala      # Result types
│   │           ├── TestSuite.scala             # Test definitions
│   │           ├── TestSuiteLoader.scala       # YAML loader
│   │           ├── ComplianceRunner.scala      # Test runner
│   │           └── examples/
│   │               └── ExampleEngine.scala     # Example implementation
│   └── test/
│       └── scala/
│           └── io/substrait/compliance/
│               ├── ComplianceEngineSpec.scala  # Engine tests
│               └── TableDataSpec.scala         # Data structure tests
└── README.md
```

## Best Practices

1. **Use Immutable Data**: Leverage case classes and immutable collections
2. **Handle Errors Properly**: Use Try, Either, or Future for error handling
3. **Provide ExecutionContext**: Always pass implicit ExecutionContext for async operations
4. **Use Pattern Matching**: Leverage sealed traits and pattern matching for type safety
5. **Test Thoroughly**: Write comprehensive tests using ScalaTest
6. **Document Code**: Use ScalaDoc comments for public APIs

## Examples

### Complete Example with Error Handling

```scala
import io.substrait.compliance._
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Try, Success, Failure}

class RobustEngine(implicit ec: ExecutionContext) extends ComplianceEngine {
  
  override def executePlan(
    plan: Array[Byte],
    inputTables: Map[String, TableData]
  ): Future[TableData] = {
    Future {
      Try {
        // Your plan execution logic
        TableData.empty
      } match {
        case Success(result) => result
        case Failure(e) => throw new RuntimeException(s"Execution failed: ${e.getMessage}", e)
      }
    }
  }
  
  // ... other methods
}

// Usage with error handling
val engine = new RobustEngine()
val runner = ComplianceRunner(engine)

runner.runFromDirectory("test-suites").onComplete {
  case Success(result) =>
    println(result.summary)
    if (!result.allPassed) {
      System.exit(1)
    }
  case Failure(e) =>
    System.err.println(s"Test execution failed: ${e.getMessage}")
    System.exit(2)
}
```

## Contributing

Contributions are welcome! Please ensure:

1. Code follows Scala style guidelines
2. All tests pass (`sbt test`)
3. New features include tests
4. Documentation is updated

## License

Apache License 2.0

## Support

For issues and questions:
- GitHub Issues: https://github.com/substrait-io/substrait-compliance/issues
- Documentation: https://substrait.io/

## Related Projects

- [Substrait](https://substrait.io/) - Cross-language serialization for relational algebra
- [Substrait Java](https://github.com/substrait-io/substrait-java) - Java implementation
- Other SDK implementations: C++, Go, TypeScript, C#