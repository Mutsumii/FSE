package account.enums;

/** 冻结类型枚举，对接 freezeType 字段。 */
public enum FreezeType {
    /** 挂失冻结：用户本人补办后解冻 */
    LOSS,
    /** 违规冻结：仅管理员可解冻 */
    VIOLATION
}
