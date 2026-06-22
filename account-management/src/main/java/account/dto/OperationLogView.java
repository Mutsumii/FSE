package account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 操作日志视图，用于 queryOperationLog 返回。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OperationLogView {

    @JsonProperty("log_id")
    private Long logId;

    @JsonProperty("staff_id")
    private Integer staffId;

    @JsonProperty("operation_type")
    private String operationType;

    @JsonProperty("target_type")
    private String targetType;

    @JsonProperty("target_id")
    private String targetId;

    @JsonProperty("security_acc_no")
    private String securityAccNo;

    @JsonProperty("fund_acc_no")
    private String fundAccNo;

    @JsonProperty("detail")
    private String detail;

    @JsonProperty("operation_time")
    private LocalDateTime operationTime;
}
