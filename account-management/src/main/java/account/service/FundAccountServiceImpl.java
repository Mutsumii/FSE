package account.service;

import account.common.BusinessException;
import account.common.ErrorCode;
import account.dao.DaoRegistry;
import account.dao.model.DomainEnums;
import account.dao.model.DomainModels;
import account.dto.AccountBindingResponse;
import account.dto.AccountStatusResponse;
import account.dto.ChangeFundPasswordRequest;
import account.dto.ClientChangeFundPasswordRequest;
import account.dto.ClientDepositRequest;
import account.dto.ClientLoginAuthResponse;
import account.dto.ClientWithdrawRequest;
import account.dto.CloseFundAccountRequest;
import account.dto.CompleteLoginCertificateRequest;
import account.dto.CreateFundAccountRequest;
import account.dto.DepositRequest;
import account.dto.FundAccountCreatedResponse;
import account.dto.FundAccountListItemResponse;
import account.dto.FundBalanceChangeResponse;
import account.dto.FundInfoResponse;
import account.dto.FundLogView;
import account.dto.FundReissueResponse;
import account.dto.FundSnapshotResponse;
import account.dto.FundTradeUpdateResponse;
import account.dto.ReissueFundAccountRequest;
import account.dto.ReportFundLossRequest;
import account.dto.UpdateFundBalanceRequest;
import account.dto.WithdrawRequest;
import account.enums.AccountStatus;
import account.integration.BlacklistClient;
import account.integration.BlacklistClientException;
import account.service.api.ClientAuthTokenService;
import account.service.api.FundAccountService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FundAccountServiceImpl implements FundAccountService {

    private static final String FUND_CERTIFICATE_SUBJECT = "FUND";
    private static final String DEFAULT_CERTIFICATE_CODE = "CERT-123456";

    private final DaoRegistry dao;
    private final BlacklistClient blacklistClient;
    private final ClientAuthTokenService clientAuthTokenService;

    public FundAccountServiceImpl(
            DaoRegistry dao,
            BlacklistClient blacklistClient,
            ClientAuthTokenService clientAuthTokenService) {
        this.dao = dao;
        this.blacklistClient = blacklistClient;
        this.clientAuthTokenService = clientAuthTokenService;
    }

    @Override
    public FundAccountCreatedResponse createFundAccount(CreateFundAccountRequest request) {
        var secAccount = dao.securityAccountDao().findByAccountNo(request.getSecAccNo())
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_005, "证券账户不存在: " + request.getSecAccNo()));

        if (secAccount.linkedFundAcc() != null && !secAccount.linkedFundAcc().isBlank()) {
            throw new BusinessException(ErrorCode.ERR_014, "证券账户已绑定资金账户: " + secAccount.linkedFundAcc());
        }

        var investor = dao.investorDao().findById(secAccount.investorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_013, "证券账户持有人不存在"));
        if (!investor.idNumber().equals(request.getIdNumber())) {
            throw new BusinessException(ErrorCode.ERR_013, "身份证号与证券账户持有人不一致");
        }

        if (isBlacklistedBySecurityAccountNo(request.getSecAccNo())) {
            throw new BusinessException(ErrorCode.ERR_012, "投资者在黑名单中，无法开立资金账户");
        }

        if (secAccount.status() == DomainEnums.AccountStatus.CLOSED) {
            throw new BusinessException(ErrorCode.ERR_021, "证券账户已销户，无法开立资金账户");
        }
        if (secAccount.status() == DomainEnums.AccountStatus.LOSS_FROZEN
                || secAccount.status() == DomainEnums.AccountStatus.VIOLATION_FROZEN) {
            throw new BusinessException(ErrorCode.ERR_003, "证券账户已冻结，无法开立资金账户");
        }

        String fundAccNo = AccountNumberGenerator.generateFundAccountNo();
        String currency = Optional.ofNullable(request.getCurrency()).orElse("CNY");

        return dao.transactionManager().execute(connection -> {
            dao.fundAccountDao().create(connection, new DomainModels.FundAccount(
                    fundAccNo,
                    request.getSecAccNo(),
                    PasswordUtil.hash(request.getTradePassword()),
                    PasswordUtil.hash(request.getWithdrawPassword()),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    currency,
                    DomainEnums.AccountStatus.NORMAL,
                    LocalDate.now(),
                    null,
                    new BigDecimal("0.0035")
            ));

            dao.securityAccountDao().bindFundAccount(connection, request.getSecAccNo(), fundAccNo);
            if (secAccount.status() == DomainEnums.AccountStatus.NO_FUND_FROZEN) {
                dao.securityAccountDao().updateStatus(connection, request.getSecAccNo(), DomainEnums.AccountStatus.NORMAL);
            }

            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    request.getStaffId(),
                    "资金开户",
                    "FUND",
                    fundAccNo,
                    "开立资金账户并绑定证券账户 " + request.getSecAccNo(),
                    LocalDateTime.now()
            ));

            return FundAccountCreatedResponse.builder()
                    .fundAccNo(fundAccNo)
                    .status(AccountStatus.NORMAL.code())
                    .secAccNo(request.getSecAccNo())
                    .currency(currency)
                    .build();
        });
    }

    @Override
    public FundBalanceChangeResponse deposit(DepositRequest request) {
        return dao.transactionManager().execute(connection -> {
            var account = dao.fundAccountDao().findByAccountNoForUpdate(connection, request.getFundAccNo())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ERR_010, "资金账户不存在: " + request.getFundAccNo()));

            checkAccountNotFrozenOrClosed(account.status(), request.getFundAccNo());

            BigDecimal newAvailable = account.availableBalance().add(request.getAmount());
            dao.fundAccountDao().updateBalances(connection, request.getFundAccNo(), newAvailable, account.frozenBalance());

            long logId = dao.fundTransactionLogDao().create(connection, new DomainModels.FundTransactionLog(
                    null,
                    request.getFundAccNo(),
                    DomainEnums.FundTransactionType.DEPOSIT,
                    request.getAmount(),
                    newAvailable,
                    account.frozenBalance(),
                    null,
                    request.getStaffId(),
                    LocalDateTime.now()
            ));

            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    request.getStaffId(),
                    "资金存款",
                    "FUND",
                    request.getFundAccNo(),
                    "存款 " + request.getAmount(),
                    LocalDateTime.now()
            ));

            return FundBalanceChangeResponse.builder()
                    .availableBalance(newAvailable)
                    .logId(logId)
                    .build();
        });
    }

    @Override
    public FundBalanceChangeResponse withdraw(WithdrawRequest request) {
        return dao.transactionManager().execute(connection -> {
            var account = dao.fundAccountDao().findByAccountNoForUpdate(connection, request.getFundAccNo())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ERR_010, "资金账户不存在: " + request.getFundAccNo()));

            checkAccountNotFrozenOrClosed(account.status(), request.getFundAccNo());

            if (!PasswordUtil.verify(request.getWithdrawPassword(), account.withdrawPassword())) {
                throw new BusinessException(ErrorCode.ERR_004, "取款密码错误");
            }
            if (account.availableBalance().compareTo(request.getAmount()) < 0) {
                throw new BusinessException(ErrorCode.ERR_001, "可用余额不足");
            }

            BigDecimal newAvailable = account.availableBalance().subtract(request.getAmount());
            dao.fundAccountDao().updateBalances(connection, request.getFundAccNo(), newAvailable, account.frozenBalance());

            long logId = dao.fundTransactionLogDao().create(connection, new DomainModels.FundTransactionLog(
                    null,
                    request.getFundAccNo(),
                    DomainEnums.FundTransactionType.WITHDRAW,
                    request.getAmount(),
                    newAvailable,
                    account.frozenBalance(),
                    null,
                    request.getStaffId(),
                    LocalDateTime.now()
            ));

            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    request.getStaffId(),
                    "资金取款",
                    "FUND",
                    request.getFundAccNo(),
                    "取款 " + request.getAmount(),
                    LocalDateTime.now()
            ));

            return FundBalanceChangeResponse.builder()
                    .availableBalance(newAvailable)
                    .logId(logId)
                    .build();
        });
    }

    @Override
    public void changeFundPassword(ChangeFundPasswordRequest request) {
        changePasswordInternal(
                request.getFundAccNo(),
                request.getPasswordType(),
                request.getOldPassword(),
                request.getNewPassword(),
                request.getStaffId(),
                true
        );
    }

    @Override
    public AccountStatusResponse reportFundLoss(ReportFundLossRequest request) {
        return dao.transactionManager().execute(connection -> {
            var account = dao.fundAccountDao().findByAccountNoForUpdate(connection, request.getFundAccNo())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ERR_010, "资金账户不存在: " + request.getFundAccNo()));

            verifyOwnership(account.secAccNo(), request.getIdNumber());
            verifySecurityAccountNumber(account.secAccNo(), request.getSecAccNo());

            if (account.status() == DomainEnums.AccountStatus.LOSS_FROZEN) {
                throw new BusinessException(ErrorCode.ERR_021, "资金账户已处于挂失冻结状态");
            }
            if (account.status() == DomainEnums.AccountStatus.CLOSED
                    || account.status() == DomainEnums.AccountStatus.PRE_CLOSE) {
                throw new BusinessException(ErrorCode.ERR_021, "资金账户当前状态不允许挂失");
            }

            BigDecimal frozenBalance = account.frozenBalance().add(account.availableBalance());
            dao.fundAccountDao().updateBalances(connection, request.getFundAccNo(), BigDecimal.ZERO, frozenBalance);
            dao.fundAccountDao().updateStatus(connection, request.getFundAccNo(), DomainEnums.AccountStatus.LOSS_FROZEN);
            freezeLinkedSecurityForFundLoss(connection, account.secAccNo());

            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    request.getStaffId(),
                    "挂失",
                    "FUND",
                    request.getFundAccNo(),
                    "资金账户挂失，原因=" + Optional.ofNullable(request.getReason()).orElse(""),
                    LocalDateTime.now()
            ));

            return AccountStatusResponse.builder()
                    .status(AccountStatus.FROZEN_LOSS.code())
                    .build();
        });
    }

    @Override
    public FundReissueResponse reissueFundAccount(ReissueFundAccountRequest request) {
        return dao.transactionManager().execute(connection -> {
            var oldAccount = dao.fundAccountDao().findByAccountNoForUpdate(connection, request.getOldFundAccNo())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ERR_010, "资金账户不存在: " + request.getOldFundAccNo()));

            verifyOwnership(oldAccount.secAccNo(), request.getIdNumber());
            verifySecurityAccountNumber(oldAccount.secAccNo(), request.getSecAccNo());

            if (!oldAccount.currency().equalsIgnoreCase(request.getCurrency())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "补办币种必须与原资金账户一致");
            }
            if (isBlacklistedBySecurityAccountNo(request.getSecAccNo())) {
                throw new BusinessException(ErrorCode.ERR_012, "投资者在黑名单中，无法补办资金账户");
            }

            if (oldAccount.status() != DomainEnums.AccountStatus.LOSS_FROZEN) {
                throw new BusinessException(ErrorCode.ERR_021, "资金账户当前状态不允许补办");
            }

            String newFundAccNo = AccountNumberGenerator.generateFundAccountNo();
            BigDecimal migratedBalance = oldAccount.availableBalance().add(oldAccount.frozenBalance());
            dao.fundAccountDao().relinkSecurityAccount(connection, request.getOldFundAccNo(), null);
            dao.fundAccountDao().create(connection, new DomainModels.FundAccount(
                    newFundAccNo,
                    oldAccount.secAccNo(),
                    PasswordUtil.hash(request.getNewTradePassword()),
                    PasswordUtil.hash(request.getNewWithdrawPassword()),
                    migratedBalance,
                    BigDecimal.ZERO,
                    oldAccount.currency(),
                    DomainEnums.AccountStatus.NORMAL,
                    LocalDate.now(),
                    oldAccount.lastInterestDate(),
                    oldAccount.annualInterestRate()
            ));

            dao.fundAccountDao().updateBalances(connection, request.getOldFundAccNo(), BigDecimal.ZERO, BigDecimal.ZERO);
            dao.fundAccountDao().updateStatus(connection, request.getOldFundAccNo(), DomainEnums.AccountStatus.CLOSED);
            dao.securityAccountDao().bindFundAccount(connection, oldAccount.secAccNo(), newFundAccNo);
            normalizeSecurityAfterRebind(connection, oldAccount.secAccNo());

            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    request.getStaffId(),
                    "补办",
                    "FUND",
                    newFundAccNo,
                    "补办资金账户，旧账户=" + request.getOldFundAccNo(),
                    LocalDateTime.now()
            ));

            clientAuthTokenService.invalidateByFundAccount(request.getOldFundAccNo());
            return FundReissueResponse.builder()
                    .newFundAccNo(newFundAccNo)
                    .oldFundAccNo(request.getOldFundAccNo())
                    .build();
        });
    }

    @Override
    public AccountStatusResponse closeFundAccount(CloseFundAccountRequest request) {
        return dao.transactionManager().execute(connection -> {
            var account = dao.fundAccountDao().findByAccountNoForUpdate(connection, request.getFundAccNo())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ERR_010, "资金账户不存在: " + request.getFundAccNo()));

            verifyOwnership(account.secAccNo(), request.getIdNumber());

            if (account.status() == DomainEnums.AccountStatus.CLOSED) {
                throw new BusinessException(ErrorCode.ERR_021, "资金账户已销户");
            }
            if (account.status() == DomainEnums.AccountStatus.LOSS_FROZEN
                    || account.status() == DomainEnums.AccountStatus.VIOLATION_FROZEN) {
                throw new BusinessException(ErrorCode.ERR_017, "资金账户处于冻结状态，无法销户");
            }
            if (account.availableBalance().compareTo(BigDecimal.ZERO) > 0
                    || account.frozenBalance().compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException(ErrorCode.ERR_007, "资金账户尚有余额或冻结资金，无法销户");
            }

            dao.fundAccountDao().updateStatus(connection, request.getFundAccNo(), DomainEnums.AccountStatus.CLOSED);

            if (account.secAccNo() != null && !account.secAccNo().isBlank()) {
                dao.securityAccountDao().unbindFundAccount(connection, account.secAccNo());
                freezeSecurityForNoFund(connection, account.secAccNo());
            }
            dao.fundAccountDao().relinkSecurityAccount(connection, request.getFundAccNo(), null);

            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    request.getStaffId(),
                    "销户",
                    "FUND",
                    request.getFundAccNo(),
                    "资金账户销户，原因=" + Optional.ofNullable(request.getReason()).orElse(""),
                    LocalDateTime.now()
            ));

            clientAuthTokenService.invalidateByFundAccount(request.getFundAccNo());
            return AccountStatusResponse.builder()
                    .status(AccountStatus.CLOSED.code())
                    .build();
        });
    }

    @Override
    public FundInfoResponse queryFundInfo(String fundAccNo, String idNumber, boolean includeLogs, Integer staffId) {
        var account = dao.fundAccountDao().findByAccountNo(fundAccNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_010, "资金账户不存在: " + fundAccNo));

        if (idNumber != null && !idNumber.isBlank() && account.secAccNo() != null && !account.secAccNo().isBlank()) {
            verifyOwnership(account.secAccNo(), idNumber);
        }

        FundInfoResponse.FundInfoResponseBuilder builder = FundInfoResponse.builder()
                .fundAccNo(fundAccNo)
                .availableBalance(account.availableBalance())
                .frozenBalance(account.frozenBalance())
                .currency(account.currency())
                .status(EnumMapper.fromDaoStatus(account.status()).code());

        if (includeLogs) {
            builder.logs(dao.fundTransactionLogDao().listRecentByFundAccountNo(fundAccNo, 50).stream()
                    .map(this::toUnifiedFundLogView)
                    .toList());
        }

        if (staffId != null) {
            dao.transactionManager().execute(connection -> {
                dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                        null,
                        staffId,
                        "查询资金账户",
                        "FUND",
                        fundAccNo,
                        "查询资金账户信息 include_logs=" + includeLogs,
                        LocalDateTime.now()
                ));
                return null;
            });
        }

        return builder.build();
    }

    @Override
    public List<FundLogView> queryFundLogs(String fundAccNo, String idNumber, int limit, Integer staffId) {
        var account = dao.fundAccountDao().findByAccountNo(fundAccNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_010, "资金账户不存在: " + fundAccNo));

        if (idNumber != null && !idNumber.isBlank() && account.secAccNo() != null && !account.secAccNo().isBlank()) {
            verifyOwnership(account.secAccNo(), idNumber);
        }

        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<FundLogView> logs = dao.fundTransactionLogDao().listRecentByFundAccountNo(fundAccNo, safeLimit).stream()
                .map(this::toUnifiedFundLogView)
                .toList();

        if (staffId != null) {
            dao.transactionManager().execute(connection -> {
                dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                        null,
                        staffId,
                        "查询资金流水",
                        "FUND",
                        fundAccNo,
                        "查询资金流水 limit=" + safeLimit,
                        LocalDateTime.now()
                ));
                return null;
            });
        }

        return logs;
    }

    @Override
    public ClientLoginAuthResponse clientLoginAuth(String fundAccNo, String tradePassword) {
        var account = dao.fundAccountDao().findByAccountNo(fundAccNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_010, "资金账户不存在"));

        checkAccountNotFrozenOrClosed(account.status(), fundAccNo);
        if (account.secAccNo() == null || account.secAccNo().isBlank()) {
            throw new BusinessException(ErrorCode.ERR_015, "资金账户未绑定证券账户，无法登录");
        }
        if (!PasswordUtil.verify(tradePassword, account.tradePassword())) {
            throw new BusinessException(ErrorCode.ERR_004, "交易密码错误");
        }

        var certificateState = ensureCertificateState(FUND_CERTIFICATE_SUBJECT, fundAccNo);
        if (!certificateState.certificateVerified()) {
            return ClientLoginAuthResponse.builder()
                    .fundAccNo(fundAccNo)
                    .secAccNo(account.secAccNo())
                    .status(AccountStatus.NORMAL.code())
                    .requiresCertificate(true)
                    .certificateSubjectType(FUND_CERTIFICATE_SUBJECT)
                    .certificateSubjectKey(fundAccNo)
                    .build();
        }

        String authToken = clientAuthTokenService.issueToken(fundAccNo, account.secAccNo());
        return ClientLoginAuthResponse.builder()
                .authToken(authToken)
                .fundAccNo(fundAccNo)
                .secAccNo(account.secAccNo())
                .status(AccountStatus.NORMAL.code())
                .requiresCertificate(false)
                .build();
    }

    @Override
    public FundSnapshotResponse getFundSnapshot(String fundAccNo, String authToken) {
        clientAuthTokenService.requireFundAccess(authToken, fundAccNo);
        var account = dao.fundAccountDao().findByAccountNo(fundAccNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_010, "资金账户不存在"));

        if (account.status() == DomainEnums.AccountStatus.LOSS_FROZEN
                || account.status() == DomainEnums.AccountStatus.VIOLATION_FROZEN) {
            throw new BusinessException(ErrorCode.ERR_003, "账户已冻结");
        }
        if (account.status() == DomainEnums.AccountStatus.CLOSED) {
            throw new BusinessException(ErrorCode.ERR_010, "账户已销户");
        }

        List<FundLogView> recentLogs = dao.fundTransactionLogDao().listRecentByFundAccountNo(fundAccNo, 10).stream()
                .map(this::toUnifiedFundLogView)
                .toList();

        return FundSnapshotResponse.builder()
                .availableBalance(account.availableBalance())
                .frozenBalance(account.frozenBalance())
                .currency(account.currency())
                .status(EnumMapper.fromDaoStatus(account.status()).code())
                .recentLogs(recentLogs)
                .build();
    }

    @Override
    public ClientLoginAuthResponse completeLoginCertificate(CompleteLoginCertificateRequest request) {
        if (!FUND_CERTIFICATE_SUBJECT.equalsIgnoreCase(request.getSubjectType())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "subject_type must be FUND");
        }
        if (!DEFAULT_CERTIFICATE_CODE.equals(request.getCertificateCode().trim())) {
            throw new BusinessException(ErrorCode.ERR_004, "安全证书认证码错误");
        }

        var account = dao.fundAccountDao().findByAccountNo(request.getSubjectKey())
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_010, "资金账户不存在"));

        checkAccountNotFrozenOrClosed(account.status(), account.fundAccNo());
        if (account.secAccNo() == null || account.secAccNo().isBlank()) {
            throw new BusinessException(ErrorCode.ERR_015, "资金账户未绑定证券账户，无法登录");
        }

        dao.transactionManager().execute(connection -> {
            dao.loginCertificateStateDao().ensureExists(connection, FUND_CERTIFICATE_SUBJECT, account.fundAccNo());
            dao.loginCertificateStateDao().markVerified(connection, FUND_CERTIFICATE_SUBJECT, account.fundAccNo(), LocalDateTime.now());
            return null;
        });

        String authToken = clientAuthTokenService.issueToken(account.fundAccNo(), account.secAccNo());
        return ClientLoginAuthResponse.builder()
                .authToken(authToken)
                .fundAccNo(account.fundAccNo())
                .secAccNo(account.secAccNo())
                .status(AccountStatus.NORMAL.code())
                .requiresCertificate(false)
                .build();
    }

    @Override
    public void clientChangeFundPassword(ClientChangeFundPasswordRequest request) {
        clientAuthTokenService.requireFundAccess(request.getAuthToken(), request.getFundAccNo());
        changePasswordInternal(
                request.getFundAccNo(),
                request.getPasswordType(),
                request.getOldPassword(),
                request.getNewPassword(),
                null,
                false
        );
    }

    @Override
    public FundBalanceChangeResponse clientDeposit(ClientDepositRequest request) {
        clientAuthTokenService.requireFundAccess(request.getAuthToken(), request.getFundAccNo());
        DepositRequest delegate = new DepositRequest();
        delegate.setFundAccNo(request.getFundAccNo());
        delegate.setAmount(request.getAmount());
        return deposit(delegate);
    }

    @Override
    public FundBalanceChangeResponse clientWithdraw(ClientWithdrawRequest request) {
        clientAuthTokenService.requireFundAccess(request.getAuthToken(), request.getFundAccNo());
        WithdrawRequest delegate = new WithdrawRequest();
        delegate.setFundAccNo(request.getFundAccNo());
        delegate.setAmount(request.getAmount());
        delegate.setWithdrawPassword(request.getWithdrawPassword());
        return withdraw(delegate);
    }

    @Override
    public FundTradeUpdateResponse updateFundBalance(UpdateFundBalanceRequest request) {
        DomainEnums.FundTransactionType daoType = mapTxnType(request.getTxnType());

        return dao.transactionManager().execute(connection -> {
            var account = dao.fundAccountDao().findByAccountNoForUpdate(connection, request.getFundAccNo())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ERR_010, "资金账户不存在: " + request.getFundAccNo()));

            if (dao.fundTransactionLogDao().existsByRefOrderIdAndTxnType(
                    connection, request.getRefOrderId(), daoType)) {
                return FundTradeUpdateResponse.builder()
                        .availableBalance(account.availableBalance())
                        .frozenBalance(account.frozenBalance())
                        .duplicate(true)
                        .build();
            }

            checkAccountNotFrozenOrClosed(account.status(), request.getFundAccNo());
            if (account.secAccNo() == null || account.secAccNo().isBlank()) {
                throw new BusinessException(ErrorCode.ERR_015, "资金账户未绑定证券账户");
            }

            BigDecimal newAvailable = account.availableBalance();
            BigDecimal newFrozen = account.frozenBalance();

            switch (request.getTxnType()) {
                case "买入冻结" -> {
                    if (newAvailable.compareTo(request.getAmount()) < 0) {
                        throw new BusinessException(ErrorCode.ERR_001, "可用余额不足");
                    }
                    newAvailable = newAvailable.subtract(request.getAmount());
                    newFrozen = newFrozen.add(request.getAmount());
                }
                case "买入扣款" -> {
                    if (newFrozen.compareTo(request.getAmount()) < 0) {
                        throw new BusinessException(ErrorCode.ERR_001, "冻结资金不足");
                    }
                    newFrozen = newFrozen.subtract(request.getAmount());
                }
                case "卖出回款" -> newAvailable = newAvailable.add(request.getAmount());
                case "撤单解冻" -> {
                    if (newFrozen.compareTo(request.getAmount()) < 0) {
                        throw new BusinessException(ErrorCode.ERR_001, "冻结资金不足");
                    }
                    newFrozen = newFrozen.subtract(request.getAmount());
                    newAvailable = newAvailable.add(request.getAmount());
                }
                default -> throw new BusinessException(ErrorCode.PARAM_INVALID, "不支持的资金变更类型: " + request.getTxnType());
            }

            dao.fundAccountDao().updateBalances(connection, request.getFundAccNo(), newAvailable, newFrozen);
            long logId = dao.fundTransactionLogDao().create(connection, new DomainModels.FundTransactionLog(
                    null,
                    request.getFundAccNo(),
                    daoType,
                    request.getAmount(),
                    newAvailable,
                    newFrozen,
                    request.getRefOrderId(),
                    null,
                    LocalDateTime.now()
            ));

            return FundTradeUpdateResponse.builder()
                    .availableBalance(newAvailable)
                    .frozenBalance(newFrozen)
                    .logId(logId)
                    .duplicate(false)
                    .build();
        });
    }

    @Override
    public AccountBindingResponse bindSecurityAccount(String fundAccNo, String secAccNo, Integer staffId) {
        return dao.transactionManager().execute(connection -> {
            var secAccount = dao.securityAccountDao().findByAccountNoForUpdate(connection, secAccNo)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ERR_005, "证券账户不存在: " + secAccNo));
            var fundAccount = dao.fundAccountDao().findByAccountNoForUpdate(connection, fundAccNo)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ERR_010, "资金账户不存在: " + fundAccNo));

            if (secAccount.linkedFundAcc() != null && !secAccount.linkedFundAcc().isBlank()) {
                if (secAccount.linkedFundAcc().equals(fundAccNo)) {
                    throw new BusinessException(ErrorCode.ERR_011, "证券账户已绑定该资金账户");
                }
                throw new BusinessException(ErrorCode.ERR_014, "证券账户已绑定其他资金账户: " + secAccount.linkedFundAcc());
            }
            if (fundAccount.secAccNo() != null && !fundAccount.secAccNo().isBlank() && !fundAccount.secAccNo().equals(secAccNo)) {
                throw new BusinessException(ErrorCode.ERR_014, "该资金账户已绑定其他证券账户: " + fundAccount.secAccNo());
            }

            checkAccountNotFrozenOrClosed(fundAccount.status(), fundAccNo);
            if (secAccount.status() == DomainEnums.AccountStatus.CLOSED) {
                throw new BusinessException(ErrorCode.ERR_005, "证券账户已销户");
            }
            if (secAccount.status() == DomainEnums.AccountStatus.LOSS_FROZEN
                    || secAccount.status() == DomainEnums.AccountStatus.VIOLATION_FROZEN) {
                throw new BusinessException(ErrorCode.ERR_003, "证券账户已冻结，无法绑定");
            }

            dao.securityAccountDao().bindFundAccount(connection, secAccNo, fundAccNo);
            dao.fundAccountDao().relinkSecurityAccount(connection, fundAccNo, secAccNo);
            normalizeSecurityAfterRebind(connection, secAccNo);

            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    staffId,
                    "绑定证券账户",
                    "FUND",
                    fundAccNo,
                    "绑定证券账户 " + secAccNo,
                    LocalDateTime.now()
            ));

            return AccountBindingResponse.builder()
                    .fundAccNo(fundAccNo)
                    .secAccNo(secAccNo)
                    .build();
        });
    }

    @Override
    public AccountBindingResponse unbindSecurityAccount(String fundAccNo, String secAccNo, Integer staffId) {
        return dao.transactionManager().execute(connection -> {
            var fundAccount = dao.fundAccountDao().findByAccountNoForUpdate(connection, fundAccNo)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ERR_010, "资金账户不存在: " + fundAccNo));

            if (fundAccount.secAccNo() == null || fundAccount.secAccNo().isBlank()) {
                throw new BusinessException(ErrorCode.ERR_015, "该资金账户未绑定任何证券账户");
            }
            if (!fundAccount.secAccNo().equals(secAccNo)) {
                throw new BusinessException(ErrorCode.ERR_015, "资金账户绑定的证券账户不匹配");
            }
            if (fundAccount.availableBalance().compareTo(BigDecimal.ZERO) > 0
                    || fundAccount.frozenBalance().compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException(ErrorCode.ERR_007, "资金账户尚有余额或冻结资金，无法解绑");
            }
            if (fundAccount.status() == DomainEnums.AccountStatus.LOSS_FROZEN
                    || fundAccount.status() == DomainEnums.AccountStatus.VIOLATION_FROZEN) {
                throw new BusinessException(ErrorCode.ERR_017, "资金账户处于冻结状态，无法解绑");
            }

            dao.securityAccountDao().unbindFundAccount(connection, secAccNo);
            dao.fundAccountDao().relinkSecurityAccount(connection, fundAccNo, null);
            freezeSecurityForNoFund(connection, secAccNo);

            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    staffId,
                    "解绑证券账户",
                    "FUND",
                    fundAccNo,
                    "解绑证券账户 " + secAccNo,
                    LocalDateTime.now()
            ));

            return AccountBindingResponse.builder()
                    .fundAccNo(fundAccNo)
                    .secAccNo(secAccNo)
                    .build();
        });
    }

    private void changePasswordInternal(
            String fundAccNo,
            String passwordType,
            String oldPassword,
            String newPassword,
            Integer staffId,
            boolean logOperation
    ) {
        dao.transactionManager().execute(connection -> {
            var account = dao.fundAccountDao().findByAccountNoForUpdate(connection, fundAccNo)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ERR_010, "资金账户不存在: " + fundAccNo));

            checkAccountNotFrozenOrClosed(account.status(), fundAccNo);

            String storedPassword = "trade".equals(passwordType) ? account.tradePassword() : account.withdrawPassword();
            String passwordLabel = "trade".equals(passwordType) ? "交易密码" : "取款密码";
            if (!PasswordUtil.verify(oldPassword, storedPassword)) {
                throw new BusinessException(ErrorCode.ERR_004, passwordLabel + "错误");
            }

            String newHash = PasswordUtil.hash(newPassword);
            if ("trade".equals(passwordType)) {
                dao.fundAccountDao().updateTradePassword(connection, fundAccNo, newHash);
            } else {
                dao.fundAccountDao().updateWithdrawPassword(connection, fundAccNo, newHash);
            }

            if (logOperation) {
                dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                        null,
                        staffId,
                        "修改密码",
                        "FUND",
                        fundAccNo,
                        "修改" + passwordLabel,
                        LocalDateTime.now()
                ));
            }
            return null;
        });
    }

    private void checkAccountNotFrozenOrClosed(DomainEnums.AccountStatus status, String fundAccNo) {
        if (status == DomainEnums.AccountStatus.LOSS_FROZEN
                || status == DomainEnums.AccountStatus.VIOLATION_FROZEN) {
            throw new BusinessException(ErrorCode.ERR_003, "账户已冻结: " + fundAccNo);
        }
        if (status == DomainEnums.AccountStatus.CLOSED) {
            throw new BusinessException(ErrorCode.ERR_021, "资金账户已销户: " + fundAccNo);
        }
        if (status == DomainEnums.AccountStatus.PRE_CLOSE) {
            throw new BusinessException(ErrorCode.ERR_021, "资金账户已预销户: " + fundAccNo);
        }
    }

    private void verifyOwnership(String secAccNo, String idNumber) {
        if (secAccNo == null || secAccNo.isBlank()) {
            throw new BusinessException(ErrorCode.ERR_015, "资金账户未绑定任何证券账户");
        }
        var secAccount = dao.securityAccountDao().findByAccountNo(secAccNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_005, "关联证券账户不存在: " + secAccNo));
        var investor = dao.investorDao().findById(secAccount.investorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_013, "投资者不存在"));
        String expectedCredential = investor.type() == DomainEnums.InvestorType.LEGAL_ENTITY
                ? investor.legalNumber()
                : investor.idNumber();
        if (expectedCredential == null || !expectedCredential.equals(idNumber)) {
            throw new BusinessException(ErrorCode.ERR_013, "身份证或法人注册登记号与证券账户持有人不一致");
        }
    }

    private void verifySecurityAccountNumber(String actualSecAccNo, String suppliedSecAccNo) {
        if (actualSecAccNo == null || !actualSecAccNo.equals(suppliedSecAccNo)) {
            throw new BusinessException(ErrorCode.ERR_013, "证券账户号与资金账户的绑定关系不一致");
        }
    }

    private DomainEnums.FundTransactionType mapTxnType(String txnTypeCn) {
        return switch (txnTypeCn) {
            case "买入冻结" -> DomainEnums.FundTransactionType.BUY_FREEZE;
            case "买入扣款" -> DomainEnums.FundTransactionType.BUY_DEBIT;
            case "卖出回款" -> DomainEnums.FundTransactionType.SELL_RETURN;
            case "撤单解冻" -> DomainEnums.FundTransactionType.CANCEL_RELEASE;
            default -> throw new BusinessException(ErrorCode.PARAM_INVALID, "不支持的资金变更类型: " + txnTypeCn);
        };
    }

    private boolean isBlacklistedBySecurityAccountNo(String secAccNo) {
        try {
            return dao.blacklistSupport(blacklistClient).isBlockedBySecurityAccountNo(secAccNo);
        } catch (BlacklistClientException e) {
            log.warn("Blacklist check failed for sec_acc_no '{}': {}", secAccNo, e.getMessage());
            return false;
        }
    }

    private void freezeLinkedSecurityForFundLoss(java.sql.Connection connection, String secAccNo) {
        if (secAccNo == null || secAccNo.isBlank()) {
            return;
        }
        var securityAccount = dao.securityAccountDao().findByAccountNoForUpdate(connection, secAccNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_005, "关联证券账户不存在: " + secAccNo));
        freezeAllSecurityHoldings(connection, secAccNo);
        if (securityAccount.status() == DomainEnums.AccountStatus.NORMAL
                || securityAccount.status() == DomainEnums.AccountStatus.NO_FUND_FROZEN) {
            dao.securityAccountDao().updateStatus(connection, secAccNo, DomainEnums.AccountStatus.LOSS_FROZEN);
        }
    }

    private void freezeSecurityForNoFund(java.sql.Connection connection, String secAccNo) {
        var securityAccount = dao.securityAccountDao().findByAccountNoForUpdate(connection, secAccNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_005, "证券账户不存在: " + secAccNo));
        if (securityAccount.status() == DomainEnums.AccountStatus.NORMAL
                || securityAccount.status() == DomainEnums.AccountStatus.PRE_CLOSE) {
            dao.securityAccountDao().updateStatus(connection, secAccNo, DomainEnums.AccountStatus.NO_FUND_FROZEN);
        }
    }

    private void normalizeSecurityAfterRebind(java.sql.Connection connection, String secAccNo) {
        var securityAccount = dao.securityAccountDao().findByAccountNoForUpdate(connection, secAccNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_005, "证券账户不存在: " + secAccNo));
        unfreezeAllSecurityHoldings(connection, secAccNo);
        if (securityAccount.status() == DomainEnums.AccountStatus.NO_FUND_FROZEN) {
            dao.securityAccountDao().updateStatus(connection, secAccNo, DomainEnums.AccountStatus.NORMAL);
        }
        if (securityAccount.status() == DomainEnums.AccountStatus.LOSS_FROZEN) {
            dao.securityAccountDao().updateStatus(connection, secAccNo, DomainEnums.AccountStatus.NORMAL);
        }
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

    private FundLogView toUnifiedFundLogView(DomainModels.FundTransactionLog log) {
        FundLogView.FundLogViewBuilder builder = FundLogView.builder()
                .logId(log.logId())
                .txnType(log.txnType().dbValue())
                .amount(log.amount())
                .txnTime(log.txnTime())
                .refOrderId(log.refOrderId());

        Optional<DomainModels.HoldingChangeLog> holdingChange = dao.holdingChangeLogDao()
                .listByRefOrderId(log.refOrderId()).stream()
                .max(Comparator.comparing(DomainModels.HoldingChangeLog::txnTime)
                        .thenComparing(DomainModels.HoldingChangeLog::logId));

        holdingChange.ifPresent(change -> builder
                .stockCode(change.stockCode())
                .stockName(change.stockName())
                .holdingChangeType(change.changeType())
                .shareQuantity(change.quantity())
                .price(change.price())
                .holdingQuantityAfter(change.quantityAfter())
                .holdingFrozenQuantityAfter(change.frozenQuantityAfter()));

        return builder.build();
    }

    @Override
    public List<FundAccountListItemResponse> listAllFundAccounts() {
        var accounts = dao.fundAccountDao().listAll();
        return accounts.stream().map(account -> {
            String name = "";
            String idNumber = "";
            if (account.secAccNo() != null && !account.secAccNo().isBlank()) {
                var secAccount = dao.securityAccountDao().findByAccountNo(account.secAccNo()).orElse(null);
                if (secAccount != null) {
                    var investor = dao.investorDao().findById(secAccount.investorId()).orElse(null);
                    if (investor != null) {
                        name = investor.name();
                        idNumber = investor.idNumber();
                    }
                }
            }
            String statusCode = switch (account.status()) {
                case NORMAL -> "normal";
                case LOSS_FROZEN -> "frozen";
                case CLOSED -> "closed";
                case VIOLATION_FROZEN, NO_FUND_FROZEN, PRE_CLOSE -> "frozen";
                default -> "normal";
            };
            return FundAccountListItemResponse.builder()
                    .fundAccNo(account.fundAccNo())
                    .secAccNo(account.secAccNo())
                    .name(name)
                    .idNumber(idNumber)
                    .availableBalance(account.availableBalance())
                    .frozenBalance(account.frozenBalance())
                    .currency(account.currency())
                    .status(statusCode)
                    .openDate(account.openDate().toString())
                    .build();
        }).toList();
    }

    private DomainModels.LoginCertificateState ensureCertificateState(String subjectType, String subjectKey) {
        return dao.transactionManager().execute(connection -> {
            dao.loginCertificateStateDao().ensureExists(connection, subjectType, subjectKey);
            return dao.loginCertificateStateDao().findBySubjectForUpdate(connection, subjectType, subjectKey)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "certificate state not found"));
        });
    }
}
