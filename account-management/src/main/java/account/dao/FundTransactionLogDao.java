package account.dao;

import account.dao.core.JdbcExecutor;
import account.dao.core.RowMapper;
import account.dao.model.DomainEnums.FundTransactionType;
import account.dao.model.DomainModels.FundTransactionLog;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

public final class FundTransactionLogDao extends BaseJdbcDao {

    private static final String SELECT_COLUMNS = """
            select log_id, fund_acc_no, txn_type, amount, available_after, frozen_after,
                   ref_order_id, operator_id, txn_time
              from fund_transaction_log
            """;

    private static final RowMapper<FundTransactionLog> LOG_MAPPER = resultSet -> new FundTransactionLog(
            resultSet.getLong("log_id"),
            resultSet.getString("fund_acc_no"),
            FundTransactionType.fromDbValue(resultSet.getString("txn_type")),
            resultSet.getBigDecimal("amount"),
            resultSet.getBigDecimal("available_after"),
            resultSet.getBigDecimal("frozen_after"),
            resultSet.getString("ref_order_id"),
            (Integer) resultSet.getObject("operator_id"),
            getLocalDateTime(resultSet, "txn_time")
    );

    public FundTransactionLogDao(JdbcExecutor executor) {
        super(executor);
    }

    public long create(Connection connection, FundTransactionLog log) {
        String sql = """
                insert into fund_transaction_log (
                    fund_acc_no, txn_type, amount, available_after, frozen_after,
                    ref_order_id, operator_id, txn_time
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        return executor.insertAndReturnKey(connection, sql, statement -> {
            statement.setString(1, log.fundAccNo());
            statement.setString(2, log.txnType().dbValue());
            statement.setBigDecimal(3, log.amount());
            statement.setBigDecimal(4, log.availableAfter());
            statement.setBigDecimal(5, log.frozenAfter());
            statement.setString(6, log.refOrderId());
            statement.setObject(7, log.operatorId());
            statement.setTimestamp(8, toSqlTimestamp(defaultTimestamp(log.txnTime())));
        });
    }

    public boolean existsByRefOrderIdAndTxnType(String refOrderId, FundTransactionType transactionType) {
        if (refOrderId == null || refOrderId.isBlank()) {
            return false;
        }
        return executor.queryOne(
                "select 1 from fund_transaction_log where ref_order_id = ? and txn_type = ? limit 1",
                statement -> {
                    statement.setString(1, refOrderId);
                    statement.setString(2, transactionType.dbValue());
                },
                resultSet -> resultSet.getInt(1)
        ).isPresent();
    }

    public boolean existsByRefOrderIdAndTxnType(Connection connection, String refOrderId, FundTransactionType transactionType) {
        if (refOrderId == null || refOrderId.isBlank()) {
            return false;
        }
        return executor.queryOne(
                connection,
                "select 1 from fund_transaction_log where ref_order_id = ? and txn_type = ? limit 1",
                statement -> {
                    statement.setString(1, refOrderId);
                    statement.setString(2, transactionType.dbValue());
                },
                resultSet -> resultSet.getInt(1)
        ).isPresent();
    }

    public List<FundTransactionLog> listRecentByFundAccountNo(String fundAccNo, int limit) {
        return executor.queryList(
                SELECT_COLUMNS + " where fund_acc_no = ? order by txn_time desc, log_id desc limit ?",
                statement -> {
                    statement.setString(1, fundAccNo);
                    statement.setInt(2, Math.max(limit, 1));
                },
                LOG_MAPPER
        );
    }

    private LocalDateTime defaultTimestamp(LocalDateTime value) {
        return value == null ? LocalDateTime.now() : value;
    }
}
