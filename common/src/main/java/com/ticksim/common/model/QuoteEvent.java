package com.ticksim.common.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public record QuoteEvent(
    String ticker,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp,
    double bidPrice,
    int bidSize,
    double askPrice,
    int askSize,
    double midPrice
) {}
