"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const table_data_1 = require("../src/table-data");
describe('TableData', () => {
    describe('constructor', () => {
        it('should create empty table with no arguments', () => {
            const table = new table_data_1.TableData();
            expect(table.rowCount()).toBe(0);
            expect(table.columnCount()).toBe(0);
            expect(table.isEmpty()).toBe(true);
        });
        it('should create table with columns and rows', () => {
            const columns = [
                { name: 'id', type: table_data_1.ColumnType.INTEGER, nullable: false },
                { name: 'name', type: table_data_1.ColumnType.STRING, nullable: true },
            ];
            const rows = [
                [1, 'Alice'],
                [2, 'Bob'],
            ];
            const table = new table_data_1.TableData(columns, rows);
            expect(table.rowCount()).toBe(2);
            expect(table.columnCount()).toBe(2);
            expect(table.isEmpty()).toBe(false);
        });
    });
    describe('addRow', () => {
        it('should add a single row', () => {
            const table = new table_data_1.TableData();
            table.addRow([1, 'test']);
            expect(table.rowCount()).toBe(1);
        });
        it('should add multiple rows individually', () => {
            const table = new table_data_1.TableData();
            table.addRow([1, 'first']);
            table.addRow([2, 'second']);
            expect(table.rowCount()).toBe(2);
        });
    });
    describe('addRows', () => {
        it('should add multiple rows at once', () => {
            const table = new table_data_1.TableData();
            table.addRows([
                [1, 'first'],
                [2, 'second'],
                [3, 'third'],
            ]);
            expect(table.rowCount()).toBe(3);
        });
    });
    describe('getRow', () => {
        it('should return row at index', () => {
            const table = new table_data_1.TableData();
            table.addRow([1, 'test']);
            const row = table.getRow(0);
            expect(row).toEqual([1, 'test']);
        });
        it('should return undefined for invalid index', () => {
            const table = new table_data_1.TableData();
            expect(table.getRow(0)).toBeUndefined();
            expect(table.getRow(-1)).toBeUndefined();
        });
    });
    describe('getCell', () => {
        it('should return cell value', () => {
            const table = new table_data_1.TableData();
            table.addRow([1, 'test']);
            expect(table.getCell(0, 0)).toBe(1);
            expect(table.getCell(0, 1)).toBe('test');
        });
        it('should return undefined for invalid indices', () => {
            const table = new table_data_1.TableData();
            table.addRow([1, 'test']);
            expect(table.getCell(1, 0)).toBeUndefined();
            expect(table.getCell(0, 2)).toBeUndefined();
        });
    });
    describe('clear', () => {
        it('should remove all rows', () => {
            const table = new table_data_1.TableData();
            table.addRows([
                [1, 'first'],
                [2, 'second'],
            ]);
            expect(table.rowCount()).toBe(2);
            table.clear();
            expect(table.rowCount()).toBe(0);
            expect(table.isEmpty()).toBe(true);
        });
    });
    describe('columns', () => {
        it('should get and set columns', () => {
            const table = new table_data_1.TableData();
            const columns = [
                { name: 'id', type: table_data_1.ColumnType.INTEGER },
                { name: 'value', type: table_data_1.ColumnType.FLOAT },
            ];
            table.setColumns(columns);
            expect(table.getColumns()).toEqual(columns);
            expect(table.columnCount()).toBe(2);
        });
    });
});
//# sourceMappingURL=table-data.test.js.map