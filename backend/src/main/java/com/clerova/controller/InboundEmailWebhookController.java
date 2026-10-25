package com.clerova.controller;

import com.clerova.email.EmailProperties;
import com.clerova.email.InboundEmailService;
import com.clerova.email.PostmarkInboundParser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

/**
 * Receives forwarded emails from Postmark's inbound webhook. Postmark is
 * configured with a URL that carries basic auth credentials:
 * https://USER:PASSWORD@api.clerova-ai.com/api/inbound/postmark
 */
@RestController
@RequestMapping("/api/inbound")
public class InboundEmailWebhookController {

    private final EmailProperties emailProperties;
    private final PostmarkInboundParser parser;
    private final InboundEmailService inboundEmailService;

    public InboundEmailWebhookController(
            EmailProperties emailProperties,
            PostmarkInboundParser parser,
            InboundEmailService inboundEmailService
    ) {
        this.emailProperties = emailProperties;
        this.parser = parser;
        this.inboundEmailService = inboundEmailService;
    }

    @PostMapping("/postmark")
    public ResponseEntity<Map<String, String>> receivePostmark(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody JsonNode payload
    ) {
        if (!emailProperties.isInboundEnabled()) {
            return ResponseEntity.status(503).body(Map.of("status", "disabled"));
        }

        if (!isAuthorized(authorization)) {
            return ResponseEntity.status(401).body(Map.of("status", "unauthorized"));
        }

        InboundEmailService.ReceiveResult result =
                inboundEmailService.receive(parser.parse(payload));

        // Always 200 for authenticated deliveries so Postmark does not retry
        // emails we deliberately skipped.
        return ResponseEntity.ok(Map.of("status", result.name()));
    }

    private boolean isAuthorized(String authorization) {
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return false;
        }

        String expected = emailProperties.getWebhookUsername() + ":" + emailProperties.getWebhookPassword();

        byte[] provided;

        try {
            provided = Base64.getDecoder().decode(authorization.substring(6).trim());
        } catch (IllegalArgumentException e) {
            return false;
        }

        return MessageDigest.isEqual(provided, expected.getBytes(StandardCharsets.UTF_8));
    }
}
