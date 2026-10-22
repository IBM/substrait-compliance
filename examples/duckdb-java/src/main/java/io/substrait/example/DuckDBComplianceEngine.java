package io.substrait.example;

import io.substrait.compliance.*;
import io.substrait.proto.Plan;
import java.sql.*;
import java.util.*;

/**
 * Example DuckDB engine implementation for Substrait compliance testing.
 *
 * This demonstrates how to integrate DuckDB with the compliance framework.
 */
public class DuckDBComplianceEngine implements ComplianceEngine {
    
    private final Connection connection;
    private final String duckdbVersion;
    
    public DuckDBComplianceEngine() throws SQLException {
        // Initialize DuckDB in-memory database
        this.connection = DriverManager.getConnection("jdbc:duckdb:");
        // Report the real engine version rather than a hard-coded string.
        String version = connection.getMetaData().getDatabaseProductVersion();
        this.duckdbVersion = version != null && version.startsWith("v") ? version.substring(1) : version;
        // Load the Substrait extension so that the substrait() table function is available.
        try {
            loadSubstraitExtension();
        } catch (SQLException e) {
            // CI sets COMPLIANCE_REQUIRE_SUBSTRAIT_EXTENSION=true so a missing extension fails
            // the run instead of silently reporting every plan as a failure.
            if ("true".equalsIgnoreCase(System.getenv("COMPLIANCE_REQUIRE_SUBSTRAIT_EXTENSION"))) {
                throw new SQLException("DuckDB substrait extension could not be loaded", e);
            }
            // Extension may not be installable in offline environments.
            // The execute_plan call will fail later with a clear message.
            System.err.println("Warning: DuckDB substrait extension could not be loaded: "
                + e.getMessage());
        }
    }
    
