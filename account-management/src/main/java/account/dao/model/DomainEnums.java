package account.dao.model;

import java.util.Arrays;

public final class DomainEnums {

    private DomainEnums() {
    }

    public interface DbValueEnum {
        String dbValue();
    }

    public enum InvestorType implements DbValueEnum {
        PERSONAL("个人"),
        LEGAL_ENTITY("法人");

        private final String dbValue;

        InvestorType(String dbValue) {
            this.dbValue = dbValue;
        }

        @Override
        public String dbValue() {
            return dbValue;
        }

        public static InvestorType fromDbValue(String dbValue) {
            return DomainEnums.fromDbValue(InvestorType.class, dbValue);
        }
    }

    public enum AccountStatus implements DbValueEnum {
        NORMAL("正常"),
        LOSS_FROZEN("挂失冻结"),
        VIOLATION_FROZEN("违规冻结"),
        NO_FUND_FROZEN("无资金账户冻结"),
        PRE_CLOSE("预销户"),
        CLOSED("已销户");

        private final String dbValue;

        AccountStatus(String dbValue) {
            this.dbValue = dbValue;
        }

        @Override
        public String dbValue() {
            return dbValue;
        }

        public static AccountStatus fromDbValue(String dbValue) {
            return DomainEnums.fromDbValue(AccountStatus.class, dbValue);
        }
    }

    public enum FundTransactionType implements DbValueEnum {
        DEPOSIT("存款"),
        WITHDRAW("取款"),
        BUY_FREEZE("买入冻结"),
        BUY_DEBIT("买入扣款"),
        SELL_RETURN("卖出回款"),
        CANCEL_RELEASE("撤单解冻"),
        INTEREST("结息");

        private final String dbValue;

        FundTransactionType(String dbValue) {
            this.dbValue = dbValue;
        }

        @Override
        public String dbValue() {
            return dbValue;
        }

        public static FundTransactionType fromDbValue(String dbValue) {
            return DomainEnums.fromDbValue(FundTransactionType.class, dbValue);
        }
    }

    public enum AccountType implements DbValueEnum {
        SECURITY("SECURITY"),
        FUND("FUND");

        private final String dbValue;

        AccountType(String dbValue) {
            this.dbValue = dbValue;
        }

        @Override
        public String dbValue() {
            return dbValue;
        }

        public static AccountType fromDbValue(String dbValue) {
            return DomainEnums.fromDbValue(AccountType.class, dbValue);
        }
    }

    public enum FreezeType implements DbValueEnum {
        LOSS("LOSS"),
        VIOLATION("VIOLATION"),
        BUY_ORDER("BUY_ORDER"),
        SELL_ORDER("SELL_ORDER");

        private final String dbValue;

        FreezeType(String dbValue) {
            this.dbValue = dbValue;
        }

        @Override
        public String dbValue() {
            return dbValue;
        }

        public static FreezeType fromDbValue(String dbValue) {
            return DomainEnums.fromDbValue(FreezeType.class, dbValue);
        }
    }

    private static <E extends Enum<E> & DbValueEnum> E fromDbValue(Class<E> type, String dbValue) {
        if (dbValue == null) {
            return null;
        }
        return Arrays.stream(type.getEnumConstants())
                .filter(value -> value.dbValue().equals(dbValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported db value '" + dbValue + "' for " + type.getSimpleName()));
    }
}
