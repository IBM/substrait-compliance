package io.substrait.compliance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents tabular data for compliance testing.
 */
public class TableData {
    
    private final List<String> columnNames;
    private final List<String> columnTypes;
    private final List<List<Object>> rows;
    
    public TableData(List<String> columnNames, List<String> columnTypes, 
                    List<List<Object>> rows) {
        this.columnNames = new ArrayList<>(columnNames);
        this.columnTypes = new ArrayList<>(columnTypes);
        this.rows = new ArrayList<>();
        for (List<Object> row : rows) {
            this.rows.add(new ArrayList<>(row));
        }
    }
    
    public List<String> getColumnNames() { return new ArrayList<>(columnNames); }
    public List<String> getColumnTypes() { return new ArrayList<>(columnTypes); }
    public int getRowCount() { return rows.size(); }
    public int getColumnCount() { return columnNames.size(); }
    public Object getValue(int row, int column) { return rows.get(row).get(column); }
    
    public List<List<Object>> getRows() {
        List<List<Object>> copy = new ArrayList<>();
        for (List<Object> row : rows) {
            copy.add(new ArrayList<>(row));
        }
        return copy;
    }
    
    @Override
    public String toString() {
        return String.format("TableData{columns=%d, rows=%d}", 
            columnNames.size(), rows.size());
    }
}
