package com.clerova.email;

import java.util.List;

/**
 * An inbound email, independent of the provider that delivered it.
 */
public record ParsedInboundEmail(
        String providerMessageId,
        List<String> recipients,
        String fromEmail,
        String fromName,
        String subject,
        String textBody,
        String replyText,
        String messageId,
        String inReplyTo,
        String references,
        boolean automated,
        List<Attachment> attachments
) {

    public record Attachment(String fileName, String contentType, Long sizeBytes) {
    }
}
