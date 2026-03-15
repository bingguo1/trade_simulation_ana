package com.ticksim.simulator.simulation;

import com.ticksim.simulator.config.SimulatorProperties;
import com.ticksim.simulator.loader.MarketDataLoader;
import com.ticksim.simulator.model.TickerInfo;
import com.ticksim.simulator.partitioner.TickerPartitionMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Creates and manages virtual thread simulators for all tickers.
 */
@Component
public class SimulationEngine {

    private static final Logger log = LoggerFactory.getLogger(SimulationEngine.class);

    private final MarketDataLoader marketDataLoader;
    private final TickerPartitionMapper partitionMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SimulatorProperties props;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<Thread> simulatorThreads = new ArrayList<>();
    private Thread keepAliveThread;

    public SimulationEngine(MarketDataLoader marketDataLoader,
                            TickerPartitionMapper partitionMapper,
                            KafkaTemplate<String, Object> kafkaTemplate,
                            SimulatorProperties props) {
        this.marketDataLoader = marketDataLoader;
        this.partitionMapper = partitionMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.props = props;
    }

    @PostConstruct
    public void start() {
        log.info("Starting SimulationEngine...");

        List<TickerInfo> tickers = marketDataLoader.loadTickers();
        List<TickerInfo> assignedTickers = partitionMapper.assignPartitions(tickers, props.getNumPartitions());

        running.set(true);

        String tradesTopic = props.getKafka().getTradesTopic();
        String quotesTopic = props.getKafka().getQuotesTopic();

        for (TickerInfo ticker : assignedTickers) {
            TickerSimulator simulator = new TickerSimulator(
                    ticker, kafkaTemplate, tradesTopic, quotesTopic, running
            );
            Thread thread = Thread.ofVirtual()
                    .name("sim-" + ticker.getTicker())
                    .start(simulator);
            simulatorThreads.add(thread);
        }

        keepAliveThread = Thread.ofPlatform()
                .name("simulator-keepalive")
                .start(() -> {
                    while (running.get()) {
                        try {
                            Thread.sleep(Long.MAX_VALUE);
                        } catch (InterruptedException e) {
                            if (!running.get()) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                });

        log.info("Started {} virtual thread simulators", simulatorThreads.size());
    }

    @PreDestroy
    public void stop() {
        log.info("Stopping SimulationEngine...");
        running.set(false);

        for (Thread thread : simulatorThreads) {
            thread.interrupt();
        }

        // Wait for threads to finish (with timeout)
        for (Thread thread : simulatorThreads) {
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (keepAliveThread != null) {
            keepAliveThread.interrupt();
            try {
                keepAliveThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            keepAliveThread = null;
        }

        simulatorThreads.clear();
        log.info("SimulationEngine stopped.");
    }
}
