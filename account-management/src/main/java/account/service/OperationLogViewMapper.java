package account.service;

import account.dao.DaoRegistry;
import account.dao.model.DomainEnums;
import account.dao.model.DomainModels;
import account.dto.OperationLogView;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OperationLogViewMapper {

    private final DaoRegistry dao;

    public OperationLogView toView(DomainModels.OperationLog log) {
        ResolvedAccounts resolvedAccounts = resolveAccounts(log);
        return OperationLogView.builder()
                .logId(log.logId())
                .staffId(log.staffId())
                .operationType(log.operationType())
                .targetType(log.targetType())
                .targetId(log.targetId())
                .securityAccNo(resolvedAccounts.securityAccNo())
                .fundAccNo(resolvedAccounts.fundAccNo())
                .detail(log.detail())
                .operationTime(log.operationTime())
                .build();
    }

    private ResolvedAccounts resolveAccounts(DomainModels.OperationLog log) {
        String targetType = trimToNull(log.targetType());
        String targetId = trimToNull(log.targetId());
        if (targetType == null || targetId == null) {
            return ResolvedAccounts.empty();
        }

        return switch (targetType.toUpperCase()) {
            case "FUND" -> resolveFromFund(targetId);
            case "SECURITY" -> resolveFromSecurity(targetId);
            case "ACCOUNT" -> resolveFromAccount(targetId);
            case "INVESTOR" -> resolveFromInvestor(targetId);
            default -> ResolvedAccounts.empty();
        };
    }

    private ResolvedAccounts resolveFromFund(String fundAccNo) {
        String secAccNo = dao.fundAccountDao()
                .findByAccountNo(fundAccNo)
                .map(account -> trimToNull(account.secAccNo()))
                .orElse(null);
        return new ResolvedAccounts(secAccNo, fundAccNo);
    }

    private ResolvedAccounts resolveFromSecurity(String secAccNo) {
        String fundAccNo = dao.securityAccountDao()
                .findByAccountNo(secAccNo)
                .map(account -> trimToNull(account.linkedFundAcc()))
                .orElse(null);
        return new ResolvedAccounts(secAccNo, fundAccNo);
    }

    private ResolvedAccounts resolveFromAccount(String accountNo) {
        var fundAccount = dao.fundAccountDao().findByAccountNo(accountNo);
        if (fundAccount.isPresent()) {
            return new ResolvedAccounts(trimToNull(fundAccount.get().secAccNo()), accountNo);
        }

        var securityAccount = dao.securityAccountDao().findByAccountNo(accountNo);
        if (securityAccount.isPresent()) {
            return new ResolvedAccounts(accountNo, trimToNull(securityAccount.get().linkedFundAcc()));
        }

        return ResolvedAccounts.empty();
    }

    private ResolvedAccounts resolveFromInvestor(String investorIdText) {
        Integer investorId;
        try {
            investorId = Integer.valueOf(investorIdText);
        } catch (NumberFormatException ignored) {
            return ResolvedAccounts.empty();
        }

        List<account.dao.model.DomainModels.SecurityAccount> accounts = dao.securityAccountDao().listByInvestorId(investorId)
                .stream()
                .filter(account -> account.status() != DomainEnums.AccountStatus.CLOSED)
                .toList();
        if (accounts.isEmpty()) {
            return ResolvedAccounts.empty();
        }

        Set<String> secAccNos = new LinkedHashSet<>();
        Set<String> fundAccNos = new LinkedHashSet<>();
        for (var account : accounts) {
            String secAccNo = trimToNull(account.secAccNo());
            if (secAccNo != null) {
                secAccNos.add(secAccNo);
            }

            String linkedFundAcc = trimToNull(account.linkedFundAcc());
            if (linkedFundAcc != null) {
                fundAccNos.add(linkedFundAcc);
            }
        }

        return new ResolvedAccounts(join(secAccNos), join(fundAccNos));
    }

    private String join(Set<String> values) {
        return values.isEmpty() ? null : String.join(", ", values);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ResolvedAccounts(String securityAccNo, String fundAccNo) {
        private static ResolvedAccounts empty() {
            return new ResolvedAccounts(null, null);
        }
    }
}
