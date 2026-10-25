package com.clerova.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends email through Postmark's API.
 * See https://postmarkapp.com/developer/api/email-api
 */
@Component
public class PostmarkEmailSender implements EmailSender {

    private final URI apiUrl;
    private final EmailProperties emailProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public PostmarkEmailSender(
            EmailProperties emailProperties,
            ObjectMapper objectMapper,
            @Value("${app.email.postmark-api-url}") String apiBaseUrl
    ) {
        this.apiUrl = URI.create(apiBaseUrl.replaceAll("/+$", "") + "/email");
        this.emailProperties = emailProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String send(OutgoingEmail email) {
        Map<String, Object> body = new LinkedHashMap<>();

        body.put("From", email.from());
        body.put("To", email.to());
        body.put("ReplyTo", email.replyTo());
        body.put("Subject", email.subject());
        body.put("TextBody", email.textBody());
        body.put("MessageStream", "outbound");

        List<Map<String, String>> headers = new ArrayList<>();

        if (email.inReplyTo() != null) {
            headers.add(Map.of("Name", "In-Reply-To", "Value", email.inReplyTo()));
        }

        if (email.references() != null) {
            headers.add(Map.of("Name", "References", "Value", email.references()));
        }

        if (!headers.isEmpty()) {
            body.put("Headers", headers);
        }

        HttpRequest request = HttpRequest.newBuilder(apiUrl)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("X-Postmark-Server-Token", emailProperties.getPostmarkServerToken())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response;

        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new EmailSendException("Could not reach the email service", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmailSendException("Sending was interrupted", e);
        }

        JsonNode result = objectMapper.readTree(response.body());

        if (response.statusCode() != 200 || result.path("ErrorCode").asInt(-1) != 0) {
            throw new EmailSendException(
                    "The email service rejected the message: " + result.path("Message").asString("unknown error"),
                    null
            );
        }

        return result.path("MessageID").asString(null);
    }
}
