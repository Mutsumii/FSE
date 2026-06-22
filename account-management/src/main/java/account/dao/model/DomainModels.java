package account.dao.model;

import account.dao.model.DomainEnums.AccountStatus;
import account.dao.model.DomainEnums.FundTransactionType;
import account.dao.model.DomainEnums.InvestorType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class DomainModels {

    private DomainModels() {
    }

    public record Investor(
            Integer investorId,
            InvestorType type,
            String name,
            String gender,
            String idType,
            String idNumber,
            String phone,
            String address,
            String workUnit,
            String occupation,
            String education,
            String legalNumber,
            String businessLicense,
            String executorName,
            String executorIdNumber,
            String executorPhone,
            String executorAddress,
            String agentName,
            String agentIdNumber,
            LocalDateTime createdAt
    ) {
    }

    public record SecurityAccount(
            String secAccNo,
            Integer investorId,
            AccountStatus status,
            LocalDate openDate,
            String linkedFundAcc
    ) {
    }

    public record FundAccount(
            String fundAccNo,
            String secAccNo,
            String tradePassword,
            String withdrawPassword,
            BigDecimal availableBalance,
            BigDecimal frozenBalance,
            String currency,
            AccountStatus status,
            LocalDate openDate,
            LocalDate lastInterestDate,
            BigDecimal annualInterestRate
    ) {
    }

    public record FundTransactionLog(
            Long logId,
            String fundAccNo,
            FundTransactionType txnType,
            BigDecimal amount,
            BigDecimal availableAfter,
            BigDecimal frozenAfter,
            String refOrderId,
            Integer operatorId,
            LocalDateTime txnTime
    ) {
    }

    public record Staff(
            Integer staffId,
            String username,
            String passwordHash,
            String status,
            LocalDateTime createdAt
    ) {
    }

    public record LoginCertificateState(
            Long stateId,
            String subjectType,
            String subjectKey,
            boolean certificateVerified,
            LocalDateTime verifiedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record Holding(
            Long holdingId,
            String secAccNo,
            String stockCode,
            String stockName,
            Integer quantity,
            Integer frozenQuantity,
            BigDecimal avgCost,
            LocalDateTime updatedAt
    ) {
        public int availableQuantity() {
            return quantity - frozenQuantity;
        }
    }

    public record HoldingChangeLog(
            Long logId,
            String secAccNo,
            String stockCode,
            String stockName,
            String refOrderId,
            String changeType,
            Integer quantity,
            BigDecimal price,
            Integer quantityAfter,
            Integer frozenQuantityAfter,
            BigDecimal avgCostAfter,
            LocalDateTime txnTime
    ) {
    }

    public record OperationLog(
            Long logId,
            Integer staffId,
            String operationType,
            String targetType,
            String targetId,
            String detail,
            LocalDateTime operationTime
    ) {
    }

    public record OperationLogQuery(
            Integer staffId,
            LocalDateTime fromTime,
            LocalDateTime toTime,
            String operationType,
            String targetType,
            String targetId,
            Integer limit,
            Integer offset
    ) {
        public int safeLimit() {
            if (limit == null || limit < 1) {
                return 100;
            }
            return Math.min(limit, 1000);
        }

        public int safeOffset() {
            if (offset == null || offset < 0) {
                return 0;
            }
            return offset;
        }
    }
}
