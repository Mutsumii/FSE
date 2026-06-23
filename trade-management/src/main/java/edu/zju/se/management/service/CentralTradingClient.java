package edu.zju.se.management.service;

import com.fasterxml.jackson.databind.JsonNode;
import edu.zju.se.management.config.AppConfig;
import edu.zju.se.management.util.JsonUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CentralTradingClient {
    private final boolean enabled;
    private final String baseUrl;
    private final HttpClient client;

    public CentralTradingClient(AppConfig config) {
        this.enabled = config.centralEnabled();
        this.baseUrl = config.centralApiBase().replaceAll("/+$", "");
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    public boolean enabled() {
        return enabled;
    }

    public List<Map<String, Object>> getStocks() throws IOException, InterruptedException {
        JsonNode root = request("GET", "/api/central-trading/stocks", null);
        List<Map<String, Object>> stocks = new ArrayList<>();
        for (JsonNode node : root) {
            Map<String, Object> stock = new HashMap<>();
            stock.put("stockCode", text(node, "stockCode"));
            stock.put("stockName", text(node, "stockName"));
            stock.put("lastPrice", decimalText(node, "latestPrice"));
            stock.put("lastQuantity", 0);
            stock.put("status", normalizeStatus(text(node, "tradeStatus")));
            stock.put("currentLimitRate", null);
            stock.put("nextLimitRate", null);
            stocks.add(stock);
        }
        return stocks;
    }

    public boolean stockExists(String stockCode) throws IOException, InterruptedException {
        JsonNode root = request("GET", "/api/central-trading/stocks", null);
        for (JsonNode node : root) {
            if (stockCode.equals(text(node, "stockCode"))) return true;
        }
        return false;
    }

    public Map<String, Object> getOrders(String stockCode) throws IOException, InterruptedException {
        JsonNode response = request("GET", "/api/central-trading/admin/stocks/" + stockCode + "/orders", null);
        JsonNode data = response.path("data");
        JsonNode stockResponse = request("GET", "/api/central-trading/stocks/" + stockCode, null);
        JsonNode stock = stockResponse.path("data");
        Map<String, Object> result = new HashMap<>();
        result.put("stockCode", stockCode);
        result.put("stockName", text(stock, "stockName"));
        result.put("lastPrice", decimalText(stock, "latestPrice"));
        result.put("previousClose", decimalText(stock, "previousClose"));
        result.put("highestPrice", decimalText(stock, "highestPrice"));
        result.put("lowestPrice", decimalText(stock, "lowestPrice"));
        result.put("bidPrice", decimalText(stock, "bidPrice"));
        result.put("askPrice", decimalText(stock, "askPrice"));
        result.put("notice", text(stock, "notice"));
        result.put("quoteTime", text(stock, "quoteTime"));
        result.put("status", normalizeStatus(text(stock, "tradeStatus")));
        result.put("buyOrders", normalizeOrders(data.path("buyOrders")));
        result.put("sellOrders", normalizeOrders(data.path("sellOrders")));
        return result;
    }

    public void setLimitRate(String stockCode, String stockType, String limitRate) throws IOException, InterruptedException {
        request("POST", "/api/central-trading/admin/stocks/" + stockCode + "/price-limit",
                Map.of("stockType", stockType, "limitRate", limitRate));
    }

    private List<Map<String, Object>> normalizeOrders(JsonNode nodes) {
        List<Map<String, Object>> orders = new ArrayList<>();
        if (!nodes.isArray()) {
            return orders;
        }
        for (JsonNode node : nodes) {
            Map<String, Object> order = new HashMap<>();
            order.put("orderId", text(node, "orderId"));
            order.put("stockCode", text(node, "stockCode"));
            order.put("side", text(node, "side"));
            order.put("price", decimalText(node, "price"));
            order.put("quantity", node.path("remainingQuantity").asInt(node.path("quantity").asInt()));
            order.put("enteredAt", text(node, "entryTime"));
            order.put("status", text(node, "status"));
            orders.add(order);
        }
        return orders;
    }

    private JsonNode request(String method, String path, Object body) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json");
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.string(body)));
        } else {
            builder.GET();
        }
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("中央交易系统返回 HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode json = JsonUtil.tree(response.body());
        if (json.isObject() && json.has("success") && !json.path("success").asBoolean()) {
            throw new IOException(json.path("message").asText("中央交易系统调用失败"));
        }
        return json;
    }

    private String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : "";
    }

    private Object decimalText(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).decimalValue() : null;
    }

    private String normalizeStatus(String status) {
        return "SUSPENDED".equalsIgnoreCase(status) || "PAUSED".equalsIgnoreCase(status) || "暂停交易".equals(status)
                ? "PAUSED" : "TRADING";
    }
}
