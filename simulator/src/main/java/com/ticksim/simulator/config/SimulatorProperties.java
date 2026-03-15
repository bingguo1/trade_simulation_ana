package com.ticksim.simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "simulator")
public class SimulatorProperties {

    private Kafka kafka = new Kafka();
    private Data data = new Data();
    private int numPartitions = 10;
    private int tradingSecondsPerDay = 23400;
    private double defaultVolatility = 0.002;
    private int batchSize = 500;
    private int lingerMs = 5;
    private List<String> tickers = new ArrayList<>();
    private int maxTickers = 0;

    public Kafka getKafka() { return kafka; }
    public void setKafka(Kafka kafka) { this.kafka = kafka; }

    public Data getData() { return data; }
    public void setData(Data data) { this.data = data; }

    public int getNumPartitions() { return numPartitions; }
    public void setNumPartitions(int numPartitions) { this.numPartitions = numPartitions; }

    public int getTradingSecondsPerDay() { return tradingSecondsPerDay; }
    public void setTradingSecondsPerDay(int tradingSecondsPerDay) { this.tradingSecondsPerDay = tradingSecondsPerDay; }

    public double getDefaultVolatility() { return defaultVolatility; }
    public void setDefaultVolatility(double defaultVolatility) { this.defaultVolatility = defaultVolatility; }

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    public int getLingerMs() { return lingerMs; }
    public void setLingerMs(int lingerMs) { this.lingerMs = lingerMs; }

    public List<String> getTickers() { return tickers; }
    public void setTickers(List<String> tickers) { this.tickers = tickers; }

    public int getMaxTickers() { return maxTickers; }
    public void setMaxTickers(int maxTickers) { this.maxTickers = maxTickers; }

    public static class Kafka {
        private String bootstrapServers = "localhost:9092";
        private String tradesTopic = "trades";
        private String quotesTopic = "quotes";

        public String getBootstrapServers() { return bootstrapServers; }
        public void setBootstrapServers(String bootstrapServers) { this.bootstrapServers = bootstrapServers; }

        public String getTradesTopic() { return tradesTopic; }
        public void setTradesTopic(String tradesTopic) { this.tradesTopic = tradesTopic; }

        public String getQuotesTopic() { return quotesTopic; }
        public void setQuotesTopic(String quotesTopic) { this.quotesTopic = quotesTopic; }
    }

    public static class Data {
        private String sp500Csv = "/data/sp500.csv";
        private String marketDataCsv = "/data/market_data.csv";

        public String getSp500Csv() { return sp500Csv; }
        public void setSp500Csv(String sp500Csv) { this.sp500Csv = sp500Csv; }

        public String getMarketDataCsv() { return marketDataCsv; }
        public void setMarketDataCsv(String marketDataCsv) { this.marketDataCsv = marketDataCsv; }
    }
}
