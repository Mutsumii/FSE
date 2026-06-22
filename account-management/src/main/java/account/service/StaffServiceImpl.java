package account.service;

import account.common.BusinessException;
import account.common.ErrorCode;
import account.dao.DaoRegistry;
import account.dao.model.DomainModels;
import account.dto.ChangeStaffPasswordRequest;
import account.dto.CompleteLoginCertificateRequest;
import account.dto.DeactivateStaffRequest;
import account.dto.StaffLoginRequest;
import account.dto.StaffLoginResponse;
import account.dto.StaffStatusResponse;
import account.service.api.StaffAuthTokenService;
import account.service.api.StaffService;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StaffServiceImpl implements StaffService {

    private static final String STAFF_CERTIFICATE_SUBJECT = "STAFF";
    private static final String DEFAULT_CERTIFICATE_CODE = "CERT-123456";
    private static final String STAFF_STATUS_NORMAL = "正常";
    private static final String STAFF_STATUS_NORMAL_LEGACY = "姝ｅ父";
    private static final String STAFF_STATUS_DISABLED = "禁用";
    private static final String STAFF_STATUS_DISABLED_LEGACY = "绂佺敤";

    private final DaoRegistry dao;
    private final StaffAuthTokenService staffAuthTokenService;

    public StaffServiceImpl(DaoRegistry dao, StaffAuthTokenService staffAuthTokenService) {
        this.dao = dao;
        this.staffAuthTokenService = staffAuthTokenService;
    }

    @Override
    public StaffLoginResponse staffLogin(StaffLoginRequest request) {
        var staff = dao.staffDao().findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_009, "工作人员不存在或密码错误"));

        if (!isActiveStaffStatus(staff.status())) {
            throw new BusinessException(ErrorCode.ERR_009, "工作人员账号不可用");
        }

        if (!PasswordUtil.verify(request.getPassword(), staff.passwordHash())) {
            throw new BusinessException(ErrorCode.ERR_009, "工作人员不存在或密码错误");
        }

        var certificateState = ensureCertificateState(STAFF_CERTIFICATE_SUBJECT, staff.username());
        log.info("[staffLogin] staff_id={} username={} status={}",
                staff.staffId(), staff.username(), staff.status());

        if (!certificateState.certificateVerified()) {
            return StaffLoginResponse.builder()
                    .staffId(staff.staffId())
                    .username(staff.username())
                    .status(staff.status())
                    .requiresCertificate(true)
                    .certificateSubjectType(STAFF_CERTIFICATE_SUBJECT)
                    .certificateSubjectKey(staff.username())
                    .build();
        }

        String authToken = staffAuthTokenService.issueToken(staff.staffId(), staff.username());
        return StaffLoginResponse.builder()
                .staffId(staff.staffId())
                .username(staff.username())
                .status(staff.status())
                .requiresCertificate(false)
                .authToken(authToken)
                .build();
    }

    @Override
    public StaffLoginResponse completeLoginCertificate(CompleteLoginCertificateRequest request) {
        if (!STAFF_CERTIFICATE_SUBJECT.equalsIgnoreCase(request.getSubjectType())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "subject_type must be STAFF");
        }
        if (!DEFAULT_CERTIFICATE_CODE.equals(request.getCertificateCode().trim())) {
            throw new BusinessException(ErrorCode.ERR_004, "安全证书认证码错误");
        }

        var staff = dao.staffDao().findByUsername(request.getSubjectKey())
                .orElseThrow(() -> new BusinessException(ErrorCode.ERR_009, "工作人员不存在"));

        if (!isActiveStaffStatus(staff.status())) {
            throw new BusinessException(ErrorCode.ERR_009, "工作人员账号不可用");
        }

        dao.transactionManager().execute(connection -> {
            dao.loginCertificateStateDao().ensureExists(connection, STAFF_CERTIFICATE_SUBJECT, staff.username());
            dao.loginCertificateStateDao().markVerified(connection, STAFF_CERTIFICATE_SUBJECT, staff.username(), LocalDateTime.now());
            return null;
        });

        String authToken = staffAuthTokenService.issueToken(staff.staffId(), staff.username());
        return StaffLoginResponse.builder()
                .staffId(staff.staffId())
                .username(staff.username())
                .status(staff.status())
                .requiresCertificate(false)
                .authToken(authToken)
                .build();
    }

    @Override
    public StaffStatusResponse deactivateStaff(DeactivateStaffRequest request) {
        StaffStatusResponse response = dao.transactionManager().execute(connection -> {
            var targetStaff = dao.staffDao().findById(request.getTargetStaffId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ERR_009, "工作人员不存在: " + request.getTargetStaffId()));

            if (!isActiveStaffStatus(targetStaff.status())) {
                throw new BusinessException(ErrorCode.ERR_011, "工作人员当前已不是正常状态");
            }

            dao.staffDao().update(connection, new DomainModels.Staff(
                    targetStaff.staffId(),
                    targetStaff.username(),
                    targetStaff.passwordHash(),
                    normalizedDisabledStatus(targetStaff.status()),
                    targetStaff.createdAt()
            ));

            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    request.getOperatorStaffId(),
                    "停用工作人员",
                    "STAFF",
                    String.valueOf(targetStaff.staffId()),
                    "停用工作人员 username=" + targetStaff.username()
                            + ", reason=" + (request.getReason() == null ? "" : request.getReason()),
                    LocalDateTime.now()
            ));

            return StaffStatusResponse.builder()
                    .staffId(targetStaff.staffId())
                    .username(targetStaff.username())
                    .status(normalizedDisabledStatus(targetStaff.status()))
                    .build();
        });

        staffAuthTokenService.invalidateByStaffId(request.getTargetStaffId());
        return response;
    }

    @Override
    public void changePassword(ChangeStaffPasswordRequest request) {
        dao.transactionManager().execute(connection -> {
            var staff = dao.staffDao().findById(request.getStaffId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ERR_009, "工作人员不存在: " + request.getStaffId()));

            if (!isActiveStaffStatus(staff.status())) {
                throw new BusinessException(ErrorCode.ERR_009, "工作人员账号不可用");
            }
            if (!PasswordUtil.verify(request.getOldPassword(), staff.passwordHash())) {
                throw new BusinessException(ErrorCode.ERR_004, "原密码错误");
            }

            dao.staffDao().update(connection, new DomainModels.Staff(
                    staff.staffId(),
                    staff.username(),
                    PasswordUtil.hash(request.getNewPassword()),
                    staff.status(),
                    staff.createdAt()
            ));

            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    request.getStaffId(),
                    "修改工作人员密码",
                    "STAFF",
                    String.valueOf(staff.staffId()),
                    "工作人员修改登录密码",
                    LocalDateTime.now()
            ));
            return null;
        });
    }

    private DomainModels.LoginCertificateState ensureCertificateState(String subjectType, String subjectKey) {
        return dao.transactionManager().execute(connection -> {
            dao.loginCertificateStateDao().ensureExists(connection, subjectType, subjectKey);
            return dao.loginCertificateStateDao().findBySubjectForUpdate(connection, subjectType, subjectKey)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "certificate state not found"));
        });
    }

    private boolean isActiveStaffStatus(String status) {
        return STAFF_STATUS_NORMAL.equals(status) || STAFF_STATUS_NORMAL_LEGACY.equals(status);
    }

    private String normalizedDisabledStatus(String currentStatus) {
        return STAFF_STATUS_NORMAL_LEGACY.equals(currentStatus) ? STAFF_STATUS_DISABLED_LEGACY : STAFF_STATUS_DISABLED;
    }
}
