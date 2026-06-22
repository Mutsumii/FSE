package account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecurityReissueResponse {

    @JsonProperty("new_sec_acc_no")
    private String newSecAccNo;

    @JsonProperty("old_sec_acc_no")
    private String oldSecAccNo;
}
