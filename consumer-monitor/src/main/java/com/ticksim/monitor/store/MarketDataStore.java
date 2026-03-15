package com.ticksim.monitor.store;

import com.ticksim.monitor.model.TickerState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MarketDataStore {

    private final ConcurrentHashMap<String, TickerState> states = new ConcurrentHashMap<>();

    public void updateTrade(String ticker, double price, int size) {
        states.computeIfAbsent(ticker, TickerState::new).onTrade(price, size);
    }

    public void updateQuote(String ticker, double bid, double ask, double mid) {
        states.computeIfAbsent(ticker, TickerState::new).onQuote(bid, ask, mid);
    }

    public TickerState getState(String ticker) {
        return states.get(ticker);
    }

    public Collection<TickerState> getAllStates() {
        return states.values();
    }

    public List<TickerState.TickerSnapshot> getSnapshot() {
        List<TickerState.TickerSnapshot> snapshots = new ArrayList<>();
        for (TickerState state : states.values()) {
            snapshots.add(state.toSnapshot());
        }
        return snapshots;
    }

    public int size() {
        return states.size();
    }
}
