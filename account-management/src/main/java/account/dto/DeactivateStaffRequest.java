package account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class DeactivateStaffRequest {

    @NotNull
    @Positive
    @JsonProperty("target_staff_id")
    private Integer targetStaffId;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("operator_staff_id")
    private Integer operatorStaffId;
}
