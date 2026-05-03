package com.klinekit.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record EquityPoint(Instant timestamp, BigDecimal equity) {}
