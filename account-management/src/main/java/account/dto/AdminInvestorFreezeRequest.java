package account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminInvestorFreezeRequest {

    @NotBlank
    @JsonProperty("id_number")
    private String idNumber;

    @JsonProperty("admin_id")
    private String adminId;

    @JsonProperty("reason")
    private String reason;
}
