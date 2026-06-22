package account.enums;

public enum AccountStatus {
    NORMAL("正常"),
    FROZEN_LOSS("挂失冻结"),
    FROZEN_VIOLATION("违规冻结"),
    FROZEN_NO_FUND("无资金账户冻结"),
    PRE_CLOSE("预销户"),
    CLOSED("已销户");

    private final String cn;

    AccountStatus(String cn) {
        this.cn = cn;
    }

    public String cn() {
        return cn;
    }

    public String code() {
        return name();
    }
}
