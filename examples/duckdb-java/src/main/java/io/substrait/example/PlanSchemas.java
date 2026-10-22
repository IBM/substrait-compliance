package io.substrait.example;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import io.substrait.proto.NamedStruct;
import io.substrait.proto.Plan;
import io.substrait.proto.ReadRel;
import io.substrait.proto.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Extracts the schema of every named table a Substrait plan reads, so input tables
 * can be created with the column names and types the plan expects.
 */
final class PlanSchemas {

    static final class TableSchema {
        final List<String> columnNames;
        final List<String> sqlTypes;

        TableSchema(List<String> columnNames, List<String> sqlTypes) {
            this.columnNames = columnNames;
            this.sqlTypes = sqlTypes;
        }
    }

    private PlanSchemas() {}

    /** Map of lower-cased table name (last part of the named table) to its schema. */
    static Map<String, TableSchema> namedTableSchemas(Plan plan) {
        Map<String, TableSchema> schemas = new LinkedHashMap<>();
        collect(plan, schemas);
        return schemas;
    }

    private static void collect(Message message, Map<String, TableSchema> schemas) {
        if (message instanceof ReadRel) {
            ReadRel read = (ReadRel) message;
            if (read.hasNamedTable() && read.hasBaseSchema()
                    && read.getNamedTable().getNamesCount() > 0) {
                List<String> names = read.getNamedTable().getNamesList();
                String table = names.get(names.size() - 1).toLowerCase(Locale.ROOT);
                TableSchema schema = toTableSchema(read.getBaseSchema());
                if (schema != null) {
                    schemas.putIfAbsent(table, schema);
                }
            }
        }
        // Walk every nested message generically so all relation types are covered.
        for (Map.Entry<FieldDescriptor, Object> field : message.getAllFields().entrySet()) {
            if (field.getKey().getJavaType() != FieldDescriptor.JavaType.MESSAGE) {
                continue;
            }
            if (field.getKey().isRepeated()) {
                for (Object item : (List<?>) field.getValue()) {
                    collect((Message) item, schemas);
                }
            } else {
                collect((Message) field.getValue(), schemas);
            }
        }
    }

    private static TableSchema toTableSchema(NamedStruct struct) {
        List<Type> types = struct.getStruct().getTypesList();
        List<String> names = struct.getNamesList();
        // Only flat schemas: nested structs list extra names for their fields.
        if (names.size() != types.size()) {
            return null;
        }
        List<String> sqlTypes = new ArrayList<>();
        for (Type type : types) {
            sqlTypes.add(sqlType(type));
        }
        return new TableSchema(new ArrayList<>(names), sqlTypes);
    }

    static String sqlType(Type type) {
        switch (type.getKindCase().name()) {
            case "BOOL": return "BOOLEAN";
            case "I8": return "TINYINT";
            case "I16": return "SMALLINT";
            case "I32": return "INTEGER";
            case "I64": return "BIGINT";
            case "FP32": return "FLOAT";
            case "FP64": return "DOUBLE";
            case "DATE": return "DATE";
            case "DECIMAL":
                return "DECIMAL(" + type.getDecimal().getPrecision() + ","
                    + type.getDecimal().getScale() + ")";
            default: return "VARCHAR"; // STRING, VARCHAR, FIXED_CHAR and anything else
        }
    }
}
