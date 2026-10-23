/**
 * Column type enumeration
 */
export enum ColumnType {
  INTEGER = 'INTEGER',
  FLOAT = 'FLOAT',
  DECIMAL = 'DECIMAL',
  BOOLEAN = 'BOOLEAN',
  STRING = 'STRING',
  DATE = 'DATE',
  TIMESTAMP = 'TIMESTAMP',
  TIME = 'TIME',
  BINARY = 'BINARY',
}

/**
 * The column type for a type name, as in a typed CSV header or an engine's result type.
 * Type parameters are ignored: `decimal(15,2)` is DECIMAL, `varchar(25)` is STRING.
 */
export function columnTypeFromName(name: string): ColumnType {
  const base = name.trim().toLowerCase().split('(')[0].trim();
  switch (base) {
    case 'int': case 'integer': case 'i8': case 'i16': case 'i32': case 'i64':
    case 'int2': case 'int4': case 'int8': case 'smallint': case 'tinyint':
    case 'bigint': case 'long': case 'hugeint':
      return ColumnType.INTEGER;
    case 'float': case 'fp32': case 'real': case 'float4':
    case 'double': case 'fp64': case 'float8': case 'number':
      return ColumnType.FLOAT;
    case 'decimal': case 'numeric':
      return ColumnType.DECIMAL;
    case 'bool': case 'boolean':
      return ColumnType.BOOLEAN;
    case 'date':
      return ColumnType.DATE;
    case 'timestamp': case 'timestamptz': case 'datetime':
      return ColumnType.TIMESTAMP;
    case 'time':
      return ColumnType.TIME;
    case 'binary': case 'blob': case 'varbinary':
      return ColumnType.BINARY;
    default:
      return ColumnType.STRING;
  }
}

/**
 * Column definition
 */
export interface Column {
  name: string;
  type: ColumnType;
  nullable?: boolean;
}

/**
 * Represents a single value in a table cell
 */
export type CellValue = null | boolean | number | string | Date | Buffer;

/**
 * Represents a single row in a table
 */
export type Row = CellValue[];

/**
 * Represents tabular data with schema and rows
 */
export class TableData {
  private columns: Column[];
  private rows: Row[];

  constructor(columns: Column[] = [], rows: Row[] = []) {
    this.columns = columns;
    this.rows = rows;
  }

  /**
   * Add a row to the table
   */
  addRow(row: Row): void {
    this.rows.push(row);
  }

  /**
   * Add multiple rows to the table
   */
  addRows(rows: Row[]): void {
    this.rows.push(...rows);
  }

  /**
   * Get all rows
   */
  getRows(): Row[] {
    return this.rows;
  }

  /**
   * Get column definitions
   */
  getColumns(): Column[] {
    return this.columns;
  }

  /**
   * Set column definitions
   */
  setColumns(columns: Column[]): void {
    this.columns = columns;
  }

  /**
   * Get number of rows
   */
  rowCount(): number {
    return this.rows.length;
  }

  /**
   * Get number of columns
   */
  columnCount(): number {
    return this.columns.length;
  }

  /**
   * Get a specific row by index
   */
  getRow(index: number): Row | undefined {
    return this.rows[index];
  }

  /**
   * Get a specific cell value
   */
  getCell(rowIndex: number, columnIndex: number): CellValue | undefined {
    const row = this.rows[rowIndex];
    return row ? row[columnIndex] : undefined;
  }

  /**
   * Check if table is empty
   */
  isEmpty(): boolean {
    return this.rows.length === 0;
  }

  /**
   * Clear all rows
   */
  clear(): void {
    this.rows = [];
  }
}

