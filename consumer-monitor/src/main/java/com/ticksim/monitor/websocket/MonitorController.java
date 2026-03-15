package com.ticksim.monitor.websocket;

import com.ticksim.monitor.model.TickerState;
import com.ticksim.monitor.store.MarketDataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Component
@RestController
public class MonitorController {

    private static final Logger log = LoggerFactory.getLogger(MonitorController.class);

    private final MarketDataStore store;
    private final SimpMessagingTemplate messagingTemplate;

    public MonitorController(MarketDataStore store, SimpMessagingTemplate messagingTemplate) {
        this.store = store;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedDelay = 500)
    public void broadcastSnapshot() {
        try {
            List<TickerState.TickerSnapshot> snapshot = store.getSnapshot();
            if (!snapshot.isEmpty()) {
                messagingTemplate.convertAndSend("/topic/monitor", snapshot);
            }
        } catch (Exception e) {
            log.error("Error broadcasting snapshot: {}", e.getMessage());
        }
    }

    @GetMapping("/api/snapshot")
    public List<TickerState.TickerSnapshot> getSnapshot() {
        return store.getSnapshot();
    }
}
