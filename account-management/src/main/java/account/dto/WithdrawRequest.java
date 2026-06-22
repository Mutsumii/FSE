package account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 资金账户取款（内部接口 withdraw）。 */
@Data
public class WithdrawRequest {

    @NotBlank
    @JsonProperty("fund_acc_no")
    private String fundAccNo;

    @NotNull
    @DecimalMin(value = "0.01", message = "取款金额必须大于 0")
    @JsonProperty("amount")
    private BigDecimal amount;

    @NotBlank
    @JsonProperty("withdraw_password")
    private String withdrawPassword;

    @JsonProperty("staff_id")
    private Integer staffId;
}
