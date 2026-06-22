package edu.zju.se.management.repository;

import edu.zju.se.management.model.BlacklistEntry;
import edu.zju.se.management.store.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BlacklistRepository {
    private final Database database;

    public BlacklistRepository(Database database) {
        this.database = database;
    }

    public boolean isBlacklistedByIdCard(String idCardNo) throws SQLException {
        if (idCardNo == null || idCardNo.isBlank()) {
            return false;
        }
        String sql = "SELECT 1 FROM blacklist WHERE id_card_no = ? AND active = TRUE LIMIT 1";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, idCardNo.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean isBlacklistedByUserName(String userName) throws SQLException {
        if (userName == null || userName.isBlank()) return false;
        String sql = "SELECT 1 FROM blacklist WHERE user_name = ? AND active = TRUE LIMIT 1";
        try (Connection conn = database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userName.trim());
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public List<BlacklistEntry> findAllActive() throws SQLException {
        String sql = "SELECT id_card_no, user_name, fund_account_no, security_account_no, reason, active, created_at " +
                "FROM blacklist WHERE active = TRUE ORDER BY created_at DESC";
        List<BlacklistEntry> entries = new ArrayList<>();
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                entries.add(map(rs));
            }
        }
        return entries;
    }

    public BlacklistEntry add(String idCardNo, String userName, String fundAccountNo, String securityAccountNo, String reason) throws SQLException {
        String sql = "INSERT INTO blacklist (id_card_no, user_name, fund_account_no, security_account_no, reason, active) VALUES (?, ?, ?, ?, ?, TRUE) " +
                "ON DUPLICATE KEY UPDATE user_name=VALUES(user_name), fund_account_no=VALUES(fund_account_no), security_account_no=VALUES(security_account_no), reason=VALUES(reason), active=TRUE, removed_at=NULL";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, idCardNo);
            ps.setString(2, userName);
            ps.setString(3, fundAccountNo);
            ps.setString(4, securityAccountNo);
            ps.setString(5, reason);
            ps.executeUpdate();
            return findByIdCard(idCardNo);
        }
    }

    public void remove(String idCardNo) throws SQLException {
        String sql = "UPDATE blacklist SET active = FALSE, removed_at = CURRENT_TIMESTAMP WHERE id_card_no = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, idCardNo);
            ps.executeUpdate();
        }
    }

    private BlacklistEntry findByIdCard(String idCardNo) throws SQLException {
        String sql = "SELECT id_card_no, user_name, fund_account_no, security_account_no, reason, active, created_at FROM blacklist WHERE id_card_no = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, idCardNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        }
        throw new SQLException("黑名单记录不存在");
    }

    private BlacklistEntry map(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new BlacklistEntry(
                rs.getString("id_card_no"),
                rs.getString("user_name"),
                rs.getString("fund_account_no"),
                rs.getString("security_account_no"),
                rs.getString("reason"),
                rs.getBoolean("active"),
                createdAt == null ? null : createdAt.toLocalDateTime()
        );
    }
}
