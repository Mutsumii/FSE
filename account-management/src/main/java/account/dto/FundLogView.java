package account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FundLogView {

    @JsonProperty("log_id")
    private Long logId;

    @JsonProperty("txn_type")
    private String txnType;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("txn_time")
    private LocalDateTime txnTime;

    @JsonProperty("ref_order_id")
    private String refOrderId;

    @JsonProperty("stock_code")
    private String stockCode;

    @JsonProperty("stock_name")
    private String stockName;

    @JsonProperty("holding_change_type")
    private String holdingChangeType;

    @JsonProperty("share_quantity")
    private Integer shareQuantity;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("holding_quantity_after")
    private Integer holdingQuantityAfter;

    @JsonProperty("holding_frozen_quantity_after")
    private Integer holdingFrozenQuantityAfter;
}
