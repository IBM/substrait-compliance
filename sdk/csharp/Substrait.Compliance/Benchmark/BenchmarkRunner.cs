using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Substrait.Compliance.Benchmark
{
    /// <summary>
    /// Performance metrics for a single operation
    /// </summary>
    public record OperationMetrics(
        string OperationName,
        TimeSpan ExecutionTime,
        long MemoryUsed,
        DateTime Timestamp
    );

    /// <summary>
    /// Benchmark statistics
    /// </summary>
    public record BenchmarkStats(
        string OperationName,
        int TotalRuns,
        TimeSpan MinTime,
        TimeSpan MaxTime,
        TimeSpan AvgTime,
        TimeSpan MedianTime,
        TimeSpan P95Time,
        TimeSpan P99Time,
        double StdDevMs,
        double Throughput // operations per second
    )
    {
        public string Summary()
        {
            return $@"Operation: {OperationName}
Total Runs: {TotalRuns}
Min Time: {MinTime.TotalMilliseconds}ms
Max Time: {MaxTime.TotalMilliseconds}ms
Avg Time: {AvgTime.TotalMilliseconds}ms
Median Time: {MedianTime.TotalMilliseconds}ms
P95 Time: {P95Time.TotalMilliseconds}ms
P99 Time: {P99Time.TotalMilliseconds}ms
Std Dev: {StdDevMs:F2}ms
Throughput: {Throughput:F2} ops/sec
";
        }
    }

    /// <summary>
    /// Complete benchmark result
    /// </summary>
    public record BenchmarkResult(
        string EngineName,
        string BenchmarkName,
        List<BenchmarkStats> Stats,
        TimeSpan TotalDuration,
        DateTime Timestamp
    )
    {
        public string Summary()
        {
            var sb = new StringBuilder();
            sb.AppendLine($"Benchmark: {BenchmarkName}");
            sb.AppendLine($"Engine: {EngineName}");
            sb.AppendLine($"Total Duration: {TotalDuration.TotalMilliseconds}ms\n");

            foreach (var stat in Stats)
            {
                sb.AppendLine(stat.Summary());
            }

            return sb.ToString();
        }

        public string ToCSV()
        {
            var sb = new StringBuilder();
            sb.AppendLine("Engine,Benchmark,Operation,TotalRuns,MinMs,MaxMs,AvgMs,MedianMs,P95Ms,P99Ms,StdDev,Throughput");

            foreach (var stat in Stats)
            {
                sb.AppendLine($"{EngineName},{BenchmarkName},{stat.OperationName}," +
                    $"{stat.TotalRuns},{stat.MinTime.TotalMilliseconds:F2}," +
                    $"{stat.MaxTime.TotalMilliseconds:F2},{stat.AvgTime.TotalMilliseconds:F2}," +
                    $"{stat.MedianTime.TotalMilliseconds:F2},{stat.P95Time.TotalMilliseconds:F2}," +
                    $"{stat.P99Time.TotalMilliseconds:F2},{stat.StdDevMs:F2},{stat.Throughput:F2}");
            }

            return sb.ToString();
        }
    }

    /// <summary>
    /// Configuration for benchmark execution
    /// </summary>
    public record BenchmarkConfig(
        int WarmupRuns = 5,
        int MeasurementRuns = 100,
        int Parallelism = 1,
        bool CollectMemoryStats = true,
        bool Verbose = false
    );

    /// <summary>
    /// Runs performance benchmarks on compliance engines
    /// </summary>
    public class BenchmarkRunner
    {
        private readonly IComplianceEngine _engine;
        private readonly BenchmarkConfig _config;

        public BenchmarkRunner(IComplianceEngine engine, BenchmarkConfig? config = null)
        {
            _engine = engine;
            _config = config ?? new BenchmarkConfig();
        }

        /// <summary>
        /// Run a complete benchmark suite
        /// </summary>
        public async Task<BenchmarkResult> RunBenchmarkAsync(
            string benchmarkName,
            List<(string Name, Func<Task> Func)> operations)
        {
            var startTime = DateTime.UtcNow;

            if (_config.Verbose)
            {
                Console.WriteLine($"Starting benchmark: {benchmarkName}");
                Console.WriteLine($"Warmup runs: {_config.WarmupRuns}");
                Console.WriteLine($"Measurement runs: {_config.MeasurementRuns}");
            }

            var stats = new List<BenchmarkStats>();
            foreach (var (name, func) in operations)
            {
                var stat = await BenchmarkOperationAsync(name, func);
                stats.Add(stat);
            }

            var endTime = DateTime.UtcNow;
            var totalDuration = endTime - startTime;

            var info = _engine.GetInfo();
            return new BenchmarkResult(
                info.Name,
                benchmarkName,
                stats,
                totalDuration,
                startTime
            );
        }

        /// <summary>
        /// Benchmark a single operation
        /// </summary>
        public async Task<BenchmarkStats> BenchmarkOperationAsync(
            string operationName,
            Func<Task> operation)
        {
            if (_config.Verbose)
            {
                Console.WriteLine($"  Benchmarking: {operationName}");
            }

            // Warmup phase
            if (_config.WarmupRuns > 0)
            {
                if (_config.Verbose)
                {
                    Console.WriteLine($"    Warmup: {_config.WarmupRuns} runs");
                }

                for (int i = 0; i < _config.WarmupRuns; i++)
                {
                    await operation();
                }
            }

            // Measurement phase
            if (_config.Verbose)
            {
                Console.WriteLine($"    Measuring: {_config.MeasurementRuns} runs");
            }

            var metrics = new List<OperationMetrics>();
            for (int i = 0; i < _config.MeasurementRuns; i++)
            {
                var metric = await MeasureOperationAsync(operationName, operation);
                metrics.Add(metric);
            }

            return CalculateStats(operationName, metrics);
        }

        /// <summary>
        /// Measure a single operation execution
        /// </summary>
        private async Task<OperationMetrics> MeasureOperationAsync(
            string operationName,
            Func<Task> operation)
        {
            long memBefore = 0;
            if (_config.CollectMemoryStats)
            {
                GC.Collect();
                GC.WaitForPendingFinalizers();
                GC.Collect();
                memBefore = GC.GetTotalMemory(false);
            }

            var stopwatch = Stopwatch.StartNew();
            var timestamp = DateTime.UtcNow;

            await operation();

            stopwatch.Stop();

            long memoryUsed = 0;
            if (_config.CollectMemoryStats)
            {
                var memAfter = GC.GetTotalMemory(false);
                memoryUsed = memAfter - memBefore;
            }

            return new OperationMetrics(
                operationName,
                stopwatch.Elapsed,
                memoryUsed,
                timestamp
            );
        }

        /// <summary>
        /// Calculate statistics from metrics
        /// </summary>
        private BenchmarkStats CalculateStats(
            string operationName,
            List<OperationMetrics> metrics)
        {
            var times = metrics
                .Select(m => m.ExecutionTime.TotalMilliseconds)
                .OrderBy(t => t)
                .ToList();

            int totalRuns = times.Count;
            double minTime = times.First();
            double maxTime = times.Last();
            double avgTime = times.Average();
            double medianTime = times[totalRuns / 2];
            double p95Time = times[(int)(totalRuns * 0.95)];
            double p99Time = times[(int)(totalRuns * 0.99)];

            // Calculate standard deviation
            double variance = times.Sum(t => Math.Pow(t - avgTime, 2)) / totalRuns;
            double stdDevMs = Math.Sqrt(variance);

            // Calculate throughput (ops/sec)
            double totalTimeSeconds = times.Sum() / 1000.0;
            double throughput = totalRuns / totalTimeSeconds;

            return new BenchmarkStats(
                operationName,
                totalRuns,
                TimeSpan.FromMilliseconds(minTime),
                TimeSpan.FromMilliseconds(maxTime),
                TimeSpan.FromMilliseconds(avgTime),
                TimeSpan.FromMilliseconds(medianTime),
                TimeSpan.FromMilliseconds(p95Time),
                TimeSpan.FromMilliseconds(p99Time),
                stdDevMs,
                throughput
            );
        }

        /// <summary>
        /// Quick benchmark helper
        /// </summary>
        public static async Task<BenchmarkStats> QuickBenchmarkAsync(
            IComplianceEngine engine,
            string operationName,
            Func<Task> operation,
            int runs = 100)
        {
            var config = new BenchmarkConfig(
                WarmupRuns: 5,
                MeasurementRuns: runs,
                Verbose: false
            );

            var runner = new BenchmarkRunner(engine, config);
            return await runner.BenchmarkOperationAsync(operationName, operation);
        }
    }
}

