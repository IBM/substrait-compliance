# DataFusion Rust Reference Implementation

Production-ready reference implementation demonstrating Apache DataFusion integration with the Substrait Compliance Framework using native Substrait support.

## Overview

This implementation showcases:
- **Native Substrait Support**: Uses DataFusion's built-in Substrait consumer
- **Async/Await**: Modern Rust async runtime with Tokio
- **Zero-Copy**: Efficient Arrow-based data processing
- **Type Safety**: Leverages Rust's type system for correctness
- **Production Quality**: Comprehensive error handling and performance optimization

## Features

### Supported Operations
- **Relations**: read, filter, project, aggregate, join, sort, limit, union, cross, fetch, window
- **Functions**: 40+ scalar and aggregate functions including arithmetic, comparison, string, date/time, array operations
- **Types**: All Substrait primitive types plus complex types (struct, list, map)
- **Extensions**: Full support for custom extensions

### Key Capabilities
- Direct Substrait plan execution via DataFusion's `from_substrait_plan()`
- Automatic type mapping between Substrait and Arrow types
- Efficient data loading using Arrow RecordBatch
- Comprehensive error handling with Result types
- Async execution for non-blocking operations

## Building

### Prerequisites
```bash
# Install Rust (if not already installed)
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# Verify installation
rustc --version
cargo --version
```

### Compile
```bash
# Build the library and example
cargo build --release

# Run tests
cargo test

# Run benchmarks
cargo bench
```

This produces:
- `target/release/libdatafusion_substrait_compliance.rlib` - Library
- `target/release/datafusion-compliance-example` - Example executable

## Usage

### Basic Example
```bash
# Run with sample data
cargo run --release

# Run with a Substrait plan
cargo run --release -- path/to/plan.substrait
```

### Programmatic Usage
```rust
use datafusion_substrait_compliance::{
    DataFusionComplianceEngine, TableData, Value
};
use std::collections::HashMap;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Create engine
    let mut engine = DataFusionComplianceEngine::new();

    // Get engine info
    let info = engine.get_engine_info();
    println!("Engine: {} {}", info.name, info.version);

    // Prepare input data
    let mut input_data = HashMap::new();
    input_data.insert("orders".to_string(), create_orders_table());

    // Load and execute plan
    let plan = load_plan_from_file("query.substrait")?;
    let result = engine.execute_plan(&plan, input_data).await;

    if result.success {
        println!("Query executed in {} ms", result.duration_ms);
        if let Some(output) = result.output {
            print_results(&output);
        }
    } else {
        eprintln!("Error: {:?}", result.error);
    }

    Ok(())
}
```

## Architecture

### Module Structure
```
DataFusionComplianceEngine
├── get_engine_info()        - Return engine metadata
├── get_capabilities()       - Declare supported features
├── execute_plan()           - Execute Substrait plans (async)
└── validate_plan()          - Validate plan structure

Private Methods:
├── load_input_data()        - Load test data into DataFusion
├── table_data_to_record_batch() - Convert to Arrow format
├── build_array_from_column() - Build typed Arrow arrays
├── convert_results_to_table_data() - Convert Arrow results
└── map_substrait_type_to_arrow() - Type system mapping
```

### Execution Flow
```
1. Initialize DataFusion SessionContext
2. Convert TableData to Arrow RecordBatch
3. Register tables in SessionContext
4. Parse Substrait plan using from_substrait_plan()
5. Execute logical plan asynchronously
6. Collect results as RecordBatch
7. Convert back to TableData
8. Return ComplianceResult with timing
```

## Type Mapping

| Substrait Type | Arrow Type | Rust Type |
|----------------|------------|-----------|
| i8             | Int8       | i8        |
| i16            | Int16      | i16       |
| i32            | Int32      | i32       |
| i64            | Int64      | i64       |
| u8             | UInt8      | u8        |
| u16            | UInt16     | u16       |
| u32            | UInt32     | u32       |
| u64            | UInt64     | u64       |
| fp32           | Float32    | f32       |
| fp64           | Float64    | f64       |
| string         | Utf8       | String    |
| binary         | Binary     | Vec<u8>   |
| boolean        | Boolean    | bool      |

## Performance Considerations

