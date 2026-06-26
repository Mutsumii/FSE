package edu.zju.se.management.repository;

import edu.zju.se.management.model.Admin;
import edu.zju.se.management.store.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuditRepository {
    private final Database database;

    public AuditRepository(Database database) { this.database = database; }

    public void log(Admin admin, String action, String targetType, String targetId, String detail) {
        String sql = "INSERT INTO audit_logs (admin_id, username, action, target_type, target_id, detail) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, admin.id()); ps.setString(2, admin.username()); ps.setString(3, action);
            ps.setString(4, targetType); ps.setString(5, targetId); ps.setString(6, detail); ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Audit log write failed: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> findRecent(int limit) throws SQLException {
        String sql = "SELECT id, username, action, target_type, target_id, detail, created_at FROM audit_logs ORDER BY id DESC LIMIT ?";
        List<Map<String, Object>> logs = new ArrayList<>();
        try (Connection conn = database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.min(Math.max(limit, 1), 500));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", rs.getLong("id")); item.put("username", rs.getString("username"));
                    item.put("action", rs.getString("action")); item.put("targetType", rs.getString("target_type"));
                    item.put("targetId", rs.getString("target_id")); item.put("detail", rs.getString("detail"));
                    item.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime()); logs.add(item);
                }
            }
        }
        return logs;
    }
}
