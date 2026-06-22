package account.dao.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JdbcExecutor {

    private final ConnectionProvider connectionProvider;

    public JdbcExecutor(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    public <T> Optional<T> queryOne(String sql, StatementBinder binder, RowMapper<T> mapper) {
        try (Connection connection = connectionProvider.getConnection()) {
            return queryOne(connection, sql, binder, mapper);
        } catch (SQLException exception) {
            throw new DaoException("Failed to execute query: " + sql, exception);
        }
    }

    public <T> Optional<T> queryOne(Connection connection, String sql, StatementBinder binder, RowMapper<T> mapper) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(mapper, "mapper");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            safeBinder(binder).bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapper.map(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException exception) {
            throw new DaoException("Failed to execute query: " + sql, exception);
        }
    }

    public <T> List<T> queryList(String sql, StatementBinder binder, RowMapper<T> mapper) {
        try (Connection connection = connectionProvider.getConnection()) {
            return queryList(connection, sql, binder, mapper);
        } catch (SQLException exception) {
            throw new DaoException("Failed to execute query: " + sql, exception);
        }
    }

    public <T> List<T> queryList(Connection connection, String sql, StatementBinder binder, RowMapper<T> mapper) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(mapper, "mapper");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            safeBinder(binder).bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(mapper.map(resultSet));
                }
                return rows;
            }
        } catch (SQLException exception) {
            throw new DaoException("Failed to execute query: " + sql, exception);
        }
    }

    public int update(String sql, StatementBinder binder) {
        try (Connection connection = connectionProvider.getConnection()) {
            return update(connection, sql, binder);
        } catch (SQLException exception) {
            throw new DaoException("Failed to execute update: " + sql, exception);
        }
    }

    public int update(Connection connection, String sql, StatementBinder binder) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(sql, "sql");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            safeBinder(binder).bind(statement);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DaoException("Failed to execute update: " + sql, exception);
        }
    }

    public long insertAndReturnKey(Connection connection, String sql, StatementBinder binder) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(sql, "sql");
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            safeBinder(binder).bind(statement);
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                }
                throw new DaoException("Insert completed without generated key: " + sql, null);
            }
        } catch (SQLException exception) {
            throw new DaoException("Failed to execute insert: " + sql, exception);
        }
    }

    public ConnectionProvider connectionProvider() {
        return connectionProvider;
    }

    private StatementBinder safeBinder(StatementBinder binder) {
        return binder == null ? StatementBinder.none() : binder;
    }
}
