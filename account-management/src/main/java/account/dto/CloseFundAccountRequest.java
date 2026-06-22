package account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 销户资金账户（内部接口 closeFundAccount）。 */
@Data
public class CloseFundAccountRequest {

    @NotBlank
    @JsonProperty("fund_acc_no")
    private String fundAccNo;

    @NotBlank
    @JsonProperty("id_number")
    private String idNumber;

    @JsonProperty("staff_id")
    private Integer staffId;

    @JsonProperty("reason")
    private String reason;
}
