import { ComplianceEngine } from '../ComplianceEngine';
import { performance } from 'perf_hooks';

/**
 * Performance metrics for a single operation
 */
export interface OperationMetrics {
  operationName: string;
  executionTime: number; // milliseconds
  memoryUsed: number; // bytes
  timestamp: Date;
}

/**
 * Benchmark statistics
 */
export interface BenchmarkStats {
  operationName: string;
  totalRuns: number;
  minTime: number;
  maxTime: number;
  avgTime: number;
  medianTime: number;
  p95Time: number;
  p99Time: number;
  stdDevMs: number;
  throughput: number; // operations per second
}

/**
 * Complete benchmark result
 */
export interface BenchmarkResult {
  engineName: string;
  benchmarkName: string;
  stats: BenchmarkStats[];
  totalDuration: number;
  timestamp: Date;
}

/**
 * Configuration for benchmark execution
 */
export interface BenchmarkConfig {
  warmupRuns?: number;
  measurementRuns?: number;
  parallelism?: number;
  collectMemoryStats?: boolean;
  verbose?: boolean;
}

/**
 * Operation to benchmark
 */
export type Operation = {
  name: string;
  func: () => Promise<any>;
};

/**
 * Runs performance benchmarks on compliance engines
 */
export class BenchmarkRunner {
  private engine: ComplianceEngine;
  private config: Required<BenchmarkConfig>;

  constructor(engine: ComplianceEngine, config: BenchmarkConfig = {}) {
    this.engine = engine;
    this.config = {
      warmupRuns: config.warmupRuns ?? 5,
      measurementRuns: config.measurementRuns ?? 100,
      parallelism: config.parallelism ?? 1,
      collectMemoryStats: config.collectMemoryStats ?? true,
      verbose: config.verbose ?? false,
    };
  }

  /**
   * Run a complete benchmark suite
   */
  async runBenchmark(
    benchmarkName: string,
    operations: Operation[]
  ): Promise<BenchmarkResult> {
    const startTime = Date.now();

    if (this.config.verbose) {
      console.log(`Starting benchmark: ${benchmarkName}`);
      console.log(`Warmup runs: ${this.config.warmupRuns}`);
      console.log(`Measurement runs: ${this.config.measurementRuns}`);
    }

    const stats: BenchmarkStats[] = [];
    for (const op of operations) {
      const stat = await this.benchmarkOperation(op.name, op.func);
      stats.push(stat);
    }

    const endTime = Date.now();
    const totalDuration = endTime - startTime;

    const info = this.engine.getInfo();
    return {
      engineName: info.name,
      benchmarkName,
      stats,
      totalDuration,
      timestamp: new Date(startTime),
    };
  }

  /**
   * Benchmark a single operation
   */
  async benchmarkOperation(
    operationName: string,
    operation: () => Promise<any>
  ): Promise<BenchmarkStats> {
    if (this.config.verbose) {
      console.log(`  Benchmarking: ${operationName}`);
    }

    // Warmup phase
    if (this.config.warmupRuns > 0) {
      if (this.config.verbose) {
        console.log(`    Warmup: ${this.config.warmupRuns} runs`);
      }
      for (let i = 0; i < this.config.warmupRuns; i++) {
        await operation();
      }
    }

    // Measurement phase
    if (this.config.verbose) {
      console.log(`    Measuring: ${this.config.measurementRuns} runs`);
    }

    const metrics: OperationMetrics[] = [];
    for (let i = 0; i < this.config.measurementRuns; i++) {
      const metric = await this.measureOperation(operationName, operation);
      metrics.push(metric);
    }

    return this.calculateStats(operationName, metrics);
  }

  /**
   * Measure a single operation execution
   */
  private async measureOperation(
    operationName: string,
    operation: () => Promise<any>
  ): Promise<OperationMetrics> {
    let memBefore = 0;
    if (this.config.collectMemoryStats && typeof process !== 'undefined') {
      memBefore = process.memoryUsage().heapUsed;
    }

    const startTime = performance.now();
    const timestamp = new Date();

    await operation();

    const endTime = performance.now();
    const executionTime = endTime - startTime;

    let memoryUsed = 0;
    if (this.config.collectMemoryStats && typeof process !== 'undefined') {
      const memAfter = process.memoryUsage().heapUsed;
      memoryUsed = memAfter - memBefore;
    }

    return {
      operationName,
      executionTime,
      memoryUsed,
      timestamp,
    };
  }

