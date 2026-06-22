package account.service;

import account.common.BusinessException;
import account.common.ErrorCode;
import account.dao.DaoRegistry;
import account.dao.model.DomainEnums;
import account.dao.model.DomainModels;
import account.dto.AdminAccountDetailsResponse;
import account.dto.AdminCloseSecurityAccountRequest;
import account.dto.AdminFreezeRequest;
import account.dto.AdminInvestorFreezeRequest;
import account.dto.AnnualInterestSettlementResponse;
import account.dto.SettleAnnualInterestRequest;
import account.service.api.AdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class AdminServiceImpl implements AdminService {

    private final DaoRegistry dao;

    public AdminServiceImpl(DaoRegistry dao) {
        this.dao = dao;
    }

    private DomainModels.Staff verifyOperator(String staffIdText) {
        int staffId;
        try {
            staffId = Integer.parseInt(staffIdText);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.ERR_009, "工作人员 ID 格式无效: " + staffIdText);
        }

        var staff = dao.staffDao().findById(staffId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_009, "工作人员不存在: " + staffIdText));

        if (!"正常".equals(staff.status())) {
            throw new BusinessException(ErrorCode.ERR_009, "工作人员账号不可用");
        }

        return staff;
    }

    @Override
    public AnnualInterestSettlementResponse settleAnnualInterest(SettleAnnualInterestRequest request) {
        var staff = verifyOperator(request.getOperatorId());
        int staffId = staff.staffId();

        BigDecimal rate = request.getYearRate();
        LocalDate today = LocalDate.now();
        var eligibleAccounts = dao.fundAccountDao().findEligibleForInterestPosting(today);

        int totalCount = 0;
        BigDecimal totalInterest = BigDecimal.ZERO;

        for (var account : eligibleAccounts) {
            try {
                BigDecimal result = dao.transactionManager().execute(connection -> {
                    var locked = dao.fundAccountDao().findByAccountNoForUpdate(connection, account.fundAccNo())
                            .orElse(null);
                    if (locked == null) {
                        return BigDecimal.ZERO;
                    }

                    BigDecimal effectiveRate = rate != null ? rate : locked.annualInterestRate();
                    if (effectiveRate == null || effectiveRate.compareTo(BigDecimal.ZERO) <= 0) {
                        effectiveRate = new BigDecimal("0.0035");
                    }

                    LocalDate lastDate = locked.lastInterestDate() != null
                            ? locked.lastInterestDate()
                            : locked.openDate();
                    long days = ChronoUnit.DAYS.between(lastDate, today);
                    if (days <= 0) {
                        return BigDecimal.ZERO;
                    }

                    BigDecimal interest = locked.availableBalance()
                            .multiply(effectiveRate)
                            .multiply(BigDecimal.valueOf(days))
                            .divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP);

                    if (interest.compareTo(BigDecimal.ZERO) <= 0) {
                        return BigDecimal.ZERO;
                    }

                    BigDecimal newAvailable = locked.availableBalance().add(interest);
                    dao.fundAccountDao().updateInterestPosting(connection, account.fundAccNo(), newAvailable, today);

                    dao.fundTransactionLogDao().create(connection, new DomainModels.FundTransactionLog(
                            null,
                            account.fundAccNo(),
                            DomainEnums.FundTransactionType.INTEREST,
                            interest,
                            newAvailable,
                            locked.frozenBalance(),
                            null,
                            staffId,
                            LocalDateTime.now()
                    ));

                    return interest;
                });

                if (result.compareTo(BigDecimal.ZERO) > 0) {
                    totalCount++;
                    totalInterest = totalInterest.add(result);
                }
            } catch (Exception e) {
                log.error("[settleAnnualInterest] account={} failed: {}", account.fundAccNo(), e.getMessage());
            }
        }

        int settledCount = totalCount;
        BigDecimal settledInterest = totalInterest;
        dao.transactionManager().execute(connection -> {
            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    staffId,
                    "年度结息",
                    "FUND",
                    "batch",
                    "年度结息完成，账户数=" + settledCount + ", 总利息=" + settledInterest,
                    LocalDateTime.now()
            ));
            return null;
        });

        log.info("[settleAnnualInterest] total_accounts={} total_interest={}", totalCount, totalInterest);
        return AnnualInterestSettlementResponse.builder()
                .totalAccounts(totalCount)
                .totalInterest(totalInterest)
                .build();
    }

    @Override
    public void adminFreezeAccount(AdminFreezeRequest request) {
        var admin = verifyOperator(request.getAdminId());

        DomainEnums.AccountType accountType = EnumMapper.toDaoAccountType(request.getAccountType());
        DomainEnums.FreezeType freezeType = EnumMapper.toDaoFreezeType(request.getFreezeType());
        String accountNo = request.getAccountNo();

        dao.transactionManager().execute(connection -> {
            if (accountType == DomainEnums.AccountType.FUND) {
                var fundAccount = dao.fundAccountDao().findByAccountNoForUpdate(connection, accountNo)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ERR_010, "资金账户不存在: " + accountNo));
                if (fundAccount.status() == DomainEnums.AccountStatus.LOSS_FROZEN
                        || fundAccount.status() == DomainEnums.AccountStatus.VIOLATION_FROZEN) {
                    throw new BusinessException(ErrorCode.ERR_011, "账户已经是冻结状态");
                }

                DomainEnums.AccountStatus newStatus = switch (freezeType) {
                    case LOSS -> DomainEnums.AccountStatus.LOSS_FROZEN;
                    case VIOLATION -> DomainEnums.AccountStatus.VIOLATION_FROZEN;
                    default -> throw new BusinessException(ErrorCode.PARAM_INVALID, "不支持的冻结类型");
                };
                freezeFundAccountAssets(connection, fundAccount, newStatus);
            } else {
                var secAccount = dao.securityAccountDao().findByAccountNoForUpdate(connection, accountNo)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ERR_005, "证券账户不存在: " + accountNo));
                if (secAccount.status() == DomainEnums.AccountStatus.LOSS_FROZEN
                        || secAccount.status() == DomainEnums.AccountStatus.VIOLATION_FROZEN) {
                    throw new BusinessException(ErrorCode.ERR_011, "账户已经是冻结状态");
                }

                DomainEnums.AccountStatus newStatus = switch (freezeType) {
                    case LOSS -> DomainEnums.AccountStatus.LOSS_FROZEN;
                    case VIOLATION -> DomainEnums.AccountStatus.VIOLATION_FROZEN;
                    default -> throw new BusinessException(ErrorCode.PARAM_INVALID, "不支持的冻结类型");
                };
                freezeSecurityAccountAssets(connection, secAccount, newStatus);
            }

            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    admin.staffId(),
                    "冻结账户",
                    accountType.dbValue(),
                    accountNo,
                    "管理员冻结，类型: " + freezeType.dbValue()
                            + "，原因: " + Optional.ofNullable(request.getReason()).orElse(""),
                    LocalDateTime.now()
            ));

            log.info("[adminFreezeAccount] accountType={} accountNo={} freezeType={} adminId={}",
                    accountType.dbValue(), accountNo, freezeType.dbValue(), request.getAdminId());
            return null;
        });
    }

    @Override
    public void adminUnfreezeAccount(AdminFreezeRequest request) {
        var admin = verifyOperator(request.getAdminId());

        DomainEnums.AccountType accountType = EnumMapper.toDaoAccountType(request.getAccountType());
        DomainEnums.FreezeType freezeType = EnumMapper.toDaoFreezeType(request.getFreezeType());
        String accountNo = request.getAccountNo();

        if (freezeType == DomainEnums.FreezeType.LOSS) {
            throw new BusinessException(ErrorCode.ERR_021, "挂失冻结不支持管理员解冻，请通过补办流程处理");
        }

        dao.transactionManager().execute(connection -> {
            if (accountType == DomainEnums.AccountType.FUND) {
                var fundAccount = dao.fundAccountDao().findByAccountNoForUpdate(connection, accountNo)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ERR_010, "资金账户不存在: " + accountNo));

                if (fundAccount.status() != DomainEnums.AccountStatus.VIOLATION_FROZEN) {
                    throw new BusinessException(ErrorCode.ERR_021, "资金账户未处于违规冻结状态");
                }

                unfreezeFundAccountAssets(connection, fundAccount);
            } else {
                var secAccount = dao.securityAccountDao().findByAccountNoForUpdate(connection, accountNo)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ERR_005, "证券账户不存在: " + accountNo));

                if (secAccount.status() != DomainEnums.AccountStatus.VIOLATION_FROZEN) {
                    throw new BusinessException(ErrorCode.ERR_021, "证券账户未处于违规冻结状态");
                }

                unfreezeSecurityAccountAssets(connection, secAccount);
            }

            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    admin.staffId(),
                    "解冻账户",
                    accountType.dbValue(),
                    accountNo,
                    "管理员解冻，类型: " + freezeType.dbValue(),
                    LocalDateTime.now()
            ));

            log.info("[adminUnfreezeAccount] accountType={} accountNo={} freezeType={}",
                    accountType.dbValue(), accountNo, freezeType.dbValue());
            return null;
        });
    }

    @Override
    public AdminAccountDetailsResponse adminGetAccountDetails(String accountNo, String adminId) {
        var admin = verifyOperator(adminId);

        dao.transactionManager().execute(connection -> {
            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    admin.staffId(),
                    "查询账户详情",
                    "ACCOUNT",
                    accountNo,
                    "管理员查询账户详情",
                    LocalDateTime.now()
            ));
            return null;
        });

        var fundAccount = dao.fundAccountDao().findByAccountNo(accountNo);
        if (fundAccount.isPresent()) {
            var fa = fundAccount.get();
            var builder = AdminAccountDetailsResponse.builder()
                    .accountNo(accountNo)
                    .accountType("FUND")
                    .fundAccNo(fa.fundAccNo())
                    .secAccNo(fa.secAccNo())
                    .availableBalance(fa.availableBalance())
                    .frozenBalance(fa.frozenBalance())
                    .currency(fa.currency())
                    .status(EnumMapper.fromDaoStatus(fa.status()).code())
                    .openDate(fa.openDate());

            if (fa.secAccNo() != null) {
                var secAccount = dao.securityAccountDao().findByAccountNo(fa.secAccNo());
                if (secAccount.isPresent()) {
                    var investor = dao.investorDao().findById(secAccount.get().investorId());
                    investor.ifPresent(inv -> builder
                            .investorId(inv.investorId())
                            .investorName(inv.name())
                            .idNumber(inv.idNumber())
                            .investorType(EnumMapper.fromDaoInvestorType(inv.type())));
                }
            }
            return builder.build();
        }

        var secAccount = dao.securityAccountDao().findByAccountNo(accountNo);
        if (secAccount.isPresent()) {
            var sa = secAccount.get();
            var builder = AdminAccountDetailsResponse.builder()
                    .accountNo(accountNo)
                    .accountType("SECURITY")
                    .secAccNo(sa.secAccNo())
                    .status(EnumMapper.fromDaoStatus(sa.status()).code())
                    .openDate(sa.openDate())
                    .linkedFundAcc(sa.linkedFundAcc());

            var investor = dao.investorDao().findById(sa.investorId());
            investor.ifPresent(inv -> builder
                    .investorId(inv.investorId())
                    .investorName(inv.name())
                    .idNumber(inv.idNumber())
                    .investorType(EnumMapper.fromDaoInvestorType(inv.type())));

            var holdings = dao.holdingDao().listBySecurityAccountNo(accountNo);
            builder.holdingsCount(holdings.size());
            builder.totalHoldingsQty(holdings.stream().mapToInt(DomainModels.Holding::quantity).sum());
            return builder.build();
        }

        throw new BusinessException(ErrorCode.ERR_010, "账户不存在: " + accountNo);
    }

    @Override
    public void adminCloseSecurityAccount(AdminCloseSecurityAccountRequest request) {
        var admin = verifyOperator(request.getAdminId());
        String secAccNo = request.getSecurityAccountNo();

        dao.transactionManager().execute(connection -> {
            var account = dao.securityAccountDao().findByAccountNoForUpdate(connection, secAccNo)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ERR_005, "证券账户不存在: " + secAccNo));

            if (account.status() == DomainEnums.AccountStatus.CLOSED) {
                throw new BusinessException(ErrorCode.ERR_021, "证券账户已销户");
            }

            if (account.linkedFundAcc() != null && !account.linkedFundAcc().isBlank()) {
                var fundAccount = dao.fundAccountDao().findByAccountNo(account.linkedFundAcc());
                if (fundAccount.isPresent()) {
                    var fa = fundAccount.get();
                    if (fa.availableBalance().compareTo(BigDecimal.ZERO) > 0
                            || fa.frozenBalance().compareTo(BigDecimal.ZERO) > 0) {
                        throw new BusinessException(ErrorCode.ERR_007, "关联资金账户尚有余额或冻结资金，请先清空资金账户");
                    }
                    if (fa.status() == DomainEnums.AccountStatus.LOSS_FROZEN
                            || fa.status() == DomainEnums.AccountStatus.VIOLATION_FROZEN) {
                        throw new BusinessException(ErrorCode.ERR_017, "关联资金账户处于冻结状态，请先解冻资金账户");
                    }
                }
            }

            dao.securityAccountDao().updateStatus(connection, secAccNo, DomainEnums.AccountStatus.CLOSED);

            if (account.linkedFundAcc() != null && !account.linkedFundAcc().isBlank()) {
                dao.securityAccountDao().unbindFundAccount(connection, secAccNo);
            }

            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    admin.staffId(),
                    "强制销户",
                    "SECURITY",
                    secAccNo,
                    "管理员强制销户，原因: " + request.getForceReason(),
                    LocalDateTime.now()
            ));

            log.info("[adminCloseSecurityAccount] sec_acc_no={} force_reason={}",
                    secAccNo, request.getForceReason());
            return null;
        });
    }

    @Override
    public void adminFreezeInvestorByIdNumber(AdminInvestorFreezeRequest request) {
        var admin = verifyOperator(request.getAdminId());
        var investor = dao.investorDao().findByIdNumber(request.getIdNumber())
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_010, "投资者不存在: " + request.getIdNumber()));

        dao.transactionManager().execute(connection -> {
            Map<String, DomainModels.SecurityAccount> securityAccounts = new LinkedHashMap<>();
            for (var account : dao.securityAccountDao().listByInvestorId(investor.investorId())) {
                var locked = dao.securityAccountDao().findByAccountNoForUpdate(connection, account.secAccNo()).orElse(null);
                if (locked == null || locked.status() == DomainEnums.AccountStatus.CLOSED) {
                    continue;
                }
                securityAccounts.put(locked.secAccNo(), locked);
            }

            Map<String, DomainModels.FundAccount> fundAccounts = new LinkedHashMap<>();
            for (var securityAccount : securityAccounts.values()) {
                var linkedFund = dao.fundAccountDao().findBySecurityAccountNo(securityAccount.secAccNo()).orElse(null);
                if (linkedFund == null) {
                    continue;
                }
                var lockedFund = dao.fundAccountDao().findByAccountNoForUpdate(connection, linkedFund.fundAccNo()).orElse(null);
                if (lockedFund == null || lockedFund.status() == DomainEnums.AccountStatus.CLOSED) {
                    continue;
                }
                fundAccounts.put(lockedFund.fundAccNo(), lockedFund);
            }

            int affectedCount = 0;
            for (var fundAccount : fundAccounts.values()) {
                if (fundAccount.status() == DomainEnums.AccountStatus.VIOLATION_FROZEN) {
                    continue;
                }
                if (fundAccount.status() == DomainEnums.AccountStatus.LOSS_FROZEN
                        || fundAccount.status() == DomainEnums.AccountStatus.PRE_CLOSE) {
                    continue;
                }
                freezeFundAccountSelf(connection, fundAccount, DomainEnums.AccountStatus.VIOLATION_FROZEN);
                affectedCount++;
            }

            for (var securityAccount : securityAccounts.values()) {
                if (securityAccount.status() == DomainEnums.AccountStatus.VIOLATION_FROZEN) {
                    continue;
                }
                if (securityAccount.status() == DomainEnums.AccountStatus.LOSS_FROZEN
                        || securityAccount.status() == DomainEnums.AccountStatus.PRE_CLOSE) {
                    continue;
                }
                freezeSecurityAccountSelf(connection, securityAccount, DomainEnums.AccountStatus.VIOLATION_FROZEN);
                affectedCount++;
            }

            if (affectedCount == 0) {
                throw new BusinessException(ErrorCode.ERR_021, "该投资者名下没有可冻结的账户");
            }

            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    admin.staffId(),
                    "按身份证冻结账户",
                    "INVESTOR",
                    request.getIdNumber(),
                    "管理端按身份证执行违规冻结，原因: " + Optional.ofNullable(request.getReason()).orElse(""),
                    LocalDateTime.now()
            ));
            return null;
        });
    }

    @Override
    public void adminUnfreezeInvestorByIdNumber(AdminInvestorFreezeRequest request) {
        var admin = verifyOperator(request.getAdminId());
        var investor = dao.investorDao().findByIdNumber(request.getIdNumber())
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_010, "投资者不存在: " + request.getIdNumber()));

        dao.transactionManager().execute(connection -> {
            Map<String, DomainModels.SecurityAccount> securityAccounts = new LinkedHashMap<>();
            for (var account : dao.securityAccountDao().listByInvestorId(investor.investorId())) {
                var locked = dao.securityAccountDao().findByAccountNoForUpdate(connection, account.secAccNo()).orElse(null);
                if (locked == null || locked.status() == DomainEnums.AccountStatus.CLOSED) {
                    continue;
                }
                securityAccounts.put(locked.secAccNo(), locked);
            }

            Map<String, DomainModels.FundAccount> fundAccounts = new LinkedHashMap<>();
            for (var securityAccount : securityAccounts.values()) {
                var linkedFund = dao.fundAccountDao().findBySecurityAccountNo(securityAccount.secAccNo()).orElse(null);
                if (linkedFund == null) {
                    continue;
                }
                var lockedFund = dao.fundAccountDao().findByAccountNoForUpdate(connection, linkedFund.fundAccNo()).orElse(null);
                if (lockedFund == null || lockedFund.status() == DomainEnums.AccountStatus.CLOSED) {
                    continue;
                }
                fundAccounts.put(lockedFund.fundAccNo(), lockedFund);
            }

            int affectedCount = 0;
            for (var fundAccount : fundAccounts.values()) {
                if (fundAccount.status() != DomainEnums.AccountStatus.VIOLATION_FROZEN) {
                    continue;
                }
                unfreezeFundAccountSelf(connection, fundAccount);
                affectedCount++;
            }

            for (var securityAccount : securityAccounts.values()) {
                if (securityAccount.status() != DomainEnums.AccountStatus.VIOLATION_FROZEN) {
                    continue;
                }
                DomainEnums.AccountStatus restoredStatus =
                        securityAccount.linkedFundAcc() == null || securityAccount.linkedFundAcc().isBlank()
                                ? DomainEnums.AccountStatus.NO_FUND_FROZEN
                                : DomainEnums.AccountStatus.NORMAL;
                unfreezeSecurityAccountSelf(connection, securityAccount, restoredStatus);
                affectedCount++;
            }

            if (affectedCount == 0) {
                throw new BusinessException(ErrorCode.ERR_021, "该投资者名下没有处于违规冻结状态的账户");
            }

            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    admin.staffId(),
                    "按身份证解冻账户",
                    "INVESTOR",
                    request.getIdNumber(),
                    "管理端按身份证解除违规冻结",
                    LocalDateTime.now()
            ));
            return null;
        });
    }

    private void freezeFundAccountAssets(
            java.sql.Connection connection,
            DomainModels.FundAccount fundAccount,
            DomainEnums.AccountStatus newStatus
    ) {
        freezeFundAccountSelf(connection, fundAccount, newStatus);

        if (fundAccount.secAccNo() == null || fundAccount.secAccNo().isBlank()) {
            return;
        }

        var linkedSecurityAccount = dao.securityAccountDao().findByAccountNoForUpdate(connection, fundAccount.secAccNo())
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_005, "鍏宠仈璇佸埜璐︽埛涓嶅瓨鍦? " + fundAccount.secAccNo()));
        freezeSecurityAccountAssets(connection, linkedSecurityAccount, newStatus);
    }

    private void unfreezeFundAccountAssets(
            java.sql.Connection connection,
            DomainModels.FundAccount fundAccount
    ) {
        unfreezeFundAccountSelf(connection, fundAccount);

        if (fundAccount.secAccNo() == null || fundAccount.secAccNo().isBlank()) {
            return;
        }

        var linkedSecurityAccount = dao.securityAccountDao().findByAccountNoForUpdate(connection, fundAccount.secAccNo())
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_005, "鍏宠仈璇佸埜璐︽埛涓嶅瓨鍦? " + fundAccount.secAccNo()));
        if (linkedSecurityAccount.status() == DomainEnums.AccountStatus.VIOLATION_FROZEN) {
            unfreezeSecurityAccountAssets(connection, linkedSecurityAccount);
        }
    }

    private void freezeSecurityAccountAssets(
            java.sql.Connection connection,
            DomainModels.SecurityAccount securityAccount,
            DomainEnums.AccountStatus newStatus
    ) {
        freezeSecurityAccountSelf(connection, securityAccount, newStatus);
    }

    private void unfreezeSecurityAccountAssets(
            java.sql.Connection connection,
            DomainModels.SecurityAccount securityAccount
    ) {
        unfreezeSecurityAccountSelf(connection, securityAccount, DomainEnums.AccountStatus.NORMAL);
    }

    private void freezeFundAccountSelf(
            java.sql.Connection connection,
            DomainModels.FundAccount fundAccount,
            DomainEnums.AccountStatus newStatus
    ) {
        BigDecimal totalBalance = fundAccount.availableBalance().add(fundAccount.frozenBalance());
        dao.fundAccountDao().updateBalances(connection, fundAccount.fundAccNo(), BigDecimal.ZERO, totalBalance);
        dao.fundAccountDao().updateStatus(connection, fundAccount.fundAccNo(), newStatus);
    }

    private void unfreezeFundAccountSelf(
            java.sql.Connection connection,
            DomainModels.FundAccount fundAccount
    ) {
        BigDecimal totalBalance = fundAccount.availableBalance().add(fundAccount.frozenBalance());
        dao.fundAccountDao().updateBalances(connection, fundAccount.fundAccNo(), totalBalance, BigDecimal.ZERO);
        dao.fundAccountDao().updateStatus(connection, fundAccount.fundAccNo(), DomainEnums.AccountStatus.NORMAL);
    }

    private void freezeSecurityAccountSelf(
            java.sql.Connection connection,
            DomainModels.SecurityAccount securityAccount,
            DomainEnums.AccountStatus newStatus
    ) {
        freezeAllSecurityHoldings(connection, securityAccount.secAccNo());
        dao.securityAccountDao().updateStatus(connection, securityAccount.secAccNo(), newStatus);
    }

    private void unfreezeSecurityAccountSelf(
            java.sql.Connection connection,
            DomainModels.SecurityAccount securityAccount,
            DomainEnums.AccountStatus restoredStatus
    ) {
        unfreezeAllSecurityHoldings(connection, securityAccount.secAccNo());
        dao.securityAccountDao().updateStatus(connection, securityAccount.secAccNo(), restoredStatus);
    }

    private void freezeAllSecurityHoldings(java.sql.Connection connection, String secAccNo) {
        for (var holding : dao.holdingDao().listBySecurityAccountNo(secAccNo)) {
            dao.holdingDao().saveOrUpdate(connection, new DomainModels.Holding(
                    holding.holdingId(),
                    holding.secAccNo(),
                    holding.stockCode(),
                    holding.stockName(),
                    0,
                    holding.quantity() + holding.frozenQuantity(),
                    holding.avgCost(),
                    LocalDateTime.now()
            ));
        }
    }

    private void unfreezeAllSecurityHoldings(java.sql.Connection connection, String secAccNo) {
        for (var holding : dao.holdingDao().listBySecurityAccountNo(secAccNo)) {
            dao.holdingDao().saveOrUpdate(connection, new DomainModels.Holding(
                    holding.holdingId(),
                    holding.secAccNo(),
                    holding.stockCode(),
                    holding.stockName(),
                    holding.quantity() + holding.frozenQuantity(),
                    0,
                    holding.avgCost(),
                    LocalDateTime.now()
            ));
        }
    }
}
