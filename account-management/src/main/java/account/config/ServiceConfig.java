package account.config;

import account.dao.DaoRegistry;
import account.dao.core.ConnectionProvider;
import account.dao.core.DriverManagerConnectionProvider;
import account.integration.BlacklistClient;
import account.integration.HttpBlacklistClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring 配置：创建 DAO 层所需的 DaoRegistry Bean，
 * 并注入到所有 Service 实现中。
 */
@Configuration
public class ServiceConfig {

    @Value("${account.datasource.url:jdbc:mysql://localhost:3306/account_db?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf-8}")
    private String dbUrl;

    @Value("${account.datasource.username:root}")
    private String dbUsername;

    @Value("${account.datasource.password:root}")
    private String dbPassword;

    @Value("${account.blacklist.base-url:http://10.196.95.30:8081}")
    private String blacklistBaseUrl;

    @Bean
    public ConnectionProvider connectionProvider() {
        return new DriverManagerConnectionProvider(dbUrl, dbUsername, dbPassword);
    }

    @Bean
    public BlacklistClient blacklistClient() {
        return HttpBlacklistClient.forBaseUrl(blacklistBaseUrl);
    }

    @Bean
    public DaoRegistry daoRegistry(ConnectionProvider connectionProvider) {
        return new DaoRegistry(connectionProvider);
    }
}
