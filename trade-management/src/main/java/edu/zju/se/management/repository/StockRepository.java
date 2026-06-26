package edu.zju.se.management.repository;

import edu.zju.se.management.model.Stock;
import edu.zju.se.management.model.TradeOrder;
import edu.zju.se.management.store.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StockRepository {
    private final Database database;

    public StockRepository(Database database) {
        this.database = database;
    }

    public List<Stock> findStocksForAdmin(long adminId, String role) throws SQLException {
        String sql;
        if ("SUPER_ADMIN".equals(role)) {
            sql = "SELECT stock_code, stock_name, last_price, last_quantity, status, current_limit_rate, next_limit_rate " +
                    "FROM stocks ORDER BY stock_code";
            try (Connection conn = database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                return mapStocks(rs);
            }
        }

        sql = "SELECT s.stock_code, s.stock_name, s.last_price, s.last_quantity, s.status, s.current_limit_rate, s.next_limit_rate " +
                "FROM stocks s JOIN admin_stock_permissions p ON s.stock_code = p.stock_code " +
                "WHERE p.admin_id = ? ORDER BY s.stock_code";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, adminId);
            try (ResultSet rs = ps.executeQuery()) {
                return mapStocks(rs);
            }
        }
    }

    public Optional<Stock> findByCode(String stockCode) throws SQLException {
        String sql = "SELECT stock_code, stock_name, last_price, last_quantity, status, current_limit_rate, next_limit_rate FROM stocks WHERE stock_code = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stockCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapStock(rs));
                }
            }
        }
        return Optional.empty();
    }

    public boolean canManage(long adminId, String role, String stockCode) throws SQLException {
        if ("SUPER_ADMIN".equals(role)) return true;
        String sql = "SELECT 1 FROM admin_stock_permissions WHERE admin_id = ? AND stock_code = ? LIMIT 1";
        try (Connection conn = database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, adminId);
            ps.setString(2, stockCode);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public List<TradeOrder> findOrders(String stockCode, String side) throws SQLException {
        String orderBy = "BUY".equals(side)
                ? "price DESC, entered_at ASC"
                : "price ASC, entered_at ASC";
        String sql = "SELECT order_id, stock_code, side, price, quantity, entered_at, status FROM trade_orders " +
                "WHERE stock_code = ? AND side = ? AND status = 'PENDING' ORDER BY " + orderBy;
        List<TradeOrder> orders = new ArrayList<>();
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stockCode);
            ps.setString(2, side);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    orders.add(new TradeOrder(
                            rs.getString("order_id"),
                            rs.getString("stock_code"),
                            rs.getString("side"),
                            rs.getBigDecimal("price"),
                            rs.getInt("quantity"),
                            rs.getTimestamp("entered_at").toLocalDateTime(),
                            rs.getString("status")
                    ));
                }
            }
        }
        return orders;
    }

    public void updateLimitRate(String stockCode, String nextLimitRate) throws SQLException {
        String sql = "UPDATE stocks SET next_limit_rate = ? WHERE stock_code = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, new java.math.BigDecimal(nextLimitRate));
            ps.setString(2, stockCode);
            ps.executeUpdate();
        }
    }

    public void updateStatus(String stockCode, String status) throws SQLException {
        String sql = "UPDATE stocks SET status = ? WHERE stock_code = ?";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, stockCode);
            ps.executeUpdate();
        }
    }

    public boolean orderExists(String orderId) throws SQLException {
        String sql = "SELECT 1 FROM trade_reviews WHERE order_id = ? LIMIT 1";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private List<Stock> mapStocks(ResultSet rs) throws SQLException {
        List<Stock> stocks = new ArrayList<>();
        while (rs.next()) {
            stocks.add(mapStock(rs));
        }
        return stocks;
    }

    private Stock mapStock(ResultSet rs) throws SQLException {
        return new Stock(
                rs.getString("stock_code"),
                rs.getString("stock_name"),
                rs.getBigDecimal("last_price"),
                rs.getInt("last_quantity"),
                rs.getString("status"),
                rs.getBigDecimal("current_limit_rate"),
                rs.getBigDecimal("next_limit_rate")
        );
    }
}
