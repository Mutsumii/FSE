package account.service;

import account.common.BusinessException;
import account.common.ErrorCode;
import account.service.api.StaffAuthTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryStaffAuthTokenService implements StaffAuthTokenService {

    private final Map<String, AuthSession> sessions = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final Clock clock;

    @Autowired
    public InMemoryStaffAuthTokenService(
            @Value("${account.staff-auth-token.ttl-seconds:28800}") long ttlSeconds) {
        this(ttlSeconds, Clock.systemDefaultZone());
    }

    InMemoryStaffAuthTokenService(long ttlSeconds, Clock clock) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("staff auth token ttl must be positive");
        }
        this.ttlMillis = ttlSeconds * 1000;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String issueToken(int staffId, String username) {
        Objects.requireNonNull(username, "username");
        purgeExpiredSessions();
        invalidateByStaffId(staffId);

        long now = clock.millis();
        String token = PasswordUtil.generateAuthToken();
        sessions.put(token, new AuthSession(token, staffId, username, now, now + ttlMillis));
        return token;
    }

    @Override
    public AuthSession requireAccess(String authToken) {
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

    @Override
    public void invalidateByStaffId(int staffId) {
        Iterator<Map.Entry<String, AuthSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, AuthSession> entry = iterator.next();
            if (entry.getValue().staffId() == staffId) {
                iterator.remove();
            }
        }
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
        return new BusinessException(ErrorCode.ERR_018, "staff authToken 无效");
    }

    private BusinessException expiredToken() {
        return new BusinessException(ErrorCode.ERR_018, "staff authToken 已失效");
    }
}
