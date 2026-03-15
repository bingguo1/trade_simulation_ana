-- Create trades table if not exists
CREATE TABLE IF NOT EXISTS trades (
    id          BIGSERIAL PRIMARY KEY,
    ticker      VARCHAR(20)  NOT NULL,
    timestamp   TIMESTAMPTZ  NOT NULL,
    price       DOUBLE PRECISION NOT NULL,
    size        INTEGER      NOT NULL,
    side        VARCHAR(4)   NOT NULL
);

-- Create quotes table if not exists
CREATE TABLE IF NOT EXISTS quotes (
    id          BIGSERIAL PRIMARY KEY,
    ticker      VARCHAR(20)  NOT NULL,
    timestamp   TIMESTAMPTZ  NOT NULL,
    bid_price   DOUBLE PRECISION NOT NULL,
    bid_size    INTEGER      NOT NULL,
    ask_price   DOUBLE PRECISION NOT NULL,
    ask_size    INTEGER      NOT NULL,
    mid_price   DOUBLE PRECISION NOT NULL
);

-- Indexes on (ticker, timestamp) for efficient queries
CREATE INDEX IF NOT EXISTS idx_trades_ticker_time  ON trades  (ticker, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_quotes_ticker_time  ON quotes  (ticker, timestamp DESC);

-- Index for time-range queries
CREATE INDEX IF NOT EXISTS idx_trades_time  ON trades  (timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_quotes_time  ON quotes  (timestamp DESC);
