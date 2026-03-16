# Fix Summary

Date: 2026-03-15

## Issues Addressed

### 1) Docker Compose Kafka image pull failure

#### Symptom

`docker compose up -d kafka1 kafka2 kafka3 kafka-setup postgres kafka-ui` failed with:

`manifest for bitnami/kafka:3.7 not found`

#### Root Cause

The `bitnami/kafka` repository currently has no published tags on Docker Hub for this setup, so `bitnami/kafka:3.7` could not be resolved.

#### Fix

Updated Kafka image references from `bitnami/kafka:3.7` to `bitnamilegacy/kafka:3.7.0` in:

- `docker-compose.yml` (`kafka1`, `kafka2`, `kafka3`, `kafka-setup`)
- `README.md` scaling example section

#### Verification

- `docker compose pull kafka1 kafka2 kafka3 kafka-setup kafka-ui` succeeded.
- `docker compose up -d kafka1 kafka2 kafka3 kafka-setup postgres kafka-ui` succeeded.
- Brokers became healthy, `kafka-ui` started, `kafka-setup` exited with code 0.

---

### 2) Local Maven run failure in module directory

#### Symptom

Running this from `simulator` failed:

```bash
DATA_PATH=../data \
KAFKA_BOOTSTRAP_SERVERS=localhost:9092,localhost:9093,localhost:9094 \
mvn spring-boot:run
```

Error:

`Could not resolve dependencies ... com.ticksim:common:jar:1.0.0`

#### Root Cause

`simulator` depends on sibling module `common`. Running Maven inside the module without reactor context attempted to resolve `common` from Maven Central instead of building local sibling modules.

#### Fix

1. Added module-local Maven configs so module-level `mvn` commands run via root reactor:

- `simulator/.mvn/maven.config`
- `consumer-monitor/.mvn/maven.config`
- `consumer-bars/.mvn/maven.config`
- `consumer-writer/.mvn/maven.config`

Each includes:

```text
-f
..
-pl
<module-name>
-am
```

2. Prevented Spring Boot plugin execution on the root aggregator POM and explicitly enabled it in runnable modules:

- Root: `pom.xml` -> Spring Boot plugin managed with `<skip>true</skip>`
- Runnable modules: `simulator/pom.xml`, `consumer-monitor/pom.xml`, `consumer-bars/pom.xml`, `consumer-writer/pom.xml` -> `<skip>false</skip>`

#### Verification

The same simulator command now resolves dependencies correctly and launches.

---

### 3) Simulator data-path mismatch (sp500.csv)

#### Symptom

Even after Maven fix, simulator startup could log missing `sp500.csv` when using `DATA_PATH=../data`.

#### Root Cause

Workspace stores `sp500.csv` at repo root, while config points to `${DATA_PATH}/sp500.csv`.

#### Fix

1. Added fallback discovery in `simulator/src/main/java/com/ticksim/simulator/loader/MarketDataLoader.java`:

If configured path does not exist, try in order:

- `sp500.csv`
- `../sp500.csv`
- `data/sp500.csv`
- `../data/sp500.csv`

2. Updated Docker simulator mounts in `docker-compose.yml`:

- Added `./sp500.csv:/data/sp500.csv:ro`
- Kept `./data:/data:ro`

3. Updated local run notes in `README.md`.

#### Verification

Simulator startup loaded `sp500.csv` successfully and initialized ticker simulation.

---

## Current Recommended Local Run

From module directories (for example `simulator`):

```bash
DATA_PATH=../data \
KAFKA_BOOTSTRAP_SERVERS=localhost:9092,localhost:9093,localhost:9094 \
mvn spring-boot:run
```

## Notes

- `bitnamilegacy` images are legacy/temporary and not actively updated.
- Long-term recommendation: migrate Kafka services to a currently maintained image family.
