package com.ticksim.bars.aggregator;

import com.ticksim.bars.model.OHLCVBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aggregates trade events into 1-second OHLCV bars.
 * Thread-safe using per-ticker locking via ConcurrentHashMap + synchronized access.
 */
@Component
public class BarAggregator {

    private static final Logger log = LoggerFactory.getLogger(BarAggregator.class);
    private static final int MAX_BARS_PER_TICKER = 500;

    // Current open bar per ticker
    private final ConcurrentHashMap<String, OHLCVBar> currentBars = new ConcurrentHashMap<>();

    // Completed bars per ticker (circular buffer via LinkedList)
    private final ConcurrentHashMap<String, Deque<OHLCVBar>> completedBars = new ConcurrentHashMap<>();

    // Set of all known tickers
    private final Set<String> knownTickers = ConcurrentHashMap.newKeySet();

    /**
     * Process a trade event. Returns completed bar if a bar was just closed, else null.
     */
    public OHLCVBar onTrade(String ticker, long timestampEpochSeconds, double price, int size) {
        knownTickers.add(ticker);

        OHLCVBar completedBar = null;

        OHLCVBar current = currentBars.get(ticker);

        if (current == null) {
            // First trade for this ticker
            OHLCVBar newBar = new OHLCVBar(ticker, timestampEpochSeconds, price, size);
            OHLCVBar existing = currentBars.putIfAbsent(ticker, newBar);
            if (existing != null) {
                // Another thread created one concurrently - add to it
                synchronized (existing) {
                    if (existing.getBarSecond() == timestampEpochSeconds) {
                        existing.addTrade(price, size);
                    } else {
                        // The existing bar is from a different second - close it
                        existing.markCompleted();
                        completedBar = existing;
                        addCompletedBar(ticker, existing);
                        OHLCVBar nextBar = new OHLCVBar(ticker, timestampEpochSeconds, price, size);
                        currentBars.put(ticker, nextBar);
                    }
                }
            }
        } else {
            synchronized (current) {
                if (current.getBarSecond() == timestampEpochSeconds) {
                    current.addTrade(price, size);
                } else {
                    // New second - close current bar, start new one
                    current.markCompleted();
                    completedBar = current;
                    addCompletedBar(ticker, current);
                    OHLCVBar newBar = new OHLCVBar(ticker, timestampEpochSeconds, price, size);
                    currentBars.put(ticker, newBar);
                }
            }
        }

        return completedBar;
    }

    private void addCompletedBar(String ticker, OHLCVBar bar) {
        Deque<OHLCVBar> deque = completedBars.computeIfAbsent(ticker, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(bar);
            while (deque.size() > MAX_BARS_PER_TICKER) {
                deque.pollFirst();
            }
        }
    }

    /**
     * Get the last N completed bars for a ticker (oldest first).
     */
    public List<OHLCVBar.BarSnapshot> getCompletedBars(String ticker, int limit) {
        Deque<OHLCVBar> deque = completedBars.get(ticker);
        if (deque == null) return Collections.emptyList();

        List<OHLCVBar.BarSnapshot> result = new ArrayList<>();
        synchronized (deque) {
            Iterator<OHLCVBar> it = deque.iterator();
            int skip = Math.max(0, deque.size() - limit);
            int count = 0;
            while (it.hasNext()) {
                OHLCVBar bar = it.next();
                if (count >= skip) {
                    result.add(bar.toSnapshot());
                }
                count++;
            }
        }
        return result;
    }

    /**
     * Get the current in-progress bar snapshot for a ticker.
     */
    public OHLCVBar.BarSnapshot getCurrentBar(String ticker) {
        OHLCVBar bar = currentBars.get(ticker);
        return bar != null ? bar.toSnapshot() : null;
    }

    public Set<String> getKnownTickers() {
        return Collections.unmodifiableSet(knownTickers);
    }
}
