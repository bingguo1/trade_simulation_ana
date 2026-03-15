package com.ticksim.simulator.model;

public class TickerInfo {
    private final String ticker;
    private final double initialPrice;
    private final double tradeRate;   // trades per second
    private final double quoteRate;   // quotes per second (= 10 * tradeRate)
    private final double drift;       // drift per second
    private final double volatility;  // volatility per sqrt(second)
    private final int assignedPartition;
    private final long avgDailyVolume;

    public TickerInfo(String ticker, double initialPrice, double tradeRate,
                      double drift, double volatility, int assignedPartition, long avgDailyVolume) {
        this.ticker = ticker;
        this.initialPrice = initialPrice;
        this.tradeRate = tradeRate;
        this.quoteRate = 10.0 * tradeRate;
        this.drift = drift;
        this.volatility = volatility;
        this.assignedPartition = assignedPartition;
        this.avgDailyVolume = avgDailyVolume;
    }

    public String getTicker() { return ticker; }
    public double getInitialPrice() { return initialPrice; }
    public double getTradeRate() { return tradeRate; }
    public double getQuoteRate() { return quoteRate; }
    public double getDrift() { return drift; }
    public double getVolatility() { return volatility; }
    public int getAssignedPartition() { return assignedPartition; }
    public long getAvgDailyVolume() { return avgDailyVolume; }

    @Override
    public String toString() {
        return String.format("TickerInfo{ticker='%s', price=%.2f, tradeRate=%.4f/s, partition=%d}",
                ticker, initialPrice, tradeRate, assignedPartition);
    }
}
