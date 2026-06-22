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
public class DashboardStatsResponse {

    @JsonProperty("security_account_count")
    private Long securityAccountCount;

    @JsonProperty("fund_account_count")
    private Long fundAccountCount;

    @JsonProperty("today_new_accounts")
    private Long todayNewAccounts;

    @JsonProperty("abnormal_account_count")
    private Long abnormalAccountCount;
}