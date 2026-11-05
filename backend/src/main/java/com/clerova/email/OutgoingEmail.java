package com.clerova.email;

import java.util.List;

/**
 * @param bcc a hidden copy, e.g. for the customer's own inbox; may be null
 * @param attachments files sent with the email, like an invoice packet
 */
public record OutgoingEmail(
        String from,
        String replyTo,
        String to,
        String subject,
        String textBody,
        String inReplyTo,
        String references,
        String bcc,
        List<Attachment> attachments
) {

    /** A file sent with the email. */
    public record Attachment(String name, String contentType, byte[] content) {
    }

    public OutgoingEmail {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public OutgoingEmail(
            String from,
            String replyTo,
            String to,
            String subject,
            String textBody,
            String inReplyTo,
            String references,
            String bcc
    ) {
        this(from, replyTo, to, subject, textBody, inReplyTo, references, bcc, List.of());
    }

    public OutgoingEmail(
            String from,
            String replyTo,
            String to,
            String subject,
            String textBody,
            String inReplyTo,
            String references
    ) {
        this(from, replyTo, to, subject, textBody, inReplyTo, references, null, List.of());
    }
}
