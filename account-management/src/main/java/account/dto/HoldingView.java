package account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HoldingView {

    @JsonProperty("stock_code")
    private String stockCode;

    @JsonProperty("stock_name")
    private String stockName;

    @JsonProperty("quantity")
    private Integer quantity;

    @JsonProperty("frozen_quantity")
    private Integer frozenQuantity;

    @JsonProperty("available_quantity")
    private Integer availableQuantity;

    @JsonProperty("avg_cost")
    private BigDecimal avgCost;
}
