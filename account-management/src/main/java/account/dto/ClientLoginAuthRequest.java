package account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 交易客户端登录鉴权请求（外部接口 clientLoginAuth）。 */
@Data
public class ClientLoginAuthRequest {

    @NotBlank(message = "资金账户号不能为空")
    @JsonProperty("fund_acc_no")
    private String fundAccNo;

    @NotBlank(message = "交易密码不能为空")
    @JsonProperty("trade_password")
    private String tradePassword;
}
