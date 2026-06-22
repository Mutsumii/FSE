package account.controller.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import account.common.AuthHeaders;
import account.common.Result;
import account.common.ResultPayloadMapper;
import account.dto.BindSecurityAccountRequest;
import account.dto.ChangeFundPasswordRequest;
import account.dto.CloseFundAccountRequest;
import account.dto.CreateFundAccountRequest;
import account.dto.DepositRequest;
import account.dto.FundLogView;
import account.dto.ReissueFundAccountRequest;
import account.dto.ReportFundLossRequest;
import account.dto.WithdrawRequest;
import account.service.api.FundAccountService;
import account.service.api.StaffAuthTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/internal/fund")
@RequiredArgsConstructor
public class FundAccountController {

    private final FundAccountService fundAccountService;
    private final StaffAuthTokenService staffAuthTokenService;
    private final ObjectMapper objectMapper;

    @PostMapping("/accounts")
    public Result<Void> createFundAccount(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken,
            @Valid @RequestBody CreateFundAccountRequest request) {
        request.setStaffId(requireStaffId(authToken));
        log.info("[createFundAccount] sec_acc_no={} id_number={} currency={} staff_id={}",
                request.getSecAccNo(), request.getIdNumber(), request.getCurrency(), request.getStaffId());
        return ResultPayloadMapper.flatten(objectMapper, fundAccountService.createFundAccount(request), "开户成功");
    }

    @PostMapping("/deposit")
    public Result<Void> deposit(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken,
            @Valid @RequestBody DepositRequest request) {
        request.setStaffId(requireStaffId(authToken));
        log.info("[deposit] fund_acc_no={} amount={} staff_id={}",
                request.getFundAccNo(), request.getAmount(), request.getStaffId());
        return ResultPayloadMapper.flatten(objectMapper, fundAccountService.deposit(request), "存款成功");
    }

    @PostMapping("/withdraw")
    public Result<Void> withdraw(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken,
            @Valid @RequestBody WithdrawRequest request) {
        request.setStaffId(requireStaffId(authToken));
        log.info("[withdraw] fund_acc_no={} amount={} staff_id={}",
                request.getFundAccNo(), request.getAmount(), request.getStaffId());
        return ResultPayloadMapper.flatten(objectMapper, fundAccountService.withdraw(request), "取款成功");
    }

    @PutMapping("/password")
    public Result<Void> changeFundPassword(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken,
            @Valid @RequestBody ChangeFundPasswordRequest request) {
        request.setStaffId(requireStaffId(authToken));
        log.info("[changeFundPassword] fund_acc_no={} password_type={} staff_id={}",
                request.getFundAccNo(), request.getPasswordType(), request.getStaffId());
        fundAccountService.changeFundPassword(request);
        return Result.success("密码修改成功");
    }

    @PostMapping("/accounts/loss")
    public Result<Void> reportFundLoss(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken,
            @Valid @RequestBody ReportFundLossRequest request) {
        request.setStaffId(requireStaffId(authToken));
        log.info("[reportFundLoss] fund_acc_no={} staff_id={} reason={}",
                request.getFundAccNo(), request.getStaffId(), request.getReason());
        return ResultPayloadMapper.flatten(objectMapper, fundAccountService.reportFundLoss(request), "挂失成功");
    }

    @PostMapping("/accounts/reissue")
    public Result<Void> reissueFundAccount(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken,
            @Valid @RequestBody ReissueFundAccountRequest request) {
        request.setStaffId(requireStaffId(authToken));
        log.info("[reissueFundAccount] old_fund_acc_no={} staff_id={}",
                request.getOldFundAccNo(), request.getStaffId());
        return ResultPayloadMapper.flatten(objectMapper, fundAccountService.reissueFundAccount(request), "补办成功");
    }

    @PostMapping("/accounts/close")
    public Result<Void> closeFundAccount(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken,
            @Valid @RequestBody CloseFundAccountRequest request) {
        request.setStaffId(requireStaffId(authToken));
        log.info("[closeFundAccount] fund_acc_no={} staff_id={} reason={}",
                request.getFundAccNo(), request.getStaffId(), request.getReason());
        return ResultPayloadMapper.flatten(objectMapper, fundAccountService.closeFundAccount(request), "销户成功");
    }

    @PostMapping("/accounts/bind")
    public Result<Void> bindSecurityAccount(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken,
            @Valid @RequestBody BindSecurityAccountRequest request) {
        request.setStaffId(requireStaffId(authToken));
        log.info("[bindSecurityAccount] fund_acc_no={} sec_acc_no={} staff_id={}",
                request.getFundAccNo(), request.getSecAccNo(), request.getStaffId());
        return ResultPayloadMapper.flatten(
                objectMapper,
                fundAccountService.bindSecurityAccount(request.getFundAccNo(), request.getSecAccNo(), request.getStaffId()),
                "绑定成功"
        );
    }

    @PostMapping("/accounts/unbind")
    public Result<Void> unbindSecurityAccount(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken,
            @Valid @RequestBody BindSecurityAccountRequest request) {
        request.setStaffId(requireStaffId(authToken));
        log.info("[unbindSecurityAccount] fund_acc_no={} sec_acc_no={} staff_id={}",
                request.getFundAccNo(), request.getSecAccNo(), request.getStaffId());
        return ResultPayloadMapper.flatten(
                objectMapper,
                fundAccountService.unbindSecurityAccount(request.getFundAccNo(), request.getSecAccNo(), request.getStaffId()),
                "解绑成功"
        );
    }

    @GetMapping("/accounts")
    public Result<Void> queryFundInfo(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken,
            @RequestParam("fund_acc_no") @NotBlank String fundAccNo,
            @RequestParam("id_number") @NotBlank String idNumber,
            @RequestParam(value = "include_logs", defaultValue = "false") boolean includeLogs) {
        Integer staffId = requireStaffId(authToken);
        log.info("[queryFundInfo] fund_acc_no={} include_logs={} staff_id={}", fundAccNo, includeLogs, staffId);
        return ResultPayloadMapper.flatten(
                objectMapper,
                fundAccountService.queryFundInfo(fundAccNo, idNumber, includeLogs, staffId),
                "查询成功"
        );
    }

    @GetMapping("/logs")
    public Result<List<FundLogView>> queryFundLogs(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken,
            @RequestParam("fund_acc_no") @NotBlank String fundAccNo,
            @RequestParam("id_number") @NotBlank String idNumber,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        Integer staffId = requireStaffId(authToken);
        log.info("[queryFundLogs] fund_acc_no={} limit={} staff_id={}", fundAccNo, limit, staffId);
        return Result.success(
                fundAccountService.queryFundLogs(fundAccNo, idNumber, limit, staffId)
        );
    }

    @GetMapping("/accounts/list")
    public Result<List> listAllFundAccounts(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken) {
        Integer staffId = requireStaffId(authToken);
        log.info("[listAllFundAccounts] staff_id={}", staffId);
        return Result.success(fundAccountService.listAllFundAccounts());
    }

    private Integer requireStaffId(String authToken) {
        return staffAuthTokenService.requireAccess(authToken).staffId();
    }
}
