package account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 补办资金账户（内部接口 reissueFundAccount）。 */
@Data
public class ReissueFundAccountRequest {

    @NotBlank
    @JsonProperty("old_fund_acc_no")
    private String oldFundAccNo;

    @NotBlank
    @JsonProperty("id_number")
    private String idNumber;

    @NotBlank
    @JsonProperty("sec_acc_no")
    private String secAccNo;

    @NotBlank
    @JsonProperty("currency")
    private String currency;

    @NotBlank
    @JsonProperty("new_trade_password")
    private String newTradePassword;

    @NotBlank
    @JsonProperty("new_withdraw_password")
    private String newWithdrawPassword;

    @JsonProperty("staff_id")
    private Integer staffId;
}
