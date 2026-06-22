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
public class FundReissueResponse {

    @JsonProperty("new_fund_acc_no")
    private String newFundAccNo;

    @JsonProperty("old_fund_acc_no")
    private String oldFundAccNo;
}
