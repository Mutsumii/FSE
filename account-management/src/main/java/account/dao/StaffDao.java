package account.dao;

import account.dao.core.JdbcExecutor;
import account.dao.core.RowMapper;
import account.dao.model.DomainModels.Staff;
import java.sql.Connection;
import java.util.Optional;

public final class StaffDao extends BaseJdbcDao {

    private static final String SELECT_COLUMNS = """
            select staff_id, username, password_hash, status, created_at
              from staff
            """;

    private static final RowMapper<Staff> STAFF_MAPPER = resultSet -> new Staff(
            resultSet.getInt("staff_id"),
            resultSet.getString("username"),
            resultSet.getString("password_hash"),
            resultSet.getString("status"),
            getLocalDateTime(resultSet, "created_at")
    );

    public StaffDao(JdbcExecutor executor) {
        super(executor);
    }

    public Optional<Staff> findById(int staffId) {
        return executor.queryOne(
                SELECT_COLUMNS + " where staff_id = ?",
                statement -> statement.setInt(1, staffId),
                STAFF_MAPPER
        );
    }

    public Optional<Staff> findByUsername(String username) {
        return executor.queryOne(
                SELECT_COLUMNS + " where username = ?",
                statement -> statement.setString(1, username),
                STAFF_MAPPER
        );
    }

    public boolean update(Connection connection, Staff staff) {
        String sql = """
                update staff
                   set username = ?,
                       password_hash = ?,
                       status = ?
                 where staff_id = ?
                """;
        return executor.update(connection, sql, statement -> {
            statement.setString(1, staff.username());
            statement.setString(2, staff.passwordHash());
            statement.setString(3, staff.status());
            statement.setInt(4, staff.staffId());
        }) > 0;
    }
}
