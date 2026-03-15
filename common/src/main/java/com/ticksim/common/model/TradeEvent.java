package com.ticksim.common.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public record TradeEvent(
    String ticker,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp,
    double price,
    int size,
    String side // "BUY" or "SELL"
) {}
