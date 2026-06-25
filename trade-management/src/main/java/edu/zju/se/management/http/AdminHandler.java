package edu.zju.se.management.http;

import com.sun.net.httpserver.HttpExchange;
import edu.zju.se.management.model.Admin;
import edu.zju.se.management.model.Stock;
import edu.zju.se.management.repository.AdminRepository;
import edu.zju.se.management.repository.AuditRepository;
import edu.zju.se.management.repository.BlacklistRepository;
import edu.zju.se.management.repository.ReviewRepository;
import edu.zju.se.management.repository.StockRepository;
import edu.zju.se.management.service.AuthService;
import edu.zju.se.management.service.CentralTradingClient;
import edu.zju.se.management.util.JsonUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminHandler extends BaseHandler {
    private final AuthService authService;
    private final AdminRepository adminRepository;
    private final StockRepository stockRepository;
    private final BlacklistRepository blacklistRepository;
    private final ReviewRepository reviewRepository;
    private final AuditRepository auditRepository;
    private final CentralTradingClient centralTradingClient;

    public AdminHandler(AuthService authService, AdminRepository adminRepository, StockRepository stockRepository, BlacklistRepository blacklistRepository, ReviewRepository reviewRepository, AuditRepository auditRepository, CentralTradingClient centralTradingClient) {
        this.authService = authService;
        this.adminRepository = adminRepository;
        this.stockRepository = stockRepository;
        this.blacklistRepository = blacklistRepository;
        this.reviewRepository = reviewRepository;
        this.auditRepository = auditRepository;
        this.centralTradingClient = centralTradingClient;
    }

    @Override
    protected void route(HttpExchange exchange) throws Exception {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if ("POST".equals(method) && "/api/admin/login".equals(path)) {
            LoginRequest request = JsonUtil.read(exchange.getRequestBody(), LoginRequest.class);
            sendJson(exchange, 200, ApiResponse.ok(authService.login(request.username, request.password)));
            return;
        }

        if ("POST".equals(method) && "/api/admin/register".equals(path)) {
            RegisterRequest request = JsonUtil.read(exchange.getRequestBody(), RegisterRequest.class);
            if (request.confirmPassword == null || !request.confirmPassword.equals(request.password)) {
                throw new IllegalArgumentException("两次输入的密码不一致");
            }
            sendJson(exchange, 200, ApiResponse.ok(authService.register(request.username, request.password)));
            return;
        }

        Admin admin = authService.requireAdmin(bearerToken(exchange));
        String[] parts = pathParts(exchange);

        if ("GET".equals(method) && "/api/admin/stocks".equals(path)) {
            Object stocks;
            if (centralTradingClient.enabled()) {
                List<Map<String, Object>> centralStocks = centralTradingClient.getStocks();
                if (!"SUPER_ADMIN".equals(admin.role())) {
                    centralStocks.removeIf(stock -> {
                        try { return !stockRepository.canManage(admin.id(), admin.role(), stock.get("stockCode").toString()); }
                        catch (Exception e) { throw new RuntimeException(e); }
                    });
                }
                stocks = centralStocks;
            } else stocks = stockRepository.findStocksForAdmin(admin.id(), admin.role());
            sendJson(exchange, 200, ApiResponse.ok(stocks));
            return;
        }

        if ("GET".equals(method) && parts.length == 6 && "stocks".equals(parts[3]) && "orders".equals(parts[5])) {
            String stockCode = parts[4];
            requireStockAccess(admin, stockCode);
            if (centralTradingClient.enabled()) {
                sendJson(exchange, 200, ApiResponse.ok(centralTradingClient.getOrders(stockCode)));
                return;
            }
            Stock stock = stockRepository.findByCode(stockCode)
                    .orElseThrow(() -> new IllegalArgumentException("股票不存在"));
            Map<String, Object> data = new HashMap<>();
            data.put("stockCode", stock.stockCode());
            data.put("stockName", stock.stockName());
            data.put("lastPrice", stock.lastPrice());
            data.put("lastQuantity", stock.lastQuantity());
            data.put("status", stock.status());
            data.put("buyOrders", stockRepository.findOrders(stockCode, "BUY"));
            data.put("sellOrders", stockRepository.findOrders(stockCode, "SELL"));
            sendJson(exchange, 200, ApiResponse.ok(data));
            return;
        }

        if ("POST".equals(method) && parts.length == 6 && "stocks".equals(parts[3]) && "limit-rate".equals(parts[5])) {
            requireStockAccess(admin, parts[4]);
            LimitRateRequest request = JsonUtil.read(exchange.getRequestBody(), LimitRateRequest.class);
            if (centralTradingClient.enabled()) {
                centralTradingClient.setLimitRate(parts[4], request.stockType == null ? "NORMAL" : request.stockType, request.nextLimitRate);
            } else {
                stockRepository.updateLimitRate(parts[4], request.nextLimitRate);
            }
            auditRepository.log(admin, "SET_LIMIT_RATE", "STOCK", parts[4], request.nextLimitRate);
            sendJson(exchange, 200, ApiResponse.ok(Map.of("stockCode", parts[4], "nextLimitRate", request.nextLimitRate)));
            return;
        }

        if ("GET".equals(method) && "/api/admin/blacklist".equals(path)) {
            sendJson(exchange, 200, ApiResponse.ok(blacklistRepository.findAllActive()));
            return;
        }

        if ("GET".equals(method) && "/api/admin/reviews/pending".equals(path)) {
            var pending = reviewRepository.findPendingManual();
            if (!"SUPER_ADMIN".equals(admin.role())) {
                pending.removeIf(review -> {
                    try {
                        return !stockRepository.canManage(admin.id(), admin.role(), review.stockCode());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            sendJson(exchange, 200, ApiResponse.ok(pending));
            return;
        }

        if ("POST".equals(method) && parts.length == 6 && "reviews".equals(parts[3])) {
            var review = reviewRepository.findByReviewId(parts[4])
                    .orElseThrow(() -> new NotFoundException("审核记录不存在"));
            requireStockAccess(admin, review.stockCode());
            ManualReviewRequest request = JsonUtil.read(exchange.getRequestBody(), ManualReviewRequest.class);
            if ("approve".equals(parts[5])) {
                reviewRepository.decideManualReview(parts[4], true, request.reason, admin.username());
                auditRepository.log(admin, "APPROVE_REVIEW", "REVIEW", parts[4], request.reason);
                sendJson(exchange, 200, ApiResponse.ok(Map.of("reviewId", parts[4], "approved", true)));
                return;
            }
            if ("reject".equals(parts[5])) {
                reviewRepository.decideManualReview(parts[4], false, request.reason, admin.username());
                auditRepository.log(admin, "REJECT_REVIEW", "REVIEW", parts[4], request.reason);
                sendJson(exchange, 200, ApiResponse.ok(Map.of("reviewId", parts[4], "approved", false)));
                return;
            }
        }

        if ("POST".equals(method) && "/api/admin/blacklist".equals(path)) {
            BlacklistRequest request = JsonUtil.read(exchange.getRequestBody(), BlacklistRequest.class);
            if (request.idCardNo == null || request.idCardNo.isBlank() || request.idCardNo.trim().length() > 50) {
                throw new IllegalArgumentException("证件号码不能为空且长度不能超过 50");
            }
            if (request.userName == null || request.userName.isBlank()) {
                throw new IllegalArgumentException("缺少必填字段 userName");
            }
            if (request.reason == null || request.reason.isBlank()) {
                throw new IllegalArgumentException("加入黑名单时必须填写限制原因");
            }
            String idCardNo = request.idCardNo.trim().toUpperCase();
            Object entry = blacklistRepository.add(idCardNo, request.userName.trim(), request.fundAccountNo,
                    request.securityAccountNo, request.reason.trim());
            auditRepository.log(admin, "ADD_BLACKLIST", "INVESTOR", request.idCardNo, request.reason);
            sendJson(exchange, 200, ApiResponse.ok(entry));
            return;
        }

        if ("DELETE".equals(method) && parts.length == 5 && "blacklist".equals(parts[3])) {
            String idCardNo = parts[4].trim().toUpperCase();
            blacklistRepository.remove(idCardNo);
            auditRepository.log(admin, "REMOVE_BLACKLIST", "BLACKLIST", idCardNo, "移出黑名单");
            sendJson(exchange, 200, ApiResponse.ok(Map.of("removed", true)));
            return;
        }

        if ("POST".equals(method) && "/api/admin/password".equals(path)) {
            PasswordRequest request = JsonUtil.read(exchange.getRequestBody(), PasswordRequest.class);
            if (!edu.zju.se.management.util.PasswordUtil.verify(request.oldPassword, admin.passwordHash())) {
                throw new SecurityException("原密码错误");
            }
            if (request.newPassword == null || request.newPassword.length() < 6) {
                throw new IllegalArgumentException("新密码长度不能少于 6 位");
            }
            adminRepository.changePassword(admin.id(), request.newPassword);
            auditRepository.log(admin, "CHANGE_PASSWORD", "ADMIN", Long.toString(admin.id()), "修改密码");
            sendJson(exchange, 200, ApiResponse.ok(Map.of("changed", true)));
            return;
        }

        if ("DELETE".equals(method) && "/api/admin/account".equals(path)) {
            DeleteAccountRequest request = JsonUtil.read(exchange.getRequestBody(), DeleteAccountRequest.class);
            if (!edu.zju.se.management.util.PasswordUtil.verify(request.password, admin.passwordHash())) {
                throw new SecurityException("密码错误，无法注销账号");
            }
            auditRepository.log(admin, "DELETE_ACCOUNT", "ADMIN", Long.toString(admin.id()), "管理员注销账号");
            adminRepository.delete(admin.id(), admin.role());
            authService.revokeAdmin(admin.id());
            sendJson(exchange, 200, ApiResponse.ok(Map.of("deleted", true)));
            return;
        }

        if ("GET".equals(method) && "/api/admin/users".equals(path)) {
            requireSuperAdmin(admin);
            List<Map<String, Object>> users = adminRepository.findAllWithPermissions();
            if (centralTradingClient.enabled()) {
                var availableCodes = centralTradingClient.getStocks().stream()
                        .map(stock -> stock.get("stockCode").toString()).collect(java.util.stream.Collectors.toSet());
                for (Map<String, Object> user : users) {
                    if ("SUPER_ADMIN".equals(user.get("role"))) user.put("stockCodes", List.of());
                    else ((List<?>) user.get("stockCodes")).removeIf(code -> !availableCodes.contains(code.toString()));
                    user.put("availableStockCodes", availableCodes);
                }
            }
            sendJson(exchange, 200, ApiResponse.ok(users));
            return;
        }

        if ("POST".equals(method) && parts.length == 6 && "users".equals(parts[3]) && "permissions".equals(parts[5])) {
            requireSuperAdmin(admin);
            PermissionRequest request = JsonUtil.read(exchange.getRequestBody(), PermissionRequest.class);
            long targetAdminId = Long.parseLong(parts[4]);
            String role = "SUPER_ADMIN".equals(request.role) ? "SUPER_ADMIN" : "ADMIN";
            List<String> stockCodes = request.stockCodes == null ? List.of() : request.stockCodes;
            if (centralTradingClient.enabled() && !"SUPER_ADMIN".equals(role)) {
                var availableCodes = centralTradingClient.getStocks().stream()
                        .map(stock -> stock.get("stockCode").toString()).collect(java.util.stream.Collectors.toSet());
                if (!availableCodes.containsAll(stockCodes)) throw new IllegalArgumentException("授权股票必须来自中央交易系统");
            }
            adminRepository.replacePermissions(targetAdminId, role, stockCodes);
            auditRepository.log(admin, "UPDATE_PERMISSIONS", "ADMIN", parts[4], role + " " + request.stockCodes);
            sendJson(exchange, 200, ApiResponse.ok(Map.of("updated", true)));
            return;
        }

        if ("GET".equals(method) && "/api/admin/audit-logs".equals(path)) {
            requireSuperAdmin(admin);
            sendJson(exchange, 200, ApiResponse.ok(auditRepository.findRecent(200)));
            return;
        }


        sendJson(exchange, 404, ApiResponse.fail("接口不存在"));
    }

    public static class LoginRequest {
        public String username;
        public String password;
    }

    public static class RegisterRequest {
        public String username;
        public String password;
        public String confirmPassword;
    }

    public static class LimitRateRequest {
        public String nextLimitRate;
        public String stockType;
    }

    public static class BlacklistRequest {
        public String idCardNo;
        public String userName;
        public String fundAccountNo;
        public String securityAccountNo;
        public String reason;
    }

    public static class ManualReviewRequest {
        public String reason;
    }

    public static class PasswordRequest {
        public String oldPassword;
        public String newPassword;
    }

    public static class DeleteAccountRequest {
        public String password;
    }

    public static class PermissionRequest {
        public String role;
        public List<String> stockCodes;
    }

    public static class AccountControlRequest {
        public String accountType;
        public String accountNo;
        public String freezeType;
        public String reason;
    }

    private void requireStockAccess(Admin admin, String stockCode) throws Exception {
        if (!stockRepository.canManage(admin.id(), admin.role(), stockCode)) {
            throw new SecurityException("无权管理股票 " + stockCode);
        }
    }

    private void requireSuperAdmin(Admin admin) {
        if (!"SUPER_ADMIN".equals(admin.role())) throw new SecurityException("需要超级管理员权限");
    }


    private void validateAccountControl(AccountControlRequest request) {
        if (!"FUND".equals(request.accountType) && !"SECURITY".equals(request.accountType))
            throw new IllegalArgumentException("accountType 只能是 FUND 或 SECURITY");
        if (request.accountNo == null || request.accountNo.isBlank()) throw new IllegalArgumentException("账户号不能为空");
        if (!"LOSS".equals(request.freezeType) && !"VIOLATION".equals(request.freezeType))
            throw new IllegalArgumentException("freezeType 只能是 LOSS 或 VIOLATION");
    }
}
