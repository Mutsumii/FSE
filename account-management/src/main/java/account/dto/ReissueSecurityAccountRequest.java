package account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 补办证券账户（内部接口 reissueSecurityAccount）。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReissueSecurityAccountRequest extends CreateSecurityAccountRequest {

    @NotBlank
    @JsonProperty("old_sec_acc_no")
    private String oldSecAccNo;

}
