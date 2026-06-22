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
public class FundAccountCreatedResponse {

    @JsonProperty("fund_acc_no")
    private String fundAccNo;

    @JsonProperty("status")
    private String status;

    @JsonProperty("sec_acc_no")
    private String secAccNo;

    @JsonProperty("currency")
    private String currency;
}
