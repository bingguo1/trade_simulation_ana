package com.ticksim.simulator.partitioner;

import com.ticksim.simulator.model.TickerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Assigns partitions to tickers using a deterministic interleaving strategy:
 * - Sort tickers by symbol alphabetically to get a stable order
 * - Sort by volume descending to get volume rank
 * - Interleave: assign partitions round-robin based on volume rank so each
 *   partition gets a mix of high and low volume tickers.
 */
@Component
public class TickerPartitionMapper {

    private static final Logger log = LoggerFactory.getLogger(TickerPartitionMapper.class);

    public List<TickerInfo> assignPartitions(List<TickerInfo> tickers, int numPartitions) {
        // Sort by volume descending to rank by volume
        List<TickerInfo> byVolume = new ArrayList<>(tickers);
        byVolume.sort(Comparator.comparingLong(TickerInfo::getAvgDailyVolume).reversed());

        List<TickerInfo> result = new ArrayList<>(tickers.size());

        for (int i = 0; i < byVolume.size(); i++) {
            TickerInfo t = byVolume.get(i);
            int partition = i % numPartitions;
            result.add(new TickerInfo(
                    t.getTicker(),
                    t.getInitialPrice(),
                    t.getTradeRate(),
                    t.getDrift(),
                    t.getVolatility(),
                    partition,
                    t.getAvgDailyVolume()
            ));
        }

        log.info("Assigned {} tickers to {} partitions", result.size(), numPartitions);
        return result;
    }
}
