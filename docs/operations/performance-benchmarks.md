# Performance benchmarks

We deliberately keep this **directional**, not publication-grade. Benchmarks live under
`src/test/java/com/ewos/benchmarks/`, are tagged `@Tag("benchmark")`, and are excluded from the
normal `mvn test` run (`maven-surefire-plugin` `<excludedGroups>benchmark</excludedGroups>`).

## Why not JMH

JMH is the right answer for library-level microbenchmarks, but it comes with a separate build
graph, a forked JVM per benchmark, and results that non-perf engineers cannot read at a glance. For
what we need — "did this commit make BCrypt hashing 10× slower?" — a JUnit-driven helper is enough
and it lives next to the rest of the test suite.

## Running

```bash
# Run every benchmark
mvn test -Dgroups=benchmark

# Run a single benchmark class
mvn test -Dgroups=benchmark -Dtest=HotPathBenchmarks

# Run one method
mvn test -Dgroups=benchmark -Dtest=HotPathBenchmarks#jwtIssuanceBenchmark
```

Benchmarks print to stdout via `BenchmarkSupport`:

```
BENCH jwt.issue.access                        mean=42.13 µs  min=38.02 µs  p95=48.71 µs  max=61.55 µs
BENCH bcrypt.encode.strength10                mean=78.42 ms  min=76.10 ms  p95=82.44 ms  max=88.90 ms
```

## Writing a new benchmark

1. Create a class under `src/test/java/com/ewos/benchmarks/`.
2. Annotate the class with `@Tag("benchmark")`.
3. Use `BenchmarkSupport.run(name, warmup, iterations, inner, op)` and read the returned
   `BenchmarkResult`.
4. Assert a **loose** sanity bound (order-of-magnitude, not a tight number) so a broken benchmark
   fails CI, but a normal CI-host slowdown does not.

## What NOT to do

- Don't add benchmarks that talk to the database or an external service — those belong in
  `mvn verify` integration tests, not in a benchmark harness.
- Don't tighten the sanity bounds to catch small regressions; that's what a proper JMH suite would
  be for. Small regressions are best caught with production APM.
- Don't wire the benchmark run into CI's default gate. It's a local + on-demand tool.
