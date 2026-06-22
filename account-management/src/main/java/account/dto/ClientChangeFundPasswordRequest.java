package account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ClientChangeFundPasswordRequest {

    @NotBlank
    @JsonProperty("fund_acc_no")
    private String fundAccNo;

    @NotBlank
    @JsonProperty("auth_token")
    private String authToken;

    @NotBlank
    @Pattern(regexp = "^(trade|withdraw)$", message = "password_type must be trade or withdraw")
    @JsonProperty("password_type")
    private String passwordType;

    @NotBlank
    @JsonProperty("old_password")
    private String oldPassword;

    @NotBlank
    @JsonProperty("new_password")
    private String newPassword;
}
