package edu.zju.se.management.model;

import java.math.BigDecimal;

public record Stock(
        String stockCode,
        String stockName,
        BigDecimal lastPrice,
        int lastQuantity,
        String status,
        BigDecimal currentLimitRate,
        BigDecimal nextLimitRate
) {
}
