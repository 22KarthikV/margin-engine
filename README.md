# Margin Engine

An event-driven margin engine. Trades arrive on Kafka, the engine recalculates margin incrementally, raises a margin call once per breach, and pushes live exposure to a React dashboard over WebSocket.

**Status: scaffold.** The engine core is implemented and unit tested. The Kafka, PostgreSQL and WebSocket wiring and the dashboard are in place but have not had a first end-to-end run, and **no performance or coverage numbers have been measured yet**. See [docs/RESULTS.md](docs/RESULTS.md); nothing should be quoted from this project until that file has measured values.

## What it solves

A trading desk needs to know, as trades stream in, whether each account still covers its margin. Doing that by recomputing every position on every trade does not scale, and at-least-once delivery means the same trade can arrive twice or out of order. A duplicate must not move margin or raise a second call. This project is built around those two problems: **incremental recalculation** and **idempotent processing**.

## How it works

```
Kafka "trades" --> TradeConsumer --> MarginEngine --+--> margin_calls (PostgreSQL, keyed by call id)
                                                    +--> /topic/exposure (STOMP WebSocket) --> React dashboard
```

- **Incremental.** Each position stores its last requirement. A trade re-prices only the positions in the traded instrument and adjusts each account total by the difference. Work per trade scales with the number of holders of that instrument, not with all positions.
- **Idempotent.** A repeated `eventId` is ignored. An event whose `sequence` is not newer than the position's last applied sequence is ignored, so late events cannot move margin.
- **One call per breach.** A margin call is raised only when an account moves from covered to under-collateralised. Its id is `account#n`, and the table is keyed by that id, so storing it twice is harmless.
- **Single writer.** The engine is synchronised and the Kafka listener runs with concurrency 1. To scale out, partition the topic by instrument and run one engine per partition.
- **Pure core.** `engine/` and `domain/` have no Spring or Kafka dependency, so they are unit tested and benchmarked on their own.

## Layout

| Path | Purpose |
|---|---|
| `src/main/java/.../domain` | Trade, position key, margin rule, margin call, exposure |
| `src/main/java/.../engine` | `MarginEngine`, the incremental core |
| `src/main/java/.../ingest` | Kafka consumer |
| `src/main/java/.../api`, `config` | WebSocket publisher and configuration |
| `src/main/java/.../persistence` | JPA entity and repository, Flyway migration in `resources/db/migration` |
| `src/main/java/.../bench` | In-process benchmark on synthetic data |
| `src/test` | JUnit 5 tests |
| `dashboard/` | React + TypeScript + Vite live exposure view |

## Run it

Requires Java 21, Maven, Docker and Node 20+.

```bash
docker compose up -d                 # PostgreSQL and Kafka
mvn spring-boot:run                  # engine on :8080, listens on topic "trades"
cd dashboard && npm install && npm run dev   # http://localhost:5173
```

Send a trade (JSON, keyed by account):

```bash
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic trades
docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic trades <<'EOF'
{"eventId":"e1","sequence":1,"account":"ACC-1","instrument":"AAPL","side":"BUY","quantity":100,"price":190.25}
EOF
```

## Test

```bash
mvn test                                              # unit tests
mvn verify                                            # also writes the JaCoCo report to target/site/jacoco
mvn org.pitest:pitest-maven:mutationCoverage          # mutation testing, report in target/pit-reports
```

## Benchmark (synthetic data)

```bash
mvn -q package -DskipTests
java -cp target/classes com.karthikv.margin.bench.EngineBenchmark [positions] [trades] [seed] [accounts]
# defaults: 100000 20000 42 100
```

It measures the engine alone: no Kafka, no database, no network. It injects duplicate and late events, then reports throughput, latency percentiles, the cost of a full recompute for comparison, the number of duplicate call ids, and whether the incremental totals match a full pass. `accounts` is also the holders per instrument and is the main knob for per-trade cost. Record each run in [docs/RESULTS.md](docs/RESULTS.md) with the machine, JDK and parameters.

## Not done yet

- First end-to-end run of Kafka, PostgreSQL, WebSocket and the dashboard.
- Measured results, including coverage and mutation score.
- Integration tests with Testcontainers.
- Per-instrument margin rates (the current rule is one percentage of notional).
- Scale-out by partition, as described above.
