package edu.zju.se.management.http;

import com.sun.net.httpserver.HttpExchange;
import edu.zju.se.management.model.ReviewRequest;
import edu.zju.se.management.repository.BlacklistRepository;
import edu.zju.se.management.service.ReviewService;
import edu.zju.se.management.util.JsonUtil;

import java.util.Map;

public class ApiHandler extends BaseHandler {
    private final ReviewService reviewService;
    private final BlacklistRepository blacklistRepository;

    public ApiHandler(ReviewService reviewService, BlacklistRepository blacklistRepository) {
        this.reviewService = reviewService;
        this.blacklistRepository = blacklistRepository;
    }

    @Override
    protected void route(HttpExchange exchange) throws Exception {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if ("POST".equals(method) && "/api/trade-management/orders/review".equals(path)) {
            ReviewRequest request = JsonUtil.read(exchange.getRequestBody(), ReviewRequest.class);
            sendJson(exchange, 200, ApiResponse.ok(reviewService.review(request)));
            return;
        }

        if ("GET".equals(method) && "/api/trade-management/blacklist/check".equals(path)) {
            Map<String, String> params = queryParams(exchange);
            String idCardNo = params.get("idCardNo");
            String userName = params.get("userName");
            if ((idCardNo == null || idCardNo.isBlank()) && (userName == null || userName.isBlank())) {
                throw new IllegalArgumentException("缺少必填参数 idCardNo");
            }
            boolean blocked = idCardNo != null && !idCardNo.isBlank()
                    ? blacklistRepository.isBlacklistedByIdCard(idCardNo)
                    : blacklistRepository.isBlacklistedByUserName(userName);
            sendJson(exchange, 200, ApiResponse.ok(blocked));
            return;
        }

        if ("GET".equals(method) && path.startsWith("/api/trade-management/reviews/")) {
            String reviewId = path.substring("/api/trade-management/reviews/".length());
            sendJson(exchange, 200, ApiResponse.ok(reviewService.findResult(reviewId)));
            return;
        }

        if ("GET".equals(method) && path.startsWith("/api/trade-management/orders/review/")) {
            String reviewId = path.substring("/api/trade-management/orders/review/".length());
            sendJson(exchange, 200, ApiResponse.ok(reviewService.findResult(reviewId)));
            return;
        }

        sendJson(exchange, 404, ApiResponse.fail("接口不存在"));
    }
}
