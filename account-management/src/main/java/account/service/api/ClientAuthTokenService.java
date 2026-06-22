package account.service.api;

public interface ClientAuthTokenService {

    String issueToken(String fundAccNo, String secAccNo);

    AuthSession requireFundAccess(String authToken, String fundAccNo);

    AuthSession requireSecurityAccess(String authToken, String secAccNo);

    void invalidateByFundAccount(String fundAccNo);

    record AuthSession(
            String authToken,
            String fundAccNo,
            String secAccNo,
            long issuedAtEpochMilli,
            long expiresAtEpochMilli
    ) {
    }
}
