package account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FundTradeUpdateResponse {

    @JsonProperty("available_balance")
    private BigDecimal availableBalance;

    @JsonProperty("frozen_balance")
    private BigDecimal frozenBalance;

    @JsonProperty("log_id")
    private Long logId;

    @JsonProperty("duplicate")
    private Boolean duplicate;
}
