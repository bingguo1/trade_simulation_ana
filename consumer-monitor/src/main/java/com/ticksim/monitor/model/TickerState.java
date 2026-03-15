package com.ticksim.monitor.model;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class TickerState {

    private final String ticker;

    // Latest market data
    private volatile double latestBid = 0.0;
    private volatile double latestAsk = 0.0;
    private volatile double latestTrade = 0.0;
    private volatile double latestPrice = 0.0;
    private volatile double previousPrice = 0.0;

    // Volume tracking
    private final AtomicLong totalVolume = new AtomicLong(0);

    // Rate tracking - rolling counts using two buckets (current second and previous)
    private final AtomicLong tradeCountCurrent = new AtomicLong(0);
    private final AtomicLong quoteCountCurrent = new AtomicLong(0);
    private volatile long currentSecond = System.currentTimeMillis() / 1000;
    private volatile double tradesPerSec = 0.0;
    private volatile double quotesPerSec = 0.0;

    public TickerState(String ticker) {
        this.ticker = ticker;
    }

    public void onTrade(double price, int size) {
        previousPrice = latestPrice > 0 ? latestPrice : price;
        latestTrade = price;
        latestPrice = price;
        totalVolume.addAndGet(size);
        tickSecond(true);
    }

    public void onQuote(double bid, double ask, double mid) {
        latestBid = bid;
        latestAsk = ask;
        if (latestPrice == 0.0) {
            latestPrice = mid;
        }
        tickSecond(false);
    }

    private void tickSecond(boolean isTrade) {
        long nowSec = System.currentTimeMillis() / 1000;
        if (nowSec != currentSecond) {
            // Roll the window
            tradesPerSec = tradeCountCurrent.get();
            quotesPerSec = quoteCountCurrent.get();
            tradeCountCurrent.set(0);
            quoteCountCurrent.set(0);
            currentSecond = nowSec;
        }
        if (isTrade) {
            tradeCountCurrent.incrementAndGet();
        } else {
            quoteCountCurrent.incrementAndGet();
        }
    }

    public String getTicker() { return ticker; }
    public double getLatestBid() { return latestBid; }
    public double getLatestAsk() { return latestAsk; }
    public double getLatestTrade() { return latestTrade; }
    public double getLatestPrice() { return latestPrice; }
    public double getPreviousPrice() { return previousPrice; }
    public long getTotalVolume() { return totalVolume.get(); }
    public double getTradesPerSec() { return tradesPerSec; }
    public double getQuotesPerSec() { return quotesPerSec; }

    /**
     * Snapshot DTO for JSON serialization
     */
    public TickerSnapshot toSnapshot() {
        return new TickerSnapshot(
                ticker, latestBid, latestAsk, latestTrade, latestPrice,
                previousPrice, totalVolume.get(), tradesPerSec, quotesPerSec
        );
    }

    public record TickerSnapshot(
            String ticker,
            double bid,
            double ask,
            double lastTrade,
            double price,
            double previousPrice,
            long volume,
            double tradesPerSec,
            double quotesPerSec
    ) {}
}
