package edu.zju.se.management.repository;

import edu.zju.se.management.model.Admin;
import edu.zju.se.management.store.Database;
import edu.zju.se.management.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AdminRepository {
    private final Database database;

    public AdminRepository(Database database) {
        this.database = database;
    }

    public Optional<Admin> findByUsername(String username) throws SQLException {
        String sql = "SELECT id, username, password_hash, role FROM admins WHERE username = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Admin(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("role")
                ));
            }
        }
    }

    public void changePassword(long adminId, String newPassword) throws SQLException {
        String sql = "UPDATE admins SET password_hash = ? WHERE id = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, PasswordUtil.hash(newPassword));
            ps.setLong(2, adminId);
            ps.executeUpdate();
        }
    }

    public Admin create(String username, String password, String role) throws SQLException {
        String sql = "INSERT INTO admins (username, password_hash, role) VALUES (?, ?, ?)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, PasswordUtil.hash(password));
            ps.setString(3, role);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return findByUsername(username).orElseThrow();
                }
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new IllegalArgumentException("用户名已存在");
        }
        throw new SQLException("管理员注册失败");
    }

    public List<Map<String, Object>> findAllWithPermissions() throws SQLException {
        String sql = "SELECT a.id, a.username, a.role, p.stock_code FROM admins a " +
                "LEFT JOIN admin_stock_permissions p ON p.admin_id = a.id ORDER BY a.id, p.stock_code";
        Map<Long, Map<String, Object>> grouped = new java.util.LinkedHashMap<>();
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long id = rs.getLong("id");
                Map<String, Object> item = grouped.computeIfAbsent(id, key -> {
                    Map<String, Object> value = new HashMap<>();
                    value.put("id", key);
                    try {
                        value.put("username", rs.getString("username"));
                        value.put("role", rs.getString("role"));
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    value.put("stockCodes", new ArrayList<String>());
                    return value;
                });
                String stockCode = rs.getString("stock_code");
                if (stockCode != null) {
                    ((List<String>) item.get("stockCodes")).add(stockCode);
                }
            }
        }
        return new ArrayList<>(grouped.values());
    }

    public void delete(long adminId, String role) throws SQLException {
        try (Connection conn = database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if ("SUPER_ADMIN".equals(role)) {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM admins WHERE role = 'SUPER_ADMIN' FOR UPDATE");
                         ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) <= 1) {
                            throw new IllegalArgumentException("不能注销系统中最后一个超级管理员");
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM admin_stock_permissions WHERE admin_id = ?")) {
                    ps.setLong(1, adminId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM admins WHERE id = ?")) {
                    ps.setLong(1, adminId);
                    if (ps.executeUpdate() == 0) {
                        throw new IllegalArgumentException("管理员账号不存在");
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public void replacePermissions(long adminId, String role, List<String> stockCodes) throws SQLException {
        try (Connection conn = database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement rolePs = conn.prepareStatement("UPDATE admins SET role = ? WHERE id = ?");
                 PreparedStatement deletePs = conn.prepareStatement("DELETE FROM admin_stock_permissions WHERE admin_id = ?");
                 PreparedStatement stockPs = conn.prepareStatement("INSERT INTO stocks (stock_code, stock_name) VALUES (?, ?) ON DUPLICATE KEY UPDATE stock_code=VALUES(stock_code)");
                 PreparedStatement insertPs = conn.prepareStatement("INSERT INTO admin_stock_permissions (admin_id, stock_code) VALUES (?, ?)")) {
                rolePs.setString(1, role);
                rolePs.setLong(2, adminId);
                rolePs.executeUpdate();
                deletePs.setLong(1, adminId);
                deletePs.executeUpdate();
                if (!"SUPER_ADMIN".equals(role)) {
                    for (String stockCode : stockCodes.stream().map(String::trim).filter(code -> code.matches("\\d{6}")).distinct().toList()) {
                        stockPs.setString(1, stockCode);
                        stockPs.setString(2, "股票 " + stockCode);
                        stockPs.addBatch();
                        insertPs.setLong(1, adminId);
                        insertPs.setString(2, stockCode);
                        insertPs.addBatch();
                    }
                    stockPs.executeBatch();
                    insertPs.executeBatch();
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}
