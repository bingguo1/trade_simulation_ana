package com.ticksim.writer.consumer;

import com.ticksim.common.model.QuoteEvent;
import com.ticksim.common.model.TradeEvent;
import com.ticksim.writer.repository.MarketDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Consumes trades and quotes from Kafka and batch-inserts into PostgreSQL.
 * Uses buffering: flush every 1000 records or every 1 second (whichever first).
 */
@Component
public class WriterConsumer {

    private static final Logger log = LoggerFactory.getLogger(WriterConsumer.class);
    private static final int BATCH_SIZE = 1000;

    private final MarketDataRepository repository;

    // Thread-safe buffers
    private final List<TradeEvent> tradeBatch = new CopyOnWriteArrayList<>();
    private final List<QuoteEvent> quoteBatch = new CopyOnWriteArrayList<>();

    private volatile long totalTradesWritten = 0;
    private volatile long totalQuotesWritten = 0;

    public WriterConsumer(MarketDataRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(
            topics = "trades",
            groupId = "consumer-writer",
            containerFactory = "tradeKafkaListenerContainerFactory"
    )
    public void consumeTrades(List<TradeEvent> trades) {
        tradeBatch.addAll(trades);
        if (tradeBatch.size() >= BATCH_SIZE) {
            flushTrades();
        }
    }

    @KafkaListener(
            topics = "quotes",
            groupId = "consumer-writer",
            containerFactory = "quoteKafkaListenerContainerFactory"
    )
    public void consumeQuotes(List<QuoteEvent> quotes) {
        quoteBatch.addAll(quotes);
        if (quoteBatch.size() >= BATCH_SIZE) {
            flushQuotes();
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void scheduledFlush() {
        flushTrades();
        flushQuotes();
    }

    private synchronized void flushTrades() {
        if (tradeBatch.isEmpty()) return;
        List<TradeEvent> toInsert = new ArrayList<>(tradeBatch);
        tradeBatch.clear();
        repository.batchInsertTrades(toInsert);
        totalTradesWritten += toInsert.size();
        if (totalTradesWritten % 10000 == 0 || toInsert.size() >= BATCH_SIZE) {
            log.info("Written {} total trades ({} in this batch)", totalTradesWritten, toInsert.size());
        }
    }

    private synchronized void flushQuotes() {
        if (quoteBatch.isEmpty()) return;
        List<QuoteEvent> toInsert = new ArrayList<>(quoteBatch);
        quoteBatch.clear();
        repository.batchInsertQuotes(toInsert);
        totalQuotesWritten += toInsert.size();
        if (totalQuotesWritten % 10000 == 0 || toInsert.size() >= BATCH_SIZE) {
            log.info("Written {} total quotes ({} in this batch)", totalQuotesWritten, toInsert.size());
        }
    }

    public long getTotalTradesWritten() { return totalTradesWritten; }
    public long getTotalQuotesWritten() { return totalQuotesWritten; }
}
