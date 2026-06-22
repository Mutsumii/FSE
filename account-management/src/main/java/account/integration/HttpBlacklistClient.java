package account.integration;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HttpBlacklistClient implements BlacklistClient {

    private static final Pattern SUCCESS_PATTERN = Pattern.compile("\"success\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATA_PATTERN = Pattern.compile("\"data\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");

    private final HttpClient httpClient;
    private final URI endpointBaseUri;
    private final Duration requestTimeout;

    public HttpBlacklistClient(HttpClient httpClient, URI endpointBaseUri, Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.endpointBaseUri = normalizeBaseUri(endpointBaseUri);
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    public static HttpBlacklistClient forBaseUrl(String baseUrl) {
        return new HttpBlacklistClient(
                HttpClient.newHttpClient(),
                URI.create(Objects.requireNonNull(baseUrl, "baseUrl")),
                Duration.ofSeconds(3)
        );
    }

    @Override
    public boolean isBlocked(String userName) {
        String normalizedUserName = normalizeUserName(userName);
        HttpRequest request = HttpRequest.newBuilder(buildCheckUri(normalizedUserName))
                .GET()
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BlacklistClientException("Blacklist API returned HTTP " + response.statusCode());
            }
            return parseResponse(response.body());
        } catch (IOException e) {
            throw new BlacklistClientException("Failed to call blacklist API", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BlacklistClientException("Blacklist API call interrupted", e);
        }
    }

    private URI buildCheckUri(String userName) {
        String separator = endpointBaseUri.toString().contains("?") ? "&" : "?";
        String encodedUserName = URLEncoder.encode(userName, StandardCharsets.UTF_8);
        return URI.create(endpointBaseUri + separator + "userName=" + encodedUserName);
    }

    private boolean parseResponse(String responseBody) {
        Matcher successMatcher = SUCCESS_PATTERN.matcher(responseBody);
        if (!successMatcher.find()) {
            throw new BlacklistClientException("Blacklist API response missing success field");
        }
        boolean success = Boolean.parseBoolean(successMatcher.group(1));
        if (!success) {
            throw new BlacklistClientException(extractMessage(responseBody));
        }
        Matcher dataMatcher = DATA_PATTERN.matcher(responseBody);
        if (!dataMatcher.find()) {
            throw new BlacklistClientException("Blacklist API response missing data field");
        }
        return Boolean.parseBoolean(dataMatcher.group(1));
    }

    private String extractMessage(String responseBody) {
        Matcher messageMatcher = MESSAGE_PATTERN.matcher(responseBody);
        if (messageMatcher.find()) {
            return messageMatcher.group(1);
        }
        return "Blacklist API call failed";
    }

    private static URI normalizeBaseUri(URI endpointBaseUri) {
        Objects.requireNonNull(endpointBaseUri, "endpointBaseUri");
        String value = endpointBaseUri.toString();
        if (!value.endsWith("/api/trade-management/blacklist/check")) {
            value = value.endsWith("/")
                    ? value + "api/trade-management/blacklist/check"
                    : value + "/api/trade-management/blacklist/check";
        }
        return URI.create(value);
    }

    private static String normalizeUserName(String userName) {
        Objects.requireNonNull(userName, "userName");
        String normalized = userName.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("userName must not be blank");
        }
        return normalized;
    }
}
