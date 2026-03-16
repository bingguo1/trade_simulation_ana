package com.ticksim.simulator.simulation;

import com.ticksim.common.model.QuoteEvent;
import com.ticksim.common.model.TradeEvent;
import com.ticksim.simulator.model.TickerInfo;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simulates one ticker using virtual threads.
 * Generates synthetic TradeEvents and QuoteEvents using a GBM price model.
 */
public class TickerSimulator implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(TickerSimulator.class);

    private final TickerInfo info;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String tradesTopic;
    private final String quotesTopic;
    private final AtomicBoolean running;
    private final boolean heartbeatEnabled;
    private final long heartbeatIntervalMs;
    private final boolean heartbeatForTicker;
    private final Random random;

    // Current price state
    private volatile double currentPrice;

    public TickerSimulator(TickerInfo info,
                           KafkaTemplate<String, Object> kafkaTemplate,
                           String tradesTopic,
                           String quotesTopic,
                           AtomicBoolean running,
                           boolean heartbeatEnabled,
                           long heartbeatIntervalMs,
                           boolean heartbeatForTicker) {
        this.info = info;
        this.kafkaTemplate = kafkaTemplate;
        this.tradesTopic = tradesTopic;
        this.quotesTopic = quotesTopic;
        this.running = running;
        this.heartbeatEnabled = heartbeatEnabled;
        this.heartbeatIntervalMs = Math.max(1000, heartbeatIntervalMs);
        this.heartbeatForTicker = heartbeatForTicker;
        this.random = new Random();
        this.currentPrice = info.getInitialPrice();
    }

    @Override
    public void run() {
        log.debug("Starting simulator for ticker: {}", info.getTicker());

        double tradeRate = info.getTradeRate();
        double quoteRate = info.getQuoteRate();

        long startTime = System.currentTimeMillis();
        long lastHeartbeatTime = startTime;
        long totalTradeCount = 0;
        long totalQuoteCount = 0;
        long totalTradeVolume = 0;
        long intervalTradeCount = 0;
        long intervalQuoteCount = 0;
        double latestBid = Double.NaN;
        double latestAsk = Double.NaN;

        // Time until next trade and quote (in milliseconds)
        long nextTradeMs = sampleInterarrival(tradeRate);
        long nextQuoteMs = sampleInterarrival(quoteRate);

        long lastEventTime = System.currentTimeMillis();

        while (running.get()) {
            try {
                long minWait = Math.min(nextTradeMs, nextQuoteMs);
                if (minWait > 0) {
                    Thread.sleep(minWait);
                }

                long now = System.currentTimeMillis();
                long elapsed = now - lastEventTime;
                lastEventTime = now;

                // Update price via GBM
                double dt = elapsed / 1000.0; // convert ms to seconds
                updatePrice(dt);

                nextTradeMs -= elapsed;
                nextQuoteMs -= elapsed;

                if (nextTradeMs <= 0) {
                    int tradeSize = sendTradeEvent();
                    totalTradeCount++;
                    intervalTradeCount++;
                    totalTradeVolume += tradeSize;
                    nextTradeMs = sampleInterarrival(tradeRate);
                }

                if (nextQuoteMs <= 0) {
                    QuoteEvent quoteEvent = sendQuoteEvent();
                    totalQuoteCount++;
                    intervalQuoteCount++;
                    latestBid = quoteEvent.bidPrice();
                    latestAsk = quoteEvent.askPrice();
                    nextQuoteMs = sampleInterarrival(quoteRate);
                }

                if (heartbeatEnabled && heartbeatForTicker && now - lastHeartbeatTime >= heartbeatIntervalMs) {
                    double heartbeatElapsedSec = Math.max(0.001, (now - lastHeartbeatTime) / 1000.0);
                    double totalElapsedSec = Math.max(0.001, (now - startTime) / 1000.0);
                    double tradesPerSec = intervalTradeCount / heartbeatElapsedSec;
                    double quotesPerSec = intervalQuoteCount / heartbeatElapsedSec;
                    String quoteText = Double.isNaN(latestBid) || Double.isNaN(latestAsk)
                            ? "N/A"
                            : String.format("bid=%.4f ask=%.4f", latestBid, latestAsk);

                    log.info(
                            "Heartbeat ticker={} price={} {} totalTradeVolume={} totalTradeCount={} totalQuoteCount={} elapsedSec={} tradesPerSec={} quotesPerSec={}",
                            info.getTicker(),
                            currentPrice,
                            quoteText,
                            totalTradeVolume,
                            totalTradeCount,
                            totalQuoteCount,
                            totalElapsedSec,
                            tradesPerSec,
                            quotesPerSec
                    );

                    lastHeartbeatTime = now;
                    intervalTradeCount = 0;
                    intervalQuoteCount = 0;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (!running.get() || isExpectedShutdownException(e)) {
                    break;
                }
                log.error("Error in simulator for {}: {}", info.getTicker(), e.getMessage());
                // Small backoff on error to avoid tight loop
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.debug("Stopping simulator for ticker: {}", info.getTicker());
    }

    /**
     * Update price using Geometric Brownian Motion:
     * price *= exp((drift - 0.5*vol^2)*dt + vol*sqrt(dt)*Z) where Z~N(0,1)
     */
    private void updatePrice(double dt) {
        if (dt <= 0) return;
        double vol = info.getVolatility();
        double drift = info.getDrift();
        double z = random.nextGaussian();
        double logReturn = (drift - 0.5 * vol * vol) * dt + vol * Math.sqrt(dt) * z;
        currentPrice *= Math.exp(logReturn);
        // Clamp price to avoid extreme values
        currentPrice = Math.max(0.01, currentPrice);
    }

    private int sendTradeEvent() {
        double price = currentPrice;
        int size = sampleTradeSize();
        String side = random.nextBoolean() ? "BUY" : "SELL";
        TradeEvent event = new TradeEvent(info.getTicker(), Instant.now(), price, size, side);

        ProducerRecord<String, Object> record = new ProducerRecord<>(
                tradesTopic,
                info.getAssignedPartition(),
                info.getTicker(),
                event
        );
        kafkaTemplate.send(record);
        return size;
    }

    private QuoteEvent sendQuoteEvent() {
        double price = currentPrice;
        // Spread: price * (0.0001 + 0.0009*random)
        double spread = price * (0.0001 + 0.0009 * random.nextDouble());
        double bidPrice = price - spread / 2.0;
        double askPrice = price + spread / 2.0;
        int bidSize = 50 + random.nextInt(451); // uniform(50, 500)
        int askSize = 50 + random.nextInt(451);
        double midPrice = (bidPrice + askPrice) / 2.0;

        QuoteEvent event = new QuoteEvent(
                info.getTicker(), Instant.now(),
                bidPrice, bidSize, askPrice, askSize, midPrice
        );

        ProducerRecord<String, Object> record = new ProducerRecord<>(
                quotesTopic,
                info.getAssignedPartition(),
                info.getTicker(),
                event
        );
        kafkaTemplate.send(record);
        return event;
    }

    private boolean isExpectedShutdownException(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof InterruptedException || current instanceof IllegalStateException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains("Producer closed")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Sample interarrival time in milliseconds from exponential distribution.
     * E[T] = 1/rate seconds.
     */
    private long sampleInterarrival(double rate) {
        if (rate <= 0) return 1000;
        double seconds = -Math.log(Math.max(1e-10, random.nextDouble())) / rate;
        return Math.max(1, (long) (seconds * 1000));
    }

    /**
     * Log-normal trade size: size = (int)max(1, exp(4.48 + 0.5*gaussian))
     * Mean ~ exp(4.48 + 0.5^2/2) = exp(4.605) ~ 100
     */
    private int sampleTradeSize() {
        double z = random.nextGaussian();
        return (int) Math.max(1, Math.exp(4.48 + 0.5 * z));
    }

    public String getTicker() {
        return info.getTicker();
    }

    public double getCurrentPrice() {
        return currentPrice;
    }
}
