package account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/** 中央交易系统资金变更（外部接口 updateFundBalance）。 */
@Data
public class UpdateFundBalanceRequest {

    @NotBlank
    @JsonProperty("fund_acc_no")
    private String fundAccNo;

    @NotBlank
    @JsonProperty("ref_order_id")
    private String refOrderId;

    @NotBlank
    @Pattern(regexp = "^(买入冻结|买入扣款|卖出回款|撤单解冻)$",
            message = "txn_type 必须为 买入冻结/买入扣款/卖出回款/撤单解冻 之一")
    @JsonProperty("txn_type")
    private String txnType;

    @NotNull
    @Positive(message = "amount 必须为正数")
    @JsonProperty("amount")
    private BigDecimal amount;
}
