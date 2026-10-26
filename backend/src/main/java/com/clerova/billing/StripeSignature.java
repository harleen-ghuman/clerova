package com.clerova.billing;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Verifies the Stripe-Signature header of a webhook: an HMAC-SHA256 of
 * "{timestamp}.{payload}" keyed with the endpoint's signing secret.
 */
public final class StripeSignature {

    static final Duration TOLERANCE = Duration.ofMinutes(5);

    private StripeSignature() {
    }

    public static boolean isValid(String payload, String header, String secret, Instant now) {
        if (header == null || secret == null || secret.isBlank()) {
            return false;
        }

        Long timestamp = null;
        boolean matched = false;

        for (String part : header.split(",")) {
            String[] pair = part.trim().split("=", 2);

            if (pair.length != 2) {
                continue;
            }

            if (pair[0].equals("t")) {
                try {
                    timestamp = Long.parseLong(pair[1]);
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }

        if (timestamp == null) {
            return false;
        }

        if (Duration.between(Instant.ofEpochSecond(timestamp), now).abs().compareTo(TOLERANCE) > 0) {
            return false;
        }

        byte[] expected = sign(timestamp + "." + payload, secret).getBytes(StandardCharsets.UTF_8);

        for (String part : header.split(",")) {
            String[] pair = part.trim().split("=", 2);

            if (pair.length == 2 && pair[0].equals("v1")
                    && MessageDigest.isEqual(expected, pair[1].getBytes(StandardCharsets.UTF_8))) {
                matched = true;
            }
        }

        return matched;
    }

    /** Hex HMAC-SHA256, as Stripe computes it. */
    public static String sign(String signedPayload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

            return HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }
}
