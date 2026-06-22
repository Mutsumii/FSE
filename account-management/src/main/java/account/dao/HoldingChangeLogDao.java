package account.dao;

import account.dao.core.JdbcExecutor;
import account.dao.core.RowMapper;
import account.dao.model.DomainModels.HoldingChangeLog;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

public final class HoldingChangeLogDao extends BaseJdbcDao {

    private static final String SELECT_COLUMNS = """
            select log_id, sec_acc_no, stock_code, stock_name, ref_order_id, change_type, quantity,
                   price, quantity_after, frozen_quantity_after, avg_cost_after, txn_time
              from holding_change_log
            """;

    private static final RowMapper<HoldingChangeLog> LOG_MAPPER = resultSet -> new HoldingChangeLog(
            resultSet.getLong("log_id"),
            resultSet.getString("sec_acc_no"),
            resultSet.getString("stock_code"),
            resultSet.getString("stock_name"),
            resultSet.getString("ref_order_id"),
            resultSet.getString("change_type"),
            resultSet.getInt("quantity"),
            resultSet.getBigDecimal("price"),
            resultSet.getInt("quantity_after"),
            resultSet.getInt("frozen_quantity_after"),
            resultSet.getBigDecimal("avg_cost_after"),
            getLocalDateTime(resultSet, "txn_time")
    );

    public HoldingChangeLogDao(JdbcExecutor executor) {
        super(executor);
    }

    public long create(Connection connection, HoldingChangeLog log) {
        String sql = """
                insert into holding_change_log (
                    sec_acc_no, stock_code, stock_name, ref_order_id, change_type, quantity,
                    price, quantity_after, frozen_quantity_after, avg_cost_after, txn_time
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        return executor.insertAndReturnKey(connection, sql, statement -> {
            statement.setString(1, log.secAccNo());
            statement.setString(2, log.stockCode());
            statement.setString(3, log.stockName());
            statement.setString(4, log.refOrderId());
            statement.setString(5, log.changeType());
            statement.setInt(6, log.quantity());
            statement.setBigDecimal(7, log.price());
            statement.setInt(8, log.quantityAfter());
            statement.setInt(9, log.frozenQuantityAfter());
            statement.setBigDecimal(10, log.avgCostAfter());
            statement.setTimestamp(11, toSqlTimestamp(defaultTimestamp(log.txnTime())));
        });
    }

    public boolean existsByRefOrderIdAndChangeTypeAndAccountAndStock(
            Connection connection,
            String refOrderId,
            String changeType,
            String secAccNo,
            String stockCode
    ) {
        if (refOrderId == null || refOrderId.isBlank()) {
            return false;
        }
        return executor.queryOne(
                connection,
                """
                select 1
                  from holding_change_log
                 where ref_order_id = ?
                   and change_type = ?
                   and sec_acc_no = ?
                   and stock_code = ?
                 limit 1
                """,
                statement -> {
                    statement.setString(1, refOrderId);
                    statement.setString(2, changeType);
                    statement.setString(3, secAccNo);
                    statement.setString(4, stockCode);
                },
                resultSet -> resultSet.getInt(1)
        ).isPresent();
    }

    public List<HoldingChangeLog> listRecentBySecurityAccountNo(String secAccNo, int limit) {
        return executor.queryList(
                SELECT_COLUMNS + " where sec_acc_no = ? order by txn_time desc, log_id desc limit ?",
                statement -> {
                    statement.setString(1, secAccNo);
                    statement.setInt(2, Math.max(limit, 1));
                },
                LOG_MAPPER
        );
    }

    public List<HoldingChangeLog> listByRefOrderId(String refOrderId) {
        if (refOrderId == null || refOrderId.isBlank()) {
            return List.of();
        }
        return executor.queryList(
                SELECT_COLUMNS + " where ref_order_id = ? order by txn_time desc, log_id desc",
                statement -> statement.setString(1, refOrderId),
                LOG_MAPPER
        );
    }

    private LocalDateTime defaultTimestamp(LocalDateTime value) {
        return value == null ? LocalDateTime.now() : value;
    }
}
