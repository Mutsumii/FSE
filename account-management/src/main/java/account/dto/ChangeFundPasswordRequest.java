package account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 修改密码（内部接口 changeFundPassword）。 */
@Data
public class ChangeFundPasswordRequest {

    @NotBlank
    @JsonProperty("fund_acc_no")
    private String fundAccNo;

    @NotBlank
    @Pattern(regexp = "^(trade|withdraw)$")
    @JsonProperty("password_type")
    private String passwordType;

    @NotBlank
    @JsonProperty("old_password")
    private String oldPassword;

    @NotBlank
    @JsonProperty("new_password")
    private String newPassword;

    @JsonProperty("staff_id")
    private Integer staffId;
}
