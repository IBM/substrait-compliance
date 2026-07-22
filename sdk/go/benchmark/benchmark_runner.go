package benchmark

import (
	"fmt"
	"math"
	"runtime"
	"sort"
	"strings"
	"time"

	"github.com/IBM/substrait-compliance/sdk/go"
)

// OperationMetrics represents performance metrics for a single operation
type OperationMetrics struct {
	OperationName string
	ExecutionTime time.Duration
	MemoryUsed    uint64
	Timestamp     time.Time
}

// BenchmarkStats contains statistical analysis of benchmark results
type BenchmarkStats struct {
	OperationName string
	TotalRuns     int
	MinTime       time.Duration
	MaxTime       time.Duration
	AvgTime       time.Duration
	MedianTime    time.Duration
	P95Time       time.Duration
	P99Time       time.Duration
	StdDevMs      float64
	Throughput    float64 // operations per second
}

// Summary returns a formatted summary string
func (s *BenchmarkStats) Summary() string {
	return fmt.Sprintf(`Operation: %s
Total Runs: %d
Min Time: %dms
Max Time: %dms
Avg Time: %dms
Median Time: %dms
P95 Time: %dms
P99 Time: %dms
Std Dev: %.2fms
Throughput: %.2f ops/sec
`, s.OperationName, s.TotalRuns,
		s.MinTime.Milliseconds(), s.MaxTime.Milliseconds(),
		s.AvgTime.Milliseconds(), s.MedianTime.Milliseconds(),
		s.P95Time.Milliseconds(), s.P99Time.Milliseconds(),
		s.StdDevMs, s.Throughput)
}

// BenchmarkResult contains complete benchmark results
type BenchmarkResult struct {
	EngineName    string
	BenchmarkName string
	Stats         []BenchmarkStats
	TotalDuration time.Duration
	Timestamp     time.Time
}

// Summary returns a formatted summary of all results
func (r *BenchmarkResult) Summary() string {
	var sb strings.Builder
	sb.WriteString(fmt.Sprintf("Benchmark: %s\n", r.BenchmarkName))
	sb.WriteString(fmt.Sprintf("Engine: %s\n", r.EngineName))
	sb.WriteString(fmt.Sprintf("Total Duration: %dms\n\n", r.TotalDuration.Milliseconds()))

	for _, stat := range r.Stats {
		sb.WriteString(stat.Summary())
		sb.WriteString("\n")
	}

	return sb.String()
}

// ToCSV exports results to CSV format
func (r *BenchmarkResult) ToCSV() string {
	var sb strings.Builder
	sb.WriteString("Engine,Benchmark,Operation,TotalRuns,MinMs,MaxMs,AvgMs,MedianMs,P95Ms,P99Ms,StdDev,Throughput\n")

	for _, stat := range r.Stats {
		sb.WriteString(fmt.Sprintf("%s,%s,%s,%d,%d,%d,%d,%d,%d,%d,%.2f,%.2f\n",
			r.EngineName, r.BenchmarkName, stat.OperationName, stat.TotalRuns,
			stat.MinTime.Milliseconds(), stat.MaxTime.Milliseconds(),
			stat.AvgTime.Milliseconds(), stat.MedianTime.Milliseconds(),
			stat.P95Time.Milliseconds(), stat.P99Time.Milliseconds(),
			stat.StdDevMs, stat.Throughput))
	}

	return sb.String()
}

// BenchmarkConfig configures benchmark execution
type BenchmarkConfig struct {
	WarmupRuns          int
	MeasurementRuns     int
	Parallelism         int
	CollectMemoryStats  bool
	Verbose             bool
}

// DefaultConfig returns default benchmark configuration
func DefaultConfig() BenchmarkConfig {
	return BenchmarkConfig{
		WarmupRuns:         5,
		MeasurementRuns:    100,
		Parallelism:        1,
		CollectMemoryStats: true,
		Verbose:            false,
	}
}

// BenchmarkRunner runs performance benchmarks
type BenchmarkRunner struct {
	engine compliance.ComplianceEngine
	config BenchmarkConfig
}

// NewBenchmarkRunner creates a new benchmark runner
func NewBenchmarkRunner(engine compliance.ComplianceEngine, config BenchmarkConfig) *BenchmarkRunner {
	return &BenchmarkRunner{
		engine: engine,
		config: config,
	}
}

// RunBenchmark runs a complete benchmark suite
func (r *BenchmarkRunner) RunBenchmark(benchmarkName string, operations []Operation) (*BenchmarkResult, error) {
	startTime := time.Now()

	if r.config.Verbose {
		fmt.Printf("Starting benchmark: %s\n", benchmarkName)
		fmt.Printf("Warmup runs: %d\n", r.config.WarmupRuns)
		fmt.Printf("Measurement runs: %d\n", r.config.MeasurementRuns)
	}

	stats := make([]BenchmarkStats, 0, len(operations))
	for _, op := range operations {
		stat, err := r.BenchmarkOperation(op.Name, op.Func)
		if err != nil {
			return nil, fmt.Errorf("benchmark operation %s failed: %w", op.Name, err)
		}
		stats = append(stats, stat)
	}

	endTime := time.Now()
	totalDuration := endTime.Sub(startTime)

	info := r.engine.GetInfo()
	return &BenchmarkResult{
		EngineName:    info.Name,
		BenchmarkName: benchmarkName,
		Stats:         stats,
		TotalDuration: totalDuration,
		Timestamp:     startTime,
	}, nil
}