  /**
   * Calculate statistics from metrics
   */
  private calculateStats(
    operationName: string,
    metrics: OperationMetrics[]
  ): BenchmarkStats {
    const times = metrics.map((m) => m.executionTime).sort((a, b) => a - b);
    const totalRuns = times.length;

    const minTime = times[0];
    const maxTime = times[totalRuns - 1];
    const avgTime = times.reduce((a, b) => a + b, 0) / totalRuns;
    const medianTime = times[Math.floor(totalRuns / 2)];
    const p95Time = times[Math.floor(totalRuns * 0.95)];
    const p99Time = times[Math.floor(totalRuns * 0.99)];

    // Calculate standard deviation
    const variance =
      times.reduce((sum, t) => sum + Math.pow(t - avgTime, 2), 0) / totalRuns;
    const stdDevMs = Math.sqrt(variance);

    // Calculate throughput (ops/sec)
    const totalTimeSeconds = times.reduce((a, b) => a + b, 0) / 1000;
    const throughput = totalRuns / totalTimeSeconds;

    return {
      operationName,
      totalRuns,
      minTime,
      maxTime,
      avgTime,
      medianTime,
      p95Time,
      p99Time,
      stdDevMs,
      throughput,
    };
  }

  /**
   * Format benchmark result as summary string
   */
  static formatSummary(result: BenchmarkResult): string {
    let output = `Benchmark: ${result.benchmarkName}\n`;
    output += `Engine: ${result.engineName}\n`;
    output += `Total Duration: ${result.totalDuration}ms\n\n`;

    for (const stat of result.stats) {
      output += `Operation: ${stat.operationName}\n`;
      output += `Total Runs: ${stat.totalRuns}\n`;
      output += `Min Time: ${stat.minTime.toFixed(2)}ms\n`;
      output += `Max Time: ${stat.maxTime.toFixed(2)}ms\n`;
      output += `Avg Time: ${stat.avgTime.toFixed(2)}ms\n`;
      output += `Median Time: ${stat.medianTime.toFixed(2)}ms\n`;
      output += `P95 Time: ${stat.p95Time.toFixed(2)}ms\n`;
      output += `P99 Time: ${stat.p99Time.toFixed(2)}ms\n`;
      output += `Std Dev: ${stat.stdDevMs.toFixed(2)}ms\n`;
      output += `Throughput: ${stat.throughput.toFixed(2)} ops/sec\n\n`;
    }

    return output;
  }

  /**
   * Export benchmark result to CSV format
   */
  static toCSV(result: BenchmarkResult): string {
    let csv =
      'Engine,Benchmark,Operation,TotalRuns,MinMs,MaxMs,AvgMs,MedianMs,P95Ms,P99Ms,StdDev,Throughput\n';

    for (const stat of result.stats) {
      csv += `${result.engineName},${result.benchmarkName},${stat.operationName},`;
      csv += `${stat.totalRuns},${stat.minTime.toFixed(2)},${stat.maxTime.toFixed(2)},`;
      csv += `${stat.avgTime.toFixed(2)},${stat.medianTime.toFixed(2)},`;
      csv += `${stat.p95Time.toFixed(2)},${stat.p99Time.toFixed(2)},`;
      csv += `${stat.stdDevMs.toFixed(2)},${stat.throughput.toFixed(2)}\n`;
    }

    return csv;
  }

  /**
   * Quick benchmark helper
   */
  static async quickBenchmark(
    engine: ComplianceEngine,
    operationName: string,
    operation: () => Promise<any>,
    runs: number = 100
  ): Promise<BenchmarkStats> {
    const config: BenchmarkConfig = {
      warmupRuns: 5,
      measurementRuns: runs,
      verbose: false,
    };

    const runner = new BenchmarkRunner(engine, config);
    return runner.benchmarkOperation(operationName, operation);
  }
}

// Made with Bob
