package account.controller.internal;

import account.common.AuthHeaders;
import account.common.Result;
import account.common.ResultPayloadMapper;
import account.dto.CompleteLoginCertificateRequest;
import account.dto.DeactivateStaffRequest;
import account.dto.StaffLoginRequest;
import account.service.api.StaffAuthTokenService;
import account.service.api.StaffService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/internal/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;
    private final StaffAuthTokenService staffAuthTokenService;
    private final ObjectMapper objectMapper;

    @PostMapping("/login")
    public Result<Void> staffLogin(@Valid @RequestBody StaffLoginRequest request) {
        log.info("[staffLogin] username={}", request.getUsername());
        return ResultPayloadMapper.flatten(objectMapper, staffService.staffLogin(request), "登录成功");
    }

    @PostMapping("/complete-certificate")
    public Result<Void> completeCertificate(@Valid @RequestBody CompleteLoginCertificateRequest request) {
        log.info("[completeStaffCertificate] subject_key={}", request.getSubjectKey());
        return ResultPayloadMapper.flatten(objectMapper, staffService.completeLoginCertificate(request), "认证成功");
    }

    @PostMapping("/deactivate")
    public Result<Void> deactivateStaff(
            @RequestHeader(AuthHeaders.STAFF_AUTH_TOKEN) String authToken,
            @Valid @RequestBody DeactivateStaffRequest request) {
        request.setOperatorStaffId(requireStaffId(authToken));
        log.info("[deactivateStaff] target_staff_id={} operator_staff_id={} reason={}",
                request.getTargetStaffId(), request.getOperatorStaffId(), request.getReason());
        return ResultPayloadMapper.flatten(objectMapper, staffService.deactivateStaff(request), "停用成功");
    }

    private Integer requireStaffId(String authToken) {
        return staffAuthTokenService.requireAccess(authToken).staffId();
    }
}
