package com.ticksim.bars.websocket;

import com.ticksim.bars.aggregator.BarAggregator;
import com.ticksim.bars.model.OHLCVBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RestController
public class BarsController {

    private static final Logger log = LoggerFactory.getLogger(BarsController.class);

    private final BarAggregator aggregator;
    private final SimpMessagingTemplate messagingTemplate;

    public BarsController(BarAggregator aggregator, SimpMessagingTemplate messagingTemplate) {
        this.aggregator = aggregator;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Scheduled broadcast of in-progress (live) bars every 500ms.
     * Sends current bar state for all tickers to allow real-time chart updates.
     */
    @Scheduled(fixedDelay = 500)
    public void broadcastLiveBars() {
        try {
            Set<String> tickers = aggregator.getKnownTickers();
            if (tickers.isEmpty()) return;

            for (String ticker : tickers) {
                OHLCVBar.BarSnapshot current = aggregator.getCurrentBar(ticker);
                if (current != null) {
                    messagingTemplate.convertAndSend("/topic/bars/live", current);
                }
            }
        } catch (Exception e) {
            log.error("Error broadcasting live bars: {}", e.getMessage());
        }
    }

    /**
     * REST endpoint: get list of known tickers.
     */
    @GetMapping("/api/tickers")
    @ResponseBody
    public Set<String> getTickers() {
        return aggregator.getKnownTickers();
    }

    /**
     * REST endpoint: get history for a specific ticker.
     */
    @GetMapping("/api/bars")
    @ResponseBody
    public Map<String, Object> getBars(
            @RequestParam String ticker,
            @RequestParam(defaultValue = "100") int limit) {
        List<OHLCVBar.BarSnapshot> history = aggregator.getCompletedBars(ticker, limit);
        OHLCVBar.BarSnapshot current = aggregator.getCurrentBar(ticker);

        Map<String, Object> result = new HashMap<>();
        result.put("ticker", ticker);
        result.put("bars", history);
        result.put("current", current);
        return result;
    }

    /**
     * WebSocket message handler: client requests history for a ticker.
     */
    @MessageMapping("/bars/subscribe")
    @SendTo("/topic/bars/history")
    public Map<String, Object> handleSubscribe(String ticker) {
        List<OHLCVBar.BarSnapshot> history = aggregator.getCompletedBars(ticker.trim(), 100);
        OHLCVBar.BarSnapshot current = aggregator.getCurrentBar(ticker.trim());

        Map<String, Object> result = new HashMap<>();
        result.put("ticker", ticker.trim());
        result.put("bars", history);
        result.put("current", current);
        return result;
    }
}
