package account.service;

import account.dao.model.DomainEnums;

public final class EnumMapper {

    private EnumMapper() {
    }

    public static DomainEnums.AccountStatus toDaoStatus(account.enums.AccountStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case NORMAL -> DomainEnums.AccountStatus.NORMAL;
            case FROZEN_LOSS -> DomainEnums.AccountStatus.LOSS_FROZEN;
            case FROZEN_VIOLATION -> DomainEnums.AccountStatus.VIOLATION_FROZEN;
            case FROZEN_NO_FUND -> DomainEnums.AccountStatus.NO_FUND_FROZEN;
            case PRE_CLOSE -> DomainEnums.AccountStatus.PRE_CLOSE;
            case CLOSED -> DomainEnums.AccountStatus.CLOSED;
        };
    }

    public static account.enums.AccountStatus fromDaoStatus(DomainEnums.AccountStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case NORMAL -> account.enums.AccountStatus.NORMAL;
            case LOSS_FROZEN -> account.enums.AccountStatus.FROZEN_LOSS;
            case VIOLATION_FROZEN -> account.enums.AccountStatus.FROZEN_VIOLATION;
            case NO_FUND_FROZEN -> account.enums.AccountStatus.FROZEN_NO_FUND;
            case PRE_CLOSE -> account.enums.AccountStatus.PRE_CLOSE;
            case CLOSED -> account.enums.AccountStatus.CLOSED;
        };
    }

    public static DomainEnums.FreezeType toDaoFreezeType(account.enums.FreezeType freezeType) {
        if (freezeType == null) {
            return null;
        }
        return switch (freezeType) {
            case LOSS -> DomainEnums.FreezeType.LOSS;
            case VIOLATION -> DomainEnums.FreezeType.VIOLATION;
        };
    }

    public static account.enums.FreezeType fromDaoFreezeType(DomainEnums.FreezeType freezeType) {
        if (freezeType == null) {
            return null;
        }
        return switch (freezeType) {
            case LOSS -> account.enums.FreezeType.LOSS;
            case VIOLATION -> account.enums.FreezeType.VIOLATION;
            default -> null;
        };
    }

    public static DomainEnums.AccountType toDaoAccountType(account.enums.AccountType accountType) {
        if (accountType == null) {
            return null;
        }
        return switch (accountType) {
            case SECURITY -> DomainEnums.AccountType.SECURITY;
            case FUND -> DomainEnums.AccountType.FUND;
        };
    }

    public static DomainEnums.InvestorType toDaoInvestorType(String investorTypeCn) {
        if (investorTypeCn == null) {
            return null;
        }
        return switch (investorTypeCn) {
            case "个人" -> DomainEnums.InvestorType.PERSONAL;
            case "法人" -> DomainEnums.InvestorType.LEGAL_ENTITY;
            default -> throw new IllegalArgumentException("Unsupported investor type: " + investorTypeCn);
        };
    }

    public static String fromDaoInvestorType(DomainEnums.InvestorType investorType) {
        if (investorType == null) {
            return null;
        }
        return switch (investorType) {
            case PERSONAL -> "个人";
            case LEGAL_ENTITY -> "法人";
        };
    }
}
