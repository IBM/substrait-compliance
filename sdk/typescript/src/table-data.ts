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

// Made with Bob
