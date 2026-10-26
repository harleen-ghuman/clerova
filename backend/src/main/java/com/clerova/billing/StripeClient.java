package com.clerova.billing;

import com.clerova.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Minimal Stripe REST client: form-encoded requests, JSON responses.
 */
@Component
public class StripeClient {

    private static final Logger log = LoggerFactory.getLogger(StripeClient.class);

    private final BillingProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public StripeClient(BillingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public JsonNode get(String path) {
        return send(HttpRequest.newBuilder(uri(path)).GET());
    }

    public JsonNode post(String path, Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));

        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private JsonNode send(HttpRequest.Builder builder) {
        HttpRequest request = builder
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + properties.getStripeSecretKey())
                .build();

        HttpResponse<String> response;

        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw unavailable("Could not reach Stripe", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("Couldn't reach Stripe. Please try again.", e);
        }

        JsonNode result = objectMapper.readTree(response.body());

        if (response.statusCode() >= 300) {
            String message = result.path("error").path("message").asString("unknown error");
            log.warn("Stripe {} {} failed: {}", request.method(), request.uri().getPath(), message);

            throw new ApiException(HttpStatus.BAD_GATEWAY, "BILLING_ERROR", "Stripe: " + message);
        }

        return result;
    }

    private URI uri(String path) {
        return URI.create(properties.getStripeApiUrl() + path);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static ApiException unavailable(String message, Exception cause) {
        log.warn("{}: {}", message, cause.toString());
        return new ApiException(HttpStatus.BAD_GATEWAY, "BILLING_ERROR", message);
    }
}
