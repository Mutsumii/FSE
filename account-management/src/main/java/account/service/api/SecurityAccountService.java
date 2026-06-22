package account.service.api;

import account.dto.AccountStatusResponse;
import account.dto.CloseSecurityAccountRequest;
import account.dto.CreateSecurityAccountRequest;
import account.dto.InvestorInfoResponse;
import account.dto.ReissueSecurityAccountRequest;
import account.dto.ReportSecurityLossRequest;
import account.dto.SecurityAccountCreatedResponse;
import account.dto.SecurityAccountListItemResponse;
import account.dto.SecurityHoldingUpdateResponse;
import account.dto.SecurityReissueResponse;
import account.dto.UpdateSecurityHoldingRequest;
import account.dto.SecuritySnapshotResponse;
import account.dto.UpdateInvestorInfoRequest;
import java.util.List;

public interface SecurityAccountService {

    SecuritySnapshotResponse getSecuritySnapshot(String secAccNo, String stockCode, String authToken);

    SecurityHoldingUpdateResponse updateSecurityHolding(UpdateSecurityHoldingRequest request);

    SecurityAccountCreatedResponse createSecurityAccount(CreateSecurityAccountRequest request);

    AccountStatusResponse reportSecurityLoss(ReportSecurityLossRequest request);

    SecurityReissueResponse reissueSecurityAccount(ReissueSecurityAccountRequest request);

    AccountStatusResponse closeSecurityAccount(CloseSecurityAccountRequest request);

    InvestorInfoResponse updateInvestorInfo(UpdateInvestorInfoRequest request);

    List<SecurityAccountListItemResponse> listAllSecurityAccounts();
}
