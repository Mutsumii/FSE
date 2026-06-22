package account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 工作人员修改密码（内部接口）。 */
@Data
public class ChangeStaffPasswordRequest {

    @NotBlank
    @JsonProperty("old_password")
    private String oldPassword;

    @NotBlank
    @JsonProperty("new_password")
    private String newPassword;

    @JsonProperty("staff_id")
    private Integer staffId;
}