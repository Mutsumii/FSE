package account.controller.internal;

import account.common.AuthHeaders;
import account.common.Result;
import account.common.ResultPayloadMapper;
import account.dto.CloseSecurityAccountRequest;
import account.dto.CreateSecurityAccountRequest;
import account.dto.ReissueSecurityAccountRequest;
import account.dto.ReportSecurityLossRequest;
import account.dto.UpdateInvestorInfoRequest;
import account.service.api.SecurityAccountService;
import account.service.api.StaffAuthTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/internal/security")
@RequiredArgsConstructor
public class SecurityAccountController {

    private final SecurityAccountService securityAccountService;
    private final StaffAuthTokenService staffAuthTokenService;
    private final ObjectMapper objectMapper;

    @PostMapping("/accounts")
    public Result<Void> createSecurityAccount(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken,
            @Valid @RequestBody CreateSecurityAccountRequest request) {
        request.setStaffId(requireStaffId(authToken));
        log.info("[createSecurityAccount] name={} id_number={} staff_id={}",
                request.getName(), request.getIdNumber(), request.getStaffId());
        return ResultPayloadMapper.flatten(objectMapper, securityAccountService.createSecurityAccount(request), "开户成功");
    }

    @PostMapping("/accounts/loss")
    public Result<Void> reportSecurityLoss(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken,
            @Valid @RequestBody ReportSecurityLossRequest request) {
        request.setStaffId(requireStaffId(authToken));
        log.info("[reportSecurityLoss] sec_acc_no={} staff_id={} reason={}",
                request.getSecAccNo(), request.getStaffId(), request.getReason());
        return ResultPayloadMapper.flatten(objectMapper, securityAccountService.reportSecurityLoss(request), "挂失成功");
    }

    @PostMapping("/accounts/reissue")
    public Result<Void> reissueSecurityAccount(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken,
            @Valid @RequestBody ReissueSecurityAccountRequest request) {
        request.setStaffId(requireStaffId(authToken));
        log.info("[reissueSecurityAccount] old_sec_acc_no={} staff_id={}",
                request.getOldSecAccNo(), request.getStaffId());
        return ResultPayloadMapper.flatten(objectMapper, securityAccountService.reissueSecurityAccount(request), "补办成功");
    }

    @PostMapping("/accounts/close")
    public Result<Void> closeSecurityAccount(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken,
            @Valid @RequestBody CloseSecurityAccountRequest request) {
        request.setStaffId(requireStaffId(authToken));
        log.info("[closeSecurityAccount] sec_acc_no={} staff_id={} reason={}",
                request.getSecAccNo(), request.getStaffId(), request.getReason());
        return ResultPayloadMapper.flatten(objectMapper, securityAccountService.closeSecurityAccount(request), "销户处理完成");
    }

    @PutMapping("/investors")
    public Result<Void> updateInvestorInfo(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken,
            @Valid @RequestBody UpdateInvestorInfoRequest request) {
        request.setStaffId(requireStaffId(authToken));
        log.info("[updateInvestorInfo] investor_id={} staff_id={}", request.getInvestorId(), request.getStaffId());
        return ResultPayloadMapper.flatten(objectMapper, securityAccountService.updateInvestorInfo(request), "更新成功");
    }

    @GetMapping("/accounts")
    public Result<List> listAllSecurityAccounts(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken) {
        Integer staffId = requireStaffId(authToken);
        log.info("[listAllSecurityAccounts] staff_id={}", staffId);
        return Result.success(securityAccountService.listAllSecurityAccounts());
    }

    private Integer requireStaffId(String authToken) {
        return staffAuthTokenService.requireAccess(authToken).staffId();
    }
}
