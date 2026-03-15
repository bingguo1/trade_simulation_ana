package com.ticksim.monitor.consumer;

import com.ticksim.common.model.QuoteEvent;
import com.ticksim.common.model.TradeEvent;
import com.ticksim.monitor.store.MarketDataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class MarketDataConsumer {

    private static final Logger log = LoggerFactory.getLogger(MarketDataConsumer.class);

    private final MarketDataStore store;

    public MarketDataConsumer(MarketDataStore store) {
        this.store = store;
    }

    @KafkaListener(
            topics = "trades",
            groupId = "consumer-monitor",
            containerFactory = "tradeKafkaListenerContainerFactory"
    )
    public void consumeTrade(TradeEvent event) {
        try {
            store.updateTrade(event.ticker(), event.price(), event.size());
        } catch (Exception e) {
            log.error("Error processing trade event for {}: {}", event.ticker(), e.getMessage());
        }
    }

    @KafkaListener(
            topics = "quotes",
            groupId = "consumer-monitor",
            containerFactory = "quoteKafkaListenerContainerFactory"
    )
    public void consumeQuote(QuoteEvent event) {
        try {
            store.updateQuote(event.ticker(), event.bidPrice(), event.askPrice(), event.midPrice());
        } catch (Exception e) {
            log.error("Error processing quote event for {}: {}", event.ticker(), e.getMessage());
        }
    }
}
