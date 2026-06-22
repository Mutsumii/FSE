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
public class FundAccountListItemResponse {

    @JsonProperty("fund_acc_no")
    private String fundAccNo;

    @JsonProperty("sec_acc_no")
    private String secAccNo;

    @JsonProperty("name")
    private String name;

    @JsonProperty("id_number")
    private String idNumber;

    @JsonProperty("available_balance")
    private BigDecimal availableBalance;

    @JsonProperty("frozen_balance")
    private BigDecimal frozenBalance;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("status")
    private String status;

    @JsonProperty("open_date")
    private String openDate;
}