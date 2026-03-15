package com.ticksim.bars.consumer;

import com.ticksim.bars.aggregator.BarAggregator;
import com.ticksim.bars.model.OHLCVBar;
import com.ticksim.common.model.TradeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class BarsConsumer {

    private static final Logger log = LoggerFactory.getLogger(BarsConsumer.class);

    private final BarAggregator aggregator;
    private final SimpMessagingTemplate messagingTemplate;

    public BarsConsumer(BarAggregator aggregator, SimpMessagingTemplate messagingTemplate) {
        this.aggregator = aggregator;
        this.messagingTemplate = messagingTemplate;
    }

    @KafkaListener(
            topics = "trades",
            groupId = "consumer-bars",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeTrade(TradeEvent event) {
        try {
            long tradeSecond = event.timestamp().getEpochSecond();
            OHLCVBar completedBar = aggregator.onTrade(
                    event.ticker(), tradeSecond, event.price(), event.size()
            );

            if (completedBar != null) {
                // Broadcast completed bar immediately
                messagingTemplate.convertAndSend("/topic/bars", completedBar.toSnapshot());
            }
        } catch (Exception e) {
            log.error("Error processing trade for bars: {}", e.getMessage());
        }
    }
}