### Optimizations
- **Zero-Copy**: Arrow columnar format eliminates serialization overhead
- **Vectorized Execution**: SIMD operations on columnar data
- **Async Runtime**: Non-blocking I/O with Tokio
- **Memory Pooling**: Efficient memory management via Arrow
- **Lazy Evaluation**: Query optimization before execution

### Benchmarks
Typical performance on modern hardware:
- Simple queries: < 5ms
- Complex joins: 20-100ms
- Large aggregations: 50-300ms
- Window functions: 100-500ms

Run benchmarks:
```bash
cargo bench
```

## Error Handling

The implementation uses Rust's Result type for comprehensive error handling:

```rust
pub enum ComplianceError {
    PlanValidation(String),
    Execution(String),
    DataConversion(String),
    UnsupportedOperation(String),
}
```

Example error output:
```
Execution failed: Plan validation failed
  - Unsupported relation type: custom_scan
  - Function not found: custom_aggregate
```

## Testing

### Unit Tests
```bash
# Run all tests
cargo test

# Run with output
cargo test -- --nocapture

# Run specific test
cargo test test_execute_plan
```

### Integration Tests
```bash
# Run against test suite
cargo run --release -- ../../test-suites/functions/arithmetic/add.substrait
```

## Async/Await

DataFusion operations are async, requiring a Tokio runtime:

```rust
#[tokio::main]
async fn main() {
    let mut engine = DataFusionComplianceEngine::new();
    let result = engine.execute_plan(&plan, input_data).await;
}
```

For synchronous contexts, use `tokio::runtime::Runtime`:

```rust
let rt = tokio::runtime::Runtime::new().unwrap();
let result = rt.block_on(engine.execute_plan(&plan, input_data));
```

## Limitations

Current limitations:
- **Streaming**: Results are materialized (not streamed)
- **Custom UDFs**: User-defined functions require registration
- **Extensions**: Some advanced extensions may not be supported
- **Memory**: Large result sets are held in memory

## Future Enhancements

Planned improvements:
1. Streaming result support
2. Custom UDF registration API
3. Distributed execution support
4. Advanced extension handling
5. Memory-mapped data sources

## Dependencies

- **DataFusion**: >= 35.0.0
- **Substrait**: >= 0.80.0
- **Tokio**: >= 1.35.0 (async runtime)
- **Prost**: >= 0.12.0 (Protocol Buffers)
- **Arrow**: Included via DataFusion

## Contributing

To extend this implementation:
1. Add new function mappings in `get_capabilities()`
2. Extend type mapping in `map_substrait_type_to_arrow()`
3. Add custom error types in the error enum
4. Update tests for new features
5. Run `cargo fmt` and `cargo clippy` before committing

## Performance Tuning

### Memory Configuration
```rust
let config = SessionConfig::new()
    .with_target_partitions(8)
    .with_batch_size(8192);
let ctx = SessionContext::with_config(config);
```

### Parallel Execution
```rust
// DataFusion automatically parallelizes queries
// Control with target_partitions setting
```

## License

Apache License 2.0

## References

- [DataFusion Documentation](https://arrow.apache.org/datafusion/)
- [DataFusion Substrait Support](https://arrow.apache.org/datafusion/user-guide/sql/substrait.html)
- [Arrow Rust](https://docs.rs/arrow/)
- [Substrait Specification](https://substrait.io/)
- [Tokio Async Runtime](https://tokio.rs/)

## Support

For issues or questions:
- DataFusion: https://github.com/apache/arrow-datafusion/issues
- Substrait: https://github.com/substrait-io/substrait/issues
- This implementation: Open an issue in the compliance framework repository

## Examples

### Simple Query
```rust
// Create a simple filter query
let input_data = create_sample_data();
let plan = create_filter_plan(); // SELECT * FROM orders WHERE amount > 100
let result = engine.execute_plan(&plan, input_data).await?;
```

### Join Query
```rust
// Join orders with customers
let mut input_data = HashMap::new();
input_data.insert("orders".to_string(), orders_table);
input_data.insert("customers".to_string(), customers_table);

let plan = create_join_plan(); // SELECT * FROM orders JOIN customers
let result = engine.execute_plan(&plan, input_data).await?;
```

### Aggregate Query
```rust
// Aggregate with grouping
let plan = create_aggregate_plan(); // SELECT customer_id, SUM(amount) FROM orders GROUP BY customer_id
let result = engine.execute_plan(&plan, input_data).await?;