// Operation represents a benchmarkable operation
type Operation struct {
	Name string
	Func func() error
}

// BenchmarkOperation benchmarks a single operation
func (r *BenchmarkRunner) BenchmarkOperation(operationName string, operation func() error) (BenchmarkStats, error) {
	if r.config.Verbose {
		fmt.Printf("  Benchmarking: %s\n", operationName)
	}

	// Warmup phase
	if r.config.WarmupRuns > 0 {
		if r.config.Verbose {
			fmt.Printf("    Warmup: %d runs\n", r.config.WarmupRuns)
		}
		for i := 0; i < r.config.WarmupRuns; i++ {
			if err := operation(); err != nil {
				return BenchmarkStats{}, fmt.Errorf("warmup failed: %w", err)
			}
		}
	}

	// Measurement phase
	if r.config.Verbose {
		fmt.Printf("    Measuring: %d runs\n", r.config.MeasurementRuns)
	}

	metrics := make([]OperationMetrics, 0, r.config.MeasurementRuns)
	for i := 0; i < r.config.MeasurementRuns; i++ {
		metric, err := r.measureOperation(operationName, operation)
		if err != nil {
			return BenchmarkStats{}, fmt.Errorf("measurement failed: %w", err)
		}
		metrics = append(metrics, metric)
	}

	return r.calculateStats(operationName, metrics), nil
}

// measureOperation measures a single operation execution
func (r *BenchmarkRunner) measureOperation(operationName string, operation func() error) (OperationMetrics, error) {
	var memBefore, memAfter runtime.MemStats
	if r.config.CollectMemoryStats {
		runtime.ReadMemStats(&memBefore)
	}

	startTime := time.Now()
	timestamp := time.Now()

	err := operation()
	if err != nil {
		return OperationMetrics{}, err
	}

	executionTime := time.Since(startTime)

	var memoryUsed uint64
	if r.config.CollectMemoryStats {
		runtime.ReadMemStats(&memAfter)
		memoryUsed = memAfter.TotalAlloc - memBefore.TotalAlloc
	}

	return OperationMetrics{
		OperationName: operationName,
		ExecutionTime: executionTime,
		MemoryUsed:    memoryUsed,
		Timestamp:     timestamp,
	}, nil
}

// calculateStats calculates statistics from metrics
func (r *BenchmarkRunner) calculateStats(operationName string, metrics []OperationMetrics) BenchmarkStats {
	times := make([]int64, len(metrics))
	for i, m := range metrics {
		times[i] = m.ExecutionTime.Nanoseconds()
	}

	sort.Slice(times, func(i, j int) bool { return times[i] < times[j] })

	totalRuns := len(times)
	minTime := time.Duration(times[0])
	maxTime := time.Duration(times[totalRuns-1])

	var sum int64
	for _, t := range times {
		sum += t
	}
	avgTime := time.Duration(sum / int64(totalRuns))

	medianTime := time.Duration(times[totalRuns/2])
	p95Time := time.Duration(times[int(float64(totalRuns)*0.95)])
	p99Time := time.Duration(times[int(float64(totalRuns)*0.99)])

	// Calculate standard deviation
	var variance float64
	avgNanos := float64(avgTime.Nanoseconds())
	for _, t := range times {
		diff := float64(t) - avgNanos
		variance += diff * diff
	}
	variance /= float64(totalRuns)
	stdDevMs := math.Sqrt(variance) / 1_000_000.0

	// Calculate throughput
	totalTimeSeconds := float64(sum) / 1_000_000_000.0
	throughput := float64(totalRuns) / totalTimeSeconds

	return BenchmarkStats{
		OperationName: operationName,
		TotalRuns:     totalRuns,
		MinTime:       minTime,
		MaxTime:       maxTime,
		AvgTime:       avgTime,
		MedianTime:    medianTime,
		P95Time:       p95Time,
		P99Time:       p99Time,
		StdDevMs:      stdDevMs,
		Throughput:    throughput,
	}
}

// QuickBenchmark runs a quick benchmark of a single operation
func QuickBenchmark(engine compliance.ComplianceEngine, operationName string, operation func() error, runs int) (BenchmarkStats, error) {
	config := BenchmarkConfig{
		WarmupRuns:      5,
		MeasurementRuns: runs,
		Verbose:         false,
	}

	runner := NewBenchmarkRunner(engine, config)
	return runner.BenchmarkOperation(operationName, operation)
}

