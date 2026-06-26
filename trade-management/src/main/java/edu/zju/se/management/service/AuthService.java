package edu.zju.se.management.service;

import edu.zju.se.management.model.Admin;
import edu.zju.se.management.repository.AdminRepository;
import edu.zju.se.management.util.PasswordUtil;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthService {
    private final AdminRepository adminRepository;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final long tokenSeconds;

    public AuthService(AdminRepository adminRepository, int tokenMinutes) {
        this.adminRepository = adminRepository;
        this.tokenSeconds = tokenMinutes * 60L;
    }

    public LoginResult login(String username, String password) throws SQLException {
        Optional<Admin> admin = adminRepository.findByUsername(username);
        if (admin.isEmpty() || !PasswordUtil.verify(password, admin.get().passwordHash())) {
            throw new SecurityException("用户名或密码错误");
        }
        if (PasswordUtil.isLegacy(admin.get().passwordHash())) {
            adminRepository.changePassword(admin.get().id(), password);
            admin = adminRepository.findByUsername(username);
        }
        return createSession(admin.get());
    }

    public LoginResult register(String username, String password) throws SQLException {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于 6 位");
        }
        Admin admin = adminRepository.create(username.trim(), password, "ADMIN");
        return createSession(admin);
    }

    public Admin requireAdmin(String token) {
        Session session = sessions.get(token);
        if (session == null || Instant.now().isAfter(session.expiresAt())) {
            sessions.remove(token);
            throw new SecurityException("登录已失效，请重新登录");
        }
        return session.admin();
    }

    public void revokeAdmin(long adminId) {
        sessions.entrySet().removeIf(entry -> entry.getValue().admin().id() == adminId);
    }

    private LoginResult createSession(Admin admin) {
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plusSeconds(tokenSeconds);
        sessions.put(token, new Session(admin, expiresAt));
        return new LoginResult(token, admin.username(), admin.role(), expiresAt.toString());
    }

    private record Session(Admin admin, Instant expiresAt) {}

    public record LoginResult(String token, String username, String role, String expiresAt) {
    }
}
