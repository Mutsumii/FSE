package edu.zju.se.management.repository;

import edu.zju.se.management.model.ReviewRequest;
import edu.zju.se.management.model.ReviewRecord;
import edu.zju.se.management.model.ReviewResult;
import edu.zju.se.management.store.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReviewRepository {
    private final Database database;

    public ReviewRepository(Database database) {
        this.database = database;
    }

    public void save(ReviewRequest request, ReviewResult result) throws SQLException {
        String sql = "INSERT INTO trade_reviews " +
                "(review_id, order_id, account_id, fund_account_no, security_account_no, id_card_no, user_name, stock_code, stock_name, side, price, quantity, amount, approved, review_status, risk_level, reject_code, reason, client_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, request.reviewId);
            ps.setString(2, request.orderId);
            ps.setString(3, request.accountId);
            ps.setString(4, request.fundAccountNo);
            ps.setString(5, request.securityAccountNo);
            ps.setString(6, request.idCardNo);
            ps.setString(7, request.userName);
            ps.setString(8, request.stockCode);
            ps.setString(9, request.stockName);
            ps.setString(10, request.side);
            ps.setBigDecimal(11, request.price);
            ps.setInt(12, request.quantity);
            ps.setBigDecimal(13, request.amount);
            ps.setBoolean(14, result.approved());
            ps.setString(15, result.reviewStatus());
            ps.setString(16, result.riskLevel());
            ps.setString(17, result.rejectCode());
            ps.setString(18, result.reason() == null ? result.message() : result.reason());
            ps.setString(19, request.clientTime);
            ps.executeUpdate();
        }
    }

    public int countTodayApprovedByFundAccount(String fundAccountNo) throws SQLException {
        String sql = "SELECT COUNT(*) FROM trade_reviews " +
                "WHERE DATE(created_at) = CURRENT_DATE " +
                "AND fund_account_no = ? AND approved = TRUE";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fundAccountNo.trim());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public List<ReviewRecord> findPendingManual() throws SQLException {
        String sql = "SELECT * FROM trade_reviews WHERE review_status = 'PENDING_MANUAL' ORDER BY created_at ASC";
        List<ReviewRecord> records = new ArrayList<>();
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                records.add(map(rs));
            }
        }
        return records;
    }

    public Optional<ReviewRecord> findByReviewId(String reviewId) throws SQLException {
        String sql = "SELECT * FROM trade_reviews WHERE review_id = ?";
        try (Connection conn = database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reviewId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<ReviewRecord> findByOrderId(String orderId) throws SQLException {
        String sql = "SELECT * FROM trade_reviews WHERE order_id = ? ORDER BY id ASC LIMIT 1";
        try (Connection conn = database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public void decideManualReview(String reviewId, boolean approved, String reason, String adminUsername) throws SQLException {
        String sql = "UPDATE trade_reviews " +
                "SET approved = ?, review_status = ?, manual_decision = ?, manual_reason = ?, decided_by = ?, decided_at = CURRENT_TIMESTAMP " +
                "WHERE review_id = ? AND review_status = 'PENDING_MANUAL'";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, approved);
            ps.setString(2, approved ? "MANUAL_APPROVED" : "MANUAL_REJECTED");
            ps.setString(3, approved ? "APPROVED" : "REJECTED");
            ps.setString(4, reason);
            ps.setString(5, adminUsername);
            ps.setString(6, reviewId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("待核验委托不存在或已处理");
            }
        }
    }

    private ReviewRecord map(ResultSet rs) throws SQLException {
        return new ReviewRecord(
                rs.getLong("id"),
                rs.getString("review_id"),
                rs.getString("order_id"),
                rs.getString("account_id"),
                rs.getString("fund_account_no"),
                rs.getString("security_account_no"),
                rs.getString("id_card_no"),
                rs.getString("user_name"),
                rs.getString("stock_code"),
                rs.getString("stock_name"),
                rs.getString("side"),
                rs.getBigDecimal("price"),
                rs.getInt("quantity"),
                rs.getBigDecimal("amount"),
                rs.getBoolean("approved"),
                rs.getString("review_status"),
                rs.getString("risk_level"),
                rs.getString("reject_code"),
                rs.getString("reason"),
                rs.getString("client_time"),
                rs.getString("manual_decision"),
                rs.getString("manual_reason"),
                rs.getString("decided_by"),
                toLocalDateTime(rs.getTimestamp("decided_at")),
                toLocalDateTime(rs.getTimestamp("created_at"))
        );
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
