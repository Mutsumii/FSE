package account.controller.external;

import account.common.AuthHeaders;
import account.common.Result;
import account.common.ResultPayloadMapper;
import account.dto.AdminCloseSecurityAccountRequest;
import account.dto.AdminFreezeRequest;
import account.dto.SettleAnnualInterestRequest;
import account.service.api.AdminService;
import account.service.api.StaffAuthTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final StaffAuthTokenService staffAuthTokenService;
    private final ObjectMapper objectMapper;

    @PostMapping("/fund/settle-annual-interest")
    public Result<Void> settleAnnualInterest(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken,
            @Valid @RequestBody SettleAnnualInterestRequest request) {
        request.setOperatorId(String.valueOf(requireStaffId(authToken)));
        log.info("[settleAnnualInterest] operator_id={} year_rate={}", request.getOperatorId(), request.getYearRate());
        return ResultPayloadMapper.flatten(objectMapper, adminService.settleAnnualInterest(request), "结息完成");
    }

    @PostMapping("/accounts/freeze")
    public Result<Void> adminFreezeAccount(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken,
            @Valid @RequestBody AdminFreezeRequest request) {
        request.setAdminId(String.valueOf(requireStaffId(authToken)));
        log.info("[adminFreezeAccount] account_type={} account_no={} freeze_type={} admin_id={} reason={}",
                request.getAccountType(), request.getAccountNo(),
                request.getFreezeType(), request.getAdminId(), request.getReason());
        adminService.adminFreezeAccount(request);
        return Result.success("操作成功");
    }

    @PostMapping("/accounts/unfreeze")
    public Result<Void> adminUnfreezeAccount(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken,
            @Valid @RequestBody AdminFreezeRequest request) {
        request.setAdminId(String.valueOf(requireStaffId(authToken)));
        log.info("[adminUnfreezeAccount] account_type={} account_no={} freeze_type={} admin_id={}",
                request.getAccountType(), request.getAccountNo(),
                request.getFreezeType(), request.getAdminId());
        adminService.adminUnfreezeAccount(request);
        return Result.success("操作成功");
    }

    @GetMapping("/accounts/{account_no}")
    public Result<Void> adminGetAccountDetails(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken,
            @PathVariable("account_no") @NotBlank String accountNo) {
        String adminId = String.valueOf(requireStaffId(authToken));
        log.info("[adminGetAccountDetails] account_no={} admin_id={}", accountNo, adminId);
        return ResultPayloadMapper.flatten(objectMapper, adminService.adminGetAccountDetails(accountNo, adminId), "查询成功");
    }

    @PostMapping("/security/force-close")
    public Result<Void> adminCloseSecurityAccount(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken,
            @Valid @RequestBody AdminCloseSecurityAccountRequest request) {
        request.setAdminId(String.valueOf(requireStaffId(authToken)));
        log.info("[adminCloseSecurityAccount] security_account_no={} admin_id={} force_reason={}",
                request.getSecurityAccountNo(), request.getAdminId(), request.getForceReason());
        adminService.adminCloseSecurityAccount(request);
        return Result.success("强制销户成功");
    }

    private Integer requireStaffId(String authToken) {
        return staffAuthTokenService.requireAccess(authToken).staffId();
    }
}
