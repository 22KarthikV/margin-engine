# Results

**No results have been measured yet.** Fill in this file from real runs, and only then quote numbers anywhere else (CV, README, interviews).

All benchmark data is **synthetic**. Say so whenever a number from this file is quoted.

## Environment (fill in)

| Item | Value |
|---|---|
| Machine / CPU / RAM | |
| OS | |
| JDK (`java -version`) | |
| Commit | |
| Date | |

## Engine benchmark

Command: `java -cp target/classes com.karthikv.margin.bench.EngineBenchmark <positions> <trades> <seed> <accounts>`

| Run | Positions | Holders per instrument | Trades sent | Throughput (trades/s) | p50 (µs) | p99 (µs) | Full recompute (ms) | Duplicate call ids | Incremental vs full mismatches |
|---|---|---|---|---|---|---|---|---|---|
| 1 | | | | | | | | | |
| 2 | | | | | | | | | |
| 3 | | | | | | | | | |

Report the median of at least three runs and discard the first run after a cold start.

## Tests

| Measure | Command | Result |
|---|---|---|
| Unit tests | `mvn test` | |
| Line / branch coverage | `mvn verify`, `target/site/jacoco/index.html` | |
| Mutation score | `mvn org.pitest:pitest-maven:mutationCoverage` | |

## End to end (needs Docker)

| Measure | How measured | Result |
|---|---|---|
| Trade to dashboard lag (p50 / p99) | Dashboard lag readout, engine and browser on the same machine | |
| Sustained trades/sec through Kafka | | |
| Duplicate margin calls with at-least-once redelivery | Replay the same topic twice and count rows in `margin_calls` | |
