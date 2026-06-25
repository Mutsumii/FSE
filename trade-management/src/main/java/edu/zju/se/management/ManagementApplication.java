package edu.zju.se.management;

import com.sun.net.httpserver.HttpServer;
import edu.zju.se.management.config.AppConfig;
import edu.zju.se.management.http.AdminHandler;
import edu.zju.se.management.http.ApiHandler;
import edu.zju.se.management.http.StaticFileHandler;
import edu.zju.se.management.repository.AdminRepository;
import edu.zju.se.management.repository.AuditRepository;
import edu.zju.se.management.repository.BlacklistRepository;
import edu.zju.se.management.repository.ReviewRepository;
import edu.zju.se.management.repository.StockRepository;
import edu.zju.se.management.service.AuthService;
import edu.zju.se.management.service.CentralTradingClient;
import edu.zju.se.management.service.ReviewService;
import edu.zju.se.management.store.Database;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class ManagementApplication {
    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.load();
        Database database = new Database(config);

        AdminRepository adminRepository = new AdminRepository(database);
        StockRepository stockRepository = new StockRepository(database);
        BlacklistRepository blacklistRepository = new BlacklistRepository(database);
        ReviewRepository reviewRepository = new ReviewRepository(database);
        AuditRepository auditRepository = new AuditRepository(database);

        AuthService authService = new AuthService(adminRepository, config.authTokenMinutes());
        CentralTradingClient centralTradingClient = new CentralTradingClient(config);
        ReviewService reviewService = new ReviewService(stockRepository, blacklistRepository, reviewRepository, centralTradingClient);

        HttpServer server = HttpServer.create(new InetSocketAddress(config.serverPort()), 0);
        server.createContext("/api/admin", new AdminHandler(authService, adminRepository, stockRepository, blacklistRepository, reviewRepository, auditRepository, centralTradingClient));
        server.createContext("/api/trade-management", new ApiHandler(reviewService, blacklistRepository));
        server.createContext("/", new StaticFileHandler("web"));
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();

        System.out.println("Trade management server started at http://localhost:" + config.serverPort());
    }
}
