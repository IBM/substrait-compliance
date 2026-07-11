"""Performance benchmarking for Substrait compliance engines."""

import time
import statistics
import tracemalloc
from dataclasses import dataclass
from datetime import datetime
from typing import List, Callable, Optional, Tuple
from ..compliance_engine import ComplianceEngine


@dataclass
class OperationMetrics:
    """Performance metrics for a single operation."""
    operation_name: str
    execution_time: float  # seconds
    memory_used: int  # bytes
    timestamp: datetime


@dataclass
class BenchmarkStats:
    """Benchmark statistics."""
    operation_name: str
    total_runs: int
    min_time: float
    max_time: float
    avg_time: float
    median_time: float
    p95_time: float
    p99_time: float
    std_dev_ms: float
    throughput: float  # operations per second

    def summary(self) -> str:
        """Return formatted summary string."""
        return f"""Operation: {self.operation_name}
Total Runs: {self.total_runs}
Min Time: {self.min_time * 1000:.2f}ms
Max Time: {self.max_time * 1000:.2f}ms
Avg Time: {self.avg_time * 1000:.2f}ms
Median Time: {self.median_time * 1000:.2f}ms
P95 Time: {self.p95_time * 1000:.2f}ms
P99 Time: {self.p99_time * 1000:.2f}ms
Std Dev: {self.std_dev_ms:.2f}ms
Throughput: {self.throughput:.2f} ops/sec
"""


@dataclass
class BenchmarkResult:
    """Complete benchmark result."""
    engine_name: str
    benchmark_name: str
    stats: List[BenchmarkStats]
    total_duration: float
    timestamp: datetime

    def summary(self) -> str:
        """Return formatted summary of all results."""
        output = f"Benchmark: {self.benchmark_name}\n"
        output += f"Engine: {self.engine_name}\n"
        output += f"Total Duration: {self.total_duration * 1000:.2f}ms\n\n"

        for stat in self.stats:
            output += stat.summary() + "\n"

        return output

    def to_csv(self) -> str:
        """Export results to CSV format."""
        csv = "Engine,Benchmark,Operation,TotalRuns,MinMs,MaxMs,AvgMs,MedianMs,P95Ms,P99Ms,StdDev,Throughput\n"

        for stat in self.stats:
            csv += f"{self.engine_name},{self.benchmark_name},{stat.operation_name},"
            csv += f"{stat.total_runs},{stat.min_time * 1000:.2f},{stat.max_time * 1000:.2f},"
            csv += f"{stat.avg_time * 1000:.2f},{stat.median_time * 1000:.2f},"
            csv += f"{stat.p95_time * 1000:.2f},{stat.p99_time * 1000:.2f},"
            csv += f"{stat.std_dev_ms:.2f},{stat.throughput:.2f}\n"

        return csv


@dataclass
class BenchmarkConfig:
    """Configuration for benchmark execution."""
    warmup_runs: int = 5
    measurement_runs: int = 100
    parallelism: int = 1
    collect_memory_stats: bool = True
    verbose: bool = False


class BenchmarkRunner:
    """Runs performance benchmarks on compliance engines."""

    def __init__(self, engine: ComplianceEngine, config: Optional[BenchmarkConfig] = None):
        """Initialize benchmark runner."""
        self.engine = engine
        self.config = config or BenchmarkConfig()

    def run_benchmark(
        self,
        benchmark_name: str,
        operations: List[Tuple[str, Callable]]
    ) -> BenchmarkResult:
        """Run a complete benchmark suite."""
        start_time = time.time()

        if self.config.verbose:
            print(f"Starting benchmark: {benchmark_name}")
            print(f"Warmup runs: {self.config.warmup_runs}")
            print(f"Measurement runs: {self.config.measurement_runs}")

        stats = []
        for name, operation in operations:
            stat = self.benchmark_operation(name, operation)
            stats.append(stat)

        end_time = time.time()
        total_duration = end_time - start_time

        info = self.engine.get_info()
        return BenchmarkResult(
            engine_name=info.name,
            benchmark_name=benchmark_name,
            stats=stats,
            total_duration=total_duration,
            timestamp=datetime.fromtimestamp(start_time)
        )

    def benchmark_operation(
        self,
        operation_name: str,
        operation: Callable
    ) -> BenchmarkStats:
        """Benchmark a single operation."""
        if self.config.verbose:
            print(f"  Benchmarking: {operation_name}")

        # Warmup phase
        if self.config.warmup_runs > 0:
            if self.config.verbose:
                print(f"    Warmup: {self.config.warmup_runs} runs")

            for _ in range(self.config.warmup_runs):
                operation()

        # Measurement phase
        if self.config.verbose:
            print(f"    Measuring: {self.config.measurement_runs} runs")

        metrics = []
        for _ in range(self.config.measurement_runs):
            metric = self._measure_operation(operation_name, operation)
            metrics.append(metric)

        return self._calculate_stats(operation_name, metrics)

    def _measure_operation(
        self,
        operation_name: str,
        operation: Callable
    ) -> OperationMetrics:
        """Measure a single operation execution."""
        if self.config.collect_memory_stats:
            tracemalloc.start()

        start_time = time.perf_counter()
        timestamp = datetime.now()

        operation()

        end_time = time.perf_counter()
        execution_time = end_time - start_time

        memory_used = 0
        if self.config.collect_memory_stats:
            current, peak = tracemalloc.get_traced_memory()
            tracemalloc.stop()
            memory_used = peak

        return OperationMetrics(
            operation_name=operation_name,
            execution_time=execution_time,
            memory_used=memory_used,
            timestamp=timestamp
        )

    def _calculate_stats(
        self,
        operation_name: str,
        metrics: List[OperationMetrics]
    ) -> BenchmarkStats:
        """Calculate statistics from metrics."""
        times = sorted([m.execution_time for m in metrics])
        total_runs = len(times)

        min_time = times[0]
        max_time = times[-1]
        avg_time = statistics.mean(times)
        median_time = statistics.median(times)
        p95_time = times[int(total_runs * 0.95)]
        p99_time = times[int(total_runs * 0.99)]

        # Calculate standard deviation
        std_dev = statistics.stdev(times) if total_runs > 1 else 0.0
        std_dev_ms = std_dev * 1000

        # Calculate throughput (ops/sec)
        total_time_seconds = sum(times)
        throughput = total_runs / total_time_seconds if total_time_seconds > 0 else 0.0

        return BenchmarkStats(
            operation_name=operation_name,
            total_runs=total_runs,
            min_time=min_time,
            max_time=max_time,
            avg_time=avg_time,
            median_time=median_time,
            p95_time=p95_time,
            p99_time=p99_time,
            std_dev_ms=std_dev_ms,
            throughput=throughput
        )

    @staticmethod
    def quick_benchmark(
        engine: ComplianceEngine,
        operation_name: str,
        operation: Callable,
        runs: int = 100
    ) -> BenchmarkStats:
        """Quick benchmark helper."""
        config = BenchmarkConfig(
            warmup_runs=5,
            measurement_runs=runs,
            verbose=False
        )

        runner = BenchmarkRunner(engine, config)
        return runner.benchmark_operation(operation_name, operation)

