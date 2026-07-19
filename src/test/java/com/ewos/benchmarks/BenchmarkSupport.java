package com.ewos.benchmarks;

import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Lightweight microbenchmark helper. Not JMH — no forks, no PID isolation, no bytecode-level
 * fences. What it gives us:
 *
 * <ul>
 *   <li>A warm-up phase so JIT compilation happens before we measure.
 *   <li>N iterations × M inner invocations per iteration.
 *   <li>Simple mean/min/max/p95 in nanoseconds and printed to stdout.
 * </ul>
 *
 * The point is a directional signal in CI ("did this get 10× slower?"), not publication-quality
 * numbers. Tag your benchmark tests with {@code @Tag("benchmark")} so they're excluded from the
 * default {@code mvn test} run; execute them explicitly with {@code mvn test -Dgroups=benchmark}.
 */
public final class BenchmarkSupport {

    private BenchmarkSupport() {}

    public static <T> BenchmarkResult run(
            String name, int warmup, int iterations, int inner, Supplier<T> op) {
        // Warm-up: ignore results, let the JIT do its thing.
        for (int i = 0; i < warmup; i++) {
            for (int j = 0; j < inner; j++) {
                consume(op.get());
            }
        }

        long[] samples = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long t0 = System.nanoTime();
            for (int j = 0; j < inner; j++) {
                consume(op.get());
            }
            samples[i] = (System.nanoTime() - t0) / inner;
        }

        BenchmarkResult result = BenchmarkResult.of(name, samples);
        System.out.println(result.pretty());
        return result;
    }

    // Prevents the JIT from proving the return value is unused.
    private static volatile Object blackhole;

    private static void consume(Object value) {
        blackhole = value;
    }

    public record BenchmarkResult(
            String name, long meanNanos, long minNanos, long maxNanos, long p95Nanos) {

        static BenchmarkResult of(String name, long[] samples) {
            long[] sorted = samples.clone();
            java.util.Arrays.sort(sorted);
            long sum = 0;
            for (long s : sorted) {
                sum += s;
            }
            long mean = sum / sorted.length;
            long min = sorted[0];
            long max = sorted[sorted.length - 1];
            int p95Idx = Math.min(sorted.length - 1, (int) Math.ceil(0.95 * sorted.length) - 1);
            long p95 = sorted[Math.max(0, p95Idx)];
            return new BenchmarkResult(name, mean, min, max, p95);
        }

        public String pretty() {
            return String.format(
                    Locale.ROOT,
                    "BENCH %-40s mean=%s  min=%s  p95=%s  max=%s",
                    name,
                    fmt(meanNanos),
                    fmt(minNanos),
                    fmt(p95Nanos),
                    fmt(maxNanos));
        }

        private static String fmt(long nanos) {
            if (nanos >= 1_000_000L) {
                return String.format(Locale.ROOT, "%.2f ms", nanos / 1_000_000.0);
            }
            if (nanos >= 1_000L) {
                return String.format(Locale.ROOT, "%.2f µs", nanos / 1_000.0);
            }
            return nanos + " ns";
        }

        public Duration mean() {
            return Duration.ofNanos(meanNanos);
        }
    }
}
