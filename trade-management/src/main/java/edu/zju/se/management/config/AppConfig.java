package edu.zju.se.management.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record AppConfig(
        int serverPort,
        String dbUrl,
        String dbUser,
        String dbPassword,
        boolean centralEnabled,
        String centralApiBase,
        int authTokenMinutes,
        boolean accountEnabled,
        String accountApiBase,
        String accountStaffUsername,
        String accountStaffPassword
) {
    public static AppConfig load() throws IOException {
        Properties props = new Properties();
        Path configPath = Path.of("config.properties");
        if (Files.exists(configPath)) {
            try (InputStream in = Files.newInputStream(configPath)) {
                props.load(in);
            }
        }

        int port = Integer.parseInt(value(props, "server.port", "8081"));
        String dbUrl = value(props, "db.url", "jdbc:mysql://localhost:3306/stock_trade_management?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai");
        String dbUser = value(props, "db.user", "root");
        String dbPassword = value(props, "db.password", "");
        boolean centralEnabled = Boolean.parseBoolean(value(props, "central.enabled", "false"));
        String centralApiBase = value(props, "central.api-base", "http://localhost:8082");
        int authTokenMinutes = Integer.parseInt(value(props, "auth.token-minutes", "30"));
        boolean accountEnabled = Boolean.parseBoolean(value(props, "account.enabled", "false"));
        String accountApiBase = value(props, "account.api-base", "http://localhost:8080");
        String accountStaffUsername = value(props, "account.staff-username", "");
        String accountStaffPassword = value(props, "account.staff-password", "");
        return new AppConfig(port, dbUrl, dbUser, dbPassword, centralEnabled, centralApiBase, authTokenMinutes,
                accountEnabled, accountApiBase, accountStaffUsername, accountStaffPassword);
    }

    private static String value(Properties props, String key, String defaultValue) {
        String envKey = key.toUpperCase().replace('.', '_').replace('-', '_');
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return props.getProperty(key, defaultValue);
    }
}
