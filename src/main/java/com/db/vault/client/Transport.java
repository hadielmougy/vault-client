package com.db.vault.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/** HTTP with authentication, timeouts, retries and error mapping. Internal. */
final class Transport {

    private static final Set<Integer> RETRYABLE = Set.of(429, 500, 502, 503, 504);

    private final HttpClient http;
    private final String baseUrl;
    private final Credentials credentials;
    private final RetryPolicy retry;
    private final Duration timeout;

    Transport(HttpClient http, String baseUrl, Credentials credentials, RetryPolicy retry, Duration timeout) {
        this.http = http;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.credentials = credentials;
        this.retry = retry;
        this.timeout = timeout;
    }

    JsonNode get(String path) {
        return send("GET", path, null);
    }

    JsonNode post(String path, JsonNode body) {
        return send("POST", path, body);
    }

    private JsonNode send(String method, String path, JsonNode body) {
        String payload = body == null ? null : Mapping.write(body);
        URI uri = URI.create(baseUrl + path);
        for (int attempt = 1; ; attempt++) {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .header("User-Agent", "document-store-client/0.1")
                    .header(credentials.headerName(), credentials.headerValue());
            if (payload != null) {
                request.header("Content-Type", "application/json; charset=utf-8")
                        .method(method, HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            } else {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> response;
            try {
                response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                if (attempt < retry.maxAttempts()) {
                    pause(retry.backoff(attempt));
                    continue;
                }
                throw new VaultClientException("Cannot reach the document store at " + uri + " after " + attempt
                        + " attempt(s): " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new VaultClientException("Interrupted while calling " + uri, e);
            }
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return Mapping.parse(response.body());
            }
            if (RETRYABLE.contains(status) && attempt < retry.maxAttempts()) {
                pause(retryAfter(response).orElse(retry.backoff(attempt)));
                continue;
            }
            throw error(status, response.body());
        }
    }

    private static java.util.Optional<Duration> retryAfter(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After").flatMap(v -> {
            try {
                return java.util.Optional.of(Duration.ofSeconds(Math.min(30, Long.parseLong(v.trim()))));
            } catch (NumberFormatException e) {
                return java.util.Optional.empty();
            }
        });
    }

    private static VaultApiException error(int status, String body) {
        try {
            JsonNode json = Mapping.parse(body);
            if (json.isObject() && json.has("code")) {
                return new VaultApiException(status, json.path("code").asText(), json.path("message").asText(),
                        Mapping.map(json.get("details")));
            }
        } catch (VaultClientException ignored) {
            // Not JSON: fall through.
        }
        String text = body == null ? "" : body.strip();
        return new VaultApiException(status, "HTTP_" + status, text.length() > 300 ? text.substring(0, 300) : text, Map.of());
    }

    private static void pause(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VaultClientException("Interrupted while waiting to retry", e);
        }
    }

    // ------------------------------------------------------------ URL building

    static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String query(Map<String, Object> params) {
        StringJoiner joiner = new StringJoiner("&", "?", "");
        joiner.setEmptyValue("");
        new LinkedHashMap<>(params).forEach((k, v) -> {
            if (v != null && !v.toString().isBlank()) {
                joiner.add(k + "=" + URLEncoder.encode(v.toString(), StandardCharsets.UTF_8));
            }
        });
        return joiner.toString();
    }
}
