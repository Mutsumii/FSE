package edu.zju.se.management.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReviewRecord(
        long id,
        String reviewId,
        String orderId,
        String accountId,
        String fundAccountNo,
        String securityAccountNo,
        String idCardNo,
        String userName,
        String stockCode,
        String stockName,
        String side,
        BigDecimal price,
        int quantity,
        BigDecimal amount,
        boolean approved,
        String reviewStatus,
        String riskLevel,
        String rejectCode,
        String reason,
        String clientTime,
        String manualDecision,
        String manualReason,
        String decidedBy,
        LocalDateTime decidedAt,
        LocalDateTime createdAt
) {
}
