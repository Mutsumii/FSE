package account.dao;

import account.dao.core.JdbcExecutor;
import account.dao.core.RowMapper;
import account.dao.model.DomainModels.OperationLog;
import account.dao.model.DomainModels.OperationLogQuery;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class OperationLogDao extends BaseJdbcDao {

    private static final String SELECT_COLUMNS = """
            select log_id, staff_id, operation_type, target_type, target_id, detail, operation_time
              from operation_log
            """;

    private static final RowMapper<OperationLog> LOG_MAPPER = resultSet -> new OperationLog(
            resultSet.getLong("log_id"),
            resultSet.getInt("staff_id"),
            resultSet.getString("operation_type"),
            resultSet.getString("target_type"),
            resultSet.getString("target_id"),
            resultSet.getString("detail"),
            getLocalDateTime(resultSet, "operation_time")
    );

    public OperationLogDao(JdbcExecutor executor) {
        super(executor);
    }

    public long create(Connection connection, OperationLog log) {
        // Investor-initiated actions have no staff operator. Their financial
        // transaction is still recorded in fund_transaction_log; staff-only
        // operation_log entries require a non-null staff_id by schema design.
        if (log.staffId() == null) {
            return 0L;
        }
        String sql = """
                insert into operation_log (
                    staff_id, operation_type, target_type, target_id, detail, operation_time
                ) values (?, ?, ?, ?, ?, ?)
                """;
        return executor.insertAndReturnKey(connection, sql, statement -> {
            statement.setInt(1, log.staffId());
            statement.setString(2, log.operationType());
            statement.setString(3, log.targetType());
            statement.setString(4, log.targetId());
            statement.setString(5, log.detail());
            statement.setTimestamp(6, toSqlTimestamp(defaultTimestamp(log.operationTime())));
        });
    }

    public List<OperationLog> query(OperationLogQuery query) {
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS).append(" where 1 = 1");
        List<Object> parameters = new ArrayList<>();

        if (query.staffId() != null) {
            sql.append(" and staff_id = ?");
            parameters.add(query.staffId());
        }
        if (query.fromTime() != null) {
            sql.append(" and operation_time >= ?");
            parameters.add(toSqlTimestamp(query.fromTime()));
        }
        if (query.toTime() != null) {
            sql.append(" and operation_time <= ?");
            parameters.add(toSqlTimestamp(query.toTime()));
        }
        if (query.operationType() != null && !query.operationType().isBlank()) {
            sql.append(" and operation_type = ?");
            parameters.add(query.operationType());
        }
        if (query.targetType() != null && !query.targetType().isBlank()) {
            sql.append(" and target_type = ?");
            parameters.add(query.targetType());
        }
        if (query.targetId() != null && !query.targetId().isBlank()) {
            sql.append(" and target_id = ?");
            parameters.add(query.targetId());
        }

        sql.append(" order by operation_time desc, log_id desc limit ? offset ?");
        parameters.add(query.safeLimit());
        parameters.add(query.safeOffset());

        return executor.queryList(sql.toString(), statement -> bindParameters(statement, parameters), LOG_MAPPER);
    }

    public boolean existsByOperationTypeAndDetailKeyword(String operationType, String detailKeyword) {
        if (detailKeyword == null || detailKeyword.isBlank()) {
            return false;
        }
        return executor.queryOne(
                "select 1 from operation_log where operation_type = ? and detail like ? limit 1",
                statement -> {
                    statement.setString(1, operationType);
                    statement.setString(2, likeKeyword(detailKeyword));
                },
                resultSet -> resultSet.getInt(1)
        ).isPresent();
    }

    public List<OperationLog> listRecent(int limit) {
        return executor.queryList(
                SELECT_COLUMNS + " order by operation_time desc, log_id desc limit ?",
                statement -> statement.setInt(1, limit),
                LOG_MAPPER
        );
    }

    private void bindParameters(java.sql.PreparedStatement statement, List<Object> parameters) throws java.sql.SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            statement.setObject(index + 1, parameters.get(index));
        }
    }

    private LocalDateTime defaultTimestamp(LocalDateTime value) {
        return value == null ? LocalDateTime.now() : value;
    }
}
