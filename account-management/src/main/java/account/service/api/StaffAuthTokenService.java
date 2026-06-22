package account.service.api;

public interface StaffAuthTokenService {

    String issueToken(int staffId, String username);

    AuthSession requireAccess(String authToken);

    void invalidateByStaffId(int staffId);

    record AuthSession(
            String authToken,
            int staffId,
            String username,
            long issuedAtEpochMilli,
            long expiresAtEpochMilli
    ) {
    }
}
