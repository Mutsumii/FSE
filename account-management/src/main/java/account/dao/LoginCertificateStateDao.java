package account.dao;

import account.dao.core.JdbcExecutor;
import account.dao.core.RowMapper;
import account.dao.model.DomainModels.LoginCertificateState;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Optional;

public final class LoginCertificateStateDao extends BaseJdbcDao {

    private static final String SELECT_COLUMNS = """
            select state_id, subject_type, subject_key, certificate_verified, verified_at, created_at, updated_at
              from login_certificate_state
            """;

    private static final RowMapper<LoginCertificateState> MAPPER = resultSet -> new LoginCertificateState(
            resultSet.getLong("state_id"),
            resultSet.getString("subject_type"),
            resultSet.getString("subject_key"),
            resultSet.getBoolean("certificate_verified"),
            getLocalDateTime(resultSet, "verified_at"),
            getLocalDateTime(resultSet, "created_at"),
            getLocalDateTime(resultSet, "updated_at")
    );

    public LoginCertificateStateDao(JdbcExecutor executor) {
        super(executor);
    }

    public Optional<LoginCertificateState> findBySubject(String subjectType, String subjectKey) {
        return executor.queryOne(
                SELECT_COLUMNS + " where subject_type = ? and subject_key = ?",
                statement -> {
                    statement.setString(1, subjectType);
                    statement.setString(2, subjectKey);
                },
                MAPPER
        );
    }

    public Optional<LoginCertificateState> findBySubjectForUpdate(Connection connection, String subjectType, String subjectKey) {
        return executor.queryOne(
                connection,
                SELECT_COLUMNS + " where subject_type = ? and subject_key = ? for update",
                statement -> {
                    statement.setString(1, subjectType);
                    statement.setString(2, subjectKey);
                },
                MAPPER
        );
    }

    public boolean create(Connection connection, String subjectType, String subjectKey) {
        String sql = """
                insert into login_certificate_state (
                    subject_type, subject_key, certificate_verified, verified_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?)
                """;
        LocalDateTime now = LocalDateTime.now();
        return executor.update(connection, sql, statement -> {
            statement.setString(1, subjectType);
            statement.setString(2, subjectKey);
            statement.setBoolean(3, false);
            statement.setTimestamp(4, null);
            statement.setTimestamp(5, toSqlTimestamp(now));
            statement.setTimestamp(6, toSqlTimestamp(now));
        }) > 0;
    }

    public boolean markVerified(Connection connection, String subjectType, String subjectKey, LocalDateTime verifiedAt) {
        String sql = """
                update login_certificate_state
                   set certificate_verified = ?,
                       verified_at = ?,
                       updated_at = ?
                 where subject_type = ? and subject_key = ?
                """;
        LocalDateTime now = verifiedAt == null ? LocalDateTime.now() : verifiedAt;
        return executor.update(connection, sql, statement -> {
            statement.setBoolean(1, true);
            statement.setTimestamp(2, toSqlTimestamp(now));
            statement.setTimestamp(3, toSqlTimestamp(now));
            statement.setString(4, subjectType);
            statement.setString(5, subjectKey);
        }) > 0;
    }

    public void ensureExists(Connection connection, String subjectType, String subjectKey) {
        if (findBySubjectForUpdate(connection, subjectType, subjectKey).isEmpty()) {
            create(connection, subjectType, subjectKey);
        }
    }
}
