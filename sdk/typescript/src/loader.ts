import * as fs from 'fs/promises';
import * as path from 'path';
import * as yaml from 'js-yaml';
import { TestSuite, TestSuiteMetadata, TestCase } from './test-suite';
import { TableData, Column, ColumnType } from './table-data';

/**
 * Loader for test suites from YAML files
 */
export class TestSuiteLoader {
  /**
   * Load a test suite from a YAML file
   */
  async loadFromFile(filePath: string): Promise<TestSuite> {
    const content = await fs.readFile(filePath, 'utf-8');
    return this.loadFromYaml(content, path.dirname(filePath));
  }

  /**
   * Load a test suite from YAML string
   */
  loadFromYaml(yamlContent: string, basePath: string = '.'): TestSuite {
    const data = yaml.load(yamlContent) as any;

    const metadata: TestSuiteMetadata = {
      name: data.name || 'Unnamed Test Suite',
      version: data.version || '1.0.0',
      description: data.description,
      category: data.category,
      tags: data.tags,
    };

    const testCases: TestCase[] = (data.tests || []).map((test: any) =>
      this.parseTestCase(test, basePath)
    );

    return new TestSuite(metadata, testCases);
  }

  /**
   * Load multiple test suites from a directory
   */
  async loadFromDirectory(dirPath: string): Promise<TestSuite[]> {
    const entries = await fs.readdir(dirPath, { withFileTypes: true });
    const suites: TestSuite[] = [];

    for (const entry of entries) {
      if (entry.isFile() && (entry.name.endsWith('.yaml') || entry.name.endsWith('.yml'))) {
        const filePath = path.join(dirPath, entry.name);
        try {
          const suite = await this.loadFromFile(filePath);
          suites.push(suite);
        } catch (error) {
          console.error(`Failed to load test suite from ${filePath}:`, error);
        }
      }
    }

    return suites;
  }

  private parseTestCase(test: any, basePath: string): TestCase {
    return {
      id: test.id || test.name,
      description: test.description,
      plan: path.resolve(basePath, test.plan),
      inputData: test.input_data ? this.parseInputData(test.input_data) : undefined,
      expectedOutput: test.expected_output ? this.parseTableData(test.expected_output) : undefined,
      tags: test.tags,
      skip: test.skip || false,
      skipReason: test.skip_reason,
    };
  }

  private parseInputData(inputData: any): Record<string, TableData> {
    const result: Record<string, TableData> = {};
    for (const [name, data] of Object.entries(inputData)) {
      result[name] = this.parseTableData(data);
    }
    return result;
  }

  private parseTableData(data: any): TableData {
    const columns: Column[] = (data.columns || []).map((col: any) => ({
      name: col.name,
      type: this.parseColumnType(col.type),
      nullable: col.nullable !== false,
    }));

    const rows: any[][] = data.rows || [];

    return new TableData(columns, rows);
  }

  private parseColumnType(typeStr: string): ColumnType {
    const normalized = typeStr.toLowerCase();
    
    if (normalized.includes('int')) return ColumnType.INTEGER;
    if (normalized.includes('float') || normalized.includes('double')) return ColumnType.FLOAT;
    if (normalized.includes('decimal') || normalized.includes('numeric')) return ColumnType.DECIMAL;
    if (normalized.includes('bool')) return ColumnType.BOOLEAN;
    if (normalized.includes('string') || normalized.includes('varchar') || normalized.includes('text')) {
      return ColumnType.STRING;
    }
    if (normalized.includes('date')) return ColumnType.DATE;
    if (normalized.includes('timestamp') || normalized.includes('datetime')) return ColumnType.TIMESTAMP;
    if (normalized.includes('time')) return ColumnType.TIME;
    if (normalized.includes('binary') || normalized.includes('blob')) return ColumnType.BINARY;
    
    return ColumnType.STRING; // Default fallback
  }
}

/**
 * Load a test suite from a YAML file (convenience function)
 */
export async function loadTestSuite(filePath: string): Promise<TestSuite> {
  const loader = new TestSuiteLoader();
  return loader.loadFromFile(filePath);
}

/**
 * Load multiple test suites from a directory (convenience function)
 */
export async function loadTestSuites(dirPath: string): Promise<TestSuite[]> {
  const loader = new TestSuiteLoader();
  return loader.loadFromDirectory(dirPath);
}

// Made with Bob
