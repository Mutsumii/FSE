package account.service.api;

import account.dto.ChangeStaffPasswordRequest;
import account.dto.CompleteLoginCertificateRequest;
import account.dto.DeactivateStaffRequest;
import account.dto.StaffStatusResponse;
import account.dto.StaffLoginRequest;
import account.dto.StaffLoginResponse;

public interface StaffService {

    StaffLoginResponse staffLogin(StaffLoginRequest request);

    StaffLoginResponse completeLoginCertificate(CompleteLoginCertificateRequest request);

    StaffStatusResponse deactivateStaff(DeactivateStaffRequest request);

    void changePassword(ChangeStaffPasswordRequest request);
}
