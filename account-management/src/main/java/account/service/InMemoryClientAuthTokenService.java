package account.service;

import account.common.BusinessException;
import account.common.ErrorCode;
import account.service.api.ClientAuthTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryClientAuthTokenService implements ClientAuthTokenService {

    private final Map<String, AuthSession> sessions = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final Clock clock;

    @Autowired
    public InMemoryClientAuthTokenService(
            @Value("${account.auth-token.ttl-seconds:7200}") long ttlSeconds) {
        this(ttlSeconds, Clock.systemDefaultZone());
    }

    InMemoryClientAuthTokenService(long ttlSeconds, Clock clock) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("auth token ttl must be positive");
        }
        this.ttlMillis = ttlSeconds * 1000;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String issueToken(String fundAccNo, String secAccNo) {
        Objects.requireNonNull(fundAccNo, "fundAccNo");
        Objects.requireNonNull(secAccNo, "secAccNo");
        purgeExpiredSessions();
        invalidateByFundAccount(fundAccNo);

        long now = clock.millis();
        String token = PasswordUtil.generateAuthToken();
        sessions.put(token, new AuthSession(token, fundAccNo, secAccNo, now, now + ttlMillis));
        return token;
    }

    @Override
    public AuthSession requireFundAccess(String authToken, String fundAccNo) {
        AuthSession session = requireValidSession(authToken);
        if (!session.fundAccNo().equals(fundAccNo)) {
            throw invalidToken();
        }
        return session;
    }

    @Override
    public AuthSession requireSecurityAccess(String authToken, String secAccNo) {
        AuthSession session = requireValidSession(authToken);
        if (!session.secAccNo().equals(secAccNo)) {
            throw invalidToken();
        }
        return session;
    }

    @Override
    public void invalidateByFundAccount(String fundAccNo) {
        if (fundAccNo == null || fundAccNo.isBlank()) {
            return;
        }
        Iterator<Map.Entry<String, AuthSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, AuthSession> entry = iterator.next();
            if (fundAccNo.equals(entry.getValue().fundAccNo())) {
                iterator.remove();
            }
        }
    }

    private AuthSession requireValidSession(String authToken) {
        if (authToken == null || authToken.isBlank()) {
            throw invalidToken();
        }
        AuthSession session = sessions.get(authToken);
        if (session == null) {
            throw invalidToken();
        }
        if (session.expiresAtEpochMilli() <= clock.millis()) {
            sessions.remove(authToken);
            throw expiredToken();
        }
        return session;
    }

    private void purgeExpiredSessions() {
        long now = clock.millis();
        Iterator<Map.Entry<String, AuthSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, AuthSession> entry = iterator.next();
            if (entry.getValue().expiresAtEpochMilli() <= now) {
                iterator.remove();
            }
        }
    }

    private BusinessException invalidToken() {
        return new BusinessException(ErrorCode.ERR_018, "authToken 无效");
    }

    private BusinessException expiredToken() {
        return new BusinessException(ErrorCode.ERR_018, "authToken 已失效");
    }
}
