package com.clerova.email;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Parses Postmark's inbound webhook JSON.
 * See https://postmarkapp.com/developer/webhooks/inbound-webhook
 */
@Component
public class PostmarkInboundParser {

    public ParsedInboundEmail parse(JsonNode payload) {
        Map<String, String> headers = new HashMap<>();

        for (JsonNode header : payload.path("Headers")) {
            String name = text(header, "Name");

            if (name != null) {
                headers.putIfAbsent(name.toLowerCase(Locale.ROOT), text(header, "Value"));
            }
        }

        List<String> recipients = new ArrayList<>();
        addIfPresent(recipients, text(payload, "OriginalRecipient"));

        for (String field : List.of("ToFull", "CcFull", "BccFull")) {
            for (JsonNode recipient : payload.path(field)) {
                addIfPresent(recipients, text(recipient, "Email"));
            }
        }

        List<ParsedInboundEmail.Attachment> attachments = new ArrayList<>();

        for (JsonNode attachment : payload.path("Attachments")) {
            JsonNode length = attachment.path("ContentLength");

            attachments.add(new ParsedInboundEmail.Attachment(
                    text(attachment, "Name"),
                    text(attachment, "ContentType"),
                    length.isNumber() ? length.asLong() : null
            ));
        }

        JsonNode from = payload.path("FromFull");

        String textBody = text(payload, "TextBody");

        if (textBody == null || textBody.isBlank()) {
            textBody = htmlToText(text(payload, "HtmlBody"));
        }

        String fromEmail = firstNonBlank(text(from, "Email"), text(payload, "From"));
        String fromName = text(from, "Name");
        String subject = text(payload, "Subject");
        String replyText = text(payload, "StrippedTextReply");

        // Forwarded by hand: the resident is the sender, and the reply-only
        // text would be just the forwarder's note, so keep the whole body
        Optional<ForwardedEmail> forwarded = ForwardedEmail.detect(subject, textBody, fromEmail);

        if (forwarded.isPresent()) {
            fromEmail = forwarded.get().fromEmail();
            fromName = forwarded.get().fromName();
            subject = forwarded.get().subject() != null ? forwarded.get().subject() : subject;
            replyText = null;
        }

        return new ParsedInboundEmail(
                text(payload, "MessageID"),
                recipients,
                fromEmail,
                fromName,
                subject,
                textBody,
                replyText,
                headers.get("message-id"),
                headers.get("in-reply-to"),
                headers.get("references"),
                isAutomated(headers),
                attachments
        );
    }

    /**
     * Auto-replies, bounces and mailing-list mail, per RFC 3834 and common
     * list headers. Notification emails from tenant portals or load boards
     * are not flagged, since they often carry real requests.
     */
    static boolean isAutomated(Map<String, String> headers) {
        String autoSubmitted = headers.get("auto-submitted");

        if (autoSubmitted != null && !autoSubmitted.trim().equalsIgnoreCase("no")) {
            return true;
        }

        String precedence = headers.getOrDefault("precedence", "").trim().toLowerCase(Locale.ROOT);

        if (List.of("bulk", "list", "junk", "auto_reply").contains(precedence)) {
            return true;
        }

        return headers.containsKey("list-unsubscribe")
                || headers.containsKey("list-id")
                || headers.containsKey("x-autoreply")
                || headers.containsKey("x-autorespond");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);

        return value.isString() ? value.asString() : null;
    }

    private static void addIfPresent(List<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value.trim());
        }
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    static String htmlToText(String html) {
        if (html == null) {
            return null;
        }

        return html
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|tr|li|h[1-6])>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
