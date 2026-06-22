package account.dao.core;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface TransactionCallback<T> {

    T doInTransaction(Connection connection) throws SQLException;
}
