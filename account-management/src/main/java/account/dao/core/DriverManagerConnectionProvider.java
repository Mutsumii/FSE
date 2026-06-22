package account.dao.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

public final class DriverManagerConnectionProvider implements ConnectionProvider {

    private final String url;
    private final Properties properties;

    public DriverManagerConnectionProvider(String url, String username, String password) {
        this(url, buildProperties(username, password));
    }

    public DriverManagerConnectionProvider(String url, Properties properties) {
        this.url = Objects.requireNonNull(url, "url");
        this.properties = new Properties();
        this.properties.putAll(Objects.requireNonNull(properties, "properties"));
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, properties);
    }

    private static Properties buildProperties(String username, String password) {
        Properties properties = new Properties();
        properties.setProperty("user", Objects.requireNonNull(username, "username"));
        properties.setProperty("password", Objects.requireNonNull(password, "password"));
        return properties;
    }
}