    /**
     * Install and load the substrait extension. It is published in DuckDB's community
     * repository (https://duckdb.org/community_extensions/extensions/substrait); the core
     * repository is tried second for older setups.
     */
    private void loadSubstraitExtension() throws SQLException {
        SQLException failure = null;
        for (String install : new String[] {"INSTALL substrait FROM community", "INSTALL substrait"}) {
            // DuckDB's JDBC driver closes a statement after an error, so each attempt
            // needs its own statement.
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(install);
                stmt.execute("LOAD substrait");
                return;
            } catch (SQLException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        throw failure;
    }

    @Override
    public EngineInfo getEngineInfo() {
        return new EngineInfo(
            "DuckDB",
            duckdbVersion,
            "0.80.0"  // Substrait version
        );
    }
    
    @Override
    public EngineCapabilities getCapabilities() {
        return EngineCapabilities.builder()
            .addRelation("read")
            .addRelation("filter")
            .addRelation("project")
            .addRelation("aggregate")
            .addRelation("join")
            .addRelation("sort")
            .addFunction("add")
            .addFunction("subtract")
            .addFunction("multiply")
            .addFunction("divide")
            .addFunction("sum")
            .addFunction("count")
            .addFunction("avg")
            .supportsExtensions(true)
            .build();
    }
    
    @Override
    public ComplianceResult executePlan(
        Plan plan,
        Map<String, TableData> inputData
    ) throws ComplianceException {
        long startTime = System.currentTimeMillis();
        try {
            // 1. Load input data into DuckDB, using the column names and types the
            //    plan's read relations declare (the TPC-H CSVs have no header row).
            loadInputData(inputData, PlanSchemas.namedTableSchemas(plan));
            
            // 2. Execute Substrait plan
            // Note: DuckDB's Substrait support would be used here
            // For this example, we'll simulate execution
            TableData output = executeSubstraitPlan(plan);
            
            long executionTime = System.currentTimeMillis() - startTime;
            return ComplianceResult.success(output, executionTime);
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return ComplianceResult.failure(e.getMessage(), e, executionTime);
        }
    }
    
    @Override
    public PlanValidationResult validatePlan(Plan plan) {
        try {
            // Validate Substrait plan structure
            // In real implementation, would use DuckDB's plan validator
            boolean isValid = plan != null && plan.getRelationsCount() > 0;
            
            if (isValid) {
                return PlanValidationResult.supported();
            } else {
                return PlanValidationResult.unsupported(
                    Collections.singletonList("Plan is empty or invalid")
                );
            }
            
        } catch (Exception e) {
            return PlanValidationResult.unsupported(
                Collections.singletonList("Validation error: " + e.getMessage())
            );
        }
    }
    
    private void loadInputData(Map<String, TableData> inputData,
                               Map<String, PlanSchemas.TableSchema> planSchemas) throws SQLException {
        for (Map.Entry<String, TableData> entry : inputData.entrySet()) {
            String tableName = entry.getKey();
            TableData data = entry.getValue();

            List<String> columnNames = data.getColumnNames();
            List<String> columnTypes = new ArrayList<>();
            for (String type : data.getColumnTypes()) {
                columnTypes.add(mapDataType(type));
            }
            // Prefer the plan's declared schema: it carries the real column names and
            // types, while header-less CSVs only yield column_N / string.
            PlanSchemas.TableSchema declared = planSchemas.get(tableName.toLowerCase(Locale.ROOT));
            if (declared != null && declared.columnNames.size() == columnNames.size()) {
                columnNames = declared.columnNames;
                columnTypes = declared.sqlTypes;
            }

            createTable(tableName, columnNames, columnTypes);
            insertData(tableName, data);
        }
    }

    private void createTable(String tableName, List<String> columnNames, List<String> sqlTypes)
            throws SQLException {
        // OR REPLACE: tables are reloaded for every test case, and several TPC-H
        // queries share the same tables.
        StringBuilder sql = new StringBuilder("CREATE OR REPLACE TABLE ");
        sql.append(quoteIdentifier(tableName)).append(" (");
        for (int i = 0; i < columnNames.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(quoteIdentifier(columnNames.get(i))).append(" ").append(sqlTypes.get(i));
        }
        sql.append(")");

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql.toString());
        }
    }

    private static final int INSERT_BATCH_ROWS = 1000;

    private void insertData(String tableName, TableData data) throws SQLException {
        // Multi-row INSERTs: one statement per row is far too slow for lineitem (60k rows).
        // String literals are cast by DuckDB to the column types (DATE, DECIMAL, ...).
        List<List<Object>> rows = data.getRows();
        for (int start = 0; start < rows.size(); start += INSERT_BATCH_ROWS) {
            int end = Math.min(rows.size(), start + INSERT_BATCH_ROWS);
            StringBuilder sql = new StringBuilder("INSERT INTO ");
            sql.append(quoteIdentifier(tableName)).append(" VALUES ");
            for (int r = start; r < end; r++) {
                if (r > start) sql.append(", ");
                sql.append("(");
                List<Object> row = rows.get(r);
                for (int i = 0; i < row.size(); i++) {
                    if (i > 0) sql.append(", ");
                    Object value = row.get(i);
                    if (value == null) {
                        sql.append("NULL");
                    } else if (value instanceof String) {
                        sql.append("'").append(value.toString().replace("'", "''")).append("'");
                    } else {
                        sql.append(value);
                    }
                }
                sql.append(")");
            }
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql.toString());
            }
        }
    }

    private static String quoteIdentifier(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }
    
    private TableData executeSubstraitPlan(Plan plan) throws SQLException {
        // The substrait extension's from_substrait() table function executes a
        // serialized (binary) Substrait plan, bound here as a BLOB parameter.
        // If there are no plan bytes we return an empty result.
        byte[] planBytes = plan.toByteArray();
        if (planBytes == null || planBytes.length == 0) {
            return new TableData(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
            );
        }

        try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM from_substrait(?)")) {
            stmt.setBytes(1, planBytes);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                return resultSetToTableData(rs);
            }
        } catch (SQLException e) {
            // Surface the engine error so the test is recorded as FAILED with a reason.
            throw new SQLException(
                "DuckDB Substrait execution failed (is the substrait extension loaded?): "
                    + e.getMessage(), e);
        }
    }

    /**
     * Convert a JDBC ResultSet to a TableData.
     */
    private TableData resultSetToTableData(java.sql.ResultSet rs) throws SQLException {
        java.sql.ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();

        List<String> colNames = new ArrayList<>();
        List<String> colTypes = new ArrayList<>();
        for (int i = 1; i <= colCount; i++) {
            colNames.add(meta.getColumnName(i));
            colTypes.add(jdbcTypeToSubstraitType(meta.getColumnTypeName(i)));
        }

        List<List<Object>> rows = new ArrayList<>();
        while (rs.next()) {
            List<Object> row = new ArrayList<>();
            for (int i = 1; i <= colCount; i++) {
                row.add(rs.getObject(i));
            }
            rows.add(row);
        }

        return new TableData(colNames, colTypes, rows);
    }

    /** Map a JDBC type name to a Substrait canonical type string. */
    static String jdbcTypeToSubstraitType(String jdbcType) {
        if (jdbcType == null) return "string";
        // DuckDB reports parameterized names such as DECIMAL(38,4) or VARCHAR(25);
        // drop the parameters before mapping.
        String base = jdbcType.trim().toUpperCase(Locale.ROOT);
        int paren = base.indexOf('(');
        if (paren > 0) {
            base = base.substring(0, paren).trim();
        }
        switch (base) {
            case "TINYINT": case "INT1": case "UTINYINT":
            case "SMALLINT": case "INT2": case "USMALLINT":
            case "INTEGER": case "INT": case "INT4": case "UINTEGER": return "integer";
            case "BIGINT": case "INT8": case "INT64": case "UBIGINT": return "bigint";
            case "HUGEINT": case "UHUGEINT":                          return "bigint";
            case "DOUBLE": case "FLOAT8":                             return "double";
            case "FLOAT": case "FLOAT4": case "REAL":                  return "float";
            case "BOOLEAN": case "BOOL":                              return "boolean";
            case "DECIMAL": case "NUMERIC":                           return "decimal";
            default:                                                   return "string";
        }
    }
    
    private String mapDataType(String typeName) {
        // Map Substrait type names to DuckDB types
        String lowerType = typeName.toLowerCase();
        if (lowerType.contains("int32") || lowerType.contains("integer")) {
            return "INTEGER";
        } else if (lowerType.contains("int64") || lowerType.contains("bigint")) {
            return "BIGINT";
        } else if (lowerType.contains("fp64") || lowerType.contains("double")) {
            return "DOUBLE";
        } else if (lowerType.contains("string") || lowerType.contains("varchar")) {
            return "VARCHAR";
        } else if (lowerType.contains("date")) {
            return "DATE";
        } else if (lowerType.contains("bool")) {
            return "BOOLEAN";
        } else if (lowerType.contains("decimal")) {
            return "DECIMAL(18,2)";
        } else {
            return "VARCHAR";
        }
    }
    
    public void close() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }
}
