package account.dao.core;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public final class TransactionManager {

    private final ConnectionProvider connectionProvider;

    public TransactionManager(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    public <T> T execute(TransactionCallback<T> callback) {
        Objects.requireNonNull(callback, "callback");
        try (Connection connection = connectionProvider.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = callback.doInTransaction(connection);
                connection.commit();
                return result;
            } catch (SQLException exception) {
                rollbackQuietly(connection);
                throw new DaoException("Transaction failed and has been rolled back", exception);
            } catch (RuntimeException exception) {
                rollbackQuietly(connection);
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException exception) {
            throw new DaoException("Failed to open transaction", exception);
        }
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            throw new DaoException("Failed to roll back transaction", rollbackException);
        }
    }
}
