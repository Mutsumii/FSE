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
public class SecurityAccountListItemResponse {

    @JsonProperty("sec_acc_no")
    private String secAccNo;

    @JsonProperty("investor_id")
    private Integer investorId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("id_number")
    private String idNumber;

    @JsonProperty("investor_type")
    private String investorType;

    @JsonProperty("status")
    private String status;

    @JsonProperty("open_date")
    private String openDate;

    @JsonProperty("linked_fund_acc")
    private String linkedFundAcc;
}