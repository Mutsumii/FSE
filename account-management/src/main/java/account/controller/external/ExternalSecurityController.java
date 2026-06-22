package account.controller.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import account.common.Result;
import account.common.ResultPayloadMapper;
import account.service.api.SecurityAccountService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/external/security")
@RequiredArgsConstructor
public class ExternalSecurityController {

    private final SecurityAccountService securityAccountService;
    private final ObjectMapper objectMapper;

    @GetMapping("/snapshot")
    public Result<Void> getSecuritySnapshot(
            @RequestParam("sec_acc_no") @NotBlank String secAccNo,
            @RequestParam("auth_token") @NotBlank String authToken,
            @RequestParam(value = "stock_code", required = false) String stockCode) {
        log.info("[getSecuritySnapshot] sec_acc_no={} stock_code={}", secAccNo, stockCode);
        return ResultPayloadMapper.flatten(
                objectMapper,
                securityAccountService.getSecuritySnapshot(secAccNo, stockCode, authToken),
                "查询成功"
        );
    }
}
