package account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FundInfoResponse {

    @JsonProperty("fund_acc_no")
    private String fundAccNo;

    @JsonProperty("available_balance")
    private BigDecimal availableBalance;

    @JsonProperty("frozen_balance")
    private BigDecimal frozenBalance;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("status")
    private String status;

    @JsonProperty("logs")
    private List<FundLogView> logs;
}
