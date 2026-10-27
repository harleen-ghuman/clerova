package com.clerova.email;

/**
 * @param bcc a hidden copy, e.g. for the customer's own inbox; may be null
 */
public record OutgoingEmail(
        String from,
        String replyTo,
        String to,
        String subject,
        String textBody,
        String inReplyTo,
        String references,
        String bcc
) {

    public OutgoingEmail(
            String from,
            String replyTo,
            String to,
            String subject,
            String textBody,
            String inReplyTo,
            String references
    ) {
        this(from, replyTo, to, subject, textBody, inReplyTo, references, null);
    }
}
