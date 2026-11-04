package com.clerova.trucking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Checks a broker's MC number against FMCSA's public data (the QCMobile
 * API). Off until FMCSA_WEB_KEY is set; then a failed lookup simply
 * means no check, never a wrong flag.
 */
@Component
public class FmcsaClient {

    private static final Logger log = LoggerFactory.getLogger(FmcsaClient.class);

    private final String webKey;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public FmcsaClient(
            @Value("${app.fmcsa.web-key:}") String webKey,
            @Value("${app.fmcsa.base-url:https://mobile.fmcsa.dot.gov/qc/services}") String baseUrl
    ) {
        this.webKey = webKey == null ? "" : webKey.trim();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public boolean isEnabled() {
        return !webKey.isEmpty();
    }

    /** The check for an MC number, or empty when it couldn't be done. */
    public Optional<BrokerCheck> check(String mcNumber) {
        if (!isEnabled() || mcNumber == null || !mcNumber.matches("\\d{1,8}")) {
            return Optional.empty();
        }

        try {
            JsonNode carriers = get("/carriers/docket-number/" + mcNumber).path("content");

            if (!carriers.isArray() || carriers.isEmpty()) {
                return Optional.of(new BrokerCheck(FmcsaStatus.NOT_FOUND, null, null));
            }

            JsonNode carrier = carriers.get(0).path("carrier");
            String legalName = text(carrier, "legalName");
            String dbaName = text(carrier, "dbaName");
            String dotNumber = text(carrier, "dotNumber");

            if (dotNumber == null) {
                return Optional.empty();
            }

            JsonNode authorities = get("/carriers/" + dotNumber + "/authority").path("content");
            boolean broker = false;

            for (JsonNode entry : authorities) {
                JsonNode authority = entry.path("carrierAuthority");
                broker |= "Y".equalsIgnoreCase(text(authority, "authorizedForBroker"))
                        || "A".equalsIgnoreCase(text(authority, "brokerAuthorityStatus"));
            }

            return Optional.of(new BrokerCheck(
                    broker ? FmcsaStatus.ACTIVE : FmcsaStatus.NOT_AUTHORIZED, legalName, dbaName));

        } catch (Exception e) {
            log.warn("FMCSA check failed for MC {}: {}", mcNumber, e.toString());
            return Optional.empty();
        }
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        baseUrl + path + "?webKey=" + URLEncoder.encode(webKey, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            return json.createObjectNode();
        }

        if (response.statusCode() != 200) {
            throw new IllegalStateException("FMCSA answered " + response.statusCode());
        }

        return json.readTree(response.body());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);

        if (value.isMissingNode() || value.isNull()) {
            return null;
        }

        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }
}
