package account.service;

import account.common.BusinessException;
import account.common.ErrorCode;
import account.dao.DaoRegistry;
import account.dao.model.DomainEnums;
import account.dao.model.DomainModels;
import account.dto.AccountStatusResponse;
import account.dto.CloseSecurityAccountRequest;
import account.dto.CreateSecurityAccountRequest;
import account.dto.HoldingView;
import account.dto.InvestorInfoResponse;
import account.dto.ReissueSecurityAccountRequest;
import account.dto.ReportSecurityLossRequest;
import account.dto.SecurityAccountCreatedResponse;
import account.dto.SecurityAccountListItemResponse;
import account.dto.SecurityHoldingUpdateResponse;
import account.dto.SecurityReissueResponse;
import account.dto.SecuritySnapshotResponse;
import account.dto.UpdateInvestorInfoRequest;
import account.dto.UpdateSecurityHoldingRequest;
import account.enums.AccountStatus;
import account.integration.BlacklistClient;
import account.integration.BlacklistClientException;
import account.service.api.ClientAuthTokenService;
import account.service.api.SecurityAccountService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SecurityAccountServiceImpl implements SecurityAccountService {

    private static final DateTimeFormatter CN_ID_BIRTHDAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final DaoRegistry dao;
    private final BlacklistClient blacklistClient;
    private final ClientAuthTokenService clientAuthTokenService;

    public SecurityAccountServiceImpl(
            DaoRegistry dao,
            BlacklistClient blacklistClient,
            ClientAuthTokenService clientAuthTokenService) {
        this.dao = dao;
        this.blacklistClient = blacklistClient;
        this.clientAuthTokenService = clientAuthTokenService;
    }

    @Override
    public SecurityAccountCreatedResponse createSecurityAccount(CreateSecurityAccountRequest request) {
        validateInvestorEligibility(request);

        if (isBlacklistedByUserName(request.getName())) {
            throw new BusinessException(ErrorCode.ERR_012, "投资者在黑名单中，无法开立证券账户");
        }

        return dao.transactionManager().execute(connection -> {
            int investorId;
            var existingInvestor = dao.investorDao().findByIdNumber(request.getIdNumber());
            if (existingInvestor.isPresent()) {
                investorId = existingInvestor.get().investorId();
                var latestAccount = dao.securityAccountDao().findLatestNonClosedByInvestorId(investorId);
                if (latestAccount.isPresent()) {
                    throw new BusinessException(
                            ErrorCode.ERR_006,
                            "该投资者已有证券账户: " + latestAccount.get().secAccNo());
                }
            } else {
                var investor = new DomainModels.Investor(
                        null,
                        EnumMapper.toDaoInvestorType(request.getInvestorType()),
                        request.getName(),
                        request.getGender(),
                        request.getIdType(),
                        request.getIdNumber(),
                        request.getPhone(),
                        request.getAddress(),
                        request.getWorkUnit(),
                        request.getOccupation(),
                        request.getEducation(),
                        request.getLegalNumber(),
                        request.getBusinessLicense(),
                        request.getExecutorName(),
                        request.getExecutorIdNumber(),
                        request.getExecutorPhone(),
                        request.getExecutorAddress(),
                        request.getAgentName(),
                        request.getAgentIdNumber(),
                        LocalDateTime.now()
                );
                investorId = dao.investorDao().create(connection, investor);
            }

            String secAccNo = AccountNumberGenerator.generateSecurityAccountNo();
            dao.securityAccountDao().create(connection, new DomainModels.SecurityAccount(
                    secAccNo,
                    investorId,
                    DomainEnums.AccountStatus.NORMAL,
                    LocalDate.now(),
                    null
            ));

            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    request.getStaffId(),
                    "证券开户",
                    "SECURITY",
                    secAccNo,
                    "证券开户，姓名=" + request.getName(),
                    LocalDateTime.now()
            ));

            return SecurityAccountCreatedResponse.builder()
                    .secAccNo(secAccNo)
                    .status(AccountStatus.NORMAL.code())
                    .investorId(investorId)
                    .build();
        });
    }

    @Override
    public AccountStatusResponse reportSecurityLoss(ReportSecurityLossRequest request) {
        return dao.transactionManager().execute(connection -> {
            var account = dao.securityAccountDao().findByAccountNoForUpdate(connection, request.getSecAccNo())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ERR_005, "证券账户不存在: " + request.getSecAccNo()));

            verifyInvestorOwnership(account.investorId(), request.getIdNumber());

            if (account.status() == DomainEnums.AccountStatus.LOSS_FROZEN) {
                throw new BusinessException(ErrorCode.ERR_021, "证券账户已处于挂失冻结状态");
            }
            if (account.status() == DomainEnums.AccountStatus.CLOSED) {
                throw new BusinessException(ErrorCode.ERR_021, "证券账户已销户，无法挂失");
            }

            freezeAllHoldings(connection, request.getSecAccNo());
            dao.securityAccountDao().updateStatus(connection, request.getSecAccNo(), DomainEnums.AccountStatus.LOSS_FROZEN);
            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    request.getStaffId(),
                    "挂失",
                    "SECURITY",
                    request.getSecAccNo(),
                    "证券账户挂失，原因=" + Optional.ofNullable(request.getReason()).orElse(""),
                    LocalDateTime.now()
            ));

            return AccountStatusResponse.builder()
                    .status(AccountStatus.FROZEN_LOSS.code())
                    .build();
        });
    }

    @Override
    public SecurityReissueResponse reissueSecurityAccount(ReissueSecurityAccountRequest request) {
        validateInvestorEligibility(request);
        if (isBlacklistedByUserName(request.getName())) {
            throw new BusinessException(ErrorCode.ERR_012, "投资者在黑名单中，无法补办证券账户");
        }

        return dao.transactionManager().execute(connection -> {
            var oldAccount = dao.securityAccountDao().findByAccountNoForUpdate(connection, request.getOldSecAccNo())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ERR_005, "证券账户不存在: " + request.getOldSecAccNo()));

            verifyReissueInvestorDetails(oldAccount.investorId(), request);

            if (oldAccount.status() != DomainEnums.AccountStatus.LOSS_FROZEN) {
                throw new BusinessException(ErrorCode.ERR_021, "证券账户当前状态不允许补办");
            }

            String newSecAccNo = AccountNumberGenerator.generateSecurityAccountNo();
            if (oldAccount.linkedFundAcc() != null && !oldAccount.linkedFundAcc().isBlank()) {
                dao.securityAccountDao().unbindFundAccount(connection, request.getOldSecAccNo());
            }
            dao.securityAccountDao().create(connection, new DomainModels.SecurityAccount(
                    newSecAccNo,
                    oldAccount.investorId(),
                    DomainEnums.AccountStatus.NORMAL,
                    LocalDate.now(),
                    oldAccount.linkedFundAcc()
            ));

            for (var holding : dao.holdingDao().listBySecurityAccountNo(request.getOldSecAccNo())) {
                dao.holdingDao().saveOrUpdate(connection, new DomainModels.Holding(
                        null,
                        newSecAccNo,
                        holding.stockCode(),
                        holding.stockName(),
                        holding.quantity() + holding.frozenQuantity(),
                        0,
                        holding.avgCost(),
                        LocalDateTime.now()
                ));
            }

            dao.holdingDao().deleteBySecurityAccountNo(connection, request.getOldSecAccNo());

            if (oldAccount.linkedFundAcc() != null && !oldAccount.linkedFundAcc().isBlank()) {
                dao.fundAccountDao().relinkSecurityAccount(connection, oldAccount.linkedFundAcc(), newSecAccNo);
            }

            dao.securityAccountDao().updateStatus(connection, request.getOldSecAccNo(), DomainEnums.AccountStatus.CLOSED);
            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    request.getStaffId(),
                    "补办",
                    "SECURITY",
                    newSecAccNo,
                    "补办证券账户，旧账户=" + request.getOldSecAccNo(),
                    LocalDateTime.now()
            ));

            return SecurityReissueResponse.builder()
                    .newSecAccNo(newSecAccNo)
                    .oldSecAccNo(request.getOldSecAccNo())
                    .build();
        });
    }

    @Override
    public AccountStatusResponse closeSecurityAccount(CloseSecurityAccountRequest request) {
        return dao.transactionManager().execute(connection -> {
            var account = dao.securityAccountDao().findByAccountNoForUpdate(connection, request.getSecAccNo())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ERR_005, "证券账户不存在: " + request.getSecAccNo()));

            verifyInvestorOwnership(account.investorId(), request.getIdNumber());

            if (account.status() == DomainEnums.AccountStatus.CLOSED) {
                throw new BusinessException(ErrorCode.ERR_021, "证券账户已销户");
            }
            if (isFrozenSecurityStatus(account.status())) {
                throw new BusinessException(ErrorCode.ERR_003, "账户处于冻结状态，无法销户");
            }

            int totalQuantity = dao.holdingDao().sumQuantityBySecurityAccountNo(request.getSecAccNo());
            if (totalQuantity > 0) {
                throw new BusinessException(ErrorCode.ERR_022, "证券账户仍有持仓，必须先全部卖出后才能销户");
            }

            if (account.linkedFundAcc() != null && !account.linkedFundAcc().isBlank()) {
                dao.securityAccountDao().unbindFundAccount(connection, request.getSecAccNo());
                dao.fundAccountDao().relinkSecurityAccount(connection, account.linkedFundAcc(), null);
            }

            dao.securityAccountDao().updateStatus(connection, request.getSecAccNo(), DomainEnums.AccountStatus.CLOSED);
            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    request.getStaffId(),
                    "销户",
                    "SECURITY",
                    request.getSecAccNo(),
                    "证券账户销户，原因=" + Optional.ofNullable(request.getReason()).orElse(""),
                    LocalDateTime.now()
            ));

            return AccountStatusResponse.builder()
                    .status(AccountStatus.CLOSED.code())
                    .build();
        });
    }

    @Override
    public InvestorInfoResponse updateInvestorInfo(UpdateInvestorInfoRequest request) {
        return dao.transactionManager().execute(connection -> {
            var currentInvestor = dao.investorDao().findById(request.getInvestorId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ERR_010, "投资者不存在: " + request.getInvestorId()));

            String name = mergeRequiredValue(request.getName(), currentInvestor.name(), "name");
            String idType = mergeRequiredValue(request.getIdType(), currentInvestor.idType(), "id_type");
            String idNumber = mergeRequiredValue(request.getIdNumber(), currentInvestor.idNumber(), "id_number");

            if (!currentInvestor.idNumber().equals(idNumber)) {
                var existing = dao.investorDao().findByIdNumber(idNumber);
                if (existing.isPresent() && !existing.get().investorId().equals(currentInvestor.investorId())) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "证件号码已被其他投资者使用");
                }
            }

            if (currentInvestor.type() == DomainEnums.InvestorType.PERSONAL && !isAdultByIdCard(idType, idNumber)) {
                throw new BusinessException(ErrorCode.ERR_019, "个人投资者必须年满18周岁");
            }

            var updatedInvestor = new DomainModels.Investor(
                    currentInvestor.investorId(),
                    currentInvestor.type(),
                    name,
                    mergeOptionalValue(request.getGender(), currentInvestor.gender()),
                    idType,
                    idNumber,
                    mergeOptionalValue(request.getPhone(), currentInvestor.phone()),
                    mergeOptionalValue(request.getAddress(), currentInvestor.address()),
                    mergeOptionalValue(request.getWorkUnit(), currentInvestor.workUnit()),
                    mergeOptionalValue(request.getOccupation(), currentInvestor.occupation()),
                    mergeOptionalValue(request.getEducation(), currentInvestor.education()),
                    mergeOptionalValue(request.getLegalNumber(), currentInvestor.legalNumber()),
                    mergeOptionalValue(request.getBusinessLicense(), currentInvestor.businessLicense()),
                    mergeOptionalValue(request.getExecutorName(), currentInvestor.executorName()),
                    mergeOptionalValue(request.getExecutorIdNumber(), currentInvestor.executorIdNumber()),
                    mergeOptionalValue(request.getExecutorPhone(), currentInvestor.executorPhone()),
                    mergeOptionalValue(request.getExecutorAddress(), currentInvestor.executorAddress()),
                    mergeOptionalValue(request.getAgentName(), currentInvestor.agentName()),
                    mergeOptionalValue(request.getAgentIdNumber(), currentInvestor.agentIdNumber()),
                    currentInvestor.createdAt()
            );

            dao.investorDao().update(connection, updatedInvestor);
            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    request.getStaffId(),
                    "更新投资者信息",
                    "INVESTOR",
                    String.valueOf(updatedInvestor.investorId()),
                    "更新投资者信息，name=" + updatedInvestor.name() + ", id_number=" + updatedInvestor.idNumber(),
                    LocalDateTime.now()
            ));

            return InvestorInfoResponse.builder()
                    .investorId(updatedInvestor.investorId())
                    .name(updatedInvestor.name())
                    .gender(updatedInvestor.gender())
                    .idType(updatedInvestor.idType())
                    .idNumber(updatedInvestor.idNumber())
                    .phone(updatedInvestor.phone())
                    .address(updatedInvestor.address())
                    .workUnit(updatedInvestor.workUnit())
                    .occupation(updatedInvestor.occupation())
                    .education(updatedInvestor.education())
                    .legalNumber(updatedInvestor.legalNumber())
                    .businessLicense(updatedInvestor.businessLicense())
                    .executorName(updatedInvestor.executorName())
                    .executorIdNumber(updatedInvestor.executorIdNumber())
                    .executorPhone(updatedInvestor.executorPhone())
                    .executorAddress(updatedInvestor.executorAddress())
                    .agentName(updatedInvestor.agentName())
                    .agentIdNumber(updatedInvestor.agentIdNumber())
                    .build();
        });
    }

    private void freezeAllHoldings(java.sql.Connection connection, String secAccNo) {
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

    @Override
    public SecuritySnapshotResponse getSecuritySnapshot(String secAccNo, String stockCode, String authToken) {
        clientAuthTokenService.requireSecurityAccess(authToken, secAccNo);
        var account = dao.securityAccountDao().findByAccountNo(secAccNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_005, "证券账户不存在"));

        if (isFrozenSecurityStatus(account.status())) {
            throw new BusinessException(ErrorCode.ERR_003, "账户已冻结");
        }
        if (account.status() == DomainEnums.AccountStatus.CLOSED) {
            throw new BusinessException(ErrorCode.ERR_021, "证券账户已销户，无法查询");
        }

        if (stockCode != null && !stockCode.isBlank()) {
            var holding = dao.holdingDao().findByAccountAndStock(secAccNo, stockCode);
            if (holding.isPresent()) {
                var item = holding.get();
                return SecuritySnapshotResponse.builder()
                        .secAccNo(secAccNo)
                        .stockCode(item.stockCode())
                        .stockName(item.stockName())
                        .quantity(item.quantity())
                        .frozenQuantity(item.frozenQuantity())
                        .availableQuantity(item.availableQuantity())
                        .avgCost(item.avgCost())
                        .build();
            }
            return SecuritySnapshotResponse.builder()
                    .secAccNo(secAccNo)
                    .stockCode(stockCode)
                    .quantity(0)
                    .frozenQuantity(0)
                    .availableQuantity(0)
                    .avgCost(BigDecimal.ZERO)
                    .build();
        }

        List<HoldingView> holdingViews = dao.holdingDao().listBySecurityAccountNo(secAccNo).stream()
                .map(item -> HoldingView.builder()
                        .stockCode(item.stockCode())
                        .stockName(item.stockName())
                        .quantity(item.quantity())
                        .frozenQuantity(item.frozenQuantity())
                        .availableQuantity(item.availableQuantity())
                        .avgCost(item.avgCost())
                        .build())
                .toList();

        return SecuritySnapshotResponse.builder()
                .secAccNo(secAccNo)
                .holdings(holdingViews)
                .build();
    }

    @Override
    public SecurityHoldingUpdateResponse updateSecurityHolding(UpdateSecurityHoldingRequest request) {
        return dao.transactionManager().execute(connection -> {
            var account = dao.securityAccountDao().findByAccountNoForUpdate(connection, request.getSecAccNo())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ERR_005, "证券账户不存在: " + request.getSecAccNo()));

            var currentHolding = dao.holdingDao().findByAccountAndStockForUpdate(
                    connection, request.getSecAccNo(), request.getStockCode());
            int currentQty = currentHolding.map(DomainModels.Holding::quantity).orElse(0);
            int currentFrozen = currentHolding.map(DomainModels.Holding::frozenQuantity).orElse(0);
            BigDecimal currentAvgCost = currentHolding.map(DomainModels.Holding::avgCost).orElse(BigDecimal.ZERO);

            if (dao.holdingChangeLogDao().existsByRefOrderIdAndChangeTypeAndAccountAndStock(
                    connection,
                    request.getRefOrderId(),
                    request.getChangeType(),
                    request.getSecAccNo(),
                    request.getStockCode())) {
                return SecurityHoldingUpdateResponse.builder()
                        .duplicate(true)
                        .quantity(Math.max(0, currentQty))
                        .frozenQuantity(Math.max(0, currentFrozen))
                        .availableQuantity(Math.max(0, currentQty - currentFrozen))
                        .avgCost(currentAvgCost)
                        .build();
            }

            if (isFrozenSecurityStatus(account.status())) {
                throw new BusinessException(ErrorCode.ERR_003, "账户已冻结");
            }
            if (account.status() == DomainEnums.AccountStatus.CLOSED) {
                throw new BusinessException(ErrorCode.ERR_021, "证券账户已销户，无法变更持仓");
            }
            if (account.status() == DomainEnums.AccountStatus.PRE_CLOSE) {
                throw new BusinessException(ErrorCode.ERR_021, "证券账户已预销户，无法变更持仓");
            }

            int newQty = currentQty;
            int newFrozen = currentFrozen;
            BigDecimal newAvgCost = currentAvgCost;

            switch (request.getChangeType()) {
                case "买入增加" -> {
                    if (request.getPrice() != null && request.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal totalCost = currentAvgCost.multiply(BigDecimal.valueOf(currentQty))
                                .add(request.getPrice().multiply(BigDecimal.valueOf(request.getQuantity())));
                        newQty = currentQty + request.getQuantity();
                        if (newQty > 0) {
                            newAvgCost = totalCost.divide(BigDecimal.valueOf(newQty), 4, RoundingMode.HALF_UP);
                        }
                    } else {
                        newQty = currentQty + request.getQuantity();
                    }
                }
                case "卖出冻结" -> {
                    int available = currentQty - currentFrozen;
                    if (available < request.getQuantity()) {
                        throw new BusinessException(ErrorCode.ERR_002, "可卖数量不足");
                    }
                    newFrozen = currentFrozen + request.getQuantity();
                }
                case "卖出扣减" -> {
                    if (currentFrozen < request.getQuantity()) {
                        throw new BusinessException(ErrorCode.ERR_002, "冻结持仓不足");
                    }
                    newQty = currentQty - request.getQuantity();
                    newFrozen = currentFrozen - request.getQuantity();
                }
                case "撤单释放" -> {
                    if (currentFrozen < request.getQuantity()) {
                        throw new BusinessException(ErrorCode.ERR_002, "冻结持仓不足");
                    }
                    newFrozen = currentFrozen - request.getQuantity();
                }
                default -> throw new BusinessException(ErrorCode.PARAM_INVALID, "不支持的持仓变更类型: " + request.getChangeType());
            }

            String stockName = request.getStockName();
            if ((stockName == null || stockName.isBlank()) && currentHolding.isPresent()) {
                stockName = currentHolding.get().stockName();
            }

            dao.holdingDao().saveOrUpdate(connection, new DomainModels.Holding(
                    currentHolding.map(DomainModels.Holding::holdingId).orElse(null),
                    request.getSecAccNo(),
                    request.getStockCode(),
                    stockName,
                    newQty,
                    newFrozen,
                    newAvgCost,
                    LocalDateTime.now()
            ));

            long logId = dao.holdingChangeLogDao().create(connection, new DomainModels.HoldingChangeLog(
                    null,
                    request.getSecAccNo(),
                    request.getStockCode(),
                    stockName,
                    request.getRefOrderId(),
                    request.getChangeType(),
                    request.getQuantity(),
                    request.getPrice(),
                    newQty,
                    newFrozen,
                    newAvgCost,
                    LocalDateTime.now()
            ));

            return SecurityHoldingUpdateResponse.builder()
                    .logId(logId)
                    .duplicate(false)
                    .quantity(Math.max(0, newQty))
                    .frozenQuantity(Math.max(0, newFrozen))
                    .availableQuantity(Math.max(0, newQty - newFrozen))
                    .avgCost(newAvgCost)
                    .build();
        });
    }

    private void validateInvestorEligibility(CreateSecurityAccountRequest request) {
        if ("个人".equals(request.getInvestorType())) {
            if (!isAdultByIdCard(request.getIdType(), request.getIdNumber())) {
                throw new BusinessException(ErrorCode.ERR_019, "未成年人不得开立证券账户");
            }
            if (!isBlank(request.getAgentName()) && isBlank(request.getAgentIdNumber())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "代办开户时必须提供代办人证件号码");
            }
            if (isBlank(request.getAgentName()) && !isBlank(request.getAgentIdNumber())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "代办开户时必须提供代办人姓名");
            }
            return;
        }

        if ("法人".equals(request.getInvestorType())) {
            requireField(request.getLegalNumber(), "legal_number");
            requireField(request.getBusinessLicense(), "business_license");
            requireField(request.getExecutorName(), "executor_name");
            requireField(request.getExecutorIdNumber(), "executor_id_number");
            requireField(request.getExecutorPhone(), "executor_phone");
            requireField(request.getExecutorAddress(), "executor_address");
            return;
        }

        throw new BusinessException(ErrorCode.ERR_019, "投资者类型仅支持个人或法人");
    }

    private boolean isBlacklistedByUserName(String userName) {
        try {
            return dao.blacklistSupport(blacklistClient).isBlockedByUserName(userName);
        } catch (BlacklistClientException e) {
            log.warn("Blacklist check failed for user '{}': {}", userName, e.getMessage());
            return false;
        }
    }

    private boolean isAdultByIdCard(String idType, String idNumber) {
        if (!isIdentityCardType(idType) || idNumber == null || idNumber.length() != 18) {
            throw new BusinessException(ErrorCode.ERR_020, "当前仅支持使用18位身份证进行个人开户校验");
        }
        try {
            LocalDate birthday = LocalDate.parse(idNumber.substring(6, 14), CN_ID_BIRTHDAY);
            return Period.between(birthday, LocalDate.now()).getYears() >= 18;
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.ERR_020, "身份证号码格式不合法，无法完成成年校验");
        }
    }

    private boolean isIdentityCardType(String idType) {
        return "身份证".equals(idType) || "居民身份证".equals(idType) || "ID".equalsIgnoreCase(idType);
    }

    private boolean isFrozenSecurityStatus(DomainEnums.AccountStatus status) {
        return status == DomainEnums.AccountStatus.LOSS_FROZEN
                || status == DomainEnums.AccountStatus.VIOLATION_FROZEN
                || status == DomainEnums.AccountStatus.NO_FUND_FROZEN;
    }

    private void verifyInvestorOwnership(int investorId, String idNumber) {
        var investor = dao.investorDao().findById(investorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_013, "投资者不存在"));
        if (!investor.idNumber().equals(idNumber)) {
            throw new BusinessException(ErrorCode.ERR_013, "身份证号与账户持有人不一致");
        }
    }

    private void verifyReissueInvestorDetails(int investorId, ReissueSecurityAccountRequest request) {
        var investor = dao.investorDao().findById(investorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_013, "投资者不存在"));
        DomainEnums.InvestorType requestedType = EnumMapper.toDaoInvestorType(request.getInvestorType());
        if (investor.type() != requestedType
                || !investor.name().equals(request.getName())
                || !investor.idType().equals(request.getIdType())
                || !investor.idNumber().equals(request.getIdNumber())) {
            throw new BusinessException(ErrorCode.ERR_013, "补办开户资料与原证券账户持有人不一致");
        }
        if (requestedType == DomainEnums.InvestorType.LEGAL_ENTITY
                && (!java.util.Objects.equals(investor.legalNumber(), request.getLegalNumber())
                || !java.util.Objects.equals(investor.businessLicense(), request.getBusinessLicense()))) {
            throw new BusinessException(ErrorCode.ERR_013, "法人注册登记号或营业执照号与原账户不一致");
        }
    }

    private String mergeRequiredValue(String requestValue, String currentValue, String fieldName) {
        if (requestValue == null) {
            return currentValue;
        }
        if (requestValue.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, fieldName + " cannot be blank");
        }
        return requestValue;
    }

    private String mergeOptionalValue(String requestValue, String currentValue) {
        if (requestValue == null) {
            return currentValue;
        }
        return requestValue.isBlank() ? null : requestValue;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void requireField(String value, String fieldName) {
        if (isBlank(value)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, fieldName + " cannot be blank");
        }
    }

    @Override
    public List<SecurityAccountListItemResponse> listAllSecurityAccounts() {
        var accounts = dao.securityAccountDao().listAll();
        return accounts.stream().map(account -> {
            var investor = dao.investorDao().findById(account.investorId()).orElse(null);
            String investorType = investor != null 
                ? (investor.type() == DomainEnums.InvestorType.PERSONAL ? "个人" : "法人")
                : "未知";
            String statusCode = switch (account.status()) {
                case NORMAL -> "normal";
                case LOSS_FROZEN -> "frozen";
                case CLOSED -> "closed";
                case VIOLATION_FROZEN, NO_FUND_FROZEN, PRE_CLOSE -> "frozen";
                default -> "normal";
            };
            return SecurityAccountListItemResponse.builder()
                    .secAccNo(account.secAccNo())
                    .investorId(account.investorId())
                    .name(investor != null ? investor.name() : "未知")
                    .idNumber(investor != null ? investor.idNumber() : "")
                    .investorType(investorType)
                    .status(statusCode)
                    .openDate(account.openDate().toString())
                    .linkedFundAcc(account.linkedFundAcc())
                    .build();
        }).toList();
    }
}
