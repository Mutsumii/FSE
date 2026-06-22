package account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class UpdateSecurityHoldingRequest {

    @NotBlank
    @JsonProperty("sec_acc_no")
    private String secAccNo;

    @NotBlank
    @JsonProperty("stock_code")
    private String stockCode;

    @NotBlank
    @JsonProperty("stock_name")
    private String stockName;

    @NotBlank
    @JsonProperty("ref_order_id")
    private String refOrderId;

    @NotBlank
    @Pattern(
            regexp = "^(买入增加|卖出冻结|卖出扣减|撤单释放)$",
            message = "change_type 必须为 买入增加/卖出冻结/卖出扣减/撤单释放 之一")
    @JsonProperty("change_type")
    private String changeType;

    @NotNull
    @Positive
    @JsonProperty("quantity")
    private Integer quantity;

    @JsonProperty("price")
    private BigDecimal price;
}
