package account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 资金账户挂失（内部接口 reportFundLoss）。 */
@Data
public class ReportFundLossRequest {

    @NotBlank
    @JsonProperty("fund_acc_no")
    private String fundAccNo;

    @NotBlank
    @JsonProperty("id_number")
    private String idNumber;

    @NotBlank
    @JsonProperty("sec_acc_no")
    private String secAccNo;

    @JsonProperty("staff_id")
    private Integer staffId;

    @JsonProperty("reason")
    private String reason;
}
