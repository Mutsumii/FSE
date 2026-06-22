package account.dao.core;

import java.sql.PreparedStatement;
import java.sql.SQLException;

@FunctionalInterface
public interface StatementBinder {

    StatementBinder NONE = statement -> {
    };

    void bind(PreparedStatement statement) throws SQLException;

    static StatementBinder none() {
        return NONE;
    }
}
