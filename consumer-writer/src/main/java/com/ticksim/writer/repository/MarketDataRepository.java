package com.ticksim.writer.repository;

import com.ticksim.common.model.QuoteEvent;
import com.ticksim.common.model.TradeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class MarketDataRepository {

    private static final Logger log = LoggerFactory.getLogger(MarketDataRepository.class);

    private static final String INSERT_TRADE =
            "INSERT INTO trades (ticker, timestamp, price, size, side) VALUES (?, ?, ?, ?, ?)";

    private static final String INSERT_QUOTE =
            "INSERT INTO quotes (ticker, timestamp, bid_price, bid_size, ask_price, ask_size, mid_price) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    public MarketDataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void batchInsertTrades(List<TradeEvent> trades) {
        if (trades.isEmpty()) return;

        try {
            jdbcTemplate.batchUpdate(INSERT_TRADE, trades, trades.size(), (ps, trade) -> {
                ps.setString(1, trade.ticker());
                ps.setTimestamp(2, Timestamp.from(trade.timestamp()));
                ps.setDouble(3, trade.price());
                ps.setInt(4, trade.size());
                ps.setString(5, trade.side());
            });
            log.debug("Inserted {} trades", trades.size());
        } catch (Exception e) {
            log.error("Failed to batch insert {} trades: {}", trades.size(), e.getMessage());
        }
    }

    public void batchInsertQuotes(List<QuoteEvent> quotes) {
        if (quotes.isEmpty()) return;

        try {
            jdbcTemplate.batchUpdate(INSERT_QUOTE, quotes, quotes.size(), (ps, quote) -> {
                ps.setString(1, quote.ticker());
                ps.setTimestamp(2, Timestamp.from(quote.timestamp()));
                ps.setDouble(3, quote.bidPrice());
                ps.setInt(4, quote.bidSize());
                ps.setDouble(5, quote.askPrice());
                ps.setInt(6, quote.askSize());
                ps.setDouble(7, quote.midPrice());
            });
            log.debug("Inserted {} quotes", quotes.size());
        } catch (Exception e) {
            log.error("Failed to batch insert {} quotes: {}", quotes.size(), e.getMessage());
        }
    }
}
