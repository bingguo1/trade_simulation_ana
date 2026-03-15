# Kafka Tick Simulation Framework

A real-time stock market data simulation, ingestion, and analysis platform built with Java 21, Apache Kafka, and Docker. Simulates 500+ S&P 500 tickers generating synthetic trades and quotes, streams them through a 3-broker Kafka cluster, and provides live WebUI dashboards and PostgreSQL persistence.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Components](#components)
  - [Simulator](#simulator)
  - [Kafka Cluster](#kafka-cluster)
  - [Consumer: Monitor](#consumer-monitor)
  - [Consumer: Bars](#consumer-bars)
  - [Consumer: Writer](#consumer-writer)
- [Data Files](#data-files)
- [Prerequisites](#prerequisites)
- [Build & Run](#build--run)
- [WebUI Guide](#webui-guide)
- [Configuration](#configuration)
- [Kafka Cluster Details](#kafka-cluster-details)
- [Database Schema](#database-schema)
- [Extending the System](#extending-the-system)

---

## Overview

This framework simulates a full market data pipeline:

1. **Simulate** — A Poisson-process event generator creates realistic bid/ask quotes and trade events for every S&P 500 ticker, using historical volumes and returns as model parameters.
2. **Stream** — Events flow into a 3-broker Kafka cluster across two topics (`trades`, `quotes`), each with 10 partitions, mixing high-volume and low-volume tickers across partitions.
3. **Consume** — Three independent consumer groups process the stream in parallel:
   - **Monitor**: maintains a live-updating table of latest market state per ticker
   - **Bars**: aggregates 1-second OHLCV bars and renders interactive candlestick charts
   - **Writer**: batch-inserts all events into PostgreSQL for historical analysis

---

## Architecture

```
                         ┌─────────────────────────────────────────┐
                         │           SIMULATOR (Java 21)           │
                         │                                         │
                         │  sp500.csv ──► MarketDataLoader         │
                         │  market_data.csv ──► TickerInfo×502     │
                         │                                         │
                         │  ┌──────────┐   ┌──────────┐           │
                         │  │ Ticker   │   │ Ticker   │   ×502    │
                         │  │Simulator │   │Simulator │  virtual  │
                         │  │(virtual  │   │(virtual  │  threads  │
                         │  │ thread)  │   │ thread)  │           │
                         │  └────┬─────┘   └────┬─────┘           │
                         │       │ GBM price     │ Poisson         │
                         │       │ model         │ arrivals        │
                         └───────┼───────────────┼─────────────────┘
                                 │               │
                    ┌────────────▼───────────────▼────────────┐
                    │         KAFKA CLUSTER (KRaft)           │
                    │                                         │
                    │  ┌─────────┐ ┌─────────┐ ┌─────────┐  │
                    │  │ Broker1 │ │ Broker2 │ │ Broker3 │  │
                    │  │ :9092   │ │ :9093   │ │ :9094   │  │
                    │  └─────────┘ └─────────┘ └─────────┘  │
                    │                                         │
                    │  Topic: trades  (10 partitions, RF=3)  │
                    │  Topic: quotes  (10 partitions, RF=3)  │
                    │                                         │
                    │  Partition assignment (per ticker):     │
                    │  P0: NVDA, WMT, ... (~50 tickers each) │
                    │  P1: AAPL, XOM, ...                    │
                    │  ... mixed high/low volume tickers      │
                    └────────┬──────────────┬─────────────────┘
                             │              │
           ┌─────────────────┼──────────────┼────────────────────────┐
           │                 │              │                        │
           ▼                 ▼              ▼                        ▼
  ┌────────────────┐ ┌──────────────┐ ┌──────────────┐    ┌────────────────┐
  │consumer-monitor│ │consumer-bars │ │consumer-writer│   │   Kafka UI     │
  │  (group-id:    │ │  (group-id:  │ │  (group-id:  │    │  port 8090     │
  │consumer-monitor│ │consumer-bars)│ │consumer-writer│   └────────────────┘
  │    port 8080)  │ │  port 8081)  │ │   port 8082) │
  │                │ │              │ │              │
  │ WebSocket →    │ │ WebSocket →  │ │ JdbcTemplate │
  │ Live ticker    │ │ Candlestick  │ │ batch insert │
  │ state table    │ │ OHLCV charts │ │ → PostgreSQL │
  └────────────────┘ └──────────────┘ └──────┬───────┘
                                             │
                                    ┌────────▼────────┐
                                    │   PostgreSQL 16  │
                                    │   tickdata DB    │
                                    │  trades / quotes │
                                    └─────────────────┘
```

### Data Flow

```
Simulator                 Kafka                  Consumer
─────────                ───────                ─────────
TickerSimulator          trades topic           MarketDataConsumer
   │                         │                      │
   ├─ TradeEvent ──────────► P0..P9 ─────────────► TickerState update
   └─ QuoteEvent ──────────► P0..P9 ─────────────► TickerState update
                                                     │
                                              @Scheduled 500ms
                                                     │
                                              WebSocket broadcast
                                                     │
                                              Browser (STOMP/SockJS)
```

### Partition Strategy

502 tickers are ranked by average daily volume (descending), then assigned to 10 partitions using interleaved round-robin:

```
Rank:   1    2    3    4    5    ...  502
Ticker: NVDA AAPL MSFT AMZN GOOGL ...  (lowest vol)
        │    │    │    │    │
        P0   P1   P2   P3   P4   ...  P9   P0   P1 ...
```

This guarantees each partition contains exactly one high-volume "anchor" ticker surrounded by low-volume tickers, preventing hot partitions.

---

## Components

### Simulator

**Package**: `com.ticksim.simulator`
**Purpose**: Generates synthetic market data for all 502 S&P 500 tickers simultaneously.

#### Price Model — Geometric Brownian Motion (GBM)

```
S(t+dt) = S(t) × exp( (μ - ½σ²)·dt  +  σ·√dt·Z )

where:
  μ  = per-second drift   (from historical daily return / 23400)
  σ  = 0.002 per √second  (configurable)
  Z  ~ N(0,1)             (standard normal sample)
  dt = inter-arrival time (seconds)
```

#### Event Arrival — Poisson Process

Inter-arrival times are drawn from an exponential distribution:

```
dt ~ Exponential(λ)   →   dt = -ln(U) / λ,   U ~ Uniform(0,1)

Trade rate:  λ_trade = avgDailyVolume / 100 / 23400   (trades/sec)
Quote rate:  λ_quote = 10 × λ_trade
```

The `/ 100` converts share volume to trade count, assuming average trade size ≈ 100 shares.

Examples with realistic volumes:

| Ticker | Avg Daily Vol | Trade Rate | Quote Rate |
|--------|--------------|------------|------------|
| NVDA   | 385M         | ~164/s     | ~1640/s    |
| AAPL   | 78M          | ~33/s      | ~330/s     |
| MSFT   | 25M          | ~11/s      | ~110/s     |
| Small cap | 500K      | ~0.2/s     | ~2/s       |

#### Trade Size — Log-Normal

```
size = max(1, exp(4.48 + 0.5·Z))   where Z ~ N(0,1)
     → E[size] ≈ 100 shares
```

#### Quote Spread

```
spread = price × Uniform(0.01%, 0.10%)
bid    = price - spread/2
ask    = price + spread/2
bidSize, askSize ~ Uniform(50, 500) shares
```

#### Concurrency

One **virtual thread** (Java 21) per ticker. 502 virtual threads are created at startup with `Thread.ofVirtual().start()`. Virtual threads are cheap (< 1KB stack, OS-thread-pooled) — suitable for hundreds or thousands of I/O-bound loops.

```
SimulationEngine
 ├── VirtualThread[NVDA] → TickerSimulator → Kafka producer
 ├── VirtualThread[AAPL] → TickerSimulator → Kafka producer
 ├── VirtualThread[MSFT] → TickerSimulator → Kafka producer
 └── ... × 502
```

**Shutdown**: Each `TickerSimulator` checks a `volatile boolean running` flag. `SimulationEngine` calls `stop()` on each simulator when a JVM shutdown hook fires.

#### Kafka Producer Settings

| Setting | Value | Rationale |
|---------|-------|-----------|
| acks | 1 | Leader ack only — prioritize throughput |
| batch.size | 16384 | Batch up to 16KB |
| linger.ms | 5 | Wait up to 5ms to fill batch |
| key.serializer | StringSerializer | Ticker symbol as key |
| value.serializer | JsonSerializer | JSON payload |

---

### Kafka Cluster

**Mode**: KRaft (no ZooKeeper) — all three brokers act as combined broker + controller.

```
┌──────────────────────────────────────────────────────────┐
│                  KRaft Controller Quorum                  │
│                                                          │
│   kafka1 (node.id=1)   kafka2 (node.id=2)   kafka3 (3)  │
│   controller:9093      controller:9093       controller   │
│   broker:9092          broker:9092           broker:9092  │
│                                                          │
│   Quorum voters: 1@kafka1:9093, 2@kafka2:9093,           │
│                  3@kafka3:9093                           │
└──────────────────────────────────────────────────────────┘
```

#### Topic Configuration

| Property | trades | quotes |
|----------|--------|--------|
| Partitions | 10 | 10 |
| Replication factor | 3 | 3 |
| min.insync.replicas | 2 | 2 |
| retention.ms | 86400000 (24h) | 86400000 (24h) |
| max.message.bytes | 1048576 (1MB) | 1048576 (1MB) |

#### Consumer Groups

| Group ID | Topics | Concurrency | Offset Reset |
|----------|--------|-------------|--------------|
| consumer-monitor | trades, quotes | 1 consumer | latest |
| consumer-bars | trades | 1 consumer | latest |
| consumer-writer | trades, quotes | 1 consumer | earliest |

With 1 consumer per group and 10 partitions, one consumer handles all 10 partitions. Scale by increasing `spring.kafka.listener.concurrency` (max 10 to match partition count).

---

### Consumer: Monitor

**Port**: 8080
**Package**: `com.ticksim.monitor`

Maintains the latest market state for every ticker and streams snapshots to connected browsers via WebSocket every 500ms.

#### State Per Ticker (`TickerState`)

```java
volatile double latestBid, latestAsk, latestPrice;
volatile double latestTradePrice;
volatile int    latestTradeSize;
volatile String latestTradeSide;
AtomicLong      totalVolume;        // cumulative shares traded
// rolling 1-second counters:
double          tradesPerSec;
double          quotesPerSec;
double          priceChangePct;     // vs previous broadcast
```

#### WebSocket Protocol

```
Client → Server:  CONNECT to ws://host:8080/ws  (SockJS + STOMP)
Server → Client:  SEND /topic/monitor every 500ms
                  Payload: List<TickerSnapshot> (all tickers, JSON)
```

#### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/snapshot` | Full state snapshot (REST) |

#### Frontend Features

- Live table updating every 500ms (no page refresh)
- Columns: Ticker, Bid, Ask, Last Trade, Last Price, Change%, Volume, Trades/s, Quotes/s
- Click any column header to sort ascending/descending
- Ticker search/filter box
- Pagination (50 / 100 / 200 / All per page)
- Price flash animation: green background on uptick, red on downtick
- Connection status indicator (green dot = live, red = disconnected)
- Aggregate stats bar: total active tickers, total trades/s, total quotes/s

---

### Consumer: Bars

**Port**: 8081
**Package**: `com.ticksim.bars`

Aggregates 1-second OHLCV bars from the `trades` topic and renders interactive candlestick charts.

#### Bar Aggregation Logic

```
For each incoming TradeEvent:
  barSecond = floor(timestamp.epochSecond)

  if barSecond == currentBar.second:
    currentBar.high = max(high, price)
    currentBar.low  = min(low, price)
    currentBar.close = price
    currentBar.volume += size
  else:
    emit currentBar (completed)     →  WebSocket broadcast
    start new bar with this trade   →  open = high = low = close = price
```

Completed bars are kept in a `Deque<OHLCVBar>` (max 500 per ticker). In-progress bars are broadcast every 500ms so the chart animates in real time.

#### WebSocket Protocol

```
Completed bars:   /topic/bars          (BarSnapshot, on completion)
Live bar update:  /topic/bars/live     (Map<ticker, BarSnapshot>, every 500ms)
```

#### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/tickers` | Set of all known tickers |
| GET | `/api/bars?ticker=AAPL&limit=100` | Historical bars for a ticker |

#### Frontend Features

- Ticker selector dropdown with type-to-filter
- Candlestick chart (TradingView `lightweight-charts`) — last 200 completed bars
- Volume histogram synchronized with candle chart (shared time axis)
- In-progress (current second) bar rendered in a different color and updated live
- Crosshair tooltip showing O/H/L/C/V
- Recent bars table below chart (last 20 bars)
- Responsive layout — chart resizes with window

---

### Consumer: Writer

**Port**: 8082
**Package**: `com.ticksim.writer`

Persists all events to PostgreSQL using batch JDBC inserts.

#### Batch Insert Strategy

```
Consumer receives messages in batches (max.poll.records = 1000)
  → Appends to in-memory buffer (CopyOnWriteArrayList)
  → Flush triggered by:
       - Buffer size ≥ 1000 records    (size-based)
       - @Scheduled every 1 second      (time-based)
  → JdbcTemplate.batchUpdate() executes single INSERT with N rows
```

This avoids one INSERT per message (which would be ~10K ops/sec at peak), instead issuing one batch per second per type.

#### Throughput Characteristics

At a typical simulation rate with all 502 tickers:
- ~2,000–5,000 trades/sec across all tickers
- ~20,000–50,000 quotes/sec across all tickers
- Batching reduces PostgreSQL ops to ~2–10 batch inserts/sec

---

## Data Files

### `sp500.csv` (required)

Place in project root. Format:

```csv
Company,Symbol,Weight,Price,Chg,% Chg
Nvidia,NVDA,7.21%,182.65,4.83,(2.72%)
Apple Inc.,AAPL,6.20%,259.88,2.42,(0.94%)
"Tesla, Inc.",TSLA,2.43%,398.68,1.95,(0.49%)
```

| Column | Used For |
|--------|----------|
| Symbol | Kafka message key, partition assignment |
| Weight | Fallback volume estimation if market_data.csv absent |
| Price | Initial simulation price |
| % Chg | Ignored (single-day, not sufficient for drift) |

Parser handles: quoted company names with commas, `(2.72%)` negative format, `%` suffix on weights.

### `data/market_data.csv` (optional but recommended)

Provides 5 days of historical volume and return data per ticker. Place in `./data/`.

```csv
ticker,date,volume,close_price,daily_return
NVDA,2025-03-10,385420000,178.50,0.0025
NVDA,2025-03-11,412350000,181.20,0.0151
NVDA,2025-03-12,298760000,179.80,-0.0078
AAPL,2025-03-10,78450000,256.30,0.0012
```

| Column | Used For |
|--------|----------|
| ticker | Join to sp500.csv |
| volume | Average over 5 days → trade rate |
| daily_return | Average over 5 days → per-second drift μ |

**Fallback**: If `market_data.csv` is absent or a ticker has no entry, volume is estimated as:
```
estimatedVolume = weightPct × 2,000,000,000
```

A sample file with 20 major tickers (5 days each) is included. Replace with real data from your broker or financial data provider (Yahoo Finance, Bloomberg, Quandl, etc.).

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Docker | 24+ | Required |
| Docker Compose | v2 plugin | `docker compose` (not `docker-compose`) |
| Java JDK | 21+ | Only needed for local dev/IDE |
| Maven | 3.9+ | Only needed for local dev/IDE |

No other local installations are required — everything runs in containers.

---

## Build & Run

### Option 1: Full Docker Stack (recommended)

```bash
# Clone / enter project directory
cd kafka_tick_simulation

# Start all services (builds images on first run, ~5-10 min)
docker compose up --build

# Or start in background
docker compose up --build -d

# Watch logs
docker compose logs -f simulator
docker compose logs -f consumer-monitor
```

Services come up in this order (enforced by `depends_on` + health checks):

```
kafka1, kafka2, kafka3  →  kafka-setup (topic creation)  →  simulator
                                                          →  consumer-monitor
                                                          →  consumer-bars
                        →  postgres  →  consumer-writer
```

**First build is slow** because Docker pulls base images and Maven downloads dependencies (~800MB total). Subsequent builds use layer cache and are fast.

```bash
# Stop everything
docker compose down

# Stop and delete all data volumes (reset state)
docker compose down -v
```

### Option 2: Local Development

Run Kafka and PostgreSQL in Docker, services locally for faster iteration.

```bash
# Start only infrastructure
docker compose up -d kafka1 kafka2 kafka3 kafka-setup postgres kafka-ui

# Wait for topics to be created (~30 sec)
docker compose logs kafka-setup

# Build all modules
mvn clean package -DskipTests

# Module directories include .mvn/maven.config so mvn commands there
# still run through the root reactor and build sibling module dependencies.

# Run simulator (in separate terminal)
cd simulator
DATA_PATH=../data \
KAFKA_BOOTSTRAP_SERVERS=localhost:9092,localhost:9093,localhost:9094 \
mvn spring-boot:run

# Kafka uses dual listeners:
# - local host apps use localhost:9092,9093,9094
# - Docker services use kafka1:9092,kafka2:9092,kafka3:9092

# sp500.csv is auto-discovered from the repo root for local runs.

# Run consumer-monitor (in separate terminal)
cd consumer-monitor
KAFKA_BOOTSTRAP_SERVERS=localhost:9092,localhost:9093,localhost:9094 \
mvn spring-boot:run

# Run consumer-bars (in separate terminal)
cd consumer-bars
KAFKA_BOOTSTRAP_SERVERS=localhost:9092,localhost:9093,localhost:9094 \
mvn spring-boot:run

# Run consumer-writer (in separate terminal)
cd consumer-writer
KAFKA_BOOTSTRAP_SERVERS=localhost:9092,localhost:9093,localhost:9094 \
DATABASE_URL=jdbc:postgresql://localhost:5432/tickdata \
DB_USER=tickuser \
DB_PASSWORD=tickpass \
mvn spring-boot:run
```

### Build Individual Modules

```bash
# Build only the simulator jar (also builds common dependency)
mvn package -pl simulator -am -DskipTests

# Build all jars
mvn package -DskipTests

# Jars are in each module's target/ directory
ls simulator/target/*.jar
```

---

## WebUI Guide

| Service | URL | Description |
|---------|-----|-------------|
| Market Monitor | http://localhost:8080 | Live ticker state table |
| OHLCV Bars | http://localhost:8081 | Candlestick chart per ticker |
| Kafka UI | http://localhost:8090 | Kafka cluster & topic inspector |
| Writer (REST) | http://localhost:8082 | Health/status only |

### Market Monitor (port 8080)

1. Open http://localhost:8080
2. The table populates automatically as events arrive (within a few seconds)
3. Click column headers to sort — e.g., click **Trades/s** to find the busiest tickers
4. Type in the **Filter** box to narrow to specific tickers (e.g., `NVDA`)
5. Green/red flashes on the **Price** column indicate uptick/downtick
6. The **Conn** indicator (top right) shows WebSocket status

### OHLCV Bars (port 8081)

1. Open http://localhost:8081
2. Select a ticker from the dropdown (type to search, e.g., `APP` finds `AAPL`)
3. Candlestick chart loads with historical bars (if any) and updates in real time
4. Hover over the chart — crosshair shows exact O/H/L/C/V
5. Scroll or pinch to zoom the time axis
6. The current (in-progress) bar is shown at the far right and updates every 500ms
7. The volume histogram is synchronized with the candlestick time axis

### Kafka UI (port 8090)

- **Topics** tab: see message counts, partition distribution, throughput for `trades` and `quotes`
- **Consumer Groups** tab: check lag for each consumer group (should stay near 0)
- **Brokers** tab: see replica status and controller election

---

## Configuration

### Simulator (`simulator/src/main/resources/application.yml`)

```yaml
simulator:
  kafka:
    bootstrap-servers: localhost:9092,localhost:9093,localhost:9094
    trades-topic: trades
    quotes-topic: quotes
  data:
    sp500-csv: /data/sp500.csv
    market-data-csv: /data/market_data.csv
  num-partitions: 10
  trading-seconds-per-day: 23400   # 6.5 trading hours
  default-volatility: 0.002        # σ per √second
  batch-size: 500
  linger-ms: 5
```

**Key tuning knobs**:

| Property | Effect |
|----------|--------|
| `default-volatility` | Increase for more price movement, decrease for calmer market |
| `trading-seconds-per-day` | Decrease to simulate faster (higher trade rate) |
| `linger-ms` | Increase to get larger Kafka batches (less ops/sec, higher latency) |

### Environment Variables (all modules)

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092,...` | Kafka broker list for host-run apps (Docker services should use `kafka1:9092,kafka2:9092,kafka3:9092`) |
| `DATA_PATH` | `/data` | Directory containing CSV files (simulator only) |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/tickdata` | PostgreSQL JDBC URL (writer only) |
| `DB_USER` | `tickuser` | PostgreSQL username (writer only) |
| `DB_PASSWORD` | `tickpass` | PostgreSQL password (writer only) |

### Scaling Consumer Concurrency

To use more than 1 consumer thread per group (up to the number of partitions = 10):

```yaml
# In consumer-monitor/src/main/resources/application.yml
spring:
  kafka:
    listener:
      concurrency: 4   # 4 threads, each handles 2-3 partitions
```

Or set via environment variable: `SPRING_KAFKA_LISTENER_CONCURRENCY=4`

---

## Kafka Cluster Details

### KRaft Mode

This cluster uses Kafka's KRaft consensus protocol (introduced in Kafka 2.8, production-ready since 3.3). There is no ZooKeeper dependency.

```
Controller Quorum: kafka1:9093, kafka2:9093, kafka3:9093
Each node role:    broker + controller (combined mode)
Cluster ID:        MkU3OEVBNTcwNTJENDM2Qk  (fixed, base64 URL-safe)
```

The cluster ID must be identical across all brokers and must be a 22-character base64 URL-safe string. This is set via `KAFKA_KRAFT_CLUSTER_ID` in docker-compose.

### Broker Listener Modes (Dual Listener)

Kafka brokers are configured with two client listeners:

- `INTERNAL://kafkaN:9092` for containers on the Docker network
- `EXTERNAL://localhost:9092|9093|9094` for apps running on the host machine

Use bootstrap servers based on where the client runs:

- Host process (for example `mvn spring-boot:run` from module directories): `localhost:9092,localhost:9093,localhost:9094`
- Docker service (in `docker-compose.yml`): `kafka1:9092,kafka2:9092,kafka3:9092`

### Manual Topic Management

```bash
# Enter any Kafka broker container
docker exec -it kafka_tick_simulation-kafka1-1 bash

# List topics
kafka-topics.sh --bootstrap-server localhost:9092 --list

# Describe topic
kafka-topics.sh --bootstrap-server localhost:9092 \
  --describe --topic trades

# Check consumer group lag
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group consumer-monitor

# Reset consumer group offset to beginning
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group consumer-writer --reset-offsets \
  --topic trades --to-earliest --execute

# Consume messages manually (last 10 trades)
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic trades --from-beginning --max-messages 10

# Check which partition a ticker lands on
# Kafka uses murmur2 hash of the key (ticker symbol) to determine partition
# But we override this with explicit partition assignment in the producer
```

### Adding Brokers

To scale to 5 brokers, add to `docker-compose.yml`:

```yaml
kafka4:
  image: bitnamilegacy/kafka:3.7.0
  environment:
    KAFKA_CFG_NODE_ID: "4"
    KAFKA_CFG_PROCESS_ROLES: broker,controller
    KAFKA_CFG_CONTROLLER_QUORUM_VOTERS: "1@kafka1:9093,2@kafka2:9093,3@kafka3:9093,4@kafka4:9093,5@kafka5:9093"
    # ... same pattern as kafka1/2/3
```

Note: Controller quorum size should be odd (3, 5, 7) for fault tolerance. With 3 controllers, the cluster tolerates 1 failure. With 5, it tolerates 2.

### Increasing Partitions

To increase partitions (e.g., from 10 to 20):

```bash
# Add partitions — can only increase, never decrease
kafka-topics.sh --bootstrap-server localhost:9092 \
  --alter --topic trades --partitions 20

kafka-topics.sh --bootstrap-server localhost:9092 \
  --alter --topic quotes --partitions 20
```

Then update `simulator.num-partitions=20` in the simulator config so the partition mapper distributes tickers across all 20 partitions.

---

## Database Schema

```sql
-- Trades table
CREATE TABLE trades (
    id         BIGSERIAL PRIMARY KEY,
    ticker     VARCHAR(20)       NOT NULL,
    timestamp  TIMESTAMPTZ       NOT NULL,
    price      DOUBLE PRECISION  NOT NULL,
    size       INTEGER           NOT NULL,
    side       VARCHAR(4)        NOT NULL   -- 'BUY' or 'SELL'
);
CREATE INDEX idx_trades_ticker_ts  ON trades (ticker, timestamp DESC);
CREATE INDEX idx_trades_ts         ON trades (timestamp DESC);

-- Quotes table
CREATE TABLE quotes (
    id         BIGSERIAL PRIMARY KEY,
    ticker     VARCHAR(20)       NOT NULL,
    timestamp  TIMESTAMPTZ       NOT NULL,
    bid_price  DOUBLE PRECISION  NOT NULL,
    bid_size   INTEGER           NOT NULL,
    ask_price  DOUBLE PRECISION  NOT NULL,
    ask_size   INTEGER           NOT NULL,
    mid_price  DOUBLE PRECISION  NOT NULL
);
CREATE INDEX idx_quotes_ticker_ts  ON quotes (ticker, timestamp DESC);
CREATE INDEX idx_quotes_ts         ON quotes (timestamp DESC);
```

### Sample Queries

```sql
-- Latest price per ticker
SELECT DISTINCT ON (ticker)
    ticker, timestamp, price, side
FROM trades
ORDER BY ticker, timestamp DESC;

-- 1-minute OHLCV bars for AAPL (last hour)
SELECT
    date_trunc('minute', timestamp) AS bar_time,
    FIRST_VALUE(price) OVER w      AS open,
    MAX(price) OVER w              AS high,
    MIN(price) OVER w              AS low,
    LAST_VALUE(price) OVER w       AS close,
    SUM(size) OVER w               AS volume
FROM trades
WHERE ticker = 'AAPL'
  AND timestamp > NOW() - INTERVAL '1 hour'
WINDOW w AS (PARTITION BY date_trunc('minute', timestamp) ORDER BY timestamp
             ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)
ORDER BY bar_time;

-- Trade count and volume by ticker (last 5 minutes)
SELECT ticker,
       COUNT(*)   AS trade_count,
       SUM(size)  AS total_volume,
       AVG(price) AS vwap
FROM trades
WHERE timestamp > NOW() - INTERVAL '5 minutes'
GROUP BY ticker
ORDER BY total_volume DESC;

-- Bid-ask spread statistics per ticker
SELECT ticker,
       AVG(ask_price - bid_price)               AS avg_spread,
       AVG((ask_price - bid_price) / mid_price) AS avg_spread_pct
FROM quotes
WHERE timestamp > NOW() - INTERVAL '10 minutes'
GROUP BY ticker
ORDER BY avg_spread_pct DESC;
```

---

## Extending the System

### Add a New Consumer Group

1. Create a new Spring Boot module (copy `consumer-bars` as template)
2. Set a unique `spring.kafka.consumer.group-id`
3. Add `@KafkaListener(topics = "trades")` methods
4. Add the new service to `docker-compose.yml`

### Add a New Topic

1. Add the topic name to the simulator's `application.yml`
2. Create it in `kafka-setup` service command
3. Add a `KafkaTemplate` send call in `TickerSimulator`

### Use Avro Instead of JSON

Replace the `JsonSerializer` / `JsonDeserializer` with:
- Confluent Schema Registry (add as a Docker service)
- `io.confluent:kafka-avro-serializer` dependency
- Generate Java classes from `.avsc` schemas

This reduces message size by ~3-5× and enforces schema evolution contracts.

### Connect Grafana for Metrics

1. Add Prometheus + Grafana to `docker-compose.yml`
2. Add `spring-boot-starter-actuator` + `micrometer-registry-prometheus` to each module
3. Configure Grafana datasource pointing to Prometheus
4. Import a Kafka + JVM dashboard from grafana.com

### Replace Sample Data with Real Data

Obtain real historical data (e.g., from Yahoo Finance using `yfinance` in Python):

```python
import yfinance as yf
import pandas as pd

tickers = pd.read_csv('sp500.csv')['Symbol'].tolist()
dfs = []
for t in tickers:
    df = yf.download(t, period='5d', interval='1d')[['Volume','Close']]
    df['ticker'] = t
    df['daily_return'] = df['Close'].pct_change()
    dfs.append(df)

result = pd.concat(dfs).rename(columns={'Volume':'volume','Close':'close_price'})
result[['ticker','volume','close_price','daily_return']].to_csv('data/market_data.csv')
```
