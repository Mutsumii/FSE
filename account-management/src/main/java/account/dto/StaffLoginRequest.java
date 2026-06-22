package account.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 工作人员登录（内部接口 staffLogin）。 */
@Data
public class StaffLoginRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;
}
