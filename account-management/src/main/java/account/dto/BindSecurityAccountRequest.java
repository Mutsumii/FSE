package account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BindSecurityAccountRequest {

    @NotBlank
    @JsonProperty("fund_acc_no")
    private String fundAccNo;

    @NotBlank
    @JsonProperty("sec_acc_no")
    private String secAccNo;

    @JsonProperty("staff_id")
    private Integer staffId;
}
