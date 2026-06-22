package account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminAccountDetailsResponse {

    @JsonProperty("account_no")
    private String accountNo;

    @JsonProperty("account_type")
    private String accountType;

    @JsonProperty("fund_acc_no")
    private String fundAccNo;

    @JsonProperty("sec_acc_no")
    private String secAccNo;

    @JsonProperty("available_balance")
    private BigDecimal availableBalance;

    @JsonProperty("frozen_balance")
    private BigDecimal frozenBalance;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("status")
    private String status;

    @JsonProperty("open_date")
    private LocalDate openDate;

    @JsonProperty("investor_id")
    private Integer investorId;

    @JsonProperty("investor_name")
    private String investorName;

    @JsonProperty("id_number")
    private String idNumber;

    @JsonProperty("investor_type")
    private String investorType;

    @JsonProperty("linked_fund_acc")
    private String linkedFundAcc;

    @JsonProperty("holdings_count")
    private Integer holdingsCount;

    @JsonProperty("total_holdings_qty")
    private Integer totalHoldingsQty;
}
