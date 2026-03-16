# cd simulator
# SIM_TICKERS=T,AAPL \
SIM_HEARTBEAT_TICKERS=T,AAPL \
SIM_HEARTBEAT_INTERVAL_MS=5000 \
DATA_PATH=../data \
KAFKA_BOOTSTRAP_SERVERS=localhost:9092,localhost:9093,localhost:9094 \
mvn spring-boot:run