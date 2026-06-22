package account.dao;

import account.dao.core.JdbcExecutor;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

abstract class BaseJdbcDao {

    protected final JdbcExecutor executor;

    BaseJdbcDao(JdbcExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    protected static LocalDate getLocalDate(ResultSet resultSet, String column) throws SQLException {
        Date value = resultSet.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    protected static LocalDateTime getLocalDateTime(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    protected static Date toSqlDate(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    protected static Timestamp toSqlTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    protected static String likeKeyword(String keyword) {
        return "%" + keyword.trim() + "%";
    }
}
