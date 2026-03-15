package com.ticksim.simulator.loader;

import com.ticksim.simulator.config.SimulatorProperties;
import com.ticksim.simulator.model.TickerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

@Component
public class MarketDataLoader {

    private static final Logger log = LoggerFactory.getLogger(MarketDataLoader.class);
    private static final int TRADING_SECONDS_PER_DAY = 23400; // 6.5 hours

    private final SimulatorProperties props;

    public MarketDataLoader(SimulatorProperties props) {
        this.props = props;
    }

    /**
     * Load ticker information from sp500.csv and optionally market_data.csv.
     * Returns list of TickerInfo (partition assignments not yet set - handled by TickerPartitionMapper).
     */
    public List<TickerInfo> loadTickers() {
        // Load base data from sp500.csv
        Map<String, double[]> sp500Data = loadSp500();  // ticker -> [price, weightPct]

        // Load optional market data (volume, daily return)
        Map<String, double[]> marketData = loadMarketData(); // ticker -> [avgVolume, avgReturn]

        List<TickerInfo> tickers = new ArrayList<>();
        double defaultVolatility = props.getDefaultVolatility();

        for (Map.Entry<String, double[]> entry : sp500Data.entrySet()) {
            String ticker = entry.getKey();
            double price = entry.getValue()[0];
            double weightPct = entry.getValue()[1];

            long avgDailyVolume;
            double drift;

            if (marketData.containsKey(ticker)) {
                double[] md = marketData.get(ticker);
                avgDailyVolume = (long) md[0];
                drift = md[1] / TRADING_SECONDS_PER_DAY; // convert daily return to per-second drift
            } else {
                // Estimate: volume = weightPct * 2_000_000_000 / 100 (scaled for simulation)
                avgDailyVolume = (long) (weightPct * 2_000_000_000L / 100.0);
                drift = 0.0;
            }

            // tradeRate = avgDailyVolume / 100.0 / (6.5 * 3600)
            double tradeRate = avgDailyVolume / 100.0 / TRADING_SECONDS_PER_DAY;
            // Clamp to reasonable bounds: min 0.01/sec, max 50/sec
            tradeRate = Math.max(0.01, Math.min(50.0, tradeRate));

            // Partition assigned later by TickerPartitionMapper
            tickers.add(new TickerInfo(ticker, price, tradeRate, drift, defaultVolatility, 0, avgDailyVolume));
        }

        log.info("Loaded {} tickers from sp500.csv", tickers.size());
        return tickers;
    }

    private Map<String, double[]> loadSp500() {
        Map<String, double[]> result = new LinkedHashMap<>();
        String csvPath = resolveSp500CsvPath();

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line = br.readLine(); // skip header
            if (line == null) {
                log.error("sp500.csv is empty");
                return result;
            }

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Format: Company,Symbol,Weight,Price,Chg,% Chg
                // Handle quoted fields (e.g., "Tesla, Inc.")
                String[] parts = parseCsvLine(line);
                if (parts.length < 4) {
                    log.warn("Skipping malformed sp500 line: {}", line);
                    continue;
                }

                String symbol = parts[1].trim();
                String weightStr = parts[2].trim().replace("%", "");
                String priceStr = parts[3].trim().replace(",", "");

                try {
                    double weightPct = Double.parseDouble(weightStr);
                    double price = Double.parseDouble(priceStr);
                    if (price <= 0) price = 10.0;
                    result.put(symbol, new double[]{price, weightPct});
                } catch (NumberFormatException e) {
                    log.warn("Could not parse sp500 line: {}", line);
                }
            }
        } catch (IOException e) {
            log.error("Failed to read sp500.csv from {}", csvPath, e);
        }

        return result;
    }

    private String resolveSp500CsvPath() {
        String configuredPath = props.getData().getSp500Csv();
        Path configured = Path.of(configuredPath);
        if (configured.toFile().exists()) {
            return configuredPath;
        }

        List<Path> fallbackPaths = List.of(
                Path.of("sp500.csv"),
                Path.of("..", "sp500.csv"),
                Path.of("data", "sp500.csv"),
                Path.of("..", "data", "sp500.csv")
        );

        for (Path fallbackPath : fallbackPaths) {
            if (fallbackPath.toFile().exists()) {
                log.warn("sp500.csv not found at {}, using {} instead", configuredPath, fallbackPath);
                return fallbackPath.toString();
            }
        }

        return configuredPath;
    }

    private Map<String, double[]> loadMarketData() {
        Map<String, double[]> result = new HashMap<>();
        String csvPath = props.getData().getMarketDataCsv();
        File f = new File(csvPath);

        if (!f.exists()) {
            log.info("market_data.csv not found at {}, using weight-based volume estimates", csvPath);
            return result;
        }

        // Format: ticker,date,volume,close_price,daily_return
        // Aggregate: average volume and average daily_return per ticker
        Map<String, List<double[]>> perTicker = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line = br.readLine(); // skip header
            if (line == null) return result;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length < 5) continue;

                String ticker = parts[0].trim();
                try {
                    double volume = Double.parseDouble(parts[2].trim());
                    double dailyReturn = Double.parseDouble(parts[4].trim());
                    perTicker.computeIfAbsent(ticker, k -> new ArrayList<>())
                            .add(new double[]{volume, dailyReturn});
                } catch (NumberFormatException e) {
                    log.warn("Could not parse market_data line: {}", line);
                }
            }
        } catch (IOException e) {
            log.error("Failed to read market_data.csv from {}", csvPath, e);
            return result;
        }

        // Compute averages per ticker
        for (Map.Entry<String, List<double[]>> entry : perTicker.entrySet()) {
            List<double[]> rows = entry.getValue();
            double sumVol = 0, sumRet = 0;
            for (double[] row : rows) {
                sumVol += row[0];
                sumRet += row[1];
            }
            double avgVol = sumVol / rows.size();
            double avgRet = sumRet / rows.size();
            result.put(entry.getKey(), new double[]{avgVol, avgRet});
        }

        log.info("Loaded market data for {} tickers from market_data.csv", result.size());
        return result;
    }

    /**
     * Parse a CSV line handling quoted fields.
     */
    private String[] parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString());
        return tokens.toArray(new String[0]);
    }
}
