package com.ticksim.bars.model;

public class OHLCVBar {
    private final String ticker;
    private final long barSecond;  // Unix timestamp of bar start (seconds)
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
    private int tradeCount;
    private boolean completed;

    public OHLCVBar(String ticker, long barSecond, double firstPrice, int firstSize) {
        this.ticker = ticker;
        this.barSecond = barSecond;
        this.open = firstPrice;
        this.high = firstPrice;
        this.low = firstPrice;
        this.close = firstPrice;
        this.volume = firstSize;
        this.tradeCount = 1;
        this.completed = false;
    }

    public void addTrade(double price, int size) {
        high = Math.max(high, price);
        low = Math.min(low, price);
        close = price;
        volume += size;
        tradeCount++;
    }

    public void markCompleted() {
        this.completed = true;
    }

    public String getTicker() { return ticker; }
    public long getBarSecond() { return barSecond; }
    public double getOpen() { return open; }
    public double getHigh() { return high; }
    public double getLow() { return low; }
    public double getClose() { return close; }
    public long getVolume() { return volume; }
    public int getTradeCount() { return tradeCount; }
    public boolean isCompleted() { return completed; }

    /**
     * Snapshot for serialization - includes ticker for WebSocket routing
     */
    public BarSnapshot toSnapshot() {
        return new BarSnapshot(ticker, barSecond, open, high, low, close, volume, tradeCount, completed);
    }

    public record BarSnapshot(
            String ticker,
            long time,       // Unix timestamp in seconds (for lightweight-charts)
            double open,
            double high,
            double low,
            double close,
            long volume,
            int tradeCount,
            boolean completed
    ) {}
}
