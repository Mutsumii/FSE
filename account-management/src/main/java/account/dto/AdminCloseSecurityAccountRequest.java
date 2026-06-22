package account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminCloseSecurityAccountRequest {

    @NotBlank
    @JsonProperty("security_account_no")
    private String securityAccountNo;

    @JsonProperty("admin_id")
    private String adminId;

    @NotBlank
    @JsonProperty("force_reason")
    private String forceReason;
}
