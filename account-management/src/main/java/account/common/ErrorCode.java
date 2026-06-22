package account.common;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(0, "OK", "成功"),

    ERR_001(1001, "ERR_001", "余额不足"),
    ERR_002(1002, "ERR_002", "持仓不足"),
    ERR_003(1003, "ERR_003", "账户已冻结"),
    ERR_004(1004, "ERR_004", "密码错误"),
    ERR_005(1005, "ERR_005", "证券账户不存在"),
    ERR_006(1006, "ERR_006", "该投资者已拥有其他证券账户"),
    ERR_007(1007, "ERR_007", "资金账户尚有可用余额或冻结资金，当前操作不允许"),
    ERR_008(1008, "ERR_008", "证券账户未关联当前资金账户"),
    ERR_009(1009, "ERR_009", "工作人员认证失败"),
    ERR_010(1010, "ERR_010", "账户不存在"),
    ERR_011(1011, "ERR_011", "账户已是请求的状态"),
    ERR_012(1012, "ERR_012", "投资者在黑名单中"),
    ERR_013(1013, "ERR_013", "证券账户持有人与投资者身份证不一致"),
    ERR_014(1014, "ERR_014", "账户绑定关系冲突"),
    ERR_015(1015, "ERR_015", "资金账户未绑定符合要求的证券账户"),
    ERR_016(1016, "ERR_016", "资金账户存在未成交委托单"),
    ERR_017(1017, "ERR_017", "资金账户处于冻结状态，当前操作不允许"),
    ERR_018(1018, "ERR_018", "认证令牌无效或已失效"),
    ERR_019(1019, "ERR_019", "开户资格不符合"),
    ERR_020(1020, "ERR_020", "证件类型或证件号码不合法"),
    ERR_021(1021, "ERR_021", "当前账户状态不允许执行该操作"),
    ERR_022(1022, "ERR_022", "证券账户仍有持仓，无法销户"),

    PARAM_INVALID(4000, "ERR_PARAM", "参数校验失败"),
    SYSTEM_ERROR(5000, "ERR_SYS", "系统内部错误");

    private final int code;
    private final String symbol;
    private final String message;

    ErrorCode(int code, String symbol, String message) {
        this.code = code;
        this.symbol = symbol;
        this.message = message;
    }
}
