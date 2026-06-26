package edu.zju.se.management.store;

import edu.zju.se.management.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Database {
    private final AppConfig config;

    public Database(AppConfig config) {
        this.config = config;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(config.dbUrl(), config.dbUser(), config.dbPassword());
    }
}
