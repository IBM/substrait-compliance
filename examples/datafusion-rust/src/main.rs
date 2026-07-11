//! DataFusion Compliance Example - Main Entry Point
//!
//! Demonstrates how to use the DataFusion compliance engine to run
//! Substrait compliance tests.

use datafusion_substrait_compliance::{
    ComplianceResult, DataFusionComplianceEngine, TableData, Value,
};
use std::collections::HashMap;
use std::env;
use std::fs;
use substrait::proto::Plan;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    println!("DataFusion Substrait Compliance Example");
    println!("========================================\n");

    // 1. Create DataFusion engine
    let mut engine = DataFusionComplianceEngine::new();

    // 2. Print engine info
    let info = engine.get_engine_info();
    println!("Engine: {} {}", info.name, info.version);
    println!("Vendor: {}", info.vendor);
    println!("Substrait Version: {}", info.substrait_version);
    println!("Description: {}\n", info.description);

    // 3. Print capabilities
    let caps = engine.get_capabilities();
    println!("Capabilities:");
    println!("  Relations: {}", caps.supported_relations.len());
    println!("  Functions: {}", caps.supported_functions.len());
    println!("  Types: {}", caps.supported_types.len());
    println!("  Max Plan Depth: {}", caps.max_plan_depth);
    println!(
        "  Extensions: {}\n",
        if caps.supports_extensions {
            "Yes"
        } else {
            "No"
        }
    );

    // 4. Create sample data
    println!("Creating sample data...");
    let input_data = create_sample_data();
    println!("Created {} tables\n", input_data.len());

    // 5. Execute a plan (if provided)
    let args: Vec<String> = env::args().collect();
    if args.len() > 1 {
        let plan_file = &args[1];
        println!("Loading plan from: {}", plan_file);

        let plan = load_plan_from_file(plan_file)?;

        // Validate plan
        println!("Validating plan...");
        let validation = engine.validate_plan(&plan);

        if !validation.is_supported {
            println!("Plan validation failed:");
            for reason in &validation.reasons {
                println!("  - {}", reason);
            }
            return Ok(());
        }

        println!("Plan is valid\n");

        // Execute plan
        println!("Executing plan...");
        let result = engine.execute_plan(&plan, input_data).await;

        if result.success {
            println!("Execution successful ({} ms)\n", result.duration_ms);

            if let Some(output) = result.output {
                println!("Results:");
                print_table_data(&output);
            }
        } else {
            println!(
                "Execution failed: {}",
                result.error.unwrap_or_else(|| "Unknown error".to_string())
            );
        }
    } else {
        println!("Usage: {} <plan_file.substrait>", args[0]);
        println!("\nNo plan file provided. Showing sample data:\n");

        for (table_name, table_data) in &input_data {
            println!("Table: {}", table_name);
            print_table_data(table_data);
            println!();
        }
    }

    println!("\nExample completed successfully!");
    Ok(())
}

/// Load a Substrait plan from file
fn load_plan_from_file(filename: &str) -> Result<Plan, Box<dyn std::error::Error>> {
    let bytes = fs::read(filename)?;
    let plan = Plan::decode(&bytes[..])?;
    Ok(plan)
}

/// Create sample input data for testing
fn create_sample_data() -> HashMap<String, TableData> {
    let mut data = HashMap::new();

    // Create a simple "orders" table
    data.insert(
        "orders".to_string(),
        TableData {
            column_names: vec![
                "order_id".to_string(),
                "customer_id".to_string(),
                "amount".to_string(),
            ],
            column_types: vec!["i32".to_string(), "i32".to_string(), "fp64".to_string()],
            rows: vec![
                vec![
                    Value::Int32(1),
                    Value::Int32(100),
                    Value::Float64(150.50),
                ],
                vec![
                    Value::Int32(2),
                    Value::Int32(101),
                    Value::Float64(200.75),
                ],
                vec![
                    Value::Int32(3),
                    Value::Int32(100),
                    Value::Float64(99.99),
                ],
                vec![
                    Value::Int32(4),
                    Value::Int32(102),
                    Value::Float64(450.00),
                ],
                vec![
                    Value::Int32(5),
                    Value::Int32(101),
                    Value::Float64(325.25),
                ],
            ],
        },
    );

    // Create a simple "customers" table
    data.insert(
        "customers".to_string(),
        TableData {
            column_names: vec![
                "customer_id".to_string(),
                "name".to_string(),
                "country".to_string(),
            ],
            column_types: vec![
                "i32".to_string(),
                "string".to_string(),
                "string".to_string(),
            ],
            rows: vec![
                vec![
                    Value::Int32(100),
                    Value::String("Alice".to_string()),
                    Value::String("USA".to_string()),
                ],
                vec![
                    Value::Int32(101),
                    Value::String("Bob".to_string()),
                    Value::String("UK".to_string()),
                ],
                vec![
                    Value::Int32(102),
                    Value::String("Charlie".to_string()),
                    Value::String("Canada".to_string()),
                ],
            ],
        },
    );

    data
}

/// Print table data in a formatted way
fn print_table_data(data: &TableData) {
    // Print header
    for (i, name) in data.column_names.iter().enumerate() {
        if i > 0 {
            print!(" | ");
        }
        print!("{}", name);
    }
    println!();

    // Print separator
    for (i, name) in data.column_names.iter().enumerate() {
        if i > 0 {
            print!("-+-");
        }
        print!("{}", "-".repeat(name.len()));
    }
    println!();

    // Print rows
    for row in &data.rows {
        for (i, value) in row.iter().enumerate() {
            if i > 0 {
                print!(" | ");
            }
            match value {
                Value::Null => print!("NULL"),
                Value::Boolean(v) => print!("{}", v),
                Value::Int8(v) => print!("{}", v),
                Value::Int16(v) => print!("{}", v),
                Value::Int32(v) => print!("{}", v),
                Value::Int64(v) => print!("{}", v),
                Value::UInt8(v) => print!("{}", v),
                Value::UInt16(v) => print!("{}", v),
                Value::UInt32(v) => print!("{}", v),
                Value::UInt64(v) => print!("{}", v),
                Value::Float32(v) => print!("{}", v),
                Value::Float64(v) => print!("{}", v),
                Value::String(v) => print!("{}", v),
                Value::Binary(v) => print!("{:?}", v),
            }
        }
        println!();
    }
}

