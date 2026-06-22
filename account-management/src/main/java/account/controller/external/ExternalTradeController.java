package account.controller.external;

import account.common.Result;
import account.common.ResultPayloadMapper;
import account.dto.UpdateFundBalanceRequest;
import account.dto.UpdateSecurityHoldingRequest;
import account.service.api.FundAccountService;
import account.service.api.SecurityAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/external/trade")
@RequiredArgsConstructor
public class ExternalTradeController {

    private final FundAccountService fundAccountService;
    private final SecurityAccountService securityAccountService;
    private final ObjectMapper objectMapper;

    @PostMapping("/fund-balance")
    public Result<Void> updateFundBalance(@Valid @RequestBody UpdateFundBalanceRequest request) {
        log.info("[updateFundBalance] fund_acc_no={} ref_order_id={} txn_type={} amount={}",
                request.getFundAccNo(), request.getRefOrderId(),
                request.getTxnType(), request.getAmount());
        return ResultPayloadMapper.flatten(objectMapper, fundAccountService.updateFundBalance(request), "资金变更成功");
    }

    @PostMapping("/security-holding")
    public Result<Void> updateSecurityHolding(@Valid @RequestBody UpdateSecurityHoldingRequest request) {
        log.info("[updateSecurityHolding] sec_acc_no={} stock_code={} ref_order_id={} change_type={} quantity={} price={}",
                request.getSecAccNo(), request.getStockCode(), request.getRefOrderId(),
                request.getChangeType(), request.getQuantity(), request.getPrice());
        return ResultPayloadMapper.flatten(objectMapper, securityAccountService.updateSecurityHolding(request), "持仓更新成功");
    }
}
