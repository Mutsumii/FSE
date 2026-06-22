package account.service;

import account.dao.FundAccountDao;
import account.dao.InvestorDao;
import account.dao.SecurityAccountDao;
import account.dao.model.DomainModels.Investor;
import account.integration.BlacklistClient;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

public final class AccountBlacklistSupport {

    private final BlacklistClient blacklistClient;
    private final InvestorDao investorDao;
    private final SecurityAccountDao securityAccountDao;
    private final FundAccountDao fundAccountDao;

    public AccountBlacklistSupport(
            BlacklistClient blacklistClient,
            InvestorDao investorDao,
            SecurityAccountDao securityAccountDao,
            FundAccountDao fundAccountDao
    ) {
        this.blacklistClient = Objects.requireNonNull(blacklistClient, "blacklistClient");
        this.investorDao = Objects.requireNonNull(investorDao, "investorDao");
        this.securityAccountDao = Objects.requireNonNull(securityAccountDao, "securityAccountDao");
        this.fundAccountDao = Objects.requireNonNull(fundAccountDao, "fundAccountDao");
    }

    public boolean isBlockedByUserName(String userName) {
        return blacklistClient.isBlocked(normalizeUserName(userName));
    }

    public boolean isBlockedBySecurityAccountNo(String secAccNo) {
        return blacklistClient.isBlocked(requireUserNameBySecurityAccountNo(secAccNo));
    }

    public boolean isBlockedByFundAccountNo(String fundAccNo) {
        return blacklistClient.isBlocked(requireUserNameByFundAccountNo(fundAccNo));
    }

    public Optional<String> findUserNameBySecurityAccountNo(String secAccNo) {
        String normalizedAccountNo = normalizeAccountNo(secAccNo, "secAccNo");
        return securityAccountDao.findByAccountNo(normalizedAccountNo)
                .flatMap(account -> investorDao.findById(account.investorId()))
                .map(Investor::name);
    }

    public Optional<String> findUserNameByFundAccountNo(String fundAccNo) {
        String normalizedAccountNo = normalizeAccountNo(fundAccNo, "fundAccNo");
        return fundAccountDao.findByAccountNo(normalizedAccountNo)
                .flatMap(account -> securityAccountDao.findByAccountNo(account.secAccNo()))
                .flatMap(securityAccount -> investorDao.findById(securityAccount.investorId()))
                .map(Investor::name);
    }

    private String requireUserNameBySecurityAccountNo(String secAccNo) {
        return findUserNameBySecurityAccountNo(secAccNo)
                .orElseThrow(() -> new NoSuchElementException("No investor found for security account " + secAccNo));
    }

    private String requireUserNameByFundAccountNo(String fundAccNo) {
        return findUserNameByFundAccountNo(fundAccNo)
                .orElseThrow(() -> new NoSuchElementException("No investor found for fund account " + fundAccNo));
    }

    private static String normalizeUserName(String userName) {
        Objects.requireNonNull(userName, "userName");
        String normalized = userName.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("userName must not be blank");
        }
        return normalized;
    }

    private static String normalizeAccountNo(String accountNo, String argumentName) {
        Objects.requireNonNull(accountNo, argumentName);
        String normalized = accountNo.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(argumentName + " must not be blank");
        }
        return normalized;
    }
}
