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

    /** @param content the file as base64, or null when the provider didn't send it */
    public record Attachment(String fileName, String contentType, Long sizeBytes, String content) {

        @Override
        public String toString() {
            return "Attachment[" + fileName + ", " + contentType + ", " + sizeBytes + " bytes]";
        }
    }
}
