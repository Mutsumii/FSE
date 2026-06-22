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
import java.util.Map;

public class AccountManagementClient {
    private final boolean enabled;
    private final String baseUrl;
    private final String username;
    private final String password;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private volatile String staffToken;

    public AccountManagementClient(AppConfig config) {
        enabled = config.accountEnabled();
        baseUrl = config.accountApiBase().replaceAll("/+$", "");
        username = config.accountStaffUsername();
        password = config.accountStaffPassword();
    }

    public boolean enabled() { return enabled; }

    public void freeze(String accountType, String accountNo, String freezeType, String reason) throws IOException, InterruptedException {
        operate("/api/admin/accounts/freeze", accountType, accountNo, freezeType, reason);
    }

    public void unfreeze(String accountType, String accountNo, String freezeType, String reason) throws IOException, InterruptedException {
        operate("/api/admin/accounts/unfreeze", accountType, accountNo, freezeType, reason);
    }

    private void operate(String path, String accountType, String accountNo, String freezeType, String reason) throws IOException, InterruptedException {
        if (!enabled) throw new IllegalStateException("账户系统联调未开启");
        JsonNode response = post(path, Map.of(
                "account_type", accountType,
                "account_no", accountNo,
                "freeze_type", freezeType,
                "reason", reason == null ? "" : reason
        ), token());
        ensureSuccess(response);
    }

    private String token() throws IOException, InterruptedException {
        if (staffToken != null && !staffToken.isBlank()) return staffToken;
        if (username.isBlank() || password.isBlank()) throw new IllegalStateException("缺少账户系统工作人员账号配置");
        JsonNode response = post("/api/internal/staff/login", Map.of("username", username, "password", password), null);
        ensureSuccess(response);
        JsonNode tokenNode = response.hasNonNull("auth_token") ? response.get("auth_token") : response.path("data").path("auth_token");
        if (tokenNode.isMissingNode() || tokenNode.asText().isBlank()) throw new IOException("账户系统登录响应缺少 auth_token");
        staffToken = tokenNode.asText();
        return staffToken;
    }

    private JsonNode post(String path, Object body, String token) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.string(body)));
        if (token != null) builder.header("X-Staff-Auth-Token", token);
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            if (response.statusCode() == 401 || response.statusCode() == 403) staffToken = null;
            throw new IOException("账户系统返回 HTTP " + response.statusCode() + ": " + response.body());
        }
        return JsonUtil.tree(response.body());
    }

    private void ensureSuccess(JsonNode response) throws IOException {
        if (response.has("success") && !response.path("success").asBoolean()) {
            throw new IOException(response.path("message").asText("账户系统调用失败"));
        }
        if (response.has("code") && response.path("code").asInt() != 0) {
            throw new IOException(response.path("message").asText("账户系统调用失败"));
        }
    }
}
