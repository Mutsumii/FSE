package edu.zju.se.management.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TradeOrder(
        String orderId,
        String stockCode,
        String side,
        BigDecimal price,
        int quantity,
        LocalDateTime enteredAt,
        String status
) {
}
