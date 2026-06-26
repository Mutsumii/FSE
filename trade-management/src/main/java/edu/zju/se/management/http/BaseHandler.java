package edu.zju.se.management.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import edu.zju.se.management.util.JsonUtil;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public abstract class BaseHandler implements HttpHandler {
    @Override
    public final void handle(HttpExchange exchange) throws IOException {
        addCors(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try {
            route(exchange);
        } catch (NotFoundException e) {
            sendJson(exchange, 404, ApiResponse.fail(e.getMessage()));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, ApiResponse.fail(e.getMessage()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            sendJson(exchange, 400, ApiResponse.fail("JSON 请求格式错误"));
        } catch (SecurityException e) {
            sendJson(exchange, 401, ApiResponse.fail(e.getMessage()));
        } catch (IllegalStateException e) {
            sendJson(exchange, 503, ApiResponse.fail(e.getMessage()));
        } catch (java.net.ConnectException e) {
            sendJson(exchange, 502, ApiResponse.fail("外部系统连接失败: " + e.getMessage()));
        } catch (java.io.IOException e) {
            sendJson(exchange, 502, ApiResponse.fail(e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 500, ApiResponse.fail("交易管理系统暂时不可用"));
        }
    }

    protected abstract void route(HttpExchange exchange) throws Exception;

    protected void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = JsonUtil.bytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    protected Map<String, String> queryParams(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return params;
        }
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            String value = parts.length > 1 ? decode(parts[1]) : "";
            params.put(key, value);
        }
        return params;
    }

    protected String bearerToken(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new SecurityException("缺少 Authorization Bearer token");
        }
        return auth.substring("Bearer ".length()).trim();
    }

    protected String[] pathParts(HttpExchange exchange) {
        return exchange.getRequestURI().getPath().split("/");
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type,Authorization");
    }
}
