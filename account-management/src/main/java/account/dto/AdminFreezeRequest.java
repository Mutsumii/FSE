package account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import account.enums.AccountType;
import account.enums.FreezeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminFreezeRequest {

    @NotNull
    @JsonProperty("account_type")
    private AccountType accountType;

    @NotBlank
    @JsonProperty("account_no")
    private String accountNo;

    @NotNull
    @JsonProperty("freeze_type")
    private FreezeType freezeType;

    @JsonProperty("admin_id")
    private String adminId;

    @JsonProperty("reason")
    private String reason;
}
