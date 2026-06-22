package account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/** 年度结息（管理员接口 settleAnnualInterest）。 */
@Data
public class SettleAnnualInterestRequest {

    @JsonProperty("operator_id")
    private String operatorId;

    /** 可选；不传则使用 fund_account 表内的年利率 */
    @JsonProperty("year_rate")
    private BigDecimal yearRate;
}
